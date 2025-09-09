package com.sumo.dao.spanner;

import com.google.cloud.spanner.*;
import com.google.cloud.Timestamp;
import com.sumo.dao.DeviceCommunityDao;
import com.sumo.entity.DeviceCommunity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Spanner implementation of DeviceCommunityDao.
 * Provides database operations for DeviceCommunity relationship entities using Google Cloud Spanner.
 */
public class SpannerDeviceCommunityDao implements DeviceCommunityDao {
    
    private static final Logger logger = LoggerFactory.getLogger(SpannerDeviceCommunityDao.class);
    
    private final DatabaseClient dbClient;
    
    public SpannerDeviceCommunityDao(DatabaseClient dbClient) {
        this.dbClient = dbClient;
    }
    
    @Override
    public DeviceCommunity save(DeviceCommunity deviceCommunity) {
        logger.debug("Saving device-community relationship: {} -> {}", 
                    deviceCommunity.getDeviceId(), deviceCommunity.getCommunityId());
        
        deviceCommunity.setUpdatedAt(Instant.now());
        if (deviceCommunity.getJoinedAt() == null) {
            deviceCommunity.setJoinedAt(Instant.now());
        }
        
        dbClient.write(List.of(
            Mutation.newInsertBuilder("device_communities")
                .set("device_id").to(deviceCommunity.getDeviceId())
                .set("community_id").to(deviceCommunity.getCommunityId())
                .set("joined_at").to(Timestamp.ofTimeMicroseconds(deviceCommunity.getJoinedAt().toEpochMilli() * 1000))
                .set("updated_at").to(Timestamp.ofTimeMicroseconds(deviceCommunity.getUpdatedAt().toEpochMilli() * 1000))
                .build()
        ));
        
        return deviceCommunity;
    }
    
    @Override
    public List<DeviceCommunity> saveAll(List<DeviceCommunity> deviceCommunities) {
        logger.debug("Saving {} device-community relationships", deviceCommunities.size());
        
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        for (DeviceCommunity deviceCommunity : deviceCommunities) {
            deviceCommunity.setUpdatedAt(now);
            if (deviceCommunity.getJoinedAt() == null) {
                deviceCommunity.setJoinedAt(now);
            }
            
            mutations.add(
                Mutation.newInsertBuilder("device_communities")
                    .set("device_id").to(deviceCommunity.getDeviceId())
                    .set("community_id").to(deviceCommunity.getCommunityId())
                    .set("joined_at").to(Timestamp.ofTimeMicroseconds(deviceCommunity.getJoinedAt().toEpochMilli() * 1000))
                    .set("updated_at").to(Timestamp.ofTimeMicroseconds(deviceCommunity.getUpdatedAt().toEpochMilli() * 1000))
                    .build()
            );
        }
        
        dbClient.write(mutations);
        return deviceCommunities;
    }
    
    @Override
    public List<DeviceCommunity> findByDeviceId(String deviceId) {
        logger.debug("Finding device-community relationships by device ID: {}", deviceId);
        
        List<DeviceCommunity> relationships = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM device_communities WHERE device_id = @deviceId")
                .bind("deviceId").to(deviceId)
                .build()
        )) {
            while (resultSet.next()) {
                relationships.add(mapResultSetToDeviceCommunity(resultSet));
            }
        }
        
        return relationships;
    }
    
    @Override
    public List<DeviceCommunity> findByCommunityId(String communityId) {
        logger.debug("Finding device-community relationships by community ID: {}", communityId);
        
        List<DeviceCommunity> relationships = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM device_communities WHERE community_id = @communityId")
                .bind("communityId").to(communityId)
                .build()
        )) {
            while (resultSet.next()) {
                relationships.add(mapResultSetToDeviceCommunity(resultSet));
            }
        }
        
        return relationships;
    }
    
    @Override
    public List<DeviceCommunity> findAll() {
        logger.debug("Finding all device-community relationships");
        
        List<DeviceCommunity> relationships = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.of("SELECT * FROM device_communities ORDER BY joined_at DESC")
        )) {
            while (resultSet.next()) {
                relationships.add(mapResultSetToDeviceCommunity(resultSet));
            }
        }
        
        return relationships;
    }
    
    @Override
    public boolean deleteByDeviceIdAndCommunityId(String deviceId, String communityId) {
        logger.debug("Deleting device-community relationship: {} -> {}", deviceId, communityId);
        
        try {
            dbClient.write(List.of(
                Mutation.delete("device_communities", Key.of(deviceId, communityId))
            ));
            return true;
        } catch (Exception e) {
            logger.error("Error deleting device-community relationship {} -> {}: {}", 
                        deviceId, communityId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public int deleteByDeviceId(String deviceId) {
        logger.debug("Deleting all device-community relationships for device: {}", deviceId);
        
        try {
            // First, get all relationships for this device
            List<DeviceCommunity> relationships = findByDeviceId(deviceId);
            
            if (relationships.isEmpty()) {
                return 0;
            }
            
            // Delete all relationships
            List<Mutation> mutations = new ArrayList<>();
            for (DeviceCommunity relationship : relationships) {
                mutations.add(
                    Mutation.delete("device_communities", 
                                  Key.of(relationship.getDeviceId(), relationship.getCommunityId()))
                );
            }
            
            dbClient.write(mutations);
            return relationships.size();
        } catch (Exception e) {
            logger.error("Error deleting device-community relationships for device {}: {}", 
                        deviceId, e.getMessage());
            return 0;
        }
    }
    
    @Override
    public int deleteByCommunityId(String communityId) {
        logger.debug("Deleting all device-community relationships for community: {}", communityId);
        
        try {
            // First, get all relationships for this community
            List<DeviceCommunity> relationships = findByCommunityId(communityId);
            
            if (relationships.isEmpty()) {
                return 0;
            }
            
            // Delete all relationships
            List<Mutation> mutations = new ArrayList<>();
            for (DeviceCommunity relationship : relationships) {
                mutations.add(
                    Mutation.delete("device_communities", 
                                  Key.of(relationship.getDeviceId(), relationship.getCommunityId()))
                );
            }
            
            dbClient.write(mutations);
            return relationships.size();
        } catch (Exception e) {
            logger.error("Error deleting device-community relationships for community {}: {}", 
                        communityId, e.getMessage());
            return 0;
        }
    }
    
    @Override
    public boolean existsByDeviceIdAndCommunityId(String deviceId, String communityId) {
        logger.debug("Checking if device-community relationship exists: {} -> {}", deviceId, communityId);
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT 1 FROM device_communities WHERE device_id = @deviceId AND community_id = @communityId LIMIT 1")
                .bind("deviceId").to(deviceId)
                .bind("communityId").to(communityId)
                .build()
        )) {
            return resultSet.next();
        }
    }
    
    private DeviceCommunity mapResultSetToDeviceCommunity(ResultSet resultSet) {
        DeviceCommunity deviceCommunity = new DeviceCommunity();
        deviceCommunity.setDeviceId(resultSet.getString("device_id"));
        deviceCommunity.setCommunityId(resultSet.getString("community_id"));
        deviceCommunity.setJoinedAt(resultSet.getTimestamp("joined_at").toSqlTimestamp().toInstant());
        deviceCommunity.setUpdatedAt(resultSet.getTimestamp("updated_at").toSqlTimestamp().toInstant());
        return deviceCommunity;
    }
}
