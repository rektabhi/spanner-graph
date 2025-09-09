package com.sumo;

import com.sumo.entity.Device;
import com.sumo.util.NetworkUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mock device data generator for creating large datasets of device information.
 * Generates realistic device data including device IDs, SSIDs, IP addresses, and subnets.
 */
public class MockDeviceDataGenerator {
    
    // private static final int TOTAL_DEVICES = 1_000_000;
    private static final String OUTPUT_FILE = "mock_devices_1m.csv";
    
    // For testing - set to a smaller number to verify logic
    private static final int TOTAL_DEVICES = 1_000_000;
    
    // Realistic SSID patterns
    private static final String[] SSID_PREFIXES = {
        "OfficeWiFi", "HomeWiFi", "GuestWiFi", "CafeWiFi", "HotelWiFi", "AirportWiFi",
        "LibraryWiFi", "SchoolWiFi", "UniversityWiFi", "CorporateWiFi", "PublicWiFi",
        "MobileHotspot", "TP-Link", "Netgear", "Linksys", "ASUS", "D-Link", "Belkin",
        "Cisco", "Aruba", "Ubiquiti", "MikroTik", "OpenWrt", "FreeWiFi", "SecureWiFi"
    };
    
    // Common network ranges for different environments
    private static final String[] NETWORK_RANGES = {
        "192.168.1", "192.168.2", "192.168.3", "192.168.4", "192.168.5", "192.168.10",
        "192.168.11", "192.168.12", "192.168.20", "192.168.30", "192.168.40", "192.168.50",
        "10.0.0", "10.0.1", "10.0.2", "10.1.0", "10.1.1", "10.2.0", "10.10.0", "10.10.1",
        "172.16.0", "172.16.1", "172.17.0", "172.18.0", "172.20.0", "172.30.0"
    };
    
    // MAC address prefixes for different manufacturers
    private static final String[] MAC_PREFIXES = {
        "00:1B:44", "00:1C:42", "00:1D:7E", "00:1E:52", "00:1F:5B", "00:21:6A",
        "00:22:15", "00:23:12", "00:24:81", "00:25:00", "00:26:08", "00:27:19",
        "00:28:45", "00:29:15", "00:2A:10", "00:2B:67", "00:2C:44", "00:2D:76",
        "00:2E:2C", "00:2F:3A", "00:30:48", "00:31:92", "00:32:4A", "00:33:21",
        "AA:BB:CC", "DD:EE:FF", "11:22:33", "44:55:66", "77:88:99", "12:34:56",
        "78:90:AB", "CD:EF:12", "34:56:78", "9A:BC:DE", "F0:12:34", "56:78:9A"
    };
    
    private final Random random = new Random();
    private final Map<String, Integer> ssidCounts = new HashMap<>();
    private final Map<String, Integer> subnetCounts = new HashMap<>();
    
    public static void main(String[] args) {
        System.out.println("=== Mock Device Data Generator ===");
        System.out.println("Generating " + TOTAL_DEVICES + " devices...");
        
        MockDeviceDataGenerator generator = new MockDeviceDataGenerator();
        
        try {
            long startTime = System.currentTimeMillis();
            generator.generateAndWriteDevices();
            long endTime = System.currentTimeMillis();
            
            System.out.println("\n=== Generation Complete ===");
            System.out.println("Total devices generated: " + TOTAL_DEVICES);
            System.out.println("Output file: " + OUTPUT_FILE);
            System.out.println("Generation time: " + (endTime - startTime) + " ms");
            System.out.println("Average time per device: " + ((endTime - startTime) / (double) TOTAL_DEVICES) + " ms");
            
            generator.printStatistics();
            
        } catch (IOException e) {
            System.err.println("Error generating device data: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Generate and write all devices to file.
     */
    public void generateAndWriteDevices() throws IOException {
        System.out.println("Starting device generation...");
        System.out.println("TOTAL_DEVICES: " + TOTAL_DEVICES);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_FILE))) {
            // Write CSV header
            writer.write("device_id,ssid,ip_address,mac_address,subnet,mac_prefix,created_at");
            writer.newLine();
            
            // Generate devices in batches for better memory management
            int batchSize = 10000;
            int totalBatches = (TOTAL_DEVICES + batchSize - 1) / batchSize;
            
            System.out.println("Batch size: " + batchSize);
            System.out.println("Total batches: " + totalBatches);
            
            int totalGenerated = 0;
            
            for (int batch = 0; batch < totalBatches; batch++) {
                int startIndex = batch * batchSize;
                int endIndex = Math.min(startIndex + batchSize, TOTAL_DEVICES);
                
                System.out.printf("Processing batch %d: devices %d to %d%n", batch + 1, startIndex, endIndex - 1);
                
                try {
                    generateBatch(writer, startIndex, endIndex);
                    totalGenerated += (endIndex - startIndex);
                    
                    // Progress update
                    if (batch % 10 == 0 || batch == totalBatches - 1) {
                        double progress = (double) endIndex / TOTAL_DEVICES * 100;
                        System.out.printf("Progress: %.1f%% (%d/%d devices generated)%n", progress, totalGenerated, TOTAL_DEVICES);
                    }
                } catch (Exception e) {
                    System.err.printf("Error in batch %d: %s%n", batch + 1, e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
            }
            
            System.out.println("Flushing writer...");
            writer.flush();
            System.out.println("Total devices generated: " + totalGenerated);
        }
        
        // Verify the file was written correctly
        verifyOutputFile();
    }
    
    /**
     * Verify the output file contains the expected number of lines.
     */
    private void verifyOutputFile() {
        try {
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(OUTPUT_FILE));
            long lineCount = 0;
            while (reader.readLine() != null) {
                lineCount++;
            }
            reader.close();
            
            System.out.println("Verification: Output file contains " + lineCount + " lines (including header)");
            System.out.println("Expected: " + (TOTAL_DEVICES + 1) + " lines (including header)");
            
            if (lineCount == TOTAL_DEVICES + 1) {
                System.out.println("✓ File verification successful!");
            } else {
                System.out.println("✗ File verification failed! Expected " + (TOTAL_DEVICES + 1) + " lines but found " + lineCount);
            }
        } catch (IOException e) {
            System.err.println("Error verifying output file: " + e.getMessage());
        }
    }
    
    /**
     * Generate a batch of devices and write them to the file.
     */
    private void generateBatch(BufferedWriter writer, int startIndex, int endIndex) throws IOException {
        int devicesInBatch = 0;
        for (int i = startIndex; i < endIndex; i++) {
            try {
                Device device = generateDevice(i);
                writeDeviceToFile(writer, device);
                updateStatistics(device);
                devicesInBatch++;
            } catch (Exception e) {
                System.err.printf("Error generating device %d: %s%n", i, e.getMessage());
                throw e;
            }
        }
        System.out.printf("  Generated %d devices in this batch%n", devicesInBatch);
    }
    
    /**
     * Generate a single device with realistic data.
     */
    private Device generateDevice(int index) {
        String deviceId = generateDeviceId(index);
        String ssid = generateSSID();
        String ip = generateIPAddress();
        String mac = generateMACAddress();
        
        Device device = new Device(deviceId, ssid, ip, mac);
        
        // Calculate subnet and MAC prefix
        device.setSubnet(NetworkUtils.getSubnet(ip));
        device.setMacPrefix(NetworkUtils.getMacPrefix(mac));
        
        return device;
    }
    
    /**
     * Generate a unique device ID.
     */
    private String generateDeviceId(int index) {
        return String.format("DEV_%07d", index + 1);
    }
    
    /**
     * Generate a realistic SSID with some patterns.
     */
    private String generateSSID() {
        String prefix = SSID_PREFIXES[random.nextInt(SSID_PREFIXES.length)];
        
        // Add some variation to SSIDs
        if (random.nextDouble() < 0.3) {
            // Add numbers to some SSIDs
            return prefix + "_" + (random.nextInt(999) + 1);
        } else if (random.nextDouble() < 0.2) {
            // Add location suffixes
            String[] locations = {"Floor1", "Floor2", "BuildingA", "BuildingB", "North", "South", "East", "West"};
            return prefix + "_" + locations[random.nextInt(locations.length)];
        } else {
            return prefix;
        }
    }
    
    /**
     * Generate a realistic IP address.
     */
    private String generateIPAddress() {
        String networkBase = NETWORK_RANGES[random.nextInt(NETWORK_RANGES.length)];
        int hostId = random.nextInt(254) + 1; // Avoid .0 and .255
        return networkBase + "." + hostId;
    }
    
    /**
     * Generate a realistic MAC address.
     */
    private String generateMACAddress() {
        String prefix = MAC_PREFIXES[random.nextInt(MAC_PREFIXES.length)];
        
        // Generate last 3 octets
        StringBuilder mac = new StringBuilder(prefix);
        for (int i = 0; i < 3; i++) {
            mac.append(":");
            mac.append(String.format("%02X", random.nextInt(256)));
        }
        
        return mac.toString();
    }
    
    /**
     * Write device data to CSV file.
     */
    private void writeDeviceToFile(BufferedWriter writer, Device device) throws IOException {
        writer.write(device.getDeviceId());
        writer.write(",");
        writer.write(escapeCSV(device.getSsid()));
        writer.write(",");
        writer.write(device.getIp());
        writer.write(",");
        writer.write(device.getMac());
        writer.write(",");
        writer.write(device.getSubnet() != null ? device.getSubnet() : "");
        writer.write(",");
        writer.write(device.getMacPrefix() != null ? device.getMacPrefix() : "");
        writer.write(",");
        writer.write(device.getCreatedAt().toString());
        writer.newLine();
    }
    
    /**
     * Escape CSV values that contain commas or quotes.
     */
    private String escapeCSV(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
    
    /**
     * Update statistics for analysis.
     */
    private void updateStatistics(Device device) {
        ssidCounts.merge(device.getSsid(), 1, Integer::sum);
        if (device.getSubnet() != null) {
            subnetCounts.merge(device.getSubnet(), 1, Integer::sum);
        }
    }
    
    /**
     * Print generation statistics.
     */
    private void printStatistics() {
        System.out.println("\n=== Generation Statistics ===");
        System.out.println("Unique SSIDs: " + ssidCounts.size());
        System.out.println("Unique Subnets: " + subnetCounts.size());
        
        System.out.println("\nTop 10 SSIDs by device count:");
        ssidCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> System.out.printf("  %s: %d devices%n", entry.getKey(), entry.getValue()));
        
        System.out.println("\nTop 10 Subnets by device count:");
        subnetCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(10)
            .forEach(entry -> System.out.printf("  %s: %d devices%n", entry.getKey(), entry.getValue()));
        
        // Calculate some community detection metrics
        int devicesInCommunities = ssidCounts.values().stream()
            .mapToInt(count -> count > 1 ? count : 0)
            .sum();
        
        System.out.println("\nCommunity Detection Metrics:");
        System.out.printf("  Devices in SSID communities: %d (%.1f%%)%n", 
            devicesInCommunities, (devicesInCommunities / (double) TOTAL_DEVICES) * 100);
        
        int devicesInSubnetCommunities = subnetCounts.values().stream()
            .mapToInt(count -> count > 1 ? count : 0)
            .sum();
        
        System.out.printf("  Devices in subnet communities: %d (%.1f%%)%n", 
            devicesInSubnetCommunities, (devicesInSubnetCommunities / (double) TOTAL_DEVICES) * 100);
    }
}
