package ir.bita.common.domain;

import ir.bita.common.exception.InvalidIdException;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a Route ID.
 * Ensures the ID is always a positive integer.
 */
public final class RouteId implements Serializable, Comparable<RouteId> {

    private static final long serialVersionUID = 1L;

    private final Long value;

    private RouteId(Long value) {
        this.value = value;
    }

    /**
     * Creates a new RouteId from a Long value.
     *
     * @param value the ID value (must be positive)
     * @return a new RouteId instance
     * @throws InvalidIdException if the value is null, zero, or negative
     */
    public static RouteId of(Long value) {
        if (value == null) {
            throw new InvalidIdException("RouteId", null, "ID cannot be null.");
        }
        if (value <= 0) {
            throw new InvalidIdException("RouteId", value, "ID must be a positive integer.");
        }
        return new RouteId(value);
    }

    /**
     * Creates a new RouteId from an Integer value.
     *
     * @param value the ID value (must be positive)
     * @return a new RouteId instance
     * @throws InvalidIdException if the value is null, zero, or negative
     */
    public static RouteId of(Integer value) {
        if (value == null) {
            throw new InvalidIdException("RouteId", null, "ID cannot be null.");
        }
        return of(value.longValue());
    }

    /**
     * Creates a RouteId from a String value.
     *
     * @param value the string representation of the ID
     * @return a new RouteId instance
     * @throws InvalidIdException if the value cannot be parsed or is invalid
     */
    public static RouteId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdException("RouteId", value, "ID cannot be null or blank.");
        }
        try {
            return of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidIdException("RouteId", value, "ID must be a valid number.");
        }
    }

    public Long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteId routeId = (RouteId) o;
        return Objects.equals(value, routeId.value);
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
    public int compareTo(RouteId other) {
        return this.value.compareTo(other.value);
    }
}
