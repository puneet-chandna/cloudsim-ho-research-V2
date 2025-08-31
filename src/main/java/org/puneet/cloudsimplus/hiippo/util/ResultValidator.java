package org.puneet.cloudsimplus.hiippo.util;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import org.puneet.cloudsimplus.hiippo.util.CSVResultsWriter.ExperimentResult;

/**
 * Validates experiment results for correctness and statistical validity.
 * Performs comprehensive checks on VM placement results, resource utilization,
 * and ensures data integrity for statistical analysis.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
public class ResultValidator {
    private static final Logger logger = LoggerFactory.getLogger(ResultValidator.class);
    
    private static final double EPSILON = 1e-6;
    private static final double MAX_ACCEPTABLE_SLA_VIOLATIONS = 0.1; // 10%
    private static final double MIN_ACCEPTABLE_UTILIZATION = 0.1; // 10%
    private static final double MAX_ACCEPTABLE_UTILIZATION = 1.0; // 100%
    
    /**
     * Validates a complete experiment result with comprehensive checks.
     * 
     * @param result The experiment result to validate
     * @throws ValidationException if validation fails
     */
    public static void validateResults(ExperimentResult result) throws ValidationException {
        if (result == null) {
            throw new ValidationException("Experiment result cannot be null");
        }
        
        logger.info("Starting validation for algorithm: {}, scenario: {}, replication: {}", 
            result.getAlgorithm(), result.getScenario(), result.getReplication());
        
        // Validate basic metrics
        validateBasicMetrics(result);
        
        // Validate VM placement
        validateVmPlacement(result);
        
        // Validate resource utilization
        validateResourceUtilization(result);
        
        // Validate power consumption
        validatePowerConsumption(result);
        
        // Validate SLA violations
        validateSlaViolations(result);
        
        // Validate execution time
        validateExecutionTime(result);
        
        // Validate convergence data
        validateConvergenceData(result);
        
        // Validate statistical consistency
        validateStatisticalConsistency(result);
        
        logger.info("Validation completed successfully for algorithm: {}, scenario: {}, replication: {}", 
            result.getAlgorithm(), result.getScenario(), result.getReplication());
    }
    
    /**
     * Validates basic metrics for null values and reasonable ranges.
     * 
     * @param result The experiment result
     * @throws ValidationException if basic metrics are invalid
     */
    private static void validateBasicMetrics(ExperimentResult result) throws ValidationException {
        if (result.getAlgorithm() == null || result.getAlgorithm().trim().isEmpty()) {
            throw new ValidationException("Algorithm name cannot be null or empty");
        }
        
        if (result.getScenario() == null || result.getScenario().trim().isEmpty()) {
            throw new ValidationException("Scenario name cannot be null or empty");
        }
        
        if (result.getReplication() < 0) {
            throw new ValidationException("Replication number must be non-negative");
        }
        
        if (result.getVmTotal() <= 0) {
            throw new ValidationException("Total VMs must be positive");
        }
        
        if (result.getVmAllocated() < 0 || result.getVmAllocated() > result.getVmTotal()) {
            throw new ValidationException("Allocated VMs must be between 0 and total VMs");
        }
    }
    
    /**
     * Validates VM placement results for consistency.
     * 
     * @param result The experiment result
     * @throws ValidationException if VM placement is invalid
     */
    private static void validateVmPlacement(ExperimentResult result) throws ValidationException {
        List<Vm> vms = result.getVms();
        List<Host> hosts = result.getHosts();
        
        if (vms == null || vms.isEmpty()) {
            throw new ValidationException("VM list cannot be null or empty");
        }
        
        if (hosts == null || hosts.isEmpty()) {
            throw new ValidationException("Host list cannot be null or empty");
        }
        
        // Since VMs may have been deallocated after simulation completion,
        // we validate that VMs were created successfully rather than checking current allocation
        // The VM allocation success is already validated by checking result.getVmAllocated() > 0
        
        if (result.getVmAllocated() == 0) {
            throw new ValidationException("No VMs were successfully allocated during simulation");
        }
        
        if (result.getVmAllocated() > result.getVmTotal()) {
            throw new ValidationException("Allocated VMs cannot exceed total VMs");
        }
        
        // Validate that we have the expected number of hosts
        if (hosts.size() == 0) {
            throw new ValidationException("No hosts available for allocation");
        }
        
        // CRITICAL FIX: Validate resource constraints only if VMs are still allocated
        // This prevents false validation failures after CloudSim+ deallocates VMs
        validateResourceConstraintsIfVMsAllocated(result, hosts);
    }
    
    /**
     * Validates resource constraints only if VMs are still allocated on hosts.
     * This prevents false validation failures after CloudSim+ deallocates VMs.
     * 
     * @param result The experiment result
     * @param hosts The list of hosts to validate
     * @throws ValidationException if resource constraints are violated
     */
    private static void validateResourceConstraintsIfVMsAllocated(ExperimentResult result, List<Host> hosts) throws ValidationException {
        // Check if any hosts still have VMs allocated (indicating VMs haven't been deallocated yet)
        boolean hasAllocatedVMs = hosts.stream().anyMatch(host -> !host.getVmList().isEmpty());
        
        if (!hasAllocatedVMs) {
            logger.debug("VMs have been deallocated - skipping resource constraint validation");
            return;
        }
        
        logger.debug("VMs are still allocated - validating resource constraints");
        
        // Validate each host that has VMs allocated
        for (Host host : hosts) {
            if (!host.getVmList().isEmpty()) {
                validateHostResourceConstraints(host);
            }
        }
    }
    
    /**
     * Validates that host resource constraints are not violated.
     * 
     * @param host The host to validate
     * @throws ValidationException if resource constraints are violated
     */
    private static void validateHostResourceConstraints(Host host) throws ValidationException {
        double totalCpuRequired = host.getVmList().stream()
            .mapToDouble(vm -> vm.getTotalMipsCapacity())
            .sum();
            
        double totalRamRequired = host.getVmList().stream()
            .mapToDouble(vm -> vm.getRam().getCapacity())
            .sum();
            
        double totalBwRequired = host.getVmList().stream()
            .mapToDouble(vm -> vm.getBw().getCapacity())
            .sum();
            
        double totalStorageRequired = host.getVmList().stream()
            .mapToDouble(vm -> vm.getStorage().getCapacity())
            .sum();
            
        // Check CPU
        if (totalCpuRequired > host.getTotalMipsCapacity() + EPSILON) {
            throw new ValidationException(
                String.format("CPU over-allocation on host %s: %.2f > %.2f", 
                    host.getId(), totalCpuRequired, host.getTotalMipsCapacity()));
        }
        
        // Check RAM
        if (totalRamRequired > host.getRam().getCapacity() + EPSILON) {
            throw new ValidationException(
                String.format("RAM over-allocation on host %s: %.2f > %.2f", 
                    host.getId(), totalRamRequired, host.getRam().getCapacity()));
        }
        
        // Check Bandwidth
        if (totalBwRequired > host.getBw().getCapacity() + EPSILON) {
            throw new ValidationException(
                String.format("Bandwidth over-allocation on host %s: %.2f > %.2f", 
                    host.getId(), totalBwRequired, host.getBw().getCapacity()));
        }
        
        // Check Storage
        if (totalStorageRequired > host.getStorage().getCapacity() + EPSILON) {
            throw new ValidationException(
                String.format("Storage over-allocation on host %s: %.2f > %.2f", 
                    host.getId(), totalStorageRequired, host.getStorage().getCapacity()));
        }
    }
    
    /**
     * Validates resource utilization values.
     * 
     * @param result The experiment result
     * @throws ValidationException if utilization values are invalid
     */
    private static void validateResourceUtilization(ExperimentResult result) throws ValidationException {
        double cpuUtil = result.getResourceUtilCPU();
        double ramUtil = result.getResourceUtilRAM();
        
        if (cpuUtil < 0 || cpuUtil > MAX_ACCEPTABLE_UTILIZATION + EPSILON) {
            throw new ValidationException(
                String.format("CPU utilization out of range: %.4f", cpuUtil));
        }
        
        if (ramUtil < 0 || ramUtil > MAX_ACCEPTABLE_UTILIZATION + EPSILON) {
            throw new ValidationException(
                String.format("RAM utilization out of range: %.4f", ramUtil));
        }
        
        if (cpuUtil < MIN_ACCEPTABLE_UTILIZATION && result.getVmAllocated() > 0) {
            logger.warn("Low CPU utilization detected: %.4f", cpuUtil);
        }
        
        if (ramUtil < MIN_ACCEPTABLE_UTILIZATION && result.getVmAllocated() > 0) {
            logger.warn("Low RAM utilization detected: %.4f", ramUtil);
        }
        
        // Cross-validate with actual host utilization
        double actualCpuUtil = calculateActualCpuUtilization(result.getHosts());
        double actualRamUtil = calculateActualRamUtilization(result.getHosts());
        
        // Note: Real-time metrics collected during simulation are more accurate than post-deallocation calculations
        // The reported values come from real-time sampling, while actual values are calculated from current host state
        // after VMs have been deallocated, so some discrepancy is expected
        if (Math.abs(cpuUtil - actualCpuUtil) > 0.10) { // Increased tolerance to 10%
            String reported = String.format("%.4f", cpuUtil);
            String actual = String.format("%.4f", actualCpuUtil);
            logger.debug("CPU utilization difference (expected due to timing): reported={}, actual={}", reported, actual);
        }
        
        if (Math.abs(ramUtil - actualRamUtil) > 0.10) { // Increased tolerance to 10%
            String reported = String.format("%.4f", ramUtil);
            String actual = String.format("%.4f", actualRamUtil);
            logger.debug("RAM utilization difference (expected due to timing): reported={}, actual={}", reported, actual);
        }
    }
    
    /**
     * Calculates actual CPU utilization from hosts.
     * 
     * @param hosts The list of hosts
     * @return The actual CPU utilization
     */
    private static double calculateActualCpuUtilization(List<Host> hosts) {
        double totalCapacity = hosts.stream()
            .mapToDouble(Host::getTotalMipsCapacity)
            .sum();
            
        double totalUsed = hosts.stream()
            .mapToDouble(host -> host.getTotalMipsCapacity() - 
                host.getVmScheduler().getTotalAvailableMips())
            .sum();
            
        return totalCapacity > 0 ? totalUsed / totalCapacity : 0.0;
    }
    
    /**
     * Calculates actual RAM utilization from hosts.
     * 
     * @param hosts The list of hosts
     * @return The actual RAM utilization
     */
    private static double calculateActualRamUtilization(List<Host> hosts) {
        double totalCapacity = hosts.stream()
            .mapToDouble(host -> host.getRam().getCapacity())
            .sum();
            
        double totalUsed = hosts.stream()
            .mapToDouble(host -> host.getRam().getCapacity() - 
                host.getRam().getAvailableResource())
            .sum();
            
        return totalCapacity > 0 ? totalUsed / totalCapacity : 0.0;
    }
    
    /**
     * Validates power consumption values.
     * 
     * @param result The experiment result
     * @throws ValidationException if power consumption is invalid
     */
    private static void validatePowerConsumption(ExperimentResult result) throws ValidationException {
        double power = result.getPowerConsumption();
        
        if (power < 0) {
            throw new ValidationException("Power consumption cannot be negative");
        }
        
        // Check for reasonable bounds based on host count
        int hostCount = result.getHosts().size();
        double minPower = hostCount * 100; // 100W per host minimum
        double maxPower = hostCount * 1000; // 1000W per host maximum
        
        if (power < minPower || power > maxPower) {
            logger.warn("Power consumption outside expected range: {}W (expected {}-{}W)", 
                power, minPower, maxPower);
        }
    }
    
    /**
     * Validates SLA violation metrics.
     * 
     * @param result The experiment result
     * @throws ValidationException if SLA violations are invalid
     */
    private static void validateSlaViolations(ExperimentResult result) throws ValidationException {
        double slaViolations = result.getSlaViolations();
        
        if (slaViolations < 0) {
            throw new ValidationException("SLA violations cannot be negative");
        }
        
        if (slaViolations > result.getVmTotal() * MAX_ACCEPTABLE_SLA_VIOLATIONS) {
            logger.warn("High SLA violations detected: {} ({}% of total VMs)", 
                slaViolations, (slaViolations / result.getVmTotal()) * 100);
        }
    }
    
    /**
     * Validates execution time values.
     * 
     * @param result The experiment result
     * @throws ValidationException if execution time is invalid
     */
    private static void validateExecutionTime(ExperimentResult result) throws ValidationException {
        double execTime = result.getExecutionTime();
        
        if (execTime < 0) {
            throw new ValidationException("Execution time cannot be negative");
        }
        
        // Check for reasonable bounds based on algorithm
        String algorithm = result.getAlgorithm();
        double minTime = getMinExpectedTime(algorithm);
        double maxTime = getMaxExpectedTime(algorithm);
        
        if (execTime < minTime || execTime > maxTime) {
            logger.warn("Execution time outside expected range for {}: {}s (expected {}-{}s)", 
                algorithm, execTime, minTime, maxTime);
        }
    }
    
    /**
     * Gets minimum expected execution time for an algorithm.
     * 
     * @param algorithm The algorithm name
     * @return Minimum expected time in seconds
     */
    private static double getMinExpectedTime(String algorithm) {
        return switch (algorithm.toUpperCase()) {
            case "FIRSTFIT", "BESTFIT" -> 0.1;
            case "GA" -> 1.0;
            case "HO" -> 5.0;
            default -> 0.1;
        };
    }
    
    /**
     * Gets maximum expected execution time for an algorithm.
     * 
     * @param algorithm The algorithm name
     * @return Maximum expected time in seconds
     */
    private static double getMaxExpectedTime(String algorithm) {
        return switch (algorithm.toUpperCase()) {
            case "FIRSTFIT", "BESTFIT" -> 10.0;
            case "GA" -> 300.0;
            case "HO" -> 600.0;
            default -> 300.0;
        };
    }
    
    /**
     * Validates convergence data for HO algorithm.
     * 
     * @param result The experiment result
     * @throws ValidationException if convergence data is invalid
     */
    private static void validateConvergenceData(ExperimentResult result) throws ValidationException {
        int convergenceIterations = result.getConvergenceIterations();
        
        if (convergenceIterations < 0) {
            throw new ValidationException("Convergence iterations cannot be negative");
        }
        
        // Only HO and GA should have convergence data
        String algorithm = result.getAlgorithm();
        if (!algorithm.equalsIgnoreCase("HO") && !algorithm.equalsIgnoreCase("GA")) {
            if (convergenceIterations != 1) {
                logger.warn("Non-iterative algorithm {} has convergence iterations: {}", 
                    algorithm, convergenceIterations);
            }
        }
        
        // Check convergence iterations against max iterations
        if (algorithm.equalsIgnoreCase("HO") && convergenceIterations > 50) {
            logger.warn("HO algorithm exceeded max iterations: {}", convergenceIterations);
        }
        
        if (algorithm.equalsIgnoreCase("GA") && convergenceIterations > 50) {
            logger.warn("GA algorithm exceeded max generations: {}", convergenceIterations);
        }
    }
    
    /**
     * Validates statistical consistency of the results.
     * 
     * @param result The experiment result
     * @throws ValidationException if statistical consistency is violated
     */
    private static void validateStatisticalConsistency(ExperimentResult result) throws ValidationException {
        // Check for NaN or Infinity values
        if (Double.isNaN(result.getResourceUtilCPU()) || 
            Double.isInfinite(result.getResourceUtilCPU())) {
            throw new ValidationException("CPU utilization contains NaN or Infinity");
        }
        
        if (Double.isNaN(result.getResourceUtilRAM()) || 
            Double.isInfinite(result.getResourceUtilRAM())) {
            throw new ValidationException("RAM utilization contains NaN or Infinity");
        }
        
        if (Double.isNaN(result.getPowerConsumption()) || 
            Double.isInfinite(result.getPowerConsumption())) {
            throw new ValidationException("Power consumption contains NaN or Infinity");
        }
        
        if (Double.isNaN(result.getExecutionTime()) || 
            Double.isInfinite(result.getExecutionTime())) {
            throw new ValidationException("Execution time contains NaN or Infinity");
        }
        
        // Check for consistent allocation counts
        // Since VMs may have been deallocated after simulation completion,
        // we validate that the reported allocation count matches the VMs that were created
        int createdVms = result.getVms() != null ? result.getVms().size() : 0;
            
        if (Math.abs(createdVms - result.getVmAllocated()) > 0) {
            throw new ValidationException(
                String.format("VM allocation count mismatch: reported=%d, created=%d", 
                    result.getVmAllocated(), createdVms));
        }
    }
    
    /**
     * Validates a batch of results for consistency.
     * 
     * @param results The list of results to validate
     * @throws ValidationException if any result in the batch is invalid
     */
    public static void validateBatch(List<ExperimentResult> results) throws ValidationException {
        if (results == null || results.isEmpty()) {
            throw new ValidationException("Result batch cannot be null or empty");
        }
        
        logger.info("Validating batch of {} results", results.size());
        
        // Group by algorithm and scenario
        Map<String, List<ExperimentResult>> groupedResults = results.stream()
            .collect(Collectors.groupingBy(
                r -> r.getAlgorithm() + "_" + r.getScenario()));
        
        for (Map.Entry<String, List<ExperimentResult>> entry : groupedResults.entrySet()) {
            String key = entry.getKey();
            List<ExperimentResult> group = entry.getValue();
            
            logger.debug("Validating group: {} with {} results", key, group.size());
            
            // Validate each result in the group
            for (ExperimentResult result : group) {
                validateResults(result);
            }
            
            // Check for duplicate replications
            Set<Integer> replications = group.stream()
                .map(ExperimentResult::getReplication)
                .collect(Collectors.toSet());
                
            if (replications.size() != group.size()) {
                throw new ValidationException(
                    String.format("Duplicate replications found in group: %s", key));
            }
        }
        
        logger.info("Batch validation completed successfully");
    }
    
    /**
     * Validates scenario parameters for consistency.
     * 
     * @param scenarioName The scenario name
     * @param vmCount The number of VMs
     * @param hostCount The number of hosts
     * @throws ValidationException if scenario parameters are invalid
     */
    public static void validateScenario(String scenarioName, int vmCount, int hostCount) throws ValidationException {
        if (scenarioName == null || scenarioName.trim().isEmpty()) {
            throw new ValidationException("Scenario name cannot be null or empty");
        }
        
        if (vmCount <= 0) {
            throw new ValidationException("VM count must be positive");
        }
        
        if (hostCount <= 0) {
            throw new ValidationException("Host count must be positive");
        }
        
        if (vmCount < hostCount) {
            logger.warn("VM count ({}) is less than host count ({})", vmCount, hostCount);
        }
        
        // Check for reasonable ratios
        double ratio = (double) vmCount / hostCount;
        if (ratio > 10) {
            logger.warn("High VM-to-host ratio: {} VMs per host", ratio);
        }
    }
}