package org.puneet.cloudsimplus.hiippo.unit;

import org.junit.jupiter.api.Test;
import org.puneet.cloudsimplus.hiippo.exceptions.ValidationException;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;

class ValidationExceptionTest {

    @Test
    void testValidationTypeEnum() {
        for (ValidationException.ValidationType type : ValidationException.ValidationType.values()) {
            assertNotNull(type.getCode());
            assertNotNull(type.getDescription());
        }
    }

    @Test
    void testValidationError() {
        ValidationException.ValidationError error = new ValidationException.ValidationError(
                "field", 42, 100, "RANGE");
        assertEquals("field", error.getFieldName());
        assertEquals(42, error.getActualValue());
        assertEquals(100, error.getExpectedValue());
        assertEquals("RANGE", error.getConstraint());
        assertTrue(error.toString().contains("field"));
    }

    @Test
    void testConstructorAndGetters() {
        ValidationException ex = new ValidationException(
                ValidationException.ValidationType.INPUT_VALIDATION, "Input error");
        assertEquals(ValidationException.ValidationType.INPUT_VALIDATION, ex.getValidationType());
        assertNotNull(ex.getTimestamp());
        assertNotNull(ex.toString());
    }

    @Test
    void testNullValueFactory() {
        ValidationException ex = ValidationException.nullValue("testField");
        assertEquals(ValidationException.ValidationType.NULL_VALIDATION, ex.getValidationType());
        assertTrue(ex.getDetailedMessage().contains("Null value not allowed"));
        assertTrue(ex.getValidationErrors().stream().anyMatch(e -> "testField".equals(e.getFieldName())));
    }

    @Test
    void testOutOfRangeFactory() {
        ValidationException ex = ValidationException.outOfRange("size", 5, 10, 20);
        assertEquals(ValidationException.ValidationType.RANGE_VALIDATION, ex.getValidationType());
        assertTrue(ex.getDetailedMessage().contains("out of valid range"));
    }

    @Test
    void testInvalidSizeFactory() {
        ValidationException ex = ValidationException.invalidSize("list", 2, 3, "EQUALS");
        assertEquals(ValidationException.ValidationType.SIZE_VALIDATION, ex.getValidationType());
        assertTrue(ex.getDetailedMessage().contains("invalid size"));
    }

    @Test
    void testInvalidConfigurationFactory() {
        ValidationException ex = ValidationException.invalidConfiguration("config", "bad", Map.of("k", "v"));
        assertEquals(ValidationException.ValidationType.CONFIGURATION_VALIDATION, ex.getValidationType());
        assertTrue(ex.getDetailedMessage().contains("invalid configuration"));
    }

    @Test
    void testConstraintViolationFactory() {
        ValidationException ex = ValidationException.constraintViolation("constraint", 1, "must be > 0");
        assertEquals(ValidationException.ValidationType.CONSTRAINT_VALIDATION, ex.getValidationType());
        assertTrue(ex.getDetailedMessage().contains("constraint violation"));
    }

    @Test
    void testInvalidResultFactory() {
        ValidationException.ValidationError error = new ValidationException.ValidationError("f", 1, 2, "EQUALS");
        ValidationException ex = ValidationException.invalidResult("type", List.of(error));
        assertEquals(ValidationException.ValidationType.RESULT_VALIDATION, ex.getValidationType());
        assertTrue(ex.getDetailedMessage().contains("invalid result"));
    }

    @Test
    void testIsCritical() {
        ValidationException ex = new ValidationException(
                ValidationException.ValidationType.NULL_VALIDATION, "Critical");
        assertTrue(ex.isCritical());
        ValidationException ex2 = new ValidationException(
                ValidationException.ValidationType.INPUT_VALIDATION, "Not critical");
        assertFalse(ex2.isCritical());
    }

    @Test
    void testToString() {
        ValidationException ex = new ValidationException(
                ValidationException.ValidationType.FORMAT_VALIDATION, "Format error");
        String str = ex.toString();
        assertTrue(str.contains("ValidationException"));
        assertTrue(str.contains("FORMAT_VALIDATION"));
    }
} 