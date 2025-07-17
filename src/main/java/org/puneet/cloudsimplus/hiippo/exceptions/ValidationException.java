package org.puneet.cloudsimplus.hiippo.exceptions;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * General validation exception for the CloudSim HO Research Framework.
 * This exception handles various validation failures including input validation,
 * result validation, configuration validation, and constraint violations.
 * 
 * @author Puneet Chandna
 * @version 1.0.0
 * @since 2025-07-15
 */
public class ValidationException extends Exception implements Serializable {
    
    @Serial
    private static final long serialVersionUID = 1L;
    
    private static final Logger LOGGER = Logger.getLogger(ValidationException.class.getName());
    
    /**
     * Types of validation failures
     */
    public enum ValidationType {
        INPUT_VALIDATION("VAL001", "Input validation failed"),
        OUTPUT_VALIDATION("VAL002", "Output validation failed"),
        CONFIGURATION_VALIDATION("VAL003", "Configuration validation failed"),
        CONSTRAINT_VALIDATION("VAL004", "Constraint validation failed"),
        RANGE_VALIDATION("VAL005", "Value out of valid range"),
        FORMAT_VALIDATION("VAL006", "Invalid format"),
        NULL_VALIDATION("VAL007", "Null value not allowed"),
        SIZE_VALIDATION("VAL008", "Size validation failed"),
        STATE_VALIDATION("VAL009", "Invalid state"),
        DEPENDENCY_VALIDATION("VAL010", "Dependency validation failed"),
        RESOURCE_VALIDATION("VAL011", "Resource validation failed"),
        CONSISTENCY_VALIDATION("VAL012", "Data consistency validation failed"),
        RESULT_VALIDATION("VAL013", "Result validation failed"),
        PRECONDITION_VALIDATION("VAL014", "Precondition validation failed"),
        POSTCONDITION_VALIDATION("VAL015", "Postcondition validation failed");
        
        private final String code;
        private final String description;
        
        ValidationType(String code, String description) {
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
    
    /**
     * Validation error details
     */
    public static class ValidationError implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        
        private final String fieldName;
        private final Object actualValue;
        private final Object expectedValue;
        private final String constraint;
        
        public ValidationError(String fieldName, Object actualValue, 
                             Object expectedValue, String constraint) {
            this.fieldName = fieldName;
            this.actualValue = actualValue;
            this.expectedValue = expectedValue;
            this.constraint = constraint;
        }
        
        public String getFieldName() {
            return fieldName;
        }
        
        public Object getActualValue() {
            return actualValue;
        }
        
        public Object getExpectedValue() {
            return expectedValue;
        }
        
        public String getConstraint() {
            return constraint;
        }
        
        @Override
        public String toString() {
            return String.format("Field '%s': expected %s %s, but got %s",
                    fieldName, constraint, expectedValue, actualValue);
        }
    }
    
    private final ValidationType validationType;
    private final List<ValidationError> validationErrors;
    private final LocalDateTime timestamp;
    private final String validationContext;
    private final Map<String, Object> metadata;
    
    /**
     * Constructs a new ValidationException with type and message.
     * 
     * @param validationType The type of validation that failed
     * @param message The detailed error message
     * @throws NullPointerException if validationType is null
     */
    public ValidationException(ValidationType validationType, String message) {
        this(validationType, message, null, new ArrayList<>(), null, new HashMap<>());
    }
    
    /**
     * Constructs a new ValidationException with type, message, and cause.
     * 
     * @param validationType The type of validation that failed
     * @param message The detailed error message
     * @param cause The underlying cause of the exception
     * @throws NullPointerException if validationType is null
     */
    public ValidationException(ValidationType validationType, String message, Throwable cause) {
        this(validationType, message, null, new ArrayList<>(), cause, new HashMap<>());
    }
    
    /**
     * Constructs a new ValidationException with type, message, and validation errors.
     * 
     * @param validationType The type of validation that failed
     * @param message The detailed error message
     * @param validationErrors List of specific validation errors
     * @throws NullPointerException if validationType is null
     */
    public ValidationException(ValidationType validationType, String message,
                             List<ValidationError> validationErrors) {
        this(validationType, message, null, validationErrors, null, new HashMap<>());
    }
    
    /**
     * Constructs a new ValidationException with all parameters.
     * 
     * @param validationType The type of validation that failed
     * @param message The detailed error message
     * @param validationContext Context where validation occurred
     * @param validationErrors List of specific validation errors
     * @param cause The underlying cause
     * @param metadata Additional metadata for debugging
     * @throws NullPointerException if validationType is null
     */
    public ValidationException(ValidationType validationType, String message,
                             String validationContext, List<ValidationError> validationErrors,
                             Throwable cause, Map<String, Object> metadata) {
        super(formatMessage(validationType, message, validationErrors), cause);
        
        Objects.requireNonNull(validationType, "Validation type cannot be null");
        
        this.validationType = validationType;
        this.validationContext = validationContext;
        this.validationErrors = validationErrors != null ? 
                new ArrayList<>(validationErrors) : new ArrayList<>();
        this.timestamp = LocalDateTime.now();
        this.metadata = metadata != null ? new HashMap<>(metadata) : new HashMap<>();
        
        logException();
    }
    
    /**
     * Creates a validation exception for null value scenarios.
     * 
     * @param fieldName The name of the field that is null
     * @return A new ValidationException configured for null validation
     */
    public static ValidationException nullValue(String fieldName) {
        String message = String.format("Null value not allowed for field '%s'", fieldName);
        List<ValidationError> errors = List.of(
                new ValidationError(fieldName, null, "non-null", "NOT_NULL")
        );
        
        return new ValidationException(ValidationType.NULL_VALIDATION, message, errors);
    }
    
    /**
     * Creates a validation exception for range violation scenarios.
     * 
     * @param fieldName The name of the field
     * @param value The actual value
     * @param minValue The minimum allowed value
     * @param maxValue The maximum allowed value
     * @return A new ValidationException configured for range validation
     */
    public static ValidationException outOfRange(String fieldName, Number value, 
                                               Number minValue, Number maxValue) {
        String message = String.format(
                "Value %s for field '%s' is out of range [%s, %s]",
                value, fieldName, minValue, maxValue
        );
        String expectedRange = String.format("[%s, %s]", minValue, maxValue);
        List<ValidationError> errors = List.of(
                new ValidationError(fieldName, value, expectedRange, "RANGE")
        );
        
        return new ValidationException(ValidationType.RANGE_VALIDATION, message, errors);
    }
    
    /**
     * Creates a validation exception for invalid size scenarios.
     * 
     * @param fieldName The name of the collection field
     * @param actualSize The actual size
     * @param expectedSize The expected size or range
     * @param constraint The size constraint (e.g., "MIN", "MAX", "EXACT")
     * @return A new ValidationException configured for size validation
     */
    public static ValidationException invalidSize(String fieldName, int actualSize,
                                                int expectedSize, String constraint) {
        String message = String.format(
                "Size validation failed for field '%s': actual size %d, expected %s %d",
                fieldName, actualSize, constraint, expectedSize
        );
        List<ValidationError> errors = List.of(
                new ValidationError(fieldName, actualSize, expectedSize, constraint)
        );
        
        return new ValidationException(ValidationType.SIZE_VALIDATION, message, errors);
    }
    
    /**
     * Creates a validation exception for invalid configuration.
     * 
     * @param configName The configuration parameter name
     * @param reason The reason for invalidity
     * @param metadata Additional configuration details
     * @return A new ValidationException configured for configuration validation
     */
    public static ValidationException invalidConfiguration(String configName, 
                                                         String reason, 
                                                         Map<String, Object> metadata) {
        String message = String.format("Invalid configuration '%s': %s", configName, reason);
        ValidationException ex = new ValidationException(
                ValidationType.CONFIGURATION_VALIDATION, message
        );
        if (metadata != null) {
            ex.metadata.putAll(metadata);
        }
        return ex;
    }
    
    /**
     * Creates a validation exception for constraint violations.
     * 
     * @param constraintName The name of the violated constraint
     * @param actualValue The value that violated the constraint
     * @param expectedCondition The expected condition
     * @return A new ValidationException configured for constraint validation
     */
    public static ValidationException constraintViolation(String constraintName,
                                                        Object actualValue,
                                                        String expectedCondition) {
        String message = String.format(
                "Constraint '%s' violated: expected %s, but got %s",
                constraintName, expectedCondition, actualValue
        );
        List<ValidationError> errors = List.of(
                new ValidationError(constraintName, actualValue, expectedCondition, "CONSTRAINT")
        );
        
        return new ValidationException(ValidationType.CONSTRAINT_VALIDATION, message, errors);
    }
    
    /**
     * Creates a validation exception for result validation failures.
     * 
     * @param resultType The type of result being validated
     * @param errors List of validation errors found
     * @return A new ValidationException configured for result validation
     */
    public static ValidationException invalidResult(String resultType, 
                                                  List<ValidationError> errors) {
        String message = String.format(
                "Result validation failed for %s with %d errors",
                resultType, errors.size()
        );
        
        return new ValidationException(ValidationType.RESULT_VALIDATION, message, errors);
    }
    
    /**
     * Formats the exception message with validation details.
     * 
     * @param validationType The validation type
     * @param message The base message
     * @param errors The validation errors
     * @return A formatted error message
     */
    private static String formatMessage(ValidationType validationType, String message,
                                      List<ValidationError> errors) {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(validationType.getCode()).append("] ");
        sb.append(validationType.getDescription());
        
        if (message != null && !message.isEmpty()) {
            sb.append(": ").append(message);
        }
        
        if (errors != null && !errors.isEmpty()) {
            sb.append(" | Errors: ");
            sb.append(errors.size()).append(" validation error(s)");
        }
        
        return sb.toString();
    }
    
    /**
     * Logs the exception details.
     */
    private void logException() {
        if (LOGGER.isLoggable(Level.WARNING)) {
            LOGGER.log(Level.WARNING, String.format(
                    "ValidationException created: [%s] %s at %s",
                    validationType.getCode(),
                    getMessage(),
                    timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            ), this);
            
            if (!validationErrors.isEmpty() && LOGGER.isLoggable(Level.FINE)) {
                LOGGER.fine("Validation errors:");
                for (ValidationError error : validationErrors) {
                    LOGGER.fine("  - " + error);
                }
            }
        }
    }
    
    /**
     * Gets the validation type.
     * 
     * @return The validation type
     */
    public ValidationType getValidationType() {
        return validationType;
    }
    
    /**
     * Gets the list of validation errors.
     * 
     * @return An unmodifiable list of validation errors
     */
    public List<ValidationError> getValidationErrors() {
        return Collections.unmodifiableList(validationErrors);
    }
    
    /**
     * Gets the timestamp when the exception occurred.
     * 
     * @return The timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    /**
     * Gets the validation context.
     * 
     * @return The validation context, may be null
     */
    public String getValidationContext() {
        return validationContext;
    }
    
    /**
     * Gets the metadata associated with this exception.
     * 
     * @return An unmodifiable map of metadata
     */
    public Map<String, Object> getMetadata() {
        return Collections.unmodifiableMap(metadata);
    }
    
    /**
     * Adds a validation error to this exception.
     * 
     * @param error The validation error to add
     */
    public void addValidationError(ValidationError error) {
        if (error != null) {
            validationErrors.add(error);
        }
    }
    
    /**
     * Adds metadata to this exception.
     * 
     * @param key The metadata key
     * @param value The metadata value
     */
    public void addMetadata(String key, Object value) {
        if (key != null) {
            metadata.put(key, value);
        }
    }
    
    /**
     * Checks if this exception has any validation errors.
     * 
     * @return true if there are validation errors, false otherwise
     */
    public boolean hasValidationErrors() {
        return !validationErrors.isEmpty();
    }
    
    /**
     * Gets a detailed string representation for logging.
     * 
     * @return A detailed string representation
     */
    public String getDetailedMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationException Details:\n");
        sb.append("  Type: ").append(validationType.getCode()).append(" - ")
          .append(validationType.getDescription()).append("\n");
        sb.append("  Message: ").append(getMessage()).append("\n");
        sb.append("  Timestamp: ").append(timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append("\n");
        
        if (validationContext != null) {
            sb.append("  Context: ").append(validationContext).append("\n");
        }
        
        if (!validationErrors.isEmpty()) {
            sb.append("  Validation Errors (").append(validationErrors.size()).append("):\n");
            for (ValidationError error : validationErrors) {
                sb.append("    - ").append(error).append("\n");
            }
        }
        
        if (!metadata.isEmpty()) {
            sb.append("  Metadata:\n");
            for (Map.Entry<String, Object> entry : metadata.entrySet()) {
                sb.append("    ").append(entry.getKey()).append(": ")
                  .append(entry.getValue()).append("\n");
            }
        }
        
        if (getCause() != null) {
            sb.append("  Cause: ").append(getCause().getClass().getName())
              .append(" - ").append(getCause().getMessage()).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Checks if this validation error is critical.
     * 
     * @return true if the error is critical, false otherwise
     */
    public boolean isCritical() {
        return switch (validationType) {
            case NULL_VALIDATION, PRECONDITION_VALIDATION, 
                 DEPENDENCY_VALIDATION, STATE_VALIDATION -> true;
            default -> validationErrors.size() > 5; // Many errors indicate critical issue
        };
    }
    
    @Override
    public String toString() {
        return String.format("ValidationException[type=%s, errors=%d, timestamp=%s]: %s",
                validationType.getCode(),
                validationErrors.size(),
                timestamp.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                getMessage());
    }
}