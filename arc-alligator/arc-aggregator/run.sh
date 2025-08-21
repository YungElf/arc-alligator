#!/bin/bash

# ARC Aggregator Quick Start Script

echo "Starting ARC Aggregator..."

# Check if Java 17+ is available
if ! command -v java &> /dev/null; then
    echo "Error: Java is not installed or not in PATH"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ]; then
    echo "Error: Java 21 or higher is required. Current version: $JAVA_VERSION"
    exit 1
fi

echo "Java version: $(java -version 2>&1 | head -n 1)"

# Check if the JAR file exists
if [ ! -f "target/arc-aggregator-1.0.0.jar" ]; then
    echo "Building project..."
    if command -v mvn &> /dev/null; then
        mvn clean package -DskipTests
    else
        echo "Error: Maven is not installed or not in PATH"
        echo "Please run: mvn clean package -DskipTests"
        exit 1
    fi
fi

# Load environment variables if .env file exists
if [ -f ".env" ]; then
    echo "Loading environment variables from .env file..."
    export $(cat .env | grep -v '^#' | xargs)
fi

# Set default values if not provided
export SPLUNK_BASE_URL=${SPLUNK_BASE_URL:-"https://your-splunk-instance.com:8089"}
export SPLUNK_AUTH_TOKEN=${SPLUNK_AUTH_TOKEN:-"your-splunk-auth-token"}
export OUTPUT_DIRECTORY=${OUTPUT_DIRECTORY:-"output"}

echo "Configuration:"
echo "  Splunk URL: $SPLUNK_BASE_URL"
echo "  Output Directory: $OUTPUT_DIRECTORY"
echo "  Server Port: ${SERVER_PORT:-8080}"

echo ""
echo "Starting application..."
echo "Health check: http://localhost:${SERVER_PORT:-8080}/actuator/health"
echo "API endpoint: http://localhost:${SERVER_PORT:-8080}/api/aggregate"
echo ""

java -jar target/arc-aggregator-1.0.0.jar 