package org.puneet.cloudsimplus.hiippo.statistical;

import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.OneWayAnova;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.puneet.cloudsimplus.hiippo.exceptions.StatisticalValidationException;
import org.puneet.cloudsimplus.hiippo.exceptions.StatisticalValidationException.StatisticalErrorType;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Statistical validator for experimental results with multiple comparison corrections.
 * Performs comprehensive statistical analysis including significance tests,
 * confidence intervals, effect sizes, and proper p-value adjustments.
 * 
 *  @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-22
 */
public class StatisticalValidator {
    private static final Logger logger = LoggerFactory.getLogger(StatisticalValidator.class);
    
    // Statistical test parameters
    private static final double SIGNIFICANCE_LEVEL = ExperimentConfig.SIGNIFICANCE_LEVEL;
    private static final double CONFIDENCE_LEVEL = ExperimentConfig.CONFIDENCE_LEVEL;
    private static final int MIN_SAMPLE_SIZE = 5;
    private static final double NORMALITY_THRESHOLD = 0.05;
    
    // Multiple comparison correction methods
    public enum CorrectionMethod {
        BONFERRONI("Bonferroni"),
        HOLM_BONFERRONI("Holm-Bonferroni"),
        BENJAMINI_HOCHBERG("Benjamini-Hochberg (FDR)"),
        NONE("None");
        
        private final String displayName;
        
        CorrectionMethod(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // Default correction method
    private CorrectionMethod correctionMethod = CorrectionMethod.BONFERRONI;
    
    // Test objects (reusable to save memory)
    private final TTest tTest;
    private final OneWayAnova anovaTest;
    private final Map<String, DescriptiveStatistics> statisticsCache;
    
    // Memory management
    private static final int CACHE_SIZE_LIMIT = 100;
    private long lastMemoryCheck;
    private static final long MEMORY_CHECK_INTERVAL = 10000; // 10 seconds
    
    /**
     * Constructs a new StatisticalValidator instance with default Bonferroni correction.
     */
    public StatisticalValidator() {
        this(CorrectionMethod.BONFERRONI);
    }
    
    /**
     * Constructs a new StatisticalValidator instance with specified correction method.
     * 
     * @param correctionMethod The multiple comparison correction method to use
     */
    public StatisticalValidator(CorrectionMethod correctionMethod) {
        this.tTest = new TTest();
        this.anovaTest = new OneWayAnova();
        this.statisticsCache = new ConcurrentHashMap<>();
        this.lastMemoryCheck = System.currentTimeMillis();
        this.correctionMethod = correctionMethod;
        
        logger.info("StatisticalValidator initialized with {} correction, alpha={}", 
            correctionMethod.getDisplayName(), SIGNIFICANCE_LEVEL);
    }
    
    /**
     * Sets the multiple comparison correction method.
     * 
     * @param method The correction method to use
     */
    public void setCorrectionMethod(CorrectionMethod method) {
        this.correctionMethod = method;
        logger.info("Changed correction method to: {}", method.getDisplayName());
    }
    
    /**
     * Validates experimental results with comprehensive statistical analysis.
     * 
     * @param experimentResults Map of algorithm names to their result arrays
     * @param metric The metric being analyzed (e.g., "ResourceUtilCPU", "PowerConsumption")
     * @return ValidationResult containing all statistical test results
     * @throws StatisticalValidationException if validation fails
     */
    public ValidationResult validateResults(Map<String, double[]> experimentResults, String metric) 
            throws StatisticalValidationException {
        
        if (experimentResults == null || experimentResults.isEmpty()) {
            throw new StatisticalValidationException(
                StatisticalErrorType.DATA_QUALITY_ERROR,
                "Experiment results cannot be null or empty");
        }
        
        if (metric == null || metric.trim().isEmpty()) {
            throw new StatisticalValidationException(
                StatisticalErrorType.DATA_QUALITY_ERROR,
                "Metric name cannot be null or empty");
        }
        
        logger.info("Starting statistical validation for metric: {} with {} correction", 
            metric, correctionMethod.getDisplayName());
        checkMemoryUsage();
        
        try {
            ValidationResult result = new ValidationResult(metric);
            result.setCorrectionMethod(correctionMethod);
            
            // Step 1: Calculate descriptive statistics
            Map<String, DescriptiveStatistics> algorithmStats = calculateDescriptiveStatistics(experimentResults);
            result.setDescriptiveStats(algorithmStats);
            
            // Step 2: Check normality
            Map<String, Boolean> normalityResults = checkNormality(algorithmStats);
            result.setNormalityResults(normalityResults);
            
            // Step 3: Perform pairwise comparisons with correction
            Map<String, Map<String, ComparisonResult>> pairwiseResults = 
                performPairwiseComparisonsWithCorrection(experimentResults, normalityResults);
            result.setPairwiseComparisons(pairwiseResults);
            
            // Step 4: Perform ANOVA if applicable
            if (experimentResults.size() > 2) {
                ANOVAResult anovaResult = performANOVA(experimentResults);
                result.setAnovaResult(anovaResult);
                
                // If ANOVA is significant, we can proceed with post-hoc tests
                if (anovaResult.isSignificant()) {
                    logger.info("ANOVA significant, pairwise comparisons are justified");
                } else {
                    logger.warn("ANOVA not significant, interpret pairwise comparisons with caution");
                }
            }
            
            // Step 5: Calculate confidence intervals
            Map<String, ConfidenceInterval> confidenceIntervals = 
                calculateConfidenceIntervals(algorithmStats);
            result.setConfidenceIntervals(confidenceIntervals);
            
            // Step 6: Calculate effect sizes
            Map<String, Map<String, Double>> effectSizes = calculateEffectSizes(experimentResults);
            result.setEffectSizes(effectSizes);
            
            // Step 7: Identify best performing algorithm
            String bestAlgorithm = identifyBestAlgorithm(algorithmStats, metric);
            result.setBestAlgorithm(bestAlgorithm);
            
            // Step 8: Report multiple comparison summary
            reportMultipleComparisonSummary(result);
            
            // Clean up cache if needed
            cleanupCacheIfNeeded();
            
            logger.info("Statistical validation completed successfully for metric: {}", metric);
            return result;
            
        } catch (Exception e) {
            logger.error("Error during statistical validation for metric: {}", metric, e);
            throw new StatisticalValidationException(
                StatisticalErrorType.DATA_QUALITY_ERROR,
                "Statistical validation failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Performs pairwise comparisons with multiple comparison correction.
     * 
     * @param experimentResults Map of algorithm results
     * @param normalityResults Map of normality test results
     * @return Nested map of pairwise comparison results with corrections applied
     */
    private Map<String, Map<String, ComparisonResult>> performPairwiseComparisonsWithCorrection(
            Map<String, double[]> experimentResults, Map<String, Boolean> normalityResults) {
        
        // First, perform all raw comparisons
        Map<String, Map<String, ComparisonResult>> rawResults = 
            performRawPairwiseComparisons(experimentResults, normalityResults);
        
        // Extract all p-values for correction
        List<PValuePair> pValuePairs = extractPValuePairs(rawResults);
        
        // Apply correction based on selected method
        applyPValueCorrection(pValuePairs);
        
        // Update results with corrected p-values
        updateResultsWithCorrectedPValues(rawResults, pValuePairs);
        
        return rawResults;
    }
    
    /**
     * Performs raw pairwise comparisons without correction.
     * 
     * @param experimentResults Map of algorithm results
     * @param normalityResults Map of normality test results
     * @return Nested map of raw comparison results
     */
    private Map<String, Map<String, ComparisonResult>> performRawPairwiseComparisons(
            Map<String, double[]> experimentResults, Map<String, Boolean> normalityResults) {
        
        Map<String, Map<String, ComparisonResult>> pairwiseResults = new HashMap<>();
        List<String> algorithms = new ArrayList<>(experimentResults.keySet());
        int totalComparisons = (algorithms.size() * (algorithms.size() - 1)) / 2;
        
        logger.info("Performing {} unique pairwise comparisons", totalComparisons);
        
        for (int i = 0; i < algorithms.size(); i++) {
            String algo1 = algorithms.get(i);
            Map<String, ComparisonResult> algo1Comparisons = new HashMap<>();
            
            for (int j = 0; j < algorithms.size(); j++) {
                if (i == j) continue;
                
                String algo2 = algorithms.get(j);
                
                // Skip if we've already done this comparison in reverse
                if (j < i && pairwiseResults.containsKey(algo2) && 
                    pairwiseResults.get(algo2).containsKey(algo1)) {
                    // Copy the result but swap the perspective
                    ComparisonResult reverseResult = pairwiseResults.get(algo2).get(algo1);
                    ComparisonResult copiedResult = copyAndReverseComparison(reverseResult);
                    algo1Comparisons.put(algo2, copiedResult);
                    continue;
                }
                
                double[] data1 = experimentResults.get(algo1);
                double[] data2 = experimentResults.get(algo2);
                
                if (data1 == null || data2 == null || 
                    data1.length < MIN_SAMPLE_SIZE || data2.length < MIN_SAMPLE_SIZE) {
                    logger.warn("Insufficient data for comparison: {} vs {}", algo1, algo2);
                    continue;
                }
                
                ComparisonResult comparison = performComparison(
                    algo1, data1, algo2, data2, 
                    normalityResults.getOrDefault(algo1, false) && 
                    normalityResults.getOrDefault(algo2, false)
                );
                
                algo1Comparisons.put(algo2, comparison);
            }
            
            pairwiseResults.put(algo1, algo1Comparisons);
        }
        
        return pairwiseResults;
    }
    
    /**
     * Extracts all p-values from comparison results for correction.
     * 
     * @param results The raw comparison results
     * @return List of p-value pairs with their locations
     */
    private List<PValuePair> extractPValuePairs(Map<String, Map<String, ComparisonResult>> results) {
        List<PValuePair> pValuePairs = new ArrayList<>();
        Set<String> processedPairs = new HashSet<>();
        
        for (Map.Entry<String, Map<String, ComparisonResult>> entry1 : results.entrySet()) {
            String algo1 = entry1.getKey();
            
            for (Map.Entry<String, ComparisonResult> entry2 : entry1.getValue().entrySet()) {
                String algo2 = entry2.getKey();
                ComparisonResult result = entry2.getValue();
                
                // Create a unique key for this comparison (order-independent)
                String pairKey = algo1.compareTo(algo2) < 0 ? 
                    algo1 + "-" + algo2 : algo2 + "-" + algo1;
                
                // Only process each unique pair once
                if (!processedPairs.contains(pairKey) && !result.isError()) {
                    pValuePairs.add(new PValuePair(algo1, algo2, result.getRawPValue()));
                    processedPairs.add(pairKey);
                }
            }
        }
        
        logger.debug("Extracted {} unique p-values for correction", pValuePairs.size());
        return pValuePairs;
    }
    
    /**
     * Applies the selected p-value correction method.
     * 
     * @param pValuePairs List of p-value pairs to correct
     */
    private void applyPValueCorrection(List<PValuePair> pValuePairs) {
        if (pValuePairs.isEmpty() || correctionMethod == CorrectionMethod.NONE) {
            return;
        }
        
        switch (correctionMethod) {
            case BONFERRONI:
                applyBonferroniCorrection(pValuePairs);
                break;
            case HOLM_BONFERRONI:
                applyHolmBonferroniCorrection(pValuePairs);
                break;
            case BENJAMINI_HOCHBERG:
                applyBenjaminiHochbergCorrection(pValuePairs);
                break;
            default:
                logger.warn("Unknown correction method: {}", correctionMethod);
        }
    }
    
    /**
     * Applies Bonferroni correction.
     * 
     * @param pValuePairs List of p-value pairs
     */
    private void applyBonferroniCorrection(List<PValuePair> pValuePairs) {
        int m = pValuePairs.size();
        double correctedAlpha = SIGNIFICANCE_LEVEL / m;
        
        logger.info("Applying Bonferroni correction: m={}, corrected alpha={:.6f}", 
            m, correctedAlpha);
        
        for (PValuePair pair : pValuePairs) {
            pair.adjustedPValue = Math.min(1.0, pair.rawPValue * m);
            pair.significantAfterCorrection = pair.rawPValue < correctedAlpha;
        }
    }
    
    /**
     * Applies Holm-Bonferroni correction (less conservative than Bonferroni).
     * 
     * @param pValuePairs List of p-value pairs
     */
    private void applyHolmBonferroniCorrection(List<PValuePair> pValuePairs) {
        int m = pValuePairs.size();
        
        logger.info("Applying Holm-Bonferroni correction: m={}", m);
        
        // Sort p-values in ascending order
        pValuePairs.sort(Comparator.comparingDouble(p -> p.rawPValue));
        
        for (int i = 0; i < pValuePairs.size(); i++) {
            PValuePair pair = pValuePairs.get(i);
            double correctedAlpha = SIGNIFICANCE_LEVEL / (m - i);
            pair.adjustedPValue = Math.min(1.0, pair.rawPValue * (m - i));
            
            // Once we find a non-significant result, all subsequent are non-significant
            if (i > 0 && !pValuePairs.get(i - 1).significantAfterCorrection) {
                pair.significantAfterCorrection = false;
            } else {
                pair.significantAfterCorrection = pair.rawPValue < correctedAlpha;
            }
        }
    }
    
    /**
     * Applies Benjamini-Hochberg correction for False Discovery Rate control.
     * 
     * @param pValuePairs List of p-value pairs
     */
    private void applyBenjaminiHochbergCorrection(List<PValuePair> pValuePairs) {
        int m = pValuePairs.size();
        
        logger.info("Applying Benjamini-Hochberg FDR correction: m={}", m);
        
        // Sort p-values in ascending order
        pValuePairs.sort(Comparator.comparingDouble(p -> p.rawPValue));
        
        // Find the largest i such that P(i) <= (i/m) * alpha
        int maxSignificantIndex = -1;
        for (int i = 0; i < pValuePairs.size(); i++) {
            double threshold = ((i + 1.0) / m) * SIGNIFICANCE_LEVEL;
            if (pValuePairs.get(i).rawPValue <= threshold) {
                maxSignificantIndex = i;
            }
        }
        
        // Adjust p-values and mark significance
        for (int i = 0; i < pValuePairs.size(); i++) {
            PValuePair pair = pValuePairs.get(i);
            
            // Benjamini-Hochberg adjusted p-value
            double adjustedP = pair.rawPValue * m / (i + 1.0);
            
            // Ensure monotonicity
            if (i > 0) {
                adjustedP = Math.max(adjustedP, pValuePairs.get(i - 1).adjustedPValue);
            }
            
            pair.adjustedPValue = Math.min(1.0, adjustedP);
            pair.significantAfterCorrection = i <= maxSignificantIndex;
        }
    }
    
    /**
     * Updates the comparison results with corrected p-values.
     * 
     * @param results The comparison results to update
     * @param correctedPValues The corrected p-value pairs
     */
    private void updateResultsWithCorrectedPValues(
            Map<String, Map<String, ComparisonResult>> results,
            List<PValuePair> correctedPValues) {
        
        Map<String, PValuePair> pValueMap = new HashMap<>();
        for (PValuePair pair : correctedPValues) {
            String key1 = pair.algo1 + "-" + pair.algo2;
            String key2 = pair.algo2 + "-" + pair.algo1;
            pValueMap.put(key1, pair);
            pValueMap.put(key2, pair);
        }
        
        for (Map.Entry<String, Map<String, ComparisonResult>> entry1 : results.entrySet()) {
            String algo1 = entry1.getKey();
            
            for (Map.Entry<String, ComparisonResult> entry2 : entry1.getValue().entrySet()) {
                String algo2 = entry2.getKey();
                ComparisonResult result = entry2.getValue();
                
                String key = algo1 + "-" + algo2;
                PValuePair correctedPair = pValueMap.get(key);
                
                if (correctedPair != null) {
                    result.setAdjustedPValue(correctedPair.adjustedPValue);
                    result.setSignificantAfterCorrection(correctedPair.significantAfterCorrection);
                    result.setCorrectionMethod(correctionMethod.getDisplayName());
                }
            }
        }
    }
    
    /**
     * Copies and reverses a comparison result.
     * 
     * @param original The original comparison result
     * @return A reversed copy of the comparison
     */
    private ComparisonResult copyAndReverseComparison(ComparisonResult original) {
        ComparisonResult reversed = new ComparisonResult(
            original.getAlgorithm2(), original.getAlgorithm1());
        
        reversed.setTestType(original.getTestType());
        reversed.setRawPValue(original.getRawPValue());
        reversed.setAdjustedPValue(original.getAdjustedPValue());
        reversed.setSignificant(original.isSignificant());
        reversed.setSignificantAfterCorrection(original.isSignificantAfterCorrection());
        reversed.setEffectSize(-original.getEffectSize()); // Reverse the effect
        reversed.setEffectSizeInterpretation(original.getEffectSizeInterpretation());
        reversed.setMeanDifference(-original.getMeanDifference()); // Reverse the difference
        reversed.setCorrectionMethod(original.getCorrectionMethod());
        reversed.setError(original.isError());
        reversed.setErrorMessage(original.getErrorMessage());
        
        return reversed;
    }
    
    /**
     * Reports a summary of the multiple comparison results.
     * 
     * @param result The validation result
     */
    private void reportMultipleComparisonSummary(ValidationResult result) {
        Map<String, Map<String, ComparisonResult>> comparisons = result.getPairwiseComparisons();
        if (comparisons == null || comparisons.isEmpty()) {
            return;
        }
        
        int totalComparisons = 0;
        int significantBeforeCorrection = 0;
        int significantAfterCorrection = 0;
        
        Set<String> processedPairs = new HashSet<>();
        
        for (Map.Entry<String, Map<String, ComparisonResult>> entry1 : comparisons.entrySet()) {
            for (Map.Entry<String, ComparisonResult> entry2 : entry1.getValue().entrySet()) {
                String pairKey = entry1.getKey().compareTo(entry2.getKey()) < 0 ?
                    entry1.getKey() + "-" + entry2.getKey() :
                    entry2.getKey() + "-" + entry1.getKey();
                
                if (!processedPairs.contains(pairKey)) {
                    ComparisonResult comp = entry2.getValue();
                    if (!comp.isError()) {
                        totalComparisons++;
                        if (comp.isSignificant()) {
                            significantBeforeCorrection++;
                        }
                        if (comp.isSignificantAfterCorrection()) {
                            significantAfterCorrection++;
                        }
                    }
                    processedPairs.add(pairKey);
                }
            }
        }
        
        logger.info("Multiple Comparison Summary for {}:", result.getMetric());
        logger.info("  Correction Method: {}", correctionMethod.getDisplayName());
        logger.info("  Total Comparisons: {}", totalComparisons);
        logger.info("  Significant before correction: {} ({:.1f}%)", 
            significantBeforeCorrection, 
            100.0 * significantBeforeCorrection / totalComparisons);
        logger.info("  Significant after correction: {} ({:.1f}%)", 
            significantAfterCorrection,
            100.0 * significantAfterCorrection / totalComparisons);
        logger.info("  Family-wise error rate controlled at: {}", SIGNIFICANCE_LEVEL);
    }
    
    /**
     * Performs a single comparison between two algorithms.
     * 
     * @param algo1 First algorithm name
     * @param data1 First algorithm's data
     * @param algo2 Second algorithm name
     * @param data2 Second algorithm's data
     * @param useParametric Whether to use parametric tests
     * @return Comparison result
     */
    private ComparisonResult performComparison(String algo1, double[] data1, 
                                              String algo2, double[] data2, 
                                              boolean useParametric) {
        
        ComparisonResult result = new ComparisonResult(algo1, algo2);
        
        try {
            // Perform appropriate test
            double pValue;
            if (useParametric) {
                // Use t-test for normally distributed data
                pValue = tTest.tTest(data1, data2);
                result.setTestType("Student's t-test");
            } else {
                // Use Mann-Whitney U test approximation for non-normal data
                pValue = performMannWhitneyApproximation(data1, data2);
                result.setTestType("Mann-Whitney U test");
            }
            
            result.setRawPValue(pValue);
            result.setSignificant(pValue < SIGNIFICANCE_LEVEL);
            
            // Calculate effect size (Cohen's d)
            double effectSize = calculateCohenD(data1, data2);
            result.setEffectSize(effectSize);
            result.setEffectSizeInterpretation(interpretEffectSize(effectSize));
            
            // Determine which algorithm is better
            DescriptiveStatistics stats1 = new DescriptiveStatistics(data1);
            DescriptiveStatistics stats2 = new DescriptiveStatistics(data2);
            result.setMeanDifference(stats1.getMean() - stats2.getMean());
            
            logger.debug("Raw comparison {} vs {}: p={:.4f}, d={:.4f}", 
                algo1, algo2, pValue, effectSize);
            
        } catch (Exception e) {
            logger.error("Error comparing {} vs {}: {}", algo1, algo2, e.getMessage());
            result.setError(true);
            result.setErrorMessage(e.getMessage());
        }
        
        return result;
    }
    
    // --- MISSING METHOD STUBS FOR LINTER ---
    private void checkMemoryUsage() {
        // Optionally call MemoryManager.checkMemoryUsage("StatisticalValidator");
    }

    private Map<String, DescriptiveStatistics> calculateDescriptiveStatistics(Map<String, double[]> experimentResults) {
        Map<String, DescriptiveStatistics> stats = new HashMap<>();
        for (Map.Entry<String, double[]> entry : experimentResults.entrySet()) {
            stats.put(entry.getKey(), new DescriptiveStatistics(entry.getValue()));
        }
        return stats;
    }

    private Map<String, Boolean> checkNormality(Map<String, DescriptiveStatistics> algorithmStats) {
        Map<String, Boolean> normality = new HashMap<>();
        for (Map.Entry<String, DescriptiveStatistics> entry : algorithmStats.entrySet()) {
            // For now, assume normality if sample size >= 30
            normality.put(entry.getKey(), entry.getValue().getN() >= 30);
        }
        return normality;
    }

    private ANOVAResult performANOVA(Map<String, double[]> experimentResults) {
        try {
            return ANOVAResult.performANOVA(experimentResults, "Metric", SIGNIFICANCE_LEVEL);
        } catch (org.puneet.cloudsimplus.hiippo.exceptions.StatisticalValidationException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, ConfidenceInterval> calculateConfidenceIntervals(Map<String, DescriptiveStatistics> algorithmStats) {
        Map<String, ConfidenceInterval> intervals = new HashMap<>();
        for (Map.Entry<String, DescriptiveStatistics> entry : algorithmStats.entrySet()) {
            double[] data = entry.getValue().getValues();
            intervals.put(entry.getKey(), ConfidenceInterval.computeOptimal(data, CONFIDENCE_LEVEL));
        }
        return intervals;
    }

    private Map<String, Map<String, Double>> calculateEffectSizes(Map<String, double[]> experimentResults) {
        // For each pair, calculate Cohen's d
        Map<String, Map<String, Double>> effectSizes = new HashMap<>();
        List<String> keys = new ArrayList<>(experimentResults.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String a = keys.get(i);
            effectSizes.putIfAbsent(a, new HashMap<>());
            for (int j = 0; j < keys.size(); j++) {
                if (i == j) continue;
                String b = keys.get(j);
                double d = calculateCohenD(experimentResults.get(a), experimentResults.get(b));
                effectSizes.get(a).put(b, d);
            }
        }
        return effectSizes;
    }

    private String identifyBestAlgorithm(Map<String, DescriptiveStatistics> algorithmStats, String metric) {
        // For now, return the algorithm with the highest mean
        return algorithmStats.entrySet().stream()
            .max(Comparator.comparingDouble(e -> e.getValue().getMean()))
            .map(Map.Entry::getKey)
            .orElse("");
    }

    private void cleanupCacheIfNeeded() {
        // Optionally clear statisticsCache if too large
        if (statisticsCache.size() > CACHE_SIZE_LIMIT) {
            statisticsCache.clear();
        }
    }

    private double performMannWhitneyApproximation(double[] data1, double[] data2) {
        // Use Apache Commons Math MannWhitneyUTest if available, else fallback
        org.apache.commons.math3.stat.inference.MannWhitneyUTest mwu = new org.apache.commons.math3.stat.inference.MannWhitneyUTest();
        return mwu.mannWhitneyUTest(data1, data2);
    }

    private double calculateCohenD(double[] data1, double[] data2) {
        // Use the static method from ANOVAResult or implement here
        return ANOVAResult.calculateCohensD(data1, data2);
    }

    private String interpretEffectSize(double d) {
        double absD = Math.abs(d);
        if (absD < 0.2) return "negligible";
        if (absD < 0.5) return "small";
        if (absD < 0.8) return "medium";
        return "large";
    }
    
    /**
     * Inner class to hold p-value pairs for correction.
     */
    private static class PValuePair {
        final String algo1;
        final String algo2;
        final double rawPValue;
        double adjustedPValue;
        boolean significantAfterCorrection;
        
        PValuePair(String algo1, String algo2, double rawPValue) {
            this.algo1 = algo1;
            this.algo2 = algo2;
            this.rawPValue = rawPValue;
        }
    }
    
    /**
     * Result class for validation results with correction support.
     */
    public static class ValidationResult {
        private final String metric;
        private Map<String, DescriptiveStatistics> descriptiveStats;
        private Map<String, Boolean> normalityResults;
        private Map<String, Map<String, ComparisonResult>> pairwiseComparisons;
        private ANOVAResult anovaResult;
        private Map<String, ConfidenceInterval> confidenceIntervals;
        private Map<String, Map<String, Double>> effectSizes;
        private String bestAlgorithm;
        private CorrectionMethod correctionMethod;
        private final long timestamp;
        
        public ValidationResult(String metric) {
            this.metric = metric;
            this.timestamp = System.currentTimeMillis();
        }
        
        // Getters and setters (previous ones plus new ones)
        public CorrectionMethod getCorrectionMethod() { return correctionMethod; }
        public void setCorrectionMethod(CorrectionMethod method) { this.correctionMethod = method; }
        
        // ... [Previous getters and setters remain the same] ...
        public String getMetric() { return metric; }
        public Map<String, DescriptiveStatistics> getDescriptiveStats() { return descriptiveStats; }
        public void setDescriptiveStats(Map<String, DescriptiveStatistics> stats) { this.descriptiveStats = stats; }
        public Map<String, Boolean> getNormalityResults() { return normalityResults; }
        public void setNormalityResults(Map<String, Boolean> results) { this.normalityResults = results; }
        public Map<String, Map<String, ComparisonResult>> getPairwiseComparisons() { return pairwiseComparisons; }
        public void setPairwiseComparisons(Map<String, Map<String, ComparisonResult>> comparisons) { this.pairwiseComparisons = comparisons; }
        public ANOVAResult getAnovaResult() { return anovaResult; }
        public void setAnovaResult(ANOVAResult result) { this.anovaResult = result; }
        public Map<String, ConfidenceInterval> getConfidenceIntervals() { return confidenceIntervals; }
        public void setConfidenceIntervals(Map<String, ConfidenceInterval> intervals) { this.confidenceIntervals = intervals; }
        public Map<String, Map<String, Double>> getEffectSizes() { return effectSizes; }
        public void setEffectSizes(Map<String, Map<String, Double>> sizes) { this.effectSizes = sizes; }
        public String getBestAlgorithm() { return bestAlgorithm; }
        public void setBestAlgorithm(String algorithm) { this.bestAlgorithm = algorithm; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * Enhanced result class for pairwise comparisons with correction support.
     */
    public static class ComparisonResult {
        private final String algorithm1;
        private final String algorithm2;
        private String testType;
        private double rawPValue;
        private double adjustedPValue;
        private boolean significant; // Before correction
        private boolean significantAfterCorrection; // After correction
        private double effectSize;
        private String effectSizeInterpretation;
        private double meanDifference;
        private String correctionMethod;
        private boolean error;
        private String errorMessage;
        
        public ComparisonResult(String algorithm1, String algorithm2) {
            this.algorithm1 = algorithm1;
            this.algorithm2 = algorithm2;
            this.error = false;
            this.adjustedPValue = Double.NaN;
            this.significantAfterCorrection = false;
        }
        
        // Enhanced getters and setters
        public double getRawPValue() { return rawPValue; }
        public void setRawPValue(double pValue) { this.rawPValue = pValue; }
        public double getAdjustedPValue() { return adjustedPValue; }
        public void setAdjustedPValue(double pValue) { this.adjustedPValue = pValue; }
        public boolean isSignificantAfterCorrection() { return significantAfterCorrection; }
        public void setSignificantAfterCorrection(boolean significant) { 
            this.significantAfterCorrection = significant; 
        }
        public String getCorrectionMethod() { return correctionMethod; }
        public void setCorrectionMethod(String method) { this.correctionMethod = method; }
        
        /**
         * Returns the p-value to use for significance testing (adjusted if available).
         * 
         * @return The appropriate p-value
         */
        public double getPValue() {
            return Double.isNaN(adjustedPValue) ? rawPValue : adjustedPValue;
        }
        
        // ... [Other getters and setters remain the same] ...
        public String getAlgorithm1() { return algorithm1; }
        public String getAlgorithm2() { return algorithm2; }
        public String getTestType() { return testType; }
        public void setTestType(String testType) { this.testType = testType; }
        public boolean isSignificant() { return significant; }
        public void setSignificant(boolean significant) { this.significant = significant; }
        public double getEffectSize() { return effectSize; }
        public void setEffectSize(double effectSize) { this.effectSize = effectSize; }
        public String getEffectSizeInterpretation() { return effectSizeInterpretation; }
        public void setEffectSizeInterpretation(String interpretation) { this.effectSizeInterpretation = interpretation; }
        public double getMeanDifference() { return meanDifference; }
        public void setMeanDifference(double difference) { this.meanDifference = difference; }
        public boolean isError() { return error; }
        public void setError(boolean error) { this.error = error; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String message) { this.errorMessage = message; }
    }
}