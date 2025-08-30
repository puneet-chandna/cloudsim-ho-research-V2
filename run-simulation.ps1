# CloudSim HO Research Experiment Runner
# Updated for JAR execution with heap configuration
# author: Puneet Chandna
# date: 2025-08-01
# version: 1.0.0
# description: This script runs the CloudSim HO Research Experiment using JAR files.
# It is used to run the experiment with the 50GB heap configuration.

Write-Host "=========================================="
Write-Host "CloudSim HO Research Experiment Runner"
Write-Host "=========================================="
Write-Host "Heap Configuration: 45GB (initial and max)"
Write-Host "System Memory: 64GB"
Write-Host "Date: $(Get-Date)"
Write-Host "=========================================="

# Check available memory
Write-Host "Checking system memory..."
$TOTAL_MEM = [math]::Round((Get-WmiObject -Class Win32_OperatingSystem).TotalVisibleMemorySize / 1MB, 2)
Write-Host "Total system memory: ${TOTAL_MEM}GB"

if ($TOTAL_MEM -lt 64) {
    Write-Host "Warning: System has less than 64GB RAM. 45GB heap may cause issues."
    $continue = Read-Host "Continue anyway? (y/N)"
    if ($continue -ne 'y' -and $continue -ne 'Y') {
        Exit
    }
}

# Check if the JAR file exists
$JAR_PATH = "target/cloudsim-ho-research-experiment.jar"
if (-Not (Test-Path $JAR_PATH)) {
    Write-Host "Error: JAR file not found at $JAR_PATH"
    Exit
}

# Java heap and GC flags
$JAVA_FLAGS = "-Xms45G -Xmx45G -XX:+UseG1GC -XX:MaxGCPauseMillis=200 -XX:+PrintGCDetails -XX:+PrintGCTimeStamps -Xloggc:gc.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof -XX:+DisableExplicitGC"

# Run the experiment with the configured JAR
Write-Host "Starting experiment with 45GB heap configuration..."
Write-Host "This may take 1-2 hours for the complete experimental suite."
Write-Host ""

# Execute JAR file with the configured heap and GC settings
java $JAVA_FLAGS -jar $JAR_PATH

# Check exit status
if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Experiment completed successfully!"
    Write-Host "Results saved in: results/"
    Write-Host "Check logs for detailed information."
    Write-Host "=========================================="
} else {
    Write-Host ""
    Write-Host "=========================================="
    Write-Host "Experiment failed!"
    Write-Host "Check logs for error details."
    Write-Host "=========================================="
    Exit
}
