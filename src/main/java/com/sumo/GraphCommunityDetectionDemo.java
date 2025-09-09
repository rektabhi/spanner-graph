package com.sumo;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.KeySet;
import com.google.cloud.spanner.Mutation;
import com.sumo.config.SpannerConfig;
import com.sumo.dao.CommunityDao;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Community;
import com.sumo.entity.Device;
import com.sumo.service.GraphCommunityDetectionService;
import com.sumo.service.impl.GraphCommunityDetectionServiceImpl;
import com.sumo.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Demonstration class for the Graph-based Community Detection System.
 * Shows how to use Spanner's graph database capabilities for community detection.
 */
public class GraphCommunityDetectionDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphCommunityDetectionDemo.class);
    
    public static void main(String[] args) {
        logger.info("Starting Graph-based Community Detection Demo");
        
        // Configuration - In a real application, these would come from environment variables or config files
        // String projectId = System.getProperty("spanner.project.id", "your-project-id");
        // String instanceId = System.getProperty("spanner.instance.id", "your-instance-id");
        // String databaseId = System.getProperty("spanner.database.id", "community-graph-db");

        String projectId = "sm-apps-core";
        String instanceId = "common-spanner-next";
        String databaseId = "communities-next";
        
        try {
            // Initialize configuration and services
            SpannerConfig config = new SpannerConfig(projectId, instanceId, databaseId);
            DatabaseClient dbClient = config.createDatabaseClient();
            DeviceDao deviceDao = config.deviceDao();
            CommunityDao communityDao = config.communityDao();
            
            GraphCommunityDetectionService graphService = new GraphCommunityDetectionServiceImpl(
                dbClient, deviceDao, communityDao);
            
            // Clear existing data to avoid conflicts
            clearExistingData(dbClient);
            
            // Create sample devices
            List<Device> sampleDevices = createSampleDevices();
            
            // Process devices and calculate network attributes
            processDeviceNetworkAttributes(sampleDevices);
            
            // Demonstrate graph-based community detection
            demonstrateGraphCommunityDetection(graphService, sampleDevices);
            
            // Demonstrate graph querying capabilities
            demonstrateGraphQuerying(graphService);
            
            // Demonstrate graph statistics
            demonstrateGraphStatistics(graphService);
            
            // Demonstrate shortest path finding
            demonstrateShortestPath(graphService);
            
            logger.info("Graph-based Community Detection Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during demo execution", e);
            System.err.println("Demo failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Create sample devices for demonstration.
     */
    private static List<Device> createSampleDevices() {
        logger.info("Creating sample devices for graph demo");
        
        List<Device> devices = Arrays.asList(
            new Device("D1", "OfficeWiFi", "192.168.1.2", "AA:BB:CC:11:22:33"),
            new Device("D2", "OfficeWiFi", "192.168.1.3", "AA:BB:CC:11:22:34"),
            new Device("D3", "HomeWiFi", "192.168.2.4", "DD:EE:FF:44:55:66"),
            new Device("D4", "OfficeWiFi", "192.168.1.10", "AA:BB:CC:11:22:35"),
            new Device("D5", "CafeWiFi", "10.0.0.5", "11:22:33:44:55:66"),
            new Device("D6", "HomeWiFi", "192.168.2.5", "DD:EE:FF:44:55:67"),
            new Device("D7", "OfficeWiFi", "192.168.1.15", "AA:BB:CC:11:22:36"),
            new Device("D8", "GuestWiFi", "172.16.0.10", "99:88:77:66:55:44"),
            new Device("D9", "GuestWiFi", "172.16.0.11", "99:88:77:66:55:45"),
            new Device("D10", "MobileHotspot", "192.168.43.1", "12:34:56:78:90:AB"),
            new Device("D11", "OfficeWiFi", "192.168.1.20", "AA:BB:CC:11:22:37"),
            new Device("D12", "HomeWiFi", "192.168.2.10", "DD:EE:FF:44:55:68")
        );
        
        logger.info("Created {} sample devices", devices.size());
        return devices;
    }
    
    /**
     * Process devices to calculate network attributes (subnet, MAC prefix).
     */
    private static void processDeviceNetworkAttributes(List<Device> devices) {
        logger.info("Processing network attributes for devices");
        
        for (Device device : devices) {
            // Calculate subnet from IP
            String subnet = NetworkUtils.getSubnet(device.getIp());
            device.setSubnet(subnet);
            
            // Calculate MAC prefix
            String macPrefix = NetworkUtils.getMacPrefix(device.getMac());
            device.setMacPrefix(macPrefix);
            
            logger.debug("Device {}: IP={}, Subnet={}, MAC={}, MAC_Prefix={}", 
                        device.getDeviceId(), device.getIp(), device.getSubnet(), 
                        device.getMac(), device.getMacPrefix());
        }
    }
    
    /**
     * Clear existing data from the database to avoid conflicts.
     */
    private static void clearExistingData(DatabaseClient dbClient) {
        logger.info("Clearing existing data from database...");
        
        try {
            // Delete in reverse order of dependencies
            dbClient.write(Arrays.asList(
                Mutation.delete("communities", KeySet.all()),
                Mutation.delete("mac_connections", KeySet.all()),
                Mutation.delete("subnet_connections", KeySet.all()),
                Mutation.delete("ssid_connections", KeySet.all()),
                Mutation.delete("devices", KeySet.all())
            ));
            logger.info("Successfully cleared existing data");
        } catch (Exception e) {
            logger.warn("Failed to clear existing data (this is OK if tables are empty): {}", e.getMessage());
        }
    }
    
    /**
     * Demonstrate graph-based community detection functionality.
     */
    private static void demonstrateGraphCommunityDetection(GraphCommunityDetectionService service, List<Device> devices) {
        logger.info("=== Graph-based Community Detection Demo ===");
        
        // Detect and store communities using graph algorithms
        Map<String, List<String>> communities = service.detectAndStoreCommunitiesWithGraph(devices);
        
        System.out.println("\n=== Graph-based Detected Communities ===");
        int communityIndex = 1;
        for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
            System.out.printf("Graph Community %d (ID: %s): %s%n", 
                            communityIndex, entry.getKey(), entry.getValue());
            communityIndex++;
        }
        
        // Demonstrate individual graph detection methods
        System.out.println("\n=== Graph-based Community Detection by SSID ===");
        Map<String, List<String>> ssidCommunities = service.detectCommunitiesBySsidGraph();
        for (Map.Entry<String, List<String>> entry : ssidCommunities.entrySet()) {
            System.out.printf("SSID Graph Community %s: %s%n", entry.getKey(), entry.getValue());
        }
        
        System.out.println("\n=== Graph-based Community Detection by Subnet ===");
        Map<String, List<String>> subnetCommunities = service.detectCommunitiesBySubnetGraph();
        for (Map.Entry<String, List<String>> entry : subnetCommunities.entrySet()) {
            System.out.printf("Subnet Graph Community %s: %s%n", entry.getKey(), entry.getValue());
        }
        
        System.out.println("\n=== Graph-based Community Detection by MAC Prefix ===");
        Map<String, List<String>> macCommunities = service.detectCommunitiesByMacPrefixGraph();
        for (Map.Entry<String, List<String>> entry : macCommunities.entrySet()) {
            System.out.printf("MAC Prefix Graph Community %s: %s%n", entry.getKey(), entry.getValue());
        }
        
        System.out.println("\n=== Connected Components Graph Algorithm ===");
        Map<String, List<String>> connectedComponents = service.detectAllCommunitiesWithConnectedComponents();
        for (Map.Entry<String, List<String>> entry : connectedComponents.entrySet()) {
            System.out.printf("Connected Component %s: %s%n", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Demonstrate graph-based querying capabilities.
     */
    private static void demonstrateGraphQuerying(GraphCommunityDetectionService service) {
        logger.info("=== Graph Querying Demo ===");
        
        System.out.println("\n=== Communities for Device D1 (Graph Traversal) ===");
        List<Community> deviceCommunities = service.getCommunitiesForDeviceGraph("D1");
        for (Community community : deviceCommunities) {
            System.out.printf("Community: %s (Type: %s, Size: %d, Devices: %s)%n", 
                            community.getCommunityId(), 
                            community.getCommunityType(), 
                            community.getSize(),
                            community.getDeviceIds());
        }
        
        System.out.println("\n=== Devices in First Community (Graph Traversal) ===");
        if (!deviceCommunities.isEmpty()) {
            String firstCommunityId = deviceCommunities.get(0).getCommunityId();
            List<Device> devicesInCommunity = service.getDevicesInCommunityGraph(firstCommunityId);
            for (Device device : devicesInCommunity) {
                System.out.printf("Device: %s (SSID: %s, IP: %s, MAC: %s)%n", 
                                device.getDeviceId(), device.getSsid(), 
                                device.getIp(), device.getMac());
            }
        }
    }
    
    /**
     * Demonstrate graph-based statistics functionality.
     */
    private static void demonstrateGraphStatistics(GraphCommunityDetectionService service) {
        logger.info("=== Graph Statistics Demo ===");
        
        System.out.println("\n=== Graph-based Community Statistics ===");
        Map<String, Integer> stats = service.getCommunityStatisticsGraph();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            System.out.printf("Community Type: %s, Count: %d%n", entry.getKey(), entry.getValue());
        }
        
        // Show total communities
        int totalCommunities = stats.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("Total Graph Communities: %d%n", totalCommunities);
    }
    
    /**
     * Demonstrate shortest path finding using graph traversal.
     */
    private static void demonstrateShortestPath(GraphCommunityDetectionService service) {
        logger.info("=== Shortest Path Demo ===");
        
        System.out.println("\n=== Shortest Path Between Devices ===");
        
        // Test shortest path between devices in different communities
        List<String> path1 = service.findShortestPath("D1", "D4");
        System.out.printf("Shortest path from D1 to D4: %s%n", path1);
        
        List<String> path2 = service.findShortestPath("D1", "D3");
        System.out.printf("Shortest path from D1 to D3: %s%n", path2);
        
        List<String> path3 = service.findShortestPath("D1", "D10");
        System.out.printf("Shortest path from D1 to D10: %s%n", path3);
        
        // Test path within the same community
        List<String> path4 = service.findShortestPath("D1", "D2");
        System.out.printf("Shortest path from D1 to D2: %s%n", path4);
    }
    
    /**
     * Print usage instructions.
     */
    private static void printUsage() {
        System.out.println("Graph-based Community Detection Demo");
        System.out.println("====================================");
        System.out.println("This demo requires Google Cloud Spanner with Graph capabilities.");
        System.out.println("Set the following system properties:");
        System.out.println("  -Dspanner.project.id=your-project-id");
        System.out.println("  -Dspanner.instance.id=your-instance-id");
        System.out.println("  -Dspanner.database.id=your-database-id");
        System.out.println();
        System.out.println("Make sure the database exists and the graph schema has been created.");
        System.out.println("See src/main/resources/graph-schema.sql for the required schema.");
    }
}
