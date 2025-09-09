#!/bin/bash

# Script to create Spanner Graph schema for Community Detection System
# Usage: ./create-graph-schema.sh <project-id> <instance-id> <database-id>

set -e

# Check if required parameters are provided
if [ $# -ne 3 ]; then
    echo "Usage: $0 <project-id> <instance-id> <database-id>"
    echo "Example: $0 my-project my-instance community-graph-db"
    exit 1
fi

PROJECT_ID=$1
INSTANCE_ID=$2
DATABASE_ID=$3

echo "Creating Spanner Graph schema for Community Detection System..."
echo "Project: $PROJECT_ID"
echo "Instance: $INSTANCE_ID"
echo "Database: $DATABASE_ID"
echo ""

# Function to execute DDL statement
execute_ddl() {
    local ddl="$1"
    local description="$2"
    
    echo "Executing: $description"
    echo "DDL: $ddl"
    
    if gcloud spanner databases ddl update $DATABASE_ID \
        --instance=$INSTANCE_ID \
        --project=$PROJECT_ID \
        --ddl="$ddl"; then
        echo "✅ Success: $description"
    else
        echo "❌ Failed: $description"
        exit 1
    fi
    echo ""
}

# Step 1: Create devices table
execute_ddl "CREATE TABLE devices (
    device_id STRING(255) NOT NULL,
    ssid STRING(255),
    ip STRING(45),
    mac STRING(17),
    subnet STRING(18),
    mac_prefix STRING(8),
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (device_id);" "Create devices table"

# Step 2: Create indexes for devices
execute_ddl "CREATE INDEX idx_devices_ssid ON devices (ssid);" "Create SSID index"
execute_ddl "CREATE INDEX idx_devices_subnet ON devices (subnet);" "Create subnet index"
execute_ddl "CREATE INDEX idx_devices_mac_prefix ON devices (mac_prefix);" "Create MAC prefix index"

# Step 3: Create SSID connections table
execute_ddl "CREATE TABLE ssid_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    ssid STRING(255) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, ssid);" "Create SSID connections table"

# Step 4: Create subnet connections table
execute_ddl "CREATE TABLE subnet_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    subnet STRING(18) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, subnet);" "Create subnet connections table"

# Step 5: Create MAC connections table
execute_ddl "CREATE TABLE mac_connections (
    from_device_id STRING(255) NOT NULL,
    to_device_id STRING(255) NOT NULL,
    mac_prefix STRING(8) NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    FOREIGN KEY (from_device_id) REFERENCES devices (device_id),
    FOREIGN KEY (to_device_id) REFERENCES devices (device_id)
) PRIMARY KEY (from_device_id, to_device_id, mac_prefix);" "Create MAC connections table"

# Step 6: Create communities table
execute_ddl "CREATE TABLE communities (
    community_id STRING(255) NOT NULL,
    root_device_id STRING(255) NOT NULL,
    size INT64 NOT NULL,
    community_type STRING(50) NOT NULL,
    device_ids ARRAY<STRING(255)> NOT NULL,
    created_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
    updated_at TIMESTAMP NOT NULL OPTIONS (allow_commit_timestamp=true),
) PRIMARY KEY (community_id);" "Create communities table"

# Step 7: Create indexes for communities
execute_ddl "CREATE INDEX idx_communities_root_device ON communities (root_device_id);" "Create communities root device index"
execute_ddl "CREATE INDEX idx_communities_type ON communities (community_type);" "Create communities type index"

# Step 8: Create the property graph
execute_ddl "CREATE PROPERTY GRAPH CommunityGraph
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
  );" "Create property graph"

echo "🎉 Spanner Graph schema created successfully!"
echo ""
echo "You can now use the Community Detection System with graph capabilities."
echo ""
echo "To test the setup, run:"
echo "mvn exec:java -Dexec.mainClass=\"com.sumo.GraphCommunityDetectionDemo\" \\"
echo "  -Dspanner.project.id=$PROJECT_ID \\"
echo "  -Dspanner.instance.id=$INSTANCE_ID \\"
echo "  -Dspanner.database.id=$DATABASE_ID"
