package com.sumo.dao.spanner;

import com.google.cloud.spanner.*;
import com.google.cloud.Timestamp;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Device;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Spanner implementation of DeviceDao.
 * Provides database operations for Device entities using Google Cloud Spanner.
 */
public class SpannerDeviceDao implements DeviceDao {
    
    private static final Logger logger = LoggerFactory.getLogger(SpannerDeviceDao.class);
    
    private final DatabaseClient dbClient;
    
    public SpannerDeviceDao(DatabaseClient dbClient) {
        this.dbClient = dbClient;
    }
    
    @Override
    public Device save(Device device) {
        logger.debug("Saving device: {}", device.getDeviceId());
        
        device.setUpdatedAt(Instant.now());
        if (device.getCreatedAt() == null) {
            device.setCreatedAt(Instant.now());
        }
        
        dbClient.write(List.of(
            Mutation.newInsertBuilder("devices")
                .set("device_id").to(device.getDeviceId())
                .set("ssid").to(device.getSsid())
                .set("ip").to(device.getIp())
                .set("mac").to(device.getMac())
                .set("subnet").to(device.getSubnet())
                .set("mac_prefix").to(device.getMacPrefix())
                .set("created_at").to(Timestamp.ofTimeMicroseconds(device.getCreatedAt().toEpochMilli() * 1000))
                .set("updated_at").to(Timestamp.ofTimeMicroseconds(device.getUpdatedAt().toEpochMilli() * 1000))
                .build()
        ));
        
        return device;
    }
    
    @Override
    public List<Device> saveAll(List<Device> devices) {
        logger.debug("Saving {} devices", devices.size());
        
        List<Mutation> mutations = new ArrayList<>();
        Instant now = Instant.now();
        
        for (Device device : devices) {
            device.setUpdatedAt(now);
            if (device.getCreatedAt() == null) {
                device.setCreatedAt(now);
            }
            
            mutations.add(
                Mutation.newInsertBuilder("devices")
                    .set("device_id").to(device.getDeviceId())
                    .set("ssid").to(device.getSsid())
                    .set("ip").to(device.getIp())
                    .set("mac").to(device.getMac())
                    .set("subnet").to(device.getSubnet())
                    .set("mac_prefix").to(device.getMacPrefix())
                    .set("created_at").to(Timestamp.ofTimeMicroseconds(device.getCreatedAt().toEpochMilli() * 1000))
                    .set("updated_at").to(Timestamp.ofTimeMicroseconds(device.getUpdatedAt().toEpochMilli() * 1000))
                    .build()
            );
        }
        
        dbClient.write(mutations);
        return devices;
    }
    
    @Override
    public Optional<Device> findById(String deviceId) {
        logger.debug("Finding device by ID: {}", deviceId);
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.of("SELECT * FROM devices WHERE device_id = @deviceId")
        )) {
            if (resultSet.next()) {
                return Optional.of(mapResultSetToDevice(resultSet));
            }
        }
        
        return Optional.empty();
    }
    
    @Override
    public List<Device> findAll() {
        logger.debug("Finding all devices");
        
        List<Device> devices = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.of("SELECT * FROM devices ORDER BY created_at DESC")
        )) {
            while (resultSet.next()) {
                devices.add(mapResultSetToDevice(resultSet));
            }
        }
        
        return devices;
    }
    
    @Override
    public List<Device> findBySsid(String ssid) {
        logger.debug("Finding devices by SSID: {}", ssid);
        
        List<Device> devices = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM devices WHERE ssid = @ssid")
                .bind("ssid").to(ssid)
                .build()
        )) {
            while (resultSet.next()) {
                devices.add(mapResultSetToDevice(resultSet));
            }
        }
        
        return devices;
    }
    
    @Override
    public List<Device> findBySubnet(String subnet) {
        logger.debug("Finding devices by subnet: {}", subnet);
        
        List<Device> devices = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM devices WHERE subnet = @subnet")
                .bind("subnet").to(subnet)
                .build()
        )) {
            while (resultSet.next()) {
                devices.add(mapResultSetToDevice(resultSet));
            }
        }
        
        return devices;
    }
    
    @Override
    public List<Device> findByMacPrefix(String macPrefix) {
        logger.debug("Finding devices by MAC prefix: {}", macPrefix);
        
        List<Device> devices = new ArrayList<>();
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT * FROM devices WHERE mac_prefix = @macPrefix")
                .bind("macPrefix").to(macPrefix)
                .build()
        )) {
            while (resultSet.next()) {
                devices.add(mapResultSetToDevice(resultSet));
            }
        }
        
        return devices;
    }
    
    @Override
    public Device update(Device device) {
        logger.debug("Updating device: {}", device.getDeviceId());
        
        device.setUpdatedAt(Instant.now());
        
        dbClient.write(List.of(
            Mutation.newUpdateBuilder("devices")
                .set("device_id").to(device.getDeviceId())
                .set("ssid").to(device.getSsid())
                .set("ip").to(device.getIp())
                .set("mac").to(device.getMac())
                .set("subnet").to(device.getSubnet())
                .set("mac_prefix").to(device.getMacPrefix())
                .set("updated_at").to(Timestamp.ofTimeMicroseconds(device.getUpdatedAt().toEpochMilli() * 1000))
                .build()
        ));
        
        return device;
    }
    
    @Override
    public boolean deleteById(String deviceId) {
        logger.debug("Deleting device: {}", deviceId);
        
        try {
            dbClient.write(List.of(
                Mutation.delete("devices", Key.of(deviceId))
            ));
            return true;
        } catch (Exception e) {
            logger.error("Error deleting device {}: {}", deviceId, e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean existsById(String deviceId) {
        logger.debug("Checking if device exists: {}", deviceId);
        
        try (ResultSet resultSet = dbClient.singleUse().executeQuery(
            Statement.newBuilder("SELECT 1 FROM devices WHERE device_id = @deviceId LIMIT 1")
                .bind("deviceId").to(deviceId)
                .build()
        )) {
            return resultSet.next();
        }
    }
    
    private Device mapResultSetToDevice(ResultSet resultSet) {
        Device device = new Device();
        device.setDeviceId(resultSet.getString("device_id"));
        device.setSsid(resultSet.getString("ssid"));
        device.setIp(resultSet.getString("ip"));
        device.setMac(resultSet.getString("mac"));
        device.setSubnet(resultSet.getString("subnet"));
        device.setMacPrefix(resultSet.getString("mac_prefix"));
        device.setCreatedAt(resultSet.getTimestamp("created_at").toSqlTimestamp().toInstant());
        device.setUpdatedAt(resultSet.getTimestamp("updated_at").toSqlTimestamp().toInstant());
        return device;
    }
}
