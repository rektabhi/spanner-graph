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
import com.sumo.service.impl.HeterogeneousGraphCommunityDetectionServiceImpl;
import com.sumo.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Demonstration class for the Heterogeneous Graph-based Community Detection System.
 * Shows how to use Spanner's graph database capabilities with devices and attributes as nodes.
 */
public class HeterogeneousGraphCommunityDetectionDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(HeterogeneousGraphCommunityDetectionDemo.class);
    private static final String CSV_FILE_PATH = "mock_devices_1m.csv";
    private static final int BATCH_SIZE = 100; // Process devices in batches to avoid resource exhaustion
    
    public static void main(String[] args) {
        logger.info("Starting Heterogeneous Graph-based Community Detection Demo");
        
        // Parse command line arguments
        String csvFilePath = CSV_FILE_PATH;
        int batchSize = BATCH_SIZE;
        
        if (args.length > 0) {
            csvFilePath = args[0];
            logger.info("Using CSV file from command line: {}", csvFilePath);
        } else {
            logger.info("Using default CSV file: {}", csvFilePath);
        }
        
        if (args.length > 1) {
            try {
                batchSize = Integer.parseInt(args[1]);
                logger.info("Using batch size from command line: {}", batchSize);
            } catch (NumberFormatException e) {
                logger.warn("Invalid batch size '{}', using default: {}", args[1], BATCH_SIZE);
            }
        } else {
            logger.info("Using default batch size: {}", batchSize);
        }
        
        // Configuration
        String projectId = "sm-apps-core";
        String instanceId = "common-spanner-next";
        String databaseId = "communities-next";
        
        try {
            // Initialize configuration and services
            SpannerConfig config = new SpannerConfig(projectId, instanceId, databaseId);
            DatabaseClient dbClient = config.createDatabaseClient();
            DeviceDao deviceDao = config.deviceDao();
            CommunityDao communityDao = config.communityDao();
            
            // Use the new heterogeneous graph service
            GraphCommunityDetectionService heterogeneousGraphService = new HeterogeneousGraphCommunityDetectionServiceImpl(
                dbClient, deviceDao, communityDao);
            
            // Clear existing data to avoid conflicts
            clearExistingData(dbClient);
            
            // Process devices in batches to avoid resource exhaustion
            processDevicesInBatches(heterogeneousGraphService, csvFilePath, batchSize);
            
            // Perform community detection on all stored data using heterogeneous graph
            logger.info("Starting final heterogeneous community detection on all stored data");
            performFinalHeterogeneousCommunityDetection(heterogeneousGraphService);
            
            // Demonstrate heterogeneous graph querying capabilities
            logger.info("Starting heterogeneous graph querying demonstrations");
            demonstrateHeterogeneousGraphQuerying(heterogeneousGraphService);
            
            // Demonstrate heterogeneous graph statistics
            logger.info("Starting heterogeneous graph statistics demonstrations");
            demonstrateHeterogeneousGraphStatistics(heterogeneousGraphService);
            
            // Demonstrate shortest path finding in heterogeneous graph
            logger.info("Starting shortest path demonstrations in heterogeneous graph");
            demonstrateShortestPathInHeterogeneousGraph(heterogeneousGraphService);
            
            logger.info("Heterogeneous Graph-based Community Detection Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during demo execution", e);
            System.err.println("Demo failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Process devices in batches to avoid resource exhaustion.
     */
    private static void processDevicesInBatches(GraphCommunityDetectionService heterogeneousGraphService, String csvFilePath, int batchSize) {
        logger.info("Processing devices in batches of {} for heterogeneous graph", batchSize);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean isFirstLine = true;
            int lineCount = 0;
            int batchNumber = 1;
            List<Device> currentBatch = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // Parse CSV line and create device
                String[] fields = parseCSVLine(line);
                if (fields.length >= 7) {
                    Device device = new Device(
                        fields[0], // device_id
                        fields[1], // ssid
                        fields[2], // ip_address
                        fields[3]  // mac_address
                    );
                    
                    // Set additional fields if available
                    if (fields.length > 4 && !fields[4].isEmpty()) {
                        device.setSubnet(fields[4]); // subnet
                    }
                    if (fields.length > 5 && !fields[5].isEmpty()) {
                        device.setMacPrefix(fields[5]); // mac_prefix
                    }
                    if (fields.length > 6 && !fields[6].isEmpty()) {
                        try {
                            device.setCreatedAt(Instant.parse(fields[6])); // created_at
                        } catch (Exception e) {
                            logger.warn("Failed to parse created_at for device {}: {}", fields[0], e.getMessage());
                        }
                    }
                    
                    currentBatch.add(device);
                    
                    // Process batch when it reaches the batch size
                    if (currentBatch.size() >= batchSize) {
                        processBatch(heterogeneousGraphService, currentBatch, batchNumber);
                        currentBatch.clear();
                        batchNumber++;
                        
                        // Add a small delay between batches to prevent overwhelming Spanner
                        try {
                            Thread.sleep(100); // 100ms delay
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.warn("Batch processing interrupted");
                            break;
                        }
                    }
                } else {
                    logger.warn("Skipping malformed line {}: {}", lineCount, line);
                }
                
                // Progress update
                if (lineCount % 10000 == 0) {
                    logger.info("Processed {} lines, completed {} batches", lineCount, batchNumber - 1);
                }
            }
            
            // Process remaining devices in the last batch
            if (!currentBatch.isEmpty()) {
                processBatch(heterogeneousGraphService, currentBatch, batchNumber);
            }
            
            logger.info("Completed processing all devices in {} batches for heterogeneous graph", batchNumber);
            
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read devices from CSV file", e);
        }
    }
    
    /**
     * Process a single batch of devices for heterogeneous graph.
     * Stores devices and creates attribute nodes and edges.
     */
    private static void processBatch(GraphCommunityDetectionService heterogeneousGraphService, List<Device> batch, int batchNumber) {
        logger.info("Processing batch {} with {} devices for heterogeneous graph", batchNumber, batch.size());
        
        try {
            // Process network attributes for this batch
            processDeviceNetworkAttributes(batch);
            
            // Store devices and create heterogeneous graph nodes and edges
            logger.info("Storing devices and creating heterogeneous graph for batch {} ({} devices)", batchNumber, batch.size());
            storeDevicesWithHeterogeneousGraph(heterogeneousGraphService, batch);
            
            logger.info("Batch {} completed: stored {} devices with heterogeneous graph", batchNumber, batch.size());
            
        } catch (Exception e) {
            logger.error("Error processing batch {}: {}", batchNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to process batch " + batchNumber, e);
        }
    }
    
    /**
     * Store devices and create heterogeneous graph nodes and edges.
     */
    private static void storeDevicesWithHeterogeneousGraph(GraphCommunityDetectionService heterogeneousGraphService, List<Device> devices) {
        logger.debug("Storing {} devices with heterogeneous graph", devices.size());
        
        try {
            // Use the heterogeneous graph service to store devices and create graph
            Map<String, List<String>> communities = heterogeneousGraphService.detectAndStoreCommunitiesWithGraph(devices);
            logger.debug("Successfully stored {} devices with heterogeneous graph (communities will be detected globally)", devices.size());
            
        } catch (Exception e) {
            logger.error("Error storing devices with heterogeneous graph: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to store devices with heterogeneous graph", e);
        }
    }
    
    /**
     * Perform final community detection on all stored data using heterogeneous graph.
     */
    private static void performFinalHeterogeneousCommunityDetection(GraphCommunityDetectionService heterogeneousGraphService) {
        logger.info("Performing final heterogeneous community detection on all stored data");
        logger.info("This will build the complete heterogeneous graph with devices and attributes as nodes");
        
        try {
            // Use the rebuild method to detect all communities from the stored data
            // This method will:
            // 1. Clear existing communities and heterogeneous graph edges
            // 2. Create attribute nodes and device-attribute edges for ALL devices
            // 3. Run connected components algorithm on the complete heterogeneous graph
            // 4. Store the detected communities
            logger.info("Building complete heterogeneous graph and detecting communities from all stored data");
            Map<String, List<String>> allCommunities = heterogeneousGraphService.rebuildAllCommunitiesWithGraph();
            logger.info("Final heterogeneous community detection completed: {} total communities detected", allCommunities.size());
            
            // Log some statistics
            int totalDevicesInCommunities = allCommunities.values().stream()
                .mapToInt(List::size)
                .sum();
            logger.info("Total devices in communities: {}", totalDevicesInCommunities);
            
            // Show some example communities
            logger.info("Example heterogeneous communities found:");
            int count = 0;
            for (Map.Entry<String, List<String>> entry : allCommunities.entrySet()) {
                if (count < 5) { // Show first 5 communities
                    logger.info("  Community {}: {} devices - {}", 
                        entry.getKey(), entry.getValue().size(), entry.getValue());
                    count++;
                } else {
                    break;
                }
            }
            
        } catch (Exception e) {
            logger.error("Error during final heterogeneous community detection: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform final heterogeneous community detection", e);
        }
    }
    
    /**
     * Demonstrate heterogeneous graph-based querying capabilities.
     */
    private static void demonstrateHeterogeneousGraphQuerying(GraphCommunityDetectionService service) {
        logger.info("=== Heterogeneous Graph Querying Demo ===");
        
        System.out.println("\n=== Communities for Device D1 (Heterogeneous Graph Traversal) ===");
        List<Community> deviceCommunities = service.getCommunitiesForDeviceGraph("D1");
        for (Community community : deviceCommunities) {
            System.out.printf("Community: %s (Type: %s, Size: %d, Devices: %s)%n", 
                            community.getCommunityId(), 
                            community.getCommunityType(), 
                            community.getSize(),
                            community.getDeviceIds());
        }
        
        System.out.println("\n=== Devices in First Community (Heterogeneous Graph Traversal) ===");
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
     * Demonstrate heterogeneous graph-based statistics functionality.
     */
    private static void demonstrateHeterogeneousGraphStatistics(GraphCommunityDetectionService service) {
        logger.info("=== Heterogeneous Graph Statistics Demo ===");
        
        System.out.println("\n=== Heterogeneous Graph-based Community Statistics ===");
        Map<String, Integer> stats = service.getCommunityStatisticsGraph();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            System.out.printf("Community Type: %s, Count: %d%n", entry.getKey(), entry.getValue());
        }
        
        // Show total communities
        int totalCommunities = stats.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("Total Heterogeneous Graph Communities: %d%n", totalCommunities);
    }
    
    /**
     * Demonstrate shortest path finding using heterogeneous graph traversal.
     */
    private static void demonstrateShortestPathInHeterogeneousGraph(GraphCommunityDetectionService service) {
        logger.info("=== Shortest Path Demo in Heterogeneous Graph ===");
        
        System.out.println("\n=== Shortest Path Between Devices in Heterogeneous Graph ===");
        
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
     * Parse a CSV line, handling quoted fields and commas within quotes.
     */
    private static String[] parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // Escaped quote
                    currentField.append('"');
                    i++; // Skip next quote
                } else {
                    // Toggle quote state
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // Field separator
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // Add the last field
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }
    
    /**
     * Process devices to calculate network attributes (subnet, MAC prefix).
     * Only calculates if not already set from CSV.
     */
    private static void processDeviceNetworkAttributes(List<Device> devices) {
        logger.info("Processing network attributes for {} devices", devices.size());
        
        int processedCount = 0;
        for (Device device : devices) {
            // Calculate subnet from IP if not already set
            if (device.getSubnet() == null || device.getSubnet().isEmpty()) {
                String subnet = NetworkUtils.getSubnet(device.getIp());
                device.setSubnet(subnet);
            }
            
            // Calculate MAC prefix if not already set
            if (device.getMacPrefix() == null || device.getMacPrefix().isEmpty()) {
                String macPrefix = NetworkUtils.getMacPrefix(device.getMac());
                device.setMacPrefix(macPrefix);
            }
            
            processedCount++;
            
            // Log progress for large datasets
            if (processedCount % 10000 == 0) {
                logger.info("Processed network attributes for {} devices", processedCount);
            }
            
            logger.debug("Device {}: IP={}, Subnet={}, MAC={}, MAC_Prefix={}", 
                        device.getDeviceId(), device.getIp(), device.getSubnet(), 
                        device.getMac(), device.getMacPrefix());
        }
        
        logger.info("Completed processing network attributes for {} devices", processedCount);
    }
    
    /**
     * Clear existing data from the database to avoid conflicts.
     */
    private static void clearExistingData(DatabaseClient dbClient) {
        logger.info("Clearing existing data from database for heterogeneous graph...");
        
        try {
            // Delete in reverse order of dependencies
            dbClient.write(Arrays.asList(
                Mutation.delete("communities", KeySet.all()),
                Mutation.delete("device_ip_edges", KeySet.all()),
                Mutation.delete("device_mac_prefix_edges", KeySet.all()),
                Mutation.delete("device_subnet_edges", KeySet.all()),
                Mutation.delete("device_ssid_edges", KeySet.all()),
                Mutation.delete("ip_nodes", KeySet.all()),
                Mutation.delete("mac_prefix_nodes", KeySet.all()),
                Mutation.delete("subnet_nodes", KeySet.all()),
                Mutation.delete("ssid_nodes", KeySet.all()),
                Mutation.delete("devices", KeySet.all())
            ));
            logger.info("Successfully cleared existing data for heterogeneous graph");
        } catch (Exception e) {
            logger.warn("Failed to clear existing data (this is OK if tables are empty): {}", e.getMessage());
        }
    }
    
    /**
     * Print usage instructions.
     */
    private static void printUsage() {
        System.out.println("Heterogeneous Graph-based Community Detection Demo");
        System.out.println("==================================================");
        System.out.println("This demo reads device data from a CSV file and creates a heterogeneous graph in Spanner.");
        System.out.println("The graph includes both devices and their attributes (SSID, subnet, MAC prefix, IP) as nodes.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java com.sumo.HeterogeneousGraphCommunityDetectionDemo [csv-file-path] [batch-size]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  csv-file-path    Path to CSV file containing device data (optional)");
        System.out.println("                   Default: mock_devices_1m.csv");
        System.out.println("  batch-size       Number of devices to process in each batch (optional)");
        System.out.println("                   Default: 100");
        System.out.println();
        System.out.println("CSV Format:");
        System.out.println("  device_id,ssid,ip_address,mac_address,subnet,mac_prefix,created_at");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - Google Cloud Spanner with Graph capabilities");
        System.out.println("  - Database exists with heterogeneous graph schema created");
        System.out.println("  - See src/main/resources/heterogeneous-graph-schema.sql for required schema");
        System.out.println();
        System.out.println("Configuration (hardcoded in demo):");
        System.out.println("  - Project ID: sm-apps-core");
        System.out.println("  - Instance ID: common-spanner-next");
        System.out.println("  - Database ID: communities-next");
        System.out.println();
        System.out.println("Heterogeneous Graph Model:");
        System.out.println("  - Nodes: Devices + SSID nodes + Subnet nodes + MAC prefix nodes + IP nodes");
        System.out.println("  - Edges: Device → SSID, Device → Subnet, Device → MAC prefix, Device → IP");
        System.out.println("  - Communities: Found by traversing shared attribute nodes");
        System.out.println();
        System.out.println("Performance Notes:");
        System.out.println("  - Large datasets are processed in batches to avoid resource exhaustion");
        System.out.println("  - A 100ms delay is added between batches to prevent overwhelming Spanner");
        System.out.println("  - For 1M devices with batch size 100, expect ~10,000 batches");
        System.out.println("  - Adjust batch size based on your Spanner instance capacity");
    }
}
