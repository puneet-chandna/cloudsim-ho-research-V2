package org.puneet.cloudsimplus.hiippo.exceptions;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Custom exception class for Hippopotamus Optimization Algorithm specific errors.
 * This exception provides detailed error information including error codes, 
 * context, and timestamps for debugging and logging purposes.
 * 
 * @author CloudSim Plus HO Research Framework
 * @version 1.0.0
 * @since CloudSim Plus 8.0.0
 */
public class HippopotamusOptimizationException extends Exception implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(HippopotamusOptimizationException.class.getName());
    
    /**
     * Error codes for different types of Hippopotamus Optimization failures
     */
    public enum ErrorCode {
        INVALID_PARAMETER("HO001", "Invalid algorithm parameter"),
        CONVERGENCE_FAILURE("HO002", "Algorithm failed to converge"),
        ALLOCATION_FAILURE("HO003", "VM allocation failed"),
        MEMORY_CONSTRAINT("HO004", "Memory constraint violation"),
        INVALID_POPULATION("HO005", "Invalid population configuration"),
        FITNESS_CALCULATION("HO006", "Error in fitness calculation"),
        POSITION_UPDATE("HO007", "Error in position update"),
        INITIALIZATION_FAILURE("HO008", "Algorithm initialization failed"),
        INVALID_VM_CONFIG("HO009", "Invalid VM configuration"),
        INVALID_HOST_CONFIG("HO010", "Invalid host configuration"),
        RESOURCE_OVERFLOW("HO011", "Resource capacity exceeded"),
        NULL_POINTER("HO012", "Null value encountered"),
        INVALID_SOLUTION("HO013", "Invalid solution generated"),
        TIMEOUT("HO014", "Algorithm execution timeout"),
        UNKNOWN("HO999", "Unknown error");
        
        private final String code;
        private final String description;
        
        ErrorCode(String code, String description) {
            this.code = code;
            this.description = description;
        }
        
        public String getCode() {
            return code;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final ErrorCode errorCode;
    private final String context;
    private final LocalDateTime timestamp;
    private final String additionalInfo;
    
    /**
     * Constructs a new HippopotamusOptimizationException with error code and message.
     * 
     * @param errorCode The specific error code
     * @param message The detailed error message
     * @throws NullPointerException if errorCode is null
     */
    public HippopotamusOptimizationException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null, null);
    }
    
    /**
     * Constructs a new HippopotamusOptimizationException with error code, message, and cause.
     * 
     * @param errorCode The specific error code
     * @param message The detailed error message
     * @param cause The underlying cause of the exception
     * @throws NullPointerException if errorCode is null
     */
    public HippopotamusOptimizationException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause, null);
    }
    
    /**
     * Constructs a new HippopotamusOptimizationException with error code, message, and context.
     * 
     * @param errorCode The specific error code
     * @param message The detailed error message
     * @param context Additional context information (e.g., iteration number, VM ID)
     * @throws NullPointerException if errorCode is null
     */
    public HippopotamusOptimizationException(ErrorCode errorCode, String message, String context) {
        this(errorCode, message, context, null, null);
    }
    
    /**
     * Constructs a new HippopotamusOptimizationException with all parameters.
     * 
     * @param errorCode The specific error code
     * @param message The detailed error message
     * @param context Additional context information
     * @param cause The underlying cause of the exception
     * @param additionalInfo Any additional information for debugging
     * @throws NullPointerException if errorCode is null
     */
    public HippopotamusOptimizationException(ErrorCode errorCode, String message, 
                                           String context, Throwable cause, 
                                           String additionalInfo) {
        super(formatMessage(errorCode, message, context), cause);
        
        Objects.requireNonNull(errorCode, "Error code cannot be null");
        
        this.errorCode = errorCode;
        this.context = context;
        this.timestamp = LocalDateTime.now();
        this.additionalInfo = additionalInfo;
        
        // Log the exception creation
        logException();
    }
    
    /**
     * Creates an exception for invalid parameter scenarios.
     * 
     * @param parameterName The name of the invalid parameter
     * @param value The invalid value
     * @param expectedRange The expected range or constraint
     * @return A new HippopotamusOptimizationException configured for invalid parameter
     */
    public static HippopotamusOptimizationException invalidParameter(
            String parameterName, Object value, String expectedRange) {
        
        String message = String.format("Invalid parameter '%s' with value '%s'. Expected: %s",
                parameterName, value, expectedRange);
        String context = String.format("Parameter: %s", parameterName);
        
        return new HippopotamusOptimizationException(
                ErrorCode.INVALID_PARAMETER, message, context);
    }
    
    /**
     * Creates an exception for convergence failure scenarios.
     * 
     * @param iterations The number of iterations attempted
     * @param threshold The convergence threshold
     * @param finalError The final error value
     * @return A new HippopotamusOptimizationException configured for convergence failure
     */
    public static HippopotamusOptimizationException convergenceFailure(
            int iterations, double threshold, double finalError) {
        
        String message = String.format(
                "Algorithm failed to converge after %d iterations. Threshold: %.6f, Final error: %.6f",
                iterations, threshold, finalError);
        String context = String.format("Iterations: %d", iterations);
        
        return new HippopotamusOptimizationException(
                ErrorCode.CONVERGENCE_FAILURE, message, context);
    }
    
    /**
     * Creates an exception for VM allocation failure scenarios.
     * 
     * @param vmId The ID of the VM that failed to allocate
     * @param reason The reason for allocation failure
     * @return A new HippopotamusOptimizationException configured for allocation failure
     */
    public static HippopotamusOptimizationException allocationFailure(long vmId, String reason) {
        String message = String.format("Failed to allocate VM %d: %s", vmId, reason);
        String context = String.format("VM ID: %d", vmId);
        
        return new HippopotamusOptimizationException(
                ErrorCode.ALLOCATION_FAILURE, message, context);
    }
    
    /**
     * Creates an exception for memory constraint violations.
     * 
     * @param required The required memory
     * @param available The available memory
     * @param unit The memory unit (e.g., "MB", "GB")
     * @return A new HippopotamusOptimizationException configured for memory constraint
     */
    public static HippopotamusOptimizationException memoryConstraint(
            long required, long available, String unit) {
        
        String message = String.format(
                "Memory constraint violation. Required: %d %s, Available: %d %s",
                required, unit, available, unit);
        String context = "Memory allocation";
        
        return new HippopotamusOptimizationException(
                ErrorCode.MEMORY_CONSTRAINT, message, context);
    }
    
    /**
     * Formats the exception message with all available information.
     * 
     * @param errorCode The error code
     * @param message The base message
     * @param context The context information
     * @return A formatted error message
     */
    private static String formatMessage(ErrorCode errorCode, String message, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(errorCode.getCode()).append("] ");
        sb.append(errorCode.getDescription());
        
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        
        if (context != null && !context.isEmpty()) {
            sb.append(" | Context: ").append(context);
        }
        
        return sb.toString();
    }
    
    /**
     * Logs the exception details for debugging purposes.
     */
    private void logException() {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(Level.WARNING, String.format(
                    "HippopotamusOptimizationException created: [%s] %s at %s",
                    errorCode.getCode(),
                    getMessage(),
                    timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ), this);
        }
    }
    
    /**
     * Gets the error code associated with this exception.
     * 
     * @return The error code
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
    
    /**
     * Gets the context information associated with this exception.
     * 
     * @return The context string, may be null
     */
    public String getContext() {
        return context;
    }
    
    /**
     * Gets the timestamp when this exception was created.
     * 
     * @return The creation timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets any additional information associated with this exception.
     * 
     * @return The additional information string, may be null
     */
    public String getAdditionalInfo() {
        return additionalInfo;
    }
    
    /**
     * Gets a detailed string representation of this exception for logging.
     * 
     * @return A detailed string representation
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("HippopotamusOptimizationException Details:\n");
        sb.append("  Error Code: ").append(errorCode.getCode()).append("\n");
        sb.append("  Description: ").append(errorCode.getDescription()).append("\n");
        sb.append("  Message: ").append(getMessage()).append("\n");
        sb.append("  Timestamp: ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        
        if (context != null) {
            sb.append("  Context: ").append(context).append("\n");
        }
        
        if (additionalInfo != null) {
            sb.append("  Additional Info: ").append(additionalInfo).append("\n");
        }
        
        if (getCause() != null) {
            sb.append("  Cause: ").append(getCause().getClass().getName())
              .append(" - ").append(getCause().getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if this exception represents a critical error that should stop execution.
     * 
     * @return true if the error is critical, false otherwise
     */
    public boolean isCritical() {
        return switch (errorCode) {
            case MEMORY_CONSTRAINT, INITIALIZATION_FAILURE, 
                 NULL_POINTER, TIMEOUT -> true;
            default -> false;
        };
    }
    
    /**
     * Checks if this exception represents a recoverable error.
     * 
     * @return true if the error is recoverable, false otherwise
     */
    public boolean isRecoverable() {
        return switch (errorCode) {
            case ALLOCATION_FAILURE, CONVERGENCE_FAILURE, 
                 INVALID_SOLUTION -> true;
            default -> false;
        };
    }
    
    @Override
    public String toString() {
        return String.format("HippopotamusOptimizationException[code=%s, timestamp=%s]: %s",
                errorCode.getCode(),
                timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                getMessage());
    }
}