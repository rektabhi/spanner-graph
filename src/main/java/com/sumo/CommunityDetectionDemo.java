package com.sumo;

import com.sumo.config.SpannerConfig;
import com.sumo.dao.CommunityDao;
import com.sumo.dao.DeviceCommunityDao;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Community;
import com.sumo.entity.Device;
import com.sumo.service.CommunityDetectionService;
import com.sumo.service.impl.CommunityDetectionServiceImpl;
import com.sumo.util.NetworkUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Demonstration class for the Community Detection System.
 * Shows how to use the Spanner-based community detection functionality.
 */
public class CommunityDetectionDemo {
    
    private static final Logger logger = LoggerFactory.getLogger(CommunityDetectionDemo.class);
    
    public static void main(String[] args) {
        logger.info("Starting Community Detection Demo");
        
        // Configuration - In a real application, these would come from environment variables or config files
        String projectId = System.getProperty("spanner.project.id", "your-project-id");
        String instanceId = System.getProperty("spanner.instance.id", "your-instance-id");
        String databaseId = System.getProperty("spanner.database.id", "community-db");
        
        try {
            // Initialize configuration and services
            SpannerConfig config = new SpannerConfig(projectId, instanceId, databaseId);
            DeviceDao deviceDao = config.deviceDao();
            CommunityDao communityDao = config.communityDao();
            DeviceCommunityDao deviceCommunityDao = config.deviceCommunityDao();
            
            CommunityDetectionService communityService = new CommunityDetectionServiceImpl(
                deviceDao, communityDao, deviceCommunityDao);
            
            // Create sample devices
            List<Device> sampleDevices = createSampleDevices();
            
            // Process devices and calculate network attributes
            processDeviceNetworkAttributes(sampleDevices);
            
            // Demonstrate community detection
            demonstrateCommunityDetection(communityService, sampleDevices);
            
            // Demonstrate querying capabilities
            demonstrateQuerying(communityService);
            
            // Demonstrate statistics
            demonstrateStatistics(communityService);
            
            logger.info("Community Detection Demo completed successfully");
            
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
        logger.info("Creating sample devices");
        
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
            new Device("D10", "MobileHotspot", "192.168.43.1", "12:34:56:78:90:AB")
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
     * Demonstrate community detection functionality.
     */
    private static void demonstrateCommunityDetection(CommunityDetectionService service, List<Device> devices) {
        logger.info("=== Community Detection Demo ===");
        
        // Detect and store communities
        Map<String, List<String>> communities = service.detectAndStoreCommunities(devices);
        
        System.out.println("\n=== Detected Communities ===");
        int communityIndex = 1;
        for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
            System.out.printf("Community %d (ID: %s): %s%n", 
                            communityIndex, entry.getKey(), entry.getValue());
            communityIndex++;
        }
        
        // Demonstrate individual detection methods
        System.out.println("\n=== Community Detection by SSID ===");
        Map<String, List<String>> ssidCommunities = service.detectCommunitiesBySsid(devices);
        for (Map.Entry<String, List<String>> entry : ssidCommunities.entrySet()) {
            System.out.printf("SSID Community %s: %s%n", entry.getKey(), entry.getValue());
        }
        
        System.out.println("\n=== Community Detection by Subnet ===");
        Map<String, List<String>> subnetCommunities = service.detectCommunitiesBySubnet(devices);
        for (Map.Entry<String, List<String>> entry : subnetCommunities.entrySet()) {
            System.out.printf("Subnet Community %s: %s%n", entry.getKey(), entry.getValue());
        }
        
        System.out.println("\n=== Community Detection by MAC Prefix ===");
        Map<String, List<String>> macCommunities = service.detectCommunitiesByMacPrefix(devices);
        for (Map.Entry<String, List<String>> entry : macCommunities.entrySet()) {
            System.out.printf("MAC Prefix Community %s: %s%n", entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Demonstrate querying capabilities.
     */
    private static void demonstrateQuerying(CommunityDetectionService service) {
        logger.info("=== Querying Demo ===");
        
        System.out.println("\n=== Communities for Device D1 ===");
        List<Community> deviceCommunities = service.getCommunitiesForDevice("D1");
        for (Community community : deviceCommunities) {
            System.out.printf("Community: %s (Type: %s, Size: %d)%n", 
                            community.getCommunityId(), 
                            community.getCommunityType(), 
                            community.getSize());
        }
        
        System.out.println("\n=== Devices in First Community ===");
        List<Community> allCommunities = service.getCommunitiesForDevice("D1");
        if (!allCommunities.isEmpty()) {
            String firstCommunityId = allCommunities.get(0).getCommunityId();
            List<Device> devicesInCommunity = service.getDevicesInCommunity(firstCommunityId);
            for (Device device : devicesInCommunity) {
                System.out.printf("Device: %s (SSID: %s, IP: %s, MAC: %s)%n", 
                                device.getDeviceId(), device.getSsid(), 
                                device.getIp(), device.getMac());
            }
        }
    }
    
    /**
     * Demonstrate statistics functionality.
     */
    private static void demonstrateStatistics(CommunityDetectionService service) {
        logger.info("=== Statistics Demo ===");
        
        System.out.println("\n=== Community Statistics ===");
        Map<String, Integer> stats = service.getCommunityStatistics();
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            System.out.printf("Community Type: %s, Count: %d%n", entry.getKey(), entry.getValue());
        }
        
        // Show total communities
        int totalCommunities = stats.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("Total Communities: %d%n", totalCommunities);
    }
    
    /**
     * Print usage instructions.
     */
    private static void printUsage() {
        System.out.println("Community Detection Demo");
        System.out.println("========================");
        System.out.println("This demo requires Google Cloud Spanner to be configured.");
        System.out.println("Set the following system properties:");
        System.out.println("  -Dspanner.project.id=your-project-id");
        System.out.println("  -Dspanner.instance.id=your-instance-id");
        System.out.println("  -Dspanner.database.id=your-database-id");
        System.out.println();
        System.out.println("Make sure the database exists and the schema has been created.");
        System.out.println("See src/main/resources/schema.sql for the required schema.");
    }
}
