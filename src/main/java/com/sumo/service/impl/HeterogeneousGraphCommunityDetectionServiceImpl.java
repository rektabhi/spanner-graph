package com.sumo.service.impl;

import com.google.cloud.spanner.*;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Community;
import com.sumo.entity.Device;
import com.sumo.service.GraphCommunityDetectionService;
import com.sumo.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Implementation of GraphCommunityDetectionService using heterogeneous graph model.
 * Uses Spanner's graph database capabilities with devices and attributes as nodes.
 */
public class HeterogeneousGraphCommunityDetectionServiceImpl implements GraphCommunityDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(HeterogeneousGraphCommunityDetectionServiceImpl.class);
    
    private final DatabaseClient dbClient;
    private final DeviceDao deviceDao;

    public HeterogeneousGraphCommunityDetectionServiceImpl(DatabaseClient dbClient, 
                                            DeviceDao deviceDao) {
        this.dbClient = dbClient;
        this.deviceDao = deviceDao;
    }
    
    @Override
    public Map<String, List<String>> detectAndStoreCommunitiesWithGraph(List<Device> devices) {
        logger.info("Starting heterogeneous graph-based community detection for {} devices", devices.size());
        
        // First, save all devices to the database
        List<Device> savedDevices = deviceDao.saveAll(devices);
        logger.debug("Saved {} devices to database", savedDevices.size());
        
        // Create heterogeneous graph nodes and edges
        createHeterogeneousGraphNodesAndEdges(savedDevices);
        
        // Note: We don't pre-compute and store communities anymore
        // Communities are now computed at runtime when requested
        
        logger.info("Heterogeneous graph-based community detection completed. Graph nodes and edges created for {} devices", savedDevices.size());
        return new HashMap<>(); // Return empty map since we don't pre-compute communities
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
        
        List<Community> communities = new ArrayList<>();
        
        // Find communities by SSID
        List<String> ssidCommunity = findCommunityByAttribute(deviceId, "SSID");
        if (!ssidCommunity.isEmpty()) {
            Community community = new Community();
            community.setCommunityId("SSID_COMMUNITY_" + deviceId);
            community.setRootDeviceId(deviceId);
            community.setSize(ssidCommunity.size());
            community.setCommunityType("SSID");
            community.setDeviceIds(ssidCommunity);
            communities.add(community);
        }
        
        // Find communities by subnet
        List<String> subnetCommunity = findCommunityByAttribute(deviceId, "SUBNET");
        if (!subnetCommunity.isEmpty()) {
            Community community = new Community();
            community.setCommunityId("SUBNET_COMMUNITY_" + deviceId);
            community.setRootDeviceId(deviceId);
            community.setSize(subnetCommunity.size());
            community.setCommunityType("SUBNET");
            community.setDeviceIds(subnetCommunity);
            communities.add(community);
        }
        
        // Find communities by MAC prefix
        List<String> macPrefixCommunity = findCommunityByAttribute(deviceId, "MAC_PREFIX");
        if (!macPrefixCommunity.isEmpty()) {
            Community community = new Community();
            community.setCommunityId("MAC_PREFIX_COMMUNITY_" + deviceId);
            community.setRootDeviceId(deviceId);
            community.setSize(macPrefixCommunity.size());
            community.setCommunityType("MAC_PREFIX");
            community.setDeviceIds(macPrefixCommunity);
            communities.add(community);
        }
        
        // Find communities by IP
        List<String> ipCommunity = findCommunityByAttribute(deviceId, "IP");
        if (!ipCommunity.isEmpty()) {
            Community community = new Community();
            community.setCommunityId("IP_COMMUNITY_" + deviceId);
            community.setRootDeviceId(deviceId);
            community.setSize(ipCommunity.size());
            community.setCommunityType("IP");
            community.setDeviceIds(ipCommunity);
            communities.add(community);
        }
        
        return communities;
    }
    
    @Override
    public List<Device> getDevicesInCommunityGraph(String communityId) {
        logger.debug("Getting devices in community using heterogeneous graph traversal: {}", communityId);
        
        // Parse community ID to determine type and device
        String[] parts = communityId.split("_");
        if (parts.length < 3) {
            logger.warn("Invalid community ID format: {}", communityId);
            return new ArrayList<>();
        }
        
        String communityType = parts[0]; // SSID, SUBNET, MAC_PREFIX, IP
        String deviceId = parts[2]; // The device ID from the community ID
        
        // Find the community for this device and attribute type
        List<String> deviceIds = findCommunityByAttribute(deviceId, communityType);
        
        // Get device details for all devices in the community
        List<Device> devices = new ArrayList<>();
        for (String id : deviceIds) {
            deviceDao.findById(id).ifPresent(devices::add);
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
        
        Map<String, Integer> stats = new HashMap<>();
        
        // Count unique attribute values to estimate community counts
        String ssidCountQuery = "SELECT COUNT(DISTINCT ssid_value) as count FROM ssid_nodes";
        String subnetCountQuery = "SELECT COUNT(DISTINCT subnet_value) as count FROM subnet_nodes";
        String macPrefixCountQuery = "SELECT COUNT(DISTINCT mac_prefix_value) as count FROM mac_prefix_nodes";
        String ipCountQuery = "SELECT COUNT(DISTINCT ip_value) as count FROM ip_nodes";
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(ssidCountQuery))) {
            if (resultSet.next()) {
                stats.put("SSID", (int) resultSet.getLong("count"));
            }
        }
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(subnetCountQuery))) {
            if (resultSet.next()) {
                stats.put("SUBNET", (int) resultSet.getLong("count"));
            }
        }
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(macPrefixCountQuery))) {
            if (resultSet.next()) {
                stats.put("MAC_PREFIX", (int) resultSet.getLong("count"));
            }
        }
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(ipCountQuery))) {
            if (resultSet.next()) {
                stats.put("IP", (int) resultSet.getLong("count"));
            }
        }
        
        return stats;
    }
    
    @Override
    public Map<String, List<String>> rebuildAllCommunitiesWithGraph() {
        logger.info("Rebuilding heterogeneous graph structure");
        
        // Clear existing graph edges (but not communities since we don't store them anymore)
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
        
        // Note: We don't pre-compute and store communities anymore
        // Communities are now computed at runtime when requested
        
        logger.info("Global heterogeneous graph rebuild completed. Graph structure ready for runtime community queries");
        return new HashMap<>(); // Return empty map since we don't pre-compute communities
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
     * Find all devices that share the same attribute value as the given device.
     * This method computes communities at runtime by traversing the heterogeneous graph.
     */
    private List<String> findCommunityByAttribute(String deviceId, String attributeType) {
        logger.debug("Finding community for device {} by attribute type {}", deviceId, attributeType);
        
        String query;
        switch (attributeType) {
            case "SSID":
                query = """
                    SELECT DISTINCT d.device_id
                    FROM devices d
                    JOIN device_ssid_edges dse ON d.device_id = dse.device_id
                    JOIN device_ssid_edges dse2 ON dse.ssid_value = dse2.ssid_value
                    WHERE dse2.device_id = @deviceId
                    """;
                break;
            case "SUBNET":
                query = """
                    SELECT DISTINCT d.device_id
                    FROM devices d
                    JOIN device_subnet_edges dse ON d.device_id = dse.device_id
                    JOIN device_subnet_edges dse2 ON dse.subnet_value = dse2.subnet_value
                    WHERE dse2.device_id = @deviceId
                    """;
                break;
            case "MAC_PREFIX":
                query = """
                    SELECT DISTINCT d.device_id
                    FROM devices d
                    JOIN device_mac_prefix_edges dmpe ON d.device_id = dmpe.device_id
                    JOIN device_mac_prefix_edges dmpe2 ON dmpe.mac_prefix_value = dmpe2.mac_prefix_value
                    WHERE dmpe2.device_id = @deviceId
                    """;
                break;
            case "IP":
                query = """
                    SELECT DISTINCT d.device_id
                    FROM devices d
                    JOIN device_ip_edges die ON d.device_id = die.device_id
                    JOIN device_ip_edges die2 ON die.ip_value = die2.ip_value
                    WHERE die2.device_id = @deviceId
                    """;
                break;
            default:
                logger.warn("Unknown attribute type: {}", attributeType);
                return new ArrayList<>();
        }
        
        List<String> deviceIds = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder(query).bind("deviceId").to(deviceId).build()
        )) {
            while (resultSet.next()) {
                deviceIds.add(resultSet.getString("device_id"));
            }
        }
        
        logger.debug("Found {} devices in {} community for device {}", deviceIds.size(), attributeType, deviceId);
        return deviceIds;
    }
    
    /**
     * Get all devices that share any attribute with the given device.
     * This is useful for finding all possible connections for a device.
     */
    public List<String> getAllConnectedDevices(String deviceId) {
        logger.debug("Getting all connected devices for device: {}", deviceId);
        
        Set<String> connectedDevices = new HashSet<>();
        
        // Get devices connected through SSID
        connectedDevices.addAll(findCommunityByAttribute(deviceId, "SSID"));
        
        // Get devices connected through subnet
        connectedDevices.addAll(findCommunityByAttribute(deviceId, "SUBNET"));
        
        // Get devices connected through MAC prefix
        connectedDevices.addAll(findCommunityByAttribute(deviceId, "MAC_PREFIX"));
        
        // Get devices connected through IP
        connectedDevices.addAll(findCommunityByAttribute(deviceId, "IP"));
        
        // Remove the device itself from the result
        connectedDevices.remove(deviceId);
        
        List<String> result = new ArrayList<>(connectedDevices);
        logger.debug("Found {} connected devices for device {}", result.size(), deviceId);
        return result;
    }
    
    /**
     * Get the largest community for a device across all attribute types.
     */
    public Community getLargestCommunityForDevice(String deviceId) {
        logger.debug("Getting largest community for device: {}", deviceId);
        
        List<Community> allCommunities = getCommunitiesForDeviceGraph(deviceId);
        
        if (allCommunities.isEmpty()) {
            return null;
        }
        
        // Find the community with the largest size
        Community largestCommunity = allCommunities.get(0);
        for (Community community : allCommunities) {
            if (community.getSize() > largestCommunity.getSize()) {
                largestCommunity = community;
            }
        }
        
        logger.debug("Largest community for device {}: {} with {} devices", 
            deviceId, largestCommunity.getCommunityType(), largestCommunity.getSize());
        return largestCommunity;
    }
    
    /**
     * Create attribute nodes and device-attribute edges for a single device.
     * Uses INSERT OR UPDATE to handle duplicate attribute nodes and edges gracefully.
     */
    private void createAttributeNodesAndEdges(Device device, List<Mutation> mutations, Instant now) {
        // Create SSID node and edge
        if (device.getSsid() != null && !device.getSsid().isEmpty()) {
            mutations.add(createAttributeNodeMutationWithIgnore("ssid_nodes", device.getSsid(), now));
            mutations.add(createDeviceAttributeEdgeMutationWithIgnore("device_ssid_edges", device.getDeviceId(), device.getSsid(), now));
        }
        
        // Create subnet node and edge
        if (device.getSubnet() != null && !device.getSubnet().isEmpty()) {
            mutations.add(createAttributeNodeMutationWithIgnore("subnet_nodes", device.getSubnet(), now));
            mutations.add(createDeviceAttributeEdgeMutationWithIgnore("device_subnet_edges", device.getDeviceId(), device.getSubnet(), now));
        }
        
        // Create MAC prefix node and edge
        if (device.getMacPrefix() != null && !device.getMacPrefix().isEmpty()) {
            mutations.add(createAttributeNodeMutationWithIgnore("mac_prefix_nodes", device.getMacPrefix(), now));
            mutations.add(createDeviceAttributeEdgeMutationWithIgnore("device_mac_prefix_edges", device.getDeviceId(), device.getMacPrefix(), now));
        }
        
        // Create IP node and edge
        if (device.getIp() != null && !device.getIp().isEmpty()) {
            mutations.add(createAttributeNodeMutationWithIgnore("ip_nodes", device.getIp(), now));
            mutations.add(createDeviceAttributeEdgeMutationWithIgnore("device_ip_edges", device.getDeviceId(), device.getIp(), now));
        }
    }

    /**
     * Create an attribute node mutation with INSERT OR UPDATE to handle duplicates.
     * This prevents errors when multiple devices share the same attribute value.
     */
    private Mutation createAttributeNodeMutationWithIgnore(String tableName, String attributeValue, Instant timestamp) {
        String columnName = getAttributeColumnNameForTable(tableName);
        return Mutation.newInsertOrUpdateBuilder(tableName)
            .set(columnName).to(attributeValue)
            .set("created_at").to(com.google.cloud.Timestamp.ofTimeMicroseconds(timestamp.toEpochMilli() * 1000))
            .build();
    }

    /**
     * Create a device-attribute edge mutation with INSERT OR UPDATE to handle duplicates.
     * This prevents errors when the same device-attribute edge is created multiple times.
     */
    private Mutation createDeviceAttributeEdgeMutationWithIgnore(String tableName, String deviceId, String attributeValue, Instant timestamp) {
        String attributeColumnName = getAttributeColumnNameForEdgeTable(tableName);
        return Mutation.newInsertOrUpdateBuilder(tableName)
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

}
