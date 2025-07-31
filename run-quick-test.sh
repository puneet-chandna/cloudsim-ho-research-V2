#!/bin/bash

# Quick test script for running individual experiments
# Usage: ./run-quick-test.sh <scenario> <algorithm> <replications>
# Example: ./run-quick-test.sh Micro HO 1

if [ $# -ne 3 ]; then
    echo "Usage: $0 <scenario> <algorithm> <replications>"
    echo "Scenarios: Micro, Small, Medium, Large, XLarge, Enterprise"
    echo "Algorithms: HO, FirstFit, GA"
    echo "Example: $0 Micro HO 1"
    exit 1
fi

SCENARIO=$1
ALGORITHM=$2
REPLICATIONS=$3

echo "Running Quick Test - Scenario: $SCENARIO, Algorithm: $ALGORITHM, Replications: $REPLICATIONS"

# Compile first
mvn compile -q

# Run with Maven exec plugin, overriding the main class
mvn exec:java -Dexec.mainClass="org.puneet.cloudsimplus.hiippo.simulation.QuickTest" -Dexec.args="$SCENARIO $ALGORITHM $REPLICATIONS" -q 