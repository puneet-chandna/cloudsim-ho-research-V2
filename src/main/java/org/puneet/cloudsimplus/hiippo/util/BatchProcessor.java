package org.puneet.cloudsimplus.hiippo.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BatchProcessor provides memory-efficient batch processing capabilities for large-scale
 * CloudSim experiments. This utility class is specifically designed to handle the
 * memory constraints of 16GB systems while maintaining statistical validity.
 * 
 * <p>The processor implements intelligent batch sizing based on available memory,
 * automatic garbage collection triggers, and progress tracking for long-running
 * experiments.</p>
 * 
 * @author Puneet Research Team
 * @version 1.0.0
 * @since 2024-01-01
 */
public class BatchProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BatchProcessor.class);
    
    /** Default batch size optimized for 16GB systems */
    public static final int DEFAULT_BATCH_SIZE = 10;
    
    /** Maximum batch size to prevent memory overflow */
    public static final int MAX_BATCH_SIZE = 50;
    
    /** Minimum batch size for efficient processing */
    public static final int MIN_BATCH_SIZE = 5;
    
    /** Memory threshold percentage to trigger batch size reduction */
    private static final double MEMORY_THRESHOLD = 0.85;
    
    /** Thread pool size for parallel batch processing */
    private static final int THREAD_POOL_SIZE = Math.max(1, 
        Runtime.getRuntime().availableProcessors() / 2);
    
    private static final ExecutorService executorService = 
        Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    
    /**
     * Processes items in batches using the default batch size.
     * This method provides a simple interface for basic batch processing needs.
     * 
     * @param <T> the type of items to process
     * @param items the list of items to process
     * @param processor the consumer that processes each batch
     * @throws IllegalArgumentException if items or processor is null
     */
    public static <T> void processBatches(List<T> items, Consumer<List<T>> processor) {
        processBatches(items, processor, determineOptimalBatchSize(items.size()));
    }
    
    /**
     * Processes items in batches with a specified batch size.
     * This method provides fine-grained control over batch processing parameters.
     * 
     * @param <T> the type of items to process
     * @param items the list of items to process
     * @param processor the consumer that processes each batch
     * @param batchSize the size of each batch
     * @throws IllegalArgumentException if items or processor is null, or if batchSize is invalid
     */
    public static <T> void processBatches(List<T> items, Consumer<List<T>> processor, int batchSize) {
        validateInputs(items, processor, batchSize);
        
        final int adjustedBatchSize = adjustBatchSizeForMemory(batchSize);
        final int totalItems = items.size();
        final AtomicInteger processedItems = new AtomicInteger(0);
        
        logger.info("Starting batch processing: {} items, batch size: {}", 
            totalItems, adjustedBatchSize);
        
        try {
            for (int i = 0; i < totalItems; i += adjustedBatchSize) {
                int endIndex = Math.min(i + adjustedBatchSize, totalItems);
                List<T> batch = items.subList(i, endIndex);
                
                // Check memory before processing batch
                MemoryManager.checkMemoryUsage(String.format("Batch %d/%d", 
                    (i / adjustedBatchSize) + 1, 
                    (int) Math.ceil((double) totalItems / adjustedBatchSize)));
                
                // Process the batch
                processBatchWithRetry(batch, processor, processedItems, totalItems);
                
                // Clean up after batch if needed
                performPostBatchCleanup();
                
                // Update progress
                logProgress(processedItems.get(), totalItems);
            }
            
            logger.info("Batch processing completed successfully: {} items processed", 
                processedItems.get());
                
        } catch (Exception e) {
            logger.error("Error during batch processing", e);
            throw new RuntimeException("Batch processing failed", e);
        }
    }
    
    /**
     * Processes items in parallel batches for improved performance.
     * This method uses a thread pool to process multiple batches concurrently
     * while maintaining memory safety.
     * 
     * @param <T> the type of items to process
     * @param items the list of items to process
     * @param processor the consumer that processes each batch
     * @param batchSize the size of each batch
     * @throws IllegalArgumentException if items or processor is null
     */
    public static <T> void processBatchesParallel(List<T> items, Consumer<List<T>> processor, 
                                                  int batchSize) {
        validateInputs(items, processor, batchSize);
        
        final int adjustedBatchSize = adjustBatchSizeForMemory(batchSize);
        final int totalItems = items.size();
        final AtomicInteger processedItems = new AtomicInteger(0);
        
        logger.info("Starting parallel batch processing: {} items, batch size: {}, threads: {}", 
            totalItems, adjustedBatchSize, THREAD_POOL_SIZE);
        
        List<CompletableFuture<Void>> futures = new java.util.ArrayList<>();
        
        for (int i = 0; i < totalItems; i += adjustedBatchSize) {
            int endIndex = Math.min(i + adjustedBatchSize, totalItems);
            List<T> batch = items.subList(i, endIndex);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    processBatchWithRetry(batch, processor, processedItems, totalItems);
                    logProgress(processedItems.get(), totalItems);
                } catch (Exception e) {
                    logger.error("Error processing batch", e);
                    throw new RuntimeException("Batch processing failed", e);
                }
            }, executorService);
            
            futures.add(future);
        }
        
        // Wait for all batches to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        logger.info("Parallel batch processing completed: {} items processed", 
            processedItems.get());
    }
    
    /**
     * Determines the optimal batch size based on available memory and item count.
     * 
     * @param totalItems the total number of items to process
     * @return the calculated optimal batch size
     */
    public static int determineOptimalBatchSize(int totalItems) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long availableMemory = maxMemory - usedMemory;
        
        // Estimate memory per item (rough heuristic for CloudSim objects)
        long estimatedMemoryPerItem = 50_000; // 50KB per item
        
        // Calculate maximum possible batch size based on available memory
        long maxPossibleBatchSize = availableMemory / (estimatedMemoryPerItem * 2); // Safety factor
        
        // Determine optimal batch size
        int optimalSize = Math.min(
            Math.max(MIN_BATCH_SIZE, (int) Math.min(maxPossibleBatchSize, DEFAULT_BATCH_SIZE)),
            Math.min(MAX_BATCH_SIZE, totalItems)
        );
        
        logger.debug("Determined optimal batch size: {} (available memory: {} MB)", 
            optimalSize, availableMemory / (1024 * 1024));
        
        return optimalSize;
    }
    
    /**
     * Validates input parameters for batch processing.
     * 
     * @param <T> the type of items
     * @param items the list of items
     * @param processor the batch processor
     * @param batchSize the batch size
     * @throws IllegalArgumentException if any parameter is invalid
     */
    private static <T> void validateInputs(List<T> items, Consumer<List<T>> processor, 
                                           int batchSize) {
        if (items == null) {
            throw new IllegalArgumentException("Items list cannot be null");
        }
        if (processor == null) {
            throw new IllegalArgumentException("Processor cannot be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (batchSize > MAX_BATCH_SIZE) {
            throw new IllegalArgumentException(
                String.format("Batch size %d exceeds maximum %d", batchSize, MAX_BATCH_SIZE));
        }
    }
    
    /**
     * Adjusts batch size based on current memory usage.
     * 
     * @param requestedBatchSize the originally requested batch size
     * @return the adjusted batch size
     */
    private static int adjustBatchSizeForMemory(int requestedBatchSize) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsageRatio = (double) usedMemory / maxMemory;
        
        if (memoryUsageRatio > MEMORY_THRESHOLD) {
            int reducedSize = Math.max(MIN_BATCH_SIZE, requestedBatchSize / 2);
            logger.warn("High memory usage detected ({}%). Reducing batch size from {} to {}", 
                String.format("%.2f", memoryUsageRatio * 100), requestedBatchSize, reducedSize);
            return reducedSize;
        }
        
        return requestedBatchSize;
    }
    
    /**
     * Processes a single batch with retry mechanism for transient failures.
     * 
     * @param <T> the type of items
     * @param batch the batch to process
     * @param processor the batch processor
     * @param processedItems counter for processed items
     * @param totalItems total items to process
     */
    private static <T> void processBatchWithRetry(List<T> batch, Consumer<List<T>> processor,
                                                  AtomicInteger processedItems, int totalItems) {
        final int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                processor.accept(batch);
                processedItems.addAndGet(batch.size());
                return;
            } catch (Exception e) {
                retryCount++;
                logger.warn("Batch processing failed (attempt {}/{}): {}", 
                    retryCount, maxRetries, e.getMessage());
                
                if (retryCount >= maxRetries) {
                    throw new RuntimeException(
                        String.format("Failed to process batch after %d attempts", maxRetries), e);
                }
                
                // Wait before retry
                try {
                    Thread.sleep(1000 * retryCount);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Batch processing interrupted", ie);
                }
                
                // Force garbage collection before retry
                System.gc();
            }
        }
    }
    
    /**
     * Performs cleanup operations after processing a batch.
     */
    private static void performPostBatchCleanup() {
        if (ExperimentConfig.shouldRunGarbageCollection()) {
            logger.debug("Triggering garbage collection after batch");
            System.gc();
            
            // Give GC time to complete
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.warn("Cleanup interrupted", e);
            }
        }
    }
    
    /**
     * Logs progress information during batch processing.
     * 
     * @param processed the number of items processed
     * @param total the total number of items
     */
    private static void logProgress(int processed, int total) {
        if (total > 0) {
            double percentage = (processed * 100.0) / total;
            if (processed % 50 == 0 || percentage >= 100) {
                logger.info("Progress: {}/{} items processed ({:.1f}%)", 
                    processed, total, percentage);
            }
        }
    }
    
    /**
     * Shuts down the thread pool used for parallel processing.
     * This method should be called when the application is shutting down.
     */
    public static void shutdown() {
        logger.info("Shutting down BatchProcessor thread pool");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Gets the current thread pool size for parallel processing.
     * 
     * @return the number of threads in the pool
     */
    public static int getThreadPoolSize() {
        return THREAD_POOL_SIZE;
    }
    
    /**
     * Checks if the system has sufficient memory for the given number of items.
     * 
     * @param itemCount the number of items to process
     * @return true if sufficient memory is available, false otherwise
     */
    public static boolean hasSufficientMemory(int itemCount) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long availableMemory = maxMemory - usedMemory;
        
        // Conservative estimate: 100KB per item
        long requiredMemory = itemCount * 100_000L;
        
        return availableMemory > requiredMemory * 1.5; // 50% safety margin
    }
}