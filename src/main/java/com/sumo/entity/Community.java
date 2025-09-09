package com.sumo.entity;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Entity representing a community of devices.
 * This class maps to the communities table in Spanner.
 */
public class Community {
    private String communityId;
    private String rootDeviceId;
    private int size;
    private String communityType; // "SSID", "SUBNET", "MAC_PREFIX", "MIXED"
    private List<String> deviceIds; // Array of device IDs in this community
    private Instant createdAt;
    private Instant updatedAt;

    // Default constructor for Spanner
    public Community() {}

    public Community(String communityId, String rootDeviceId, int size, String communityType) {
        this.communityId = communityId;
        this.rootDeviceId = rootDeviceId;
        this.size = size;
        this.communityType = communityType;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getCommunityId() {
        return communityId;
    }

    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    public String getRootDeviceId() {
        return rootDeviceId;
    }

    public void setRootDeviceId(String rootDeviceId) {
        this.rootDeviceId = rootDeviceId;
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public String getCommunityType() {
        return communityType;
    }

    public void setCommunityType(String communityType) {
        this.communityType = communityType;
    }

    public List<String> getDeviceIds() {
        return deviceIds;
    }

    public void setDeviceIds(List<String> deviceIds) {
        this.deviceIds = deviceIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Community community = (Community) o;
        return Objects.equals(communityId, community.communityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(communityId);
    }

    @Override
    public String toString() {
        return "Community{" +
                "communityId='" + communityId + '\'' +
                ", rootDeviceId='" + rootDeviceId + '\'' +
                ", size=" + size +
                ", communityType='" + communityType + '\'' +
                '}';
    }
}
