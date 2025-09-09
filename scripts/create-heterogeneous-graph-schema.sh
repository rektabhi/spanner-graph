#!/bin/bash

# Script to create heterogeneous graph schema in Google Cloud Spanner
# This script creates a heterogeneous graph with devices and attributes as nodes

set -e

# Configuration
PROJECT_ID="sm-apps-core"
INSTANCE_ID="common-spanner-next"
DATABASE_ID="communities-next"
SCHEMA_FILE="src/main/resources/heterogeneous-graph-schema.sql"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}Creating Heterogeneous Graph Schema in Google Cloud Spanner${NC}"
echo "================================================================"
echo "Project ID: $PROJECT_ID"
echo "Instance ID: $INSTANCE_ID"
echo "Database ID: $DATABASE_ID"
echo "Schema File: $SCHEMA_FILE"
echo ""

# Check if schema file exists
if [ ! -f "$SCHEMA_FILE" ]; then
    echo -e "${RED}Error: Schema file $SCHEMA_FILE not found${NC}"
    exit 1
fi

# Check if gcloud is installed and authenticated
if ! command -v gcloud &> /dev/null; then
    echo -e "${RED}Error: gcloud CLI is not installed${NC}"
    echo "Please install gcloud CLI and authenticate:"
    echo "  https://cloud.google.com/sdk/docs/install"
    exit 1
fi

# Check if user is authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then
    echo -e "${RED}Error: Not authenticated with gcloud${NC}"
    echo "Please run: gcloud auth login"
    exit 1
fi

# Set the project
echo -e "${YELLOW}Setting project to $PROJECT_ID...${NC}"
gcloud config set project $PROJECT_ID

# Check if instance exists
echo -e "${YELLOW}Checking if Spanner instance exists...${NC}"
if ! gcloud spanner instances describe $INSTANCE_ID --project=$PROJECT_ID &> /dev/null; then
    echo -e "${RED}Error: Spanner instance $INSTANCE_ID not found${NC}"
    echo "Please create the instance first or check the instance ID"
    exit 1
fi

# Check if database exists
echo -e "${YELLOW}Checking if database exists...${NC}"
if ! gcloud spanner databases describe $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID &> /dev/null; then
    echo -e "${RED}Error: Database $DATABASE_ID not found${NC}"
    echo "Please create the database first or check the database ID"
    exit 1
fi

# Backup existing schema (if any)
echo -e "${YELLOW}Backing up existing schema...${NC}"
BACKUP_FILE="schema_backup_$(date +%Y%m%d_%H%M%S).sql"
gcloud spanner databases ddl describe $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID > $BACKUP_FILE 2>/dev/null || echo "No existing schema to backup"
echo "Backup saved to: $BACKUP_FILE"

# Apply the heterogeneous graph schema
echo -e "${YELLOW}Applying heterogeneous graph schema...${NC}"
echo "This will create:"
echo "  - Device nodes table"
echo "  - Attribute nodes tables (SSID, subnet, MAC prefix, IP)"
echo "  - Device-attribute edge tables"
echo "  - Heterogeneous property graph"
echo "  - Indexes for performance"
echo ""

# Read and apply the schema
if gcloud spanner databases ddl update $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID --ddl="$(cat $SCHEMA_FILE)"; then
    echo -e "${GREEN}✓ Heterogeneous graph schema created successfully!${NC}"
else
    echo -e "${RED}✗ Failed to create heterogeneous graph schema${NC}"
    echo "Check the error messages above and fix any issues"
    exit 1
fi

# Verify the schema was created
echo -e "${YELLOW}Verifying schema creation...${NC}"
echo "Checking for key tables:"

# Check for device table
if gcloud spanner databases ddl describe $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID | grep -q "CREATE TABLE devices"; then
    echo -e "${GREEN}✓ devices table created${NC}"
else
    echo -e "${RED}✗ devices table not found${NC}"
fi

# Check for attribute node tables
for table in "ssid_nodes" "subnet_nodes" "mac_prefix_nodes" "ip_nodes"; do
    if gcloud spanner databases ddl describe $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID | grep -q "CREATE TABLE $table"; then
        echo -e "${GREEN}✓ $table table created${NC}"
    else
        echo -e "${RED}✗ $table table not found${NC}"
    fi
done

# Check for edge tables
for table in "device_ssid_edges" "device_subnet_edges" "device_mac_prefix_edges" "device_ip_edges"; do
    if gcloud spanner databases ddl describe $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID | grep -q "CREATE TABLE $table"; then
        echo -e "${GREEN}✓ $table table created${NC}"
    else
        echo -e "${RED}✗ $table table not found${NC}"
    fi
done

# Check for property graph
if gcloud spanner databases ddl describe $DATABASE_ID --instance=$INSTANCE_ID --project=$PROJECT_ID | grep -q "CREATE PROPERTY GRAPH HeterogeneousCommunityGraph"; then
    echo -e "${GREEN}✓ HeterogeneousCommunityGraph property graph created${NC}"
else
    echo -e "${RED}✗ HeterogeneousCommunityGraph property graph not found${NC}"
fi

echo ""
echo -e "${GREEN}Heterogeneous Graph Schema Setup Complete!${NC}"
echo "=============================================="
echo ""
echo "Next steps:"
echo "1. Run the heterogeneous graph demo:"
echo "   java com.sumo.HeterogeneousGraphCommunityDetectionDemo"
echo ""
echo "2. The heterogeneous graph model includes:"
echo "   - Device nodes (devices table)"
echo "   - Attribute nodes (ssid_nodes, subnet_nodes, mac_prefix_nodes, ip_nodes)"
echo "   - Device-attribute edges (device_ssid_edges, device_subnet_edges, etc.)"
echo "   - Property graph: HeterogeneousCommunityGraph"
echo ""
echo "3. Community detection will find devices connected through shared attributes"
echo ""
echo "Schema backup saved to: $BACKUP_FILE"
