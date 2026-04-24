package io.openharness4j.llm;

public class LLMAdapterException extends RuntimeException {

    public LLMAdapterException(String message) {
        super(message);
    }

    public LLMAdapterException(String message, Throwable cause) {
        super(message, cause);
    }
}
