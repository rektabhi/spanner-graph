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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Demonstration class for the Graph-based Community Detection System.
 * Shows how to use Spanner's graph database capabilities for community detection.
 */
public class GraphCommunityDetectionDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(GraphCommunityDetectionDemo.class);
    private static final String CSV_FILE_PATH = "mock_devices_1m.csv";
    private static final int BATCH_SIZE = 100; // Process devices in batches to avoid resource exhaustion
    
    // Global counters for community IDs to avoid duplicates across batches
    private static int globalSsidCommunityCounter = 1;
    private static int globalSubnetCommunityCounter = 1;
    private static int globalMacPrefixCommunityCounter = 1;
    private static int globalGraphComponentCounter = 1;
    
    public static void main(String[] args) {
        logger.info("Starting Graph-based Community Detection Demo");
        
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
            
            // Process devices in batches to avoid resource exhaustion
            processDevicesInBatches(graphService, csvFilePath, batchSize);
            
            // Perform community detection on all stored data
            logger.info("Starting final community detection on all stored data");
            performFinalCommunityDetection(graphService);
            
            // Demonstrate graph querying capabilities
            logger.info("Starting graph querying demonstrations");
            demonstrateGraphQuerying(graphService);
            
            // Demonstrate graph statistics
            logger.info("Starting graph statistics demonstrations");
            demonstrateGraphStatistics(graphService);
            
            // Demonstrate shortest path finding
            logger.info("Starting shortest path demonstrations");
            demonstrateShortestPath(graphService);
            
            logger.info("Graph-based Community Detection Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during demo execution", e);
            System.err.println("Demo failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Process devices in batches to avoid resource exhaustion.
     */
    private static void processDevicesInBatches(GraphCommunityDetectionService graphService, String csvFilePath, int batchSize) {
        logger.info("Processing devices in batches of {} to avoid resource exhaustion", batchSize);
        
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
                        processBatch(graphService, currentBatch, batchNumber);
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
                processBatch(graphService, currentBatch, batchNumber);
            }
            
            logger.info("Completed processing all devices in {} batches", batchNumber);
            
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read devices from CSV file", e);
        }
    }
    
    /**
     * Process a single batch of devices - just store devices and relationships, no community detection yet.
     */
    private static void processBatch(GraphCommunityDetectionService graphService, List<Device> batch, int batchNumber) {
        logger.info("Processing batch {} with {} devices", batchNumber, batch.size());
        
        try {
            // Process network attributes for this batch
            processDeviceNetworkAttributes(batch);
            
            // Store devices and their relationships in the database
            // We'll do community detection at the end to avoid ID conflicts
            logger.info("Storing devices and relationships for batch {} ({} devices)", batchNumber, batch.size());
            storeDevicesOnly(graphService, batch);
            
            logger.info("Batch {} completed: stored {} devices and their relationships", batchNumber, batch.size());
            
        } catch (Exception e) {
            logger.error("Error processing batch {}: {}", batchNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to process batch " + batchNumber, e);
        }
    }
    
    /**
     * Store devices and create graph relationships, handling ALREADY_EXISTS errors gracefully.
     */
    private static void storeDevicesOnly(GraphCommunityDetectionService graphService, List<Device> devices) {
        logger.debug("Storing {} devices with graph relationships", devices.size());
        
        try {
            // Use the existing method but handle ALREADY_EXISTS errors
            Map<String, List<String>> communities = graphService.detectAndStoreCommunitiesWithGraph(devices);
            logger.debug("Successfully stored {} devices and created {} communities", devices.size(), communities.size());
            
        } catch (Exception e) {
            // Check if this is an ALREADY_EXISTS error for communities
            if (e.getMessage() != null && e.getMessage().contains("ALREADY_EXISTS")) {
                logger.debug("Community already exists (expected for subsequent batches), continuing...");
                // This is expected behavior for subsequent batches, so we continue
            } else {
                logger.error("Unexpected error storing devices: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to store devices", e);
            }
        }
    }
    
    /**
     * Perform final community detection on all stored data.
     */
    private static void performFinalCommunityDetection(GraphCommunityDetectionService graphService) {
        logger.info("Performing final community detection on all stored data");
        
        try {
            // Use the rebuild method to detect all communities from the stored data
            logger.info("Rebuilding all communities from stored graph data");
            Map<String, List<String>> allCommunities = graphService.rebuildAllCommunitiesWithGraph();
            logger.info("Final community detection completed: {} total communities detected", allCommunities.size());
            
            // Log some statistics
            int totalDevicesInCommunities = allCommunities.values().stream()
                .mapToInt(List::size)
                .sum();
            logger.info("Total devices in communities: {}", totalDevicesInCommunities);
            
        } catch (Exception e) {
            logger.error("Error during final community detection: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to perform final community detection", e);
        }
    }
    
    /**
     * Read devices from CSV file for demonstration.
     */
    private static List<Device> readDevicesFromCSV(String csvFilePath) {
        logger.info("Reading devices from CSV file: {}", csvFilePath);
        
        List<Device> devices = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean isFirstLine = true;
            int lineCount = 0;
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // Parse CSV line
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
                    
                    devices.add(device);
                } else {
                    logger.warn("Skipping malformed line {}: {}", lineCount, line);
                }
                
                // Progress update for large files
                if (lineCount % 10000 == 0) {
                    logger.info("Processed {} lines, loaded {} devices", lineCount, devices.size());
                }
            }
            
            logger.info("Successfully loaded {} devices from CSV file", devices.size());
            
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read devices from CSV file", e);
        }
        
        return devices;
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
        System.out.println("This demo reads device data from a CSV file and creates community graphs in Spanner.");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java com.sumo.GraphCommunityDetectionDemo [csv-file-path] [batch-size]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  csv-file-path    Path to CSV file containing device data (optional)");
        System.out.println("                   Default: mock_devices_1m.csv");
        System.out.println("  batch-size       Number of devices to process in each batch (optional)");
        System.out.println("                   Default: 1000");
        System.out.println();
        System.out.println("CSV Format:");
        System.out.println("  device_id,ssid,ip_address,mac_address,subnet,mac_prefix,created_at");
        System.out.println();
        System.out.println("Requirements:");
        System.out.println("  - Google Cloud Spanner with Graph capabilities");
        System.out.println("  - Database exists with graph schema created");
        System.out.println("  - See src/main/resources/graph-schema.sql for required schema");
        System.out.println();
        System.out.println("Configuration (hardcoded in demo):");
        System.out.println("  - Project ID: sm-apps-core");
        System.out.println("  - Instance ID: common-spanner-next");
        System.out.println("  - Database ID: communities-next");
        System.out.println();
        System.out.println("Performance Notes:");
        System.out.println("  - Large datasets are processed in batches to avoid resource exhaustion");
        System.out.println("  - A 100ms delay is added between batches to prevent overwhelming Spanner");
        System.out.println("  - For 1M devices with batch size 1000, expect ~1000 batches");
        System.out.println("  - Adjust batch size based on your Spanner instance capacity");
    }
}
