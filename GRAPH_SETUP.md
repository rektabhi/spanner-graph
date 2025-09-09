# Spanner Graph Schema Setup Guide

This guide explains how to set up the Spanner Graph schema for the Community Detection System.

## Prerequisites

1. **Google Cloud Spanner Instance**: You need a Spanner instance with Graph capabilities
2. **gcloud CLI**: Install and configure the Google Cloud CLI
3. **Authentication**: Ensure you're authenticated with `gcloud auth login`

## Important Notes

⚠️ **Spanner Emulator Limitation**: The Spanner Emulator does not support Graph features. You must use a real Spanner instance.

⚠️ **DDL Execution**: DDL statements cannot be executed within transactions. They must be executed individually.

## Setup Methods

### Method 1: Using the Automated Script (Recommended)

```bash
# Make the script executable (if not already done)
chmod +x scripts/create-graph-schema.sh

# Run the script with your Spanner details
./scripts/create-graph-schema.sh <project-id> <instance-id> <database-id>

# Example:
./scripts/create-graph-schema.sh my-project my-instance community-graph-db
```

### Method 2: Manual Step-by-Step Execution

1. **Create the database** (if it doesn't exist):
```bash
gcloud spanner databases create community-graph-db \
    --instance=my-instance \
    --project=my-project
```

2. **Execute DDL statements one by one** using the file `src/main/resources/graph-schema-step-by-step.sql`:

```bash
# Execute each DDL statement individually
gcloud spanner databases ddl update community-graph-db \
    --instance=my-instance \
    --project=my-project \
    --ddl="CREATE TABLE devices (...)"
```

### Method 3: Using Spanner Console

1. Go to the [Google Cloud Console](https://console.cloud.google.com/spanner)
2. Navigate to your Spanner instance and database
3. Go to the "Schema" tab
4. Execute each DDL statement from `graph-schema-step-by-step.sql` one by one

## Schema Components

### Tables Created

1. **`devices`** - Node table containing device information
2. **`ssid_connections`** - Edge table for SSID-based relationships
3. **`subnet_connections`** - Edge table for subnet-based relationships
4. **`mac_connections`** - Edge table for MAC prefix-based relationships
5. **`communities`** - Results table for detected communities

### Graph Structure

- **Nodes**: `devices` table represents vertices in the graph
- **Edges**: Connection tables represent relationships between devices
- **Labels**: 
  - `SSID_CONNECTED` - Devices connected via same SSID
  - `SUBNET_CONNECTED` - Devices connected via same subnet
  - `MAC_CONNECTED` - Devices connected via same MAC prefix

## Verification

After setup, verify the schema was created correctly:

```bash
# List tables
gcloud spanner databases ddl describe community-graph-db \
    --instance=my-instance \
    --project=my-project

# Test with a simple query
gcloud spanner databases execute-sql community-graph-db \
    --instance=my-instance \
    --project=my-project \
    --sql="SELECT COUNT(*) FROM devices"
```

## Testing the Graph Implementation

Once the schema is set up, test the graph-based community detection:

```bash
# Run the graph-based demo
mvn exec:java -Dexec.mainClass="com.sumo.GraphCommunityDetectionDemo" \
  -Dspanner.project.id=my-project \
  -Dspanner.instance.id=my-instance \
  -Dspanner.database.id=community-graph-db

# Run the test (no database required)
mvn exec:java -Dexec.mainClass="com.sumo.GraphCommunityDetectionTest"
```

## Troubleshooting

### Common Issues

1. **"DDL statements are not permitted in this context"**
   - Solution: Execute DDL statements outside of transactions
   - Use individual `gcloud spanner databases ddl update` commands

2. **"Graph features not supported"**
   - Solution: Ensure you're using a real Spanner instance, not the emulator

3. **"Table not found" errors**
   - Solution: Execute DDL statements in the correct order
   - Create tables before creating the property graph

4. **Foreign key constraint errors**
   - Solution: Ensure referenced tables exist before creating foreign keys

### Getting Help

- Check the [Spanner Graph Documentation](https://cloud.google.com/spanner/docs/graph)
- Review the [GQL Schema Statements](https://cloud.google.com/spanner/docs/reference/standard-sql/graph-schema-statements)
- Use the [Spanner Troubleshooting Guide](https://cloud.google.com/spanner/docs/troubleshooting)

## Next Steps

After successful setup:

1. **Load Sample Data**: Use the demo applications to load test data
2. **Run Community Detection**: Execute graph-based community detection algorithms
3. **Explore Graph Queries**: Use GQL to perform advanced graph operations
4. **Monitor Performance**: Use Spanner monitoring to track query performance

## Files Reference

- `src/main/resources/graph-schema.sql` - Complete schema (for reference)
- `src/main/resources/graph-schema-step-by-step.sql` - Step-by-step DDL statements
- `scripts/create-graph-schema.sh` - Automated setup script
- `GRAPH_SETUP.md` - This setup guide
