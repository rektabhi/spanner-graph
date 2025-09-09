-- Spanner Graph DDL for Community Detection System
-- This schema leverages Spanner's graph database capabilities

-- Node tables (vertices) for devices
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

-- Edge tables for relationships between devices
-- SSID-based connections
CREATE TABLE ssid_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    ssid STRING(255) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, ssid);

-- Subnet-based connections
CREATE TABLE subnet_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    subnet STRING(18) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, subnet);

-- MAC prefix-based connections
CREATE TABLE mac_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    mac_prefix STRING(8) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, mac_prefix);

-- Community results table (populated by graph queries)
CREATE TABLE communities (
    community_id STRING(255) NOT NULL,
    root_device_id STRING(255) NOT NULL,
    size INT64 NOT NULL,
    community_type STRING(50) NOT NULL,
    device_ids ARRAY<STRING(255)> NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (community_id);

-- Create the property graph
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

-- Indexes for better query performance
CREATE INDEX idx_devices_ssid ON devices (ssid);
CREATE INDEX idx_devices_subnet ON devices (subnet);
CREATE INDEX idx_devices_mac_prefix ON devices (mac_prefix);
CREATE INDEX idx_communities_root_device ON communities (root_device_id);
CREATE INDEX idx_communities_type ON communities (community_type);
