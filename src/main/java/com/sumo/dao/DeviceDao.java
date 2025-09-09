package com.sumo.dao;

import com.sumo.entity.Device;
import java.util.List;
import java.util.Optional;

/**
 * Data Access Object interface for Device operations.
 * Defines the contract for device-related database operations.
 */
public interface DeviceDao {
    
    /**
     * Save a device to the database.
     * @param device the device to save
     * @return the saved device
     */
    Device save(Device device);
    
    /**
     * Save multiple devices in a batch operation.
     * @param devices list of devices to save
     * @return list of saved devices
     */
    List<Device> saveAll(List<Device> devices);
    
    /**
     * Find a device by its ID.
     * @param deviceId the device ID
     * @return Optional containing the device if found
     */
    Optional<Device> findById(String deviceId);
    
    /**
     * Find all devices.
     * @return list of all devices
     */
    List<Device> findAll();
    
    /**
     * Find devices by SSID.
     * @param ssid the SSID to search for
     * @return list of devices with the given SSID
     */
    List<Device> findBySsid(String ssid);
    
    /**
     * Find devices by subnet.
     * @param subnet the subnet to search for
     * @return list of devices in the given subnet
     */
    List<Device> findBySubnet(String subnet);
    
    /**
     * Find devices by MAC prefix.
     * @param macPrefix the MAC prefix to search for
     * @return list of devices with the given MAC prefix
     */
    List<Device> findByMacPrefix(String macPrefix);
    
    /**
     * Update a device.
     * @param device the device to update
     * @return the updated device
     */
    Device update(Device device);
    
    /**
     * Delete a device by its ID.
     * @param deviceId the device ID to delete
     * @return true if the device was deleted, false otherwise
     */
    boolean deleteById(String deviceId);
    
    /**
     * Check if a device exists by its ID.
     * @param deviceId the device ID to check
     * @return true if the device exists, false otherwise
     */
    boolean existsById(String deviceId);
}
