package com.sumo;

import java.util.*;
import java.util.function.BiConsumer;

public class Community {
    static class Device {
        String deviceId;
        String ssid;
        String ip;
        String mac;

        public Device(String deviceId, String ssid, String ip, String mac) {
            this.deviceId = deviceId;
            this.ssid = ssid;
            this.ip = ip;
            this.mac = mac;
        }
    }

    // Utility to get /24 subnet from IP (assuming IPv4)
    private static String getSubnet(String ip) {
        if (ip == null || ip.isEmpty() || !ip.contains(".")) return null;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return null;
        return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
    }

    // Utility to get MAC prefix (first 3 octets as "AA:BB:CC")
    private static String getMacPrefix(String mac) {
        if (mac == null) return null;
        // Remove non-hex characters (colons, dashes, spaces)
        String cleaned = mac.replaceAll("[^0-9A-Fa-f]", "");
        if (cleaned.length() < 6) return null;
        cleaned = cleaned.substring(0, 6).toUpperCase();
        return cleaned.substring(0, 2) + ":" + cleaned.substring(2, 4) + ":" + cleaned.substring(4, 6);
    }

    // Disjoint Set Union-Find structure for clustering
    static class UnionFind {
        private Map<String, String> parent = new HashMap<>();
        private Map<String, Integer> size = new HashMap<>();

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
        public int getCommunitySize(String deviceId) {
            String root = find(deviceId);
            return size.getOrDefault(root, 0);
        }
    }
    // Will add risk scores eventually
   /* public int getRiskScore(String deviceId){
        int iRisk = 0;
        return iRisk;
    }
    */
    public static void main(String[] args) {
        // For testing
        List<Device> devices = Arrays.asList(
                new Device("D1", "OfficeWiFi", "192.168.1.2", "AA:BB:CC:11:22:33"),
                new Device("D2", "OfficeWiFi", "192.168.1.3", "AA:BB:CC:11:22:34"),
                new Device("D3", "HomeWiFi", "192.168.2.4", "DD:EE:FF:44:55:66"),
                new Device("D4", "OfficeWiFi", "192.168.1.10", "AA:BB:CC:11:22:35"),
                new Device("D5", "CafeWiFi", "10.0.0.5", "11:22:33:44:55:66")
        );

        UnionFind uf = new UnionFind();


        for (Device d : devices) {
            uf.add(d.deviceId);
        }

        // Grouping
        Map<String, List<Device>> ssidGroups = new HashMap<>();
        Map<String, List<Device>> subnetGroups = new HashMap<>();
        Map<String, List<Device>> macPrefixGroups = new HashMap<>();

        for (Device d : devices) {
            String subnet = getSubnet(d.ip);
            if (subnet != null) {
                subnetGroups.computeIfAbsent(subnet, k -> new ArrayList<>()).add(d);
            }
            String macPrefix = getMacPrefix(d.mac);
            if (macPrefix != null) {
                macPrefixGroups.computeIfAbsent(macPrefix, k -> new ArrayList<>()).add(d);
            }
            if (d.ssid != null) {
                ssidGroups.computeIfAbsent(d.ssid, k -> new ArrayList<>()).add(d);
            }
        }

        // Helper to union all devices in a group
        BiConsumer<List<Device>, UnionFind> unionGroup = (list, ufInstance) -> {
            if (list == null || list.size() < 2) return;
            String firstId = list.get(0).deviceId;
            for (int i = 1; i < list.size(); i++) {
                ufInstance.union(firstId, list.get(i).deviceId);
            }
        };

        // Connecting devices with the same SSID
        for (List<Device> group : ssidGroups.values()) {
            unionGroup.accept(group, uf);
        }

        // Connect devices in the same subnet
        for (List<Device> group : subnetGroups.values()) {
            unionGroup.accept(group, uf);
        }

        // Connect devices with the same MAC prefix
        for (List<Device> group : macPrefixGroups.values()) {
            unionGroup.accept(group, uf);
        }
        // Building communities: root -> list of device IDs
        Map<String, List> communities = new HashMap<>();
        for (Device d : devices) {
            String root = uf.find(d.deviceId);
            communities.computeIfAbsent(root, k -> new ArrayList<>()).add(d.deviceId);
        }

// Print communities
        int idx = 1;
        for (Map.Entry<String, List> entry : communities.entrySet()) {
            System.out.println("Community " + idx + " (root=" + entry.getKey() + "): " + entry.getValue());
            idx++;
        }

// Print per-device community membership and size
        System.out.println("\nPer-device community sizes:");
        for (Device d : devices) {
            System.out.println(d.deviceId + " -> communityRoot=" + uf.find(d.deviceId)
                    + ", communitySize=" + uf.getCommunitySize(d.deviceId));
        }

    }
}


