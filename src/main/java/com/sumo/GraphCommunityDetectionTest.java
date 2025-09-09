package com.sumo;

import com.sumo.entity.Device;
import com.sumo.util.NetworkUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Test class to demonstrate graph-based community detection logic without requiring Spanner setup.
 * This class shows how graph algorithms would work for community detection.
 */
public class GraphCommunityDetectionTest {
    
    public static void main(String[] args) {
        System.out.println("=== Graph-based Community Detection Test (No Database) ===\n");
        
        // Create sample devices
        List<Device> devices = createSampleDevices();
        
        // Process network attributes
        processDeviceNetworkAttributes(devices);
        
        // Display device information
        displayDevices(devices);
        
        // Demonstrate graph-based community detection
        demonstrateGraphCommunityDetection(devices);
        
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
            new Device("D10", "MobileHotspot", "192.168.43.1", "12:34:56:78:90:AB"),
            new Device("D11", "OfficeWiFi", "192.168.1.20", "AA:BB:CC:11:22:37"),
            new Device("D12", "HomeWiFi", "192.168.2.10", "DD:EE:FF:44:55:68")
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
        System.out.println("-".repeat(90));
        
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
    
    private static void demonstrateGraphCommunityDetection(List<Device> devices) {
        System.out.println("=== Graph-based Community Detection Results ===");
        
        // Build graph representation
        Graph graph = buildGraph(devices);
        
        // Demonstrate different graph-based community detection methods
        System.out.println("\n=== Graph-based Community Detection by SSID ===");
        Map<String, List<String>> ssidCommunities = detectCommunitiesByAttributeGraph(devices, Device::getSsid, "SSID");
        displayGraphCommunities(ssidCommunities, "SSID");
        
        System.out.println("\n=== Graph-based Community Detection by Subnet ===");
        Map<String, List<String>> subnetCommunities = detectCommunitiesByAttributeGraph(devices, Device::getSubnet, "SUBNET");
        displayGraphCommunities(subnetCommunities, "Subnet");
        
        System.out.println("\n=== Graph-based Community Detection by MAC Prefix ===");
        Map<String, List<String>> macCommunities = detectCommunitiesByAttributeGraph(devices, Device::getMacPrefix, "MAC_PREFIX");
        displayGraphCommunities(macCommunities, "MAC Prefix");
        
        System.out.println("\n=== Connected Components Graph Algorithm ===");
        Map<String, List<String>> connectedComponents = findConnectedComponents(graph);
        displayGraphCommunities(connectedComponents, "Connected Component");
        
        System.out.println("\n=== Graph Traversal Examples ===");
        demonstrateGraphTraversal(graph);
    }
    
    /**
     * Build a graph representation of device relationships.
     */
    private static Graph buildGraph(List<Device> devices) {
        Graph graph = new Graph();
        
        // Add all devices as vertices
        for (Device device : devices) {
            graph.addVertex(device.getDeviceId());
        }
        
        // Add edges based on shared attributes
        for (int i = 0; i < devices.size(); i++) {
            Device device1 = devices.get(i);
            for (int j = i + 1; j < devices.size(); j++) {
                Device device2 = devices.get(j);
                
                // SSID connection
                if (device1.getSsid() != null && device1.getSsid().equals(device2.getSsid())) {
                    graph.addEdge(device1.getDeviceId(), device2.getDeviceId(), "SSID", device1.getSsid());
                }
                
                // Subnet connection
                if (device1.getSubnet() != null && device1.getSubnet().equals(device2.getSubnet())) {
                    graph.addEdge(device1.getDeviceId(), device2.getDeviceId(), "SUBNET", device1.getSubnet());
                }
                
                // MAC prefix connection
                if (device1.getMacPrefix() != null && device1.getMacPrefix().equals(device2.getMacPrefix())) {
                    graph.addEdge(device1.getDeviceId(), device2.getDeviceId(), "MAC_PREFIX", device1.getMacPrefix());
                }
            }
        }
        
        return graph;
    }
    
    /**
     * Detect communities by attribute using graph-based approach.
     */
    private static Map<String, List<String>> detectCommunitiesByAttributeGraph(List<Device> devices, 
                                                                              java.util.function.Function<Device, String> attributeExtractor,
                                                                              String communityType) {
        Map<String, List<Device>> groups = groupDevicesByAttribute(devices, attributeExtractor);
        Map<String, List<String>> communities = new HashMap<>();
        
        int communityIndex = 1;
        for (Map.Entry<String, List<Device>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String communityId = communityType + "_GRAPH_" + communityIndex;
                List<String> deviceIds = entry.getValue().stream()
                    .map(Device::getDeviceId)
                    .collect(Collectors.toList());
                communities.put(communityId, deviceIds);
                communityIndex++;
            }
        }
        
        return communities;
    }
    
    /**
     * Group devices by a specific attribute.
     */
    private static Map<String, List<Device>> groupDevicesByAttribute(List<Device> devices, 
                                                                     java.util.function.Function<Device, String> attributeExtractor) {
        Map<String, List<Device>> groups = new HashMap<>();
        
        for (Device device : devices) {
            String attribute = attributeExtractor.apply(device);
            if (attribute != null && !attribute.isEmpty()) {
                groups.computeIfAbsent(attribute, k -> new ArrayList<>()).add(device);
            }
        }
        
        return groups;
    }
    
    /**
     * Find connected components using graph traversal.
     */
    private static Map<String, List<String>> findConnectedComponents(Graph graph) {
        Map<String, List<String>> components = new HashMap<>();
        Set<String> visited = new HashSet<>();
        
        for (String vertex : graph.getVertices()) {
            if (!visited.contains(vertex)) {
                List<String> component = new ArrayList<>();
                dfs(vertex, graph, visited, component);
                
                if (component.size() > 1) {
                    String componentId = "GRAPH_COMPONENT_" + (components.size() + 1);
                    components.put(componentId, component);
                }
            }
        }
        
        return components;
    }
    
    /**
     * Depth-first search for connected components.
     */
    private static void dfs(String vertex, Graph graph, Set<String> visited, List<String> component) {
        visited.add(vertex);
        component.add(vertex);
        
        for (String neighbor : graph.getNeighbors(vertex)) {
            if (!visited.contains(neighbor)) {
                dfs(neighbor, graph, visited, component);
            }
        }
    }
    
    /**
     * Display graph communities.
     */
    private static void displayGraphCommunities(Map<String, List<String>> communities, String communityType) {
        for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
            System.out.printf("  %s '%s': [%s] (%d devices)%n", 
                            communityType, entry.getKey(), 
                            String.join(", ", entry.getValue()), 
                            entry.getValue().size());
        }
    }
    
    /**
     * Demonstrate graph traversal capabilities.
     */
    private static void demonstrateGraphTraversal(Graph graph) {
        System.out.println("Graph Statistics:");
        System.out.printf("  Total Vertices: %d%n", graph.getVertices().size());
        System.out.printf("  Total Edges: %d%n", graph.getEdgeCount());
        
        System.out.println("\nNeighbors of D1:");
        List<String> neighbors = graph.getNeighbors("D1");
        System.out.printf("  D1 neighbors: %s%n", neighbors);
        
        System.out.println("\nShortest Path Examples:");
        List<String> path1 = findShortestPath(graph, "D1", "D4");
        System.out.printf("  Shortest path D1 -> D4: %s%n", path1);
        
        List<String> path2 = findShortestPath(graph, "D1", "D3");
        System.out.printf("  Shortest path D1 -> D3: %s%n", path2);
    }
    
    /**
     * Find shortest path between two vertices using BFS.
     */
    private static List<String> findShortestPath(Graph graph, String start, String end) {
        if (start.equals(end)) {
            return Arrays.asList(start);
        }
        
        Queue<String> queue = new LinkedList<>();
        Map<String, String> parent = new HashMap<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(start);
        visited.add(start);
        
        while (!queue.isEmpty()) {
            String current = queue.poll();
            
            for (String neighbor : graph.getNeighbors(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parent.put(neighbor, current);
                    queue.offer(neighbor);
                    
                    if (neighbor.equals(end)) {
                        // Reconstruct path
                        List<String> path = new ArrayList<>();
                        String node = end;
                        while (node != null) {
                            path.add(0, node);
                            node = parent.get(node);
                        }
                        return path;
                    }
                }
            }
        }
        
        return new ArrayList<>(); // No path found
    }
    
    /**
     * Simple graph implementation for demonstration.
     */
    private static class Graph {
        private Map<String, Set<String>> adjacencyList = new HashMap<>();
        private Map<String, Map<String, String>> edgeAttributes = new HashMap<>();
        private int edgeCount = 0;
        
        public void addVertex(String vertex) {
            adjacencyList.putIfAbsent(vertex, new HashSet<>());
        }
        
        public void addEdge(String from, String to, String type, String attribute) {
            addVertex(from);
            addVertex(to);
            
            adjacencyList.get(from).add(to);
            adjacencyList.get(to).add(from);
            
            edgeAttributes.computeIfAbsent(from, k -> new HashMap<>()).put(to, type + ":" + attribute);
            edgeAttributes.computeIfAbsent(to, k -> new HashMap<>()).put(from, type + ":" + attribute);
            
            edgeCount++;
        }
        
        public Set<String> getVertices() {
            return adjacencyList.keySet();
        }
        
        public List<String> getNeighbors(String vertex) {
            return new ArrayList<>(adjacencyList.getOrDefault(vertex, new HashSet<>()));
        }
        
        public int getEdgeCount() {
            return edgeCount;
        }
    }
}
