#!/bin/bash

# CloudSim HO Research Experiment Runner
# Updated for 8GB heap configuration
# author: Puneet Chandna
# date: 2025-08-01
# version: 1.0.0
# description: This script runs the CloudSim HO Research Experiment.
# It is used to run the experiment with the 8GB heap configuration.

echo "=========================================="
echo "CloudSim HO Research Experiment Runner"
echo "=========================================="
echo "Heap Configuration: 50GB (initial and max)"
echo "System Memory: 64GB"
echo "Heap Configuration: 50GB (initial and max)"
echo "System Memory: 64GB"
echo "Date: $(date)"
echo "=========================================="

# Check if Maven is available
if ! command -v mvn &> /dev/null; then
    echo "Error: Maven is not installed or not in PATH"
    exit 1
fi

# Check available memory
echo "Checking system memory..."
TOTAL_MEM=$(free -g | awk '/^Mem:/{print $2}')
echo "Total system memory: ${TOTAL_MEM}GB"

if [ "$TOTAL_MEM" -lt 64 ]; then
    echo "Warning: System has less than 64GB RAM. 50GB heap may cause issues."
    read -p "Continue anyway? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        exit 1
    fi
fi

# Clean and build the project
echo "Building project..."
mvn clean compile -q
if [ $? -ne 0 ]; then
    echo "Error: Build failed"
    exit 1
fi

echo "Build successful!"

# Run the experiment
echo "Starting experiment with 50GB heap configuration..."
echo "This may take 1-2 hours for the complete experimental suite."
echo ""

# Run with Maven exec plugin (uses pom.xml configuration)
mvn exec:java -Dexec.mainClass="org.puneet.cloudsimplus.hiippo.App" -q

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