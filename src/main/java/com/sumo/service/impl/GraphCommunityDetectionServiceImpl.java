package com.sumo.service.impl;

import com.google.cloud.spanner.*;
import com.sumo.dao.CommunityDao;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Community;
import com.sumo.entity.Device;
import com.sumo.service.GraphCommunityDetectionService;
import com.sumo.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of GraphCommunityDetectionService.
 * Uses Spanner's graph database capabilities for community detection.
 */
public class GraphCommunityDetectionServiceImpl implements GraphCommunityDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphCommunityDetectionServiceImpl.class);
    
    private final DatabaseClient dbClient;
    private final DeviceDao deviceDao;
    private final CommunityDao communityDao;
    
    public GraphCommunityDetectionServiceImpl(DatabaseClient dbClient, 
                                            DeviceDao deviceDao, 
                                            CommunityDao communityDao) {
        this.dbClient = dbClient;
        this.deviceDao = deviceDao;
        this.communityDao = communityDao;
    }
    
    @Override
    public Map<String, List<String>> detectAndStoreCommunitiesWithGraph(List<Device> devices) {
        logger.info("Starting graph-based community detection for {} devices", devices.size());
        
        // First, save all devices to the database
        List<Device> savedDevices = deviceDao.saveAll(devices);
        logger.debug("Saved {} devices to database", savedDevices.size());
        
        // Create graph edges based on device relationships
        createGraphEdges(savedDevices);
        
        // Detect communities using graph queries
        Map<String, List<String>> communities = detectAllCommunitiesWithConnectedComponents();
        
        // Store communities in database
        storeCommunities(communities);
        
        logger.info("Graph-based community detection completed. Found {} communities", communities.size());
        return communities;
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesBySsidGraph() {
        logger.debug("Detecting communities by SSID using graph traversal");
        
        String gqlQuery = """
            SELECT 
                d1.device_id as device1,
                d2.device_id as device2,
                d1.ssid
            FROM devices d1
            JOIN devices d2 ON d1.ssid = d2.ssid AND d1.device_id < d2.device_id
            WHERE d1.ssid IS NOT NULL
            """;
        
        return executeGraphQueryAndGroup(gqlQuery, "SSID");
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesBySubnetGraph() {
        logger.debug("Detecting communities by subnet using graph traversal");
        
        String gqlQuery = """
            SELECT 
                d1.device_id as device1,
                d2.device_id as device2,
                d1.subnet
            FROM devices d1
            JOIN devices d2 ON d1.subnet = d2.subnet AND d1.device_id < d2.device_id
            WHERE d1.subnet IS NOT NULL
            """;
        
        return executeGraphQueryAndGroup(gqlQuery, "SUBNET");
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesByMacPrefixGraph() {
        logger.debug("Detecting communities by MAC prefix using graph traversal");
        
        String gqlQuery = """
            SELECT 
                d1.device_id as device1,
                d2.device_id as device2,
                d1.mac_prefix
            FROM devices d1
            JOIN devices d2 ON d1.mac_prefix = d2.mac_prefix AND d1.device_id < d2.device_id
            WHERE d1.mac_prefix IS NOT NULL
            """;
        
        return executeGraphQueryAndGroup(gqlQuery, "MAC_PREFIX");
    }
    
    @Override
    public Map<String, List<String>> detectAllCommunitiesWithConnectedComponents() {
        logger.debug("Detecting all communities using connected components graph algorithm");
        
        // Since Spanner doesn't support WITH RECURSIVE, we use a simpler approach
        // Find all devices that share at least one connection type
        
        String connectedComponentsQuery = """
            WITH device_connections AS (
                SELECT DISTINCT d1.device_id as device1, d2.device_id as device2
                FROM devices d1
                JOIN devices d2 ON d1.device_id < d2.device_id
                WHERE (
                    (d1.ssid = d2.ssid AND d1.ssid IS NOT NULL) OR
                    (d1.subnet = d2.subnet AND d1.subnet IS NOT NULL) OR
                    (d1.mac_prefix = d2.mac_prefix AND d1.mac_prefix IS NOT NULL)
                )
            ),
            device_groups AS (
                SELECT 
                    LEAST(device1, device2) as group_root,
                    ARRAY_AGG(DISTINCT device1 ORDER BY device1) || 
                    ARRAY_AGG(DISTINCT device2 ORDER BY device2) as all_devices
                FROM device_connections
                GROUP BY LEAST(device1, device2)
            ),
            expanded_groups AS (
                SELECT 
                    group_root,
                    ARRAY(
                        SELECT DISTINCT device_id 
                        FROM UNNEST(all_devices) as device_id
                        ORDER BY device_id
                    ) as members
                FROM device_groups
            )
            SELECT group_root as root, members
            FROM expanded_groups
            WHERE ARRAY_LENGTH(members) > 1
            """;
        
        Map<String, List<String>> communities = new HashMap<>();
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.of(connectedComponentsQuery)
        )) {
            int communityIndex = 1;
            while (resultSet.next()) {
                String root = resultSet.getString("root");
                List<String> members = resultSet.getStringList("members");
                
                if (members.size() > 1) {
                    String communityId = "GRAPH_COMPONENT_" + communityIndex;
                    communities.put(communityId, members);
                    communityIndex++;
                }
            }
        }
        
        return communities;
    }
    
    @Override
    public List<Community> getCommunitiesForDeviceGraph(String deviceId) {
        logger.debug("Getting communities for device using graph traversal: {}", deviceId);
        
        String gqlQuery = """
            SELECT c.community_id, c.root_device_id, c.size, c.community_type
            FROM communities c
            WHERE @deviceId IN UNNEST(c.device_ids)
            """;
        
        List<Community> communities = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder(gqlQuery).bind("deviceId").to(deviceId).build()
        )) {
            while (resultSet.next()) {
                Community community = new Community();
                community.setCommunityId(resultSet.getString("community_id"));
                community.setRootDeviceId(resultSet.getString("root_device_id"));
                community.setSize((int) resultSet.getLong("size"));
                community.setCommunityType(resultSet.getString("community_type"));
                communities.add(community);
            }
        }
        
        return communities;
    }
    
    @Override
    public List<Device> getDevicesInCommunityGraph(String communityId) {
        logger.debug("Getting devices in community using graph traversal: {}", communityId);
        
        String gqlQuery = """
            SELECT d.*
            FROM communities c
            CROSS JOIN UNNEST(c.device_ids) as device_id
            JOIN devices d ON d.device_id = device_id
            WHERE c.community_id = @communityId
            """;
        
        List<Device> devices = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder(gqlQuery).bind("communityId").to(communityId).build()
        )) {
            while (resultSet.next()) {
                devices.add(mapResultSetToDevice(resultSet));
            }
        }
        
        return devices;
    }
    
    @Override
    public List<String> findShortestPath(String fromDeviceId, String toDeviceId) {
        logger.debug("Finding shortest path between devices: {} -> {}", fromDeviceId, toDeviceId);
        
        // This is a simplified shortest path implementation
        // In a real Spanner Graph implementation, this would use GQL with SHORTEST PATH
        
        // Simplified shortest path implementation using direct connections
        // Since Spanner doesn't support recursive CTEs, we'll find direct connections first
        
        String directConnectionQuery = """
            SELECT DISTINCT d2.device_id
            FROM devices d1
            JOIN devices d2 ON (
                (d1.ssid = d2.ssid AND d1.ssid IS NOT NULL) OR
                (d1.subnet = d2.subnet AND d1.subnet IS NOT NULL) OR
                (d1.mac_prefix = d2.mac_prefix AND d1.mac_prefix IS NOT NULL)
            ) AND d1.device_id != d2.device_id
            WHERE d1.device_id = @fromDeviceId
            """;
        
        List<String> directConnections = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder(directConnectionQuery)
                .bind("fromDeviceId").to(fromDeviceId)
                .build()
        )) {
            while (resultSet.next()) {
                directConnections.add(resultSet.getString("device_id"));
            }
        }
        
        // Check if target device is directly connected
        if (directConnections.contains(toDeviceId)) {
            return Arrays.asList(fromDeviceId, toDeviceId);
        }
        
        // For now, return empty list if no direct connection
        // In a real implementation, you would implement BFS using multiple queries
        logger.debug("No direct path found between {} and {}", fromDeviceId, toDeviceId);
        return Collections.emptyList();
    }
    
    @Override
    public Map<String, Integer> getCommunityStatisticsGraph() {
        logger.debug("Getting community statistics using graph queries");
        
        String statsQuery = """
            SELECT community_type, COUNT(*) as count
            FROM communities
            GROUP BY community_type
            """;
        
        Map<String, Integer> stats = new HashMap<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.of(statsQuery)
        )) {
            while (resultSet.next()) {
                stats.put(resultSet.getString("community_type"), 
                         (int) resultSet.getLong("count"));
            }
        }
        
        return stats;
    }
    
    @Override
    public Map<String, List<String>> rebuildAllCommunitiesWithGraph() {
        logger.info("Rebuilding all communities using graph algorithms");
        
        // Clear existing communities
        clearAllCommunities();
        
        // Get all devices from database
        List<Device> allDevices = deviceDao.findAll();
        logger.debug("Found {} devices in database", allDevices.size());
        
        if (allDevices.isEmpty()) {
            return new HashMap<>();
        }
        
        // Recreate graph edges
        createGraphEdges(allDevices);
        
        // Detect and store new communities
        return detectAndStoreCommunitiesWithGraph(allDevices);
    }
    
    /**
     * Create graph edges based on device relationships.
     */
    private void createGraphEdges(List<Device> devices) {
        logger.debug("Creating graph edges for {} devices", devices.size());
        
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        for (Device device : devices) {
            // Process network attributes if not already set
            if (device.getSubnet() == null) {
                device.setSubnet(NetworkUtils.getSubnet(device.getIp()));
            }
            if (device.getMacPrefix() == null) {
                device.setMacPrefix(NetworkUtils.getMacPrefix(device.getMac()));
            }
            
            // Update device with processed attributes
            deviceDao.update(device);
        }
        
        // Create edges for devices that share attributes
        for (int i = 0; i < devices.size(); i++) {
            Device device1 = devices.get(i);
            for (int j = i + 1; j < devices.size(); j++) {
                Device device2 = devices.get(j);
                
                // SSID connection
                if (device1.getSsid() != null && device1.getSsid().equals(device2.getSsid())) {
                    mutations.add(createEdgeMutation("ssid_connections", device1, device2, device1.getSsid(), now));
                }
                
                // Subnet connection
                if (device1.getSubnet() != null && device1.getSubnet().equals(device2.getSubnet())) {
                    mutations.add(createEdgeMutation("subnet_connections", device1, device2, device1.getSubnet(), now));
                }
                
                // MAC prefix connection
                if (device1.getMacPrefix() != null && device1.getMacPrefix().equals(device2.getMacPrefix())) {
                    mutations.add(createEdgeMutation("mac_connections", device1, device2, device1.getMacPrefix(), now));
                }
            }
        }
        
        if (!mutations.isEmpty()) {
            dbClient.write(mutations);
            logger.debug("Created {} graph edges", mutations.size());
        }
    }
    
    /**
     * Create an edge mutation for the graph.
     */
    private Mutation createEdgeMutation(String tableName, Device device1, Device device2, String attribute, Instant timestamp) {
        String columnName = getColumnNameForTable(tableName);
        return Mutation.newInsertBuilder(tableName)
            .set("from_device_id").to(device1.getDeviceId())
            .set("to_device_id").to(device2.getDeviceId())
            .set(columnName).to(attribute)
            .set("created_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(timestamp.toEpochMilli() * 1000))
            .build();
    }
    
    /**
     * Get the correct column name for the given table.
     */
    private String getColumnNameForTable(String tableName) {
        switch (tableName) {
            case "ssid_connections":
                return "ssid";
            case "subnet_connections":
                return "subnet";
            case "mac_connections":
                return "mac_prefix";
            default:
                throw new IllegalArgumentException("Unknown table: " + tableName);
        }
    }
    
    /**
     * Execute a graph query and group results into communities.
     */
    private Map<String, List<String>> executeGraphQueryAndGroup(String query, String communityType) {
        Map<String, List<String>> communities = new HashMap<>();
        Map<String, Set<String>> groups = new HashMap<>();
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(query))) {
            while (resultSet.next()) {
                String device1 = resultSet.getString("device1");
                String device2 = resultSet.getString("device2");
                String attribute = resultSet.getString(communityType.toLowerCase());
                
                groups.computeIfAbsent(attribute, k -> new HashSet<>()).add(device1);
                groups.computeIfAbsent(attribute, k -> new HashSet<>()).add(device2);
            }
        }
        
        int communityIndex = 1;
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String communityId = communityType + "_GRAPH_" + communityIndex;
                communities.put(communityId, new ArrayList<>(entry.getValue()));
                communityIndex++;
            }
        }
        
        return communities;
    }
    
    /**
     * Store communities in the database.
     */
    private void storeCommunities(Map<String, List<String>> communities) {
        List<Community> communityEntities = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
            String communityId = entry.getKey();
            List<String> deviceIds = entry.getValue();
            
            if (deviceIds.size() > 1) {
                String rootDeviceId = deviceIds.get(0);
                String communityType = determineCommunityType(deviceIds);
                
                Community community = new Community(communityId, rootDeviceId, deviceIds.size(), communityType);
                communityEntities.add(community);
            }
        }
        
        // Save to database using a custom method that handles the device_ids array
        if (!communityEntities.isEmpty()) {
            saveCommunitiesWithDeviceIds(communityEntities, communities);
            logger.debug("Saved {} communities to database", communityEntities.size());
        }
    }
    
    /**
     * Save communities with device IDs array.
     */
    private void saveCommunitiesWithDeviceIds(List<Community> communities, Map<String, List<String>> communityDeviceMap) {
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        for (Community community : communities) {
            List<String> deviceIds = communityDeviceMap.get(community.getCommunityId());
            
            mutations.add(
                Mutation.newInsertBuilder("communities")
                    .set("community_id").to(community.getCommunityId())
                    .set("root_device_id").to(community.getRootDeviceId())
                    .set("size").to(community.getSize())
                    .set("community_type").to(community.getCommunityType())
                    .set("device_ids").toStringArray(deviceIds)
                    .set("created_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(now.toEpochMilli() * 1000))
                    .set("updated_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(now.toEpochMilli() * 1000))
                    .build()
            );
        }
        
        dbClient.write(mutations);
    }
    
    /**
     * Determine the community type based on device attributes.
     */
    private String determineCommunityType(List<String> deviceIds) {
        Set<String> ssids = new HashSet<>();
        Set<String> subnets = new HashSet<>();
        Set<String> macPrefixes = new HashSet<>();
        
        for (String deviceId : deviceIds) {
            deviceDao.findById(deviceId).ifPresent(device -> {
                if (device.getSsid() != null) ssids.add(device.getSsid());
                if (device.getSubnet() != null) subnets.add(device.getSubnet());
                if (device.getMacPrefix() != null) macPrefixes.add(device.getMacPrefix());
            });
        }
        
        int attributeTypes = 0;
        if (ssids.size() == 1) attributeTypes++;
        if (subnets.size() == 1) attributeTypes++;
        if (macPrefixes.size() == 1) attributeTypes++;
        
        if (attributeTypes == 1) {
            if (ssids.size() == 1) return "SSID";
            if (subnets.size() == 1) return "SUBNET";
            if (macPrefixes.size() == 1) return "MAC_PREFIX";
        }
        
        return "MIXED";
    }
    
    /**
     * Clear all existing communities.
     */
    private void clearAllCommunities() {
        logger.debug("Clearing all existing communities");
        
        List<Community> existingCommunities = communityDao.findAll();
        for (Community community : existingCommunities) {
            communityDao.deleteById(community.getCommunityId());
        }
    }
    
    /**
     * Map ResultSet to Device entity.
     */
    private Device mapResultSetToDevice(ResultSet resultSet) {
        Device device = new Device();
        device.setDeviceId(resultSet.getString("device_id"));
        device.setSsid(resultSet.getString("ssid"));
        device.setIp(resultSet.getString("ip"));
        device.setMac(resultSet.getString("mac"));
        device.setSubnet(resultSet.getString("subnet"));
        device.setMacPrefix(resultSet.getString("mac_prefix"));
        device.setCreatedAt(resultSet.getTimestamp("created_at").toSqlTimestamp().toInstant());
        device.setUpdatedAt(resultSet.getTimestamp("updated_at").toSqlTimestamp().toInstant());
        return device;
    }
}

