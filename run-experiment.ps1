Param(
    [switch]$SkipTests
)

Write-Host "=========================================="
Write-Host "CloudSim HO Research Experiment Runner"
Write-Host "=========================================="
Write-Host "Heap Configuration: 50GB (initial and max)"
Write-Host "System: Windows PowerShell"
Write-Host ("Date: {0}" -f (Get-Date))
Write-Host "=========================================="

# Ensure Maven is available
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Error "Maven is not installed or not in PATH. Install Maven and reopen PowerShell."
    exit 1
}

# Check total physical memory in GB
try {
    $cs = Get-CimInstance -ClassName Win32_ComputerSystem
    $totalMemGB = [math]::Round($cs.TotalPhysicalMemory / 1GB)
} catch {
    Write-Warning "Unable to determine system memory. Proceeding without memory check."
    $totalMemGB = $null
}

if ($null -ne $totalMemGB) {
    Write-Host "Total system memory: $totalMemGB GB"
    if ($totalMemGB -lt 64) {
        Write-Warning "System has less than 64GB RAM. 50GB heap may cause issues."
    }
}

# Build the project (compile only)
Write-Host "Building project..."
$compileArgs = @('clean','compile','-q')
if ($SkipTests) { $compileArgs += '-DskipTests' }
mvn @compileArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Build failed"
    exit $LASTEXITCODE
}
Write-Host "Build successful!"

# Run the experiment via Maven exec plugin
Write-Host "Starting experiment..."
$execArgs = @('exec:java','-q','-Dexec.mainClass=org.puneet.cloudsimplus.hiippo.App')
mvn @execArgs
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
    Write-Error "Experiment failed! Check logs for error details."
    Write-Host "=========================================="
    exit $LASTEXITCODE
}


