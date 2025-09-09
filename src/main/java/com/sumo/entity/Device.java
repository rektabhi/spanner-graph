package com.sumo.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Entity representing a network device with its identifying information.
 * This class maps to the devices table in Spanner.
 */
public class Device {
    private String deviceId;
    private String ssid;
    private String ip;
    private String mac;
    private String subnet;
    private String macPrefix;
    private Instant createdAt;
    private Instant updatedAt;

    // Default constructor for Spanner
    public Device() {}

    public Device(String deviceId, String ssid, String ip, String mac) {
        this.deviceId = deviceId;
        this.ssid = ssid;
        this.ip = ip;
        this.mac = mac;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    // Getters and Setters
    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getMac() {
        return mac;
    }

    public void setMac(String mac) {
        this.mac = mac;
    }

    public String getSubnet() {
        return subnet;
    }

    public void setSubnet(String subnet) {
        this.subnet = subnet;
    }

    public String getMacPrefix() {
        return macPrefix;
    }

    public void setMacPrefix(String macPrefix) {
        this.macPrefix = macPrefix;
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
        Device device = (Device) o;
        return Objects.equals(deviceId, device.deviceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(deviceId);
    }

    @Override
    public String toString() {
        return "Device{" +
                "deviceId='" + deviceId + '\'' +
                ", ssid='" + ssid + '\'' +
                ", ip='" + ip + '\'' +
                ", mac='" + mac + '\'' +
                ", subnet='" + subnet + '\'' +
                ", macPrefix='" + macPrefix + '\'' +
                '}';
    }
}
