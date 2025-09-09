-- Spanner Heterogeneous Graph DDL for Community Detection System
-- This schema creates a heterogeneous graph with devices and attributes as nodes

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

-- Node tables for device attributes
CREATE TABLE ssid_nodes (
    ssid_value STRING(255) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (ssid_value);

CREATE TABLE subnet_nodes (
    subnet_value STRING(18) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (subnet_value);

CREATE TABLE mac_prefix_nodes (
    mac_prefix_value STRING(8) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (mac_prefix_value);

CREATE TABLE ip_nodes (
    ip_value STRING(45) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (ip_value);

-- Edge tables for device-attribute relationships
CREATE TABLE device_ssid_edges (
    device_id STRING(255) NOT NULL,
    ssid_value STRING(255) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (device_id) REFERENCES devices (device_id),
    FOREIGN KEY (ssid_value) REFERENCES ssid_nodes (ssid_value)
) PRIMARY KEY (device_id, ssid_value);

CREATE TABLE device_subnet_edges (
    device_id STRING(255) NOT NULL,
    subnet_value STRING(18) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (device_id) REFERENCES devices (device_id),
    FOREIGN KEY (subnet_value) REFERENCES subnet_nodes (subnet_value)
) PRIMARY KEY (device_id, subnet_value);

CREATE TABLE device_mac_prefix_edges (
    device_id STRING(255) NOT NULL,
    mac_prefix_value STRING(8) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (device_id) REFERENCES devices (device_id),
    FOREIGN KEY (mac_prefix_value) REFERENCES mac_prefix_nodes (mac_prefix_value)
) PRIMARY KEY (device_id, mac_prefix_value);

CREATE TABLE device_ip_edges (
    device_id STRING(255) NOT NULL,
    ip_value STRING(45) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (device_id) REFERENCES devices (device_id),
    FOREIGN KEY (ip_value) REFERENCES ip_nodes (ip_value)
) PRIMARY KEY (device_id, ip_value);

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

-- Create the heterogeneous property graph
CREATE PROPERTY GRAPH HeterogeneousCommunityGraph
  NODE TABLES (
    devices,
    ssid_nodes,
    subnet_nodes,
    mac_prefix_nodes,
    ip_nodes
  )
  EDGE TABLES (
    device_ssid_edges
      SOURCE KEY (device_id) REFERENCES devices (device_id)
      DESTINATION KEY (ssid_value) REFERENCES ssid_nodes (ssid_value)
      LABEL HAS_SSID,
    device_subnet_edges
      SOURCE KEY (device_id) REFERENCES devices (device_id)
      DESTINATION KEY (subnet_value) REFERENCES subnet_nodes (subnet_value)
      LABEL IN_SUBNET,
    device_mac_prefix_edges
      SOURCE KEY (device_id) REFERENCES devices (device_id)
      DESTINATION KEY (mac_prefix_value) REFERENCES mac_prefix_nodes (mac_prefix_value)
      LABEL HAS_MAC_PREFIX,
    device_ip_edges
      SOURCE KEY (device_id) REFERENCES devices (device_id)
      DESTINATION KEY (ip_value) REFERENCES ip_nodes (ip_value)
      LABEL HAS_IP
  );

-- Indexes for better query performance
CREATE INDEX idx_devices_ssid ON devices (ssid);
CREATE INDEX idx_devices_subnet ON devices (subnet);
CREATE INDEX idx_devices_mac_prefix ON devices (mac_prefix);
CREATE INDEX idx_devices_ip ON devices (ip);
CREATE INDEX idx_communities_root_device ON communities (root_device_id);
CREATE INDEX idx_communities_type ON communities (community_type);

-- Indexes for edge tables
CREATE INDEX idx_device_ssid_edges_device ON device_ssid_edges (device_id);
CREATE INDEX idx_device_ssid_edges_ssid ON device_ssid_edges (ssid_value);
CREATE INDEX idx_device_subnet_edges_device ON device_subnet_edges (device_id);
CREATE INDEX idx_device_subnet_edges_subnet ON device_subnet_edges (subnet_value);
CREATE INDEX idx_device_mac_prefix_edges_device ON device_mac_prefix_edges (device_id);
CREATE INDEX idx_device_mac_prefix_edges_mac_prefix ON device_mac_prefix_edges (mac_prefix_value);
CREATE INDEX idx_device_ip_edges_device ON device_ip_edges (device_id);
CREATE INDEX idx_device_ip_edges_ip ON device_ip_edges (ip_value);
