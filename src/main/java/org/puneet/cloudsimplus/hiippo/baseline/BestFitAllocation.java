package org.puneet.cloudsimplus.hiippo.baseline;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.hosts.HostSuitability;
import org.cloudsimplus.vms.Vm;
import org.cloudsimplus.allocationpolicies.VmAllocationPolicyAbstract;
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.cloudsimplus.datacenters.Datacenter;

/**
 * Best Fit VM Allocation Policy Implementation for CloudSim Plus.
 * 
 * This class implements the Best Fit algorithm for VM placement, which selects 
 * the host that minimizes resource waste for each VM. The algorithm calculates 
 * waste based on multiple resources: CPU, RAM, Bandwidth, and Storage.
 * 
 * Features:
 * - Multi-resource waste calculation with configurable weights
 * - Performance caching with proper invalidation
 * - Comprehensive statistics collection for research
 * - Detailed logging for debugging and analysis
 * - Experimental controls for research flexibility
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-18
 */
public class BestFitAllocation extends BaselineVmAllocationPolicy {
    
    private static final Logger logger = LoggerFactory.getLogger(BestFitAllocation.class);
    
    /**
     * Default weight factors for resource waste calculation (0.0 to 1.0)
     */
    private double cpuWeight = 0.25;
    private double ramWeight = 0.25;
    private double bwWeight = 0.25;
    private double storageWeight = 0.25;
    
    /**
     * Minimum threshold for considering a host suitable (to avoid near-zero capacity hosts)
     */
    private static final double MIN_CAPACITY_THRESHOLD = 0.01;
    
    /**
     * Cache for host waste calculations to improve performance
     */
    private final Map<String, Double> wasteCache = new ConcurrentHashMap<>();
    
    /**
     * Flag to enable/disable caching (useful for experiments)
     */
    private boolean cachingEnabled = true;
    
    /**
     * Statistics tracking for algorithm performance
     */
    private int totalAllocations = 0;
    private int successfulAllocations = 0;
    private int cacheHits = 0;
    private int cacheMisses = 0;
    
    /**
     * Resource-specific wastage statistics
     */
    private final Map<String, Double> totalResourceWastage = new ConcurrentHashMap<>();
    private final Map<String, Long> resourceWastageCount = new ConcurrentHashMap<>();
    
    /**
     * Timing statistics
     */
    private final AtomicLong totalAllocationTime = new AtomicLong(0);
    private final AtomicLong totalHostSearchTime = new AtomicLong(0);
    
    private static final String STORAGE_KEY = "storage";
    
    /**
     * Default constructor for BestFitAllocation.
     */
    public BestFitAllocation() {
        super();
        initializeStatistics();
        logger.info("Initialized BestFit allocation policy with weights - CPU: {}, RAM: {}, BW: {}, Storage: {}", 
            cpuWeight, ramWeight, bwWeight, storageWeight);
    }
    
    /**
     * Constructor with custom resource weights.
     * 
     * @param cpuWeight Weight for CPU resource (0.0 to 1.0)
     * @param ramWeight Weight for RAM resource (0.0 to 1.0)
     * @param bwWeight Weight for Bandwidth resource (0.0 to 1.0)
     * @param storageWeight Weight for Storage resource (0.0 to 1.0)
     */
    public BestFitAllocation(double cpuWeight, double ramWeight, double bwWeight, double storageWeight) {
        super();
        setResourceWeights(cpuWeight, ramWeight, bwWeight, storageWeight);
        initializeStatistics();
        logger.info("Initialized BestFit allocation policy with custom weights - CPU: {}, RAM: {}, BW: {}, Storage: {}", 
            cpuWeight, ramWeight, bwWeight, storageWeight);
    }
    
    /**
     * Initializes statistics tracking structures.
     */
    private void initializeStatistics() {
        totalResourceWastage.put("cpu", 0.0);
        totalResourceWastage.put("ram", 0.0);
        totalResourceWastage.put("bw", 0.0);
        totalResourceWastage.put(STORAGE_KEY, 0.0);
        
        resourceWastageCount.put("cpu", 0L);
        resourceWastageCount.put("ram", 0L);
        resourceWastageCount.put("bw", 0L);
        resourceWastageCount.put(STORAGE_KEY, 0L);
    }
    
    /**
     * Sets custom resource weights for waste calculation.
     * Weights are normalized to sum to 1.0.
     * 
     * @param cpuWeight Weight for CPU resource
     * @param ramWeight Weight for RAM resource
     * @param bwWeight Weight for Bandwidth resource
     * @param storageWeight Weight for Storage resource
     */
    public void setResourceWeights(double cpuWeight, double ramWeight, double bwWeight, double storageWeight) {
        double sum = cpuWeight + ramWeight + bwWeight + storageWeight;
        if (sum <= 0) {
            throw new IllegalArgumentException("Sum of weights must be positive");
        }
        
        // Normalize weights
        this.cpuWeight = cpuWeight / sum;
        this.ramWeight = ramWeight / sum;
        this.bwWeight = bwWeight / sum;
        this.storageWeight = storageWeight / sum;
        
        logger.debug("Resource weights updated - CPU: {}, RAM: {}, BW: {}, Storage: {}", 
            this.cpuWeight, this.ramWeight, this.bwWeight, this.storageWeight);
    }
    
    /**
     * Enables or disables caching for experimental purposes.
     * 
     * @param enabled true to enable caching, false to disable
     */
    public void setCachingEnabled(boolean enabled) {
        this.cachingEnabled = enabled;
        if (!enabled) {
            wasteCache.clear();
        }
        logger.info("Caching {} for BestFit allocation", enabled ? "enabled" : "disabled");
    }
    
    /**
     * Allocates a host for the specified VM using the Best Fit algorithm.
     * 
     * The algorithm:
     * 1. Iterates through all available hosts
     * 2. Calculates multi-resource waste for each suitable host
     * 3. Selects the host with minimum waste
     * 4. Allocates the VM to the selected host
     * 
     * @param vm The VM to allocate
     * @return true if allocation was successful, false otherwise
     * @throws IllegalArgumentException if vm is null
     */
    @Override
    public HostSuitability allocateHostForVm(Vm vm) {
        if (vm == null) {
            logger.error("BestFit: VM cannot be null");
            return HostSuitability.NULL;
        }
        
        totalAllocations++;
        long startTime = System.nanoTime();
        
        logger.info("BestFit: Starting allocation for VM {} (MIPS: {}, RAM: {} MB, BW: {} Mbps, Storage: {} GB)", 
            vm.getId(), vm.getTotalMipsCapacity(), vm.getRam().getCapacity(),
            vm.getBw().getCapacity(), vm.getStorage().getCapacity());
        
        try {
            // Validate VM requirements
            if (!validateVmRequirements(vm)) {
                logger.warn("BestFit: VM {} requirements validation failed", vm.getId());
                failedAllocations++;
                return HostSuitability.NULL;
            }
            
            // CRITICAL FIX: Use parent class findSuitableHosts method
            List<Host> suitableHosts = findSuitableHosts(vm);
            
            logger.info("BestFit: Found {} suitable hosts for VM {}", suitableHosts.size(), vm.getId());
            
            if (suitableHosts.isEmpty()) {
                logger.warn("BestFit: No suitable hosts found for VM {}", vm.getId());
                failedAllocations++;
                return HostSuitability.NULL;
            }
            
            // Select the best host (minimum waste)
            Host bestHost = selectHost(vm, suitableHosts);
            if (bestHost == null) {
                logger.warn("BestFit: No best host selected for VM {}", vm.getId());
                failedAllocations++;
                return HostSuitability.NULL;
            }
            
            logger.info("BestFit: Selected host {} for VM {} (waste: {})", 
                bestHost.getId(), vm.getId(), calculateResourceWastage(bestHost, vm));
            
            // CRITICAL FIX: Use parent class performAllocation method
            boolean allocationSuccess = performAllocation(vm, bestHost);
            
            logger.info("BestFit: Allocation result for VM {} on host {}: {}", 
                vm.getId(), bestHost.getId(), allocationSuccess);
            
            if (!allocationSuccess) {
                logger.warn("BestFit: Failed to allocate VM {} to host {}", vm.getId(), bestHost.getId());
                failedAllocations++;
                return HostSuitability.NULL;
            }
            
            // Update statistics
            successfulAllocations++;
            long allocationTime = System.nanoTime() - startTime;
            totalAllocationTime.addAndGet(allocationTime);
            
            // Log successful allocation
            logger.info("BestFit: Successfully allocated VM {} to host {} in {} ns", 
                vm.getId(), bestHost.getId(), allocationTime);
            
            // Log resource utilization
            logResourceUtilization(bestHost, vm);
            
            // Clear cache for this host
            clearCacheForHost(bestHost);
            
            return HostSuitability.NULL;
            
        } catch (Exception e) {
            logger.error("BestFit: Error allocating VM {}: {}", vm.getId(), e.getMessage(), e);
            failedAllocations++;
            return HostSuitability.NULL;
        }
    }
    
    /**
     * Selects the best host from the list of suitable hosts based on minimum resource waste.
     *
     * @param vm The VM to allocate
     * @param suitableHosts List of suitable hosts
     * @return The best host or null if none found
     */
    @Override
    protected Host selectHost(Vm vm, List<Host> suitableHosts) {
        if (suitableHosts == null || suitableHosts.isEmpty()) {
            return null;
        }
        Host bestHost = null;
        double minWaste = Double.MAX_VALUE;
        for (Host host : suitableHosts) {
            double waste = calculateResourceWastage(host, vm);
            if (waste < minWaste) {
                minWaste = waste;
                bestHost = host;
            }
        }
        return bestHost;
    }
    
    /**
     * Checks if a host is suitable for a VM with additional validation.
     * 
     * @param host The host to check
     * @param vm The VM to place
     * @return true if host can accommodate the VM
     */
    private boolean isHostSuitableForVm(Host host, Vm vm) {
        if (host == null || vm == null) {
            logger.info("Host or VM is null for suitability check");
            return false;
        }
        
        // Check if host is active and not failed
        if (!host.isActive() || host.isFailed()) {
            logger.info("Host {} is not active or failed for VM {}", host.getId(), vm.getId());
            return false;
        }
        
        // Simple resource availability check - use CloudSim Plus built-in method
        try {
            // Use CloudSim Plus's built-in suitability check
            boolean suitable = Boolean.TRUE.equals(host.isSuitableForVm(vm));
            if (!suitable) {
                logger.info("Host {} rejected for VM {} by CloudSim Plus suitability check", host.getId(), vm.getId());
            }
            return suitable;
        } catch (Exception e) {
            logger.warn("Error checking host suitability for VM {} on host {}: {}", vm.getId(), host.getId(), e.getMessage());
            
            // Fallback: manual resource check
            try {
                // Check if host has enough CPU capacity
                double hostAvailableMips = host.getTotalMipsCapacity();
                double vmMips = vm.getTotalMipsCapacity();
                
                // Check if host has enough RAM
                long hostAvailableRam = host.getRam().getAvailableResource();
                long vmRam = vm.getRam().getCapacity();
                
                // Check if host has enough bandwidth
                long hostAvailableBw = host.getBw().getAvailableResource();
                long vmBw = vm.getBw().getCapacity();
                
                // Check if host has enough storage
                long hostAvailableStorage = host.getStorage().getAvailableResource();
                long vmStorage = vm.getStorage().getCapacity();
                
                // All resources must be available
                return hostAvailableMips >= vmMips && 
                       hostAvailableRam >= vmRam && 
                       hostAvailableBw >= vmBw && 
                       hostAvailableStorage >= vmStorage;
                
            } catch (Exception e2) {
                logger.error("Fallback resource check also failed for VM {} on host {}: {}", vm.getId(), host.getId(), e2.getMessage());
                return false;
            }
        }
    }
    
    /**
     * Calculates the total resource wastage for placing a VM on a host.
     * Uses weighted combination of multiple resources.
     * 
     * @param host The target host
     * @param vm The VM to place
     * @return The calculated waste value (lower is better)
     */
    private double calculateResourceWastage(Host host, Vm vm) {
        if (cachingEnabled) {
            String cacheKey = generateCacheKey(host, vm);
            
            // Check cache first
            Double cachedWaste = wasteCache.get(cacheKey);
            if (cachedWaste != null) {
                cacheHits++;
                return cachedWaste;
            }
            
            cacheMisses++;
        }
        
        ResourceWastage wastage = calculateDetailedResourceWastage(host, vm);
        
        if (cachingEnabled) {
            // Cache the result
            String cacheKey = generateCacheKey(host, vm);
            wasteCache.put(cacheKey, wastage.total);
        }
        
        return wastage.total;
    }
    
    /**
     * Calculates detailed resource wastage for all resource types.
     * 
     * @param host The target host
     * @param vm The VM to place
     * @return Detailed wastage information
     */
    private ResourceWastage calculateDetailedResourceWastage(Host host, Vm vm) {
        if (host == null || vm == null) {
            return new ResourceWastage(Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0);
        }
        
        try {
            // Calculate CPU wastage
            double hostTotalMips = host.getTotalMipsCapacity();
            double hostUsedMips = getTotalAllocatedMips(host);
            double vmMips = vm.getTotalMipsCapacity();
            double cpuWastage = Math.max(0, hostTotalMips - (hostUsedMips + vmMips));
            
            // Calculate RAM wastage
            double hostTotalRam = host.getRam().getCapacity();
            double hostUsedRam = host.getRam().getAllocatedResource();
            double vmRam = vm.getRam().getCapacity();
            double ramWastage = Math.max(0, hostTotalRam - (hostUsedRam + vmRam));
            
            // Calculate BW wastage
            double hostTotalBw = host.getBw().getCapacity();
            double hostUsedBw = host.getBw().getAllocatedResource();
            double vmBw = vm.getBw().getCapacity();
            double bwWastage = Math.max(0, hostTotalBw - (hostUsedBw + vmBw));
            
            // Calculate storage wastage
            double hostTotalStorage = host.getStorage().getCapacity();
            double hostUsedStorage = host.getStorage().getAllocatedResource();
            double vmStorage = vm.getStorage().getCapacity();
            double storageWastage = Math.max(0, hostTotalStorage - (hostUsedStorage + vmStorage));
            
            // Normalize wastage values to [0,1] range
            double normalizedCpuWastage = hostTotalMips > 0 ? cpuWastage / hostTotalMips : 0;
            double normalizedRamWastage = hostTotalRam > 0 ? ramWastage / hostTotalRam : 0;
            double normalizedBwWastage = hostTotalBw > 0 ? bwWastage / hostTotalBw : 0;
            double normalizedStorageWastage = hostTotalStorage > 0 ? storageWastage / hostTotalStorage : 0;
            
            // Weighted sum of normalized wastage
            double totalWastage = (cpuWeight * normalizedCpuWastage) + 
                                (ramWeight * normalizedRamWastage) + 
                                (bwWeight * normalizedBwWastage) + 
                                (storageWeight * normalizedStorageWastage);
            
            logger.trace("Host {} wastage for VM {}: CPU={}, RAM={}, BW={}, Storage={}, Total={}",
                host.getId(), vm.getId(), String.format("%.4f", normalizedCpuWastage), String.format("%.4f", normalizedRamWastage), 
                String.format("%.4f", normalizedBwWastage), String.format("%.4f", normalizedStorageWastage), String.format("%.4f", totalWastage));
            
            return new ResourceWastage(totalWastage, normalizedCpuWastage, 
                normalizedRamWastage, normalizedBwWastage, normalizedStorageWastage);
            
        } catch (Exception e) {
            logger.error("Error calculating resource wastage for host {} and VM {}", 
                host.getId(), vm.getId(), e);
            return new ResourceWastage(Double.MAX_VALUE, 1.0, 1.0, 1.0, 1.0);
        }
    }
    
    /**
     * Updates resource-specific wastage statistics.
     * 
     * @param wastage The wastage information to record
     */
    private void updateResourceStatistics(ResourceWastage wastage) {
        totalResourceWastage.merge("cpu", wastage.cpu, Double::sum);
        totalResourceWastage.merge("ram", wastage.ram, Double::sum);
        totalResourceWastage.merge("bw", wastage.bw, Double::sum);
        totalResourceWastage.merge(STORAGE_KEY, wastage.storage, Double::sum);
        
        resourceWastageCount.merge("cpu", 1L, Long::sum);
        resourceWastageCount.merge("ram", 1L, Long::sum);
        resourceWastageCount.merge("bw", 1L, Long::sum);
        resourceWastageCount.merge(STORAGE_KEY, 1L, Long::sum);
    }
    
    /**
     * Logs the resource utilization of a host after VM allocation.
     * 
     * @param host The host that received the VM
     * @param vm The VM that was allocated
     */
    private void logResourceUtilization(Host host, Vm vm) {
        try {
            double ramUtilization = (host.getRam().getAllocatedResource() / (double) host.getRam().getCapacity()) * 100;
            double bwUtilization = (host.getBw().getAllocatedResource() / (double) host.getBw().getCapacity()) * 100;
            double storageUtilization = (host.getStorage().getAllocatedResource() / (double) host.getStorage().getCapacity()) * 100;
            logger.debug("Host {} utilization after VM {} allocation: RAM={}%, BW={}%, Storage={}%",
                host.getId(), vm.getId(), String.format("%.2f", ramUtilization), String.format("%.2f", bwUtilization), String.format("%.2f", storageUtilization));
        } catch (Exception e) {
            logger.error("Error logging resource utilization", e);
        }
    }
    
    /**
     * Generates a cache key for host-VM combination.
     * Includes host's current allocation state for accuracy.
     * 
     * @param host The host
     * @param vm The VM
     * @return A unique cache key
     */
    private String generateCacheKey(Host host, Vm vm) {
        // Include host's current allocation state in the key
        return String.format("h%d-vm%d-cpu%.2f-ram%.2f-bw%.2f-storage%.2f", 
            host.getId(), 
            vm.getId(),
            host.getTotalAllocatedMips(),
            host.getRam().getAllocatedResource(),
            host.getBw().getAllocatedResource(),
            host.getStorage().getAllocatedResource());
    }
    
    /**
     * Clears cache entries for a specific host.
     * 
     * @param host The host whose cache entries should be cleared
     */
    private void clearCacheForHost(Host host) {
        String hostPrefix = String.format("h%d-", host.getId());
        int removed = 0;
        
        Iterator<String> iterator = wasteCache.keySet().iterator();
        while (iterator.hasNext()) {
            String key = iterator.next();
            if (key.startsWith(hostPrefix)) {
                iterator.remove();
                removed++;
            }
        }
        
        if (removed > 0) {
            logger.trace("Cleared {} cache entries for Host {}", removed, host.getId());
        }
    }
    
    /**
     * Returns the name of this allocation policy.
     * 
     * @return "BestFit"
     */
    @Override
    public String getName() {
        return "BestFit";
    }
    
    /**
     * Gets the convergence iterations for BestFit (always 1 as it's single-pass).
     * 
     * @return Always 1 for BestFit algorithm
     */
    public int getConvergenceIterations() {
        return 1; // BestFit is a single-pass algorithm
    }
    
    /**
     * Gets the optimization time for BestFit (total allocation time).
     * 
     * @return Total allocation time in milliseconds
     */
    public double getOptimizationTime() {
        return totalAllocationTime.get() / 1_000_000.0; // Convert nanoseconds to milliseconds
    }
    
    /**
     * Gets a description of this allocation algorithm.
     * 
     * @return A detailed description of the Best Fit algorithm
     */
    public String getDescription() {
        return "Best Fit allocation algorithm that places VMs on hosts with minimum " +
               "remaining resources after allocation, optimizing multi-resource utilization";
    }
    
    /**
     * Gets the current statistics of the BestFit algorithm.
     * 
     * @return A map containing algorithm statistics
     */
    @Override
    public String getStatistics() {
        int total = totalAllocations;
        int successful = successfulAllocations;
        return String.format(
            "%s Policy Statistics: Total Attempts=%d, Successful=%d, Success Rate=%.2f%%, CacheSize=%d",
            getName(), total, successful, total > 0 ? (double) successful / total * 100 : 0.0, wasteCache.size()
        );
    }
    
    /**
     * Resets the internal statistics counters.
     * Useful for running multiple experiments.
     */
    public void resetStatistics() {
        logger.debug("Resetting BestFit allocation statistics");
        
        // Reset counters
        successfulAllocations = 0;
        totalAllocations = 0;
        cacheHits = 0;
        cacheMisses = 0;
        totalAllocationTime.set(0);
        totalHostSearchTime.set(0);
        
        // Clear cache
        wasteCache.clear();
        
        // Reset resource statistics
        initializeStatistics();
    }
    
    /**
     * Returns a string representation of this allocation policy.
     * 
     * @return String representation including current statistics
     */
    @Override
    public String toString() {
        int total = totalAllocations;
        int successful = successfulAllocations;
        return String.format("BestFitAllocation[total=%d, success=%d, rate=%.2f%%, hosts=%d, caching=%s]",
            total, successful,
            total > 0 ? (double) successful / total * 100 : 0,
            getHostList().size(),
            cachingEnabled ? "enabled" : "disabled");
    }
    
    /**
     * Inner class to hold detailed resource wastage information.
     */
    private static class ResourceWastage {
        final double total;
        final double cpu;
        final double ram;
        final double bw;
        final double storage;
        
        ResourceWastage(double total, double cpu, double ram, double bw, double storage) {
            this.total = total;
            this.cpu = cpu;
            this.ram = ram;
            this.bw = bw;
            this.storage = storage;
        }
    }

    // 1. Implement defaultFindHostForVm(Vm vm)
    @Override
    public java.util.Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> suitableHosts = getHostList().stream()
            .filter(h -> h != null && isHostSuitableForVm(h, vm))
            .toList();
        Host bestHost = selectHost(vm, suitableHosts);
        return java.util.Optional.ofNullable(bestHost);
    }

    // 3. Fix getAllocatedMips() usage for total allocated MIPS
    private double getTotalAllocatedMips(Host host) {
        // Simple approach: sum the MIPS of all VMs currently allocated to this host
        return host.getVmList().stream()
            .mapToDouble(Vm::getTotalMipsCapacity)
            .sum();
    }

    /**
     * CRITICAL FIX: Get available hosts directly from datacenter
     */
    private List<Host> getAvailableHosts() {
        try {
            // Try to get hosts from datacenter first
            if (getDatacenter() != null) {
                return getDatacenter().getHostList();
            }
            
            // Fallback to getHostList() method
            List<Host> hosts = getHostList();
            if (hosts != null && !hosts.isEmpty()) {
                return hosts;
            }
            
            // Last resort: try to get hosts from any available source
            logger.warn("BestFit: No hosts available from datacenter or host list");
            return new ArrayList<>();
            
        } catch (Exception e) {
            logger.error("BestFit: Error getting available hosts", e);
            return new ArrayList<>();
        }
    }
    
    /**
     * CRITICAL FIX: Manually set host list for BestFit allocation
     */
    public void setHostList(List<Host> hosts) {
        try {
            // Use reflection to access the protected hostList field
            Field hostListField = VmAllocationPolicyAbstract.class.getDeclaredField("hostList");
            hostListField.setAccessible(true);
            hostListField.set(this, hosts);
            logger.debug("BestFit: Successfully set {} hosts in allocation policy", hosts.size());
        } catch (Exception e) {
            logger.error("BestFit: Failed to set host list", e);
        }
    }
}