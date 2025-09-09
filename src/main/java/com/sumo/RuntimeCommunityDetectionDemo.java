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
 * Demo showing runtime community detection using the heterogeneous graph model.
 * This demo reads device data from CSV file and processes it in batches,
 * then demonstrates how communities are computed on-demand rather than pre-computed.
 */
public class RuntimeCommunityDetectionDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(RuntimeCommunityDetectionDemo.class);
    private static final String CSV_FILE_PATH = "mock_devices_1m.csv";
    private static final int BATCH_SIZE = 100; // Process devices in batches to avoid resource exhaustion
    private static final int MAX_SIZE = 1000; // Maximum number of devices to process for demo purposes
    
    public static void main(String[] args) {
        logger.info("Starting Runtime Community Detection Demo");
        
        // Parse command line arguments
        String csvFilePath = CSV_FILE_PATH;
        int batchSize = BATCH_SIZE;
        int maxSize = MAX_SIZE;
        
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
        
        if (args.length > 2) {
            try {
                maxSize = Integer.parseInt(args[2]);
                logger.info("Using max size from command line: {}", maxSize);
            } catch (NumberFormatException e) {
                logger.warn("Invalid max size '{}', using default: {}", args[2], MAX_SIZE);
            }
        } else {
            logger.info("Using default max size: {}", maxSize);
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
            
            // Use the runtime community detection service
            HeterogeneousGraphCommunityDetectionServiceImpl service = new HeterogeneousGraphCommunityDetectionServiceImpl(
                dbClient, deviceDao, communityDao);
            
            // Clear existing data to avoid conflicts
            clearExistingData(dbClient);
            
            // Process devices from CSV file in batches
            logger.info("Processing devices from CSV file in batches of {} (max {} devices)", batchSize, maxSize);
            processDevicesFromCSV(service, csvFilePath, batchSize, maxSize);
            
            // Demonstrate runtime community detection on real data
            demonstrateRuntimeCommunityDetection(service);
            
            // Demonstrate additional runtime methods
            demonstrateAdditionalRuntimeMethods(service);
            
            logger.info("Runtime Community Detection Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during demo execution", e);
            System.err.println("Demo failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Process devices from CSV file in batches.
     */
    private static void processDevicesFromCSV(HeterogeneousGraphCommunityDetectionServiceImpl service, String csvFilePath, int batchSize, int maxSize) {
        logger.info("Processing devices from CSV file: {} in batches of {} (max {} devices)", csvFilePath, batchSize, maxSize);
        
        try (BufferedReader reader = new BufferedReader(new FileReader(csvFilePath))) {
            String line;
            boolean isFirstLine = true;
            int lineCount = 0;
            int batchNumber = 1;
            int totalDevicesProcessed = 0;
            List<Device> currentBatch = new ArrayList<>();
            
            while ((line = reader.readLine()) != null) {
                lineCount++;
                
                // Skip header line
                if (isFirstLine) {
                    isFirstLine = false;
                    continue;
                }
                
                // Check if we've reached the maximum number of devices to process
                if (totalDevicesProcessed >= maxSize) {
                    logger.info("Reached maximum device limit of {}, stopping processing", maxSize);
                    break;
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
                    totalDevicesProcessed++;
                    
                    // Process batch when it reaches the batch size
                    if (currentBatch.size() >= batchSize) {
                        processBatch(service, currentBatch, batchNumber);
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
                    logger.info("Processed {} lines, completed {} batches, devices processed: {}", lineCount, batchNumber - 1, totalDevicesProcessed);
                }
            }
            
            // Process remaining devices in the last batch
            if (!currentBatch.isEmpty()) {
                processBatch(service, currentBatch, batchNumber);
            }
            
            logger.info("Completed processing {} devices from CSV in {} batches (max limit: {})", totalDevicesProcessed, batchNumber, maxSize);
            
        } catch (IOException e) {
            logger.error("Error reading CSV file: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to read devices from CSV file", e);
        }
    }
    
    /**
     * Process a single batch of devices for runtime community detection.
     */
    private static void processBatch(HeterogeneousGraphCommunityDetectionServiceImpl service, List<Device> batch, int batchNumber) {
        logger.info("Processing batch {} with {} devices for runtime community detection", batchNumber, batch.size());
        
        try {
            // Process network attributes for this batch
            processDeviceNetworkAttributes(batch);
            
            // Store devices and create heterogeneous graph nodes and edges
            logger.info("Storing devices and creating heterogeneous graph for batch {} ({} devices)", batchNumber, batch.size());
            service.detectAndStoreCommunitiesWithGraph(batch);
            
            logger.info("Batch {} completed: stored {} devices with heterogeneous graph structure", batchNumber, batch.size());
            
        } catch (Exception e) {
            logger.error("Error processing batch {}: {}", batchNumber, e.getMessage(), e);
            throw new RuntimeException("Failed to process batch " + batchNumber, e);
        }
    }
    
    /**
     * Demonstrate runtime community detection for specific devices.
     */
    private static void demonstrateRuntimeCommunityDetection(HeterogeneousGraphCommunityDetectionServiceImpl service) {
        logger.info("=== Runtime Community Detection Demo ===");
        
        // Get some sample device IDs from the database for demonstration
        List<String> sampleDeviceIds = getSampleDeviceIds(service);
        
        if (sampleDeviceIds.isEmpty()) {
            logger.warn("No devices found in database for demonstration");
            return;
        }
        
        // Show communities for first few devices
        int maxDevices = Math.min(5, sampleDeviceIds.size());
        for (int i = 0; i < maxDevices; i++) {
            String deviceId = sampleDeviceIds.get(i);
            logger.info("\n--- Communities for Device {} ---", deviceId);
            
            // Get all communities for this device (computed at runtime)
            List<Community> communities = service.getCommunitiesForDeviceGraph(deviceId);
            
            if (communities.isEmpty()) {
                logger.info("No communities found for device {}", deviceId);
            } else {
                for (Community community : communities) {
                    logger.info("Community: {} (Type: {}, Size: {}, Devices: {})", 
                        community.getCommunityId(), 
                        community.getCommunityType(), 
                        community.getSize(),
                        community.getDeviceIds());
                }
            }
        }
    }
    
    /**
     * Demonstrate additional runtime methods.
     */
    private static void demonstrateAdditionalRuntimeMethods(HeterogeneousGraphCommunityDetectionServiceImpl service) {
        logger.info("\n=== Additional Runtime Methods Demo ===");
        
        // Get some sample device IDs from the database
        List<String> sampleDeviceIds = getSampleDeviceIds(service);
        
        if (sampleDeviceIds.isEmpty()) {
            logger.warn("No devices found in database for demonstration");
            return;
        }
        
        String testDeviceId = sampleDeviceIds.get(0); // Use first device for demonstration
        
        // Get all connected devices
        logger.info("\n--- All Connected Devices for {} ---", testDeviceId);
        List<String> connectedDevices = service.getAllConnectedDevices(testDeviceId);
        logger.info("Connected devices: {}", connectedDevices);
        
        // Get largest community
        logger.info("\n--- Largest Community for {} ---", testDeviceId);
        Community largestCommunity = service.getLargestCommunityForDevice(testDeviceId);
        if (largestCommunity != null) {
            logger.info("Largest community: {} (Type: {}, Size: {}, Devices: {})", 
                largestCommunity.getCommunityId(),
                largestCommunity.getCommunityType(),
                largestCommunity.getSize(),
                largestCommunity.getDeviceIds());
        } else {
            logger.info("No communities found for device {}", testDeviceId);
        }
        
        // Get community statistics
        logger.info("\n--- Community Statistics ---");
        Map<String, Integer> stats = service.getCommunityStatisticsGraph();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            logger.info("{} communities: {}", entry.getKey(), entry.getValue());
        }
        
        // Demonstrate getting devices in a specific community
        if (largestCommunity != null) {
            logger.info("\n--- Devices in Community {} ---", largestCommunity.getCommunityId());
            List<Device> devicesInCommunity = service.getDevicesInCommunityGraph(largestCommunity.getCommunityId());
            for (Device device : devicesInCommunity) {
                logger.info("Device: {} (SSID: {}, IP: {}, MAC: {})", 
                    device.getDeviceId(), device.getSsid(), device.getIp(), device.getMac());
            }
        }
    }
    
    /**
     * Get sample device IDs from the database for demonstration.
     */
    private static List<String> getSampleDeviceIds(HeterogeneousGraphCommunityDetectionServiceImpl service) {
        // This is a simplified approach - in a real implementation, you might want to query the database
        // For now, we'll return some common device ID patterns that might exist in the CSV
        List<String> sampleIds = new ArrayList<>();
        
        // Try to find devices with common patterns
        for (int i = 1; i <= 10; i++) {
            sampleIds.add("D" + i);
        }
        
        // Add some other common patterns
        sampleIds.add("device_001");
        sampleIds.add("device_002");
        sampleIds.add("DEVICE_1");
        sampleIds.add("DEVICE_2");
        
        return sampleIds;
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
        logger.info("Clearing existing data from database for runtime community detection...");
        
        try {
            // Delete in reverse order of dependencies
            dbClient.write(Arrays.asList(
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
            logger.info("Successfully cleared existing data for runtime community detection");
        } catch (Exception e) {
            logger.warn("Failed to clear existing data (this is OK if tables are empty): {}", e.getMessage());
        }
    }
}
