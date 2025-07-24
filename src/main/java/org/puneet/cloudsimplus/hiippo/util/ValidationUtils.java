package org.puneet.cloudsimplus.hiippo.util;

import org.cloudsimplus.cloudlets.Cloudlet;
import org.cloudsimplus.datacenters.Datacenter;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.resources.Pe;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.brokers.DatacenterBroker;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusParameters;
import org.puneet.cloudsimplus.hiippo.algorithm.AlgorithmConstants;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Utility class for validating simulation inputs, outputs, and configurations
 * in the Hippopotamus Optimization research framework.
 * 
 * Provides comprehensive validation methods for VMs, Hosts, algorithm parameters,
 * and simulation results to ensure data integrity and experiment validity.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
public class ValidationUtils {
    private static final Logger logger = LoggerFactory.getLogger(ValidationUtils.class);
    
    // Validation thresholds and limits
    private static final double EPSILON = 1e-9;
    private static final int MIN_VMS = 1;
    private static final int MAX_VMS = 10000;
    private static final int MIN_HOSTS = 1;
    private static final int MAX_HOSTS = 1000;
    
    // Resource limits
    private static final double MIN_MIPS = 100;
    private static final double MAX_MIPS = 1000000;
    private static final long MIN_RAM_MB = 128;
    private static final long MAX_RAM_MB = 1048576; // 1TB
    private static final long MIN_STORAGE_MB = 1024;
    private static final long MAX_STORAGE_MB = 10485760; // 10TB
    private static final long MIN_BW_MBPS = 10;
    private static final long MAX_BW_MBPS = 100000; // 100Gbps
    
    // Algorithm parameter limits
    private static final int MIN_POPULATION_SIZE = 2;
    private static final int MAX_POPULATION_SIZE = 1000;
    private static final int MIN_ITERATIONS = 1;
    private static final int MAX_ITERATIONS = 10000;
    
    /**
     * Private constructor to prevent instantiation
     */
    private ValidationUtils() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
    
    /**
     * Validates a list of VMs for simulation
     * 
     * @param vms List of VMs to validate
     * @return true if all VMs are valid, false otherwise
     * @throws ValidationException if critical validation errors occur
     */
    public static boolean validateVms(List<Vm> vms) throws ValidationException {
        if (vms == null) {
            logger.error("VM list is null");
            throw new ValidationException("VM list cannot be null");
        }
        
        if (vms.isEmpty()) {
            logger.error("VM list is empty");
            throw new ValidationException("VM list cannot be empty");
        }
        
        if (vms.size() < MIN_VMS || vms.size() > MAX_VMS) {
            logger.error("Invalid number of VMs: {}. Expected between {} and {}", 
                        vms.size(), MIN_VMS, MAX_VMS);
            throw new ValidationException(
                String.format("Number of VMs must be between %d and %d", MIN_VMS, MAX_VMS));
        }
        
        logger.debug("Validating {} VMs", vms.size());
        
        Set<Long> vmIds = new HashSet<>();
        boolean allValid = true;
        
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            
            if (vm == null) {
                logger.error("VM at index {} is null", i);
                allValid = false;
                continue;
            }
            
            // Validate VM ID uniqueness
            if (vmIds.contains(vm.getId())) {
                logger.error("Duplicate VM ID found: {}", vm.getId());
                allValid = false;
            }
            vmIds.add(vm.getId());
            
            // Validate individual VM
            if (!validateSingleVm(vm)) {
                allValid = false;
            }
        }
        
        if (!allValid) {
            throw new ValidationException("One or more VMs failed validation");
        }
        
        logger.info("Successfully validated {} VMs", vms.size());
        return true;
    }
    
    /**
     * Validates a single VM instance
     * 
     * @param vm The VM to validate
     * @return true if VM is valid, false otherwise
     */
    private static boolean validateSingleVm(Vm vm) {
        boolean valid = true;
        
        // Validate MIPS
        if (vm.getTotalMipsCapacity() < MIN_MIPS || vm.getTotalMipsCapacity() > MAX_MIPS) {
            logger.error("VM {} has invalid MIPS: {}. Expected between {} and {}", 
                        vm.getId(), vm.getTotalMipsCapacity(), MIN_MIPS, MAX_MIPS);
            valid = false;
        }
        
        // Validate PEs
        if (vm.getPesNumber() <= 0 || vm.getPesNumber() > 128) {
            logger.error("VM {} has invalid number of PEs: {}", vm.getId(), vm.getPesNumber());
            valid = false;
        }
        
        // Validate RAM
        if (vm.getRam().getCapacity() < MIN_RAM_MB || vm.getRam().getCapacity() > MAX_RAM_MB) {
            logger.error("VM {} has invalid RAM: {} MB", vm.getId(), vm.getRam().getCapacity());
            valid = false;
        }
        
        // Validate Storage
        if (vm.getStorage().getCapacity() < MIN_STORAGE_MB || 
            vm.getStorage().getCapacity() > MAX_STORAGE_MB) {
            logger.error("VM {} has invalid storage: {} MB", 
                        vm.getId(), vm.getStorage().getCapacity());
            valid = false;
        }
        
        // Validate Bandwidth
        if (vm.getBw().getCapacity() < MIN_BW_MBPS || vm.getBw().getCapacity() > MAX_BW_MBPS) {
            logger.error("VM {} has invalid bandwidth: {} Mbps", 
                        vm.getId(), vm.getBw().getCapacity());
            valid = false;
        }
        
        logger.trace("VM {} validation result: {}", vm.getId(), valid);
        return valid;
    }
    
    /**
     * Validates a list of Hosts for simulation
     * 
     * @param hosts List of hosts to validate
     * @return true if all hosts are valid, false otherwise
     * @throws ValidationException if critical validation errors occur
     */
    public static boolean validateHosts(List<Host> hosts) throws ValidationException {
        if (hosts == null) {
            logger.error("Host list is null");
            throw new ValidationException("Host list cannot be null");
        }
        
        if (hosts.isEmpty()) {
            logger.error("Host list is empty");
            throw new ValidationException("Host list cannot be empty");
        }
        
        if (hosts.size() < MIN_HOSTS || hosts.size() > MAX_HOSTS) {
            logger.error("Invalid number of hosts: {}. Expected between {} and {}", 
                        hosts.size(), MIN_HOSTS, MAX_HOSTS);
            throw new ValidationException(
                String.format("Number of hosts must be between %d and %d", MIN_HOSTS, MAX_HOSTS));
        }
        
        logger.debug("Validating {} hosts", hosts.size());
        
        Set<Long> hostIds = new HashSet<>();
        boolean allValid = true;
        
        for (int i = 0; i < hosts.size(); i++) {
            Host host = hosts.get(i);
            
            if (host == null) {
                logger.error("Host at index {} is null", i);
                allValid = false;
                continue;
            }
            
            // Validate Host ID uniqueness
            if (hostIds.contains(host.getId())) {
                logger.error("Duplicate Host ID found: {}", host.getId());
                allValid = false;
            }
            hostIds.add(host.getId());
            
            // Validate individual host
            if (!validateSingleHost(host)) {
                allValid = false;
            }
        }
        
        if (!allValid) {
            throw new ValidationException("One or more hosts failed validation");
        }
        
        logger.info("Successfully validated {} hosts", hosts.size());
        return true;
    }
    
    /**
     * Validates a single Host instance
     * 
     * @param host The host to validate
     * @return true if host is valid, false otherwise
     */
    private static boolean validateSingleHost(Host host) {
        boolean valid = true;
        
        // Validate MIPS
        if (host.getTotalMipsCapacity() < MIN_MIPS || host.getTotalMipsCapacity() > MAX_MIPS) {
            logger.error("Host {} has invalid MIPS: {}. Expected between {} and {}", 
                        host.getId(), host.getTotalMipsCapacity(), MIN_MIPS, MAX_MIPS);
            valid = false;
        }
        
        // Validate PEs
        if (host.getPesNumber() <= 0 || host.getPesNumber() > 256) {
            logger.error("Host {} has invalid number of PEs: {}", 
                        host.getId(), host.getPesNumber());
            valid = false;
        }
        
        // Validate RAM
        if (host.getRam().getCapacity() < MIN_RAM_MB || host.getRam().getCapacity() > MAX_RAM_MB) {
            logger.error("Host {} has invalid RAM: {} MB", 
                        host.getId(), host.getRam().getCapacity());
            valid = false;
        }
        
        // Validate Storage
        if (host.getStorage().getCapacity() < MIN_STORAGE_MB || 
            host.getStorage().getCapacity() > MAX_STORAGE_MB) {
            logger.error("Host {} has invalid storage: {} MB", 
                        host.getId(), host.getStorage().getCapacity());
            valid = false;
        }
        
        // Validate Bandwidth
        if (host.getBw().getCapacity() < MIN_BW_MBPS || host.getBw().getCapacity() > MAX_BW_MBPS) {
            logger.error("Host {} has invalid bandwidth: {} Mbps", 
                        host.getId(), host.getBw().getCapacity());
            valid = false;
        }
        
        // Validate schedulers
        if (host.getVmScheduler() == null) {
            logger.error("Host {} has null VM scheduler", host.getId());
            valid = false;
        }
        
        logger.trace("Host {} validation result: {}", host.getId(), valid);
        return valid;
    }
    
    /**
     * Validates resource allocation feasibility
     * 
     * @param vms List of VMs to allocate
     * @param hosts List of available hosts
     * @return true if allocation is theoretically feasible, false otherwise
     */
    public static boolean validateAllocationFeasibility(List<Vm> vms, List<Host> hosts) {
        if (vms == null || hosts == null) {
            logger.error("VMs or hosts list is null");
            return false;
        }
        
        logger.debug("Validating allocation feasibility for {} VMs on {} hosts", 
                    vms.size(), hosts.size());
        
        // Calculate total required resources
        double totalRequiredMips = 0;
        long totalRequiredRam = 0;
        long totalRequiredStorage = 0;
        long totalRequiredBw = 0;
        int totalRequiredPes = 0;
        
        for (Vm vm : vms) {
            if (vm == null) continue;
            
            totalRequiredMips += vm.getTotalMipsCapacity();
            totalRequiredRam += vm.getRam().getCapacity();
            totalRequiredStorage += vm.getStorage().getCapacity();
            totalRequiredBw += vm.getBw().getCapacity();
            totalRequiredPes += vm.getPesNumber();
        }
        
        // Calculate total available resources
        double totalAvailableMips = 0;
        long totalAvailableRam = 0;
        long totalAvailableStorage = 0;
        long totalAvailableBw = 0;
        int totalAvailablePes = 0;
        
        for (Host host : hosts) {
            if (host == null) continue;
            
            totalAvailableMips += host.getTotalMipsCapacity();
            totalAvailableRam += host.getRam().getCapacity();
            totalAvailableStorage += host.getStorage().getCapacity();
            totalAvailableBw += host.getBw().getCapacity();
            totalAvailablePes += host.getPesNumber();
        }
        
        // Check feasibility
        boolean feasible = true;
        
        if (totalRequiredMips > totalAvailableMips) {
            logger.warn("Insufficient MIPS: Required={}, Available={}", 
                       totalRequiredMips, totalAvailableMips);
            feasible = false;
        }
        
        if (totalRequiredRam > totalAvailableRam) {
            logger.warn("Insufficient RAM: Required={} MB, Available={} MB", 
                       totalRequiredRam, totalAvailableRam);
            feasible = false;
        }
        
        if (totalRequiredPes > totalAvailablePes) {
            logger.warn("Insufficient PEs: Required={}, Available={}", 
                       totalRequiredPes, totalAvailablePes);
            feasible = false;
        }
        
        logger.info("Allocation feasibility: {} (MIPS: {:.2f}%, RAM: {:.2f}%, PEs: {:.2f}%)", 
                   feasible, 
                   (totalRequiredMips / totalAvailableMips) * 100,
                   (totalRequiredRam / (double) totalAvailableRam) * 100,
                   (totalRequiredPes / (double) totalAvailablePes) * 100);
        
        return feasible;
    }
    
    /**
     * Validates Hippopotamus algorithm parameters
     * 
     * @param parameters The algorithm parameters to validate
     * @return true if parameters are valid, false otherwise
     * @throws ValidationException if critical validation errors occur
     */
    public static boolean validateAlgorithmParameters(HippopotamusParameters parameters) 
            throws ValidationException {
        if (parameters == null) {
            logger.error("Algorithm parameters are null");
            throw new ValidationException("Algorithm parameters cannot be null");
        }
        
        logger.debug("Validating Hippopotamus algorithm parameters");
        boolean valid = true;
        
        // Validate population size
        int popSize = parameters.getPopulationSize();
        if (popSize < MIN_POPULATION_SIZE || popSize > MAX_POPULATION_SIZE) {
            logger.error("Invalid population size: {}. Expected between {} and {}", 
                        popSize, MIN_POPULATION_SIZE, MAX_POPULATION_SIZE);
            valid = false;
        }
        
        // Validate max iterations
        int maxIter = parameters.getMaxIterations();
        if (maxIter < MIN_ITERATIONS || maxIter > MAX_ITERATIONS) {
            logger.error("Invalid max iterations: {}. Expected between {} and {}", 
                        maxIter, MIN_ITERATIONS, MAX_ITERATIONS);
            valid = false;
        }
        
        // Validate convergence threshold
        double convergenceThreshold = parameters.getConvergenceThreshold();
        if (convergenceThreshold <= 0 || convergenceThreshold > 1) {
            logger.error("Invalid convergence threshold: {}. Expected between 0 and 1", 
                        convergenceThreshold);
            valid = false;
        }
        
        // Validate position update parameters (alpha, beta, gamma)
        if (!validateParameterRange(parameters.getAlpha(), 0, 1, "Alpha")) {
            valid = false;
        }
        
        if (!validateParameterRange(parameters.getBeta(), 0, 1, "Beta")) {
            valid = false;
        }
        
        if (!validateParameterRange(parameters.getGamma(), 0, 1, "Gamma")) {
            valid = false;
        }
        
        // Validate fitness weights (should sum to 1.0)
        double weightSum = parameters.getWeightUtilization() + 
                          parameters.getWeightPower() + 
                          parameters.getWeightSLA();
        
        if (Math.abs(weightSum - 1.0) > EPSILON) {
            logger.error("Fitness weights do not sum to 1.0: {} + {} + {} = {}", 
                        parameters.getWeightUtilization(),
                        parameters.getWeightPower(),
                        parameters.getWeightSLA(),
                        weightSum);
            valid = false;
        }
        
        if (!valid) {
            throw new ValidationException("Algorithm parameters validation failed");
        }
        
        logger.info("Algorithm parameters validated successfully");
        return true;
    }
    
    /**
     * Validates a parameter value against a range
     * 
     * @param value The value to validate
     * @param min Minimum allowed value
     * @param max Maximum allowed value
     * @param paramName Name of the parameter for logging
     * @return true if value is within range, false otherwise
     */
    private static boolean validateParameterRange(double value, double min, double max, 
                                                  String paramName) {
        if (value < min || value > max) {
            logger.error("Invalid {}: {}. Expected between {} and {}", 
                        paramName, value, min, max);
            return false;
        }
        return true;
    }
    
    /**
     * Validates metrics values for consistency
     * 
     * @param metrics Map of metric names to values
     * @return true if all metrics are valid, false otherwise
     */
    public static boolean validateMetrics(Map<String, Double> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            logger.error("Metrics map is null or empty");
            return false;
        }
        
        logger.debug("Validating {} metrics", metrics.size());
        boolean valid = true;
        
        // Validate CPU utilization (0-1 range)
        Double cpuUtil = metrics.get("ResourceUtilCPU");
        if (cpuUtil != null && (cpuUtil < 0 || cpuUtil > 1)) {
            logger.error("Invalid CPU utilization: {}. Expected between 0 and 1", cpuUtil);
            valid = false;
        }
        
        // Validate RAM utilization (0-1 range)
        Double ramUtil = metrics.get("ResourceUtilRAM");
        if (ramUtil != null && (ramUtil < 0 || ramUtil > 1)) {
            logger.error("Invalid RAM utilization: {}. Expected between 0 and 1", ramUtil);
            valid = false;
        }
        
        // Validate power consumption (non-negative)
        Double power = metrics.get("PowerConsumption");
        if (power != null && power < 0) {
            logger.error("Invalid power consumption: {}. Cannot be negative", power);
            valid = false;
        }
        
        // Validate SLA violations (non-negative integer)
        Double slaViolations = metrics.get("SLAViolations");
        if (slaViolations != null && (slaViolations < 0 || 
            Math.floor(slaViolations) != slaViolations)) {
            logger.error("Invalid SLA violations: {}. Must be non-negative integer", 
                        slaViolations);
            valid = false;
        }
        
        // Validate execution time (positive)
        Double execTime = metrics.get("ExecutionTime");
        if (execTime != null && execTime <= 0) {
            logger.error("Invalid execution time: {}. Must be positive", execTime);
            valid = false;
        }
        
        // Validate VM counts
        Double vmAllocated = metrics.get("VmAllocated");
        Double vmTotal = metrics.get("VmTotal");
        
        if (vmAllocated != null && vmTotal != null) {
            if (vmAllocated > vmTotal) {
                logger.error("VMs allocated ({}) exceeds total VMs ({})", 
                            vmAllocated, vmTotal);
                valid = false;
            }
            
            if (vmAllocated < 0 || vmTotal < 0) {
                logger.error("Negative VM counts detected: Allocated={}, Total={}", 
                            vmAllocated, vmTotal);
                valid = false;
            }
        }
        
        logger.info("Metrics validation result: {}", valid);
        return valid;
    }
    
    /**
     * Validates a VM placement solution
     * 
     * @param vmToHostMap Map of VM IDs to Host IDs
     * @param vms List of VMs
     * @param hosts List of hosts
     * @return true if placement is valid, false otherwise
     */
    public static boolean validatePlacement(Map<Long, Long> vmToHostMap, 
                                          List<Vm> vms, List<Host> hosts) {
        if (vmToHostMap == null || vms == null || hosts == null) {
            logger.error("Null parameters provided for placement validation");
            return false;
        }
        
        logger.debug("Validating placement of {} VMs on {} hosts", 
                    vms.size(), hosts.size());
        
        // Create host lookup map
        Map<Long, Host> hostMap = hosts.stream()
            .filter(Objects::nonNull)
            .collect(Collectors.toMap(Host::getId, h -> h));
        
        // Track resource usage per host
        Map<Long, ResourceUsage> hostUsage = new HashMap<>();
        for (Host host : hosts) {
            if (host != null) {
                hostUsage.put(host.getId(), new ResourceUsage());
            }
        }
        
        boolean valid = true;
        
        // Validate each VM placement
        for (Vm vm : vms) {
            if (vm == null) continue;
            
            Long hostId = vmToHostMap.get(vm.getId());
            if (hostId == null) {
                logger.warn("VM {} has no host assignment", vm.getId());
                continue;
            }
            
            Host host = hostMap.get(hostId);
            if (host == null) {
                logger.error("VM {} assigned to non-existent host {}", 
                            vm.getId(), hostId);
                valid = false;
                continue;
            }
            
            // Update resource usage
            ResourceUsage usage = hostUsage.get(hostId);
            usage.mips += vm.getTotalMipsCapacity();
            usage.ram += vm.getRam().getCapacity();
            usage.storage += vm.getStorage().getCapacity();
            usage.bw += vm.getBw().getCapacity();
            usage.pes += vm.getPesNumber();
            
            // Check if host can accommodate VM
            if (!canHostAccommodateVm(host, usage)) {
                logger.error("Host {} cannot accommodate VM {} - resource overcommit", 
                            hostId, vm.getId());
                valid = false;
            }
        }
        
        // Log placement summary
        int placedVms = (int) vmToHostMap.values().stream()
            .filter(Objects::nonNull)
            .count();
        
        logger.info("Placement validation complete: {} VMs placed, Valid: {}", 
                   placedVms, valid);
        
        return valid;
    }
    
    /**
     * Checks if a host can accommodate the given resource usage
     * 
     * @param host The host to check
     * @param usage The cumulative resource usage
     * @return true if host can accommodate, false otherwise
     */
    private static boolean canHostAccommodateVm(Host host, ResourceUsage usage) {
        return usage.mips <= host.getTotalMipsCapacity() &&
               usage.ram <= host.getRam().getCapacity() &&
               usage.storage <= host.getStorage().getCapacity() &&
               usage.bw <= host.getBw().getCapacity() &&
               usage.pes <= host.getPesNumber();
    }
    
    /**
     * Validates datacenter configuration
     * 
     * @param datacenter The datacenter to validate
     * @return true if datacenter is valid, false otherwise
     */
    public static boolean validateDatacenter(Datacenter datacenter) {
        if (datacenter == null) {
            logger.error("Datacenter is null");
            return false;
        }
        
        logger.debug("Validating datacenter {}", datacenter.getName());
        
        // Validate hosts
        List<Host> hosts = datacenter.getHostList();
        if (hosts == null || hosts.isEmpty()) {
            logger.error("Datacenter {} has no hosts", datacenter.getName());
            return false;
        }
        
        // Validate allocation policy
        if (datacenter.getVmAllocationPolicy() == null) {
            logger.error("Datacenter {} has null VM allocation policy", 
                        datacenter.getName());
            return false;
        }
        
        // Validate characteristics
        if (datacenter.getCharacteristics() == null) {
            logger.error("Datacenter {} has null characteristics", 
                        datacenter.getName());
            return false;
        }
        
        logger.info("Datacenter {} validated successfully with {} hosts", 
                   datacenter.getName(), hosts.size());
        return true;
    }
    
    /**
     * Validates cloudlet specifications
     * 
     * @param cloudlets List of cloudlets to validate
     * @return true if all cloudlets are valid, false otherwise
     */
    public static boolean validateCloudlets(List<Cloudlet> cloudlets) {
        if (cloudlets == null || cloudlets.isEmpty()) {
            logger.warn("Cloudlet list is null or empty");
            return true; // Empty list is technically valid
        }
        
        logger.debug("Validating {} cloudlets", cloudlets.size());
        boolean valid = true;
        
        for (Cloudlet cloudlet : cloudlets) {
            if (cloudlet == null) {
                logger.error("Null cloudlet found in list");
                valid = false;
                continue;
            }
            
            // Validate cloudlet length
            if (cloudlet.getLength() <= 0) {
                logger.error("Cloudlet {} has invalid length: {}", 
                            cloudlet.getId(), cloudlet.getLength());
                valid = false;
            }
            
            // Validate file sizes
            if (cloudlet.getFileSize() < 0 || cloudlet.getOutputSize() < 0) {
                logger.error("Cloudlet {} has invalid file sizes", cloudlet.getId());
                valid = false;
            }
            
            // Validate PEs requirement
            if (cloudlet.getPesNumber() <= 0 || cloudlet.getPesNumber() > 128) {
                logger.error("Cloudlet {} has invalid PE requirement: {}", 
                            cloudlet.getId(), cloudlet.getPesNumber());
                valid = false;
            }
        }
        
        logger.info("Cloudlet validation result: {}", valid);
        return valid;
    }
    
    /**
     * Validates experiment configuration
     * 
     * @param algorithmName Name of the algorithm
     * @param scenarioName Name of the scenario
     * @param replication Replication number
     * @return true if configuration is valid, false otherwise
     */
    public static boolean validateExperimentConfig(String algorithmName, 
                                                  String scenarioName, 
                                                  int replication) {
        boolean valid = true;
        
        // Validate algorithm name
        if (algorithmName == null || algorithmName.trim().isEmpty()) {
            logger.error("Algorithm name is null or empty");
            valid = false;
        }
        
        // Validate scenario name
        if (scenarioName == null || scenarioName.trim().isEmpty()) {
            logger.error("Scenario name is null or empty");
            valid = false;
        }
        
        // Validate replication number
        if (replication < 0 || replication >= ExperimentConfig.REPLICATION_COUNT) {
            logger.error("Invalid replication number: {}. Expected between 0 and {}", 
                        replication, ExperimentConfig.REPLICATION_COUNT - 1);
            valid = false;
        }
        
        if (valid) {
            logger.debug("Experiment configuration validated: Algorithm={}, Scenario={}, Rep={}", 
                        algorithmName, scenarioName, replication);
        }
        
        return valid;
    }
    
    /**
     * Helper class to track resource usage
     */
    private static class ResourceUsage {
        double mips = 0;
        long ram = 0;
        long storage = 0;
        long bw = 0;
        int pes = 0;
    }
}