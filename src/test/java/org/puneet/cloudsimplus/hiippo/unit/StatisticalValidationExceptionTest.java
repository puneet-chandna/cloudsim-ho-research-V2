package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.exceptions.StatisticalValidationException;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class StatisticalValidationExceptionTest {

    @Test
    void testStatisticalErrorTypeEnum() {
        for (StatisticalValidationException.StatisticalErrorType type : StatisticalValidationException.StatisticalErrorType.values()) {
            assertNotNull(type.getCode());
            assertNotNull(type.getDescription());
        }
    }

    @Test
    void testStatisticalTestResult() {
        StatisticalValidationException.StatisticalTestResult result = new StatisticalValidationException.StatisticalTestResult(
                "t-test", 2.5, 0.04, 1.96, true);
        result.addStatistic("mean1", 1.0);
        assertEquals("t-test", result.getTestName());
        assertEquals(2.5, result.getTestStatistic());
        assertEquals(0.04, result.getPValue());
        assertEquals(1.96, result.getCriticalValue());
        assertTrue(result.isRejected());
        assertTrue(result.getAdditionalStats().containsKey("mean1"));
        assertTrue(result.toString().contains("t-test"));
    }

    @Test
    void testConstructorAndGetters() {
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.NORMALITY_VIOLATION, "Normality error");
        assertEquals(StatisticalValidationException.StatisticalErrorType.NORMALITY_VIOLATION, ex.getErrorType());
        assertNotNull(ex.getTimestamp());
        assertNotNull(ex.toString());
    }

    @Test
    void testInsufficientSampleSizeFactory() {
        StatisticalValidationException ex = StatisticalValidationException.insufficientSampleSize(5, 10, "t-test");
        assertEquals(StatisticalValidationException.StatisticalErrorType.INSUFFICIENT_SAMPLE_SIZE, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Sample size too small"));
    }

    @Test
    void testNormalityViolationFactory() {
        StatisticalValidationException ex = StatisticalValidationException.normalityViolation("dataset", "t-test", 0.2, 0.05);
        assertEquals(StatisticalValidationException.StatisticalErrorType.NORMALITY_VIOLATION, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("does not follow normal distribution"));
    }

    @Test
    void testAnovaAssumptionViolationFactory() {
        StatisticalValidationException ex = StatisticalValidationException.anovaAssumptionViolation(List.of("homogeneity"), Map.of());
        assertEquals(StatisticalValidationException.StatisticalErrorType.ANOVA_ASSUMPTION_VIOLATION, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("ANOVA assumptions not met"));
    }

    @Test
    void testInvalidPValueFactory() {
        StatisticalValidationException ex = StatisticalValidationException.invalidPValue(-0.1, "t-test");
        assertEquals(StatisticalValidationException.StatisticalErrorType.INVALID_P_VALUE, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Invalid p-value"));
    }

    @Test
    void testConfidenceIntervalErrorFactory() {
        StatisticalValidationException ex = StatisticalValidationException.confidenceIntervalError("dataset", 0.95, "bad interval");
        assertEquals(StatisticalValidationException.StatisticalErrorType.CONFIDENCE_INTERVAL_ERROR, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Confidence interval calculation failed"));
    }

    @Test
    void testInsufficientReplicationsFactory() {
        StatisticalValidationException ex = StatisticalValidationException.insufficientReplications(5, 30, 0.95);
        assertEquals(StatisticalValidationException.StatisticalErrorType.INSUFFICIENT_REPLICATIONS, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Insufficient replications"));
    }

    @Test
    void testIsCriticalAndInvalidatesResults() {
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.INSUFFICIENT_SAMPLE_SIZE, "Critical");
        assertTrue(ex.isCritical());
        StatisticalValidationException ex2 = new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.NORMALITY_VIOLATION, "Not critical");
        assertFalse(ex2.isCritical());
        assertFalse(ex2.invalidatesResults());
    }

    @Test
    void testToString() {
        StatisticalValidationException ex = new StatisticalValidationException(
                StatisticalValidationException.StatisticalErrorType.DATA_QUALITY_ERROR, "Data error");
        String str = ex.toString();
        assertTrue(str.contains("StatisticalValidationException"));
        assertTrue(str.contains("DATA_QUALITY_ERROR"));
    }
} 