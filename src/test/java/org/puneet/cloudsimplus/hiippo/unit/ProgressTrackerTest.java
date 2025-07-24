package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.util.ProgressTracker;
import static org.junit.jupiter.api.Assertions.*;

class ProgressTrackerTest {

    @Test
    void testReportProgressAndSummary() {
        ProgressTracker tracker = new ProgressTracker();
        assertDoesNotThrow(() -> tracker.reportProgress("phase1", 1, 10));
        assertDoesNotThrow(() -> tracker.reportProgress("phase1", 5, 10));
        assertDoesNotThrow(() -> tracker.reportProgress("phase1", 10, 10));
        String summary = tracker.getSummaryReport();
        assertNotNull(summary);
        assertTrue(summary.contains("phase1"));
        tracker.logSummaryReport();
    }

    @Test
    void testResetPhaseAndAll() {
        ProgressTracker tracker = new ProgressTracker();
        tracker.reportProgress("phase2", 2, 10);
        tracker.resetPhase("phase2");
        tracker.reportProgress("phase3", 3, 10);
        tracker.resetAll();
        assertEquals(0.0, tracker.getOverallProgress());
    }

    @Test
    void testProgressBar() {
        ProgressTracker tracker = new ProgressTracker();
        tracker.reportProgress("phase4", 5, 10);
        String bar = tracker.createProgressBar("phase4", 20);
        assertNotNull(bar);
        assertTrue(bar.contains("phase4"));
    }
} 