package org.pronto.pronto.exception;

import lombok.Getter;

/** A 400 with a machine-readable code, e.g. RENDERED_MESSAGE_TOO_LONG. */
@Getter
public class BadRequestException extends RuntimeException {

    private final String code;

    public BadRequestException(String code, String message) {
        super(message);
        this.code = code;
    }
}
