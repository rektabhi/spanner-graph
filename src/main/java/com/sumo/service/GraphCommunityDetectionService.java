package com.sumo.service;

import com.sumo.entity.Community;
import com.sumo.entity.Device;
import java.util.List;
import java.util.Map;

/**
 * Service interface for graph-based community detection operations.
 * Leverages Spanner's graph database capabilities for efficient community detection.
 */
public interface GraphCommunityDetectionService {
    
    /**
     * Detect communities using graph queries and store them in the database.
     * @param devices list of devices to analyze
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectAndStoreCommunitiesWithGraph(List<Device> devices);
    
    /**
     * Detect communities using SSID-based graph traversal.
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectCommunitiesBySsidGraph();
    
    /**
     * Detect communities using subnet-based graph traversal.
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectCommunitiesBySubnetGraph();
    
    /**
     * Detect communities using MAC prefix-based graph traversal.
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectCommunitiesByMacPrefixGraph();
    
    /**
     * Detect all communities using connected components graph algorithm.
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectAllCommunitiesWithConnectedComponents();
    
    /**
     * Get communities for a specific device using graph traversal.
     * @param deviceId the device ID
     * @return list of communities the device belongs to
     */
    List<Community> getCommunitiesForDeviceGraph(String deviceId);
    
    /**
     * Get devices in a specific community using graph traversal.
     * @param communityId the community ID
     * @return list of devices in the community
     */
    List<Device> getDevicesInCommunityGraph(String communityId);
    
    /**
     * Find shortest path between two devices using graph traversal.
     * @param fromDeviceId source device ID
     * @param toDeviceId target device ID
     * @return list of device IDs representing the path, or empty if no path exists
     */
    List<String> findShortestPath(String fromDeviceId, String toDeviceId);
    
    /**
     * Get community statistics using graph queries.
     * @return map of community type to count of communities
     */
    Map<String, Integer> getCommunityStatisticsGraph();
    
    /**
     * Rebuild all communities using graph algorithms.
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> rebuildAllCommunitiesWithGraph();
}
