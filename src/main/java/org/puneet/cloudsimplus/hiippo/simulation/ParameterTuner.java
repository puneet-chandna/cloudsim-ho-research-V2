package org.puneet.cloudsimplus.hiippo.simulation;

import org.cloudsimplus.hosts.Host;
import org.cloudsimplus.vms.Vm;
import org.puneet.cloudsimplus.hiippo.algorithm.AlgorithmConstants;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusOptimization;
import org.puneet.cloudsimplus.hiippo.algorithm.HippopotamusParameters;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.statistical.ConfidenceInterval;
import org.puneet.cloudsimplus.hiippo.statistical.StatisticalValidator;
import org.puneet.cloudsimplus.hiippo.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ParameterTuner performs systematic parameter tuning for the Hippopotamus Optimization algorithm.
 * It tests different parameter combinations and identifies optimal settings through grid search
 * and sensitivity analysis. This class acts as a coordinator, delegating actual experiment
 * execution to ExperimentRunner.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-24
 */
public class ParameterTuner {
    private static final Logger logger = LoggerFactory.getLogger(ParameterTuner.class);
    
    // Parameter ranges for tuning
    private static final int[] POPULATION_SIZES = {10, 20, 30, 40, 50};
    private static final int[] MAX_ITERATIONS = {30, 50, 70, 100};
    private static final double[] ALPHA_VALUES = {0.3, 0.4, 0.5, 0.6, 0.7};
    private static final double[] BETA_VALUES = {0.2, 0.3, 0.4, 0.5};
    private static final double[] GAMMA_VALUES = {0.1, 0.2, 0.3, 0.4};
    
    // Fitness weight combinations
    private static final double[][] WEIGHT_COMBINATIONS = {
        {0.4, 0.3, 0.3}, // Default
        {0.5, 0.25, 0.25}, // Utilization focused
        {0.3, 0.4, 0.3}, // Power focused
        {0.3, 0.3, 0.4}, // SLA focused
        {0.33, 0.33, 0.34} // Balanced
    };
    
    // Tuning configuration
    private static final int TUNING_REPLICATIONS = 10; // Reduced for faster tuning
    private static final String TUNING_SCENARIO = "Medium"; // Default scenario for tuning
    private static final int BATCH_SIZE = AlgorithmConstants.BATCH_SIZE; // Use consistent batch size
    private static final int DEFAULT_THREAD_POOL_SIZE = 2;
    
    // Default scenario specifications (VM count, Host count)
    private static final Map<String, int[]> SCENARIO_SPECS = Map.of(
        "Micro", new int[]{10, 3},
        "Small", new int[]{50, 10},
        "Medium", new int[]{100, 20},
        "Large", new int[]{200, 40},
        "XLarge", new int[]{500, 100}
    );
    
    private final ExperimentRunner experimentRunner;
    private final StatisticalValidator statisticalValidator;
    private final CSVResultsWriter csvWriter;
    private final Map<String, ParameterEvaluationResult> parameterCache;
    private final ExecutorService executorService;
    private final int threadPoolSize;
    
    /**
     * Constructs a new ParameterTuner instance with default thread pool size.
     */
    public ParameterTuner() {
        this(DEFAULT_THREAD_POOL_SIZE);
    }
    
    /**
     * Constructs a new ParameterTuner instance with specified thread pool size.
     * 
     * @param threadPoolSize Number of threads for parallel execution
     * @throws IllegalArgumentException if threadPoolSize is less than 1
     */
    public ParameterTuner(int threadPoolSize) {
        if (threadPoolSize < 1) {
            throw new IllegalArgumentException("Thread pool size must be at least 1");
        }
        
        this.threadPoolSize = threadPoolSize;
        this.experimentRunner = new ExperimentRunner();
        this.statisticalValidator = new StatisticalValidator();
        this.csvWriter = new CSVResultsWriter();
        this.parameterCache = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(threadPoolSize);
        
        // Validate configuration arrays
        validateConfigurationArrays();
        
        logger.info("ParameterTuner initialized with {} threads", threadPoolSize);
    }
    
    /**
     * Validates that all configuration arrays are non-empty.
     * 
     * @throws ValidationException if any configuration array is empty
     */
    private void validateConfigurationArrays() throws ValidationException {
        if (POPULATION_SIZES.length == 0) {
            throw new ValidationException("Population sizes array cannot be empty");
        }
        if (MAX_ITERATIONS.length == 0) {
            throw new ValidationException("Max iterations array cannot be empty");
        }
        if (ALPHA_VALUES.length == 0) {
            throw new ValidationException("Alpha values array cannot be empty");
        }
        if (BETA_VALUES.length == 0) {
            throw new ValidationException("Beta values array cannot be empty");
        }
        if (GAMMA_VALUES.length == 0) {
            throw new ValidationException("Gamma values array cannot be empty");
        }
        if (WEIGHT_COMBINATIONS.length == 0) {
            throw new ValidationException("Weight combinations array cannot be empty");
        }
    }
    
    /**
     * Runs complete parameter tuning process including grid search and sensitivity analysis.
     * 
     * @return TuningResult containing optimal parameters and analysis results
     * @throws ValidationException if tuning fails validation
     */
    public TuningResult runCompleteTuning() throws ValidationException {
        logger.info("Starting complete parameter tuning process");
        
        if (TUNING_SCENARIO == null || TUNING_SCENARIO.isEmpty()) {
            throw new ValidationException("Tuning scenario must be specified");
        }
        
        TuningResult result = new TuningResult();
        
        try {
            // Phase 1: Grid search for optimal parameters
            logger.info("Phase 1: Grid Search");
            GridSearchResult gridSearchResult = performGridSearch();
            
            if (gridSearchResult == null || gridSearchResult.getBestParameters() == null) {
                throw new ValidationException("Grid search failed to produce valid results");
            }
            
            result.setGridSearchResult(gridSearchResult);
            result.setOptimalParameters(gridSearchResult.getBestParameters());
            
            // Clean up after grid search
            MemoryManager.checkMemoryUsage("After Grid Search");
            System.gc();
            
            // Phase 2: Sensitivity analysis on individual parameters
            logger.info("Phase 2: Sensitivity Analysis");
            SensitivityAnalysisResult sensitivityResult = performSensitivityAnalysis(
                gridSearchResult.getBestParameters());
            
            if (sensitivityResult == null) {
                throw new ValidationException("Sensitivity analysis failed");
            }
            
            result.setSensitivityAnalysisResult(sensitivityResult);
            
            // Phase 3: Weight optimization
            logger.info("Phase 3: Weight Optimization");
            WeightOptimizationResult weightResult = optimizeFitnessWeights(
                gridSearchResult.getBestParameters());
            
            if (weightResult == null || weightResult.getOptimalWeights() == null) {
                throw new ValidationException("Weight optimization failed");
            }
            
            result.setWeightOptimizationResult(weightResult);
            
            // Save all results
            saveTuningResults(result);
            
            // Clear cache after completion
            parameterCache.clear();
            
            logger.info("Parameter tuning completed successfully");
            return result;
            
        } catch (Exception e) {
            logger.error("Parameter tuning failed", e);
            throw new RuntimeException("Parameter tuning failed", e);
        } finally {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
    
    /**
     * Performs grid search to find optimal parameter combinations.
     * 
     * @return GridSearchResult containing best parameters and performance metrics
     * @throws ValidationException if grid search fails validation
     */
    private GridSearchResult performGridSearch() throws ValidationException {
        logger.info("Starting grid search for parameter optimization");
        
        List<ParameterSet> parameterSets = generateParameterCombinations();
        
        if (parameterSets.isEmpty()) {
            throw new ValidationException("No parameter combinations generated for grid search");
        }
        
        logger.info("Testing {} parameter combinations", parameterSets.size());
        
        GridSearchResult result = new GridSearchResult();
        Map<ParameterSet, PerformanceMetrics> performanceMap = new ConcurrentHashMap<>();
        
        // Process parameter sets in batches
        BatchProcessor.processBatches(parameterSets, batch -> {
            processBatchOfParameters(batch, performanceMap);
        }, BATCH_SIZE);
        
        if (performanceMap.isEmpty()) {
            throw new ValidationException("No parameter sets were successfully evaluated");
        }
        
        // Find best performing parameter set
        ParameterSet bestParameters = findBestParameters(performanceMap);
        
        if (bestParameters == null) {
            throw new ValidationException("Failed to find best parameters");
        }
        
        result.setBestParameters(bestParameters);
        result.setPerformanceMap(performanceMap);
        result.setSearchCompletionTime(LocalDateTime.now());
        
        // Log top 5 parameter sets
        logTopParameterSets(performanceMap, 5);
        
        return result;
    }
    
    /**
     * Processes a batch of parameter sets for evaluation.
     * 
     * @param batch List of parameter sets to evaluate
     * @param performanceMap Map to store performance results
     */
    private void processBatchOfParameters(List<ParameterSet> batch,
                                        Map<ParameterSet, PerformanceMetrics> performanceMap) {
        if (batch == null || batch.isEmpty()) {
            logger.warn("Empty batch provided for processing");
            return;
        }
        
        List<Future<ParameterEvaluationResult>> futures = new ArrayList<>();
        
        // Submit tasks for parallel execution
        for (ParameterSet parameters : batch) {
            if (parameters != null) {
                Future<ParameterEvaluationResult> future = executorService.submit(() -> 
                    evaluateParameterSet(parameters));
                futures.add(future);
            }
        }
        
        // Collect results
        for (int i = 0; i < futures.size(); i++) {
            try {
                ParameterEvaluationResult evalResult = futures.get(i).get(30, TimeUnit.MINUTES);
                
                if (evalResult != null && evalResult.getMetrics() != null) {
                    performanceMap.put(batch.get(i), evalResult.getMetrics());
                    
                    // Save intermediate results using CSVResultsWriter
                    saveIntermediateResult(batch.get(i), evalResult);
                } else {
                    logger.warn("Null evaluation result for parameters: {}", batch.get(i));
                }
                
            } catch (TimeoutException e) {
                logger.error("Parameter evaluation timed out for: {}", batch.get(i));
            } catch (Exception e) {
                logger.error("Failed to evaluate parameters: {}", batch.get(i), e);
            }
        }
        
        // Check memory after batch
        MemoryManager.checkMemoryUsage("Grid Search Batch");
    }
    
    /**
     * Evaluates a single parameter set by running multiple replications.
     * Uses cache to avoid re-running experiments for the same parameter set.
     * Delegates actual experiment execution to ExperimentRunner.
     * 
     * @param parameters Parameter set to evaluate
     * @return ParameterEvaluationResult containing average performance metrics
     * @throws ValidationException if evaluation fails validation
     */
    private ParameterEvaluationResult evaluateParameterSet(ParameterSet parameters) throws ValidationException {
        if (parameters == null) {
            throw new IllegalArgumentException("Cannot evaluate null parameter set");
        }
        
        logger.debug("Evaluating parameters: {}", parameters);
        
        // Check cache first
        String cacheKey = generateCacheKey(parameters);
        if (parameterCache.containsKey(cacheKey)) {
            logger.debug("Using cached result for parameters: {}", parameters);
            return parameterCache.get(cacheKey);
        }
        
        List<ExperimentResult> results = new ArrayList<>();
        
        try {
            // Get scenario specifications
            int[] specs = SCENARIO_SPECS.get(TUNING_SCENARIO);
            if (specs == null) {
                throw new ValidationException("Unknown scenario: " + TUNING_SCENARIO);
            }
            
            // Create test scenario with VM and host counts
            TestScenario scenario = TestScenarios.createScenario(TUNING_SCENARIO, specs[0], specs[1]);
            
            if (scenario == null) {
                throw new ValidationException("Failed to create test scenario");
            }
            
            // Store original HO parameters to restore later
            HippopotamusParameters originalParams = saveOriginalParameters();
            
            try {
                // Set custom parameters for HO algorithm
                setCustomParameters(parameters);
                
                // Run multiple replications using ExperimentRunner
                for (int rep = 0; rep < TUNING_REPLICATIONS; rep++) {
                    ExperimentConfig.initializeRandomSeed(rep);
                    
                    // Use standard ExperimentRunner method
                    ExperimentResult result = experimentRunner.runExperiment("HO", scenario, rep);
                    
                    if (result != null && result.getMetrics() != null && !result.getMetrics().isEmpty()) {
                        // Add parameter information to result
                        result.setParameters(parameters.toMap());
                        results.add(result);
                    } else {
                        logger.warn("Invalid experiment result for replication {}", rep);
                    }
                }
            } finally {
                // Restore original parameters
                restoreOriginalParameters(originalParams);
            }
            
            if (results.isEmpty()) {
                logger.error("No valid results obtained for parameter set");
                return null;
            }
            
            // Calculate average performance
            PerformanceMetrics avgMetrics = calculateAverageMetrics(results);
            
            if (avgMetrics == null) {
                logger.error("Failed to calculate average metrics");
                return null;
            }
            
            // Calculate confidence intervals
            Map<String, ConfidenceInterval> intervals = calculateConfidenceIntervals(results);
            
            ParameterEvaluationResult evalResult = new ParameterEvaluationResult(
                parameters, avgMetrics, intervals, results);
            
            // Cache the result
            parameterCache.put(cacheKey, evalResult);
            
            return evalResult;
            
        } catch (Exception e) {
            logger.error("Failed to evaluate parameter set", e);
            throw new ValidationException("Failed to evaluate parameter set: " + parameters, e);
        }
    }
    
    /**
     * Saves current HO algorithm parameters.
     * 
     * @return Current parameters
     */
    private HippopotamusParameters saveOriginalParameters() {
        HippopotamusParameters params = new HippopotamusParameters();
        params.setPopulationSize(AlgorithmConstants.DEFAULT_POPULATION_SIZE);
        params.setMaxIterations(AlgorithmConstants.DEFAULT_MAX_ITERATIONS);
        params.setAlpha(AlgorithmConstants.ALPHA);
        params.setBeta(AlgorithmConstants.BETA);
        params.setGamma(AlgorithmConstants.GAMMA);
        params.setUtilizationWeight(AlgorithmConstants.W_UTILIZATION);
        params.setPowerWeight(AlgorithmConstants.W_POWER);
        params.setSlaWeight(AlgorithmConstants.W_SLA);
        params.setConvergenceThreshold(AlgorithmConstants.DEFAULT_CONVERGENCE_THRESHOLD);
        return params;
    }
    
    /**
     * Sets custom parameters for HO algorithm.
     * Note: This is now implemented to update the static AlgorithmConstants and HippopotamusOptimization parameters.
     *
     * @param parameters Custom parameters to set
     */
    private void setCustomParameters(ParameterSet parameters) {
        // Set static AlgorithmConstants (if used by HO)
        AlgorithmConstants.DEFAULT_POPULATION_SIZE = parameters.getPopulationSize();
        AlgorithmConstants.DEFAULT_MAX_ITERATIONS = parameters.getMaxIterations();
        AlgorithmConstants.ALPHA = parameters.getAlpha();
        AlgorithmConstants.BETA = parameters.getBeta();
        AlgorithmConstants.GAMMA = parameters.getGamma();
        AlgorithmConstants.W_UTILIZATION = parameters.getWeightUtilization();
        AlgorithmConstants.W_POWER = parameters.getWeightPower();
        AlgorithmConstants.W_SLA = parameters.getWeightSLA();
        // If HippopotamusOptimization uses a singleton or static config, update it here as well
        HippopotamusOptimization.setParameters(convertToHippopotamusParameters(parameters));
        logger.debug("Set custom HO parameters: {}", parameters);
    }

    /**
     * Restores original HO algorithm parameters.
     *
     * @param originalParams Original parameters to restore
     */
    private void restoreOriginalParameters(HippopotamusParameters originalParams) {
        AlgorithmConstants.DEFAULT_POPULATION_SIZE = originalParams.getPopulationSize();
        AlgorithmConstants.DEFAULT_MAX_ITERATIONS = originalParams.getMaxIterations();
        AlgorithmConstants.ALPHA = originalParams.getAlpha();
        AlgorithmConstants.BETA = originalParams.getBeta();
        AlgorithmConstants.GAMMA = originalParams.getGamma();
        AlgorithmConstants.W_UTILIZATION = originalParams.getWeightUtilization();
        AlgorithmConstants.W_POWER = originalParams.getWeightPower();
        AlgorithmConstants.W_SLA = originalParams.getWeightSLA();
        HippopotamusOptimization.setParameters(originalParams);
        logger.debug("Restored original HO parameters");
    }
    
    /**
     * Converts ParameterSet to HippopotamusParameters.
     * 
     * @param parameters ParameterSet to convert
     * @return HippopotamusParameters instance
     */
    private HippopotamusParameters convertToHippopotamusParameters(ParameterSet parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Cannot convert null parameters");
        }
        
        HippopotamusParameters hoParams = new HippopotamusParameters();
        hoParams.setPopulationSize(parameters.getPopulationSize());
        hoParams.setMaxIterations(parameters.getMaxIterations());
        hoParams.setAlpha(parameters.getAlpha());
        hoParams.setBeta(parameters.getBeta());
        hoParams.setGamma(parameters.getGamma());
        hoParams.setUtilizationWeight(parameters.getWeightUtilization());
        hoParams.setPowerWeight(parameters.getWeightPower());
        hoParams.setSlaWeight(parameters.getWeightSLA());
        hoParams.setConvergenceThreshold(AlgorithmConstants.DEFAULT_CONVERGENCE_THRESHOLD);
        
        return hoParams;
    }
    
    /**
     * Generates a cache key for a parameter set.
     * 
     * @param parameters Parameter set
     * @return Cache key string
     */
    private String generateCacheKey(ParameterSet parameters) {
        return String.format("%d_%d_%.3f_%.3f_%.3f_%.3f_%.3f_%.3f",
            parameters.getPopulationSize(),
            parameters.getMaxIterations(),
            parameters.getAlpha(),
            parameters.getBeta(),
            parameters.getGamma(),
            parameters.getWeightUtilization(),
            parameters.getWeightPower(),
            parameters.getWeightSLA()
        );
    }
    
    /**
     * Performs sensitivity analysis on individual parameters.
     * 
     * @param baseParameters Base parameter set to vary from
     * @return SensitivityAnalysisResult containing parameter impacts
     * @throws ValidationException if sensitivity analysis fails validation
     */
    private SensitivityAnalysisResult performSensitivityAnalysis(ParameterSet baseParameters) throws ValidationException {
        if (baseParameters == null) {
            throw new IllegalArgumentException("Cannot perform sensitivity analysis with null base parameters");
        }
        
        logger.info("Starting sensitivity analysis with base parameters: {}", baseParameters);
        
        SensitivityAnalysisResult result = new SensitivityAnalysisResult();
        result.setBaseParameters(baseParameters);
        
        // Analyze each parameter independently
        analyzeSingleParameter("populationSize", POPULATION_SIZES, baseParameters, result);
        analyzeSingleParameter("maxIterations", MAX_ITERATIONS, baseParameters, result);
        analyzeSingleParameter("alpha", ALPHA_VALUES, baseParameters, result);
        analyzeSingleParameter("beta", BETA_VALUES, baseParameters, result);
        analyzeSingleParameter("gamma", GAMMA_VALUES, baseParameters, result);
        
        // Save sensitivity analysis results using CSVResultsWriter
        saveSensitivityResults(result);
        
        return result;
    }
    
    /**
     * Analyzes sensitivity of a single parameter.
     * 
     * @param parameterName Name of parameter to analyze
     * @param values Array of values to test
     * @param baseParameters Base parameter set
     * @param result Result object to populate
     */
    private void analyzeSingleParameter(String parameterName, 
                                      Object values,
                                      ParameterSet baseParameters,
                                      SensitivityAnalysisResult result) {
        if (parameterName == null || values == null || baseParameters == null || result == null) {
            logger.error("Cannot analyze parameter with null inputs");
            return;
        }
        
        logger.info("Analyzing sensitivity for parameter: {}", parameterName);
        
        List<ParameterImpact> impacts = new ArrayList<>();
        
        if (values instanceof int[]) {
            for (int value : (int[]) values) {
                ParameterSet variedParams = baseParameters.copy();
                variedParams.setParameter(parameterName, value);
                
                ParameterEvaluationResult evalResult = evaluateParameterSet(variedParams);
                if (evalResult != null && evalResult.getMetrics() != null) {
                    impacts.add(new ParameterImpact(value, evalResult.getMetrics()));
                }
            }
        } else if (values instanceof double[]) {
            for (double value : (double[]) values) {
                ParameterSet variedParams = baseParameters.copy();
                variedParams.setParameter(parameterName, value);
                
                ParameterEvaluationResult evalResult = evaluateParameterSet(variedParams);
                if (evalResult != null && evalResult.getMetrics() != null) {
                    impacts.add(new ParameterImpact(value, evalResult.getMetrics()));
                }
            }
        }
        
        if (!impacts.isEmpty()) {
            result.addParameterAnalysis(parameterName, impacts);
            
            // Log parameter impact summary
            logParameterImpact(parameterName, impacts);
        } else {
            logger.warn("No valid impacts calculated for parameter: {}", parameterName);
        }
    }
    
    /**
     * Optimizes fitness function weights.
     * 
     * @param baseParameters Base parameter set
     * @return WeightOptimizationResult containing optimal weights
     * @throws ValidationException if weight optimization fails validation
     */
    private WeightOptimizationResult optimizeFitnessWeights(ParameterSet baseParameters) throws ValidationException {
        if (baseParameters == null) {
            throw new IllegalArgumentException("Cannot optimize weights with null base parameters");
        }
        
        logger.info("Starting fitness weight optimization");
        
        WeightOptimizationResult result = new WeightOptimizationResult();
        Map<WeightCombination, PerformanceMetrics> weightPerformance = new HashMap<>();
        
        for (double[] weights : WEIGHT_COMBINATIONS) {
            if (weights.length >= 3) {
                ParameterSet params = baseParameters.copy();
                params.setWeightUtilization(weights[0]);
                params.setWeightPower(weights[1]);
                params.setWeightSLA(weights[2]);
                
                ParameterEvaluationResult evalResult = evaluateParameterSet(params);
                
                if (evalResult != null && evalResult.getMetrics() != null) {
                    WeightCombination combination = new WeightCombination(weights[0], weights[1], weights[2]);
                    weightPerformance.put(combination, evalResult.getMetrics());
                }
            }
        }
        
        if (weightPerformance.isEmpty()) {
            throw new ValidationException("No weight combinations were successfully evaluated");
        }
        
        // Find best weight combination
        WeightCombination bestWeights = findBestWeights(weightPerformance);
        
        if (bestWeights == null) {
            throw new ValidationException("Failed to find best weights");
        }
        
        result.setOptimalWeights(bestWeights);
        result.setWeightPerformanceMap(weightPerformance);
        
        return result;
    }
    
    /**
     * Generates all parameter combinations for grid search.
     * 
     * @return List of parameter sets to test
     */
    private List<ParameterSet> generateParameterCombinations() {
        List<ParameterSet> combinations = new ArrayList<>();
        
        // Limit combinations for memory efficiency
        // Use strategic sampling instead of full grid
        int[] popSizes = (POPULATION_SIZES.length > 3) ? 
            new int[]{POPULATION_SIZES[0], POPULATION_SIZES[POPULATION_SIZES.length/2], 
                     POPULATION_SIZES[POPULATION_SIZES.length-1]} : 
            POPULATION_SIZES;
            
        int[] maxIters = (MAX_ITERATIONS.length > 2) ?
            new int[]{MAX_ITERATIONS[0], MAX_ITERATIONS[MAX_ITERATIONS.length-1]} :
            MAX_ITERATIONS;
            
        double[] alphas = (ALPHA_VALUES.length > 3) ?
            new double[]{ALPHA_VALUES[0], ALPHA_VALUES[ALPHA_VALUES.length/2], 
                        ALPHA_VALUES[ALPHA_VALUES.length-1]} :
            ALPHA_VALUES;
            
        double[] betas = (BETA_VALUES.length > 2) ?
            new double[]{BETA_VALUES[0], BETA_VALUES[BETA_VALUES.length-1]} :
            BETA_VALUES;
            
        double[] gammas = (GAMMA_VALUES.length > 2) ?
            new double[]{GAMMA_VALUES[0], GAMMA_VALUES[GAMMA_VALUES.length-1]} :
            GAMMA_VALUES;
        
        for (int popSize : popSizes) {
            for (int maxIter : maxIters) {
                for (double alpha : alphas) {
                    for (double beta : betas) {
                        for (double gamma : gammas) {
                            ParameterSet params = new ParameterSet();
                            params.setPopulationSize(popSize);
                            params.setMaxIterations(maxIter);
                            params.setAlpha(alpha);
                            params.setBeta(beta);
                            params.setGamma(gamma);
                            params.setWeightUtilization(AlgorithmConstants.W_UTILIZATION);
                            params.setWeightPower(AlgorithmConstants.W_POWER);
                            params.setWeightSLA(AlgorithmConstants.W_SLA);
                            
                            combinations.add(params);
                        }
                    }
                }
            }
        }
        
        logger.info("Generated {} parameter combinations for testing", combinations.size());
        return combinations;
    }
    
    /**
     * Finds the best performing parameter set.
     * 
     * @param performanceMap Map of parameters to performance metrics
     * @return Best performing parameter set
     */
    private ParameterSet findBestParameters(Map<ParameterSet, PerformanceMetrics> performanceMap) {
        if (performanceMap == null || performanceMap.isEmpty()) {
            return null;
        }
        
        return performanceMap.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .min(Comparator.comparing(e -> e.getValue().getOverallScore()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
    
    /**
     * Finds the best weight combination.
     * 
     * @param weightPerformance Map of weights to performance
     * @return Best weight combination
     */
    private WeightCombination findBestWeights(Map<WeightCombination, PerformanceMetrics> weightPerformance) {
        if (weightPerformance == null || weightPerformance.isEmpty()) {
            return null;
        }
        
        return weightPerformance.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .min(Comparator.comparing(e -> e.getValue().getOverallScore()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
    
    /**
     * Calculates average metrics from multiple experiment results.
     * 
     * @param results List of experiment results
     * @return Average performance metrics
     */
    private PerformanceMetrics calculateAverageMetrics(List<ExperimentResult> results) {
        if (results == null || results.isEmpty()) {
            logger.warn("Cannot calculate average metrics from empty results");
            return null;
        }
        
        PerformanceMetrics avgMetrics = new PerformanceMetrics();
        
        // Filter out null results and empty metrics
        List<ExperimentResult> validResults = results.stream()
            .filter(r -> r != null && r.getMetrics() != null && !r.getMetrics().isEmpty())
            .collect(Collectors.toList());
        
        if (validResults.isEmpty()) {
            logger.warn("No valid results to calculate averages");
            return avgMetrics;
        }
        
        // Calculate averages for each metric
        double avgCpuUtil = validResults.stream()
            .mapToDouble(r -> r.getMetrics().getOrDefault("ResourceUtilCPU", 0.0))
            .average().orElse(0.0);
            
        double avgRamUtil = validResults.stream()
            .mapToDouble(r -> r.getMetrics().getOrDefault("ResourceUtilRAM", 0.0))
            .average().orElse(0.0);
            
        double avgPower = validResults.stream()
            .mapToDouble(r -> r.getMetrics().getOrDefault("PowerConsumption", 0.0))
            .average().orElse(0.0);
            
        double avgSLA = validResults.stream()
            .mapToDouble(r -> r.getMetrics().getOrDefault("SLAViolations", 0.0))
            .average().orElse(0.0);
            
        double avgExecTime = validResults.stream()
            .mapToDouble(r -> r.getMetrics().getOrDefault("ExecutionTime", 0.0))
            .average().orElse(0.0);
        
        avgMetrics.setCpuUtilization(avgCpuUtil);
        avgMetrics.setRamUtilization(avgRamUtil);
        avgMetrics.setPowerConsumption(avgPower);
        avgMetrics.setSlaViolations(avgSLA);
        avgMetrics.setExecutionTime(avgExecTime);
        
        return avgMetrics;
    }
    
    /**
     * Calculates confidence intervals for metrics using StatisticalValidator.
     * 
     * @param results List of experiment results
     * @return Map of metric names to confidence intervals
     */
    private Map<String, ConfidenceInterval> calculateConfidenceIntervals(List<ExperimentResult> results) {
        Map<String, ConfidenceInterval> intervals = new HashMap<>();
        
        if (results == null || results.isEmpty()) {
            return intervals;
        }
        
        String[] metrics = {"ResourceUtilCPU", "ResourceUtilRAM", "PowerConsumption", 
                           "SLAViolations", "ExecutionTime"};
        
        for (String metric : metrics) {
            double[] values = results.stream()
                .filter(r -> r != null && r.getMetrics() != null)
                .mapToDouble(r -> r.getMetrics().getOrDefault(metric, 0.0))
                .toArray();
                
            if (values.length > 0) {
                try {
                    ConfidenceInterval interval = statisticalValidator.calculateConfidenceInterval(
                        values, ExperimentConfig.CONFIDENCE_LEVEL);
                    if (interval != null) {
                        intervals.put(metric, interval);
                    }
                } catch (Exception e) {
                    logger.error("Failed to calculate confidence interval for metric: {}", metric, e);
                }
            }
        }
        
        return intervals;
    }
    
    /**
     * Gets the results directory path, creating it if necessary.
     * 
     * @param subdirectory Subdirectory name
     * @return Path to results directory
     */
    private Path getResultsPath(String subdirectory) {
        Path resultsDir = Paths.get("results", "parameter_sensitivity");
        if (subdirectory != null && !subdirectory.isEmpty()) {
            resultsDir = resultsDir.resolve(subdirectory);
        }
        
        try {
            Files.createDirectories(resultsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create results directory: " + resultsDir, e);
        }
        
        return resultsDir;
    }
    
    /**
     * Saves tuning results to CSV files using CSVResultsWriter.
     * 
     * @param result Complete tuning result
     */
    private void saveTuningResults(TuningResult result) {
        if (result == null) {
            logger.error("Cannot save null tuning results");
            return;
        }
        
        try {
            // Save grid search results
            if (result.getGridSearchResult() != null) {
                csvWriter.writeParameterTuningResults(
                    getResultsPath("").resolve("grid_search_results.csv").toString(), 
                    convertGridSearchToMap(result.getGridSearchResult())
                );
            }
            
            // Save sensitivity analysis
            if (result.getSensitivityAnalysisResult() != null) {
                saveSensitivityResults(result.getSensitivityAnalysisResult());
            }
            
            // Save weight optimization
            if (result.getWeightOptimizationResult() != null) {
                csvWriter.writeParameterTuningResults(
                    getResultsPath("").resolve("weight_optimization.csv").toString(),
                    convertWeightOptimizationToMap(result.getWeightOptimizationResult())
                );
            }
            
            // Save summary
            saveTuningSummary(result);
            
            logger.info("Tuning results saved successfully");
            
        } catch (Exception e) {
            logger.error("Failed to save tuning results", e);
        }
    }
    
    /**
     * Converts GridSearchResult to a map format for CSV writing.
     * 
     * @param result Grid search result
     * @return Map containing CSV data
     */
    private Map<String, List<Map<String, Object>>> convertGridSearchToMap(GridSearchResult result) {
        Map<String, List<Map<String, Object>>> csvData = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        if (result.getPerformanceMap() != null) {
            for (Map.Entry<ParameterSet, PerformanceMetrics> entry : result.getPerformanceMap().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    Map<String, Object> row = new HashMap<>();
                    ParameterSet params = entry.getKey();
                    PerformanceMetrics metrics = entry.getValue();
                    
                    row.put("PopulationSize", params.getPopulationSize());
                    row.put("MaxIterations", params.getMaxIterations());
                    row.put("Alpha", params.getAlpha());
                    row.put("Beta", params.getBeta());
                    row.put("Gamma", params.getGamma());
                    row.put("WeightUtil", params.getWeightUtilization());
                    row.put("WeightPower", params.getWeightPower());
                    row.put("WeightSLA", params.getWeightSLA());
                    row.put("CpuUtil", metrics.getCpuUtilization());
                    row.put("RamUtil", metrics.getRamUtilization());
                    row.put("Power", metrics.getPowerConsumption());
                    row.put("SLA", metrics.getSlaViolations());
                    row.put("ExecTime", metrics.getExecutionTime());
                    row.put("OverallScore", metrics.getOverallScore());
                    
                    rows.add(row);
                }
            }
        }
        
        csvData.put("grid_search", rows);
        return csvData;
    }
    
    /**
     * Converts WeightOptimizationResult to a map format for CSV writing.
     * 
     * @param result Weight optimization result
     * @return Map containing CSV data
     */
    private Map<String, List<Map<String, Object>>> convertWeightOptimizationToMap(WeightOptimizationResult result) {
        Map<String, List<Map<String, Object>>> csvData = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        
        if (result.getWeightPerformanceMap() != null) {
            for (Map.Entry<WeightCombination, PerformanceMetrics> entry : 
                 result.getWeightPerformanceMap().entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    Map<String, Object> row = new HashMap<>();
                    WeightCombination weights = entry.getKey();
                    PerformanceMetrics metrics = entry.getValue();
                    
                    row.put("WeightUtil", weights.getUtilizationWeight());
                    row.put("WeightPower", weights.getPowerWeight());
                    row.put("WeightSLA", weights.getSlaWeight());
                    row.put("CpuUtil", metrics.getCpuUtilization());
                    row.put("RamUtil", metrics.getRamUtilization());
                    row.put("Power", metrics.getPowerConsumption());
                    row.put("SLA", metrics.getSlaViolations());
                    row.put("OverallScore", metrics.getOverallScore());
                    
                    rows.add(row);
                }
            }
        }
        
        csvData.put("weight_optimization", rows);
        return csvData;
    }
    
    /**
     * Saves sensitivity analysis results using CSVResultsWriter.
     * 
     * @param result Sensitivity analysis result
     */
    private void saveSensitivityResults(SensitivityAnalysisResult result) {
        if (result == null) {
            return;
        }
        
        for (String parameter : result.getAnalyzedParameters()) {
            List<ParameterImpact> impacts = result.getParameterAnalysis(parameter);
            if (impacts != null && !impacts.isEmpty()) {
                Map<String, List<Map<String, Object>>> csvData = new HashMap<>();
                List<Map<String, Object>> rows = new ArrayList<>();
                
                for (ParameterImpact impact : impacts) {
                    if (impact != null && impact.getMetrics() != null) {
                        Map<String, Object> row = new HashMap<>();
                        PerformanceMetrics metrics = impact.getMetrics();
                        
                        row.put("ParameterValue", impact.getValue());
                        row.put("CpuUtil", metrics.getCpuUtilization());
                        row.put("RamUtil", metrics.getRamUtilization());
                        row.put("Power", metrics.getPowerConsumption());
                        row.put("SLA", metrics.getSlaViolations());
                        row.put("ExecTime", metrics.getExecutionTime());
                        row.put("OverallScore", metrics.getOverallScore());
                        
                        rows.add(row);
                    }
                }
                
                csvData.put(parameter + "_sensitivity", rows);
                csvWriter.writeParameterTuningResults(
                    getResultsPath("").resolve(parameter + "_sensitivity.csv").toString(), 
                    csvData);
            }
        }
    }
    
    /**
     * Saves tuning summary using CSVResultsWriter.
     * 
     * @param result Complete tuning result
     */
    private void saveTuningSummary(TuningResult result) {
        if (result == null) {
            return;
        }
        
        Map<String, Object> summary = new HashMap<>();
        summary.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (result.getOptimalParameters() != null) {
            ParameterSet optimal = result.getOptimalParameters();
            summary.put("optimal_population_size", optimal.getPopulationSize());
            summary.put("optimal_max_iterations", optimal.getMaxIterations());
            summary.put("optimal_alpha", optimal.getAlpha());
            summary.put("optimal_beta", optimal.getBeta());
            summary.put("optimal_gamma", optimal.getGamma());
        }
        
        if (result.getWeightOptimizationResult() != null && 
            result.getWeightOptimizationResult().getOptimalWeights() != null) {
            WeightCombination weights = result.getWeightOptimizationResult().getOptimalWeights();
            summary.put("optimal_weight_utilization", weights.getUtilizationWeight());
            summary.put("optimal_weight_power", weights.getPowerWeight());
            summary.put("optimal_weight_sla", weights.getSlaWeight());
        }
        
        csvWriter.writeTuningSummary(
            getResultsPath("").resolve("tuning_summary.csv").toString(), 
            summary);
    }
    
    /**
     * Saves intermediate results during tuning using CSVResultsWriter.
     * 
     * @param parameters Current parameter set
     * @param result Evaluation result
     */
    private void saveIntermediateResult(ParameterSet parameters, ParameterEvaluationResult result) {
        if (parameters == null || result == null || result.getMetrics() == null) {
            return;
        }
        
        Map<String, Object> row = new HashMap<>();
        row.put("Timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
        row.put("PopSize", parameters.getPopulationSize());
        row.put("MaxIter", parameters.getMaxIterations());
        row.put("Alpha", parameters.getAlpha());
        row.put("Beta", parameters.getBeta());
        row.put("Gamma", parameters.getGamma());
        
        PerformanceMetrics metrics = result.getMetrics();
        row.put("CpuUtil", metrics.getCpuUtilization());
        row.put("RamUtil", metrics.getRamUtilization());
        row.put("Power", metrics.getPowerConsumption());
        row.put("SLA", metrics.getSlaViolations());
        row.put("ExecTime", metrics.getExecutionTime());
        
        csvWriter.appendIntermediateResult(
            getResultsPath("").resolve("intermediate_results.csv").toString(), 
            row);
    }
    
    /**
     * Logs top performing parameter sets.
     * 
     * @param performanceMap Map of parameters to performance
     * @param topN Number of top sets to log
     */
    private void logTopParameterSets(Map<ParameterSet, PerformanceMetrics> performanceMap, int topN) {
        if (performanceMap == null || performanceMap.isEmpty() || topN <= 0) {
            return;
        }
        
        logger.info("Top {} parameter sets:", topN);
        
        performanceMap.entrySet().stream()
            .filter(e -> e.getValue() != null)
            .sorted(Comparator.comparing(e -> e.getValue().getOverallScore()))
            .limit(topN)
            .forEach(entry -> {
                if (entry.getKey() != null) {
                    logger.info("  Parameters: {}, Score: {:.4f}", 
                        entry.getKey(), entry.getValue().getOverallScore());
                }
            });
    }
    
    /**
     * Logs parameter impact summary.
     * 
     * @param parameterName Name of parameter
     * @param impacts List of parameter impacts
     */
    private void logParameterImpact(String parameterName, List<ParameterImpact> impacts) {
        if (parameterName == null || impacts == null || impacts.isEmpty()) {
            return;
        }
        
        double minScore = impacts.stream()
            .filter(i -> i != null && i.getMetrics() != null)
            .mapToDouble(i -> i.getMetrics().getOverallScore())
            .min().orElse(0.0);
            
        double maxScore = impacts.stream()
            .filter(i -> i != null && i.getMetrics() != null)
            .mapToDouble(i -> i.getMetrics().getOverallScore())
            .max().orElse(0.0);
            
        double range = maxScore - minScore;
        double relativeImpact = (minScore > 0) ? (range / minScore * 100) : 0.0;
        
        logger.info("Parameter {} impact: {:.2f}% (range: {:.4f} - {:.4f})",
            parameterName, relativeImpact, minScore, maxScore);
    }
    
    /**
     * Gets the configured thread pool size from AlgorithmConstants or default.
     * 
     * @return Thread pool size
     */
    public static int getConfiguredThreadPoolSize() {
        try {
            // Try to get from configuration
            String configValue = System.getProperty("parameter.tuner.threads");
            if (configValue != null) {
                return Integer.parseInt(configValue);
            }
            
            // Use available processors with a reasonable limit
            return Math.min(DEFAULT_THREAD_POOL_SIZE, 
                          Runtime.getRuntime().availableProcessors());
        } catch (Exception e) {
            logger.warn("Failed to get configured thread pool size, using default", e);
            return DEFAULT_THREAD_POOL_SIZE;
        }
    }
    
    /**
     * Shuts down the parameter tuner and releases resources.
     */
    public void shutdown() {
        logger.info("Shutting down ParameterTuner");
        
        // Clear cache
        parameterCache.clear();
        
        // Shutdown executor
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    // Inner classes
    
    /**
     * Represents a set of algorithm parameters.
     */
    public static class ParameterSet {
        private int populationSize;
        private int maxIterations;
        private double alpha;
        private double beta;
        private double gamma;
        private double weightUtilization;
        private double weightPower;
        private double weightSLA;
        
        // Getters and setters with null checks
        public int getPopulationSize() { return populationSize; }
        public void setPopulationSize(int populationSize) { 
            if (populationSize <= 0) {
                throw new IllegalArgumentException("Population size must be positive");
            }
            this.populationSize = populationSize; 
        }
        
        public int getMaxIterations() { return maxIterations; }
        public void setMaxIterations(int maxIterations) { 
            if (maxIterations <= 0) {
                throw new IllegalArgumentException("Max iterations must be positive");
            }
            this.maxIterations = maxIterations; 
        }
        
        public double getAlpha() { return alpha; }
        public void setAlpha(double alpha) { 
            if (alpha < 0 || alpha > 1) {
                throw new IllegalArgumentException("Alpha must be in range [0,1]");
            }
            this.alpha = alpha; 
        }
        
        public double getBeta() { return beta; }
        public void setBeta(double beta) { 
            if (beta < 0 || beta > 1) {
                throw new IllegalArgumentException("Beta must be in range [0,1]");
            }
            this.beta = beta; 
        }
        
        public double getGamma() { return gamma; }
        public void setGamma(double gamma) { 
            if (gamma < 0 || gamma > 1) {
                throw new IllegalArgumentException("Gamma must be in range [0,1]");
            }
            this.gamma = gamma; 
        }
        
        public double getWeightUtilization() { return weightUtilization; }
        public void setWeightUtilization(double weightUtilization) { 
            if (weightUtilization < 0) {
                throw new IllegalArgumentException("Weight must be non-negative");
            }
            this.weightUtilization = weightUtilization; 
        }
        
        public double getWeightPower() { return weightPower; }
        public void setWeightPower(double weightPower) { 
            if (weightPower < 0) {
                throw new IllegalArgumentException("Weight must be non-negative");
            }
            this.weightPower = weightPower; 
        }
        
        public double getWeightSLA() { return weightSLA; }
        public void setWeightSLA(double weightSLA) { 
            if (weightSLA < 0) {
                throw new IllegalArgumentException("Weight must be non-negative");
            }
            this.weightSLA = weightSLA; 
        }
        
        /**
         * Creates a copy of this parameter set.
         * 
         * @return Copy of parameter set
         */
        public ParameterSet copy() {
            ParameterSet copy = new ParameterSet();
            copy.populationSize = this.populationSize;
            copy.maxIterations = this.maxIterations;
            copy.alpha = this.alpha;
            copy.beta = this.beta;
            copy.gamma = this.gamma;
            copy.weightUtilization = this.weightUtilization;
            copy.weightPower = this.weightPower;
            copy.weightSLA = this.weightSLA;
            return copy;
        }
        
        /**
         * Sets a parameter by name.
         * 
         * @param name Parameter name
         * @param value Parameter value
         */
        public void setParameter(String name, Object value) {
            if (name == null || value == null) {
                throw new IllegalArgumentException("Parameter name and value cannot be null");
            }
            
            switch (name) {
                case "populationSize" -> setPopulationSize((int) value);
                case "maxIterations" -> setMaxIterations((int) value);
                case "alpha" -> setAlpha((double) value);
                case "beta" -> setBeta((double) value);
                case "gamma" -> setGamma((double) value);
                default -> throw new IllegalArgumentException("Unknown parameter: " + name);
            }
        }
        
        /**
         * Converts parameter set to map.
         * 
         * @return Map representation
         */
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<>();
            map.put("populationSize", populationSize);
            map.put("maxIterations", maxIterations);
            map.put("alpha", alpha);
            map.put("beta", beta);
            map.put("gamma", gamma);
            map.put("weightUtilization", weightUtilization);
            map.put("weightPower", weightPower);
            map.put("weightSLA", weightSLA);
            return map;
        }
        
        @Override
        public String toString() {
            return String.format("ParameterSet[pop=%d,iter=%d,α=%.2f,β=%.2f,γ=%.2f]",
                populationSize, maxIterations, alpha, beta, gamma);
        }
    }
    
    /**
     * Represents performance metrics for evaluation.
     */
    public static class PerformanceMetrics {
        private double cpuUtilization;
        private double ramUtilization;
        private double powerConsumption;
        private double slaViolations;
        private double executionTime;
        
        // Getters and setters with validation
        public double getCpuUtilization() { return cpuUtilization; }
        public void setCpuUtilization(double cpuUtilization) { 
            if (cpuUtilization < 0 || cpuUtilization > 1) {
                logger.warn("CPU utilization out of range [0,1]: {}", cpuUtilization);
            }
            this.cpuUtilization = Math.max(0, Math.min(1, cpuUtilization)); 
        }
        
        public double getRamUtilization() { return ramUtilization; }
        public void setRamUtilization(double ramUtilization) { 
            if (ramUtilization < 0 || ramUtilization > 1) {
                logger.warn("RAM utilization out of range [0,1]: {}", ramUtilization);
            }
            this.ramUtilization = Math.max(0, Math.min(1, ramUtilization)); 
        }
        
        public double getPowerConsumption() { return powerConsumption; }
        public void setPowerConsumption(double powerConsumption) { 
            if (powerConsumption < 0) {
                logger.warn("Power consumption cannot be negative: {}", powerConsumption);
            }
            this.powerConsumption = Math.max(0, powerConsumption); 
        }
        
        public double getSlaViolations() { return slaViolations; }
        public void setSlaViolations(double slaViolations) { 
            if (slaViolations < 0) {
                logger.warn("SLA violations cannot be negative: {}", slaViolations);
            }
            this.slaViolations = Math.max(0, slaViolations); 
        }
        
        public double getExecutionTime() { return executionTime; }
        public void setExecutionTime(double executionTime) { 
            if (executionTime < 0) {
                logger.warn("Execution time cannot be negative: {}", executionTime);
            }
            this.executionTime = Math.max(0, executionTime); 
        }
        
        /**
         * Calculates overall performance score (lower is better).
         * Maximizes utilization while minimizing power, SLA violations, and execution time.
         * 
         * @return Overall score
         */
        public double getOverallScore() {
            // Penalties for poor performance (all normalized to [0,1])
            double cpuPenalty = 1.0 - cpuUtilization; // Penalty for low utilization
            double ramPenalty = 1.0 - ramUtilization; // Penalty for low utilization
            double powerPenalty = Math.min(1.0, powerConsumption / 2000.0); // Normalize power
            double slaPenalty = Math.min(1.0, slaViolations / 10.0); // Normalize SLA
            double timePenalty = Math.min(1.0, executionTime / 100.0); // Normalize time
            
            // Weighted combination (lower is better)
            return 0.3 * cpuPenalty + 
                   0.2 * ramPenalty + 
                   0.2 * powerPenalty + 
                   0.2 * slaPenalty + 
                   0.1 * timePenalty;
        }
    }
    
    /**
     * Result of parameter evaluation.
     */
    private static class ParameterEvaluationResult {
        private final ParameterSet parameters;
        private final PerformanceMetrics metrics;
        private final Map<String, ConfidenceInterval> confidenceIntervals;
        private final List<ExperimentResult> rawResults;
        
        public ParameterEvaluationResult(ParameterSet parameters, 
                                       PerformanceMetrics metrics,
                                       Map<String, ConfidenceInterval> confidenceIntervals,
                                       List<ExperimentResult> rawResults) {
            this.parameters = Objects.requireNonNull(parameters, "Parameters cannot be null");
            this.metrics = Objects.requireNonNull(metrics, "Metrics cannot be null");
            this.confidenceIntervals = Objects.requireNonNull(confidenceIntervals, 
                "Confidence intervals cannot be null");
            this.rawResults = Objects.requireNonNull(rawResults, "Raw results cannot be null");
        }
        
        public ParameterSet getParameters() { return parameters; }
        public PerformanceMetrics getMetrics() { return metrics; }
        public Map<String, ConfidenceInterval> getConfidenceIntervals() { 
            return confidenceIntervals; 
        }
        public List<ExperimentResult> getRawResults() { return rawResults; }
    }
    
    /**
     * Complete tuning result.
     */
    public static class TuningResult {
        private GridSearchResult gridSearchResult;
        private SensitivityAnalysisResult sensitivityAnalysisResult;
        private WeightOptimizationResult weightOptimizationResult;
        private ParameterSet optimalParameters;
        
        // Getters and setters
        public GridSearchResult getGridSearchResult() { return gridSearchResult; }
        public void setGridSearchResult(GridSearchResult gridSearchResult) { 
            this.gridSearchResult = gridSearchResult; 
        }
        
        public SensitivityAnalysisResult getSensitivityAnalysisResult() { 
            return sensitivityAnalysisResult; 
        }
        public void setSensitivityAnalysisResult(SensitivityAnalysisResult sensitivityAnalysisResult) { 
            this.sensitivityAnalysisResult = sensitivityAnalysisResult; 
        }
        
        public WeightOptimizationResult getWeightOptimizationResult() { 
            return weightOptimizationResult; 
        }
        public void setWeightOptimizationResult(WeightOptimizationResult weightOptimizationResult) { 
            this.weightOptimizationResult = weightOptimizationResult; 
        }
        
        public ParameterSet getOptimalParameters() { return optimalParameters; }
        public void setOptimalParameters(ParameterSet optimalParameters) { 
            this.optimalParameters = optimalParameters; 
        }
    }
    
    /**
     * Grid search result.
     */
    private static class GridSearchResult {
        private ParameterSet bestParameters;
        private Map<ParameterSet, PerformanceMetrics> performanceMap;
        private LocalDateTime searchCompletionTime;
        
        public ParameterSet getBestParameters() { return bestParameters; }
        public void setBestParameters(ParameterSet bestParameters) { 
            this.bestParameters = bestParameters; 
        }
        
        public Map<ParameterSet, PerformanceMetrics> getPerformanceMap() { 
            return performanceMap; 
        }
        public void setPerformanceMap(Map<ParameterSet, PerformanceMetrics> performanceMap) { 
            this.performanceMap = performanceMap; 
        }
        
        public LocalDateTime getSearchCompletionTime() { return searchCompletionTime; }
        public void setSearchCompletionTime(LocalDateTime searchCompletionTime) { 
            this.searchCompletionTime = searchCompletionTime; 
        }
    }
    
    /**
     * Sensitivity analysis result.
     */
    private static class SensitivityAnalysisResult {
        private ParameterSet baseParameters;
        private final Map<String, List<ParameterImpact>> parameterAnalysis = new HashMap<>();
        
        public ParameterSet getBaseParameters() { return baseParameters; }
        public void setBaseParameters(ParameterSet baseParameters) { 
            this.baseParameters = baseParameters; 
        }
        
        public void addParameterAnalysis(String parameter, List<ParameterImpact> impacts) {
            if (parameter != null && impacts != null) {
                parameterAnalysis.put(parameter, impacts);
            }
        }
        
        public List<ParameterImpact> getParameterAnalysis(String parameter) {
            return parameterAnalysis.get(parameter);
        }
        
        public Set<String> getAnalyzedParameters() {
            return parameterAnalysis.keySet();
        }
    }
    
    /**
     * Weight optimization result.
     */
    private static class WeightOptimizationResult {
        private WeightCombination optimalWeights;
        private Map<WeightCombination, PerformanceMetrics> weightPerformanceMap;
        
        public WeightCombination getOptimalWeights() { return optimalWeights; }
        public void setOptimalWeights(WeightCombination optimalWeights) { 
            this.optimalWeights = optimalWeights; 
        }
        
        public Map<WeightCombination, PerformanceMetrics> getWeightPerformanceMap() { 
            return weightPerformanceMap; 
        }
        public void setWeightPerformanceMap(Map<WeightCombination, PerformanceMetrics> weightPerformanceMap) { 
            this.weightPerformanceMap = weightPerformanceMap; 
        }
    }
    
    /**
     * Represents impact of a parameter value on performance.
     */
    private static class ParameterImpact {
        private final double value;
        private final PerformanceMetrics metrics;
        
        public ParameterImpact(double value, PerformanceMetrics metrics) {
            this.value = value;
            this.metrics = Objects.requireNonNull(metrics, "Metrics cannot be null");
        }
        
        public double getValue() { return value; }
        public PerformanceMetrics getMetrics() { return metrics; }
    }
    
    /**
     * Represents a combination of fitness function weights.
     */
    private static class WeightCombination {
        private final double utilizationWeight;
        private final double powerWeight;
        private final double slaWeight;
        
        public WeightCombination(double utilizationWeight, double powerWeight, double slaWeight) {
            if (utilizationWeight < 0 || powerWeight < 0 || slaWeight < 0) {
                throw new IllegalArgumentException("Weights must be non-negative");
            }
            
            double total = utilizationWeight + powerWeight + slaWeight;
            if (Math.abs(total - 1.0) > 0.01) {
                logger.warn("Weights do not sum to 1.0: {}", total);
            }
            
            this.utilizationWeight = utilizationWeight;
            this.powerWeight = powerWeight;
            this.slaWeight = slaWeight;
        }
        
        public double getUtilizationWeight() { return utilizationWeight; }
        public double getPowerWeight() { return powerWeight; }
        public double getSlaWeight() { return slaWeight; }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WeightCombination that = (WeightCombination) o;
            return Double.compare(that.utilizationWeight, utilizationWeight) == 0 &&
                   Double.compare(that.powerWeight, powerWeight) == 0 &&
                   Double.compare(that.slaWeight, slaWeight) == 0;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(utilizationWeight, powerWeight, slaWeight);
        }
    }
}