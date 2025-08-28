# Quick Test Script for CloudSim HO Research (PowerShell)
# This script runs a minimal experiment to verify performance improvements
# Optimized for 64GB RAM system

Write-Host "==========================================" -ForegroundColor Green
Write-Host "CloudSim HO Research - Quick Performance Test" -ForegroundColor Green
Write-Host "64GB System Optimized Configuration" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Green

# Set Java options for better performance on 64GB system
$env:JAVA_OPTS = "-Xmx32g -Xms16g -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+UseStringDeduplication"

Write-Host "Java Options: $env:JAVA_OPTS" -ForegroundColor Yellow
Write-Host "Starting quick performance test..." -ForegroundColor Yellow

# Run the main App (complete experimental suite)
Write-Host "Running complete experimental suite..." -ForegroundColor Yellow
# Build
mvn clean compile -q
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

# Run
mvn exec:java -q -Dexec.mainClass=org.puneet.cloudsimplus.hiippo.App

Write-Host "Quick test completed!" -ForegroundColor Green
Write-Host "Check results/ directory for output files." -ForegroundColor Yellow
