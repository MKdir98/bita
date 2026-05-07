package ir.bita.esm.llm.validation;

/**
 * Exception thrown when LLM response validation fails.
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }

    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
