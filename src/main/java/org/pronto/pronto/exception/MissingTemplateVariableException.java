package org.pronto.pronto.exception;

import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Getter
public class MissingTemplateVariableException extends RuntimeException {

    private final List<String> missing;

    public MissingTemplateVariableException(List<String> missing) {
        super("missing template variables: " + StringUtils.join(missing, ", "));
        this.missing = List.copyOf(missing);
    }
}
