package com.sumo.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity representing the relationship between a device and its community.
 * This class maps to the device_communities table in Spanner.
 */
public class DeviceCommunity {
    private String deviceId;
    private String communityId;
    private Instant joinedAt;
    private Instant updatedAt;

    // Default constructor for Spanner
    public DeviceCommunity() {}

    public DeviceCommunity(String deviceId, String communityId) {
        this.deviceId = deviceId;
        this.communityId = communityId;
        this.joinedAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getCommunityId() {
        return communityId;
    }

    public void setCommunityId(String communityId) {
        this.communityId = communityId;
    }

    public Instant getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(Instant joinedAt) {
        this.joinedAt = joinedAt;
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
        DeviceCommunity that = (DeviceCommunity) o;
        return Objects.equals(deviceId, that.deviceId) && Objects.equals(communityId, that.communityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId, communityId);
    }

    @Override
    public String toString() {
        return "DeviceCommunity{" +
                "deviceId='" + deviceId + '\'' +
                ", communityId='" + communityId + '\'' +
                ", joinedAt=" + joinedAt +
                '}';
    }
}
