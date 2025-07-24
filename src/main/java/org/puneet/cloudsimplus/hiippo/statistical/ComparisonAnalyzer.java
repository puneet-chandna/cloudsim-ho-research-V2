package org.puneet.cloudsimplus.hiippo.statistical;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.inference.MannWhitneyUTest;
import org.apache.commons.math3.stat.inference.OneWayAnova;
import org.apache.commons.math3.util.Precision;
import org.puneet.cloudsimplus.hiippo.exceptions.StatisticalValidationException;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Performs comprehensive statistical comparison analysis between algorithms.
 * Includes pairwise comparisons, effect size calculations, and significance testing.
 * Uses parametric tests (t-test) for large samples and non-parametric tests (Mann-Whitney U)
 * for small samples to ensure statistical robustness.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-22
 */
public class ComparisonAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(ComparisonAnalyzer.class);
    
    private final TTest tTest;
    private final MannWhitneyUTest mannWhitneyUTest;
    private final OneWayAnova anova;
    private final Map<String, Map<String, List<Double>>> algorithmMetrics;
    private final Map<String, ComparisonResult> comparisonResults;
    private final List<String> algorithms;
    private final List<String> metrics;
    
    // File paths
    private static final String RAW_RESULTS_FILE = "results/raw_results/main_results.csv";
    private static final String COMPARISON_OUTPUT_DIR = "results/comparison_data";
    private static final String PAIRWISE_COMPARISON_FILE = "pairwise_comparisons.csv";
    private static final String EFFECT_SIZES_FILE = "effect_sizes.csv";
    private static final String RANKING_FILE = "algorithm_rankings.csv";
    
    // Statistical thresholds
    private static final double SIGNIFICANCE_LEVEL = ExperimentConfig.SIGNIFICANCE_LEVEL;
    private static final double SMALL_EFFECT_SIZE = 0.2;
    private static final double MEDIUM_EFFECT_SIZE = 0.5;
    private static final double LARGE_EFFECT_SIZE = 0.8;
    private static final int NORMALITY_SAMPLE_THRESHOLD = 30;
    
    /**
     * Constructs a new ComparisonAnalyzer instance.
     */
    public ComparisonAnalyzer() {
        this.tTest = new TTest();
        this.mannWhitneyUTest = new MannWhitneyUTest();
        this.anova = new OneWayAnova();
        this.algorithmMetrics = new HashMap<>();
        this.comparisonResults = new HashMap<>();
        this.algorithms = new ArrayList<>();
        this.metrics = Arrays.asList(
            "ResourceUtilCPU", "ResourceUtilRAM", "PowerConsumption", 
            "SLAViolations", "ExecutionTime", "ConvergenceIterations"
        );
        
        logger.info("ComparisonAnalyzer initialized with significance level: {}", SIGNIFICANCE_LEVEL);
        logger.info("Using non-parametric tests for samples < {}", NORMALITY_SAMPLE_THRESHOLD);
    }
    
    /**
     * Performs complete comparison analysis workflow.
     * 
     * @throws IOException if file operations fail
     * @throws StatisticalValidationException if statistical validation fails
     */
    public void performCompleteAnalysis() throws IOException, StatisticalValidationException {
        logger.info("Starting complete comparison analysis");
        
        try {
            // Check memory before starting
            MemoryManager.checkMemoryUsage("ComparisonAnalysis-Start");
            
            // Step 1: Load experimental results
            loadExperimentalResults();
            
            // Step 2: Validate data completeness
            validateDataCompleteness();
            
            // Step 3: Perform ANOVA tests
            Map<String, ANOVAResult> anovaResults = performANOVATests();
            
            // Step 4: Perform pairwise comparisons
            performPairwiseComparisons();
            
            // Step 5: Calculate effect sizes
            calculateEffectSizes();
            
            // Step 6: Generate algorithm rankings
            generateAlgorithmRankings();
            
            // Step 7: Write all results
            writeComparisonResults(anovaResults);
            
            logger.info("Comparison analysis completed successfully");
            
        } catch (Exception e) {
            logger.error("Error during comparison analysis", e);
            throw new StatisticalValidationException(StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR, "Comparison analysis failed: " + e.getMessage(), e);
        } finally {
            // Clean up memory
            MemoryManager.checkMemoryUsage("ComparisonAnalysis-End");
        }
    }
    
    /**
     * Loads experimental results from CSV file.
     * 
     * @throws IOException if file reading fails
     */
    private void loadExperimentalResults() throws IOException {
        logger.info("Loading experimental results from: {}", RAW_RESULTS_FILE);
        
        Path resultsPath = Paths.get(RAW_RESULTS_FILE);
        if (!Files.exists(resultsPath)) {
            throw new IOException("Results file not found: " + RAW_RESULTS_FILE);
        }
        
        try (FileReader reader = new FileReader(RAW_RESULTS_FILE);
             CSVParser parser = new CSVParser(reader, CSVFormat.DEFAULT.withFirstRecordAsHeader())) {
            
            int recordCount = 0;
            for (CSVRecord record : parser) {
                try {
                    String algorithm = record.get("Algorithm");
                    String scenario = record.get("Scenario");
                    
                    // Initialize data structures
                    if (!algorithms.contains(algorithm)) {
                        algorithms.add(algorithm);
                        algorithmMetrics.put(algorithm, new HashMap<>());
                        for (String metric : metrics) {
                            algorithmMetrics.get(algorithm).put(metric, new ArrayList<>());
                        }
                    }
                    
                    // Extract metrics
                    for (String metric : metrics) {
                        String value = record.get(metric);
                        if (value != null && !value.isEmpty()) {
                            double metricValue = Double.parseDouble(value);
                            algorithmMetrics.get(algorithm).get(metric).add(metricValue);
                        }
                    }
                    
                    recordCount++;
                    if (recordCount % 1000 == 0) {
                        logger.debug("Loaded {} records", recordCount);
                        MemoryManager.checkMemoryUsage("Loading-" + recordCount);
                    }
                    
                } catch (NumberFormatException e) {
                    logger.warn("Skipping invalid record: {}", record.toString());
                }
            }
            
            logger.info("Loaded {} records for {} algorithms", recordCount, algorithms.size());
        }
    }
    
    /**
     * Validates that all algorithms have complete data.
     * 
     * @throws ValidationException if data is incomplete
     */
    private void validateDataCompleteness() throws ValidationException {
        logger.info("Validating data completeness");
        
        for (String algorithm : algorithms) {
            Map<String, List<Double>> metrics = algorithmMetrics.get(algorithm);
            
            if (metrics == null || metrics.isEmpty()) {
                throw new ValidationException("No metrics found for algorithm: " + algorithm);
            }
            
            int expectedSamples = ExperimentConfig.REPLICATION_COUNT * 5; // 5 scenarios
            
            for (Map.Entry<String, List<Double>> entry : metrics.entrySet()) {
                String metricName = entry.getKey();
                List<Double> values = entry.getValue();
                
                if (values == null || values.isEmpty()) {
                    throw new ValidationException(
                        String.format("No values for metric %s in algorithm %s", metricName, algorithm)
                    );
                }
                
                if (values.size() < expectedSamples * 0.9) { // Allow 10% missing data
                    logger.warn("Incomplete data for {} - {}: {} samples (expected ~{})", 
                        algorithm, metricName, values.size(), expectedSamples);
                }
            }
        }
        
        logger.info("Data validation completed successfully");
    }
    
    /**
     * Performs ANOVA tests for each metric across all algorithms.
     * 
     * @return Map of metric names to ANOVA results
     * @throws StatisticalValidationException if ANOVA fails
     */
    private Map<String, ANOVAResult> performANOVATests() throws StatisticalValidationException {
        logger.info("Performing ANOVA tests for {} metrics", metrics.size());
        
        Map<String, ANOVAResult> anovaResults = new HashMap<>();
        
        for (String metric : metrics) {
            try {
                logger.debug("Running ANOVA for metric: {}", metric);
                
                // Prepare data for ANOVA
                Collection<double[]> groups = new ArrayList<>();
                List<String> groupLabels = new ArrayList<>();
                
                for (String algorithm : algorithms) {
                    List<Double> values = algorithmMetrics.get(algorithm).get(metric);
                    if (values != null && !values.isEmpty()) {
                        double[] array = values.stream()
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .toArray();
                        
                        if (array.length >= 3) { // Minimum samples for ANOVA
                            groups.add(array);
                            groupLabels.add(algorithm);
                        }
                    }
                }
                
                if (groups.size() < 2) {
                    logger.warn("Insufficient groups for ANOVA on metric: {}", metric);
                    continue;
                }
                
                // Perform ANOVA
                double fStatistic = anova.anovaFValue(groups);
                double pValue = anova.anovaPValue(groups);
                boolean significant = pValue < SIGNIFICANCE_LEVEL;
                
                // Calculate degrees of freedom
                int dfBetween = groups.size() - 1;
                int dfWithin = groups.stream()
                    .mapToInt(group -> group.length - 1)
                    .sum();
                
                // Map<String, Double> groupMeans = ANOVAResult.calculateGroupMeans(data);
                // Map<String, Double> groupStdDevs = ANOVAResult.calculateGroupStdDevs(data);
                // Map<String, Integer> groupSizes = ANOVAResult.calculateGroupSizes(data);
                double sumSquaresBetween = 0.0;
                double sumSquaresWithin = 0.0;
                
                // Create ANOVA result
                ANOVAResult result = new ANOVAResult(
                    fStatistic, pValue, dfBetween, dfWithin,
                    sumSquaresBetween, sumSquaresWithin,
                    metric, groupLabels, new HashMap<>(), new HashMap<>(), new HashMap<>(),
                    SIGNIFICANCE_LEVEL, 0L);
                
                anovaResults.put(metric, result);
                
                logger.info("ANOVA for {}: F={:.4f}, p={:.4f}, significant={}", 
                    metric, fStatistic, pValue, significant);
                
            } catch (Exception e) {
                logger.error("ANOVA failed for metric: {}", metric, e);
                throw new StatisticalValidationException(StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR, "ANOVA failed for " + metric, e);
            }
        }
        
        return anovaResults;
    }
    
    /**
     * Performs pairwise comparisons between all algorithm pairs.
     * Uses parametric tests for large samples and non-parametric tests for small samples.
     * 
     * @throws StatisticalValidationException if comparison fails
     */
    private void performPairwiseComparisons() throws StatisticalValidationException {
        logger.info("Performing pairwise comparisons for {} algorithm pairs", 
            algorithms.size() * (algorithms.size() - 1) / 2);
        
        for (int i = 0; i < algorithms.size(); i++) {
            for (int j = i + 1; j < algorithms.size(); j++) {
                String algo1 = algorithms.get(i);
                String algo2 = algorithms.get(j);
                String comparisonKey = algo1 + "_vs_" + algo2;
                
                logger.debug("Comparing {} vs {}", algo1, algo2);
                
                ComparisonResult result = new ComparisonResult(algo1, algo2);
                
                for (String metric : metrics) {
                    try {
                        List<Double> values1 = algorithmMetrics.get(algo1).get(metric);
                        List<Double> values2 = algorithmMetrics.get(algo2).get(metric);
                        
                        if (values1 == null || values2 == null || 
                            values1.isEmpty() || values2.isEmpty()) {
                            logger.warn("Insufficient data for comparison: {} - {}", 
                                comparisonKey, metric);
                            continue;
                        }
                        
                        // Convert to arrays
                        double[] array1 = values1.stream()
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .toArray();
                        double[] array2 = values2.stream()
                            .filter(Objects::nonNull)
                            .mapToDouble(Double::doubleValue)
                            .toArray();
                        
                        if (array1.length < 2 || array2.length < 2) {
                            continue;
                        }
                        
                        // Determine which test to use based on sample size
                        double pValue;
                        String testUsed;
                        
                        if (array1.length < NORMALITY_SAMPLE_THRESHOLD || 
                            array2.length < NORMALITY_SAMPLE_THRESHOLD) {
                            // Use non-parametric Mann-Whitney U test for small samples
                            pValue = mannWhitneyUTest.mannWhitneyUTest(array1, array2);
                            testUsed = "Mann-Whitney U";
                            logger.debug("Using Mann-Whitney U test for {} - {} (small sample)", 
                                comparisonKey, metric);
                        } else {
                            // Use parametric t-test for large samples
                            pValue = tTest.tTest(array1, array2);
                            testUsed = "Student's t";
                            logger.debug("Using Student's t-test for {} - {} (large sample)", 
                                comparisonKey, metric);
                        }
                        
                        // Calculate t-statistic for reporting (even if Mann-Whitney was used)
                        double tStatistic = tTest.t(array1, array2);
                        boolean significant = pValue < SIGNIFICANCE_LEVEL;
                        
                        // Calculate descriptive statistics
                        DescriptiveStatistics stats1 = new DescriptiveStatistics(array1);
                        DescriptiveStatistics stats2 = new DescriptiveStatistics(array2);
                        
                        // Store comparison result with test type
                        MetricComparison comparison = new MetricComparison(
                            metric, stats1.getMean(), stats2.getMean(),
                            stats1.getStandardDeviation(), stats2.getStandardDeviation(),
                            tStatistic, pValue, significant
                        );
                        comparison.setTestUsed(testUsed);
                        
                        result.addMetricComparison(metric, comparison);
                        
                    } catch (Exception e) {
                        logger.error("Comparison failed for {} - {}", comparisonKey, metric, e);
                    }
                }
                
                comparisonResults.put(comparisonKey, result);
            }
        }
        
        logger.info("Pairwise comparisons completed");
    }
    
    /**
     * Calculates effect sizes (Cohen's d) for all comparisons.
     */
    private void calculateEffectSizes() {
        logger.info("Calculating effect sizes for all comparisons");
        
        for (Map.Entry<String, ComparisonResult> entry : comparisonResults.entrySet()) {
            ComparisonResult result = entry.getValue();
            
            for (MetricComparison comparison : result.getMetricComparisons().values()) {
                double cohensD = calculateCohensD(
                    comparison.getMean1(), comparison.getMean2(),
                    comparison.getStd1(), comparison.getStd2()
                );
                
                comparison.setEffectSize(cohensD);
                comparison.setEffectSizeMagnitude(interpretEffectSize(cohensD));
                
                logger.debug("{} - {}: Cohen's d = {:.3f} ({})", 
                    entry.getKey(), comparison.getMetric(), 
                    cohensD, comparison.getEffectSizeMagnitude());
            }
        }
    }
    
    /**
     * Calculates Cohen's d effect size.
     * 
     * @param mean1 Mean of first group
     * @param mean2 Mean of second group
     * @param std1 Standard deviation of first group
     * @param std2 Standard deviation of second group
     * @return Cohen's d value
     */
    private double calculateCohensD(double mean1, double mean2, double std1, double std2) {
        if (std1 == 0 && std2 == 0) {
            return 0;
        }
        
        // Pooled standard deviation
        double pooledStd = Math.sqrt((std1 * std1 + std2 * std2) / 2);
        
        if (pooledStd == 0) {
            return 0;
        }
        
        return Math.abs(mean1 - mean2) / pooledStd;
    }
    
    /**
     * Interprets effect size magnitude.
     * 
     * @param cohensD Cohen's d value
     * @return Effect size interpretation
     */
    private String interpretEffectSize(double cohensD) {
        double absD = Math.abs(cohensD);
        
        if (absD < SMALL_EFFECT_SIZE) {
            return "negligible";
        } else if (absD < MEDIUM_EFFECT_SIZE) {
            return "small";
        } else if (absD < LARGE_EFFECT_SIZE) {
            return "medium";
        } else {
            return "large";
        }
    }
    
    /**
     * Generates algorithm rankings based on multiple metrics.
     * 
     * @throws IOException if file writing fails
     */
    private void generateAlgorithmRankings() throws IOException {
        logger.info("Generating algorithm rankings");
        
        Map<String, Map<String, Double>> algorithmScores = new HashMap<>();
        
        // Calculate average ranks for each metric
        for (String metric : metrics) {
            List<AlgorithmScore> scores = new ArrayList<>();
            
            for (String algorithm : algorithms) {
                List<Double> values = algorithmMetrics.get(algorithm).get(metric);
                if (values != null && !values.isEmpty()) {
                    double mean = values.stream()
                        .filter(Objects::nonNull)
                        .mapToDouble(Double::doubleValue)
                        .average()
                        .orElse(0.0);
                    
                    scores.add(new AlgorithmScore(algorithm, mean));
                }
            }
            
            // Sort based on metric type (lower is better for most metrics)
            boolean lowerIsBetter = !metric.contains("Util"); // Utilization higher is better
            scores.sort((a, b) -> lowerIsBetter ? 
                Double.compare(a.score, b.score) : 
                Double.compare(b.score, a.score));
            
            // Assign ranks
            for (int i = 0; i < scores.size(); i++) {
                algorithmScores.computeIfAbsent(scores.get(i).algorithm, k -> new HashMap<>())
                    .put(metric, (double) (i + 1));
            }
        }
        
        // Calculate average rank across all metrics
        Map<String, Double> averageRanks = new HashMap<>();
        for (String algorithm : algorithms) {
            Map<String, Double> ranks = algorithmScores.get(algorithm);
            if (ranks != null) {
                double avgRank = ranks.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
                averageRanks.put(algorithm, avgRank);
            }
        }
        
        // Sort by average rank
        List<Map.Entry<String, Double>> sortedRanks = averageRanks.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());
        
        // Write rankings
        writeRankings(algorithmScores, sortedRanks);
        
        logger.info("Algorithm rankings generated successfully");
    }
    
    /**
     * Writes all comparison results to CSV files.
     * 
     * @param anovaResults ANOVA test results
     * @throws IOException if file writing fails
     */
    private void writeComparisonResults(Map<String, ANOVAResult> anovaResults) throws IOException {
        logger.info("Writing comparison results to output files");
        
        // Create output directory
        Files.createDirectories(Paths.get(COMPARISON_OUTPUT_DIR));
        
        // Write ANOVA results
        writeANOVAResults(anovaResults);
        
        // Write pairwise comparisons
        writePairwiseComparisons();
        
        // Write effect sizes
        writeEffectSizes();
        
        logger.info("All comparison results written successfully");
    }
    
    /**
     * Writes ANOVA results to CSV file.
     * 
     * @param anovaResults ANOVA test results
     * @throws IOException if file writing fails
     */
    private void writeANOVAResults(Map<String, ANOVAResult> anovaResults) throws IOException {
        Path outputPath = Paths.get(COMPARISON_OUTPUT_DIR, "anova_results.csv");
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                 "Metric", "F_Statistic", "P_Value", "DF_Between", "DF_Within", 
                 "Significant", "Algorithms"))) {
            
            for (Map.Entry<String, ANOVAResult> entry : anovaResults.entrySet()) {
                ANOVAResult result = entry.getValue();
                printer.printRecord(
                    result.getMetricName(),
                    Precision.round(result.getFStatistic(), 4),
                    Precision.round(result.getPValue(), 4),
                    result.getDfBetween(),
                    result.getDfWithin(),
                    result.isSignificant(),
                    String.join(";", result.getGroupNames())
                );
            }
            
            logger.info("ANOVA results written to: {}", outputPath);
        }
    }
    
    /**
     * Writes pairwise comparison results to CSV file.
     * 
     * @throws IOException if file writing fails
     */
    private void writePairwiseComparisons() throws IOException {
        Path outputPath = Paths.get(COMPARISON_OUTPUT_DIR, PAIRWISE_COMPARISON_FILE);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                 "Algorithm1", "Algorithm2", "Metric", "Mean1", "Mean2", "Std1", "Std2",
                 "T_Statistic", "P_Value", "Significant", "Test_Used", "Winner"))) {
            
            for (ComparisonResult result : comparisonResults.values()) {
                for (MetricComparison comparison : result.getMetricComparisons().values()) {
                    String winner = "none";
                    if (comparison.isSignificant()) {
                        boolean algo1Better = isAlgorithm1Better(
                            comparison.getMetric(), 
                            comparison.getMean1(), 
                            comparison.getMean2()
                        );
                        winner = algo1Better ? result.getAlgorithm1() : result.getAlgorithm2();
                    }
                    
                    printer.printRecord(
                        result.getAlgorithm1(),
                        result.getAlgorithm2(),
                        comparison.getMetric(),
                        Precision.round(comparison.getMean1(), 4),
                        Precision.round(comparison.getMean2(), 4),
                        Precision.round(comparison.getStd1(), 4),
                        Precision.round(comparison.getStd2(), 4),
                        Precision.round(comparison.getTStatistic(), 4),
                        Precision.round(comparison.getPValue(), 4),
                        comparison.isSignificant(),
                        comparison.getTestUsed(),
                        winner
                    );
                }
            }
            
            logger.info("Pairwise comparisons written to: {}", outputPath);
        }
    }
    
    /**
     * Writes effect sizes to CSV file.
     * 
     * @throws IOException if file writing fails
     */
    private void writeEffectSizes() throws IOException {
        Path outputPath = Paths.get(COMPARISON_OUTPUT_DIR, EFFECT_SIZES_FILE);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.withHeader(
                 "Algorithm1", "Algorithm2", "Metric", "CohenD", "Magnitude"))) {
            
            for (Map.Entry<String, ComparisonResult> entry : comparisonResults.entrySet()) {
                ComparisonResult result = entry.getValue();
                
                for (MetricComparison comparison : result.getMetricComparisons().values()) {
                    printer.printRecord(
                        result.getAlgorithm1(),
                        result.getAlgorithm2(),
                        comparison.getMetric(),
                        Precision.round(comparison.getEffectSize(), 4),
                        comparison.getEffectSizeMagnitude()
                    );
                }
            }
            
            logger.info("Effect sizes written to: {}", outputPath);
        }
    }
    
    /**
     * Writes algorithm rankings to CSV file.
     * 
     * @param algorithmScores Detailed scores for each metric
     * @param sortedRanks Overall rankings
     * @throws IOException if file writing fails
     */
    private void writeRankings(Map<String, Map<String, Double>> algorithmScores,
                              List<Map.Entry<String, Double>> sortedRanks) throws IOException {
        Path outputPath = Paths.get(COMPARISON_OUTPUT_DIR, RANKING_FILE);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT)) {
            
            // Write header
            List<String> header = new ArrayList<>();
            header.add("Rank");
            header.add("Algorithm");
            header.add("Average_Rank");
            header.addAll(metrics);
            printer.printRecord(header);
            
            // Write rankings
            int rank = 1;
            for (Map.Entry<String, Double> entry : sortedRanks) {
                String algorithm = entry.getKey();
                List<Object> row = new ArrayList<>();
                row.add(rank++);
                row.add(algorithm);
                row.add(Precision.round(entry.getValue(), 2));
                
                // Add individual metric ranks
                Map<String, Double> scores = algorithmScores.get(algorithm);
                for (String metric : metrics) {
                    Double score = scores != null ? scores.get(metric) : null;
                    row.add(score != null ? score.intValue() : "N/A");
                }
                
                printer.printRecord(row);
            }
            
            logger.info("Algorithm rankings written to: {}", outputPath);
        }
    }
    
    /**
     * Determines if algorithm 1 is better than algorithm 2 for a given metric.
     * 
     * @param metric Metric name
     * @param mean1 Mean value for algorithm 1
     * @param mean2 Mean value for algorithm 2
     * @return true if algorithm 1 is better
     */
    private boolean isAlgorithm1Better(String metric, double mean1, double mean2) {
        // For utilization metrics, higher is better
        if (metric.contains("Util")) {
            return mean1 > mean2;
        }
        // For other metrics (power, SLA, time), lower is better
        return mean1 < mean2;
    }
    
    /**
     * Gets comparison results for external use.
     * 
     * @return Map of comparison results
     */
    public Map<String, ComparisonResult> getComparisonResults() {
        return new HashMap<>(comparisonResults);
    }
    
    /**
     * Gets algorithm metrics for external use.
     * 
     * @return Map of algorithm metrics
     */
    public Map<String, Map<String, List<Double>>> getAlgorithmMetrics() {
        return new HashMap<>(algorithmMetrics);
    }
    
    /**
     * Helper class to store algorithm scores.
     */
    private static class AlgorithmScore {
        final String algorithm;
        final double score;
        
        AlgorithmScore(String algorithm, double score) {
            this.algorithm = algorithm;
            this.score = score;
        }
    }
    
    /**
     * Represents a comparison result between two algorithms.
     */
    public static class ComparisonResult {
        private final String algorithm1;
        private final String algorithm2;
        private final Map<String, MetricComparison> metricComparisons;
        
        public ComparisonResult(String algorithm1, String algorithm2) {
            this.algorithm1 = algorithm1;
            this.algorithm2 = algorithm2;
            this.metricComparisons = new HashMap<>();
        }
        
        public void addMetricComparison(String metric, MetricComparison comparison) {
            metricComparisons.put(metric, comparison);
        }
        
        public String getAlgorithm1() {
            return algorithm1;
        }
        
        public String getAlgorithm2() {
            return algorithm2;
        }
        
        public Map<String, MetricComparison> getMetricComparisons() {
            return metricComparisons;
        }
    }
    
    /**
     * Represents a metric comparison between two algorithms.
     */
    public static class MetricComparison {
        private final String metric;
        private final double mean1;
        private final double mean2;
        private final double std1;
        private final double std2;
        private final double tStatistic;
        private final double pValue;
        private final boolean significant;
        private double effectSize;
        private String effectSizeMagnitude;
        private String testUsed;
        
        public MetricComparison(String metric, double mean1, double mean2,
                               double std1, double std2, double tStatistic,
                               double pValue, boolean significant) {
            this.metric = metric;
            this.mean1 = mean1;
            this.mean2 = mean2;
            this.std1 = std1;
            this.std2 = std2;
            this.tStatistic = tStatistic;
            this.pValue = pValue;
            this.significant = significant;
            this.testUsed = "Student's t"; // Default
        }
        
        // Getters
        public String getMetric() { return metric; }
        public double getMean1() { return mean1; }
        public double getMean2() { return mean2; }
        public double getStd1() { return std1; }
        public double getStd2() { return std2; }
        public double getTStatistic() { return tStatistic; }
        public double getPValue() { return pValue; }
        public boolean isSignificant() { return significant; }
        public double getEffectSize() { return effectSize; }
        public String getEffectSizeMagnitude() { return effectSizeMagnitude; }
        public String getTestUsed() { return testUsed; }
        
        // Setters
        public void setEffectSize(double effectSize) {
            this.effectSize = effectSize;
        }
        
        public void setEffectSizeMagnitude(String magnitude) {
            this.effectSizeMagnitude = magnitude;
        }
        
        public void setTestUsed(String testUsed) {
            this.testUsed = testUsed;
        }
    }
}