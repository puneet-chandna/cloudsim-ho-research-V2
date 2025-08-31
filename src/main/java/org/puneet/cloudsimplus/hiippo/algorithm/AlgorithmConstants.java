package org.puneet.cloudsimplus.hiippo.algorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * AlgorithmConstants.java
 * 
 * Centralized configuration for all algorithm parameters used in the Hippopotamus Optimization
 * algorithm and related components. This class provides a single source of truth for all
 * configurable constants, optimized for memory efficiency on 16GB systems.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public final class AlgorithmConstants {
    private static final Logger logger = LoggerFactory.getLogger(AlgorithmConstants.class);
    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private AlgorithmConstants() {
        throw new AssertionError("Cannot instantiate AlgorithmConstants");
    }
    
    // ===================================================================================
    // HIPPOPOTAMUS OPTIMIZATION ALGORITHM PARAMETERS
    // ===================================================================================
    
    /**
     * Default population size for the HO algorithm.
     * Optimized for superior VM placement performance and solution quality.
     */
    public static final int DEFAULT_POPULATION_SIZE = 30;  // Optimized for performance
    public static final int MIN_POPULATION_SIZE = 20;       // Optimized for minimum quality
    public static final int MAX_POPULATION_SIZE = 100;      // Optimized for enterprise scenarios
    /**
     * Maximum number of iterations for the HO algorithm.
     * Optimized for performance and VM placement quality.
     */
    public static final int DEFAULT_MAX_ITERATIONS = 50;  // Optimized for performance
    public static final int MIN_ITERATIONS = 20;           // Optimized for minimum quality
    public static final int MAX_ITERATIONS = 200;          // Optimized for enterprise scenarios
    /**
     * Convergence threshold for early termination.
     * Optimized for better VM placement precision and efficiency.
     */
    public static final double DEFAULT_CONVERGENCE_THRESHOLD = 0.001;  // Optimized for faster convergence
    
    /**
     * Maximum number of iterations without improvement before convergence is declared.
     * Optimized for better convergence stability in VM placement.
     */
    public static final int CONVERGENCE_CHECK_WINDOW = 5;  // Optimized for faster convergence

     // ===================================================================================
    // ALGORITHM BEHAVIOR PARAMETERS 
    // ===================================================================================

    /**
     * Elite preservation ratio. Fraction of best solutions preserved between iterations.
     * Optimized for better VM placement solution preservation.
     */
    public static final double ELITE_RATIO = 0.25;  // Better solution preservation

    /**
     * Minimum number of elite solutions to preserve.
     * Optimized for better solution diversity in VM placement.
     */
    public static final int MIN_ELITE_SIZE = 8;  // Better solution diversity

    /**
     * Exploration probability in early iterations.
     * Optimized for better global search in VM placement.
     */
    public static final double INITIAL_EXPLORATION_PROB = 0.95;  // Higher exploration for better global search

    /**
     * Exploitation probability in later iterations.
     * Optimized for better local search in VM placement.
     */
    public static final double FINAL_EXPLOITATION_PROB = 0.05;  // Lower exploitation for better local search

     /**
     * Iteration progress point (as a fraction) at which to start transitioning
     * from exploration to exploitation.
     * Optimized for better exploration-exploitation balance in VM placement.
     */
    public static final double EXPLORATION_DECAY_START = 0.6;  // Longer exploration phase

    /**
     * Random restart probability when stuck in local optima.
     * Optimized for better escape from local optima in VM placement.
     */
    public static final double RANDOM_RESTART_PROB = 0.15;  // Better escape from local optima

    
    // ===================================================================================
    // POSITION UPDATE PARAMETERS
    // ===================================================================================
    
    /**
     * Alpha parameter: Influence of the leader hippopotamus on position updates.
     * Optimized for better VM placement convergence and solution quality.
     * Range: [0.0, 1.0]
     */
    public static final double ALPHA = 0.75;  // Stronger leader influence for better convergence
    
    /**
     * Beta parameter: Exploration parameter for prey influence.
     * Optimized for better exploration-exploitation balance in VM placement.
     * Range: [0.0, 1.0]
     */
    public static final double BETA = 0.15;  // Better exploration balance
    
    /**
     * Gamma parameter: Exploitation parameter for random walk.
     * Optimized for better local search in VM placement optimization.
     * Range: [0.0, 1.0]
     */
    public static final double GAMMA = 0.10;  // Maintained for local search
    
    /**
     * Levy flight parameter for random walk generation.
     * Optimized for better step size distribution in VM placement.
     */
    public static final double LEVY_LAMBDA = 2.2;  // Better step size distribution for VM placement
    
    // ===================================================================================
    // FITNESS FUNCTION WEIGHTS
    // ===================================================================================
    
    /**
     * Weight for resource utilization in fitness function.
     * Optimized for superior VM placement resource efficiency.
     */
    public static final double W_UTILIZATION = 0.60;  // Higher weight for superior resource efficiency
    
    /**
     * Weight for power consumption in fitness function.
     * Optimized for superior power efficiency in VM placement.
     */
    public static final double W_POWER = 0.25;  // Superior power efficiency
    
    /**
     * Weight for SLA violations in fitness function.
     * Optimized for superior SLA compliance in VM placement.
     */
    public static final double W_SLA = 0.15;  // Superior SLA compliance

    public static final double CPU_UTILIZATION_NORM = 1.0;
    public static final double RAM_UTILIZATION_NORM = 1.0;
    public static final double POWER_CONSUMPTION_NORM = 2000.0;
    public static final double SLA_VIOLATION_PENALTY = 0.1;
    
    // ===================================================================================
    // MEMORY MANAGEMENT PARAMETERS
    // ===================================================================================
    
    /**
     * Batch size for processing VMs to manage memory efficiently.
     * Optimized for better memory management in VM placement scenarios.
     */
    public static final int BATCH_SIZE = 15;  // Increased for better memory efficiency
    
    /**
     * Interval in milliseconds for checking memory usage during execution.
     */
    public static final long MEMORY_CHECK_INTERVAL = 5000;
    
    /**
     * Memory warning threshold as a percentage of max heap size.
     * When memory usage exceeds this threshold, garbage collection is triggered.
     */
    public static final double MEMORY_WARNING_THRESHOLD = 0.85;
    
    /**
     * Maximum memory allocation for a single hippopotamus solution.
     * Used to estimate memory requirements for large populations.
     */
    public static final long MAX_SOLUTION_MEMORY_BYTES = 1024 * 1024; // 1MB per solution
    
   // ===================================================================================
    // GENETIC ALGORITHM PARAMETERS (For Baseline Comparison)
    // ===================================================================================

    public static final int GA_POPULATION_SIZE = 30;       // Reduced from 80
    public static final int GA_MAX_GENERATIONS = 50;       // Reduced from 100
    public static final double GA_CROSSOVER_RATE = 0.8;
    public static final double GA_MUTATION_RATE = 0.1;
    public static final int GA_TOURNAMENT_SIZE = 3;

    // ===================================================================================
    // VALIDATION AND PRECISION PARAMETERS
    // ===================================================================================
    
    public static final double EPSILON = 1e-9; // For floating-point comparisons
    public static final double MIN_IMPROVEMENT = 1e-6; // Minimum improvement to consider solutions different
    public static final double MAX_SLA_VIOLATION_RATIO = 0.05;
    public static final double MIN_RESOURCE_UTILIZATION = 0.5;
    
     // ===================================================================================
    // EXPERIMENT CONFIGURATION CONSTANTS
    // ===================================================================================

    public static final int REPLICATION_COUNT = 5;  // Reduced for faster verification
    public static final double CONFIDENCE_LEVEL = 0.95;
    public static final double SIGNIFICANCE_LEVEL = 0.05;
    public static final long RANDOM_SEED = 123456L;

    // ===================================================================================
    // SCENARIO SCALING CONSTANTS
    // ===================================================================================

    public static final int[] VM_SCALING_FACTORS = {10, 50, 100, 200, 500};
    public static final int[] HOST_SCALING_FACTORS = {3, 10, 20, 40, 100};
    public static final int MAX_VMS_16GB = 500;
    public static final int MAX_HOSTS_16GB = 100;

    // ===================================================================================
    // FILE AND DIRECTORY CONSTANTS
    // ===================================================================================

    public static final String RESULTS_BASE_DIR = "results";
    public static final String RAW_RESULTS_DIR = RESULTS_BASE_DIR + "/raw_results";
    public static final String STATISTICAL_RESULTS_DIR = RESULTS_BASE_DIR + "/statistical_analysis";
    public static final String PARAMETER_SENSITIVITY_DIR = RESULTS_BASE_DIR + "/parameter_sensitivity";
    public static final String SCALABILITY_DIR = RESULTS_BASE_DIR + "/scalability_analysis";
    public static final String CONVERGENCE_DIR = RESULTS_BASE_DIR + "/convergence_data";
    public static final String COMPARISON_DIR = RESULTS_BASE_DIR + "/comparison_data";

    // ===================================================================================
    // UTILITY, VALIDATION, AND DYNAMIC METHODS (MERGED)
    // ===================================================================================

    /**
     * Validates population size parameter.
     */
    public static int validatePopulationSize(int populationSize) {
        if (populationSize < MIN_POPULATION_SIZE || populationSize > MAX_POPULATION_SIZE) {
            String message = String.format("Population size must be between %d and %d, but was %d",
                    MIN_POPULATION_SIZE, MAX_POPULATION_SIZE, populationSize);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        return populationSize;
    }

    /**
     * Validates maximum iterations parameter.
     */
    public static int validateMaxIterations(int maxIterations) {
        if (maxIterations < MIN_ITERATIONS || maxIterations > MAX_ITERATIONS) {
            String message = String.format("Max iterations must be between %d and %d, but was %d",
                    MIN_ITERATIONS, MAX_ITERATIONS, maxIterations);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        return maxIterations;
    }

    /**
     * Validates convergence threshold parameter.
     */
    public static double validateConvergenceThreshold(double threshold) {
        if (threshold <= 0.0 || threshold > 0.1) {
            String message = String.format("Convergence threshold must be > 0.0 and <= 0.1, but was %.4f", threshold);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        return threshold;
    }

    /**
     * Validates fitness weight parameters.
     */
    public static void validateFitnessWeights(double wUtilization, double wPower, double wSLA) {
        double sum = wUtilization + wPower + wSLA;
        if (Math.abs(sum - 1.0) > EPSILON) {
            String message = String.format("Fitness weights must sum to 1.0. Current sum: %.4f", sum);
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
        if (wUtilization < 0 || wPower < 0 || wSLA < 0) {
            String message = "All fitness weights must be non-negative.";
            logger.error(message);
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Gets exploration probability based on current iteration progress.
     */
    public static double getExplorationProbability(int currentIteration, int maxIterations) {
        if (currentIteration < 0 || maxIterations <= 0 || currentIteration > maxIterations) {
            return INITIAL_EXPLORATION_PROB;
        }

        double progress = (double) currentIteration / maxIterations;
        if (progress < EXPLORATION_DECAY_START) {
            return INITIAL_EXPLORATION_PROB;
        } else {
            double decayProgress = (progress - EXPLORATION_DECAY_START) / (1.0 - EXPLORATION_DECAY_START);
            return INITIAL_EXPLORATION_PROB - (INITIAL_EXPLORATION_PROB - FINAL_EXPLOITATION_PROB) * decayProgress;
        }
    }
    
    /**
     * Estimates the memory requirement for a given scenario.
     */
    public static long estimateMemoryRequirement(int vmCount, int hostCount, int populationSize) {
        long baseMemory = 50_000_000L; // 50MB Base overhead
        long vmMemory = (long) vmCount * 10_000L; // 10KB per VM
        long hostMemory = (long) hostCount * 20_000L; // 20KB per Host
        long algorithmMemory = (long) populationSize * MAX_SOLUTION_MEMORY_BYTES;
        return baseMemory + vmMemory + hostMemory + algorithmMemory;
    }

    /**
     * Logs all key algorithm constants for debugging and reproducibility.
     */
    public static void logAlgorithmConfiguration() {
        logger.info("=== Hippopotamus Optimization Algorithm Configuration ===");
        logger.info("Population Size: {} (min: {}, max: {})",
                DEFAULT_POPULATION_SIZE, MIN_POPULATION_SIZE, MAX_POPULATION_SIZE);
        logger.info("Max Iterations: {} (min: {}, max: {})",
                DEFAULT_MAX_ITERATIONS, MIN_ITERATIONS, MAX_ITERATIONS);
        logger.info("Convergence Threshold: {}, Check Window: {}", DEFAULT_CONVERGENCE_THRESHOLD, CONVERGENCE_CHECK_WINDOW);
        logger.info("Position Update - Alpha: {}, Beta: {}, Gamma: {}", ALPHA, BETA, GAMMA);
        logger.info("Fitness Weights - Utilization: {}, Power: {}, SLA: {}", W_UTILIZATION, W_POWER, W_SLA);
        logger.info("Behavior - Elite Ratio: {}, Random Restart: {}", ELITE_RATIO, RANDOM_RESTART_PROB);
        logger.info("======================================================");
    }
}