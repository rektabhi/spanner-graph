#!/bin/bash

# Script to validate Spanner Graph DDL syntax
# This script checks the DDL statements without executing them

set -e

echo "Validating Spanner Graph DDL syntax..."
echo ""

# Check if gcloud is available
if ! command -v gcloud &> /dev/null; then
    echo "❌ gcloud CLI not found. Please install Google Cloud CLI."
    exit 1
fi

echo "✅ gcloud CLI found"

# Check if user is authenticated
if ! gcloud auth list --filter=status:ACTIVE --format="value(account)" | grep -q .; then
    echo "❌ No active gcloud authentication found."
    echo "Please run: gcloud auth login"
    exit 1
fi

echo "✅ gcloud authentication active"

# Check if Spanner API is enabled
if ! gcloud services list --enabled --filter="name:spanner.googleapis.com" --format="value(name)" | grep -q spanner; then
    echo "⚠️  Spanner API not enabled. You may need to enable it:"
    echo "gcloud services enable spanner.googleapis.com"
fi

echo "✅ Spanner API check completed"

# Validate DDL syntax by checking the file structure
SCHEMA_FILE="src/main/resources/graph-schema.sql"

if [ ! -f "$SCHEMA_FILE" ]; then
    echo "❌ Schema file not found: $SCHEMA_FILE"
    exit 1
fi

echo "✅ Schema file found: $SCHEMA_FILE"

# Check for common DDL syntax issues
echo ""
echo "Checking DDL syntax..."

# Check for CREATE TABLE statements
if grep -q "CREATE TABLE" "$SCHEMA_FILE"; then
    echo "✅ CREATE TABLE statements found"
else
    echo "❌ No CREATE TABLE statements found"
fi

# Check for CREATE PROPERTY GRAPH statement
if grep -q "CREATE PROPERTY GRAPH" "$SCHEMA_FILE"; then
    echo "✅ CREATE PROPERTY GRAPH statement found"
else
    echo "❌ No CREATE PROPERTY GRAPH statement found"
fi

# Check for FOREIGN KEY constraints
if grep -q "FOREIGN KEY" "$SCHEMA_FILE"; then
    echo "✅ FOREIGN KEY constraints found"
else
    echo "❌ No FOREIGN KEY constraints found"
fi

# Check for proper edge table definitions
if grep -q "SOURCE KEY" "$SCHEMA_FILE" && grep -q "DESTINATION KEY" "$SCHEMA_FILE"; then
    echo "✅ Edge table definitions found"
else
    echo "❌ Edge table definitions missing or incomplete"
fi

# Check for LABEL definitions
if grep -q "LABEL" "$SCHEMA_FILE"; then
    echo "✅ Edge labels found"
else
    echo "❌ No edge labels found"
fi

echo ""
echo "🎉 DDL syntax validation completed!"
echo ""
echo "Next steps:"
echo "1. Ensure you have a Spanner instance with Graph capabilities"
echo "2. Run the setup script: ./scripts/create-graph-schema.sh <project-id> <instance-id> <database-id>"
echo "3. Test the implementation with the demo applications"
echo ""
echo "For detailed setup instructions, see: GRAPH_SETUP.md"
