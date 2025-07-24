package org.puneet.cloudsimplus.hiippo.policy;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Comprehensive validation framework for VM allocation policies.
 * Validates allocation correctness, resource constraints, and SLA compliance
 * with detailed error reporting and statistical analysis.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class AllocationValidator {
    private static final Logger logger = LoggerFactory.getLogger(AllocationValidator.class);
    
    // Validation thresholds
    private static final double CPU_TOLERANCE = 0.001; // 0.1% tolerance
    private static final double RAM_TOLERANCE = 0.001;
    private static final double BW_TOLERANCE = 0.001;
    private static final double SLA_THRESHOLD = 0.05; // 5% SLA violation threshold
    
    // Statistical tracking
    private final Map<String, ValidationMetrics> validationHistory = new LinkedHashMap<>();
    private long totalValidations = 0;
    private long failedValidations = 0;
    
    /**
     * Validation result with detailed metrics
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> violations;
        private final double cpuUtilization;
        private final double ramUtilization;
        private final double bwUtilization;
        private final long validationTime;
        
        public ValidationResult(boolean valid, List<String> violations, 
                              double cpuUtilization, double ramUtilization, 
                              double bwUtilization, long validationTime) {
            this.valid = valid;
            this.violations = new ArrayList<>(violations);
            this.cpuUtilization = cpuUtilization;
            this.ramUtilization = ramUtilization;
            this.bwUtilization = bwUtilization;
            this.validationTime = validationTime;
        }
        
        // Getters
        public boolean isValid() { return valid; }
        public List<String> getViolations() { return Collections.unmodifiableList(violations); }
        public double getCpuUtilization() { return cpuUtilization; }
        public double getRamUtilization() { return ramUtilization; }
        public double getBwUtilization() { return bwUtilization; }
        public long getValidationTime() { return validationTime; }
    }
    
    /**
     * Detailed validation metrics for statistical analysis
     */
    private static class ValidationMetrics {
        long totalChecks = 0;
        long successfulChecks = 0;
        Map<String, Integer> violationTypes = new HashMap<>();
        double avgCpuUtilization = 0.0;
        double avgRamUtilization = 0.0;
        double avgBwUtilization = 0.0;
    }
    
    /**
     * Validates complete allocation state across all hosts
     * 
     * @param hosts List of all hosts in the datacenter
     * @return Comprehensive validation result
     */
    public ValidationResult validateAllocation(List<Host> hosts) {
        long startTime = System.currentTimeMillis();
        List<String> violations = new ArrayList<>();
        
        if (hosts == null || hosts.isEmpty()) {
            violations.add("Invalid input: hosts list is null or empty");
            return new ValidationResult(false, violations, 0.0, 0.0, 0.0, 
                                      System.currentTimeMillis() - startTime);
        }
        
        // Phase 1: Validate individual hosts
        Map<String, Double> utilizationStats = validateHosts(hosts, violations);
        
        // Phase 2: Validate global constraints
        validateGlobalConstraints(hosts, violations);
        
        // Phase 3: Validate SLA compliance
        validateSLACompliance(hosts, violations);
        
        // Phase 4: Validate inter-host constraints
        validateInterHostConstraints(hosts, violations);
        
        boolean valid = violations.isEmpty();
        long validationTime = System.currentTimeMillis() - startTime;
        
        // Update statistics
        updateValidationStats(valid, violations, utilizationStats, validationTime);
        
        ValidationResult result = new ValidationResult(
            valid, violations,
            utilizationStats.getOrDefault("cpu", 0.0),
            utilizationStats.getOrDefault("ram", 0.0),
            utilizationStats.getOrDefault("bw", 0.0),
            validationTime
        );
        
        logValidationResult(result, hosts.size());
        return result;
    }
    
    /**
     * Validates allocation for a single host
     * 
     * @param host Host to validate
     * @return Validation result for this specific host
     */
    public ValidationResult validateHostAllocation(Host host) {
        long startTime = System.currentTimeMillis();
        List<String> violations = new ArrayList<>();
        
        if (host == null) {
            violations.add("Host is null");
            return new ValidationResult(false, violations, 0.0, 0.0, 0.0,
                                      System.currentTimeMillis() - startTime);
        }
        
        // Validate host capacity constraints
        validateHostCapacity(host, violations);
        
        // Validate VM allocations on this host
        validateVmAllocations(host, violations);
        
        // Calculate utilization
        double cpuUtil = calculateCpuUtilization(host);
        double ramUtil = calculateRamUtilization(host);
        double bwUtil = calculateBwUtilization(host);
        
        boolean valid = violations.isEmpty();
        long validationTime = System.currentTimeMillis() - startTime;
        
        return new ValidationResult(valid, violations, cpuUtil, ramUtil, bwUtil, validationTime);
    }
    
    /**
     * Validates specific VM placement on a host
     * 
     * @param vm VM to validate placement for
     * @param host Target host for placement
     * @return Validation result for this specific placement
     */
    public ValidationResult validateVmPlacement(Vm vm, Host host) {
        long startTime = System.currentTimeMillis();
        List<String> violations = new ArrayList<>();
        
        // Null checks
        if (vm == null) {
            violations.add("VM is null");
            return new ValidationResult(false, violations, 0.0, 0.0, 0.0,
                                      System.currentTimeMillis() - startTime);
        }
        if (host == null) {
            violations.add("Host is null");
            return new ValidationResult(false, violations, 0.0, 0.0, 0.0,
                                      System.currentTimeMillis() - startTime);
        }
        
        // Resource availability check
        if (!host.isSuitableForVm(vm)) {
            violations.add(String.format(
                "Host %d insufficient for VM %d: CPU=%.2f/%.2f, RAM=%.2f/%.2f, BW=%.2f/%.2f",
                host.getId(), vm.getId(),
                vm.getTotalMipsCapacity(), host.getTotalMipsCapacity(),
                vm.getRam().getCapacity(), host.getRam().getAvailableResource(),
                vm.getBw().getCapacity(), host.getBw().getAvailableResource()
            ));
        }
        
        // Overcommit check
        validateOvercommitConstraints(vm, host, violations);
        
        // Affinity/anti-affinity rules
        validatePlacementRules(vm, host, violations);
        
        // Calculate projected utilization
        double projectedCpu = calculateProjectedCpuUtilization(vm, host);
        double projectedRam = calculateProjectedRamUtilization(vm, host);
        double projectedBw = calculateProjectedBwUtilization(vm, host);
        
        boolean valid = violations.isEmpty();
        long validationTime = System.currentTimeMillis() - startTime;
        
        return new ValidationResult(valid, violations, projectedCpu, projectedRam, projectedBw, validationTime);
    }
    
    /**
     * Phase 1: Validate individual hosts
     */
    private Map<String, Double> validateHosts(List<Host> hosts, List<String> violations) {
        double totalCpu = 0.0, totalRam = 0.0, totalBw = 0.0;
        int validHosts = 0;
        
        for (Host host : hosts) {
            if (host == null) {
                violations.add("Found null host in list");
                continue;
            }
            
            ValidationResult hostResult = validateHostAllocation(host);
            if (!hostResult.isValid()) {
                violations.addAll(hostResult.getViolations());
            } else {
                validHosts++;
                totalCpu += hostResult.getCpuUtilization();
                totalRam += hostResult.getRamUtilization();
                totalBw += hostResult.getBwUtilization();
            }
        }
        
        Map<String, Double> stats = new HashMap<>();
        if (validHosts > 0) {
            stats.put("cpu", totalCpu / validHosts);
            stats.put("ram", totalRam / validHosts);
            stats.put("bw", totalBw / validHosts);
        }
        
        return stats;
    }
    
    /**
     * Validates host capacity constraints
     */
    private void validateHostCapacity(Host host, List<String> violations) {
        // CPU overcommit check
        double allocatedMips = host.getVmList().stream()
            .mapToDouble(vm -> {
                Object mips = host.getVmScheduler().getAllocatedMips(vm);
                if (mips instanceof Number) return ((Number) mips).doubleValue();
                else return 0.0;
            }).sum();
        double totalMips = host.getTotalMipsCapacity();
        if (allocatedMips > totalMips * (1 + CPU_TOLERANCE)) {
            violations.add(String.format(
                "Host %d CPU overcommit: %.2f/%.2f (%.2f%%)",
                host.getId(), allocatedMips, totalMips, (allocatedMips/totalMips)*100
            ));
        }
        
        // RAM overcommit check
        long allocatedRam = host.getRam().getAllocatedResource();
        long totalRam = host.getRam().getCapacity();
        if (allocatedRam > totalRam * (1 + RAM_TOLERANCE)) {
            violations.add(String.format(
                "Host %d RAM overcommit: %d/%d (%.2f%%)",
                host.getId(), allocatedRam, totalRam, (allocatedRam*100.0)/totalRam
            ));
        }
        
        // BW overcommit check
        long allocatedBw = host.getBw().getAllocatedResource();
        long totalBw = host.getBw().getCapacity();
        if (allocatedBw > totalBw * (1 + BW_TOLERANCE)) {
            violations.add(String.format(
                "Host %d BW overcommit: %d/%d (%.2f%%)",
                host.getId(), allocatedBw, totalBw, (allocatedBw*100.0)/totalBw
            ));
        }
    }
    
    /**
     * Validates VM allocations on a host
     */
    private void validateVmAllocations(Host host, List<String> violations) {
        int vmCount = host.getVmCreatedList().size();
        if (vmCount == 0) {
            return; // Empty host is valid
        }
        
        // Check for duplicate VM IDs
        Set<Integer> vmIds = new HashSet<>();
        for (Vm vm : host.getVmCreatedList()) {
            if (!vmIds.add((int)vm.getId())) {
                violations.add(String.format(
                    "Host %d has duplicate VM ID: %d", host.getId(), vm.getId()
                ));
            }
            
            // Ensure VM is actually allocated
            if (vm.getHost() == null || vm.getHost().getId() != host.getId()) {
                violations.add(String.format(
                    "VM %d references incorrect host: expected %d, found %d",
                    vm.getId(), host.getId(), 
                    vm.getHost() != null ? vm.getHost().getId() : null
                ));
            }
        }
    }
    
    /**
     * Phase 2: Validate global constraints
     */
    private void validateGlobalConstraints(List<Host> hosts, List<String> violations) {
        Set<Integer> globalVmIds = new HashSet<>();
        Set<Integer> duplicateVmIds = new HashSet<>();
        
        for (Host host : hosts) {
            for (Vm vm : host.getVmCreatedList()) {
                int vmId = (int)vm.getId();
                if (!globalVmIds.add(vmId)) {
                    duplicateVmIds.add(vmId);
                }
            }
        }
        
        if (!duplicateVmIds.isEmpty()) {
            violations.add("Duplicate VM IDs across hosts: " + duplicateVmIds);
        }
        
        // Validate total resource usage
        long totalAllocatedRam = hosts.stream()
            .mapToLong(h -> h.getRam().getAllocatedResource())
            .sum();
        long totalRam = hosts.stream()
            .mapToLong(h -> h.getRam().getCapacity())
            .sum();
            
        if (totalAllocatedRam > totalRam * (1 + RAM_TOLERANCE)) {
            violations.add(String.format(
                "Global RAM overcommit: %d/%d (%.2f%%)",
                totalAllocatedRam, totalRam, (totalAllocatedRam*100.0)/totalRam
            ));
        }
    }
    
    /**
     * Phase 3: Validate SLA compliance
     */
    private void validateSLACompliance(List<Host> hosts, List<String> violations) {
        for (Host host : hosts) {
            double cpuUtilization = calculateCpuUtilization(host);
            double ramUtilization = calculateRamUtilization(host);
            
            // Check for resource exhaustion
            if (cpuUtilization > 1.0 - SLA_THRESHOLD) {
                violations.add(String.format(
                    "Host %d CPU near exhaustion: %.2f%% utilization",
                    host.getId(), cpuUtilization * 100
                ));
            }
            
            if (ramUtilization > 1.0 - SLA_THRESHOLD) {
                violations.add(String.format(
                    "Host %d RAM near exhaustion: %.2f%% utilization",
                    host.getId(), ramUtilization * 100
                ));
            }
            
            // Check VM performance degradation
            for (Vm vm : host.getVmCreatedList()) {
                double vmCpuUtil = vm.getCpuPercentUtilization();
                if (vmCpuUtil > 0.95) {
                    violations.add(String.format(
                        "VM %d on host %d approaching CPU limit: %.2f%%",
                        vm.getId(), host.getId(), vmCpuUtil * 100
                    ));
                }
            }
        }
    }
    
    /**
     * Phase 4: Validate inter-host constraints
     */
    private void validateInterHostConstraints(List<Host> hosts, List<String> violations) {
        // Validate network topology constraints
        Set<String> networkSegments = new HashSet<>();
        for (Host host : hosts) {
            // Placeholder for network validation
            // In real implementation, check network connectivity
        }
        
        // Validate storage constraints
        long totalStorage = hosts.stream()
            .mapToLong(h -> h.getStorage().getCapacity())
            .sum();
        // Additional storage validation logic
    }
    
    /**
     * Utility methods for utilization calculations
     */
    private double calculateCpuUtilization(Host host) {
        double allocatedMips = host.getVmList().stream()
            .mapToDouble(vm -> {
                Object mips = host.getVmScheduler().getAllocatedMips(vm);
                if (mips instanceof Number) return ((Number) mips).doubleValue();
                else return 0.0;
            }).sum();
        return allocatedMips / host.getTotalMipsCapacity();
    }
    
    private double calculateRamUtilization(Host host) {
        return (double) host.getRam().getAllocatedResource() / host.getRam().getCapacity();
    }
    
    private double calculateBwUtilization(Host host) {
        return (double) host.getBw().getAllocatedResource() / host.getBw().getCapacity();
    }
    
    private double calculateProjectedCpuUtilization(Vm vm, Host host) {
        double currentUtil = calculateCpuUtilization(host);
        double vmMips = vm.getTotalMipsCapacity();
        double hostMips = host.getTotalMipsCapacity();
        return currentUtil + (vmMips / hostMips);
    }
    
    private double calculateProjectedRamUtilization(Vm vm, Host host) {
        double currentUtil = calculateRamUtilization(host);
        long vmRam = vm.getRam().getCapacity();
        long hostRam = host.getRam().getCapacity();
        return currentUtil + (double) vmRam / hostRam;
    }
    
    private double calculateProjectedBwUtilization(Vm vm, Host host) {
        double currentUtil = calculateBwUtilization(host);
        long vmBw = vm.getBw().getCapacity();
        long hostBw = host.getBw().getCapacity();
        return currentUtil + (double) vmBw / hostBw;
    }
    
    /**
     * Overcommit validation
     */
    private void validateOvercommitConstraints(Vm vm, Host host, List<String> violations) {
        // Check if placement would exceed overcommit ratios
        double allocatedMips = host.getVmList().stream()
            .mapToDouble(v -> {
                Object mips = host.getVmScheduler().getAllocatedMips(v);
                if (mips instanceof Number) return ((Number) mips).doubleValue();
                else return 0.0;
            }).sum();
        double cpuRatio = (allocatedMips + vm.getTotalMipsCapacity()) / host.getTotalMipsCapacity();
        double ramRatio = (host.getRam().getAllocatedResource() + vm.getRam().getCapacity())
                         / (double) host.getRam().getCapacity();
        
        if (cpuRatio > 1.5) {
            violations.add(String.format(
                "CPU overcommit ratio too high: %.2f", cpuRatio
            ));
        }
        
        if (ramRatio > 1.2) {
            violations.add(String.format(
                "RAM overcommit ratio too high: %.2f", ramRatio
            ));
        }
    }
    
    /**
     * Placement rules validation
     */
    private void validatePlacementRules(Vm vm, Host host, List<String> violations) {
        // Check for anti-affinity rules
        for (Vm existingVm : host.getVmCreatedList()) {
            if (vm.getId() == existingVm.getId()) {
                continue; // Same VM is valid
            }
            
            // Example: Check if VMs should not be on same host
            if (hasAntiAffinity(vm, existingVm)) {
                violations.add(String.format(
                    "Anti-affinity violation: VM %d conflicts with VM %d on host %d",
                    vm.getId(), existingVm.getId(), host.getId()
                ));
            }
        }
    }
    
    /**
     * Anti-affinity check (placeholder implementation)
     */
    private boolean hasAntiAffinity(Vm vm1, Vm vm2) {
        // In real implementation, check tags, groups, or other attributes
        return false;
    }
    
    /**
     * Statistical tracking
     */
    private void updateValidationStats(boolean valid, List<String> violations,
                                     Map<String, Double> utilStats, long duration) {
        totalValidations++;
        if (!valid) {
            failedValidations++;
        }
        
        String key = valid ? "SUCCESS" : "FAILURE";
        ValidationMetrics metrics = validationHistory.computeIfAbsent(key, k -> new ValidationMetrics());
        
        metrics.totalChecks++;
        if (valid) {
            metrics.successfulChecks++;
        }
        
        for (String violation : violations) {
            metrics.violationTypes.merge(violation.split(":")[0], 1, Integer::sum);
        }
        
        if (valid && utilStats != null) {
            metrics.avgCpuUtilization = (metrics.avgCpuUtilization * (metrics.totalChecks - 1) 
                                         + utilStats.getOrDefault("cpu", 0.0)) / metrics.totalChecks;
            metrics.avgRamUtilization = (metrics.avgRamUtilization * (metrics.totalChecks - 1) 
                                         + utilStats.getOrDefault("ram", 0.0)) / metrics.totalChecks;
            metrics.avgBwUtilization = (metrics.avgBwUtilization * (metrics.totalChecks - 1) 
                                        + utilStats.getOrDefault("bw", 0.0)) / metrics.totalChecks;
        }
    }
    
    /**
     * Logging utility for validation results
     */
    private void logValidationResult(ValidationResult result, int hostCount) {
        logger.info("Validation completed for {} hosts - Valid: {}, Time: {}ms, Violations: {}",
            hostCount, result.isValid(), result.getValidationTime(), result.getViolations().size());
        
        if (!result.isValid()) {
            logger.warn("Validation violations: {}", result.getViolations());
        }
    }
    
    /**
     * Get validation statistics
     */
    public Map<String, Object> getValidationStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalValidations", totalValidations);
        stats.put("failedValidations", failedValidations);
        stats.put("successRate", totalValidations > 0 ? 
                  (double) (totalValidations - failedValidations) / totalValidations : 0.0);
        
        for (Map.Entry<String, ValidationMetrics> entry : validationHistory.entrySet()) {
            stats.put("metrics." + entry.getKey(), entry.getValue());
        }
        
        return stats;
    }
    
    /**
     * Reset validation statistics
     */
    public void resetStatistics() {
        validationHistory.clear();
        totalValidations = 0;
        failedValidations = 0;
    }
    
    /**
     * Generate detailed validation report
     */
    public String generateValidationReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== Allocation Validation Report ===\n");
        report.append(String.format("Total Validations: %d\n", totalValidations));
        report.append(String.format("Failed Validations: %d\n", failedValidations));
        report.append(String.format("Success Rate: %.2f%%\n", 
            totalValidations > 0 ? 100.0 * (totalValidations - failedValidations) / totalValidations : 0.0));
        
        for (Map.Entry<String, ValidationMetrics> entry : validationHistory.entrySet()) {
            report.append(String.format("\n%s Metrics:\n", entry.getKey()));
            report.append(String.format("  Total Checks: %d\n", entry.getValue().totalChecks));
            report.append(String.format("  Successful Checks: %d\n", entry.getValue().successfulChecks));
            report.append(String.format("  Average CPU Utilization: %.2f%%\n", 
                entry.getValue().avgCpuUtilization * 100));
            report.append(String.format("  Average RAM Utilization: %.2f%%\n", 
                entry.getValue().avgRamUtilization * 100));
            report.append(String.format("  Average BW Utilization: %.2f%%\n", 
                entry.getValue().avgBwUtilization * 100));
            
            if (!entry.getValue().violationTypes.isEmpty()) {
                report.append("  Violation Types:\n");
                entry.getValue().violationTypes.forEach((type, count) ->
                    report.append(String.format("    %s: %d\n", type, count))
                );
            }
        }
        
        return report.toString();
    }
}

/**
🔗 Integration Points
Usage Example in CloudSimHOSimulation
java
Copy
// Validate allocation after optimization
AllocationValidator validator = new AllocationValidator();
ValidationResult result = validator.validateAllocation(datacenter.getHostList());

if (!result.isValid()) {
    logger.error("Invalid allocation detected: {}", result.getViolations());
    throw new ValidationException("Allocation validation failed");
}
Usage Example in ExperimentRunner
java
Copy
// Validate each allocation decision
for (Vm vm : vmList) {
    Host allocatedHost = vm.getHost();
    if (allocatedHost != null) {
        ValidationResult placementResult = validator.validateVmPlacement(vm, allocatedHost);
        if (!placementResult.isValid()) {
            logger.warn("Invalid placement: {}", placementResult.getViolations());
        }
    }
}
*/
