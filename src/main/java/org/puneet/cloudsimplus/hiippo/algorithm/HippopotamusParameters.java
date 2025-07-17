package org.puneet.cloudsimplus.hiippo.algorithm;

import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration parameters for the Hippopotamus Optimization (HO) algorithm.
 * This class encapsulates all tunable parameters used by the HO algorithm,
 * providing both default values and validation for research-grade experiments.
 * 
 * <p>The parameters are designed to be memory-efficient for 16GB systems while
 * maintaining algorithmic effectiveness for VM placement optimization.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class HippopotamusParameters {
    
    private static final Logger logger = LoggerFactory.getLogger(HippopotamusParameters.class);
    
    // Core algorithm parameters
    private int populationSize;
    private int maxIterations;
    private double convergenceThreshold;
    
    // Position update parameters
    private double alpha;  // Leader influence factor
    private double beta;   // Prey influence factor
    private double gamma;  // Random walk influence factor
    
    // Fitness function weights (normalized to sum to 1.0)
    private double weightUtilization;
    private double weightPower;
    private double weightSLA;
    
    // Exploration parameters
    private double explorationRate;
    private double exploitationRate;
    
    // Memory management parameters
    private int batchSize;
    private long memoryCheckInterval;
    
    // Convergence tracking
    private boolean enableConvergenceTracking;
    private int convergenceWindowSize;
    
    /**
     * Creates a new HippopotamusParameters instance with default values
     * optimized for 16GB memory systems.
     */
    public HippopotamusParameters() {
        this.populationSize = AlgorithmConstants.DEFAULT_POPULATION_SIZE;
        this.maxIterations = AlgorithmConstants.DEFAULT_MAX_ITERATIONS;
        this.convergenceThreshold = AlgorithmConstants.DEFAULT_CONVERGENCE_THRESHOLD;
        
        this.alpha = AlgorithmConstants.ALPHA;
        this.beta = AlgorithmConstants.BETA;
        this.gamma = AlgorithmConstants.GAMMA;
        
        this.weightUtilization = AlgorithmConstants.W_UTILIZATION;
        this.weightPower = AlgorithmConstants.W_POWER;
        this.weightSLA = AlgorithmConstants.W_SLA;
        
        this.explorationRate = 0.7;
        this.exploitationRate = 0.3;
        
        this.batchSize = AlgorithmConstants.BATCH_SIZE;
        this.memoryCheckInterval = AlgorithmConstants.MEMORY_CHECK_INTERVAL;
        
        this.enableConvergenceTracking = true;
        this.convergenceWindowSize = 10;
        
        validateParameters();
        logger.info("Initialized HippopotamusParameters with defaults: {}", this);
    }
    
    /**
     * Creates a new HippopotamusParameters instance with custom values.
     * 
     * @param populationSize the population size
     * @param maxIterations the maximum number of iterations
     * @param convergenceThreshold the convergence threshold
     * @param alpha the leader influence factor
     * @param beta the prey influence factor
     * @param gamma the random walk influence factor
     * @param weightUtilization the resource utilization weight
     * @param weightPower the power consumption weight
     * @param weightSLA the SLA violation weight
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public HippopotamusParameters(int populationSize, int maxIterations, 
                                  double convergenceThreshold,
                                  double alpha, double beta, double gamma,
                                  double weightUtilization, double weightPower, 
                                  double weightSLA) {
        
        setPopulationSize(populationSize);
        setMaxIterations(maxIterations);
        setConvergenceThreshold(convergenceThreshold);
        setAlpha(alpha);
        setBeta(beta);
        setGamma(gamma);
        setWeightUtilization(weightUtilization);
        setWeightPower(weightPower);
        setWeightSLA(weightSLA);
        
        // Set defaults for other parameters
        this.explorationRate = 0.7;
        this.exploitationRate = 0.3;
        this.batchSize = AlgorithmConstants.BATCH_SIZE;
        this.memoryCheckInterval = AlgorithmConstants.MEMORY_CHECK_INTERVAL;
        this.enableConvergenceTracking = true;
        this.convergenceWindowSize = 10;
        
        validateParameters();
        logger.info("Initialized HippopotamusParameters with custom values: {}", this);
    }
    
    /**
     * Validates all parameters to ensure they are within acceptable ranges.
     * 
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private void validateParameters() {
        if (populationSize <= 0 || populationSize > 100) {
            throw new IllegalArgumentException(
                "Population size must be between 1 and 100, got: " + populationSize);
        }
        
        if (maxIterations <= 0 || maxIterations > 1000) {
            throw new IllegalArgumentException(
                "Max iterations must be between 1 and 1000, got: " + maxIterations);
        }
        
        if (convergenceThreshold < 0.0 || convergenceThreshold > 1.0) {
            throw new IllegalArgumentException(
                "Convergence threshold must be between 0.0 and 1.0, got: " + convergenceThreshold);
        }
        
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException(
                "Alpha must be between 0.0 and 1.0, got: " + alpha);
        }
        
        if (beta < 0.0 || beta > 1.0) {
            throw new IllegalArgumentException(
                "Beta must be between 0.0 and 1.0, got: " + beta);
        }
        
        if (gamma < 0.0 || gamma > 1.0) {
            throw new IllegalArgumentException(
                "Gamma must be between 0.0 and 1.0, got: " + gamma);
        }
        
        double weightSum = weightUtilization + weightPower + weightSLA;
        if (Math.abs(weightSum - 1.0) > 0.001) {
            throw new IllegalArgumentException(
                "Fitness weights must sum to 1.0, current sum: " + weightSum);
        }
        
        if (batchSize <= 0 || batchSize > 100) {
            throw new IllegalArgumentException(
                "Batch size must be between 1 and 100, got: " + batchSize);
        }
        
        if (memoryCheckInterval < 1000 || memoryCheckInterval > 60000) {
            throw new IllegalArgumentException(
                "Memory check interval must be between 1000 and 60000 ms, got: " + memoryCheckInterval);
        }
        
        if (convergenceWindowSize <= 0 || convergenceWindowSize > 50) {
            throw new IllegalArgumentException(
                "Convergence window size must be between 1 and 50, got: " + convergenceWindowSize);
        }
    }
    
    /**
     * Creates a deep copy of this parameters instance.
     * 
     * @return a new HippopotamusParameters instance with identical values
     */
    public HippopotamusParameters copy() {
        HippopotamusParameters copy = new HippopotamusParameters();
        copy.populationSize = this.populationSize;
        copy.maxIterations = this.maxIterations;
        copy.convergenceThreshold = this.convergenceThreshold;
        copy.alpha = this.alpha;
        copy.beta = this.beta;
        copy.gamma = this.gamma;
        copy.weightUtilization = this.weightUtilization;
        copy.weightPower = this.weightPower;
        copy.weightSLA = this.weightSLA;
        copy.explorationRate = this.explorationRate;
        copy.exploitationRate = this.exploitationRate;
        copy.batchSize = this.batchSize;
        copy.memoryCheckInterval = this.memoryCheckInterval;
        copy.enableConvergenceTracking = this.enableConvergenceTracking;
        copy.convergenceWindowSize = this.convergenceWindowSize;
        return copy;
    }
    
    /**
     * Creates a parameters instance optimized for small-scale experiments.
     * 
     * @return parameters with reduced population and iterations
     */
    public static HippopotamusParameters createSmallScale() {
        HippopotamusParameters params = new HippopotamusParameters();
        params.setPopulationSize(10);
        params.setMaxIterations(25);
        params.setBatchSize(5);
        logger.info("Created small-scale parameters: {}", params);
        return params;
    }
    
    /**
     * Creates a parameters instance optimized for large-scale experiments.
     * 
     * @return parameters with increased population and iterations
     */
    public static HippopotamusParameters createLargeScale() {
        HippopotamusParameters params = new HippopotamusParameters();
        params.setPopulationSize(30);
        params.setMaxIterations(100);
        params.setBatchSize(20);
        logger.info("Created large-scale parameters: {}", params);
        return params;
    }
    
    /**
     * Creates a parameters instance for parameter sensitivity analysis.
     * 
     * @param parameterName the parameter to vary
     * @param value the value for the parameter
     * @return parameters with the specified parameter varied
     */
    public static HippopotamusParameters createForSensitivity(String parameterName, double value) {
        HippopotamusParameters params = new HippopotamusParameters();
        
        switch (parameterName.toLowerCase()) {
            case "population":
            case "populationsize":
                params.setPopulationSize((int) value);
                break;
            case "iterations":
            case "maxiterations":
                params.setMaxIterations((int) value);
                break;
            case "alpha":
                params.setAlpha(value);
                break;
            case "beta":
                params.setBeta(value);
                break;
            case "gamma":
                params.setGamma(value);
                break;
            case "utilization":
            case "weightutilization":
                params.setWeightUtilization(value);
                params.setWeightPower((1.0 - value) * 0.5);
                params.setWeightSLA((1.0 - value) * 0.5);
                break;
            default:
                throw new IllegalArgumentException("Unknown parameter: " + parameterName);
        }
        
        logger.info("Created sensitivity parameters for {}={}: {}", parameterName, value, params);
        return params;
    }
    
    // Getters and setters with validation
    
    public int getPopulationSize() {
        return populationSize;
    }
    
    public void setPopulationSize(int populationSize) {
        if (populationSize <= 0 || populationSize > 100) {
            throw new IllegalArgumentException(
                "Population size must be between 1 and 100, got: " + populationSize);
        }
        this.populationSize = populationSize;
    }
    
    public int getMaxIterations() {
        return maxIterations;
    }
    
    public void setMaxIterations(int maxIterations) {
        if (maxIterations <= 0 || maxIterations > 1000) {
            throw new IllegalArgumentException(
                "Max iterations must be between 1 and 1000, got: " + maxIterations);
        }
        this.maxIterations = maxIterations;
    }
    
    public double getConvergenceThreshold() {
        return convergenceThreshold;
    }
    
    public void setConvergenceThreshold(double convergenceThreshold) {
        if (convergenceThreshold < 0.0 || convergenceThreshold > 1.0) {
            throw new IllegalArgumentException(
                "Convergence threshold must be between 0.0 and 1.0, got: " + convergenceThreshold);
        }
        this.convergenceThreshold = convergenceThreshold;
    }
    
    public double getAlpha() {
        return alpha;
    }
    
    public void setAlpha(double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException(
                "Alpha must be between 0.0 and 1.0, got: " + alpha);
        }
        this.alpha = alpha;
    }
    
    public double getBeta() {
        return beta;
    }
    
    public void setBeta(double beta) {
        if (beta < 0.0 || beta > 1.0) {
            throw new IllegalArgumentException(
                "Beta must be between 0.0 and 1.0, got: " + beta);
        }
        this.beta = beta;
    }
    
    public double getGamma() {
        return gamma;
    }
    
    public void setGamma(double gamma) {
        if (gamma < 0.0 || gamma > 1.0) {
            throw new IllegalArgumentException(
                "Gamma must be between 0.0 and 1.0, got: " + gamma);
        }
        this.gamma = gamma;
    }
    
    public double getWeightUtilization() {
        return weightUtilization;
    }
    
    public void setWeightUtilization(double weightUtilization) {
        if (weightUtilization < 0.0 || weightUtilization > 1.0) {
            throw new IllegalArgumentException(
                "Weight utilization must be between 0.0 and 1.0, got: " + weightUtilization);
        }
        this.weightUtilization = weightUtilization;
    }
    
    public double getWeightPower() {
        return weightPower;
    }
    
    public void setWeightPower(double weightPower) {
        if (weightPower < 0.0 || weightPower > 1.0) {
            throw new IllegalArgumentException(
                "Weight power must be between 0.0 and 1.0, got: " + weightPower);
        }
        this.weightPower = weightPower;
    }
    
    public double getWeightSLA() {
        return weightSLA;
    }
    
    public void setWeightSLA(double weightSLA) {
        if (weightSLA < 0.0 || weightSLA > 1.0) {
            throw new IllegalArgumentException(
                "Weight SLA must be between 0.0 and 1.0, got: " + weightSLA);
        }
        this.weightSLA = weightSLA;
    }
    
    public double getExplorationRate() {
        return explorationRate;
    }
    
    public void setExplorationRate(double explorationRate) {
        if (explorationRate < 0.0 || explorationRate > 1.0) {
            throw new IllegalArgumentException(
                "Exploration rate must be between 0.0 and 1.0, got: " + explorationRate);
        }
        this.explorationRate = explorationRate;
    }
    
    public double getExploitationRate() {
        return exploitationRate;
    }
    
    public void setExploitationRate(double exploitationRate) {
        if (exploitationRate < 0.0 || exploitationRate > 1.0) {
            throw new IllegalArgumentException(
                "Exploitation rate must be between 0.0 and 1.0, got: " + exploitationRate);
        }
        this.exploitationRate = exploitationRate;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    
    public void setBatchSize(int batchSize) {
        if (batchSize <= 0 || batchSize > 100) {
            throw new IllegalArgumentException(
                "Batch size must be between 1 and 100, got: " + batchSize);
        }
        this.batchSize = batchSize;
    }
    
    public long getMemoryCheckInterval() {
        return memoryCheckInterval;
    }
    
    public void setMemoryCheckInterval(long memoryCheckInterval) {
        if (memoryCheckInterval < 1000 || memoryCheckInterval > 60000) {
            throw new IllegalArgumentException(
                "Memory check interval must be between 1000 and 60000 ms, got: " + memoryCheckInterval);
        }
        this.memoryCheckInterval = memoryCheckInterval;
    }
    
    public boolean isEnableConvergenceTracking() {
        return enableConvergenceTracking;
    }
    
    public void setEnableConvergenceTracking(boolean enableConvergenceTracking) {
        this.enableConvergenceTracking = enableConvergenceTracking;
    }
    
    public int getConvergenceWindowSize() {
        return convergenceWindowSize;
    }
    
    public void setConvergenceWindowSize(int convergenceWindowSize) {
        if (convergenceWindowSize <= 0 || convergenceWindowSize > 50) {
            throw new IllegalArgumentException(
                "Convergence window size must be between 1 and 50, got: " + convergenceWindowSize);
        }
        this.convergenceWindowSize = convergenceWindowSize;
    }
    
    @Override
    public String toString() {
        return String.format(
            "HippopotamusParameters{populationSize=%d, maxIterations=%d, " +
            "convergenceThreshold=%.4f, alpha=%.2f, beta=%.2f, gamma=%.2f, " +
            "weights=[%.2f,%.2f,%.2f], batchSize=%d}",
            populationSize, maxIterations, convergenceThreshold,
            alpha, beta, gamma, weightUtilization, weightPower, weightSLA,
            batchSize);
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HippopotamusParameters that = (HippopotamusParameters) o;
        return populationSize == that.populationSize &&
               maxIterations == that.maxIterations &&
               Double.compare(that.convergenceThreshold, convergenceThreshold) == 0 &&
               Double.compare(that.alpha, alpha) == 0 &&
               Double.compare(that.beta, beta) == 0 &&
               Double.compare(that.gamma, gamma) == 0 &&
               Double.compare(that.weightUtilization, weightUtilization) == 0 &&
               Double.compare(that.weightPower, weightPower) == 0 &&
               Double.compare(that.weightSLA, weightSLA) == 0 &&
               Double.compare(that.explorationRate, explorationRate) == 0 &&
               Double.compare(that.exploitationRate, exploitationRate) == 0 &&
               batchSize == that.batchSize &&
               memoryCheckInterval == that.memoryCheckInterval &&
               enableConvergenceTracking == that.enableConvergenceTracking &&
               convergenceWindowSize == that.convergenceWindowSize;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(populationSize, maxIterations, convergenceThreshold,
                          alpha, beta, gamma, weightUtilization, weightPower,
                          weightSLA, explorationRate, exploitationRate,
                          batchSize, memoryCheckInterval, enableConvergenceTracking,
                          convergenceWindowSize);
    }
}