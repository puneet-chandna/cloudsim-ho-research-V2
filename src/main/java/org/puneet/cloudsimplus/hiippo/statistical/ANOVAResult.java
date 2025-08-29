package org.puneet.cloudsimplus.hiippo.statistical;

import org.apache.commons.math3.stat.inference.OneWayAnova;
import org.apache.commons.math3.stat.inference.TestUtils;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import org.puneet.cloudsimplus.hiippo.exceptions.StatisticalValidationException;

/**
 * Encapsulates results from ANOVA (Analysis of Variance) statistical tests.
 * Provides comprehensive analysis including F-statistic, p-value, effect size,
 * and post-hoc test results for comparing multiple algorithms.
 * 
 * <p>This class supports both one-way ANOVA for single factor analysis and
 * stores additional statistical measures for research publication quality.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-21
 */
public class ANOVAResult {
    
    private static final Logger logger = LoggerFactory.getLogger(ANOVAResult.class);
    
    /** Significance level for hypothesis testing */
    public static final double DEFAULT_SIGNIFICANCE_LEVEL = 0.05;
    
    /** Minimum groups required for ANOVA */
    private static final int MIN_GROUPS = 2;
    
    /** Minimum observations per group */
    private static final int MIN_OBSERVATIONS_PER_GROUP = 3;
    
    private final double fStatistic;
    private final double pValue;
    private final int dfBetween;  // Degrees of freedom between groups
    private final int dfWithin;   // Degrees of freedom within groups
    private final double sumSquaresBetween;
    private final double sumSquaresWithin;
    private final double meanSquaresBetween;
    private final double meanSquaresWithin;
    private final double etaSquared;  // Effect size
    private final boolean isSignificant;
    private final String metricName;
    private final List<String> groupNames;
    private final Map<String, Double> groupMeans;
    private final Map<String, Double> groupStdDevs;
    private final Map<String, Integer> groupSizes;
    private final Map<String, Map<String, PostHocResult>> postHocResults;
    private final long computationTimeMs;
    
    /**
     * Creates a comprehensive ANOVA result with all statistical measures.
     * 
     * @param fStatistic F-statistic from ANOVA test
     * @param pValue p-value from ANOVA test
     * @param dfBetween degrees of freedom between groups
     * @param dfWithin degrees of freedom within groups
     * @param sumSquaresBetween sum of squares between groups
     * @param sumSquaresWithin sum of squares within groups
     * @param metricName name of the metric being analyzed
     * @param groupNames names of the groups being compared
     * @param groupMeans mean values for each group
     * @param groupStdDevs standard deviations for each group
     * @param groupSizes sample sizes for each group
     * @param significanceLevel significance level for test
     * @param computationTimeMs time taken for computation
     * @throws IllegalArgumentException if parameters are invalid
     */
    public ANOVAResult(double fStatistic, double pValue, int dfBetween, int dfWithin,
                      double sumSquaresBetween, double sumSquaresWithin,
                      String metricName, List<String> groupNames,
                      Map<String, Double> groupMeans, Map<String, Double> groupStdDevs,
                      Map<String, Integer> groupSizes, double significanceLevel,
                      long computationTimeMs) {
        
        validateParameters(fStatistic, pValue, dfBetween, dfWithin, groupNames);
        
        this.fStatistic = fStatistic;
        this.pValue = pValue;
        this.dfBetween = dfBetween;
        this.dfWithin = dfWithin;
        this.sumSquaresBetween = sumSquaresBetween;
        this.sumSquaresWithin = sumSquaresWithin;
        this.meanSquaresBetween = sumSquaresBetween / dfBetween;
        this.meanSquaresWithin = sumSquaresWithin / dfWithin;
        this.etaSquared = sumSquaresBetween / (sumSquaresBetween + sumSquaresWithin);
        this.isSignificant = pValue < significanceLevel;
        this.metricName = Objects.requireNonNull(metricName, "Metric name cannot be null");
        this.groupNames = new ArrayList<>(Objects.requireNonNull(groupNames, "Group names cannot be null"));
        this.groupMeans = new HashMap<>(Objects.requireNonNull(groupMeans, "Group means cannot be null"));
        this.groupStdDevs = new HashMap<>(Objects.requireNonNull(groupStdDevs, "Group std devs cannot be null"));
        this.groupSizes = new HashMap<>(Objects.requireNonNull(groupSizes, "Group sizes cannot be null"));
        this.postHocResults = new HashMap<>();
        this.computationTimeMs = computationTimeMs;
        
        logger.info("ANOVA Result created for {}: F({},{})={}, p={}, significant={}",
            metricName, dfBetween, dfWithin, String.format("%.3f", fStatistic), String.format("%.4f", pValue), isSignificant);
    }
    
    /**
     * Performs one-way ANOVA analysis on grouped data.
     * 
     * @param data map of group names to their observations
     * @param metricName name of the metric being analyzed
     * @param significanceLevel significance level for test
     * @return ANOVA result
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static ANOVAResult performANOVA(Map<String, double[]> data, String metricName, 
                                         double significanceLevel) throws StatisticalValidationException {
        logger.info("Performing ANOVA for metric: {} with {} groups", metricName, data.size());
        
        long startTime = System.currentTimeMillis();
        
        validateData(data);
        
        try {
            // Prepare data for ANOVA
            Collection<double[]> groups = data.values();
            OneWayAnova anova = new OneWayAnova();
            
            // Perform ANOVA test
            double fStatistic = anova.anovaFValue(groups);
            double pValue = anova.anovaPValue(groups);
            
            // Calculate degrees of freedom
            int k = data.size(); // number of groups
            int n = data.values().stream().mapToInt(arr -> arr.length).sum(); // total observations
            int dfBetween = k - 1;
            int dfWithin = n - k;
            
            // Calculate sum of squares
            double grandMean = calculateGrandMean(data);
            double sumSquaresBetween = calculateSumSquaresBetween(data, grandMean);
            double sumSquaresWithin = calculateSumSquaresWithin(data);
            
            // Calculate group statistics
            List<String> groupNames = new ArrayList<>(data.keySet());
            Map<String, Double> groupMeans = calculateGroupMeans(data);
            Map<String, Double> groupStdDevs = calculateGroupStdDevs(data);
            Map<String, Integer> groupSizes = calculateGroupSizes(data);
            
            long computationTime = System.currentTimeMillis() - startTime;
            
            ANOVAResult result = new ANOVAResult(
                fStatistic, pValue, dfBetween, dfWithin,
                sumSquaresBetween, sumSquaresWithin,
                metricName, groupNames, groupMeans, groupStdDevs, groupSizes,
                significanceLevel, computationTime
            );
            
            // Perform post-hoc tests if ANOVA is significant
            if (result.isSignificant()) {
                result.performPostHocTests(data);
            }
            
            return result;
            
        } catch (MathIllegalArgumentException e) {
            throw new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR,
                "Failed to perform ANOVA: " + e.getMessage());
        }
    }
    
    /**
     * Performs one-way ANOVA with default significance level (0.05).
     * 
     * @param data map of group names to their observations
     * @param metricName name of the metric being analyzed
     * @return ANOVA result
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static ANOVAResult performANOVA(Map<String, double[]> data, String metricName) throws StatisticalValidationException {
        return performANOVA(data, metricName, DEFAULT_SIGNIFICANCE_LEVEL);
    }
    
    /**
     * Performs post-hoc pairwise comparisons using Tukey's HSD test.
     * 
     * @param data original grouped data
     */
    private void performPostHocTests(Map<String, double[]> data) {
        logger.info("Performing post-hoc tests for {} groups", groupNames.size());
        
        // Perform pairwise t-tests with Bonferroni correction
        int numComparisons = groupNames.size() * (groupNames.size() - 1) / 2;
        double adjustedAlpha = DEFAULT_SIGNIFICANCE_LEVEL / numComparisons;
        
        for (int i = 0; i < groupNames.size(); i++) {
            String group1 = groupNames.get(i);
            Map<String, PostHocResult> group1Results = new HashMap<>();
            
            for (int j = i + 1; j < groupNames.size(); j++) {
                String group2 = groupNames.get(j);
                
                try {
                    double[] data1 = data.get(group1);
                    double[] data2 = data.get(group2);
                    
                    // Perform t-test
                    double tStatistic = TestUtils.t(data1, data2);
                    double pValue = TestUtils.tTest(data1, data2);
                    boolean isSignificant = pValue < adjustedAlpha;
                    
                    // Calculate effect size (Cohen's d)
                    double cohensD = calculateCohensD(data1, data2);
                    
                    PostHocResult postHoc = new PostHocResult(
                        group1, group2, tStatistic, pValue, 
                        adjustedAlpha, isSignificant, cohensD
                    );
                    
                    group1Results.put(group2, postHoc);
                    
                    // Also store in reverse direction for easy lookup
                    postHocResults.computeIfAbsent(group2, k -> new HashMap<>())
                                 .put(group1, postHoc);
                    
                } catch (Exception e) {
                    logger.error("Failed post-hoc test between {} and {}", group1, group2, e);
                }
            }
            
            postHocResults.put(group1, group1Results);
        }
    }
    
    /**
     * Checks if the result indicates a statistically significant difference.
     * 
     * @return true if p-value is less than significance level
     */
    public boolean isSignificant() {
        return isSignificant;
    }
    
    /**
     * Gets the effect size interpretation based on eta-squared.
     * 
     * @return effect size interpretation (small, medium, large)
     */
    public String getEffectSizeInterpretation() {
        if (etaSquared < 0.01) return "negligible";
        else if (etaSquared < 0.06) return "small";
        else if (etaSquared < 0.14) return "medium";
        else return "large";
    }
    
    /**
     * Generates a comprehensive summary of the ANOVA results.
     * 
     * @return formatted summary string
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("ANOVA Results for %s:\n", metricName));
        sb.append(String.format("F(%d,%d) = %.3f, p = %.4f\n", dfBetween, dfWithin, fStatistic, pValue));
        sb.append(String.format("Effect size (η²) = %.3f (%s)\n", etaSquared, getEffectSizeInterpretation()));
        sb.append(String.format("Result: %s (α = %.3f)\n", 
            isSignificant ? "Significant difference" : "No significant difference",
            DEFAULT_SIGNIFICANCE_LEVEL));
        
        if (isSignificant && !postHocResults.isEmpty()) {
            sb.append("\nPost-hoc comparisons (significant pairs):\n");
            for (String group1 : postHocResults.keySet()) {
                for (Map.Entry<String, PostHocResult> entry : postHocResults.get(group1).entrySet()) {
                    if (entry.getValue().isSignificant()) {
                        sb.append(String.format("  %s vs %s: p = %.4f, d = %.3f\n",
                            group1, entry.getKey(), 
                            entry.getValue().getPValue(),
                            entry.getValue().getCohensD()));
                    }
                }
            }
        }
        
        return sb.toString();
    }
    
    /**
     * Converts ANOVA result to CSV format for storage.
     * 
     * @return CSV string representation
     */
    public String toCsvString() {
        return String.format("%s,%.6f,%.6f,%d,%d,%.6f,%.6f,%.6f,%s,%.3f,%d",
            metricName, fStatistic, pValue, dfBetween, dfWithin,
            meanSquaresBetween, meanSquaresWithin, etaSquared,
            isSignificant ? "YES" : "NO",
            computationTimeMs / 1000.0,
            groupNames.size()
        );
    }
    
    /**
     * Gets CSV header for ANOVA results.
     * 
     * @return CSV header string
     */
    public static String getCsvHeader() {
        return "Metric,F_Statistic,P_Value,DF_Between,DF_Within,MS_Between,MS_Within," +
               "Eta_Squared,Significant,Computation_Time_Sec,Num_Groups";
    }
    
    /**
     * Gets detailed group statistics as CSV.
     * 
     * @return CSV string with group statistics
     */
    public String getGroupStatsCsvString() {
        StringBuilder sb = new StringBuilder();
        for (String group : groupNames) {
            sb.append(String.format("%s,%s,%.6f,%.6f,%d\n",
                metricName, group, 
                groupMeans.get(group),
                groupStdDevs.get(group),
                groupSizes.get(group)
            ));
        }
        return sb.toString();
    }
    
    /**
     * Gets CSV header for group statistics.
     * 
     * @return CSV header string
     */
    public static String getGroupStatsCsvHeader() {
        return "Metric,Group,Mean,StdDev,SampleSize";
    }
    
    // Getters
    public double getFStatistic() { return fStatistic; }
    public double getPValue() { return pValue; }
    public int getDfBetween() { return dfBetween; }
    public int getDfWithin() { return dfWithin; }
    public double getSumSquaresBetween() { return sumSquaresBetween; }
    public double getSumSquaresWithin() { return sumSquaresWithin; }
    public double getMeanSquaresBetween() { return meanSquaresBetween; }
    public double getMeanSquaresWithin() { return meanSquaresWithin; }
    public double getEtaSquared() { return etaSquared; }
    public String getMetricName() { return metricName; }
    public List<String> getGroupNames() { return new ArrayList<>(groupNames); }
    public Map<String, Double> getGroupMeans() { return new HashMap<>(groupMeans); }
    public Map<String, Double> getGroupStdDevs() { return new HashMap<>(groupStdDevs); }
    public Map<String, Integer> getGroupSizes() { return new HashMap<>(groupSizes); }
    public Map<String, Map<String, PostHocResult>> getPostHocResults() { 
        return new HashMap<>(postHocResults); 
    }
    
    // Helper methods
    
    private static void validateParameters(double fStatistic, double pValue, 
                                         int dfBetween, int dfWithin, List<String> groupNames) {
        if (Double.isNaN(fStatistic) || fStatistic < 0) {
            throw new IllegalArgumentException("F-statistic must be a non-negative number");
        }
        if (Double.isNaN(pValue) || pValue < 0 || pValue > 1) {
            throw new IllegalArgumentException("P-value must be between 0 and 1");
        }
        if (dfBetween < 1) {
            throw new IllegalArgumentException("Degrees of freedom between must be at least 1");
        }
        if (dfWithin < 1) {
            throw new IllegalArgumentException("Degrees of freedom within must be at least 1");
        }
        if (groupNames == null || groupNames.size() < MIN_GROUPS) {
            throw new IllegalArgumentException("At least " + MIN_GROUPS + " groups required");
        }
    }
    
    private static void validateData(Map<String, double[]> data) throws StatisticalValidationException {
        if (data == null || data.isEmpty()) {
            throw new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR,
                "Data map cannot be null or empty");
        }
        if (data.size() < MIN_GROUPS) {
            throw new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR,
                "ANOVA requires at least " + MIN_GROUPS + " groups");
        }
        
        // Check for all-zero data which would cause F-statistic errors
        boolean allZero = true;
        for (Map.Entry<String, double[]> entry : data.entrySet()) {
            if (entry.getValue() == null || entry.getValue().length < MIN_OBSERVATIONS_PER_GROUP) {
                throw new StatisticalValidationException(
                    StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR,
                    "Group '" + entry.getKey() + "' has insufficient observations");
            }
            
            // Check if this group has any non-zero values
            for (double value : entry.getValue()) {
                if (value != 0.0) {
                    allZero = false;
                    break;
                }
            }
        }
        
        if (allZero) {
            throw new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR,
                "All data values are zero, cannot perform ANOVA");
        }
    }
    
    private static double calculateGrandMean(Map<String, double[]> data) throws StatisticalValidationException {
        double sum = 0;
        int count = 0;
        for (double[] group : data.values()) {
            for (double value : group) {
                sum += value;
                count++;
            }
        }
        return sum / count;
    }
    
    private static double calculateSumSquaresBetween(Map<String, double[]> data, double grandMean) throws StatisticalValidationException {
        double ssb = 0;
        for (double[] group : data.values()) {
            double groupMean = Arrays.stream(group).average().orElse(0);
            ssb += group.length * Math.pow(groupMean - grandMean, 2);
        }
        return ssb;
    }
    
    private static double calculateSumSquaresWithin(Map<String, double[]> data) throws StatisticalValidationException {
        double ssw = 0;
        for (double[] group : data.values()) {
            double groupMean = Arrays.stream(group).average().orElse(0);
            for (double value : group) {
                ssw += Math.pow(value - groupMean, 2);
            }
        }
        return ssw;
    }
    
    private static Map<String, Double> calculateGroupMeans(Map<String, double[]> data) {
        return data.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> Arrays.stream(e.getValue()).average().orElse(0)
            ));
    }
    
    private static Map<String, Double> calculateGroupStdDevs(Map<String, double[]> data) {
        Map<String, Double> stdDevs = new HashMap<>();
        for (Map.Entry<String, double[]> entry : data.entrySet()) {
            double[] values = entry.getValue();
            double mean = Arrays.stream(values).average().orElse(0);
            double variance = Arrays.stream(values)
                .map(v -> Math.pow(v - mean, 2))
                .sum() / (values.length - 1);
            stdDevs.put(entry.getKey(), Math.sqrt(variance));
        }
        return stdDevs;
    }
    
    private static Map<String, Integer> calculateGroupSizes(Map<String, double[]> data) {
        return data.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> e.getValue().length
            ));
    }
    
    public static double calculateCohensD(double[] data1, double[] data2) {
        double mean1 = Arrays.stream(data1).average().orElse(0);
        double mean2 = Arrays.stream(data2).average().orElse(0);
        
        double var1 = Arrays.stream(data1)
            .map(v -> Math.pow(v - mean1, 2))
            .sum() / (data1.length - 1);
        double var2 = Arrays.stream(data2)
            .map(v -> Math.pow(v - mean2, 2))
            .sum() / (data2.length - 1);
            
        double pooledStdDev = Math.sqrt(((data1.length - 1) * var1 + 
                                        (data2.length - 1) * var2) / 
                                       (data1.length + data2.length - 2));
        
        return (mean1 - mean2) / pooledStdDev;
    }
    
    /**
     * Inner class representing post-hoc test results.
     */
    public static class PostHocResult {
        private final String group1;
        private final String group2;
        private final double tStatistic;
        private final double pValue;
        private final double adjustedAlpha;
        private final boolean isSignificant;
        private final double cohensD;
        
        public PostHocResult(String group1, String group2, double tStatistic, 
                           double pValue, double adjustedAlpha, 
                           boolean isSignificant, double cohensD) {
            this.group1 = group1;
            this.group2 = group2;
            this.tStatistic = tStatistic;
            this.pValue = pValue;
            this.adjustedAlpha = adjustedAlpha;
            this.isSignificant = isSignificant;
            this.cohensD = cohensD;
        }
        
        // Getters
        public String getGroup1() { return group1; }
        public String getGroup2() { return group2; }
        public double getTStatistic() { return tStatistic; }
        public double getPValue() { return pValue; }
        public double getAdjustedAlpha() { return adjustedAlpha; }
        public boolean isSignificant() { return isSignificant; }
        public double getCohensD() { return cohensD; }
    }
}