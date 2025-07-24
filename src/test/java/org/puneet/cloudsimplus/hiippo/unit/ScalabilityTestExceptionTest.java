package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.exceptions.ScalabilityTestException;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class ScalabilityTestExceptionTest {

    @Test
    void testScalabilityErrorTypeEnum() {
        for (ScalabilityTestException.ScalabilityErrorType type : ScalabilityTestException.ScalabilityErrorType.values()) {
            assertNotNull(type.getCode());
            assertNotNull(type.getDescription());
        }
    }

    @Test
    void testPerformanceThreshold() {
        ScalabilityTestException.PerformanceThreshold t = new ScalabilityTestException.PerformanceThreshold(
                "CPU", 90.0, 95.0, ScalabilityTestException.PerformanceThreshold.ThresholdType.MAXIMUM);
        assertTrue(t.isViolated());
        assertTrue(t.toString().contains("CPU"));
    }

    @Test
    void testConstructorAndGetters() {
        ScalabilityTestException ex = new ScalabilityTestException(
                ScalabilityTestException.ScalabilityErrorType.MEMORY_EXHAUSTION, "Memory error");
        assertEquals(ScalabilityTestException.ScalabilityErrorType.MEMORY_EXHAUSTION, ex.getErrorType());
        assertNotNull(ex.getTimestamp());
        assertNotNull(ex.toString());
    }

    @Test
    void testMemoryExhaustionFactory() {
        ScalabilityTestException ex = ScalabilityTestException.memoryExhaustion("scenario", 10, 5, 1000, 500);
        assertEquals(ScalabilityTestException.ScalabilityErrorType.MEMORY_EXHAUSTION, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Memory exhausted"));
    }

    @Test
    void testTimeLimitExceededFactory() {
        ScalabilityTestException ex = ScalabilityTestException.timeLimitExceeded("scenario", 1000, 2000, null);
        assertEquals(ScalabilityTestException.ScalabilityErrorType.TIME_LIMIT_EXCEEDED, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Execution time exceeded"));
    }

    @Test
    void testPerformanceDegradationFactory() {
        ScalabilityTestException ex = ScalabilityTestException.performanceDegradation("scenario", 100.0, 80.0, 0.2, null);
        assertEquals(ScalabilityTestException.ScalabilityErrorType.PERFORMANCE_DEGRADATION, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Unacceptable performance degradation"));
    }

    @Test
    void testScenarioTooLargeFactory() {
        ScalabilityTestException ex = ScalabilityTestException.scenarioTooLarge(1000, 100, 500, 50, 1024);
        assertEquals(ScalabilityTestException.ScalabilityErrorType.SCENARIO_TOO_LARGE, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Scenario exceeds system capabilities"));
    }

    @Test
    void testComplexityViolationFactory() {
        ScalabilityTestException ex = ScalabilityTestException.complexityViolation("scenario", "O(n^2)", 2.0, null);
        assertEquals(ScalabilityTestException.ScalabilityErrorType.COMPLEXITY_VIOLATION, ex.getErrorType());
        assertTrue(ex.getDetailedMessage().contains("Time complexity exceeds theoretical bounds"));
    }

    @Test
    void testIsCriticalAndRetriable() {
        ScalabilityTestException ex = new ScalabilityTestException(
                ScalabilityTestException.ScalabilityErrorType.MEMORY_EXHAUSTION, "Critical");
        assertTrue(ex.isCritical());
        assertTrue(ex.isRetriableWithReducedScale());
        ScalabilityTestException ex2 = new ScalabilityTestException(
                ScalabilityTestException.ScalabilityErrorType.PERFORMANCE_DEGRADATION, "Not critical");
        assertTrue(ex2.isRetriableWithReducedScale());
        assertFalse(ex2.isCritical());
    }

    @Test
    void testToString() {
        ScalabilityTestException ex = new ScalabilityTestException(
                ScalabilityTestException.ScalabilityErrorType.BENCHMARK_FAILURE, "Benchmark error");
        String str = ex.toString();
        assertTrue(str.contains("ScalabilityTestException"));
        assertTrue(str.contains("BENCHMARK_FAILURE"));
    }
} 