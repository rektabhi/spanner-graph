package com.sumo.service.impl;

import com.sumo.dao.CommunityDao;
import com.sumo.dao.DeviceCommunityDao;
import com.sumo.dao.DeviceDao;
import com.sumo.entity.Community;
import com.sumo.entity.Device;
import com.sumo.entity.DeviceCommunity;
import com.sumo.service.CommunityDetectionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * Implementation of CommunityDetectionService.
 * Provides business logic for community detection using Union-Find algorithm.
 */
public class CommunityDetectionServiceImpl implements CommunityDetectionService {
    
    private static final Logger logger = LoggerFactory.getLogger(CommunityDetectionServiceImpl.class);
    
    private final DeviceDao deviceDao;
    private final CommunityDao communityDao;
    private final DeviceCommunityDao deviceCommunityDao;
    
    public CommunityDetectionServiceImpl(DeviceDao deviceDao, 
                                       CommunityDao communityDao, 
                                       DeviceCommunityDao deviceCommunityDao) {
        this.deviceDao = deviceDao;
        this.communityDao = communityDao;
        this.deviceCommunityDao = deviceCommunityDao;
    }
    
    @Override
    public Map<String, List<String>> detectAndStoreCommunities(List<Device> devices) {
        logger.info("Starting community detection for {} devices", devices.size());
        
        // First, save all devices to the database
        List<Device> savedDevices = deviceDao.saveAll(devices);
        logger.debug("Saved {} devices to database", savedDevices.size());
        
        // Detect communities using Union-Find algorithm
        Map<String, List<String>> communities = detectCommunities(savedDevices);
        
        // Store communities in database
        storeCommunities(communities);
        
        logger.info("Community detection completed. Found {} communities", communities.size());
        return communities;
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesBySsid(List<Device> devices) {
        logger.debug("Detecting communities by SSID");
        return detectCommunitiesByAttribute(devices, Device::getSsid, "SSID");
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesBySubnet(List<Device> devices) {
        logger.debug("Detecting communities by subnet");
        return detectCommunitiesByAttribute(devices, Device::getSubnet, "SUBNET");
    }
    
    @Override
    public Map<String, List<String>> detectCommunitiesByMacPrefix(List<Device> devices) {
        logger.debug("Detecting communities by MAC prefix");
        return detectCommunitiesByAttribute(devices, Device::getMacPrefix, "MAC_PREFIX");
    }
    
    @Override
    public List<Community> getCommunitiesForDevice(String deviceId) {
        logger.debug("Getting communities for device: {}", deviceId);
        
        List<DeviceCommunity> relationships = deviceCommunityDao.findByDeviceId(deviceId);
        List<Community> communities = new ArrayList<>();
        
        for (DeviceCommunity relationship : relationships) {
            communityDao.findById(relationship.getCommunityId())
                .ifPresent(communities::add);
        }
        
        return communities;
    }
    
    @Override
    public List<Device> getDevicesInCommunity(String communityId) {
        logger.debug("Getting devices in community: {}", communityId);
        
        List<DeviceCommunity> relationships = deviceCommunityDao.findByCommunityId(communityId);
        List<Device> devices = new ArrayList<>();
        
        for (DeviceCommunity relationship : relationships) {
            deviceDao.findById(relationship.getDeviceId())
                .ifPresent(devices::add);
        }
        
        return devices;
    }
    
    @Override
    public Map<String, Integer> getCommunityStatistics() {
        logger.debug("Getting community statistics");
        
        List<Community> allCommunities = communityDao.findAll();
        return allCommunities.stream()
            .collect(Collectors.groupingBy(
                Community::getCommunityType,
                Collectors.collectingAndThen(Collectors.counting(), Math::toIntExact)
            ));
    }
    
    @Override
    public Map<String, List<String>> rebuildAllCommunities() {
        logger.info("Rebuilding all communities from existing devices");
        
        // Clear existing communities and relationships
        clearAllCommunities();
        
        // Get all devices from database
        List<Device> allDevices = deviceDao.findAll();
        logger.debug("Found {} devices in database", allDevices.size());
        
        if (allDevices.isEmpty()) {
            return new HashMap<>();
        }
        
        // Detect and store new communities
        return detectAndStoreCommunities(allDevices);
    }
    
    /**
     * Main community detection method using Union-Find algorithm.
     * Groups devices by SSID, subnet, and MAC prefix.
     */
    private Map<String, List<String>> detectCommunities(List<Device> devices) {
        UnionFind uf = new UnionFind();
        
        // Add all devices to Union-Find structure
        for (Device device : devices) {
            uf.add(device.getDeviceId());
        }
        
        // Group devices by different attributes
        Map<String, List<Device>> ssidGroups = groupDevicesByAttribute(devices, Device::getSsid);
        Map<String, List<Device>> subnetGroups = groupDevicesByAttribute(devices, Device::getSubnet);
        Map<String, List<Device>> macPrefixGroups = groupDevicesByAttribute(devices, Device::getMacPrefix);
        
        // Helper to union all devices in a group
        BiConsumer<List<Device>, UnionFind> unionGroup = (list, ufInstance) -> {
            if (list == null || list.size() < 2) return;
            String firstId = list.get(0).getDeviceId();
            for (int i = 1; i < list.size(); i++) {
                ufInstance.union(firstId, list.get(i).getDeviceId());
            }
        };
        
        // Connect devices with the same SSID
        for (List<Device> group : ssidGroups.values()) {
            unionGroup.accept(group, uf);
        }
        
        // Connect devices in the same subnet
        for (List<Device> group : subnetGroups.values()) {
            unionGroup.accept(group, uf);
        }
        
        // Connect devices with the same MAC prefix
        for (List<Device> group : macPrefixGroups.values()) {
            unionGroup.accept(group, uf);
        }
        
        // Build communities: root -> list of device IDs
        Map<String, List<String>> communities = new HashMap<>();
        for (Device device : devices) {
            String root = uf.find(device.getDeviceId());
            communities.computeIfAbsent(root, k -> new ArrayList<>()).add(device.getDeviceId());
        }
        
        return communities;
    }
    
    /**
     * Detect communities based on a specific attribute.
     */
    private Map<String, List<String>> detectCommunitiesByAttribute(List<Device> devices, 
                                                                  java.util.function.Function<Device, String> attributeExtractor,
                                                                  String communityType) {
        Map<String, List<Device>> groups = groupDevicesByAttribute(devices, attributeExtractor);
        Map<String, List<String>> communities = new HashMap<>();
        
        int communityIndex = 1;
        for (Map.Entry<String, List<Device>> entry : groups.entrySet()) {
            if (entry.getValue().size() > 1) {
                String communityId = communityType + "_" + communityIndex;
                List<String> deviceIds = entry.getValue().stream()
                    .map(Device::getDeviceId)
                    .collect(Collectors.toList());
                communities.put(communityId, deviceIds);
                communityIndex++;
            }
        }
        
        return communities;
    }
    
    /**
     * Group devices by a specific attribute.
     */
    private Map<String, List<Device>> groupDevicesByAttribute(List<Device> devices, 
                                                             java.util.function.Function<Device, String> attributeExtractor) {
        Map<String, List<Device>> groups = new HashMap<>();
        
        for (Device device : devices) {
            String attribute = attributeExtractor.apply(device);
            if (attribute != null && !attribute.isEmpty()) {
                groups.computeIfAbsent(attribute, k -> new ArrayList<>()).add(device);
            }
        }
        
        return groups;
    }
    
    /**
     * Store communities and their relationships in the database.
     */
    private void storeCommunities(Map<String, List<String>> communities) {
        List<Community> communityEntities = new ArrayList<>();
        List<DeviceCommunity> relationships = new ArrayList<>();
        
        for (Map.Entry<String, List<String>> entry : communities.entrySet()) {
            String communityId = entry.getKey();
            List<String> deviceIds = entry.getValue();
            
            if (deviceIds.size() > 1) { // Only store communities with more than one device
                String rootDeviceId = deviceIds.get(0);
                String communityType = determineCommunityType(deviceIds);
                
                Community community = new Community(communityId, rootDeviceId, deviceIds.size(), communityType);
                communityEntities.add(community);
                
                // Create device-community relationships
                for (String deviceId : deviceIds) {
                    relationships.add(new DeviceCommunity(deviceId, communityId));
                }
            }
        }
        
        // Save to database
        if (!communityEntities.isEmpty()) {
            communityDao.saveAll(communityEntities);
            logger.debug("Saved {} communities to database", communityEntities.size());
        }
        
        if (!relationships.isEmpty()) {
            deviceCommunityDao.saveAll(relationships);
            logger.debug("Saved {} device-community relationships to database", relationships.size());
        }
    }
    
    /**
     * Determine the community type based on device attributes.
     */
    private String determineCommunityType(List<String> deviceIds) {
        Set<String> ssids = new HashSet<>();
        Set<String> subnets = new HashSet<>();
        Set<String> macPrefixes = new HashSet<>();
        
        for (String deviceId : deviceIds) {
            deviceDao.findById(deviceId).ifPresent(device -> {
                if (device.getSsid() != null) ssids.add(device.getSsid());
                if (device.getSubnet() != null) subnets.add(device.getSubnet());
                if (device.getMacPrefix() != null) macPrefixes.add(device.getMacPrefix());
            });
        }
        
        int attributeTypes = 0;
        if (ssids.size() == 1) attributeTypes++;
        if (subnets.size() == 1) attributeTypes++;
        if (macPrefixes.size() == 1) attributeTypes++;
        
        if (attributeTypes == 1) {
            if (ssids.size() == 1) return "SSID";
            if (subnets.size() == 1) return "SUBNET";
            if (macPrefixes.size() == 1) return "MAC_PREFIX";
        }
        
        return "MIXED";
    }
    
    /**
     * Clear all existing communities and relationships.
     */
    private void clearAllCommunities() {
        logger.debug("Clearing all existing communities");
        
        List<Community> existingCommunities = communityDao.findAll();
        for (Community community : existingCommunities) {
            communityDao.deleteById(community.getCommunityId());
        }
        
        List<DeviceCommunity> existingRelationships = deviceCommunityDao.findAll();
        for (DeviceCommunity relationship : existingRelationships) {
            deviceCommunityDao.deleteByDeviceIdAndCommunityId(
                relationship.getDeviceId(), relationship.getCommunityId());
        }
    }
    
    /**
     * Union-Find data structure for community detection.
     */
    private static class UnionFind {
        private Map<String, String> parent = new HashMap<>();
        private Map<String, Integer> size = new HashMap<>();
        
        public void add(String deviceId) {
            if (!parent.containsKey(deviceId)) {
                parent.put(deviceId, deviceId);
                size.put(deviceId, 1);
            }
        }
        
        public String find(String deviceId) {
            String p = parent.get(deviceId);
            if (p == null) return null;
            if (!p.equals(deviceId)) {
                String root = find(p);
                parent.put(deviceId, root);
                return root;
            }
            return p;
        }
        
        public void union(String id1, String id2) {
            if (id1 == null || id2 == null) return;
            String root1 = find(id1);
            String root2 = find(id2);
            if (root1 == null || root2 == null) return;
            if (root1.equals(root2)) return;
            
            int size1 = size.getOrDefault(root1, 1);
            int size2 = size.getOrDefault(root2, 1);
            
            if (size1 < size2) {
                parent.put(root1, root2);
                size.put(root2, size1 + size2);
            } else {
                parent.put(root2, root1);
                size.put(root1, size1 + size2);
            }
        }
    }
}
