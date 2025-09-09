-- Spanner DDL for Community Detection System

-- Devices table to store device information
CREATE TABLE devices (
    device_id STRING(255) NOT NULL,
    ssid STRING(255),
    ip STRING(45), -- IPv4 or IPv6
    mac STRING(17), -- MAC address format
    subnet STRING(18), -- /24 subnet format
    mac_prefix STRING(8), -- First 3 octets
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (device_id);

-- Communities table to store community information
CREATE TABLE communities (
    community_id STRING(255) NOT NULL,
    root_device_id STRING(255) NOT NULL,
    size INT64 NOT NULL,
    community_type STRING(50) NOT NULL, -- SSID, SUBNET, MAC_PREFIX, MIXED
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (community_id);

-- Device-Community relationships
CREATE TABLE device_communities (
    device_id STRING(255) NOT NULL,
    community_id STRING(255) NOT NULL,
    joined_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (device_id, community_id),
  INTERLEAVE IN PARENT communities ON DELETE CASCADE;

-- Indexes for better query performance
CREATE INDEX idx_devices_ssid ON devices (ssid);
CREATE INDEX idx_devices_subnet ON devices (subnet);
CREATE INDEX idx_devices_mac_prefix ON devices (mac_prefix);
CREATE INDEX idx_communities_root_device ON communities (root_device_id);
CREATE INDEX idx_communities_type ON communities (community_type);
CREATE INDEX idx_device_communities_community ON device_communities (community_id);
