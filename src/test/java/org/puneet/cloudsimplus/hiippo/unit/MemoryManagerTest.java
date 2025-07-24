package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.util.MemoryManager;
import static org.junit.jupiter.api.Assertions.*;

class MemoryManagerTest {

    @Test
    void testInitializeAndShutdown() {
        assertDoesNotThrow(MemoryManager::initialize);
        assertDoesNotThrow(MemoryManager::shutdown);
    }

    @Test
    void testMemoryStatsAndGC() {
        assertDoesNotThrow(() -> MemoryManager.checkMemoryUsage("test phase"));
        assertDoesNotThrow(() -> MemoryManager.triggerGarbageCollection("test"));
        assertDoesNotThrow(MemoryManager::forceGarbageCollection);
    }

    @Test
    void testBatchSizeOptimization() {
        int batch = MemoryManager.optimizeBatchSize(10);
        assertTrue(batch >= 5 && batch <= 50);
        int recommended = MemoryManager.getRecommendedBatchSize();
        assertTrue(recommended >= 5 && recommended <= 50);
    }

    @Test
    void testWaitForMemoryPressureReduction() {
        assertDoesNotThrow(() -> MemoryManager.waitForMemoryPressureReduction(10));
    }

    @Test
    void testEmergencyMemoryCleanup() {
        assertDoesNotThrow(MemoryManager::emergencyMemoryCleanup);
    }

    @Test
    void testLogMemoryStateAndReport() {
        assertDoesNotThrow(() -> MemoryManager.logMemoryState("test"));
        String report = MemoryManager.getMemoryReport();
        assertNotNull(report);
        assertTrue(report.contains("Memory Report"));
    }

    @Test
    void testTrackAllocationAndDeallocation() {
        assertDoesNotThrow(() -> MemoryManager.trackAllocation(1024, "test alloc"));
        assertDoesNotThrow(() -> MemoryManager.trackDeallocation(1024, "test dealloc"));
    }
} 