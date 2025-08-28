#!/bin/bash

# Quick Test Script for CloudSim HO Research
# This script runs a minimal experiment to verify performance improvements
# Optimized for 64GB RAM system

echo "=========================================="
echo "CloudSim HO Research - Quick Performance Test"
echo "64GB System Optimized Configuration"
echo "=========================================="

# Set Java options for better performance on 64GB system
export JAVA_OPTS="-Xmx32g -Xms16g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

echo "Java Options: $JAVA_OPTS"
echo "Starting quick performance test..."

# Run the experiment with minimal configuration
mvn clean compile exec:java -Dexec.mainClass="org.puneet.cloudsimplus.hiippo.App" \
    -Dexec.args="--quick-test --scenarios=Micro,Small --replications=3" \
    -Djava.util.logging.config.file=src/main/resources/logback.xml

echo "Quick test completed!"
echo "Check results/ directory for output files." 