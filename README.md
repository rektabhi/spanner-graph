# Community Detection System

A Java-based system for detecting and managing device communities using Google Cloud Spanner. This system groups network devices based on common attributes like SSID, IP subnet, and MAC address prefixes.

## Features

- **Community Detection**: Automatically groups devices based on network attributes
- **Multiple Grouping Criteria**: Support for SSID, IP subnet, and MAC prefix grouping
- **Extensible Design**: Easy to add new grouping criteria
- **Spanner Integration**: Uses Google Cloud Spanner for scalable data storage
- **Clean Architecture**: Separation of concerns with DAO pattern and service layer
- **Union-Find Algorithm**: Efficient community detection using disjoint set data structure

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
   - `CommunityDetectionService`: Main service for community detection
   - `CommunityDetectionServiceImpl`: Implementation using Union-Find algorithm

4. **Utilities** (`com.sumo.util`)
   - `NetworkUtils`: Network-related utility functions

5. **Configuration** (`com.sumo.config`)
   - `SpannerConfig`: Spanner database configuration

## Database Schema

The system uses three main tables:

- **devices**: Stores device information (ID, SSID, IP, MAC, subnet, MAC prefix)
- **communities**: Stores community information (ID, root device, size, type)
- **device_communities**: Stores device-community relationships

See `src/main/resources/schema.sql` for the complete schema definition.

## Setup

### Prerequisites

1. Java 17 or higher
2. Maven 3.6 or higher
3. Google Cloud Spanner instance
4. Google Cloud credentials configured

### Configuration

1. Set up your Google Cloud Spanner instance
2. Create a database using the schema in `src/main/resources/schema.sql`
3. Configure your credentials (via service account key or default credentials)

### Running the Demo

```bash
# Compile the project
mvn clean compile

# Run the demo (replace with your actual Spanner details)
mvn exec:java -Dexec.mainClass="com.sumo.CommunityDetectionDemo" \
  -Dspanner.project.id=your-project-id \
  -Dspanner.instance.id=your-instance-id \
  -Dspanner.database.id=your-database-id
```

## Usage Example

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

// Detect communities
Map<String, List<String>> communities = service.detectAndStoreCommunities(devices);

// Query communities
List<Community> deviceCommunities = service.getCommunitiesForDevice("D1");
List<Device> devicesInCommunity = service.getDevicesInCommunity("COMMUNITY_1");
```

## Community Detection Algorithm

The system uses a Union-Find (Disjoint Set) data structure to efficiently group devices:

1. **Initialization**: Each device starts as its own community
2. **Grouping by SSID**: Devices with the same SSID are merged
3. **Grouping by Subnet**: Devices in the same /24 subnet are merged
4. **Grouping by MAC Prefix**: Devices with the same MAC prefix (first 3 octets) are merged
5. **Result**: Final communities represent devices that share at least one common attribute

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
