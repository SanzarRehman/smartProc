#!/bin/bash

# Script to fix the RRF LOB issue

echo "=== Fixing RRF LOB Issue ==="
echo ""
echo "This script will:"
echo "1. Stop your application (if running)"
echo "2. Fix the database schema"
echo "3. Rebuild the application"
echo ""

# Get database credentials from application.yml
DB_HOST="localhost"
DB_PORT="5432"
DB_NAME="procurement_db"
DB_USER="postgres"

echo "Please enter your PostgreSQL password:"
read -s DB_PASSWORD

echo ""
echo "Step 1: Applying database fix..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f fix-rrf-lob-issue.sql

if [ $? -eq 0 ]; then
    echo "✓ Database schema fixed successfully"
else
    echo "✗ Failed to fix database schema"
    exit 1
fi

echo ""
echo "Step 2: Cleaning build artifacts..."
./gradlew clean

echo ""
echo "Step 3: Rebuilding application..."
./gradlew build -x test

if [ $? -eq 0 ]; then
    echo "✓ Build successful"
    echo ""
    echo "=== Fix Complete ==="
    echo ""
    echo "You can now start your application with:"
    echo "  ./gradlew bootRun"
    echo ""
    echo "Or if using Maven:"
    echo "  mvn spring-boot:run"
else
    echo "✗ Build failed"
    exit 1
fi
