package com.sumo.dao.spanner;

import com.google.cloud.spanner.*;
import com.google.cloud.Timestamp;
import com.sumo.dao.CommunityDao;
import com.sumo.entity.Community;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spanner implementation of CommunityDao.
 * Provides database operations for Community entities using Google Cloud Spanner.
 */
public class SpannerCommunityDao implements CommunityDao {
    
    private static final Logger logger = LoggerFactory.getLogger(SpannerCommunityDao.class);
    
    private final DatabaseClient dbClient;
    
    public SpannerCommunityDao(DatabaseClient dbClient) {
        this.dbClient = dbClient;
    }
    
    @Override
    public Community save(Community community) {
        logger.debug("Saving community: {}", community.getCommunityId());
        
        community.setUpdatedAt(Instant.now());
        if (community.getCreatedAt() == null) {
            community.setCreatedAt(Instant.now());
        }
        
        dbClient.write(List.of(
            Mutation.newInsertBuilder("communities")
                .set("community_id").to(community.getCommunityId())
                .set("root_device_id").to(community.getRootDeviceId())
                .set("size").to(community.getSize())
                .set("community_type").to(community.getCommunityType())
                .set("created_at").to(Timestamp.ofTimeMicroseconds(community.getCreatedAt().toEpochMilli() * 1000))
                .set("updated_at").to(Timestamp.ofTimeMicroseconds(community.getUpdatedAt().toEpochMilli() * 1000))
                .build()
        ));
        
        return community;
    }
    
    @Override
    public List<Community> saveAll(List<Community> communities) {
        logger.debug("Saving {} communities", communities.size());
        
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        for (Community community : communities) {
            community.setUpdatedAt(now);
            if (community.getCreatedAt() == null) {
                community.setCreatedAt(now);
            }
            
            mutations.add(
                Mutation.newInsertBuilder("communities")
                    .set("community_id").to(community.getCommunityId())
                    .set("root_device_id").to(community.getRootDeviceId())
                    .set("size").to(community.getSize())
                    .set("community_type").to(community.getCommunityType())
                    .set("created_at").to(Timestamp.ofTimeMicroseconds(community.getCreatedAt().toEpochMilli() * 1000))
                    .set("updated_at").to(Timestamp.ofTimeMicroseconds(community.getUpdatedAt().toEpochMilli() * 1000))
                    .build()
            );
        }
        
        dbClient.write(mutations);
        return communities;
    }
    
    @Override
    public Optional<Community> findById(String communityId) {
        logger.debug("Finding community by ID: {}", communityId);
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM communities WHERE community_id = @communityId")
                .bind("communityId").to(communityId)
                .build()
        )) {
            if (resultSet.next()) {
                return Optional.of(mapResultSetToCommunity(resultSet));
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public List<Community> findAll() {
        logger.debug("Finding all communities");
        
        List<Community> communities = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.of("SELECT * FROM communities ORDER BY created_at DESC")
        )) {
            while (resultSet.next()) {
                communities.add(mapResultSetToCommunity(resultSet));
            }
        }
        
        return communities;
    }
    
    @Override
    public List<Community> findByRootDeviceId(String rootDeviceId) {
        logger.debug("Finding communities by root device ID: {}", rootDeviceId);
        
        List<Community> communities = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM communities WHERE root_device_id = @rootDeviceId")
                .bind("rootDeviceId").to(rootDeviceId)
                .build()
        )) {
            while (resultSet.next()) {
                communities.add(mapResultSetToCommunity(resultSet));
            }
        }
        
        return communities;
    }
    
    @Override
    public List<Community> findByType(String communityType) {
        logger.debug("Finding communities by type: {}", communityType);
        
        List<Community> communities = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM communities WHERE community_type = @communityType")
                .bind("communityType").to(communityType)
                .build()
        )) {
            while (resultSet.next()) {
                communities.add(mapResultSetToCommunity(resultSet));
            }
        }
        
        return communities;
    }
    
    @Override
    public List<Community> findByMinSize(int minSize) {
        logger.debug("Finding communities with minimum size: {}", minSize);
        
        List<Community> communities = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM communities WHERE size >= @minSize ORDER BY size DESC")
                .bind("minSize").to(minSize)
                .build()
        )) {
            while (resultSet.next()) {
                communities.add(mapResultSetToCommunity(resultSet));
            }
        }
        
        return communities;
    }
    
    @Override
    public Community update(Community community) {
        logger.debug("Updating community: {}", community.getCommunityId());
        
        community.setUpdatedAt(Instant.now());
        
        dbClient.write(List.of(
            Mutation.newUpdateBuilder("communities")
                .set("community_id").to(community.getCommunityId())
                .set("root_device_id").to(community.getRootDeviceId())
                .set("size").to(community.getSize())
                .set("community_type").to(community.getCommunityType())
                .set("updated_at").to(Timestamp.ofTimeMicroseconds(community.getUpdatedAt().toEpochMilli() * 1000))
                .build()
        ));
        
        return community;
    }
    
    @Override
    public boolean deleteById(String communityId) {
        logger.debug("Deleting community: {}", communityId);
        
        try {
            dbClient.write(List.of(
                Mutation.delete("communities", Key.of(communityId))
            ));
            return true;
        } catch (Exception e) {
            logger.error("Error deleting community {}: {}", communityId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean existsById(String communityId) {
        logger.debug("Checking if community exists: {}", communityId);
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT 1 FROM communities WHERE community_id = @communityId LIMIT 1")
                .bind("communityId").to(communityId)
                .build()
        )) {
            return resultSet.next();
        }
    }
    
    private Community mapResultSetToCommunity(ResultSet resultSet) {
        Community community = new Community();
        community.setCommunityId(resultSet.getString("community_id"));
        community.setRootDeviceId(resultSet.getString("root_device_id"));
        community.setSize((int) resultSet.getLong("size"));
        community.setCommunityType(resultSet.getString("community_type"));
        community.setCreatedAt(resultSet.getTimestamp("created_at").toSqlTimestamp().toInstant());
        community.setUpdatedAt(resultSet.getTimestamp("updated_at").toSqlTimestamp().toInstant());
        return community;
    }
}
