package ir.bita.esm.llm.entity;

/**
 * Status of a tool execution.
 */
public enum ToolExecutionStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    EXECUTED,
    FAILED
}
