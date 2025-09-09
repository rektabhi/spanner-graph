package com.sumo;

import com.google.cloud.spanner.DatabaseClient;
import com.sumo.config.SpannerConfig;
import com.sumo.dao.CommunityDao;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Device;
import com.sumo.service.GraphCommunityDetectionService;
import com.sumo.service.impl.HeterogeneousGraphCommunityDetectionServiceImpl;
import com.sumo.service.impl.OptimizedHeterogeneousGraphCommunityDetectionServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Demo showing the fixed heterogeneous graph implementation that handles duplicate attribute values.
 * This demo creates devices with shared attribute values to test the duplicate row fix.
 */
public class FixedHeterogeneousGraphDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(FixedHeterogeneousGraphDemo.class);
    
    public static void main(String[] args) {
        logger.info("Starting Fixed Heterogeneous Graph Demo");
        
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
            
            // Test with INSERT OR IGNORE approach
            logger.info("=== Testing INSERT OR IGNORE Approach ===");
            testInsertOrIgnoreApproach(dbClient, deviceDao, communityDao);
            
            // Test with optimized approach
            logger.info("=== Testing Optimized Approach ===");
            testOptimizedApproach(dbClient, deviceDao, communityDao);
            
            logger.info("Fixed Heterogeneous Graph Demo completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during demo execution", e);
            System.err.println("Demo failed: " + e.getMessage());
            System.exit(1);
        }
    }
    
    /**
     * Test the INSERT OR IGNORE approach with devices that share attribute values.
     */
    private static void testInsertOrIgnoreApproach(DatabaseClient dbClient, DeviceDao deviceDao, CommunityDao communityDao) {
        logger.info("Testing INSERT OR IGNORE approach with shared attribute values");
        
        // Create devices with shared attribute values to test duplicate handling
        List<Device> testDevices = createTestDevicesWithSharedAttributes();
        
        // Use the INSERT OR IGNORE implementation
        GraphCommunityDetectionService service = new HeterogeneousGraphCommunityDetectionServiceImpl(
            dbClient, deviceDao, communityDao);
        
        try {
            // This should not fail even with duplicate attribute values
            Map<String, List<String>> communities = service.detectAndStoreCommunitiesWithGraph(testDevices);
            logger.info("INSERT OR IGNORE approach succeeded! Found {} communities", communities.size());
            
            // Show the communities found
            for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
                logger.info("Community {}: {} devices - {}", 
                    entry.getKey(), entry.getValue().size(), entry.getValue());
            }
            
        } catch (Exception e) {
            logger.error("INSERT OR IGNORE approach failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Test the optimized approach with devices that share attribute values.
     */
    private static void testOptimizedApproach(DatabaseClient dbClient, DeviceDao deviceDao, CommunityDao communityDao) {
        logger.info("Testing optimized approach with shared attribute values");
        
        // Create devices with shared attribute values to test duplicate handling
        List<Device> testDevices = createTestDevicesWithSharedAttributes();
        
        // Use the optimized implementation
        GraphCommunityDetectionService service = new OptimizedHeterogeneousGraphCommunityDetectionServiceImpl(
            dbClient, deviceDao, communityDao);
        
        try {
            // This should not fail even with duplicate attribute values
            Map<String, List<String>> communities = service.detectAndStoreCommunitiesWithGraph(testDevices);
            logger.info("Optimized approach succeeded! Found {} communities", communities.size());
            
            // Show the communities found
            for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
                logger.info("Community {}: {} devices - {}", 
                    entry.getKey(), entry.getValue().size(), entry.getValue());
            }
            
        } catch (Exception e) {
            logger.error("Optimized approach failed: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Create test devices with shared attribute values to test duplicate handling.
     */
    private static List<Device> createTestDevicesWithSharedAttributes() {
        logger.info("Creating test devices with shared attribute values");
        
        Instant now = Instant.now();
        
        // Create devices that share attribute values to test duplicate handling
        List<Device> devices = Arrays.asList(
            // Group 1: Same SSID and subnet
            new Device("TEST_D1", "HomeWiFi", "192.168.1.100", "00:23:12:34:56:78"),
            new Device("TEST_D2", "HomeWiFi", "192.168.1.101", "00:23:12:34:56:79"), // Same SSID, same MAC prefix
            new Device("TEST_D3", "HomeWiFi", "192.168.1.102", "00:23:12:34:56:80"), // Same SSID, same MAC prefix
            
            // Group 2: Same subnet, different SSID
            new Device("TEST_D4", "OfficeWiFi", "192.168.1.103", "AA:BB:CC:DD:EE:FF"),
            new Device("TEST_D5", "OfficeWiFi", "192.168.1.104", "AA:BB:CC:DD:EE:00"), // Same SSID, same MAC prefix
            
            // Group 3: Same MAC prefix, different SSID and subnet
            new Device("TEST_D6", "GuestWiFi", "10.0.0.100", "00:23:12:34:56:81"), // Same MAC prefix as Group 1
            new Device("TEST_D7", "GuestWiFi", "10.0.0.101", "11:22:33:44:55:66"),
            
            // Group 4: Same IP subnet (different from others)
            new Device("TEST_D8", "PublicWiFi", "172.16.0.100", "FF:FF:FF:FF:FF:FF"),
            new Device("TEST_D9", "PublicWiFi", "172.16.0.101", "EE:EE:EE:EE:EE:EE")
        );
        
        // Set created_at for all devices
        for (Device device : devices) {
            device.setCreatedAt(now);
        }
        
        logger.info("Created {} test devices with shared attribute values:", devices.size());
        for (Device device : devices) {
            logger.info("  Device {}: SSID={}, IP={}, MAC={}", 
                device.getDeviceId(), device.getSsid(), device.getIp(), device.getMac());
        }
        
        return devices;
    }
}
