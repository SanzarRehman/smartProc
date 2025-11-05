#!/bin/bash

# Procurement Email Automation - Run Script
# This script helps you run the application with environment variables

echo "=========================================="
echo "Procurement Email Automation System"
echo "=========================================="
echo ""

# Check if environment variables are set
if [ -z "$EMAIL_HOST" ]; then
    echo "⚠️  EMAIL_HOST is not set"
    echo "   Example: export EMAIL_HOST=imap.gmail.com"
fi

if [ -z "$EMAIL_USERNAME" ]; then
    echo "⚠️  EMAIL_USERNAME is not set"
    echo "   Example: export EMAIL_USERNAME=your-email@gmail.com"
fi

if [ -z "$EMAIL_PASSWORD" ]; then
    echo "⚠️  EMAIL_PASSWORD is not set"
    echo "   Example: export EMAIL_PASSWORD=your-app-password"
fi

if [ -z "$GEMINI_API_KEY" ]; then
    echo "⚠️  GEMINI_API_KEY is not set"
    echo "   Example: export GEMINI_API_KEY=your-api-key"
fi

echo ""
echo "Current Configuration:"
echo "  EMAIL_HOST: ${EMAIL_HOST:-not set}"
echo "  EMAIL_PORT: ${EMAIL_PORT:-993 (default)}"
echo "  EMAIL_USERNAME: ${EMAIL_USERNAME:-not set}"
echo "  EMAIL_PASSWORD: ${EMAIL_PASSWORD:+***set***}"
echo "  GEMINI_API_KEY: ${GEMINI_API_KEY:+***set***}"
echo "  KEYCLOAK_URL: ${KEYCLOAK_URL:-http://localhost:8080 (default)}"
echo ""

# Ask user if they want to continue
read -p "Continue with these settings? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Exiting. Please set environment variables and try again."
    exit 1
fi

echo ""
echo "Starting application..."
echo ""

# Run the application with Gradle
./gradlew bootRun
