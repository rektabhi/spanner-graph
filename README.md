# Community Detection System

A Java-based system for detecting and managing device communities using Google Cloud Spanner with **graph database capabilities**. This system leverages Spanner's native graph processing to efficiently group network devices based on common attributes like SSID, IP subnet, and MAC address prefixes.

## Features

- **Graph-based Community Detection**: Leverages Spanner's graph database capabilities for efficient community detection
- **Native Graph Processing**: Uses graph algorithms (connected components, shortest path) directly in the database
- **Multiple Grouping Criteria**: Support for SSID, IP subnet, and MAC prefix grouping
- **Graph Traversal**: Advanced graph queries for path finding and relationship analysis
- **Extensible Design**: Easy to add new grouping criteria and graph algorithms
- **Spanner Graph Integration**: Uses Google Cloud Spanner Graph for scalable graph data storage
- **Clean Architecture**: Separation of concerns with DAO pattern and service layer
- **Dual Implementation**: Both application-level (Union-Find) and graph-based approaches available

## Architecture

### Core Components

1. **Entity Classes** (`com.sumo.entity`)
   - `Device`: Represents a network device
   - `Community`: Represents a group of related devices
   - `DeviceCommunity`: Represents the relationship between devices and communities

2. **Data Access Layer** (`com.sumo.dao`)
   - `DeviceDao`: Interface for device operations
   - `CommunityDao`: Interface for community operations
   - `DeviceCommunityDao`: Interface for device-community relationships
   - Spanner implementations in `com.sumo.dao.spanner`

3. **Business Logic Layer** (`com.sumo.service`)
   - `CommunityDetectionService`: Main service for application-level community detection
   - `CommunityDetectionServiceImpl`: Implementation using Union-Find algorithm
   - `GraphCommunityDetectionService`: Graph-based community detection service
   - `GraphCommunityDetectionServiceImpl`: Implementation using Spanner Graph capabilities

4. **Utilities** (`com.sumo.util`)
   - `NetworkUtils`: Network-related utility functions

5. **Configuration** (`com.sumo.config`)
   - `SpannerConfig`: Spanner database configuration

## Database Schema

The system supports two database schemas:

### Traditional Relational Schema
- **devices**: Stores device information (ID, SSID, IP, MAC, subnet, MAC prefix)
- **communities**: Stores community information (ID, root device, size, type)
- **device_communities**: Stores device-community relationships

See `src/main/resources/schema.sql` for the complete relational schema.

### Graph Database Schema
- **devices**: Node table for device vertices
- **ssid_connections**: Edge table for SSID-based relationships
- **subnet_connections**: Edge table for subnet-based relationships  
- **mac_connections**: Edge table for MAC prefix-based relationships
- **communities**: Enhanced with device_ids array for graph-based storage

See `src/main/resources/graph-schema.sql` for the complete graph schema with Spanner Graph capabilities.

## Setup

### Prerequisites

1. Java 17 or higher
2. Maven 3.6 or higher
3. Google Cloud Spanner instance
4. Google Cloud credentials configured

### Configuration

1. Set up your Google Cloud Spanner instance
2. Create a database using the appropriate schema:
   - **Traditional approach**: Use `src/main/resources/schema.sql`
   - **Graph-based approach**: Use `src/main/resources/graph-schema.sql` (see `GRAPH_SETUP.md` for detailed instructions)
3. Configure your credentials (via service account key or default credentials)

**Note**: For graph-based features, you must use a real Spanner instance (not the emulator) as the emulator doesn't support Graph capabilities.

### Running the Demos

```bash
# Compile the project
mvn clean compile

# Run the traditional demo (replace with your actual Spanner details)
mvn exec:java -Dexec.mainClass="com.sumo.CommunityDetectionDemo" \
  -Dspanner.project.id=your-project-id \
  -Dspanner.instance.id=your-instance-id \
  -Dspanner.database.id=your-database-id

# Run the graph-based demo (requires graph schema)
mvn exec:java -Dexec.mainClass="com.sumo.GraphCommunityDetectionDemo" \
  -Dspanner.project.id=your-project-id \
  -Dspanner.instance.id=your-instance-id \
  -Dspanner.database.id=your-graph-database-id

# Run tests without database setup
mvn exec:java -Dexec.mainClass="com.sumo.CommunityDetectionTest"
mvn exec:java -Dexec.mainClass="com.sumo.GraphCommunityDetectionTest"
```

## Usage Examples

### Traditional Application-Level Approach

```java
// Initialize services
SpannerConfig config = new SpannerConfig(projectId, instanceId, databaseId);
DeviceDao deviceDao = config.deviceDao();
CommunityDao communityDao = config.communityDao();
DeviceCommunityDao deviceCommunityDao = config.deviceCommunityDao();

CommunityDetectionService service = new CommunityDetectionServiceImpl(
    deviceDao, communityDao, deviceCommunityDao);

// Create devices
List<Device> devices = Arrays.asList(
    new Device("D1", "OfficeWiFi", "192.168.1.2", "AA:BB:CC:11:22:33"),
    new Device("D2", "OfficeWiFi", "192.168.1.3", "AA:BB:CC:11:22:34")
);

// Process network attributes
for (Device device : devices) {
    device.setSubnet(NetworkUtils.getSubnet(device.getIp()));
    device.setMacPrefix(NetworkUtils.getMacPrefix(device.getMac()));
}

// Detect communities using Union-Find algorithm
Map<String, List<String>> communities = service.detectAndStoreCommunities(devices);

// Query communities
List<Community> deviceCommunities = service.getCommunitiesForDevice("D1");
List<Device> devicesInCommunity = service.getDevicesInCommunity("COMMUNITY_1");
```

### Graph-Based Approach (Recommended)

```java
// Initialize graph-based services
SpannerConfig config = new SpannerConfig(projectId, instanceId, databaseId);
DatabaseClient dbClient = config.createDatabaseClient();
DeviceDao deviceDao = config.deviceDao();
CommunityDao communityDao = config.communityDao();

GraphCommunityDetectionService graphService = new GraphCommunityDetectionServiceImpl(
    dbClient, deviceDao, communityDao);

// Create devices
List<Device> devices = Arrays.asList(
    new Device("D1", "OfficeWiFi", "192.168.1.2", "AA:BB:CC:11:22:33"),
    new Device("D2", "OfficeWiFi", "192.168.1.3", "AA:BB:CC:11:22:34")
);

// Detect communities using graph algorithms
Map<String, List<String>> communities = graphService.detectAndStoreCommunitiesWithGraph(devices);

// Advanced graph operations
List<String> shortestPath = graphService.findShortestPath("D1", "D2");
Map<String, List<String>> connectedComponents = graphService.detectAllCommunitiesWithConnectedComponents();

// Graph-based queries
List<Community> deviceCommunities = graphService.getCommunitiesForDeviceGraph("D1");
List<Device> devicesInCommunity = graphService.getDevicesInCommunityGraph("GRAPH_COMMUNITY_1");
```

## Community Detection Algorithms

### Traditional Union-Find Algorithm

The traditional approach uses a Union-Find (Disjoint Set) data structure to efficiently group devices:

1. **Initialization**: Each device starts as its own community
2. **Grouping by SSID**: Devices with the same SSID are merged
3. **Grouping by Subnet**: Devices in the same /24 subnet are merged
4. **Grouping by MAC Prefix**: Devices with the same MAC prefix (first 3 octets) are merged
5. **Result**: Final communities represent devices that share at least one common attribute

### Graph-Based Algorithms (Recommended)

The graph-based approach leverages Spanner's native graph processing capabilities:

1. **Graph Construction**: Devices are vertices, relationships are edges
2. **Connected Components**: Uses graph traversal to find connected device groups
3. **Graph Queries**: Leverages SQL with recursive CTEs for complex graph operations
4. **Shortest Path**: Finds optimal paths between devices using BFS
5. **Graph Statistics**: Provides insights into network topology and connectivity

**Benefits of Graph-Based Approach:**
- **Performance**: Processing happens at the database layer
- **Scalability**: Leverages Spanner's distributed graph processing
- **Flexibility**: Easy to add new graph algorithms and queries
- **Advanced Analytics**: Supports complex graph operations like shortest path, centrality, etc.

## Extensibility

To add new grouping criteria:

1. Add the new attribute to the `Device` entity
2. Update the database schema to include the new field
3. Modify the `CommunityDetectionServiceImpl.detectCommunities()` method
4. Add a new detection method in the service interface

## Dependencies

- Google Cloud Spanner Client Library
- Google Cloud Spanner JDBC Driver
- SLF4J for logging
- Logback for logging implementation

## License

This project is part of a demonstration and should be used according to your organization's policies.
