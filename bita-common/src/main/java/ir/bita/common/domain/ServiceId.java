package ir.bita.common.domain;

import ir.bita.common.exception.InvalidIdException;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a Service ID.
 * Ensures the ID is always a positive integer.
 */
public final class ServiceId implements Serializable, Comparable<ServiceId> {

    private static final long serialVersionUID = 1L;

    private final Long value;

    private ServiceId(Long value) {
        this.value = value;
    }

    /**
     * Creates a new ServiceId from a Long value.
     *
     * @param value the ID value (must be positive)
     * @return a new ServiceId instance
     * @throws InvalidIdException if the value is null, zero, or negative
     */
    public static ServiceId of(Long value) {
        if (value == null) {
            throw new InvalidIdException("ServiceId", null, "ID cannot be null.");
        }
        if (value <= 0) {
            throw new InvalidIdException("ServiceId", value, "ID must be a positive integer.");
        }
        return new ServiceId(value);
    }

    /**
     * Creates a new ServiceId from an Integer value.
     *
     * @param value the ID value (must be positive)
     * @return a new ServiceId instance
     * @throws InvalidIdException if the value is null, zero, or negative
     */
    public static ServiceId of(Integer value) {
        if (value == null) {
            throw new InvalidIdException("ServiceId", null, "ID cannot be null.");
        }
        return of(value.longValue());
    }

    /**
     * Creates a ServiceId from a String value.
     *
     * @param value the string representation of the ID
     * @return a new ServiceId instance
     * @throws InvalidIdException if the value cannot be parsed or is invalid
     */
    public static ServiceId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdException("ServiceId", value, "ID cannot be null or blank.");
        }
        try {
            return of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidIdException("ServiceId", value, "ID must be a valid number.");
        }
    }

    public Long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceId serviceId = (ServiceId) o;
        return Objects.equals(value, serviceId.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }

    @Override
    public int compareTo(ServiceId other) {
        return this.value.compareTo(other.value);
    }
}
