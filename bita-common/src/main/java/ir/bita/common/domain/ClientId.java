package ir.bita.common.domain;

import ir.bita.common.exception.InvalidIdException;

import java.io.Serializable;
import java.util.Objects;

/**
 * Value Object representing a Client ID.
 * Ensures the ID is always a positive integer.
 */
public final class ClientId implements Serializable, Comparable<ClientId> {

    private static final long serialVersionUID = 1L;

    private final Long value;

    private ClientId(Long value) {
        this.value = value;
    }

    /**
     * Creates a new ClientId from a Long value.
     *
     * @param value the ID value (must be positive)
     * @return a new ClientId instance
     * @throws InvalidIdException if the value is null, zero, or negative
     */
    public static ClientId of(Long value) {
        if (value == null) {
            throw new InvalidIdException("ClientId", null, "ID cannot be null.");
        }
        if (value <= 0) {
            throw new InvalidIdException("ClientId", value, "ID must be a positive integer.");
        }
        return new ClientId(value);
    }

    /**
     * Creates a new ClientId from an Integer value.
     *
     * @param value the ID value (must be positive)
     * @return a new ClientId instance
     * @throws InvalidIdException if the value is null, zero, or negative
     */
    public static ClientId of(Integer value) {
        if (value == null) {
            throw new InvalidIdException("ClientId", null, "ID cannot be null.");
        }
        return of(value.longValue());
    }

    /**
     * Creates a ClientId from a String value.
     *
     * @param value the string representation of the ID
     * @return a new ClientId instance
     * @throws InvalidIdException if the value cannot be parsed or is invalid
     */
    public static ClientId fromString(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidIdException("ClientId", value, "ID cannot be null or blank.");
        }
        try {
            return of(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            throw new InvalidIdException("ClientId", value, "ID must be a valid number.");
        }
    }

    public Long getValue() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ClientId clientId = (ClientId) o;
        return Objects.equals(value, clientId.value);
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
    public int compareTo(ClientId other) {
        return this.value.compareTo(other.value);
    }
}
