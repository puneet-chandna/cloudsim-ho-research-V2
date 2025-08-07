package org.puneet.cloudsimplus.hiippo.policy;

import org.cloudsimplus.allocationpolicies.VmAllocationPolicy;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.vms.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.MetricsCollector;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.puneet.cloudsimplus.hiippo.util.ValidationUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Abstract base class for baseline VM allocation policies.
 * Provides common functionality and metrics collection for all baseline algorithms.
 * 
 * @author Puneet Chandna
 * @since 2025-07-15
 */
public abstract class BaselineVmAllocationPolicy extends VmAllocationPolicyAbstract {
    
    private static final Logger logger = LoggerFactory.getLogger(BaselineVmAllocationPolicy.class);
    
    /**
     * Map to track VM to Host allocations for quick lookup
     */
    protected final Map<Long, Host> vmToHostMap;
    
    /**
     * Map to track allocation timestamps for each VM
     */
    protected final Map<Long, Long> allocationTimestamps;
    
    /**
     * Metrics collector instance for tracking allocation performance
     */
    protected MetricsCollector metricsCollector;
    
    /**
     * Allocation validator for ensuring valid placements
     */
    protected AllocationValidator allocationValidator;
    
    /**
     * Counter for successful allocations
     */
    protected int successfulAllocations;
    
    /**
     * Counter for failed allocations
     */
    protected int failedAllocations;
    
    /**
     * Execution start time for performance tracking
     */
    protected long executionStartTime;
    
    /**
     * Flag to enable detailed logging
     */
    protected boolean detailedLogging;
    
    /**
     * Maximum retry attempts for allocation
     */
    protected static final int MAX_RETRY_ATTEMPTS = 3;
    
    /**
     * Creates a new BaselineVmAllocationPolicy
     */
    public BaselineVmAllocationPolicy() {
        super();
        this.vmToHostMap = new ConcurrentHashMap<>();
        this.allocationTimestamps = new ConcurrentHashMap<>();
        this.allocationValidator = new AllocationValidator();
        this.successfulAllocations = 0;
        this.failedAllocations = 0;
        this.detailedLogging = false;
        
        logger.info("Initialized {} allocation policy", getName());
    }
    
    /**
     * Gets the name of the allocation policy
     * 
     * @return The policy name
     */
    public abstract String getName();
    
    /**
     * Sets the host list for this allocation policy
     * 
     * @param hosts The list of hosts to set
     */
    public void setHostList(List<Host> hosts) {
        try {
            // Use reflection to access the protected hostList field from VmAllocationPolicyAbstract
            java.lang.reflect.Field hostListField = VmAllocationPolicyAbstract.class.getDeclaredField("hostList");
            hostListField.setAccessible(true);
            hostListField.set(this, hosts);
            logger.debug("Successfully set {} hosts in {} allocation policy", hosts.size(), getName());
        } catch (Exception e) {
            logger.error("Failed to set host list in {} allocation policy", getName(), e);
        }
    }
    
    /**
     * Allocates a host for the given VM with comprehensive error handling
     * 
     * @param vm The VM to allocate
     * @return boolean indicating if allocation was successful
     * @throws IllegalArgumentException if vm is null
     */
    @Override
    public HostSuitability allocateHostForVm(final Vm vm) {
        if (vm == null) {
            logger.error("Cannot allocate host for null VM");
            return HostSuitability.NULL;
        }
        
        logger.debug("Attempting to allocate host for VM {} using {} policy", 
            vm.getId(), getName());
        
        // Start timing
        long startTime = System.currentTimeMillis();
        
        try {
            // Check memory before allocation
            MemoryManager.checkMemoryUsage("VM_ALLOCATION_" + vm.getId());
            
                    // Validate VM requirements
        if (!validateVmRequirements(vm)) {
            logger.warn("VM {} has invalid requirements", vm.getId());
            failedAllocations++;
            return HostSuitability.NULL;
        }
        
        // Check if VM is already allocated
        if (vmToHostMap.containsKey(vm.getId())) {
            logger.warn("VM {} is already allocated to host {}", 
                vm.getId(), vmToHostMap.get(vm.getId()).getId());
            return HostSuitability.NULL;
        }
        
        // Get suitable hosts
        List<Host> suitableHosts = findSuitableHosts(vm);
        
        if (suitableHosts.isEmpty()) {
            logger.warn("No suitable host found for VM {} (MIPS: {}, RAM: {} MB, BW: {} Mbps, Storage: {} GB)",
                vm.getId(), vm.getTotalMipsCapacity(), vm.getRam().getCapacity(),
                vm.getBw().getCapacity(), vm.getStorage().getCapacity());
            failedAllocations++;
            return HostSuitability.NULL;
        }
        
        // Log suitable hosts found
        if (detailedLogging) {
            logger.debug("Found {} suitable hosts for VM {}: {}", 
                suitableHosts.size(), vm.getId(),
                suitableHosts.stream()
                    .map(h -> h.getId())
                    .collect(Collectors.toList()));
        }
        
        // Attempt allocation with retry logic
        Host bestHost = selectHost(vm, suitableHosts);
        
        if (bestHost == null) {
            logger.warn("No suitable host found for VM {} after selectHost", vm.getId());
            return HostSuitability.NULL;
        }
        
        boolean allocated = performAllocation(vm, bestHost);
        
        // Record allocation time
        long allocationTime = System.currentTimeMillis() - startTime;
        
        if (allocated) {
            successfulAllocations++;
            logger.info("Successfully allocated VM {} to host {} in {} ms after {} attempts", 
                vm.getId(), bestHost.getId(), allocationTime, MAX_RETRY_ATTEMPTS);
            
            // Collect metrics if available
            // if (metricsCollector != null) {
            //     metricsCollector.recordVmAllocation(vm, bestHost, allocationTime);
            // }
            vmToHostMap.put(vm.getId(), bestHost);
            allocationTimestamps.put(vm.getId(), System.currentTimeMillis());
            
            // Log resource utilization after allocation
            if (detailedLogging) {
                logHostUtilization(bestHost);
            }
            return HostSuitability.NULL;
        } else {
            failedAllocations++;
            logger.error("Failed to allocate VM {} after {} attempts in {} ms", 
                vm.getId(), MAX_RETRY_ATTEMPTS, allocationTime);
            
            // Optionally record failure, but no method exists
            return HostSuitability.NULL;
        }
        
    } catch (Exception e) {
        logger.error("Unexpected error during VM {} allocation: {}", 
            vm.getId(), e.getMessage(), e);
        failedAllocations++;
        return HostSuitability.NULL;
    }
    }
    
    /**
     * Selects a host from the list of suitable hosts based on the specific policy
     * 
     * @param vm The VM to allocate
     * @param suitableHosts List of suitable hosts
     * @return Selected host or null if none suitable
     */
    protected abstract Host selectHost(Vm vm, List<Host> suitableHosts);
    
    /**
     * Deallocates the host of the given VM
     * 
     * @param vm The VM to deallocate
     */
    @Override
    public void deallocateHostForVm(final Vm vm) {
        if (vm == null) {
            logger.error("Cannot deallocate host for null VM");
            return;
        }
        
        logger.debug("Deallocating host for VM {}", vm.getId());
        
        try {
            Host host = vm.getHost();
            
            if (host == Host.NULL) {
                logger.warn("VM {} is not allocated to any host", vm.getId());
                return;
            }
            
            // Remove from tracking maps
            vmToHostMap.remove(vm.getId());
            allocationTimestamps.remove(vm.getId());
            
            // Perform deallocation
            ((org.cloudsimplus.hosts.HostAbstract)host).destroyVm(vm);
            
            logger.info("Successfully deallocated VM {} from host {}", 
                vm.getId(), host.getId());
                
        } catch (Exception e) {
            logger.error("Error during VM {} deallocation: {}", 
                vm.getId(), e.getMessage(), e);
        }
    }
    
    /**
     * Gets the host allocated to the given VM
     * 
     * @param vm The VM to check
     * @return The allocated host or Host.NULL if not allocated
     */
    public Host getHostForVm(final Vm vm) {
        if (vm == null) {
            logger.error("Cannot get host for null VM");
            return Host.NULL;
        }
        
        Host host = vmToHostMap.get(vm.getId());
        return host != null ? host : Host.NULL;
    }
    
    /**
     * Checks if a VM has been allocated to a host
     * 
     * @param vm The VM to check
     * @return true if allocated, false otherwise
     */
    public boolean isVmAllocated(final Vm vm) {
        if (vm == null) {
            return false;
        }
        
        return vmToHostMap.containsKey(vm.getId()) && vm.getHost() != Host.NULL;
    }
    
    /**
     * Optimizes the allocation of all VMs
     * 
     * @param vmList List of VMs to optimize
     * @return Map of optimized allocations
     */
    @Override
    public Map<Vm, Host> getOptimizedAllocationMap(final List<? extends Vm> vmList) {
        if (vmList == null || vmList.isEmpty()) {
            logger.warn("No VMs provided for optimization");
            return Collections.emptyMap();
        }
        
        logger.info("Optimizing allocation for {} VMs using {} policy", 
            vmList.size(), getName());
        
        Map<Vm, Host> optimizedMap = new HashMap<>();
        
        try {
            // For baseline policies, we don't perform optimization
            // Just return current allocations
            for (Vm vm : vmList) {
                if (isVmAllocated(vm)) {
                    optimizedMap.put(vm, vm.getHost());
                }
            }
            
            logger.info("Optimization complete. {} VMs allocated", optimizedMap.size());
            
        } catch (Exception e) {
            logger.error("Error during allocation optimization: {}", e.getMessage(), e);
        }
        
        return optimizedMap;
    }
    
    /**
     * Validates VM requirements before allocation
     * 
     * @param vm The VM to validate
     * @return true if requirements are valid, false otherwise
     */
    protected boolean validateVmRequirements(final Vm vm) {
        try {
            if (vm.getTotalMipsCapacity() <= 0) {
                logger.error("VM {} has invalid MIPS requirement: {}", 
                    vm.getId(), vm.getTotalMipsCapacity());
                return false;
            }
            
            if (vm.getRam().getCapacity() <= 0) {
                logger.error("VM {} has invalid RAM requirement: {}", 
                    vm.getId(), vm.getRam().getCapacity());
                return false;
            }
            
            if (vm.getBw().getCapacity() < 0) {
                logger.error("VM {} has invalid bandwidth requirement: {}", 
                    vm.getId(), vm.getBw().getCapacity());
                return false;
            }
            
            if (vm.getStorage().getCapacity() < 0) {
                logger.error("VM {} has invalid storage requirement: {}", 
                    vm.getId(), vm.getStorage().getCapacity());
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            logger.error("Error validating VM {} requirements: {}", 
                vm.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Finds all hosts suitable for the given VM
     * 
     * @param vm The VM to place
     * @return List of suitable hosts
     */
    protected List<Host> findSuitableHosts(final Vm vm) {
        List<Host> suitableHosts = new ArrayList<>();
        
        try {
            int totalHosts = getHostList().size();
            int suitableCount = 0;
            int validatorRejectedCount = 0;
            
            for (Host host : getHostList()) {
                if (host == null || host == Host.NULL) {
                    continue;
                }
                
                if (Boolean.TRUE.equals(host.isSuitableForVm(vm))) {
                    suitableCount++;
                    // Additional validation
                    if (allocationValidator != null) {
                        AllocationValidator.ValidationResult result = allocationValidator.validateVmPlacement(vm, host);
                        if (result.isValid()) {
                            suitableHosts.add(host);
                        } else {
                            validatorRejectedCount++;
                            if (detailedLogging) {
                                logger.debug("VM {} rejected for host {} by validator: {}", 
                                    vm.getId(), host.getId(), result.getViolations());
                            }
                        }
                    } else {
                        suitableHosts.add(host);
                    }
                }
            }
            
            // Log allocation statistics
            if (detailedLogging || suitableHosts.isEmpty()) {
                logger.info("VM {} allocation - Total hosts: {}, Suitable: {}, Validator rejected: {}, Final suitable: {}", 
                    vm.getId(), totalHosts, suitableCount, validatorRejectedCount, suitableHosts.size());
            }
            
            // Sort by available resources (descending)
            suitableHosts.sort((h1, h2) -> {
                double h1Resources = h1.getRam().getAvailableResource() + 
                                   h1.getTotalAvailableMips();
                double h2Resources = h2.getRam().getAvailableResource() + 
                                   h2.getTotalAvailableMips();
                return Double.compare(h2Resources, h1Resources);
            });
            
        } catch (Exception e) {
            logger.error("Error finding suitable hosts for VM {}: {}", 
                vm.getId(), e.getMessage(), e);
        }
        
        return suitableHosts;
    }
    
    /**
     * Performs the actual allocation of VM to host
     * 
     * @param vm The VM to allocate
     * @param host The selected host
     * @return true if allocation successful, false otherwise
     */
    protected boolean performAllocation(final Vm vm, final Host host) {
        try {
            // Validate placement one more time
            if (!Boolean.TRUE.equals(host.isSuitableForVm(vm))) {
                logger.warn("Host {} is no longer suitable for VM {}", 
                    host.getId(), vm.getId());
                return false;
            }
            
            // Perform allocation using CloudSim Plus method
            HostSuitability suitability = host.createVm(vm);
            boolean allocated = suitability.fully();
            
            if (allocated) {
                // Update tracking maps
                vmToHostMap.put(vm.getId(), host);
                allocationTimestamps.put(vm.getId(), System.currentTimeMillis());
                
                // Log resource utilization after allocation
                if (detailedLogging) {
                    logHostUtilization(host);
                }
                
                logger.debug("Successfully allocated VM {} to host {}", vm.getId(), host.getId());
            } else {
                logger.warn("Failed to allocate VM {} to host {}", vm.getId(), host.getId());
            }
            
            return allocated;
            
        } catch (Exception e) {
            logger.error("Error performing allocation of VM {} to host {}: {}", 
                vm.getId(), host.getId(), e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Logs the utilization of a host
     * 
     * @param host The host to log
     */
    protected void logHostUtilization(final Host host) {
        try {
            double cpuUtilization = host.getCpuPercentUtilization() * 100;
            double ramUtilization = host.getRamUtilization() * 100;
            long availableMips = (long) host.getTotalAvailableMips();
            long availableRam = (long) host.getRam().getAvailableResource();
            
            logger.debug("Host {} utilization - CPU: {:.2f}%, RAM: {:.2f}%, " +
                "Available MIPS: {}, Available RAM: {}", 
                host.getId(), cpuUtilization, ramUtilization, 
                availableMips, availableRam);
                
        } catch (Exception e) {
            logger.error("Error logging host {} utilization: {}", 
                host.getId(), e.getMessage());
        }
    }
    
    /**
     * Sets the metrics collector for this policy
     * 
     * @param metricsCollector The metrics collector
     */
    public void setMetricsCollector(final MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
        logger.debug("Metrics collector set for {} policy", getName());
    }
    
    /**
     * Sets the allocation validator
     * 
     * @param allocationValidator The allocation validator
     */
    public void setAllocationValidator(final AllocationValidator allocationValidator) {
        this.allocationValidator = allocationValidator;
        logger.debug("Allocation validator set for {} policy", getName());
    }
    
    /**
     * Enables or disables detailed logging
     * 
     * @param enabled true to enable, false to disable
     */
    public void setDetailedLogging(final boolean enabled) {
        this.detailedLogging = enabled;
        logger.info("Detailed logging {} for {} policy", 
            enabled ? "enabled" : "disabled", getName());
    }
    
    /**
     * Gets the number of successful allocations
     * 
     * @return The count of successful allocations
     */
    public int getSuccessfulAllocations() {
        return successfulAllocations;
    }
    
    /**
     * Gets the number of failed allocations
     * 
     * @return The count of failed allocations
     */
    public int getFailedAllocations() {
        return failedAllocations;
    }
    
    /**
     * Gets the total number of allocation attempts
     * 
     * @return The total count of attempts
     */
    public int getTotalAllocationAttempts() {
        return successfulAllocations + failedAllocations;
    }
    
    /**
     * Gets the allocation success rate
     * 
     * @return The success rate as a percentage
     */
    public double getSuccessRate() {
        int total = getTotalAllocationAttempts();
        return total > 0 ? (double) successfulAllocations / total * 100 : 0;
    }
    
    /**
     * Resets allocation counters
     */
    public void resetCounters() {
        successfulAllocations = 0;
        failedAllocations = 0;
        logger.debug("Allocation counters reset for {} policy", getName());
    }
    
    /**
     * Gets allocation statistics as a formatted string
     * 
     * @return Formatted statistics string
     */
    public String getStatistics() {
        return String.format(
            "%s Policy Statistics: Total Attempts=%d, Successful=%d, Failed=%d, Success Rate=%.2f%%",
            getName(), getTotalAllocationAttempts(), successfulAllocations, 
            failedAllocations, getSuccessRate()
        );
    }
    
    /**
     * Cleans up resources used by this policy
     */
    public void cleanup() {
        logger.info("Cleaning up {} policy resources", getName());
        
        try {
            vmToHostMap.clear();
            allocationTimestamps.clear();
            resetCounters();
            
            logger.info("Cleanup completed for {} policy", getName());
            
        } catch (Exception e) {
            logger.error("Error during {} policy cleanup: {}", 
                getName(), e.getMessage(), e);
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s[hosts=%d, allocatedVMs=%d, statistics=%s]",
            getName(), getHostList().size(), vmToHostMap.size(), getStatistics());
    }
}