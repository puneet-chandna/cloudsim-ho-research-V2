package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.util.BatchProcessor;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class BatchProcessorTest {

    @Test
    void testProcessBatches() {
        List<Integer> items = new ArrayList<>();
        for (int i = 0; i < 20; i++) items.add(i);
        AtomicInteger sum = new AtomicInteger(0);
        assertDoesNotThrow(() -> BatchProcessor.processBatches(items, batch -> {
            for (int v : batch) sum.addAndGet(v);
        }, 5));
        assertEquals(190, sum.get());
    }

    @Test
    void testDetermineOptimalBatchSize() {
        int optimal = BatchProcessor.determineOptimalBatchSize(100);
        assertTrue(optimal >= 5 && optimal <= 50);
    }

    @Test
    void testThreadPoolSize() {
        int size = BatchProcessor.getThreadPoolSize();
        assertTrue(size >= 1);
    }

    @Test
    void testHasSufficientMemory() {
        assertTrue(BatchProcessor.hasSufficientMemory(1));
    }

    @Test
    void testShutdown() {
        assertDoesNotThrow(BatchProcessor::shutdown);
    }
} 