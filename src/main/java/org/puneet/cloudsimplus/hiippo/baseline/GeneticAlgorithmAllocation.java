package org.puneet.cloudsimplus.hiippo.baseline;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.algorithm.AlgorithmConstants;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.policy.BaselineVmAllocationPolicy;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.puneet.cloudsimplus.hiippo.util.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.Map;
import java.util.HashMap;
/**
 * Genetic Algorithm implementation for VM allocation in CloudSim Plus.
 * This baseline algorithm uses evolutionary computation to find near-optimal
 * VM-to-host mappings by evolving a population of solutions over multiple generations.
 * 
 * Optimized for systems with 16GB RAM with reduced population and generation sizes.
 * 
 * @author Puneet Chandna
 * @version 1.0
 * @since 2025-07-18
 */
public class GeneticAlgorithmAllocation extends BaselineVmAllocationPolicy {
    private static final Logger logger = LoggerFactory.getLogger(GeneticAlgorithmAllocation.class);
    
    // GA parameters optimized for 16GB systems
    private static final int POPULATION_SIZE = 30; // Reduced from 50
    private static final double MUTATION_RATE = 0.1;
    private static final double CROSSOVER_RATE = 0.8;
    private static final int MAX_GENERATIONS = 50; // Reduced from 100
    private static final int TOURNAMENT_SIZE = 3; // Reduced from 5
    private static final int ELITE_SIZE = 2; // Number of best solutions to preserve
    
    // Fitness weights (same as HO algorithm for fair comparison)
    private static final double W_UTILIZATION = AlgorithmConstants.W_UTILIZATION;
    private static final double W_POWER = AlgorithmConstants.W_POWER;
    private static final double W_SLA = AlgorithmConstants.W_SLA;
    
    // Cache for performance optimization
    private final Map<String, Double> fitnessCache = new ConcurrentHashMap<>();
    private Random random;
    // Remove: private List<Vm> pendingVms;
    private Solution bestSolution;
    private int currentGeneration = 0;
    
    /**
     * Constructs a new Genetic Algorithm VM allocation policy.
     */
    public GeneticAlgorithmAllocation() {
        super();
        this.random = new Random(ExperimentConfig.RANDOM_SEED);
        // Remove: this.pendingVms = new ArrayList<>();
        logger.info("Initialized Genetic Algorithm allocation policy with population size: {}, generations: {}", 
                   POPULATION_SIZE, MAX_GENERATIONS);
    }
    
    /**
     * Allocates a host for the specified VM using genetic algorithm optimization.
     * VMs are collected and processed in batches for better optimization results.
     * 
     * @param vm the VM to allocate
     * @return true if allocation was successful, false otherwise
     */
    @Override
    public boolean allocateHostForVm(Vm vm) {
        // This method is now just a placeholder.
        // The main logic will be in placeAllVms().
        return true; 
    }

    /**
     * This is the new primary method. It should be called by your
     * custom DatacenterBroker once all VMs have been submitted.
     * It runs the GA for all VMs that need placement.
     */
    public boolean placeAllVms() {
        // Collect all VMs that haven't been allocated yet.
        final List<Vm> vmsToPlace = /* TODO: Provide VM list source here */ new ArrayList<>();

        if (vmsToPlace.isEmpty()) {
            logger.info("GA: No VMs to place.");
            return true;
        }

        logger.info("Running GA for a batch of {} VMs.", vmsToPlace.size());
        MemoryManager.checkMemoryUsage("GA Execution");

        // Run the genetic algorithm on the entire list.
        Solution solution = runGeneticAlgorithm(vmsToPlace);

        if (solution != null && solution.isValid()) {
            logger.info("GA found a valid solution. Applying allocation...");
            return applySolution(solution, vmsToPlace);
        }

        logger.error("GA failed to find a valid solution for the batch of VMs.");
        return false;
    }
    
    /**
     * Determines if the current batch of VMs should be processed.
     * 
     * @return true if batch should be processed
     */
    private boolean shouldProcessBatch() {
        return false; // No longer applicable
    }
    
    /**
     * Checks if there are more VMs waiting to be allocated.
     * 
     * @return true if more VMs are expected
     */
    private boolean hasMoreVmsToAllocate() {
        // This is a simplified check - in production, you might track total expected VMs
        return false;
    }
    
    /**
     * Processes immediate allocation for a single VM when batch processing is not applicable.
     * 
     * @param vm the VM to allocate immediately
     * @return true if allocation was successful
     */
    private boolean processImmediateAllocation(Vm vm) {
        logger.debug("Processing immediate allocation for VM {}", vm.getId());
        
        List<Vm> singleVmList = Arrays.asList(vm);
        Solution solution = runGeneticAlgorithm(singleVmList);
        
        if (solution != null && solution.isValid()) {
            return applySolution(solution, singleVmList);
        }
        
        logger.warn("GA failed to find valid solution for VM {}", vm.getId());
        return false;
    }
    
    /**
     * Processes batch allocation for all pending VMs.
     * 
     * @return true if all VMs were successfully allocated
     */
    private boolean processBatchAllocation() {
        if (false) { // No longer applicable
            logger.debug("No pending VMs to process");
            return true;
        }
        
        // Check memory before processing
        MemoryManager.checkMemoryUsage("GA Batch Processing");
        
        // Create a copy of pending VMs for processing
        List<Vm> vmsToProcess = new ArrayList<>(); // No longer applicable
        // pendingVms.clear(); // No longer applicable
        
        logger.info("Running GA for batch of {} VMs", vmsToProcess.size());
        long startTime = System.currentTimeMillis();
        
        // Run genetic algorithm
        Solution solution = runGeneticAlgorithm(vmsToProcess);
        
        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("GA completed in {} ms for {} VMs", executionTime, vmsToProcess.size());
        
        if (solution != null && solution.isValid()) {
            boolean success = applySolution(solution, vmsToProcess);
            if (success) {
                logger.info("Successfully allocated all {} VMs using GA", vmsToProcess.size());
            }
            return success;
        }
        
        logger.error("GA failed to find valid solution for batch of {} VMs", vmsToProcess.size());
        return false;
    }
    
    /**
     * Main genetic algorithm implementation.
     * 
     * @param vms list of VMs to allocate
     * @return best solution found
     */
    private Solution runGeneticAlgorithm(List<Vm> vms) {
        if (vms == null || vms.isEmpty()) {
            logger.warn("No VMs to process in GA");
            return null;
        }
        
        try {
            // Initialize population
            List<Solution> population = initializePopulation(vms);
            if (population.isEmpty()) {
                logger.error("Failed to initialize population");
                return null;
            }
            
            bestSolution = population.get(0);
            double bestFitness = evaluateFitness(bestSolution, vms);
            
            // Evolution loop
            for (currentGeneration = 0; currentGeneration < MAX_GENERATIONS; currentGeneration++) {
                // Evaluate fitness for all solutions
                evaluatePopulation(population, vms);
                
                // Sort by fitness (ascending - lower is better)
                population.sort(Comparator.comparingDouble(solution -> getCachedFitness(solution, vms)));
                
                // Update best solution
                Solution currentBest = population.get(0);
                double currentBestFitness = getCachedFitness(currentBest, vms);
                
                if (currentBestFitness < bestFitness) {
                    bestSolution = currentBest.clone();
                    bestFitness = currentBestFitness;
                    logger.debug("New best solution found at generation {} with fitness {}", 
                               currentGeneration, bestFitness);
                }
                
                // Check for convergence
                if (hasConverged(population)) {
                    logger.info("GA converged at generation {}", currentGeneration);
                    break;
                }
                
                // Create next generation
                population = evolvePopulation(population);
                
                // Memory management for large problems
                if (currentGeneration % 10 == 0) {
                    fitnessCache.clear();
                    if (ExperimentConfig.shouldRunGarbageCollection()) {
                        System.gc();
                    }
                }
            }
            
            logger.info("GA completed after {} generations with best fitness: {}", 
                       currentGeneration, bestFitness);
            
            return bestSolution;
            
        } catch (Exception e) {
            logger.error("Error in genetic algorithm execution: {}", e.getMessage(), e);
            return null;
        } finally {
            // Clear cache to free memory
            fitnessCache.clear();
        }
    }
    
    /**
     * Initializes the population with random valid solutions.
     * 
     * @param vms list of VMs to allocate
     * @return initial population
     */
    private List<Solution> initializePopulation(List<Vm> vms) {
        List<Solution> population = new ArrayList<>();
        List<Host> availableHosts = getHostList();
        
        if (availableHosts.isEmpty()) {
            logger.error("No available hosts for GA initialization");
            return population;
        }
        
        logger.debug("Initializing population of size {} for {} VMs and {} hosts", 
                    POPULATION_SIZE, vms.size(), availableHosts.size());
        
        for (int i = 0; i < POPULATION_SIZE; i++) {
            Solution solution = createRandomSolution(vms, availableHosts);
            if (solution != null && solution.isValid()) {
                population.add(solution);
            }
        }
        
        // If we couldn't create enough valid random solutions, try systematic approaches
        while (population.size() < POPULATION_SIZE) {
            Solution solution = createHeuristicSolution(vms, availableHosts, population.size());
            if (solution != null && solution.isValid()) {
                population.add(solution);
            } else {
                break; // Can't create more valid solutions
            }
        }
        
        logger.info("Initialized population with {} valid solutions", population.size());
        return population;
    }
    
    /**
     * Creates a random solution by randomly assigning VMs to hosts.
     * 
     * @param vms list of VMs
     * @param hosts list of available hosts
     * @return random solution or null if no valid solution could be created
     */
    private Solution createRandomSolution(List<Vm> vms, List<Host> hosts) {
        Solution solution = new Solution(vms.size());
        
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            List<Host> suitableHosts = findSuitableHosts(vm, hosts, solution);
            
            if (suitableHosts.isEmpty()) {
                return null; // No valid solution possible
            }
            
            // Randomly select a host
            Host selectedHost = suitableHosts.get(random.nextInt(suitableHosts.size()));
            solution.setAllocation(i, hosts.indexOf(selectedHost));
        }
        
        return solution;
    }
    
    /**
     * Creates a heuristic-based solution as fallback for population initialization.
     * 
     * @param vms list of VMs
     * @param hosts list of available hosts
     * @param index population index (used to vary the heuristic)
     * @return heuristic solution
     */
    private Solution createHeuristicSolution(List<Vm> vms, List<Host> hosts, int index) {
        Solution solution = new Solution(vms.size());
        
        // Use different heuristics based on index
        switch (index % 3) {
            case 0: // First-fit style
                return createFirstFitSolution(vms, hosts);
            case 1: // Best-fit style
                return createBestFitSolution(vms, hosts);
            case 2: // Worst-fit style
                return createWorstFitSolution(vms, hosts);
            default:
                return createRandomSolution(vms, hosts);
        }
    }
    
    /**
     * Creates a first-fit style solution.
     */
    private Solution createFirstFitSolution(List<Vm> vms, List<Host> hosts) {
        Solution solution = new Solution(vms.size());
        
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            boolean allocated = false;
            
            for (int j = 0; j < hosts.size(); j++) {
                if (canAllocateVmToHost(vm, hosts.get(j), solution, vms, hosts)) {
                    solution.setAllocation(i, j);
                    allocated = true;
                    break;
                }
            }
            
            if (!allocated) {
                return null;
            }
        }
        
        return solution;
    }
    
    /**
     * Creates a best-fit style solution.
     */
    private Solution createBestFitSolution(List<Vm> vms, List<Host> hosts) {
        Solution solution = new Solution(vms.size());
        
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            int bestHost = -1;
            double minWaste = Double.MAX_VALUE;
            
            for (int j = 0; j < hosts.size(); j++) {
                Host host = hosts.get(j);
                if (canAllocateVmToHost(vm, host, solution, vms, hosts)) {
                    double waste = calculateResourceWaste(vm, host, solution, vms, hosts);
                    if (waste < minWaste) {
                        minWaste = waste;
                        bestHost = j;
                    }
                }
            }
            
            if (bestHost == -1) {
                return null;
            }
            
            solution.setAllocation(i, bestHost);
        }
        
        return solution;
    }
    
    /**
     * Creates a worst-fit style solution.
     */
    private Solution createWorstFitSolution(List<Vm> vms, List<Host> hosts) {
        Solution solution = new Solution(vms.size());
        
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            int worstHost = -1;
            double maxAvailable = -1;
            
            for (int j = 0; j < hosts.size(); j++) {
                Host host = hosts.get(j);
                if (canAllocateVmToHost(vm, host, solution, vms, hosts)) {
                    double available = calculateAvailableResources(host, solution, vms, hosts);
                    if (available > maxAvailable) {
                        maxAvailable = available;
                        worstHost = j;
                    }
                }
            }
            
            if (worstHost == -1) {
                return null;
            }
            
            solution.setAllocation(i, worstHost);
        }
        
        return solution;
    }
    
    /**
     * Finds suitable hosts for a VM given current solution state.
     */
    private List<Host> findSuitableHosts(Vm vm, List<Host> hosts, Solution partialSolution) {
        List<Host> suitable = new ArrayList<>();
        
        for (int i = 0; i < hosts.size(); i++) {
            Host host = hosts.get(i);
            if (canAllocateVmToHost(vm, host, partialSolution, null, hosts)) {
                suitable.add(host);
            }
        }
        
        return suitable;
    }
    
    /**
     * Checks if a VM can be allocated to a host considering the partial solution.
     */
    private boolean canAllocateVmToHost(Vm vm, Host host, Solution solution, 
                                       List<Vm> allVms, List<Host> allHosts) {
        // Check basic capacity
        if (!host.isSuitableForVm(vm)) {
            return false;
        }
        
        // Calculate already allocated resources from solution
        double allocatedMips = 0;
        double allocatedRam = 0;
        double allocatedBw = 0;
        double allocatedStorage = 0;
        
        if (solution != null && allVms != null) {
            int hostIndex = allHosts.indexOf(host);
            for (int i = 0; i < solution.getSize(); i++) {
                if (solution.getAllocation(i) == hostIndex) {
                    Vm allocatedVm = allVms.get(i);
                    allocatedMips += allocatedVm.getTotalMipsCapacity();
                    allocatedRam += allocatedVm.getRam().getCapacity();
                    allocatedBw += allocatedVm.getBw().getCapacity();
                    allocatedStorage += allocatedVm.getStorage().getCapacity();
                }
            }
        }
        
        // Check if adding this VM would exceed capacity
        return (host.getTotalMipsCapacity() - allocatedMips >= vm.getTotalMipsCapacity()) &&
               (host.getRam().getCapacity() - allocatedRam >= vm.getRam().getCapacity()) &&
               (host.getBw().getCapacity() - allocatedBw >= vm.getBw().getCapacity()) &&
               (host.getStorage().getCapacity() - allocatedStorage >= vm.getStorage().getCapacity());
    }
    
    /**
     * Calculates resource waste if VM is allocated to host.
     */
    private double calculateResourceWaste(Vm vm, Host host, Solution solution, 
                                        List<Vm> allVms, List<Host> allHosts) {
        // This is a simplified calculation - you might want to make it more sophisticated
        double cpuWaste = host.getTotalMipsCapacity() - vm.getTotalMipsCapacity();
        double ramWaste = host.getRam().getAvailableResource() - vm.getRam().getCapacity();
        
        return cpuWaste + ramWaste;
    }
    
    /**
     * Calculates available resources on a host.
     */
    private double calculateAvailableResources(Host host, Solution solution, 
                                             List<Vm> allVms, List<Host> allHosts) {
        double availableMips = host.getTotalMipsCapacity();
        double availableRam = host.getRam().getCapacity();
        
        if (solution != null && allVms != null) {
            int hostIndex = allHosts.indexOf(host);
            for (int i = 0; i < solution.getSize(); i++) {
                if (solution.getAllocation(i) == hostIndex) {
                    Vm vm = allVms.get(i);
                    availableMips -= vm.getTotalMipsCapacity();
                    availableRam -= vm.getRam().getCapacity();
                }
            }
        }
        
        return availableMips + availableRam;
    }
    
    /**
     * Evaluates fitness for entire population.
     */
    private void evaluatePopulation(List<Solution> population, List<Vm> vms) {
        population.parallelStream().forEach(solution -> evaluateFitness(solution, vms));
    }
    
    /**
     * Gets cached fitness value or calculates if not cached.
     */
    private double getCachedFitness(Solution solution, List<Vm> vms) {
        String key = solution.toString();
        return fitnessCache.computeIfAbsent(key, k -> evaluateFitness(solution, vms));
    }
    
    /**
     * Evaluates fitness of a solution using multi-objective function.
     * Lower fitness values are better.
     * 
     * @param solution the solution to evaluate
     * @return fitness value
     */
    private double evaluateFitness(Solution solution, List<Vm> vms) {
        if (solution == null || !solution.isValid()) {
            return Double.MAX_VALUE;
        }
        
        try {
            double utilization = calculateResourceUtilization(solution, vms);
            double power = calculatePowerConsumption(solution, vms);
            double slaViolations = calculateSLAViolations(solution, vms);
            
            // Normalize values to [0,1] range
            double normalizedUtil = 1.0 - utilization; // We want high utilization, so invert
            double normalizedPower = power / 10000.0; // Assume max power is 10000W
            double normalizedSLA = slaViolations / 100.0; // Assume max 100 violations
            
            // Multi-objective fitness (lower is better)
            double fitness = W_UTILIZATION * normalizedUtil + 
                           W_POWER * normalizedPower + 
                           W_SLA * normalizedSLA;
            
            // Cache the fitness value
            fitnessCache.put(solution.toString(), fitness);
            
            return fitness;
            
        } catch (Exception e) {
            logger.error("Error calculating fitness: {}", e.getMessage());
            return Double.MAX_VALUE;
        }
    }
    
    /**
 * Calculates average resource utilization across all hosts in the solution.
 * 
 * @param solution the solution to evaluate
 * @return average utilization (0-1 range, higher is better)
 */
private double calculateResourceUtilization(Solution solution, List<Vm> vms) {
    List<Host> hosts = getHostList();
    
    if (hosts.isEmpty() || vms.isEmpty()) {
        return 0.0;
    }
    
    // Track utilization for each host
    Map<Integer, Double> hostCpuUtilization = new HashMap<>();
    Map<Integer, Double> hostRamUtilization = new HashMap<>();
    
    // Initialize with current utilization
    for (int i = 0; i < hosts.size(); i++) {
        Host host = hosts.get(i);
        double currentCpuUtil = 0.0;
        try {
            // getAllocatedMips() may require a VM argument; if so, sum over all VMs
            double totalAllocatedMips = 0.0;
            for (Vm vm : host.getVmList()) {
                Object mipsObj = host.getVmScheduler().getAllocatedMips(vm);
                if (mipsObj instanceof Number) {
                    totalAllocatedMips += ((Number) mipsObj).doubleValue();
                } else if (mipsObj != null && mipsObj.getClass().getSimpleName().equals("MipsShare")) {
                    try {
                        totalAllocatedMips += ((Number) mipsObj.getClass().getMethod("getValue").invoke(mipsObj)).doubleValue();
                    } catch (Exception e) {
                        logger.error("Error extracting value from MipsShare: {}", e.getMessage());
                    }
                }
            }
            currentCpuUtil = host.getTotalMipsCapacity() > 0 ? totalAllocatedMips / host.getTotalMipsCapacity() : 0.0;
        } catch (Exception e) {
            logger.error("Error getting allocated MIPS for host {}: {}", host.getId(), e.getMessage());
        }
        double currentRamUtil = (host.getRam().getCapacity() - host.getRam().getAvailableResource()) / host.getRam().getCapacity();
        
        hostCpuUtilization.put(i, currentCpuUtil);
        hostRamUtilization.put(i, currentRamUtil);
    }
    
    // Add utilization from the solution's VM allocations
    for (int i = 0; i < solution.getSize(); i++) {
        int hostIndex = solution.getAllocation(i);
        if (hostIndex >= 0 && hostIndex < hosts.size()) {
            Vm vm = vms.get(i);
            Host host = hosts.get(hostIndex);
            
            double additionalCpu = vm.getTotalMipsCapacity() / host.getTotalMipsCapacity();
            double additionalRam = vm.getRam().getCapacity() / host.getRam().getCapacity();
            
            hostCpuUtilization.merge(hostIndex, additionalCpu, Double::sum);
            hostRamUtilization.merge(hostIndex, additionalRam, Double::sum);
        }
    }
    
    // Calculate average utilization across all hosts
    double totalCpuUtil = 0.0;
    double totalRamUtil = 0.0;
    int activeHosts = 0;
    
    for (int i = 0; i < hosts.size(); i++) {
        double cpuUtil = hostCpuUtilization.getOrDefault(i, 0.0);
        double ramUtil = hostRamUtilization.getOrDefault(i, 0.0);
        
        if (cpuUtil > 0 || ramUtil > 0) {
            activeHosts++;
            totalCpuUtil += Math.min(cpuUtil, 1.0); // Cap at 1.0
            totalRamUtil += Math.min(ramUtil, 1.0);
        }
    }
    
    if (activeHosts == 0) {
        return 0.0;
    }
    
    // Return average of CPU and RAM utilization
    return ((totalCpuUtil + totalRamUtil) / 2.0) / activeHosts;
}

    
    /**
 * Calculates total power consumption for the solution.
 * Adapted from V1's power model.
 * 
 * @param solution the solution to evaluate
 * @return total power consumption in watts
 */
private double calculatePowerConsumption(Solution solution, List<Vm> vms) {
    List<Host> hosts = getHostList();
    
    double totalPower = 0.0;
    
    // Calculate power for each host based on its utilization
    Map<Integer, Double> hostCpuUtilization = new HashMap<>();
    
    // First, get current utilization
    for (int i = 0; i < hosts.size(); i++) {
        Host host = hosts.get(i);
        double currentUtil = 0.0;
        try {
            double totalAllocatedMips = 0.0;
            for (Vm vm : host.getVmList()) {
                Object mipsObj = host.getVmScheduler().getAllocatedMips(vm);
                if (mipsObj instanceof Number) {
                    totalAllocatedMips += ((Number) mipsObj).doubleValue();
                } else if (mipsObj != null && mipsObj.getClass().getSimpleName().equals("MipsShare")) {
                    try {
                        totalAllocatedMips += ((Number) mipsObj.getClass().getMethod("getValue").invoke(mipsObj)).doubleValue();
                    } catch (Exception e) {
                        logger.error("Error extracting value from MipsShare: {}", e.getMessage());
                    }
                }
            }
            currentUtil = host.getTotalMipsCapacity() > 0 ? totalAllocatedMips / host.getTotalMipsCapacity() : 0.0;
        } catch (Exception e) {
            logger.error("Error getting allocated MIPS for host {}: {}", host.getId(), e.getMessage());
        }
        hostCpuUtilization.put(i, currentUtil);
    }
    
    // Add utilization from solution
    for (int i = 0; i < solution.getSize(); i++) {
        int hostIndex = solution.getAllocation(i);
        if (hostIndex >= 0 && hostIndex < hosts.size()) {
            Vm vm = vms.get(i);
            Host host = hosts.get(hostIndex);
            
            double additionalUtil = vm.getTotalMipsCapacity() / host.getTotalMipsCapacity();
            hostCpuUtilization.merge(hostIndex, additionalUtil, Double::sum);
        }
    }
    
    // Calculate power for each host
    for (int i = 0; i < hosts.size(); i++) {
        Host host = hosts.get(i);
        double cpuUtil = Math.min(hostCpuUtilization.getOrDefault(i, 0.0), 1.0);
        
        // Power model: idle power + (max power - idle power) * utilization
        // Using simplified linear model from V1
        double maxPower = host.getTotalMipsCapacity() * 1.5; // Approximate max power
        double idlePower = maxPower * 0.6; // Assume 60% power at idle
        
        double hostPower = 0.0;
        if (cpuUtil > 0) {
            hostPower = idlePower + (maxPower - idlePower) * cpuUtil;
        }
        
        totalPower += hostPower;
    }
    
    return totalPower;
}
    
    /**
 * Calculates potential SLA violations for the solution.
 * Adapted from V1's SLA violation risk calculation.
 * 
 * @param solution the solution to evaluate
 * @return number of potential SLA violations
 */
private double calculateSLAViolations(Solution solution, List<Vm> vms) {
    List<Host> hosts = getHostList();
    
    double totalViolations = 0.0;
    
    // Track resource allocation for each host
    Map<Integer, Double> hostMipsAllocation = new HashMap<>();
    Map<Integer, Double> hostRamAllocation = new HashMap<>();
    
    // Initialize with current allocations
    for (int i = 0; i < hosts.size(); i++) {
        Host host = hosts.get(i);
        double totalAllocatedMips = 0.0;
        for (Vm vm : host.getVmList()) {
            Object mipsObj = host.getVmScheduler().getAllocatedMips(vm);
            if (mipsObj instanceof Number) {
                totalAllocatedMips += ((Number) mipsObj).doubleValue();
            } else if (mipsObj != null && mipsObj.getClass().getSimpleName().equals("MipsShare")) {
                try {
                    totalAllocatedMips += ((Number) mipsObj.getClass().getMethod("getValue").invoke(mipsObj)).doubleValue();
                } catch (Exception e) {
                    logger.error("Error extracting value from MipsShare: {}", e.getMessage());
                }
            }
        }
        hostMipsAllocation.put(i, totalAllocatedMips);
        hostRamAllocation.put(i, host.getRam().getCapacity() - host.getRam().getAvailableResource());
    }
    
    // Add allocations from solution
    for (int i = 0; i < solution.getSize(); i++) {
        int hostIndex = solution.getAllocation(i);
        if (hostIndex >= 0 && hostIndex < hosts.size()) {
            Vm vm = vms.get(i);
            
            hostMipsAllocation.merge(hostIndex, vm.getTotalMipsCapacity(), Double::sum);
            hostRamAllocation.merge(hostIndex, vm.getRam().getCapacity(), Double::sum);
        }
    }
    
    // Check for potential SLA violations
    for (int i = 0; i < solution.getSize(); i++) {
        int hostIndex = solution.getAllocation(i);
        if (hostIndex >= 0 && hostIndex < hosts.size()) {
            Host host = hosts.get(hostIndex);
            Vm vm = vms.get(i);
            
            double totalMips = hostMipsAllocation.get(hostIndex);
            double totalRam = hostRamAllocation.get(hostIndex);
            
            double mipsUtilization = totalMips / host.getTotalMipsCapacity();
            double ramUtilization = totalRam / host.getRam().getCapacity();
            
            // High risk of SLA violation if utilization > 90%
            if (mipsUtilization > 0.9 || ramUtilization > 0.9) {
                totalViolations += 0.8; // High risk
            } else if (mipsUtilization > 0.8 || ramUtilization > 0.8) {
                totalViolations += 0.3; // Medium risk
            } else if (mipsUtilization > 0.7 || ramUtilization > 0.7) {
                totalViolations += 0.1; // Low risk
            }
            
            // Additional penalty for oversubscription
            if (mipsUtilization > 1.0 || ramUtilization > 1.0) {
                totalViolations += 2.0; // Severe violation
            }
        }
    }
    
    return totalViolations;
}
    
    /**
     * Checks if the population has converged.
     */
    private boolean hasConverged(List<Solution> population) {
        if (population.size() < 2) {
            return true;
        }
        
        // Check fitness variance
        double minFitness = getCachedFitness(population.get(0), /* TODO: Provide VM list source here */ new ArrayList<>());
        double maxFitness = getCachedFitness(population.get(Math.min(ELITE_SIZE, population.size() - 1)), /* TODO: Provide VM list source here */ new ArrayList<>());
        
        return (maxFitness - minFitness) < AlgorithmConstants.DEFAULT_CONVERGENCE_THRESHOLD;
    }
    
    /**
     * Evolves the population to create the next generation.
     */
    private List<Solution> evolvePopulation(List<Solution> population) {
        List<Solution> newPopulation = new ArrayList<>();
        
        // Elitism - keep best solutions
        for (int i = 0; i < ELITE_SIZE && i < population.size(); i++) {
            newPopulation.add(population.get(i).clone());
        }
        
        // Generate rest of population through crossover and mutation
        while (newPopulation.size() < POPULATION_SIZE) {
            Solution parent1 = tournamentSelection(population);
            
            if (random.nextDouble() < CROSSOVER_RATE) {
                Solution parent2 = tournamentSelection(population);
                Solution offspring = crossover(parent1, parent2);
                
                if (random.nextDouble() < MUTATION_RATE) {
                    offspring = mutate(offspring);
                }
                
                if (offspring.isValid()) {
                    newPopulation.add(offspring);
                }
            } else {
                // No crossover, just copy parent with possible mutation
                Solution offspring = parent1.clone();
                if (random.nextDouble() < MUTATION_RATE) {
                    offspring = mutate(offspring);
                }
                
                if (offspring.isValid()) {
                    newPopulation.add(offspring);
                }
            }
        }
        
        return newPopulation;
    }
    
    /**
     * Performs tournament selection to choose a parent.
     * 
     * @param population the population to select from
     * @return selected solution
     */
    private Solution tournamentSelection(List<Solution> population) {
        if (population == null || population.isEmpty()) {
            logger.error("Cannot perform tournament selection on empty population");
            return null;
        }
        
        Solution best = null;
        double bestFitness = Double.MAX_VALUE;
        
        // Select random individuals for tournament
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            Solution candidate = population.get(random.nextInt(population.size()));
            double fitness = getCachedFitness(candidate, /* TODO: Provide VM list source here */ new ArrayList<>());
            
            if (fitness < bestFitness) {
                bestFitness = fitness;
                best = candidate;
            }
        }
        
        return best;
    }
    
    /**
     * Performs single-point crossover between two parent solutions.
     * 
     * @param parent1 first parent
     * @param parent2 second parent
     * @return offspring solution
     */
    private Solution crossover(Solution parent1, Solution parent2) {
        if (parent1 == null || parent2 == null) {
            logger.error("Cannot perform crossover with null parents");
            return parent1 != null ? parent1.clone() : parent2.clone();
        }
        
        int size = parent1.getSize();
        Solution offspring = new Solution(size);
        
        // Single-point crossover
        int crossoverPoint = random.nextInt(size);
        
        for (int i = 0; i < size; i++) {
            if (i < crossoverPoint) {
                offspring.setAllocation(i, parent1.getAllocation(i));
            } else {
                offspring.setAllocation(i, parent2.getAllocation(i));
            }
        }
        
        return offspring;
    }
    
    /**
     * Performs random mutation on a solution.
     * 
     * @param solution the solution to mutate
     * @return mutated solution
     */
    private Solution mutate(Solution solution) {
        if (solution == null) {
            logger.error("Cannot mutate null solution");
            return null;
        }
        
        Solution mutated = solution.clone();
        int size = mutated.getSize();
        
        // Randomly change one allocation
        int vmIndex = random.nextInt(size);
        int numHosts = getHostList().size();
        
        if (numHosts > 1) {
            int currentHost = mutated.getAllocation(vmIndex);
            int newHost;
            
            // Ensure we select a different host
            do {
                newHost = random.nextInt(numHosts);
            } while (newHost == currentHost);
            
            mutated.setAllocation(vmIndex, newHost);
        }
        
        return mutated;
    }
    
    /**
     * Applies the solution by actually allocating VMs to hosts.
     */
    private boolean applySolution(Solution solution, List<Vm> vms) {
        if (solution == null || !solution.isValid() || vms == null) {
            logger.error("Cannot apply invalid solution");
            return false;
        }
        
        List<Host> hosts = getHostList();
        boolean allSuccess = true;
        
        for (int i = 0; i < vms.size(); i++) {
            Vm vm = vms.get(i);
            int hostIndex = solution.getAllocation(i);
            
            if (hostIndex >= 0 && hostIndex < hosts.size()) {
                Host host = hosts.get(hostIndex);
                
                if (!allocateHostForVm(vm, host)) {
                    logger.error("Failed to allocate VM {} to host {} as determined by GA", 
                               vm.getId(), host.getId());
                    allSuccess = false;
                }
            } else {
                logger.error("Invalid host index {} for VM {}", hostIndex, vm.getId());
                allSuccess = false;
            }
        }
        
        return allSuccess;
    }
    
    /**
     * Returns the name of this allocation policy.
     * 
     * @return policy name
     */
    @Override
    public String getName() {
        return "GA";
    }
    
    /**
     * Selects a host for the given VM from a list of suitable hosts using a best-fit heuristic (minimum resource waste).
     *
     * @param vm The VM to allocate
     * @param suitableHosts List of suitable hosts
     * @return The selected host or null if none found
     */
    @Override
    protected Host selectHost(Vm vm, List<Host> suitableHosts) {
        if (suitableHosts == null || suitableHosts.isEmpty()) {
            return null;
        }
        Host bestHost = null;
        double minWaste = Double.MAX_VALUE;
        for (Host host : suitableHosts) {
            double waste = calculateResourceWaste(vm, host, null, null, null); // Use simple waste calc
            if (waste < minWaste) {
                minWaste = waste;
                bestHost = host;
            }
        }
        return bestHost;
    }

    /**
     * Returns the first suitable host for the given VM as Optional, as required by VmAllocationPolicyAbstract.
     */
    @Override
    public java.util.Optional<Host> defaultFindHostForVm(Vm vm) {
        List<Host> suitableHosts = getHostList().stream()
            .filter(h -> h != null && h.isActive() && h.isSuitableForVm(vm))
            .collect(Collectors.toList());
        Host host = selectHost(vm, suitableHosts);
        return java.util.Optional.ofNullable(host);
    }
    
    /**
     * Internal class representing a solution (VM-to-Host mapping).
     */
    private static class Solution implements Cloneable {
        private final int[] allocations; // VM index -> Host index mapping
        private final int size;
        
        public Solution(int size) {
            this.size = size;
            this.allocations = new int[size];
            Arrays.fill(allocations, -1); // Initialize with no allocation
        }
        
        public void setAllocation(int vmIndex, int hostIndex) {
            if (vmIndex >= 0 && vmIndex < size) {
                allocations[vmIndex] = hostIndex;
            }
        }
        
        public int getAllocation(int vmIndex) {
            if (vmIndex >= 0 && vmIndex < size) {
                return allocations[vmIndex];
            }
            return -1;
        }
        
        public int getSize() {
            return size;
        }
        
        public boolean isValid() {
            // Check that all VMs are allocated
            for (int allocation : allocations) {
                if (allocation < 0) {
                    return false;
                }
            }
            return true;
        }
        
        @Override
        public Solution clone() {
            Solution cloned = new Solution(size);
            System.arraycopy(allocations, 0, cloned.allocations, 0, size);
            return cloned;
        }
        
        @Override
        public String toString() {
            return Arrays.toString(allocations);
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            Solution solution = (Solution) obj;
            return Arrays.equals(allocations, solution.allocations);
        }
        
        @Override
        public int hashCode() {
            return Arrays.hashCode(allocations);
        }
    }
}