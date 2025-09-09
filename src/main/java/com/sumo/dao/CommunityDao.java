package com.sumo.dao;

import com.sumo.entity.Community;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Community operations.
 * Defines the contract for community-related database operations.
 */
public interface CommunityDao {
    
    /**
     * Save a community to the database.
     * @param community the community to save
     * @return the saved community
     */
    Community save(Community community);
    
    /**
     * Save multiple communities in a batch operation.
     * @param communities list of communities to save
     * @return list of saved communities
     */
    List<Community> saveAll(List<Community> communities);
    
    /**
     * Find a community by its ID.
     * @param communityId the community ID
     * @return Optional containing the community if found
     */
    Optional<Community> findById(String communityId);
    
    /**
     * Find all communities.
     * @return list of all communities
     */
    List<Community> findAll();
    
    /**
     * Find communities by root device ID.
     * @param rootDeviceId the root device ID
     * @return list of communities with the given root device
     */
    List<Community> findByRootDeviceId(String rootDeviceId);
    
    /**
     * Find communities by type.
     * @param communityType the community type (SSID, SUBNET, MAC_PREFIX, MIXED)
     * @return list of communities of the given type
     */
    List<Community> findByType(String communityType);
    
    /**
     * Find communities with size greater than or equal to the given size.
     * @param minSize the minimum community size
     * @return list of communities with size >= minSize
     */
    List<Community> findByMinSize(int minSize);
    
    /**
     * Update a community.
     * @param community the community to update
     * @return the updated community
     */
    Community update(Community community);
    
    /**
     * Delete a community by its ID.
     * @param communityId the community ID to delete
     * @return true if the community was deleted, false otherwise
     */
    boolean deleteById(String communityId);
    
    /**
     * Check if a community exists by its ID.
     * @param communityId the community ID to check
     * @return true if the community exists, false otherwise
     */
    boolean existsById(String communityId);
}
