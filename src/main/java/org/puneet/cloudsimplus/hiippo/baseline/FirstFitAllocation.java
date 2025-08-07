package org.puneet.cloudsimplus.hiippo.baseline;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.puneet.cloudsimplus.hiippo.util.MetricsCollector;
import org.puneet.cloudsimplus.hiippo.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * First-Fit VM Allocation Policy Implementation.
 * 
 * <p>This class implements the First-Fit allocation algorithm, which places each VM
 * on the first host that has sufficient resources. This is a simple but effective
 * baseline algorithm commonly used in cloud computing research.</p>
 * 
 * <p>Algorithm characteristics:
 * <ul>
 *   <li>Time Complexity: O(n*m) where n is number of VMs and m is number of hosts</li>
 *   <li>Space Complexity: O(1) - no additional space required</li>
 *   <li>Deterministic: Always produces the same result for the same input</li>
 *   <li>Online: Can handle VMs arriving one at a time</li>
 * </ul>
 * </p>
 * 
 * @author Puneet Chandna
 * @version 1.0
 * @since 2025-07-18
 */
public class FirstFitAllocation extends BaselineVmAllocationPolicy {
    
    private static final Logger logger = LoggerFactory.getLogger(FirstFitAllocation.class);
    
    /**
     * Map to track allocation attempts for metrics collection
     */
    private final Map<Long, Integer> allocationAttempts = new ConcurrentHashMap<>();
    
    /**
     * Counter for successful allocations
     */
    private final AtomicInteger successfulAllocations = new AtomicInteger(0);
    
    /**
     * Counter for failed allocations
     */
    private final AtomicInteger failedAllocations = new AtomicInteger(0);
    
    /**
     * Total time spent in allocation (nanoseconds)
     */
    private long totalAllocationTime = 0;
    
    /**
     * Map to track VM to Host mappings for quick lookup
     */
    private final Map<Long, Long> vmToHostMap = new ConcurrentHashMap<>();
    
    /**
     * Creates a new FirstFitAllocation policy instance.
     */
    public FirstFitAllocation() {
        super();
        logger.info("Initializing First-Fit VM Allocation Policy");
    }
    
    /**
     * Allocates a host for the specified VM using the First-Fit algorithm.
     * 
     * <p>The algorithm iterates through the list of hosts in order and selects
     * the first host that has sufficient resources to accommodate the VM.</p>
     * 
     * @param vm The VM to allocate
     * @return true if allocation was successful, false otherwise
     * @throws IllegalArgumentException if vm is null
     */
    @Override
    public HostSuitability allocateHostForVm(Vm vm) {
        // Input validation
        if (vm == null) {
            logger.error("Cannot allocate null VM");
            throw new IllegalArgumentException("VM cannot be null");
        }
        
        // Check if VM is already allocated
        if (vm.isCreated()) {
            logger.debug("VM {} is already allocated to Host {}", 
                vm.getId(), vm.getHost().getId());
            return HostSuitability.NULL;
        }
        
        logger.debug("Attempting to allocate VM {} using First-Fit algorithm", vm.getId());
        
        long startTime = System.nanoTime();
        int attempts = 0;
        
        try {
            // Validate VM requirements
            if (!validateVmRequirements(vm)) {
                logger.warn("VM {} has invalid resource requirements", vm.getId());
                failedAllocations.incrementAndGet();
                return HostSuitability.NULL;
            }
            
            // Get sorted list of hosts (by ID for consistency)
            List<Host> sortedHosts = getHostList().stream()
                .filter(host -> host != null && host.isActive())
                .sorted(Comparator.comparingLong(Host::getId))
                .collect(Collectors.toList());
            
            if (sortedHosts.isEmpty()) {
                logger.error("No active hosts available for allocation");
                failedAllocations.incrementAndGet();
                return HostSuitability.NULL;
            }
            
            logger.debug("Searching through {} active hosts for VM {}", 
                sortedHosts.size(), vm.getId());
            
            // First-Fit algorithm: find first suitable host
            for (Host host : sortedHosts) {
                attempts++;
                
                try {
                    if (isHostSuitableForVm(host, vm)) {
                        logger.debug("Host {} is suitable for VM {} - attempting allocation", 
                            host.getId(), vm.getId());
                        
                        // Attempt allocation using parent class method
                        HostSuitability suitability = super.allocateHostForVm(vm, host);
                        
                        if (suitability != HostSuitability.NULL) {
                            // Track successful allocation
                            vmToHostMap.put(vm.getId(), host.getId());
                            successfulAllocations.incrementAndGet();
                            
                            // Collect metrics
                            collectAllocationMetrics(vm, host, attempts, 
                                System.nanoTime() - startTime);
                            
                            logger.info("Successfully allocated VM {} to Host {} after {} attempts", 
                                vm.getId(), host.getId(), attempts);
                            
                            return HostSuitability.NULL;
                        } else {
                            logger.warn("Allocation of VM {} to Host {} failed despite suitability check", 
                                vm.getId(), host.getId());
                        }
                    } else {
                        logger.trace("Host {} is not suitable for VM {} - continuing search", 
                            host.getId(), vm.getId());
                    }
                } catch (Exception e) {
                    logger.error("Error checking host {} for VM {}: {}", 
                        host.getId(), vm.getId(), e.getMessage());
                    // Continue to next host
                }
            }
            
            // No suitable host found
            logger.warn("No suitable host found for VM {} after {} attempts", 
                vm.getId(), attempts);
            failedAllocations.incrementAndGet();
            allocationAttempts.put(vm.getId(), attempts);
            
            return HostSuitability.NULL;
            
        } catch (Exception e) {
            logger.error("Unexpected error during First-Fit allocation for VM {}: {}", 
                vm.getId(), e.getMessage(), e);
            failedAllocations.incrementAndGet();
            return HostSuitability.NULL;
        } finally {
            totalAllocationTime += (System.nanoTime() - startTime);
            
            // Log memory usage if needed
            if (attempts > 50) {
                MemoryManager.checkMemoryUsage("FirstFit-AfterLargeAllocation");
            }
        }
    }
    
    /**
     * Checks if a host is suitable for a VM.
     * 
     * <p>This method performs comprehensive checks including:
     * <ul>
     *   <li>CPU availability</li>
     *   <li>RAM availability</li>
     *   <li>Storage availability</li>
     *   <li>Bandwidth availability</li>
     *   <li>Host state and failures</li>
     * </ul>
     * </p>
     * 
     * @param host The host to check
     * @param vm The VM to place
     * @return true if the host can accommodate the VM, false otherwise
     */
    private boolean isHostSuitableForVm(Host host, Vm vm) {
        if (host == null || vm == null) {
            return false;
        }
        
        try {
            // CRITICAL FIX: Trust CloudSim+'s built-in suitability check and add minimal validation
            if (!host.isActive() || host.isFailed()) {
                logger.trace("Host {} is not active or has failed", host.getId());
                return false;
            }
            
            // Use CloudSim+'s built-in suitability check
            boolean suitable = host.isSuitableForVm(vm);
            
            // CRITICAL FIX: Trust CloudSim+'s built-in check - no redundant validation needed
            // The host.isSuitableForVm(vm) method already performs comprehensive resource validation
            
            return suitable;
        } catch (Exception e) {
            logger.error("Error checking host {} suitability for VM {}: {}",
                host.getId(), vm.getId(), e.getMessage());
            return false;
        }
    }
    
    /**
     * Collects metrics for the allocation.
     * 
     * @param vm The allocated VM
     * @param host The host where VM was allocated
     * @param attempts Number of hosts checked
     * @param allocationTime Time taken for allocation in nanoseconds
     */
    private void collectAllocationMetrics(Vm vm, Host host, int attempts, long allocationTime) {
        try {
            Map<String, Object> metrics = new HashMap<>();
            metrics.put("algorithm", "FirstFit");
            metrics.put("vmId", vm.getId());
            metrics.put("hostId", host.getId());
            metrics.put("attempts", attempts);
            metrics.put("allocationTimeMs", allocationTime / 1_000_000.0);
            double cpuUtilization = host.getCpuPercentUtilization();
            double ramUtilization = host.getRam().getPercentUtilization();
            double storageUtilization = host.getStorage().getPercentUtilization();
            double bwUtilization = host.getBw().getPercentUtilization();
            metrics.put("hostCpuUtilization", cpuUtilization);
            metrics.put("hostRamUtilization", ramUtilization);
            metrics.put("hostStorageUtilization", storageUtilization);
            metrics.put("hostBwUtilization", bwUtilization);
            metrics.put("vmMips", vm.getTotalMipsCapacity());
            metrics.put("vmRam", vm.getRam().getCapacity());
            metrics.put("vmStorage", vm.getStorage().getCapacity());
            metrics.put("vmBw", vm.getBw().getCapacity());
            // MetricsCollector.recordAllocationMetrics is not defined; skip or implement if needed
            logger.trace("Collected metrics for VM {} allocation to Host {}", 
                vm.getId(), host.getId());
        } catch (Exception e) {
            logger.error("Error collecting metrics for VM {} allocation: {}", 
                vm.getId(), e.getMessage());
        }
    }
    
    /**
     * Deallocates a VM from its host.
     * 
     * @param vm The VM to deallocate
     */
    @Override
    public void deallocateHostForVm(Vm vm) {
        if (vm == null) {
            logger.warn("Cannot deallocate null VM");
            return;
        }
        
        try {
            Host host = vm.getHost();
            if (host != Host.NULL) {
                logger.debug("Deallocating VM {} from Host {}", vm.getId(), host.getId());
                super.deallocateHostForVm(vm);
                vmToHostMap.remove(vm.getId());
                logger.info("Successfully deallocated VM {} from Host {}", 
                    vm.getId(), host.getId());
            } else {
                logger.trace("VM {} is not allocated to any host", vm.getId());
            }
        } catch (Exception e) {
            logger.error("Error deallocating VM {}: {}", vm.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Returns the name of this allocation policy.
     * 
     * @return "FirstFit"
     */
    @Override
    public String getName() {
        return "FirstFit";
    }
    
    /**
     * Gets the convergence iterations for FirstFit (always 1 as it's single-pass).
     * 
     * @return Always 1 for FirstFit algorithm
     */
    public int getConvergenceIterations() {
        return 1; // FirstFit is a single-pass algorithm
    }
    
    /**
     * Gets the optimization time for FirstFit (total allocation time).
     * 
     * @return Total allocation time in milliseconds
     */
    public double getOptimizationTime() {
        return totalAllocationTime / 1_000_000.0; // Convert nanoseconds to milliseconds
    }
    
    /**
     * Gets the total number of successful allocations.
     * 
     * @return Number of successful allocations
     */
    public int getSuccessfulAllocations() {
        return successfulAllocations.get();
    }
    
    /**
     * Gets the total number of failed allocations.
     * 
     * @return Number of failed allocations
     */
    public int getFailedAllocations() {
        return failedAllocations.get();
    }
    
    /**
     * Gets the average allocation time in milliseconds.
     * 
     * @return Average allocation time or 0 if no allocations
     */
    public double getAverageAllocationTimeMs() {
        int totalAllocations = successfulAllocations.get() + failedAllocations.get();
        if (totalAllocations == 0) {
            return 0.0;
        }
        return (totalAllocationTime / 1_000_000.0) / totalAllocations;
    }
    
    /**
     * Gets statistics about this allocation policy.
     * 
     * @return Map containing various statistics
     */
    @Override
    public String getStatistics() {
        return String.format(
            "Algorithm: %s, Success: %d, Failed: %d, Total: %d, SuccessRate: %.2f%%, AvgTime: %.2fms, AvgAttempts: %.2f",
            getName(),
            successfulAllocations.get(),
            failedAllocations.get(),
            successfulAllocations.get() + failedAllocations.get(),
            calculateSuccessRate(),
            getAverageAllocationTimeMs(),
            allocationAttempts.values().stream().mapToInt(Integer::intValue).average().orElse(0.0)
        );
    }
    
    /**
     * Calculates the success rate of allocations.
     * 
     * @return Success rate as a percentage (0-100)
     */
    private double calculateSuccessRate() {
        int total = successfulAllocations.get() + failedAllocations.get();
        if (total == 0) {
            return 0.0;
        }
        return (successfulAllocations.get() * 100.0) / total;
    }
    
    /**
     * Resets all statistics for this policy.
     */
    public void resetStatistics() {
        successfulAllocations.set(0);
        failedAllocations.set(0);
        totalAllocationTime = 0;
        allocationAttempts.clear();
        vmToHostMap.clear();
        logger.info("Reset statistics for First-Fit allocation policy");
    }
    
    /**
     * Gets a string representation of this policy.
     * 
     * @return String representation including statistics
     */
    @Override
    public String toString() {
        return String.format("FirstFitAllocation[success=%d, failed=%d, successRate=%.2f%%, avgTime=%.2fms]",
            successfulAllocations.get(),
            failedAllocations.get(),
            calculateSuccessRate(),
            getAverageAllocationTimeMs());
    }

    /**
     * Selects the first suitable host from the list for the given VM (First-Fit logic).
     */
    @Override
    protected Host selectHost(Vm vm, List<Host> suitableHosts) {
        if (suitableHosts == null) return null;
        for (Host host : suitableHosts) {
            if (isHostSuitableForVm(host, vm)) {
                return host;
            }
        }
        return null;
    }

    /**
     * Returns the first suitable host for the given VM as Optional, as required by VmAllocationPolicyAbstract.
     */
    @Override
    public java.util.Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> suitableHosts = getHostList().stream()
            .filter(h -> isHostSuitableForVm(h, vm))
            .toList();
        Host host = selectHost(vm, suitableHosts);
        return java.util.Optional.ofNullable(host);
    }
}