# Runtime Community Detection Demo with CSV Processing

## Overview

The `RuntimeCommunityDetectionDemo` has been updated to read device data from the mock devices CSV file and process it in batches, demonstrating runtime community detection on real data rather than just test data.

## Key Features

### 1. **CSV File Processing**
- Reads from `mock_devices_1m.csv` by default
- Supports custom CSV file path via command line argument
- Processes devices in configurable batches (default: 100 devices per batch)
- Handles large datasets efficiently with progress logging

### 2. **Batch Processing**
- **Configurable Batch Size**: Default 100 devices per batch, customizable via command line
- **Maximum Device Limit**: Default 100 devices total for demo purposes, customizable via command line
- **Progress Logging**: Shows progress every 10,000 lines processed
- **Error Handling**: Gracefully handles malformed CSV lines
- **Resource Management**: 100ms delay between batches to prevent overwhelming Spanner

### 3. **Runtime Community Detection**
- **No Pre-computation**: Communities are computed on-demand when requested
- **Real Data**: Uses actual device data from CSV instead of synthetic test data
- **Multiple Community Types**: Finds communities by SSID, subnet, MAC prefix, and IP
- **Dynamic Queries**: Always returns current community information

## Usage

### Basic Usage
```bash
java com.sumo.RuntimeCommunityDetectionDemo
```
- Uses default CSV file: `mock_devices_1m.csv`
- Uses default batch size: 100 devices per batch
- Uses default max size: 100 devices total

### Custom CSV File
```bash
java com.sumo.RuntimeCommunityDetectionDemo /path/to/your/devices.csv
```
- Uses specified CSV file
- Uses default batch size: 100 devices per batch
- Uses default max size: 100 devices total

### Custom CSV File and Batch Size
```bash
java com.sumo.RuntimeCommunityDetectionDemo /path/to/your/devices.csv 500
```
- Uses specified CSV file
- Uses custom batch size: 500 devices per batch
- Uses default max size: 100 devices total

### Custom CSV File, Batch Size, and Max Size
```bash
java com.sumo.RuntimeCommunityDetectionDemo /path/to/your/devices.csv 500 1000
```
- Uses specified CSV file
- Uses custom batch size: 500 devices per batch
- Uses custom max size: 1000 devices total

## CSV Format

The demo expects CSV files with the following format:
```csv
device_id,ssid,ip_address,mac_address,subnet,mac_prefix,created_at
D1,HomeWiFi,192.168.1.100,00:23:12:34:56:78,192.168.1.0/24,00:23:12,2024-01-01T00:00:00Z
D2,HomeWiFi,192.168.1.101,00:23:12:34:56:79,192.168.1.0/24,00:23:12,2024-01-01T00:00:01Z
...
```

### Field Descriptions
- **device_id**: Unique identifier for the device
- **ssid**: WiFi network name
- **ip_address**: IP address of the device
- **mac_address**: MAC address of the device
- **subnet**: Network subnet (optional, calculated if not provided)
- **mac_prefix**: MAC address prefix (optional, calculated if not provided)
- **created_at**: Timestamp when device was created (optional)

## Demo Flow

### 1. **Initialization**
- Parse command line arguments
- Initialize Spanner configuration
- Clear existing data from database

### 2. **CSV Processing**
- Read CSV file line by line
- Parse device information
- Process network attributes (subnet, MAC prefix)
- Group devices into batches

### 3. **Batch Processing**
- Process each batch of devices
- Store devices in database
- Create heterogeneous graph nodes and edges
- Log progress and handle errors

### 4. **Runtime Community Detection**
- Demonstrate community detection for sample devices
- Show different community types (SSID, subnet, MAC prefix, IP)
- Display community statistics

### 5. **Additional Runtime Methods**
- Show all connected devices for a sample device
- Find largest community for a device
- Display community statistics
- Show devices in specific communities

## Example Output

```
Starting Runtime Community Detection Demo
Using default CSV file: mock_devices_1m.csv
Using default batch size: 100
Using default max size: 100
Clearing existing data from database for runtime community detection...
Processing devices from CSV file: mock_devices_1m.csv in batches of 100 (max 100 devices)
Reached maximum device limit of 100, stopping processing
Completed processing 100 devices from CSV in 1 batches (max limit: 100)

=== Runtime Community Detection Demo ===

--- Communities for Device D1 ---
Community: SSID_COMMUNITY_D1 (Type: SSID, Size: 25, Devices: [D1, D2, D3, ...])
Community: SUBNET_COMMUNITY_D1 (Type: SUBNET, Size: 15, Devices: [D1, D4, D5, ...])

--- Communities for Device D2 ---
Community: SSID_COMMUNITY_D2 (Type: SSID, Size: 25, Devices: [D1, D2, D3, ...])
Community: MAC_PREFIX_COMMUNITY_D2 (Type: MAC_PREFIX, Size: 8, Devices: [D2, D6, D7, ...])

=== Additional Runtime Methods Demo ===

--- All Connected Devices for D1 ---
Connected devices: [D2, D3, D4, D5, D6, D7, D8, D9, D10]

--- Largest Community for D1 ---
Largest community: SSID_COMMUNITY_D1 (Type: SSID, Size: 25, Devices: [D1, D2, D3, ...])

--- Community Statistics ---
SSID communities: 150
SUBNET communities: 75
MAC_PREFIX communities: 200
IP communities: 1000

--- Devices in Community SSID_COMMUNITY_D1 ---
Device: D1 (SSID: HomeWiFi, IP: 192.168.1.100, MAC: 00:23:12:34:56:78)
Device: D2 (SSID: HomeWiFi, IP: 192.168.1.101, MAC: 00:23:12:34:56:79)
...
```

## Performance Considerations

### 1. **Batch Size Tuning**
- **Small Batches (50-100)**: Better for memory-constrained environments
- **Medium Batches (100-500)**: Good balance of performance and resource usage
- **Large Batches (500-1000)**: Better performance but higher memory usage

### 2. **Max Size Configuration**
- **Demo Mode (100-500)**: Good for quick demonstrations and testing
- **Development Mode (1000-5000)**: Suitable for development and debugging
- **Production Mode (unlimited)**: Remove max size limit for full data processing

### 3. **Progress Monitoring**
- Progress logged every 10,000 lines
- Batch completion logging
- Error handling for malformed data
- Max size limit tracking

### 4. **Resource Management**
- 100ms delay between batches
- Graceful handling of interruptions
- Memory-efficient CSV parsing
- Early termination when max size reached

## Error Handling

### 1. **CSV Parsing Errors**
- Skips malformed lines with warning
- Continues processing remaining data
- Logs line numbers for debugging

### 2. **Database Errors**
- Handles duplicate key errors gracefully
- Retries failed batches
- Comprehensive error logging

### 3. **Resource Errors**
- Handles memory constraints
- Manages database connection limits
- Graceful shutdown on interruption

## Benefits

### 1. **Real Data Testing**
- Uses actual device data instead of synthetic data
- Tests with realistic data volumes
- Validates performance with real-world scenarios

### 2. **Scalability Demonstration**
- Shows how the system handles large datasets
- Demonstrates batch processing efficiency
- Validates runtime community detection performance

### 3. **Production Readiness**
- Handles real CSV data formats
- Robust error handling
- Configurable for different environments

## Future Enhancements

### 1. **Database Query for Sample Devices**
- Query actual device IDs from database
- More accurate sample selection
- Better demonstration data

### 2. **Performance Metrics**
- Track processing time per batch
- Monitor memory usage
- Measure query performance

### 3. **Advanced Filtering**
- Filter devices by attributes
- Process specific device subsets
- Custom community detection criteria

The updated demo provides a comprehensive test of the runtime community detection system with real data, demonstrating its scalability and performance characteristics in a production-like environment.
