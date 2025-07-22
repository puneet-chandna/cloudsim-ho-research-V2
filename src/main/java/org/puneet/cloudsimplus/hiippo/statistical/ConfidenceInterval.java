package org.puneet.cloudsimplus.hiippo.statistical;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math3.stat.interval.ConfidenceInterval;
import org.apache.commons.math3.stat.interval.NormalApproximationInterval;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.DoubleStream;

/**
 * Computes confidence intervals for experimental results using various statistical methods.
 * Supports both normal approximation and t-distribution based intervals.
 * 
 * <p>This class provides comprehensive confidence interval calculations for the
 * Hippopotamus Optimization research framework, ensuring statistical validity
 * of experimental results.</p>
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-20
 */
public class ConfidenceInterval {
    
    private static final Logger logger = LoggerFactory.getLogger(ConfidenceInterval.class);
    
    /** Default confidence level (95%) */
    public static final double DEFAULT_CONFIDENCE_LEVEL = 0.95;
    
    /** Minimum sample size required for reliable interval calculation */
    private static final int MIN_SAMPLE_SIZE = 3;
    
    private final double lowerBound;
    private final double upperBound;
    private final double mean;
    private final double standardError;
    private final double confidenceLevel;
    private final int sampleSize;
    private final String methodUsed;
    
    /**
     * Creates a confidence interval with specified bounds and parameters.
     * 
     * @param lowerBound the lower bound of the interval
     * @param upperBound the upper bound of the interval
     * @param mean the sample mean
     * @param standardError the standard error of the mean
     * @param confidenceLevel the confidence level (e.g., 0.95 for 95%)
     * @param sampleSize the number of observations
     * @param methodUsed the statistical method used for calculation
     * @throws IllegalArgumentException if any parameter is invalid
     */
    public ConfidenceInterval(double lowerBound, double upperBound, double mean, 
                            double standardError, double confidenceLevel, 
                            int sampleSize, String methodUsed) {
        validateParameters(lowerBound, upperBound, confidenceLevel, sampleSize);
        
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.mean = mean;
        this.standardError = standardError;
        this.confidenceLevel = confidenceLevel;
        this.sampleSize = sampleSize;
        this.methodUsed = Objects.requireNonNull(methodUsed, "Method used cannot be null");
        
        logger.debug("Created confidence interval: [{}, {}] with {}% confidence (n={}) using {}",
            lowerBound, upperBound, confidenceLevel * 100, sampleSize, methodUsed);
    }
    
    /**
     * Computes confidence interval using normal approximation.
     * Suitable for large sample sizes (n >= 30) or known population standard deviation.
     * 
     * @param data the sample data
     * @param confidenceLevel the desired confidence level (e.g., 0.95)
     * @return confidence interval
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static ConfidenceInterval computeNormalApproximation(double[] data, double confidenceLevel) {
        logger.info("Computing normal approximation confidence interval with {}% confidence", 
            confidenceLevel * 100);
        
        validateData(data);
        
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        int n = data.length;
        
        try {
            NormalApproximationInterval intervalCalculator = new NormalApproximationInterval();
            ConfidenceInterval interval = intervalCalculator.createInterval(n, mean, stdDev, confidenceLevel);
            
            double standardError = stdDev / Math.sqrt(n);
            
            return new ConfidenceInterval(
                interval.getLowerBound(),
                interval.getUpperBound(),
                mean,
                standardError,
                confidenceLevel,
                n,
                "Normal Approximation"
            );
            
        } catch (MathIllegalArgumentException e) {
            throw new StatisticalValidationException(
                "Failed to compute normal approximation interval: " + e.getMessage(), e);
        }
    }
    
    /**
     * Computes confidence interval using t-distribution.
     * Suitable for small sample sizes (n < 30) with unknown population standard deviation.
     * 
     * @param data the sample data
     * @param confidenceLevel the desired confidence level (e.g., 0.95)
     * @return confidence interval
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static ConfidenceInterval computeTDistribution(double[] data, double confidenceLevel) {
        logger.info("Computing t-distribution confidence interval with {}% confidence", 
            confidenceLevel * 100);
        
        validateData(data);
        
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        int n = data.length;
        
        try {
            TDistribution tDist = new TDistribution(n - 1);
            double tValue = tDist.inverseCumulativeProbability(1 - (1 - confidenceLevel) / 2);
            double standardError = stdDev / Math.sqrt(n);
            double marginOfError = tValue * standardError;
            
            return new ConfidenceInterval(
                mean - marginOfError,
                mean + marginOfError,
                mean,
                standardError,
                confidenceLevel,
                n,
                "t-Distribution"
            );
            
        } catch (MathIllegalArgumentException e) {
            throw new StatisticalValidationException(
                "Failed to compute t-distribution interval: " + e.getMessage(), e);
        }
    }
    
    /**
     * Computes confidence interval using the most appropriate method based on sample size.
     * Uses t-distribution for n < 30, normal approximation for n >= 30.
     * 
     * @param data the sample data
     * @param confidenceLevel the desired confidence level (e.g., 0.95)
     * @return confidence interval
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static ConfidenceInterval computeOptimal(double[] data, double confidenceLevel) {
        logger.info("Computing optimal confidence interval based on sample size");
        
        validateData(data);
        
        if (data.length < 30) {
            return computeTDistribution(data, confidenceLevel);
        } else {
            return computeNormalApproximation(data, confidenceLevel);
        }
    }
    
    /**
     * Computes confidence interval using the default 95% confidence level.
     * 
     * @param data the sample data
     * @return confidence interval
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static ConfidenceInterval computeOptimal(double[] data) {
        return computeOptimal(data, DEFAULT_CONFIDENCE_LEVEL);
    }
    
    /**
     * Computes confidence intervals for multiple metrics from experimental results.
     * 
     * @param results list of experimental results
     * @param metricExtractor function to extract the metric value from each result
     * @param confidenceLevel the desired confidence level
     * @return confidence interval for the specified metric
     * @throws StatisticalValidationException if data is insufficient or invalid
     */
    public static <T> ConfidenceInterval computeForMetric(
            List<T> results, 
            java.util.function.ToDoubleFunction<T> metricExtractor, 
            double confidenceLevel) {
        
        logger.info("Computing confidence interval for metric across {} results", results.size());
        
        if (results == null || results.isEmpty()) {
            throw new StatisticalValidationException("Results list cannot be null or empty");
        }
        
        double[] data = results.stream()
            .mapToDouble(metricExtractor)
            .toArray();
            
        return computeOptimal(data, confidenceLevel);
    }
    
    /**
     * Computes the width of the confidence interval.
     * 
     * @return the width (upper bound - lower bound)
     */
    public double getWidth() {
        return upperBound - lowerBound;
    }
    
    /**
     * Computes the relative width as a percentage of the mean.
     * 
     * @return relative width as percentage (width / mean * 100)
     */
    public double getRelativeWidth() {
        if (mean == 0) {
            return 0.0;
        }
        return (getWidth() / Math.abs(mean)) * 100.0;
    }
    
    /**
     * Checks if the confidence interval contains zero.
     * 
     * @return true if zero is within the interval
     */
    public boolean containsZero() {
        return lowerBound <= 0 && upperBound >= 0;
    }
    
    /**
     * Checks if this interval overlaps with another interval.
     * 
     * @param other the other confidence interval
     * @return true if intervals overlap
     * @throws IllegalArgumentException if other is null
     */
    public boolean overlaps(ConfidenceInterval other) {
        Objects.requireNonNull(other, "Other interval cannot be null");
        return this.lowerBound <= other.upperBound && this.upperBound >= other.lowerBound;
    }
    
    /**
     * Formats the confidence interval as a string.
     * 
     * @return formatted string representation
     */
    @Override
    public String toString() {
        return String.format("[%.4f, %.4f] (mean=%.4f, n=%d, %.0f%% CI, method=%s)",
            lowerBound, upperBound, mean, sampleSize, confidenceLevel * 100, methodUsed);
    }
    
    /**
     * Creates a CSV-formatted string for result storage.
     * 
     * @return CSV string with all interval parameters
     */
    public String toCsvString() {
        return String.format("%.6f,%.6f,%.6f,%.6f,%d,%.4f,%s",
            lowerBound, upperBound, mean, standardError, sampleSize, confidenceLevel, methodUsed);
    }
    
    // Getters
    public double getLowerBound() { return lowerBound; }
    public double getUpperBound() { return upperBound; }
    public double getMean() { return mean; }
    public double getStandardError() { return standardError; }
    public double getConfidenceLevel() { return confidenceLevel; }
    public int getSampleSize() { return sampleSize; }
    public String getMethodUsed() { return methodUsed; }
    
    /**
     * Validates input parameters for confidence interval calculation.
     */
    private static void validateParameters(double lowerBound, double upperBound, 
                                         double confidenceLevel, int sampleSize) {
        if (lowerBound > upperBound) {
            throw new IllegalArgumentException(
                "Lower bound must be less than or equal to upper bound");
        }
        if (confidenceLevel <= 0 || confidenceLevel >= 1) {
            throw new IllegalArgumentException(
                "Confidence level must be between 0 and 1");
        }
        if (sampleSize < MIN_SAMPLE_SIZE) {
            throw new IllegalArgumentException(
                "Sample size must be at least " + MIN_SAMPLE_SIZE);
        }
    }
    
    /**
     * Validates input data array for confidence interval calculation.
     */
    private static void validateData(double[] data) {
        if (data == null) {
            throw new StatisticalValidationException("Data array cannot be null");
        }
        if (data.length < MIN_SAMPLE_SIZE) {
            throw new StatisticalValidationException(
                "Insufficient data points. Need at least " + MIN_SAMPLE_SIZE);
        }
        if (DoubleStream.of(data).anyMatch(Double::isNaN)) {
            throw new StatisticalValidationException("Data contains NaN values");
        }
        if (DoubleStream.of(data).allMatch(d -> d == data[0])) {
            logger.warn("All data points are identical - interval width will be zero");
        }
    }
    
    /**
     * Creates a header string for CSV output.
     * 
     * @return CSV header string
     */
    public static String getCsvHeader() {
        return "LowerBound,UpperBound,Mean,StandardError,SampleSize,ConfidenceLevel,Method";
    }
    
    /**
     * Computes confidence intervals for multiple metrics simultaneously.
     * 
     * @param results list of experimental results
     * @param metricExtractors map of metric names to extractor functions
     * @param confidenceLevel the desired confidence level
     * @return map of metric names to their confidence intervals
     */
    public static <T> java.util.Map<String, ConfidenceInterval> computeMultipleMetrics(
            List<T> results,
            java.util.Map<String, java.util.function.ToDoubleFunction<T>> metricExtractors,
            double confidenceLevel) {
        
        logger.info("Computing confidence intervals for {} metrics", metricExtractors.size());
        
        java.util.Map<String, ConfidenceInterval> intervals = new java.util.HashMap<>();
        
        for (java.util.Map.Entry<String, java.util.function.ToDoubleFunction<T>> entry : 
             metricExtractors.entrySet()) {
            try {
                ConfidenceInterval interval = computeForMetric(
                    results, entry.getValue(), confidenceLevel);
                intervals.put(entry.getKey(), interval);
            } catch (Exception e) {
                logger.error("Failed to compute interval for metric: {}. Skipping this metric.", entry.getKey(), e);
                // Continue with the next metric instead of throwing
            }
        }
        
        return intervals;
    }
}