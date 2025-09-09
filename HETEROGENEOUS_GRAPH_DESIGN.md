# Heterogeneous Graph Model Design

## Current Model (Homogeneous)
- **Nodes**: Only devices
- **Edges**: Direct connections between devices that share attributes
- **Example**: Device A ←→ Device B (if they share same SSID)

## New Model (Heterogeneous)
- **Nodes**: 
  - Devices (device_id)
  - SSID nodes (ssid_value)
  - Subnet nodes (subnet_value) 
  - MAC prefix nodes (mac_prefix_value)
  - IP nodes (ip_value)
- **Edges**: 
  - Device → SSID (device has SSID)
  - Device → Subnet (device belongs to subnet)
  - Device → MAC prefix (device has MAC prefix)
  - Device → IP (device has IP)

## Benefits of Heterogeneous Model
1. **More Natural Representation**: Attributes are first-class citizens
2. **Better Community Detection**: Can find communities based on shared attributes
3. **Richer Queries**: Can traverse from devices to attributes and vice versa
4. **Scalability**: More efficient for large attribute sets
5. **Flexibility**: Easy to add new attribute types

## Graph Structure Example
```
Device D1 → SSID "HomeWiFi"
Device D1 → Subnet "192.168.1.0/24"
Device D1 → MAC_PREFIX "AA:BB:CC"
Device D1 → IP "192.168.1.100"

Device D2 → SSID "HomeWiFi"  (same SSID as D1)
Device D2 → Subnet "192.168.1.0/24"  (same subnet as D1)
Device D2 → MAC_PREFIX "DD:EE:FF"
Device D2 → IP "192.168.1.101"

Device D3 → SSID "OfficeWiFi"
Device D3 → Subnet "10.0.0.0/24"
Device D3 → MAC_PREFIX "AA:BB:CC"  (same MAC prefix as D1)
Device D3 → IP "10.0.0.50"
```

## Community Detection in Heterogeneous Model
- **SSID Communities**: All devices connected to same SSID node
- **Subnet Communities**: All devices connected to same subnet node  
- **MAC Prefix Communities**: All devices connected to same MAC prefix node
- **Multi-attribute Communities**: Devices connected through multiple shared attributes

## Database Schema Changes Needed
1. **New Node Tables**:
   - `ssid_nodes` (ssid_value, created_at)
   - `subnet_nodes` (subnet_value, created_at)
   - `mac_prefix_nodes` (mac_prefix_value, created_at)
   - `ip_nodes` (ip_value, created_at)

2. **New Edge Tables**:
   - `device_ssid_edges` (device_id, ssid_value)
   - `device_subnet_edges` (device_id, subnet_value)
   - `device_mac_prefix_edges` (device_id, mac_prefix_value)
   - `device_ip_edges` (device_id, ip_value)

3. **Updated Property Graph**:
   - Include all node tables
   - Include all edge tables with proper labels
