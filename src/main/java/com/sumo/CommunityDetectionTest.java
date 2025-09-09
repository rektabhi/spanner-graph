package com.sumo;

import com.sumo.entity.Device;
import com.sumo.util.NetworkUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Test class to demonstrate community detection logic without requiring Spanner setup.
 * This class shows the core community detection algorithm in action.
 */
public class CommunityDetectionTest {
    
    public static void main(String[] args) {
        System.out.println("=== Community Detection Test (No Database) ===\n");
        
        // Create sample devices
        List<Device> devices = createSampleDevices();
        
        // Process network attributes
        processDeviceNetworkAttributes(devices);
        
        // Display device information
        displayDevices(devices);
        
        // Demonstrate community detection by different criteria
        demonstrateCommunityDetection(devices);
        
        System.out.println("\n=== Test Completed ===");
    }
    
    private static List<Device> createSampleDevices() {
        return Arrays.asList(
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
    }
    
    private static void processDeviceNetworkAttributes(List<Device> devices) {
        for (Device device : devices) {
            device.setSubnet(NetworkUtils.getSubnet(device.getIp()));
            device.setMacPrefix(NetworkUtils.getMacPrefix(device.getMac()));
        }
    }
    
    private static void displayDevices(List<Device> devices) {
        System.out.println("=== Device Information ===");
        System.out.printf("%-4s %-12s %-15s %-18s %-18s %-10s%n", 
                         "ID", "SSID", "IP", "MAC", "Subnet", "MAC Prefix");
        System.out.println("-".repeat(80));
        
        for (Device device : devices) {
            System.out.printf("%-4s %-12s %-15s %-18s %-18s %-10s%n",
                             device.getDeviceId(),
                             device.getSsid(),
                             device.getIp(),
                             device.getMac(),
                             device.getSubnet(),
                             device.getMacPrefix());
        }
        System.out.println();
    }
    
    private static void demonstrateCommunityDetection(List<Device> devices) {
        System.out.println("=== Community Detection Results ===");
        
        // Group by SSID
        Map<String, List<Device>> ssidGroups = groupDevicesByAttribute(devices, Device::getSsid);
        System.out.println("\nCommunities by SSID:");
        displayGroups(ssidGroups, "SSID");
        
        // Group by Subnet
        Map<String, List<Device>> subnetGroups = groupDevicesByAttribute(devices, Device::getSubnet);
        System.out.println("\nCommunities by Subnet:");
        displayGroups(subnetGroups, "Subnet");
        
        // Group by MAC Prefix
        Map<String, List<Device>> macGroups = groupDevicesByAttribute(devices, Device::getMacPrefix);
        System.out.println("\nCommunities by MAC Prefix:");
        displayGroups(macGroups, "MAC Prefix");
        
        // Combined community detection using Union-Find
        System.out.println("\n=== Combined Community Detection (Union-Find) ===");
        Map<String, List<String>> combinedCommunities = detectCombinedCommunities(devices);
        displayCombinedCommunities(combinedCommunities);
    }
    
    private static Map<String, List<Device>> groupDevicesByAttribute(List<Device> devices, 
                                                                    java.util.function.Function<Device, String> attributeExtractor) {
        return devices.stream()
            .filter(device -> attributeExtractor.apply(device) != null)
            .collect(Collectors.groupingBy(attributeExtractor));
    }
    
    private static void displayGroups(Map<String, List<Device>> groups, String groupType) {
        for (Map.Entry<String, List<Device>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String deviceIds = entry.getValue().stream()
                    .map(Device::getDeviceId)
                    .collect(Collectors.joining(", "));
                System.out.printf("  %s '%s': [%s] (%d devices)%n", 
                                groupType, entry.getKey(), deviceIds, entry.getValue().size());
            }
        }
    }
    
    private static Map<String, List<String>> detectCombinedCommunities(List<Device> devices) {
        // Simple Union-Find implementation for demonstration
        UnionFind uf = new UnionFind();
        
        // Add all devices
        for (Device device : devices) {
            uf.add(device.getDeviceId());
        }
        
        // Group by SSID and union
        Map<String, List<Device>> ssidGroups = groupDevicesByAttribute(devices, Device::getSsid);
        for (List<Device> group : ssidGroups.values()) {
            if (group.size() > 1) {
                String firstId = group.get(0).getDeviceId();
                for (int i = 1; i < group.size(); i++) {
                    uf.union(firstId, group.get(i).getDeviceId());
                }
            }
        }
        
        // Group by subnet and union
        Map<String, List<Device>> subnetGroups = groupDevicesByAttribute(devices, Device::getSubnet);
        for (List<Device> group : subnetGroups.values()) {
            if (group.size() > 1) {
                String firstId = group.get(0).getDeviceId();
                for (int i = 1; i < group.size(); i++) {
                    uf.union(firstId, group.get(i).getDeviceId());
                }
            }
        }
        
        // Group by MAC prefix and union
        Map<String, List<Device>> macGroups = groupDevicesByAttribute(devices, Device::getMacPrefix);
        for (List<Device> group : macGroups.values()) {
            if (group.size() > 1) {
                String firstId = group.get(0).getDeviceId();
                for (int i = 1; i < group.size(); i++) {
                    uf.union(firstId, group.get(i).getDeviceId());
                }
            }
        }
        
        // Build final communities
        return devices.stream()
            .collect(Collectors.groupingBy(
                device -> uf.find(device.getDeviceId()),
                Collectors.mapping(Device::getDeviceId, Collectors.toList())
            ));
    }
    
    private static void displayCombinedCommunities(Map<String, List<String>> communities) {
        int communityIndex = 1;
        for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
            if (entry.getValue().size() > 1) {
                System.out.printf("Community %d (root: %s): [%s] (%d devices)%n",
                                communityIndex, entry.getKey(), 
                                String.join(", ", entry.getValue()), 
                                entry.getValue().size());
                communityIndex++;
            }
        }
    }
    
    // Simple Union-Find implementation for demonstration
    private static class UnionFind {
        private Map<String, String> parent = new java.util.HashMap<>();
        private Map<String, Integer> size = new java.util.HashMap<>();
        
        public void add(String deviceId) {
            if (!parent.containsKey(deviceId)) {
                parent.put(deviceId, deviceId);
                size.put(deviceId, 1);
            }
        }
        
        public String find(String deviceId) {
            String p = parent.get(deviceId);
            if (p == null) return null;
            if (!p.equals(deviceId)) {
                String root = find(p);
                parent.put(deviceId, root);
                return root;
            }
            return p;
        }
        
        public void union(String id1, String id2) {
            if (id1 == null || id2 == null) return;
            String root1 = find(id1);
            String root2 = find(id2);
            if (root1 == null || root2 == null) return;
            if (root1.equals(root2)) return;
            
            int size1 = size.getOrDefault(root1, 1);
            int size2 = size.getOrDefault(root2, 1);
            
            if (size1 < size2) {
                parent.put(root1, root2);
                size.put(root2, size1 + size2);
            } else {
                parent.put(root2, root1);
                size.put(root1, size1 + size2);
            }
        }
    }
}
