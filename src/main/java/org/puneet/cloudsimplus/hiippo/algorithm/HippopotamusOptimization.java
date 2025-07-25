
package org.puneet.cloudsimplus.hiippo.algorithm;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.puneet.cloudsimplus.hiippo.policy.HippopotamusVmAllocationPolicy;
import org.apache.commons.math3.special.Gamma;

/**
 * Main implementation of the Hippopotamus Optimization (HO) algorithm for
 * Virtual Machine placement optimization in CloudSim Plus.
 * 
 * <p>This algorithm is inspired by the social behavior and territorial
 * dynamics of hippopotamuses, adapted for cloud resource optimization.
 * It balances exploration and exploitation through position updates
 * influenced by leader hippos, prey dynamics, and random walks.</p>
 * 
 * <p>The implementation includes comprehensive convergence tracking,
 * memory-efficient processing, and statistical validation capabilities
 * suitable for research-grade experiments.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class HippopotamusOptimization {
    
    private static final Logger logger = LoggerFactory.getLogger(HippopotamusOptimization.class);
    
    private final HippopotamusParameters parameters;
    private final Random random;
    private final ConvergenceAnalyzer convergenceAnalyzer;
    
    // Solution cache for memory efficiency
    private final Map<String, HippopotamusVmAllocationPolicy.Solution> solutionCache;
    private final List<Double> fitnessHistory;
    
    // Add static parameters for global tuning
    public static HippopotamusParameters staticParameters = new HippopotamusParameters();

    public static void setParameters(HippopotamusParameters params) {
        if (params != null) {
            staticParameters = params.copy();
            logger.info("Global HO parameters updated: {}", staticParameters);
        }
    }
    
    /**
     * Creates a new HippopotamusOptimization instance with default parameters.
     */
    public HippopotamusOptimization() {
        this(new HippopotamusParameters());
    }
    
    /**
     * Creates a new HippopotamusOptimization instance with custom parameters.
     * 
     * @param parameters the algorithm parameters
     * @throws NullPointerException if parameters is null
     */
    public HippopotamusOptimization(HippopotamusParameters parameters) {
        this.parameters = Objects.requireNonNull(parameters, "Parameters cannot be null");
        this.random = new Random();
        this.convergenceAnalyzer = new ConvergenceAnalyzer(parameters.getConvergenceWindowSize());
        this.solutionCache = new ConcurrentHashMap<>();
        this.fitnessHistory = new ArrayList<>();
        
        logger.info("Initialized HippopotamusOptimization with parameters: {}", parameters);
    }
    
    /**
     * Main optimization method that finds the optimal VM placement solution.
     * 
     * @param vms the list of VMs to place
     * @param hosts the list of available hosts
     * @return the optimal VM placement solution
     * @throws IllegalArgumentException if vms or hosts is empty
     */
    public HippopotamusVmAllocationPolicy.Solution optimize(List<Vm> vms, List<Host> hosts) {
        Objects.requireNonNull(vms, "VM list cannot be null");
        Objects.requireNonNull(hosts, "Host list cannot be null");
        
        if (vms.isEmpty()) {
            throw new IllegalArgumentException("VM list cannot be empty");
        }
        
        if (hosts.isEmpty()) {
            throw new IllegalArgumentException("Host list cannot be empty");
        }
        
        logger.info("Starting HO optimization for {} VMs and {} hosts", vms.size(), hosts.size());
        
        // Check memory availability
        if (!MemoryManager.hasEnoughMemoryForScenario(vms.size(), hosts.size())) {
            logger.warn("Insufficient memory for scenario, reducing population size");
            parameters.setPopulationSize(Math.max(5, parameters.getPopulationSize() / 2));
        }
        
        // Initialize population
        List<Hippopotamus> population = initializePopulation(vms, hosts);
        
        // Find initial best solution
        Hippopotamus leader = findLeader(population);
        HippopotamusVmAllocationPolicy.Solution bestSolution = leader.getSolution().copy();
        double bestFitness = evaluateFitness(bestSolution);
        
        logger.debug("Initial best fitness: {}", bestFitness);
        
        // Main optimization loop
        int iteration = 0;
        boolean converged = false;
        
        while (iteration < parameters.getMaxIterations() && !converged) {
            // Memory check
            if (iteration % 10 == 0) {
                MemoryManager.checkMemoryUsage("HO Iteration " + iteration);
            }
            
            // Update positions for all hippos
            for (Hippopotamus hippo : population) {
                if (hippo != leader) {
                    updatePosition(hippo, leader, findPrey(population));
                }
            }
            
            // Evaluate new positions
            for (Hippopotamus hippo : population) {
                double fitness = evaluateFitness(hippo.getSolution());
                hippo.setFitness(fitness);
                
                // Update leader if better solution found
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    bestSolution = hippo.getSolution().copy();
                    leader = hippo;
                }
            }
            
            // Track convergence
            fitnessHistory.add(bestFitness);
            converged = convergenceAnalyzer.checkConvergence(fitnessHistory);
            
            // Log progress
            if (iteration % 20 == 0 || converged) {
                logger.info("Iteration {}: Best fitness = {}, Converged = {}", 
                           iteration, bestFitness, converged);
            }
            
            iteration++;
            
            
        }
        
        logger.info("HO optimization completed after {} iterations. Best fitness: {}", 
                   iteration, bestFitness);
        
        // Final validation
        validateSolution(bestSolution, vms, hosts);
        
        return bestSolution;
    }
    
    /**
     * Initializes the population of hippos with random solutions.
     * 
     * @param vms the VMs to place
     * @param hosts the available hosts
     * @return the initialized population
     */
    private List<Hippopotamus> initializePopulation(List<Vm> vms, List<Host> hosts) {
        List<Hippopotamus> population = new ArrayList<>();
        
        for (int i = 0; i < parameters.getPopulationSize(); i++) {
            HippopotamusVmAllocationPolicy.Solution solution = createRandomSolution(vms, hosts);
            double fitness = evaluateFitness(solution);
            
            Hippopotamus hippo = new Hippopotamus(solution, fitness);
            population.add(hippo);
            
            logger.trace("Initialized hippo {} with fitness {}", i, fitness);
        }
        
        return population;
    }
    
    /**
     * Creates a random solution for VM placement.
     * 
     * @param vms the VMs to place
     * @param hosts the available hosts
     * @return a random solution
     */
    private HippopotamusVmAllocationPolicy.Solution createRandomSolution(List<Vm> vms, List<Host> hosts) {
        HippopotamusVmAllocationPolicy.Solution solution = new HippopotamusVmAllocationPolicy.Solution();
        
        for (Vm vm : vms) {
            // Find suitable hosts for this VM
            List<Host> suitableHosts = hosts.stream()
                .filter(host -> host.isSuitableForVm(vm))
                .collect(Collectors.toList());
            
            if (!suitableHosts.isEmpty()) {
                Host selectedHost = suitableHosts.get(random.nextInt(suitableHosts.size()));
                solution.addMapping(vm, selectedHost);
            }
        }
        
        return solution;
    }
    
    /**
     * Finds the leader hippo (best solution) in the population.
     * 
     * @param population the population of hippos
     * @return the leader hippo
     */
    private Hippopotamus findLeader(List<Hippopotamus> population) {
        return population.stream()
            .min(Comparator.comparingDouble(Hippopotamus::getFitness))
            .orElseThrow(() -> new IllegalStateException("Population is empty"));
    }
    
    /**
     * Finds a random prey hippo for position updates.
     * 
     * @param population the population of hippos
     * @return a random prey hippo
     */
    private Hippopotamus findPrey(List<Hippopotamus> population) {
        return population.get(random.nextInt(population.size()));
    }
    
    /**
     * Updates the position of a hippo based on the HO algorithm equations.
     * 
     * <p>Position update equation:
     * newPosition = currentPosition + α * (leaderPosition - currentPosition) + 
     *               β * rand() * (preyPosition - currentPosition) +
     *               γ * levy() * (currentPosition)</p>
     * 
     * @param hippo the hippo to update
     * @param leader the leader hippo
     * @param prey the prey hippo
     */
    private void updatePosition(Hippopotamus hippo, Hippopotamus leader, Hippopotamus prey) {
        HippopotamusVmAllocationPolicy.Solution currentSolution = hippo.getSolution();
        HippopotamusVmAllocationPolicy.Solution leaderSolution = leader.getSolution();
        HippopotamusVmAllocationPolicy.Solution preySolution = prey.getSolution();
        
        // Create new solution based on position update
        HippopotamusVmAllocationPolicy.Solution newSolution = new HippopotamusVmAllocationPolicy.Solution();
        
        // Apply position update for each VM mapping
        for (Map.Entry<Vm, Host> entry : currentSolution.getAllocations().entrySet()) {
            Vm vm = entry.getKey();
            Host currentHost = entry.getValue();
            
            // Get leader and prey hosts
            Host leaderHost = leaderSolution.getHostForVm(vm);
            Host preyHost = preySolution.getHostForVm(vm);
            
            if (leaderHost != null && preyHost != null) {
                // Calculate new host based on HO equations
                Host newHost = selectNewHost(vm, currentHost, leaderHost, preyHost);
                newSolution.addMapping(vm, newHost);
            } else {
                // Keep current mapping if leader/prey mapping not available
                newSolution.addMapping(vm, currentHost);
            }
        }
        
        // Validate and repair solution
        newSolution = repairSolution(newSolution);
        
        hippo.setSolution(newSolution);
    }
    
    /**
     * Selects a new host for a VM based on the HO position update equations.
     * 
     * @param vm the VM to place
     * @param currentHost the current host
     * @param leaderHost the leader's host
     * @param preyHost the prey's host
     * @return the new host
     */
    private Host selectNewHost(Vm vm, Host currentHost, Host leaderHost, Host preyHost) {
        // For discrete host selection, we use a probabilistic approach
        double rand = random.nextDouble();
        double levy = generateLevyFlight();
        
        // Calculate selection probabilities
        double probLeader = parameters.getAlpha();
        double probPrey = parameters.getBeta() * rand;
        double probRandom = parameters.getGamma() * levy;
        
        // Normalize probabilities
        double totalProb = probLeader + probPrey + probRandom;
        probLeader /= totalProb;
        probPrey /= totalProb;
        probRandom /= totalProb;
        
        // Select host based on probabilities
        double selection = random.nextDouble();
        if (selection < probLeader) {
            return leaderHost;
        } else if (selection < probLeader + probPrey) {
            return preyHost;
        } else {
            // Random selection from available hosts
            List<Host> availableHosts = currentHost.getDatacenter().getHostList().stream()
                .filter(host -> host.isSuitableForVm(vm))
                .collect(Collectors.toList());
            
            if (!availableHosts.isEmpty()) {
                return availableHosts.get(random.nextInt(availableHosts.size()));
            }
        }
        
        return currentHost;
    }
    
    /**
     * Generates a Levy flight random number for exploration.
     * 
     * @return a Levy flight value
     */
    private double generateLevyFlight() {
        // Simplified Levy flight generation using Mantegna's algorithm
        double sigma = Math.pow(
            (Gamma.gamma(1 + 1.5) * Math.sin(Math.PI * 1.5 / 2)) /
            (Gamma.gamma((1 + 1.5) / 2) * 1.5 * Math.pow(2, (1.5 - 1) / 2)),
            1 / 1.5);
        
        double u = random.nextGaussian() * sigma;
        double v = random.nextGaussian();
        
        return u / Math.pow(Math.abs(v), 1 / 1.5);
    }
    
    /**
     * Evaluates the fitness of a solution using the multi-objective function.
     * 
     * <p>Fitness = w1 * (1 - resourceUtilization) + w2 * powerConsumption + w3 * slaViolations</p>
     * 
     * @param solution the solution to evaluate
     * @return the fitness value (lower is better)
     */
    public double evaluateFitness(HippopotamusVmAllocationPolicy.Solution solution) {
        if (solution == null || solution.getAllocations().isEmpty()) {
            return Double.MAX_VALUE;
        }
        
        // Calculate resource utilization
        double cpuUtilization = calculateCpuUtilization(solution);
        double ramUtilization = calculateRamUtilization(solution);
        double resourceUtilization = (cpuUtilization + ramUtilization) / 2.0;
        
        // Calculate power consumption
        double powerConsumption = calculatePowerConsumption(solution);
        
        // Calculate SLA violations
        double slaViolations = calculateSLAViolations(solution);
        
        // Normalize values to [0,1] range
        resourceUtilization = Math.max(0.0, Math.min(1.0, resourceUtilization));
        powerConsumption = Math.max(0.0, Math.min(1.0, powerConsumption / 1000.0));
        slaViolations = Math.max(0.0, Math.min(1.0, slaViolations));
        
        // Calculate weighted fitness
        double fitness = 
            parameters.getWeightUtilization() * (1.0 - resourceUtilization) +
            parameters.getWeightPower() * powerConsumption +
            parameters.getWeightSLA() * slaViolations;
        
        // Cache fitness for memory efficiency
        String cacheKey = solution.getCacheKey();
        solutionCache.put(cacheKey, solution);
        
        return fitness;
    }
    
    /**
     * Calculates CPU utilization across all hosts in the solution.
     * 
     * @param solution the solution
     * @return the average CPU utilization
     */
    private double calculateCpuUtilization(HippopotamusVmAllocationPolicy.Solution solution) {
        Map<Host, List<Vm>> hostVms = solution.getHostVmsMap();
        
        if (hostVms.isEmpty()) return 0.0;
        
        double totalUtilization = 0.0;
        for (Map.Entry<Host, List<Vm>> entry : hostVms.entrySet()) {
            Host host = entry.getKey();
            List<Vm> vms = entry.getValue();
            
            double usedMips = vms.stream()
                .mapToDouble(Vm::getTotalMipsCapacity)
                .sum();
            
            double utilization = usedMips / host.getTotalMipsCapacity();
            totalUtilization += utilization;
        }
        
        return totalUtilization / hostVms.size();
    }
    
    /**
     * Calculates RAM utilization across all hosts in the solution.
     * 
     * @param solution the solution
     * @return the average RAM utilization
     */
    private double calculateRamUtilization(HippopotamusVmAllocationPolicy.Solution solution) {
        Map<Host, List<Vm>> hostVms = solution.getHostVmsMap();
        
        if (hostVms.isEmpty()) return 0.0;
        
        double totalUtilization = 0.0;
        for (Map.Entry<Host, List<Vm>> entry : hostVms.entrySet()) {
            Host host = entry.getKey();
            List<Vm> vms = entry.getValue();
            
            double usedRam = vms.stream()
                .mapToDouble(vm -> vm.getRam().getCapacity())
                .sum();
            
            double utilization = usedRam / host.getRam().getCapacity();
            totalUtilization += utilization;
        }
        
        return totalUtilization / hostVms.size();
    }
    
    /**
     * Calculates power consumption for the solution.
     * 
     * @param solution the solution
     * @return the total power consumption
     */
    private double calculatePowerConsumption(HippopotamusVmAllocationPolicy.Solution solution) {
        Map<Host, List<Vm>> hostVms = solution.getHostVmsMap();
        
        double totalPower = 0.0;
        for (Map.Entry<Host, List<Vm>> entry : hostVms.entrySet()) {
            Host host = entry.getKey();
            List<Vm> vms = entry.getValue();
            
            // Simplified power model: idle power + dynamic power based on utilization
            double utilization = vms.stream()
                .mapToDouble(Vm::getTotalMipsCapacity)
                .sum() / host.getTotalMipsCapacity();
            
            double power = host.getPowerModel().getPower(utilization);
            totalPower += power;
        }
        
        return totalPower;
    }
    
    /**
     * Calculates SLA violations for the solution.
     * 
     * @param solution the solution
     * @return the number of SLA violations
     */
    private double calculateSLAViolations(HippopotamusVmAllocationPolicy.Solution solution) {
        Map<Host, List<Vm>> hostVms = solution.getHostVmsMap();
        
        int violations = 0;
        for (Map.Entry<Host, List<Vm>> entry : hostVms.entrySet()) {
            Host host = entry.getKey();
            List<Vm> vms = entry.getValue();
            
            // Check CPU over-subscription
            double totalRequestedMips = vms.stream()
                .mapToDouble(Vm::getTotalMipsCapacity)
                .sum();
            
            if (totalRequestedMips > host.getTotalMipsCapacity()) {
                violations++;
            }
            
            // Check RAM over-subscription
            double totalRequestedRam = vms.stream()
                .mapToDouble(vm -> vm.getRam().getCapacity())
                .sum();
            
            if (totalRequestedRam > host.getRam().getCapacity()) {
                violations++;
            }
        }
        
        return violations;
    }
    
    /**
     * Repairs a solution to ensure all VMs are placed and constraints are met.
     * 
     * @param solution the solution to repair
     * @return the repaired solution
     */
    private HippopotamusVmAllocationPolicy.Solution repairSolution(HippopotamusVmAllocationPolicy.Solution solution) {
        HippopotamusVmAllocationPolicy.Solution repaired = new HippopotamusVmAllocationPolicy.Solution();
        
        // Ensure all VMs are placed
        for (Vm vm : solution.getAllocations().keySet()) {
            Host host = solution.getHostForVm(vm);
            
            if (host == null || !host.isSuitableForVm(vm)) {
                // Find new suitable host
                List<Host> availableHosts = solution.getAllocations().values().stream()
                    .distinct()
                    .filter(h -> h.isSuitableForVm(vm))
                    .collect(Collectors.toList());
                
                if (!availableHosts.isEmpty()) {
                    host = availableHosts.get(random.nextInt(availableHosts.size()));
                }
            }
            
            if (host != null) {
                repaired.addMapping(vm, host);
            }
        }
        
        return repaired;
    }
    
    /**
     * Validates a solution to ensure it meets all constraints.
     * 
     * @param solution the solution to validate
     * @param vms the original VM list
     * @param hosts the original host list
     * @throws IllegalStateException if the solution is invalid
     */
    private void validateSolution(HippopotamusVmAllocationPolicy.Solution solution, List<Vm> vms, List<Host> hosts) {
        // Check all VMs are placed
        if (solution.getAllocations().size() != vms.size()) {
            throw new IllegalStateException(
                String.format("Solution does not place all VMs. Expected: %d, Actual: %d",
                             vms.size(), solution.getAllocations().size()));
        }
        
        // Check all VMs are from the original list
        for (Vm vm : solution.getAllocations().keySet()) {
            if (!vms.contains(vm)) {
                throw new IllegalStateException("Solution contains unknown VM: " + vm.getId());
            }
        }
        
        // Check all hosts are from the original list
        for (Host host : solution.getAllocations().values()) {
            if (!hosts.contains(host)) {
                throw new IllegalStateException("Solution contains unknown host: " + host.getId());
            }
        }
        
        logger.debug("Solution validation passed");
    }
    
    /**
     * Gets the convergence history of the optimization process.
     * 
     * @return list of best fitness values per iteration
     */
    public List<Double> getConvergenceHistory() {
        return new ArrayList<>(fitnessHistory);
    }
    
    /**
     * Gets the convergence analyzer for detailed convergence analysis.
     * 
     * @return the convergence analyzer
     */
    public ConvergenceAnalyzer getConvergenceAnalyzer() {
        return convergenceAnalyzer;
    }
    
    /**
     * Gets the current algorithm parameters.
     * 
     * @return the parameters
     */
    public HippopotamusParameters getParameters() {
        return parameters;
    }
    
    /**
     * Clears internal caches to free memory.
     */
    public void clearCache() {
        solutionCache.clear();
        fitnessHistory.clear();
        System.gc();
        logger.debug("Cleared internal caches");
    }
}
