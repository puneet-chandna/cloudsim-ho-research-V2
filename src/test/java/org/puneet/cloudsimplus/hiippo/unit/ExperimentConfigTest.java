package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.util.ExperimentConfig;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;

class ExperimentConfigTest {

    @Test
    void testConfigConstants() {
        assertEquals(123456L, ExperimentConfig.RANDOM_SEED);
        assertEquals(5, ExperimentConfig.REPLICATION_COUNT);  // Updated from 30 to 5
        assertEquals(0.95, ExperimentConfig.CONFIDENCE_LEVEL);
        assertEquals(0.05, ExperimentConfig.SIGNIFICANCE_LEVEL);
        assertTrue(ExperimentConfig.ENABLE_BATCH_PROCESSING);
        assertTrue(ExperimentConfig.MAX_HEAP_SIZE > 0);
        assertTrue(ExperimentConfig.MEMORY_WARNING_THRESHOLD > 0);
    }

    @Test
    void testRandomSeedInitializationAndRetrieval() {
        ExperimentConfig.initializeRandomSeed(0);
        assertNotNull(ExperimentConfig.getRandomGenerator(0));
        assertThrows(IllegalArgumentException.class, () -> ExperimentConfig.initializeRandomSeed(-1));
        assertThrows(IllegalStateException.class, () -> ExperimentConfig.getRandomGenerator(99));
    }

    @Test
    void testShouldRunGarbageCollection() {
        // This test just ensures the method runs without error
        assertDoesNotThrow(ExperimentConfig::shouldRunGarbageCollection);
    }

    @Test
    void testGetMemoryUsageStats() {
        String stats = ExperimentConfig.getMemoryUsageStats();
        assertNotNull(stats);
        assertTrue(stats.contains("Memory:"));
    }

    @Test
    void testEstimateMemoryRequirementAndCheck() {
        long estimate = ExperimentConfig.estimateMemoryRequirement(10, 3);
        assertTrue(estimate > 0);
        assertTrue(ExperimentConfig.hasEnoughMemoryForScenario(1, 1));
    }

    @Test
    void testGetScenarioSpecificationsAndAlgorithms() {
        Map<String, int[]> specs = ExperimentConfig.getScenarioSpecifications();
        assertTrue(specs.containsKey("Micro"));
        assertTrue(specs.get("Micro").length == 2);
        assertTrue(ExperimentConfig.getAlgorithms().contains("HO"));
    }
} 