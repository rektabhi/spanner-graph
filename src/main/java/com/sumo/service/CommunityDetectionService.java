package com.sumo.service;

import com.sumo.entity.Community;
import com.sumo.entity.Device;
import com.sumo.entity.DeviceCommunity;
import java.util.List;
import java.util.Map;

/**
 * Service interface for community detection operations.
 * Defines the contract for community detection business logic.
 */
public interface CommunityDetectionService {
    
    /**
     * Detect communities based on device attributes and store them in the database.
     * @param devices list of devices to analyze
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectAndStoreCommunities(List<Device> devices);
    
    /**
     * Detect communities based on SSID grouping.
     * @param devices list of devices to analyze
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectCommunitiesBySsid(List<Device> devices);
    
    /**
     * Detect communities based on subnet grouping.
     * @param devices list of devices to analyze
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectCommunitiesBySubnet(List<Device> devices);
    
    /**
     * Detect communities based on MAC prefix grouping.
     * @param devices list of devices to analyze
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> detectCommunitiesByMacPrefix(List<Device> devices);
    
    /**
     * Get all communities for a specific device.
     * @param deviceId the device ID
     * @return list of communities the device belongs to
     */
    List<Community> getCommunitiesForDevice(String deviceId);
    
    /**
     * Get all devices in a specific community.
     * @param communityId the community ID
     * @return list of devices in the community
     */
    List<Device> getDevicesInCommunity(String communityId);
    
    /**
     * Get community statistics.
     * @return map of community type to count of communities
     */
    Map<String, Integer> getCommunityStatistics();
    
    /**
     * Rebuild all communities from existing devices in the database.
     * This will clear existing communities and rebuild them.
     * @return map of community ID to list of device IDs in that community
     */
    Map<String, List<String>> rebuildAllCommunities();
}
