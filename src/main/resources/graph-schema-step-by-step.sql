-- Spanner Graph DDL for Community Detection System - Step by Step
-- Execute these statements one by one in Spanner console or using gcloud CLI

-- Step 1: Create the devices table (nodes)
CREATE TABLE devices (
    device_id STRING(255) NOT NULL,
    ssid STRING(255),
    ip STRING(45),
    mac STRING(17),
    subnet STRING(18),
    mac_prefix STRING(8),
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (device_id);

-- Step 2: Create indexes for better performance
CREATE INDEX idx_devices_ssid ON devices (ssid);
CREATE INDEX idx_devices_subnet ON devices (subnet);
CREATE INDEX idx_devices_mac_prefix ON devices (mac_prefix);

-- Step 3: Create SSID connections table (edges)
CREATE TABLE ssid_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    ssid STRING(255) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, ssid);

-- Step 4: Create subnet connections table (edges)
CREATE TABLE subnet_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    subnet STRING(18) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, subnet);

-- Step 5: Create MAC connections table (edges)
CREATE TABLE mac_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    mac_prefix STRING(8) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, mac_prefix);

-- Step 6: Create communities table
CREATE TABLE communities (
    community_id STRING(255) NOT NULL,
    root_device_id STRING(255) NOT NULL,
    size INT64 NOT NULL,
    community_type STRING(50) NOT NULL,
    device_ids ARRAY<STRING(255)> NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (community_id);

-- Step 7: Create indexes for communities table
CREATE INDEX idx_communities_root_device ON communities (root_device_id);
CREATE INDEX idx_communities_type ON communities (community_type);

-- Step 8: Create the property graph (execute this last)
CREATE PROPERTY GRAPH CommunityGraph
  NODE TABLES (
    devices
  )
  EDGE TABLES (
    ssid_connections
      SOURCE KEY (from_device_id) REFERENCES devices (device_id)
      DESTINATION KEY (to_device_id) REFERENCES devices (device_id)
      LABEL SSID_CONNECTED,
    subnet_connections
      SOURCE KEY (from_device_id) REFERENCES devices (device_id)
      DESTINATION KEY (to_device_id) REFERENCES devices (device_id)
      LABEL SUBNET_CONNECTED,
    mac_connections
      SOURCE KEY (from_device_id) REFERENCES devices (device_id)
      DESTINATION KEY (to_device_id) REFERENCES devices (device_id)
      LABEL MAC_CONNECTED
  );
