package com.sumo.dao;

import com.sumo.entity.DeviceCommunity;
import java.util.List;

/**
 * Data Access Object interface for DeviceCommunity relationship operations.
 * Defines the contract for device-community relationship database operations.
 */
public interface DeviceCommunityDao {
    
    /**
     * Save a device-community relationship.
     * @param deviceCommunity the relationship to save
     * @return the saved relationship
     */
    DeviceCommunity save(DeviceCommunity deviceCommunity);
    
    /**
     * Save multiple device-community relationships in a batch operation.
     * @param deviceCommunities list of relationships to save
     * @return list of saved relationships
     */
    List<DeviceCommunity> saveAll(List<DeviceCommunity> deviceCommunities);
    
    /**
     * Find all device-community relationships for a specific device.
     * @param deviceId the device ID
     * @return list of relationships for the device
     */
    List<DeviceCommunity> findByDeviceId(String deviceId);
    
    /**
     * Find all device-community relationships for a specific community.
     * @param communityId the community ID
     * @return list of relationships for the community
     */
    List<DeviceCommunity> findByCommunityId(String communityId);
    
    /**
     * Find all device-community relationships.
     * @return list of all relationships
     */
    List<DeviceCommunity> findAll();
    
    /**
     * Delete a device-community relationship.
     * @param deviceId the device ID
     * @param communityId the community ID
     * @return true if the relationship was deleted, false otherwise
     */
    boolean deleteByDeviceIdAndCommunityId(String deviceId, String communityId);
    
    /**
     * Delete all relationships for a specific device.
     * @param deviceId the device ID
     * @return number of relationships deleted
     */
    int deleteByDeviceId(String deviceId);
    
    /**
     * Delete all relationships for a specific community.
     * @param communityId the community ID
     * @return number of relationships deleted
     */
    int deleteByCommunityId(String communityId);
    
    /**
     * Check if a device-community relationship exists.
     * @param deviceId the device ID
     * @param communityId the community ID
     * @return true if the relationship exists, false otherwise
     */
    boolean existsByDeviceIdAndCommunityId(String deviceId, String communityId);
}
