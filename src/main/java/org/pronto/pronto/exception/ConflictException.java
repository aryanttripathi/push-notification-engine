package org.pronto.pronto.exception;

import lombok.Getter;

@Getter
public class ConflictException extends RuntimeException {

    public static final String CONFLICT = "CONFLICT";

    private final String code;

    public ConflictException(String message) {
        this(CONFLICT, message);
    }

    public ConflictException(String code, String message) {
        super(message);
        this.code = code;
    }
}
