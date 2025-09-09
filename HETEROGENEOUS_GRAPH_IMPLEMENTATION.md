# Heterogeneous Graph Model Implementation

## Overview

This implementation transforms the community detection system from a homogeneous graph model (devices as nodes) to a heterogeneous graph model where both devices AND device attributes (SSID, subnet, MAC prefix, IP) are nodes, with edges representing relationships between devices and their attributes.

## Key Changes Made

### 1. Database Schema (`heterogeneous-graph-schema.sql`)

**New Node Tables:**
- `ssid_nodes` - SSID values as nodes
- `subnet_nodes` - Subnet values as nodes  
- `mac_prefix_nodes` - MAC prefix values as nodes
- `ip_nodes` - IP addresses as nodes

**New Edge Tables:**
- `device_ssid_edges` - Device → SSID relationships
- `device_subnet_edges` - Device → Subnet relationships
- `device_mac_prefix_edges` - Device → MAC prefix relationships
- `device_ip_edges` - Device → IP relationships

**Property Graph:**
- `HeterogeneousCommunityGraph` - Includes all node and edge tables with proper labels

### 2. Service Implementation (`HeterogeneousGraphCommunityDetectionServiceImpl.java`)

**Key Methods:**
- `createHeterogeneousGraphNodesAndEdges()` - Creates attribute nodes and device-attribute edges
- `detectAllCommunitiesWithConnectedComponents()` - Uses heterogeneous graph traversal
- `detectCommunitiesBySsidGraph()` - Finds communities through SSID nodes
- `detectCommunitiesBySubnetGraph()` - Finds communities through subnet nodes
- `detectCommunitiesByMacPrefixGraph()` - Finds communities through MAC prefix nodes

**Graph Creation Process:**
1. Store devices in database
2. Create attribute nodes for each unique attribute value
3. Create device-attribute edges
4. Run community detection on heterogeneous graph

### 3. Demo Application (`HeterogeneousGraphCommunityDetectionDemo.java`)

**Features:**
- Processes devices in batches
- Creates heterogeneous graph with devices and attributes as nodes
- Demonstrates community detection through attribute traversal
- Shows graph querying capabilities
- Provides statistics and shortest path examples

### 4. Setup Script (`create-heterogeneous-graph-schema.sh`)

**Functionality:**
- Creates heterogeneous graph schema in Spanner
- Backs up existing schema
- Verifies schema creation
- Provides setup instructions

## Heterogeneous Graph Model Benefits

### 1. **More Natural Representation**
- Attributes are first-class citizens in the graph
- Direct relationships between devices and their attributes
- Easier to understand and visualize

### 2. **Better Community Detection**
- Communities found by traversing shared attribute nodes
- More accurate detection of device relationships
- Can find complex multi-attribute communities

### 3. **Richer Queries**
- Can traverse from devices to attributes and vice versa
- Find all devices with same SSID: Device → SSID → Device
- Find all devices in same subnet: Device → Subnet → Device
- Find devices with same MAC prefix: Device → MAC_PREFIX → Device

### 4. **Scalability**
- More efficient for large attribute sets
- Reduces redundant storage of attribute values
- Better performance for attribute-based queries

### 5. **Flexibility**
- Easy to add new attribute types
- Can extend to other device properties
- Supports complex relationship patterns

## Example Graph Structure

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

### SSID Communities
```sql
-- Find all devices connected to same SSID node
SELECT d.device_id, s.ssid_value
FROM devices d
JOIN device_ssid_edges dse ON d.device_id = dse.device_id
JOIN ssid_nodes s ON dse.ssid_value = s.ssid_value
WHERE s.ssid_value = 'HomeWiFi'
```

### Subnet Communities
```sql
-- Find all devices connected to same subnet node
SELECT d.device_id, sn.subnet_value
FROM devices d
JOIN device_subnet_edges dse ON d.device_id = dse.device_id
JOIN subnet_nodes sn ON dse.subnet_value = sn.subnet_value
WHERE sn.subnet_value = '192.168.1.0/24'
```

### Multi-Attribute Communities
```sql
-- Find devices connected through multiple shared attributes
WITH device_connections AS (
    -- SSID connections
    SELECT DISTINCT d1.device_id as device1, d2.device_id as device2
    FROM devices d1
    JOIN device_ssid_edges dse1 ON d1.device_id = dse1.device_id
    JOIN device_ssid_edges dse2 ON dse1.ssid_value = dse2.ssid_value
    JOIN devices d2 ON dse2.device_id = d2.device_id
    WHERE d1.device_id < d2.device_id
    
    UNION ALL
    
    -- Subnet connections
    SELECT DISTINCT d1.device_id as device1, d2.device_id as device2
    FROM devices d1
    JOIN device_subnet_edges dse1 ON d1.device_id = dse1.device_id
    JOIN device_subnet_edges dse2 ON dse1.subnet_value = dse2.subnet_value
    JOIN devices d2 ON dse2.device_id = d2.device_id
    WHERE d1.device_id < d2.device_id
    
    -- ... more connection types
)
-- Find connected components
```

## Usage Instructions

### 1. Setup Schema
```bash
./scripts/create-heterogeneous-graph-schema.sh
```

### 2. Run Demo
```bash
java com.sumo.HeterogeneousGraphCommunityDetectionDemo [csv-file] [batch-size]
```

### 3. Example Output
```
Starting Heterogeneous Graph-based Community Detection Demo
Processing devices in batches of 100 for heterogeneous graph
Building complete heterogeneous graph and detecting communities from all stored data
Final heterogeneous community detection completed: 15 total communities detected
Total devices in communities: 150

Example heterogeneous communities found:
  Community HETERO_GRAPH_COMPONENT_1: 25 devices - [D1, D2, D3, ...]
  Community HETERO_GRAPH_COMPONENT_2: 18 devices - [D4, D5, D6, ...]
  Community HETERO_GRAPH_COMPONENT_3: 12 devices - [D7, D8, D9, ...]
```

## Performance Considerations

### 1. **Batch Processing**
- Devices processed in configurable batches (default: 100)
- 100ms delay between batches to prevent overwhelming Spanner
- Progress logging for large datasets

### 2. **Mutation Batching**
- Graph edges written in batches of 1000 mutations
- Prevents transaction size limits
- Error handling for failed batches

### 3. **Indexing**
- Indexes on all edge tables for efficient traversal
- Indexes on attribute node tables
- Optimized for graph query performance

## Comparison: Homogeneous vs Heterogeneous

| Aspect | Homogeneous Model | Heterogeneous Model |
|--------|------------------|-------------------|
| **Nodes** | Devices only | Devices + Attributes |
| **Edges** | Device ↔ Device | Device → Attribute |
| **Storage** | Redundant attribute storage | Efficient attribute storage |
| **Queries** | Complex joins | Simple traversals |
| **Scalability** | Limited by device pairs | Scales with attribute diversity |
| **Flexibility** | Fixed relationships | Extensible relationships |
| **Community Detection** | Direct device comparison | Attribute-based traversal |

## Future Enhancements

1. **Additional Attribute Types**
   - Device manufacturer
   - Operating system
   - Location data
   - Time-based attributes

2. **Advanced Graph Algorithms**
   - PageRank for device importance
   - Clustering coefficients
   - Graph neural networks

3. **Real-time Updates**
   - Incremental graph updates
   - Streaming community detection
   - Dynamic attribute changes

4. **Graph Visualization**
   - Interactive graph exploration
   - Community visualization
   - Attribute relationship mapping

The heterogeneous graph model provides a more natural, scalable, and flexible approach to community detection in device networks, enabling richer analysis and better performance for large-scale deployments.
