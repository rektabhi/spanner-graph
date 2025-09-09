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
 * Implementation of GraphCommunityDetectionService using heterogeneous graph model.
 * Uses Spanner's graph database capabilities with devices and attributes as nodes.
 */
public class HeterogeneousGraphCommunityDetectionServiceImpl implements GraphCommunityDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(HeterogeneousGraphCommunityDetectionServiceImpl.class);
    
    private final DatabaseClient dbClient;
    private final DeviceDao deviceDao;
    private final CommunityDao communityDao;
    
    public HeterogeneousGraphCommunityDetectionServiceImpl(DatabaseClient dbClient, 
                                            DeviceDao deviceDao, 
                                            CommunityDao communityDao) {
        this.dbClient = dbClient;
        this.deviceDao = deviceDao;
        this.communityDao = communityDao;
    }
    
    @Override
    public Map<String, List<String>> detectAndStoreCommunitiesWithGraph(List<Device> devices) {
        logger.info("Starting heterogeneous graph-based community detection for {} devices", devices.size());
        
        // First, save all devices to the database
        List<Device> savedDevices = deviceDao.saveAll(devices);
        logger.debug("Saved {} devices to database", savedDevices.size());
        
        // Create heterogeneous graph nodes and edges
        createHeterogeneousGraphNodesAndEdges(savedDevices);
        
        // Detect communities using heterogeneous graph queries
        Map<String, List<String>> communities = detectAllCommunitiesWithConnectedComponents();
        
        // Store communities in database
        storeCommunities(communities);
        
        logger.info("Heterogeneous graph-based community detection completed. Found {} communities", communities.size());
        return communities;
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesBySsidGraph() {
        logger.debug("Detecting communities by SSID using heterogeneous graph traversal");
        
        String gqlQuery = """
            SELECT 
                d.device_id,
                s.ssid_value
            FROM devices d
            JOIN device_ssid_edges dse ON d.device_id = dse.device_id
            JOIN ssid_nodes s ON dse.ssid_value = s.ssid_value
            WHERE s.ssid_value IS NOT NULL
            """;
        
        return executeHeterogeneousGraphQueryAndGroup(gqlQuery, "SSID");
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesBySubnetGraph() {
        logger.debug("Detecting communities by subnet using heterogeneous graph traversal");
        
        String gqlQuery = """
            SELECT 
                d.device_id,
                sn.subnet_value
            FROM devices d
            JOIN device_subnet_edges dse ON d.device_id = dse.device_id
            JOIN subnet_nodes sn ON dse.subnet_value = sn.subnet_value
            WHERE sn.subnet_value IS NOT NULL
            """;
        
        return executeHeterogeneousGraphQueryAndGroup(gqlQuery, "SUBNET");
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesByMacPrefixGraph() {
        logger.debug("Detecting communities by MAC prefix using heterogeneous graph traversal");
        
        String gqlQuery = """
            SELECT 
                d.device_id,
                mp.mac_prefix_value
            FROM devices d
            JOIN device_mac_prefix_edges dmpe ON d.device_id = dmpe.device_id
            JOIN mac_prefix_nodes mp ON dmpe.mac_prefix_value = mp.mac_prefix_value
            WHERE mp.mac_prefix_value IS NOT NULL
            """;
        
        return executeHeterogeneousGraphQueryAndGroup(gqlQuery, "MAC_PREFIX");
    }
    
    @Override
    public Map<String, List<String>> detectAllCommunitiesWithConnectedComponents() {
        logger.debug("Detecting all communities using connected components in heterogeneous graph");
        
        // Find connected components by traversing the heterogeneous graph
        // Devices are connected if they share any attribute (SSID, subnet, MAC prefix, IP)
        
        String connectedComponentsQuery = """
            WITH device_connections AS (
                -- Find devices connected through SSID
                SELECT DISTINCT d1.device_id as device1, d2.device_id as device2, 'SSID' as connection_type
                FROM devices d1
                JOIN device_ssid_edges dse1 ON d1.device_id = dse1.device_id
                JOIN device_ssid_edges dse2 ON dse1.ssid_value = dse2.ssid_value
                JOIN devices d2 ON dse2.device_id = d2.device_id
                WHERE d1.device_id < d2.device_id
                
                UNION ALL
                
                -- Find devices connected through subnet
                SELECT DISTINCT d1.device_id as device1, d2.device_id as device2, 'SUBNET' as connection_type
                FROM devices d1
                JOIN device_subnet_edges dse1 ON d1.device_id = dse1.device_id
                JOIN device_subnet_edges dse2 ON dse1.subnet_value = dse2.subnet_value
                JOIN devices d2 ON dse2.device_id = d2.device_id
                WHERE d1.device_id < d2.device_id
                
                UNION ALL
                
                -- Find devices connected through MAC prefix
                SELECT DISTINCT d1.device_id as device1, d2.device_id as device2, 'MAC_PREFIX' as connection_type
                FROM devices d1
                JOIN device_mac_prefix_edges dmpe1 ON d1.device_id = dmpe1.device_id
                JOIN device_mac_prefix_edges dmpe2 ON dmpe1.mac_prefix_value = dmpe2.mac_prefix_value
                JOIN devices d2 ON dmpe2.device_id = d2.device_id
                WHERE d1.device_id < d2.device_id
                
                UNION ALL
                
                -- Find devices connected through IP (same subnet)
                SELECT DISTINCT d1.device_id as device1, d2.device_id as device2, 'IP' as connection_type
                FROM devices d1
                JOIN device_ip_edges die1 ON d1.device_id = die1.device_id
                JOIN device_ip_edges die2 ON die1.ip_value = die2.ip_value
                JOIN devices d2 ON die2.device_id = d2.device_id
                WHERE d1.device_id < d2.device_id
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
                    String communityId = "HETERO_GRAPH_COMPONENT_" + communityIndex;
                    communities.put(communityId, members);
                    communityIndex++;
                }
            }
        }
        
        logger.debug("Heterogeneous connected components algorithm found {} communities", communities.size());
        return communities;
    }
    
    @Override
    public List<Community> getCommunitiesForDeviceGraph(String deviceId) {
        logger.debug("Getting communities for device using heterogeneous graph traversal: {}", deviceId);
        
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
        logger.debug("Getting devices in community using heterogeneous graph traversal: {}", communityId);
        
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
        logger.debug("Finding shortest path between devices in heterogeneous graph: {} -> {}", fromDeviceId, toDeviceId);
        
        // Find direct connections through shared attributes
        String directConnectionQuery = """
            WITH device_attributes AS (
                SELECT @fromDeviceId as device_id, 'SSID' as attr_type, ssid_value as attr_value
                FROM device_ssid_edges WHERE device_id = @fromDeviceId
                UNION ALL
                SELECT @fromDeviceId as device_id, 'SUBNET' as attr_type, subnet_value as attr_value
                FROM device_subnet_edges WHERE device_id = @fromDeviceId
                UNION ALL
                SELECT @fromDeviceId as device_id, 'MAC_PREFIX' as attr_type, mac_prefix_value as attr_value
                FROM device_mac_prefix_edges WHERE device_id = @fromDeviceId
                UNION ALL
                SELECT @fromDeviceId as device_id, 'IP' as attr_type, ip_value as attr_value
                FROM device_ip_edges WHERE device_id = @fromDeviceId
            )
            SELECT DISTINCT d.device_id
            FROM devices d
            WHERE d.device_id != @fromDeviceId
            AND (
                EXISTS (SELECT 1 FROM device_ssid_edges dse WHERE dse.device_id = d.device_id 
                       AND dse.ssid_value IN (SELECT attr_value FROM device_attributes WHERE attr_type = 'SSID'))
                OR
                EXISTS (SELECT 1 FROM device_subnet_edges dse WHERE dse.device_id = d.device_id 
                       AND dse.subnet_value IN (SELECT attr_value FROM device_attributes WHERE attr_type = 'SUBNET'))
                OR
                EXISTS (SELECT 1 FROM device_mac_prefix_edges dmpe WHERE dmpe.device_id = d.device_id 
                       AND dmpe.mac_prefix_value IN (SELECT attr_value FROM device_attributes WHERE attr_type = 'MAC_PREFIX'))
                OR
                EXISTS (SELECT 1 FROM device_ip_edges die WHERE die.device_id = d.device_id 
                       AND die.ip_value IN (SELECT attr_value FROM device_attributes WHERE attr_type = 'IP'))
            )
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
        logger.debug("No direct path found between {} and {} in heterogeneous graph", fromDeviceId, toDeviceId);
        return Collections.emptyList();
    }
    
    @Override
    public Map<String, Integer> getCommunityStatisticsGraph() {
        logger.debug("Getting community statistics using heterogeneous graph queries");
        
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
        logger.info("Rebuilding all communities using heterogeneous graph algorithms");
        
        // Clear existing communities and graph edges
        clearAllCommunities();
        clearAllHeterogeneousGraphEdges();
        
        // Get all devices from database
        List<Device> allDevices = deviceDao.findAll();
        logger.debug("Found {} devices in database", allDevices.size());
        
        if (allDevices.isEmpty()) {
            return new HashMap<>();
        }
        
        // Create heterogeneous graph nodes and edges for ALL devices
        logger.info("Creating heterogeneous graph nodes and edges for all {} devices in database", allDevices.size());
        createHeterogeneousGraphNodesAndEdgesForAllDevices(allDevices);
        
        // Detect communities using the complete heterogeneous graph
        Map<String, List<String>> communities = detectAllCommunitiesWithConnectedComponents();
        
        // Store communities in database
        storeCommunities(communities);
        
        logger.info("Global heterogeneous community detection completed. Found {} communities", communities.size());
        return communities;
    }
    
    /**
     * Create heterogeneous graph nodes and edges for a list of devices.
     */
    private void createHeterogeneousGraphNodesAndEdges(List<Device> devices) {
        logger.debug("Creating heterogeneous graph nodes and edges for {} devices", devices.size());
        
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        // Process network attributes for all devices
        for (Device device : devices) {
            if (device.getSubnet() == null) {
                device.setSubnet(NetworkUtils.getSubnet(device.getIp()));
            }
            if (device.getMacPrefix() == null) {
                device.setMacPrefix(NetworkUtils.getMacPrefix(device.getMac()));
            }
            
            // Update device with processed attributes
            deviceDao.update(device);
            
            // Create attribute nodes and device-attribute edges
            createAttributeNodesAndEdges(device, mutations, now);
        }
        
        if (!mutations.isEmpty()) {
            writeMutationsInBatches(mutations);
            logger.debug("Created {} heterogeneous graph nodes and edges", mutations.size());
        }
    }
    
    /**
     * Create heterogeneous graph nodes and edges for ALL devices in the database.
     */
    private void createHeterogeneousGraphNodesAndEdgesForAllDevices(List<Device> allDevices) {
        logger.info("Creating heterogeneous graph nodes and edges for all {} devices in database", allDevices.size());
        
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        // Process network attributes for all devices
        for (Device device : allDevices) {
            if (device.getSubnet() == null) {
                device.setSubnet(NetworkUtils.getSubnet(device.getIp()));
            }
            if (device.getMacPrefix() == null) {
                device.setMacPrefix(NetworkUtils.getMacPrefix(device.getMac()));
            }
            
            // Update device with processed attributes
            deviceDao.update(device);
            
            // Create attribute nodes and device-attribute edges
            createAttributeNodesAndEdges(device, mutations, now);
        }
        
        // Write all mutations in batches to avoid overwhelming Spanner
        if (!mutations.isEmpty()) {
            writeMutationsInBatches(mutations);
            logger.info("Created {} heterogeneous graph nodes and edges for all devices", mutations.size());
        }
    }
    
    /**
     * Create attribute nodes and device-attribute edges for a single device.
     */
    private void createAttributeNodesAndEdges(Device device, List<Mutation> mutations, Instant now) {
        // Create SSID node and edge
        if (device.getSsid() != null && !device.getSsid().isEmpty()) {
            mutations.add(createAttributeNodeMutation("ssid_nodes", device.getSsid(), now));
            mutations.add(createDeviceAttributeEdgeMutation("device_ssid_edges", device.getDeviceId(), device.getSsid(), now));
        }
        
        // Create subnet node and edge
        if (device.getSubnet() != null && !device.getSubnet().isEmpty()) {
            mutations.add(createAttributeNodeMutation("subnet_nodes", device.getSubnet(), now));
            mutations.add(createDeviceAttributeEdgeMutation("device_subnet_edges", device.getDeviceId(), device.getSubnet(), now));
        }
        
        // Create MAC prefix node and edge
        if (device.getMacPrefix() != null && !device.getMacPrefix().isEmpty()) {
            mutations.add(createAttributeNodeMutation("mac_prefix_nodes", device.getMacPrefix(), now));
            mutations.add(createDeviceAttributeEdgeMutation("device_mac_prefix_edges", device.getDeviceId(), device.getMacPrefix(), now));
        }
        
        // Create IP node and edge
        if (device.getIp() != null && !device.getIp().isEmpty()) {
            mutations.add(createAttributeNodeMutation("ip_nodes", device.getIp(), now));
            mutations.add(createDeviceAttributeEdgeMutation("device_ip_edges", device.getDeviceId(), device.getIp(), now));
        }
    }
    
    /**
     * Create an attribute node mutation.
     */
    private Mutation createAttributeNodeMutation(String tableName, String attributeValue, Instant timestamp) {
        String columnName = getAttributeColumnNameForTable(tableName);
        return Mutation.newInsertBuilder(tableName)
            .set(columnName).to(attributeValue)
            .set("created_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(timestamp.toEpochMilli() * 1000))
            .build();
    }
    
    /**
     * Create a device-attribute edge mutation.
     */
    private Mutation createDeviceAttributeEdgeMutation(String tableName, String deviceId, String attributeValue, Instant timestamp) {
        String attributeColumnName = getAttributeColumnNameForEdgeTable(tableName);
        return Mutation.newInsertBuilder(tableName)
            .set("device_id").to(deviceId)
            .set(attributeColumnName).to(attributeValue)
            .set("created_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(timestamp.toEpochMilli() * 1000))
            .build();
    }
    
    /**
     * Get the correct column name for the given attribute node table.
     */
    private String getAttributeColumnNameForTable(String tableName) {
        switch (tableName) {
            case "ssid_nodes":
                return "ssid_value";
            case "subnet_nodes":
                return "subnet_value";
            case "mac_prefix_nodes":
                return "mac_prefix_value";
            case "ip_nodes":
                return "ip_value";
            default:
                throw new IllegalArgumentException("Unknown attribute node table: " + tableName);
        }
    }
    
    /**
     * Get the correct column name for the given device-attribute edge table.
     */
    private String getAttributeColumnNameForEdgeTable(String tableName) {
        switch (tableName) {
            case "device_ssid_edges":
                return "ssid_value";
            case "device_subnet_edges":
                return "subnet_value";
            case "device_mac_prefix_edges":
                return "mac_prefix_value";
            case "device_ip_edges":
                return "ip_value";
            default:
                throw new IllegalArgumentException("Unknown device-attribute edge table: " + tableName);
        }
    }
    
    /**
     * Execute a heterogeneous graph query and group results into communities.
     */
    private Map<String, List<String>> executeHeterogeneousGraphQueryAndGroup(String query, String communityType) {
        Map<String, List<String>> communities = new HashMap<>();
        Map<String, Set<String>> groups = new HashMap<>();
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(query))) {
            while (resultSet.next()) {
                String deviceId = resultSet.getString("device_id");
                String attributeValue = resultSet.getString(communityType.toLowerCase() + "_value");
                
                groups.computeIfAbsent(attributeValue, k -> new HashSet<>()).add(deviceId);
            }
        }
        
        int communityIndex = 1;
        for (Map.Entry<String, Set<String>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String communityId = communityType + "_HETERO_GRAPH_" + communityIndex;
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
     * Clear all existing heterogeneous graph edges and attribute nodes.
     */
    private void clearAllHeterogeneousGraphEdges() {
        logger.debug("Clearing all existing heterogeneous graph edges and attribute nodes");
        
        try {
            dbClient.write(Arrays.asList(
                Mutation.delete("device_ip_edges", KeySet.all()),
                Mutation.delete("device_mac_prefix_edges", KeySet.all()),
                Mutation.delete("device_subnet_edges", KeySet.all()),
                Mutation.delete("device_ssid_edges", KeySet.all()),
                Mutation.delete("ip_nodes", KeySet.all()),
                Mutation.delete("mac_prefix_nodes", KeySet.all()),
                Mutation.delete("subnet_nodes", KeySet.all()),
                Mutation.delete("ssid_nodes", KeySet.all())
            ));
            logger.debug("Successfully cleared all heterogeneous graph edges and attribute nodes");
        } catch (Exception e) {
            logger.warn("Failed to clear heterogeneous graph edges (this is OK if tables are empty): {}", e.getMessage());
        }
    }
    
    /**
     * Write mutations in batches to avoid overwhelming Spanner.
     */
    private void writeMutationsInBatches(List<Mutation> mutations) {
        final int BATCH_SIZE = 1000; // Write 1000 mutations at a time
        
        for (int i = 0; i < mutations.size(); i += BATCH_SIZE) {
            int endIndex = Math.min(i + BATCH_SIZE, mutations.size());
            List<Mutation> batch = mutations.subList(i, endIndex);
            
            try {
                dbClient.write(batch);
                logger.debug("Wrote batch of {} mutations ({} to {})", batch.size(), i, endIndex - 1);
            } catch (Exception e) {
                logger.error("Failed to write mutation batch {} to {}: {}", i, endIndex - 1, e.getMessage());
                throw new RuntimeException("Failed to write heterogeneous graph edges", e);
            }
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
