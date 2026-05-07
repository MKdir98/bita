package ir.bita.common.exception;

/**
 * Exception thrown when an invalid ID value is provided.
 * IDs must be positive integers.
 */
public class InvalidIdException extends RuntimeException {

    private final String idType;
    private final Object value;

    public InvalidIdException(String idType, Object value) {
        super(String.format("Invalid %s: %s. ID must be a positive integer.", idType, value));
        this.idType = idType;
        this.value = value;
    }

    public InvalidIdException(String idType, Object value, String reason) {
        super(String.format("Invalid %s: %s. %s", idType, value, reason));
        this.idType = idType;
        this.value = value;
    }

    public String getIdType() {
        return idType;
    }

    public Object getValue() {
        return value;
    }
}
