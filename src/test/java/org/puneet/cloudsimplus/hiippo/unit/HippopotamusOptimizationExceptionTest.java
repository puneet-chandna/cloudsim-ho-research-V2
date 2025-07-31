package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.exceptions.HippopotamusOptimizationException;
import static org.junit.jupiter.api.Assertions.*;

class HippopotamusOptimizationExceptionTest {

    @Test
    void testErrorCodeEnum() {
        for (HippopotamusOptimizationException.ErrorCode code : HippopotamusOptimizationException.ErrorCode.values()) {
            assertNotNull(code.getCode());
            assertNotNull(code.getDescription());
        }
    }

    @Test
    void testConstructorAndGetters() {
        HippopotamusOptimizationException ex = new HippopotamusOptimizationException(
                HippopotamusOptimizationException.ErrorCode.ALLOCATION_FAILURE,
                "Allocation failed", "context info");
        assertEquals(HippopotamusOptimizationException.ErrorCode.ALLOCATION_FAILURE, ex.getErrorCode());
        assertEquals("context info", ex.getContext());
        assertNotNull(ex.getTimestamp());
        assertNull(ex.getAdditionalInfo());
        assertTrue(ex.getDetailedMessage().contains("Allocation failed"));
    }

    @Test
    void testInvalidParameterFactory() {
        HippopotamusOptimizationException ex = HippopotamusOptimizationException.invalidParameter(
                "populationSize", -1, "> 0");
        assertEquals(HippopotamusOptimizationException.ErrorCode.INVALID_PARAMETER, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Invalid parameter 'populationSize'"));
    }

    @Test
    void testConvergenceFailureFactory() {
        HippopotamusOptimizationException ex = HippopotamusOptimizationException.convergenceFailure(100, 0.001, 0.1);
        assertEquals(HippopotamusOptimizationException.ErrorCode.CONVERGENCE_FAILURE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("failed to converge"));
    }

    @Test
    void testAllocationFailureFactory() {
        HippopotamusOptimizationException ex = HippopotamusOptimizationException.allocationFailure(42L, "No host");
        assertEquals(HippopotamusOptimizationException.ErrorCode.ALLOCATION_FAILURE, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Failed to allocate VM 42"));
    }

    @Test
    void testMemoryConstraintFactory() {
        HippopotamusOptimizationException ex = HippopotamusOptimizationException.memoryConstraint(1024, 512, "MB");
        assertEquals(HippopotamusOptimizationException.ErrorCode.MEMORY_CONSTRAINT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Memory constraint violation"));
    }

    @Test
    void testIsCriticalAndRecoverable() {
        HippopotamusOptimizationException critical = new HippopotamusOptimizationException(
                HippopotamusOptimizationException.ErrorCode.MEMORY_CONSTRAINT, "Critical error");
        assertTrue(critical.isCritical());
        HippopotamusOptimizationException recoverable = new HippopotamusOptimizationException(
                HippopotamusOptimizationException.ErrorCode.ALLOCATION_FAILURE, "Recoverable error");
        assertTrue(recoverable.isRecoverable());
    }

    @Test
    void testToString() {
        HippopotamusOptimizationException ex = new HippopotamusOptimizationException(
                HippopotamusOptimizationException.ErrorCode.UNKNOWN, "Unknown error");
        String str = ex.toString();
        assertTrue(str.contains("HippopotamusOptimizationException"));
        assertTrue(str.contains("HO999")); // UNKNOWN error code
    }
} 