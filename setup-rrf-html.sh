#!/bin/bash

# Complete fix for RRF LOB issue + HTML generation setup

echo "=========================================="
echo "RRF System Update Script"
echo "=========================================="
echo ""
echo "This script will:"
echo "1. Fix the database LOB issue"
echo "2. Clean old plain-text RRF records"
echo "3. Rebuild the application with HTML support"
echo ""

# Database credentials
read -p "Enter PostgreSQL host [localhost]: " DB_HOST
DB_HOST=${DB_HOST:-localhost}

read -p "Enter PostgreSQL port [5432]: " DB_PORT
DB_PORT=${DB_PORT:-5432}

read -p "Enter database name [procurement_db]: " DB_NAME
DB_NAME=${DB_NAME:-procurement_db}

read -p "Enter PostgreSQL user [postgres]: " DB_USER
DB_USER=${DB_USER:-postgres}

echo ""
read -sp "Enter PostgreSQL password: " DB_PASSWORD
echo ""
echo ""

# Step 1: Fix LOB issue
echo "Step 1: Fixing database LOB issue..."
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f fix-rrf-lob-issue.sql

if [ $? -eq 0 ]; then
    echo "✓ Database schema fixed"
else
    echo "✗ Failed to fix database schema"
    exit 1
fi

# Step 2: Clean old RRF records
echo ""
echo "Step 2: Cleaning old plain-text RRF records..."
read -p "Do you want to delete old RRF records? (y/n): " DELETE_OLD
if [ "$DELETE_OLD" = "y" ] || [ "$DELETE_OLD" = "Y" ]; then
    PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "DELETE FROM request_for_requisition;"
    echo "✓ Old RRF records deleted"
else
    echo "⊘ Skipped deleting old records"
fi

# Step 3: Rebuild application
echo ""
echo "Step 3: Rebuilding application with HTML support..."
./gradlew clean build -x test

if [ $? -eq 0 ]; then
    echo "✓ Application rebuilt successfully"
else
    echo "✗ Build failed"
    exit 1
fi

echo ""
echo "=========================================="
echo "Setup Complete!"
echo "=========================================="
echo ""
echo "Next steps:"
echo "1. Start your application:"
echo "   ./gradlew bootRun"
echo ""
echo "2. Generate a new RRF by sending a procurement email"
echo ""
echo "3. View the HTML RRF in browser:"
echo "   http://localhost:8080/api/rrf/{PO-NUMBER}/html"
echo ""
echo "4. Download as PDF:"
echo "   http://localhost:8080/api/rrf/{PO-NUMBER}/pdf"
echo ""
echo "New features:"
echo "  ✓ LLM generates professional HTML documents"
echo "  ✓ View formatted RRF in browser"
echo "  ✓ HTML-to-PDF conversion with styling"
echo "  ✓ No more LOB errors"
echo ""
