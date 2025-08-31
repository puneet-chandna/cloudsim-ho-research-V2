#!/bin/bash

# CloudSim HO Research Experiment Runner
# Updated for JAR execution with heap configuration
# author: Puneet Chandna
# date: 2025-08-01
# version: 1.0.0
# description: This script runs the CloudSim HO Research Experiment using JAR files.
# It is used to run the experiment with the 50GB heap configuration.

echo "=========================================="
echo "CloudSim HO Research Experiment Runner"
echo "=========================================="
echo "Heap Configuration: 45GB (initial and max)"
echo "System Memory: 64GB"
echo "Date: $(date)"
echo "=========================================="

# Check available memory
echo "Checking system memory..."
TOTAL_MEM=$(free -g | awk '/^Mem:/{print $2}')
echo "Total system memory: ${TOTAL_MEM}GB"

if [ "$TOTAL_MEM" -lt 64 ]; then
    echo "Warning: System has less than 64GB RAM. 45GB heap may cause issues."
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Check if the JAR file exists
JAR_PATH="target/cloudsim-ho-research-v2-1.0-SNAPSHOT.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "Error: JAR file not found at $JAR_PATH"
    exit 1
fi

# Java heap and GC flags
JAVA_FLAGS="-Xms45G -Xmx45G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:gc.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof -XX:+DisableExplicitGC"

# Run the experiment with the configured JAR
echo "Starting experiment with 45GB heap configuration..."
echo "This may take 1-2 hours for the complete experimental suite."
echo ""

# Execute JAR file with the configured heap and GC settings
java $JAVA_FLAGS -jar "$JAR_PATH"

# Check exit status
if [ $? -eq 0 ]; then
    echo ""
    echo "=========================================="
    echo "Experiment completed successfully!"
    echo "Results saved in: results/"
    echo "Check logs for detailed information."
    echo "=========================================="
else
    echo ""
    echo "=========================================="
    echo "Experiment failed!"
    echo "Check logs for error details."
    echo "=========================================="
    exit 1
fi
