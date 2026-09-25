package ir.bita.common.domain;

/**
 * Type of a Groovy template variable.
 * PORT type additionally maps to K8s container ports and Ingress rules.
 */
public enum VariableType {
    STRING,
    SECRET,
    INT,
    BOOLEAN,
    PORT
}
