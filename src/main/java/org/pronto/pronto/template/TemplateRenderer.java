package org.pronto.pronto.template;

import org.apache.commons.lang3.StringUtils;
import org.pronto.pronto.entity.MessageTemplate;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.exception.BadRequestException;
import org.pronto.pronto.exception.MissingTemplateVariableException;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Renders {{placeholders}} in a template. System variables come from the DB and can't be overridden. */
@Component
public class TemplateRenderer {

    public static final String USER_NAME = "user_name";
    public static final String USER_CITY = "user_city";
    public static final String USER_ID = "user_id";
    public static final String RENDERED_MESSAGE_TOO_LONG = "RENDERED_MESSAGE_TOO_LONG";

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*\\}\\}");
    // Column sizes of the notifications table.
    private static final int MAX_TITLE = 300;
    private static final int MAX_BODY = 2000;
    private static final int MAX_DEEP_LINK = 1000;

    public RenderedMessage render(MessageTemplate template, User user, Map<String, Object> variables) {
        Map<String, String> values = valuesOf(user, variables);
        List<String> missing = variablesOf(template).stream().filter(name -> !values.containsKey(name)).toList();
        if (!missing.isEmpty()) {
            throw new MissingTemplateVariableException(missing);
        }
        RenderedMessage message = RenderedMessage.builder()
                .title(fill(template.getTitle(), values))
                .body(fill(template.getBody(), values))
                .deepLink(fill(template.getDeepLink(), values))
                .build();
        checkLength("title", message.getTitle(), MAX_TITLE);
        checkLength("body", message.getBody(), MAX_BODY);
        checkLength("deepLink", message.getDeepLink(), MAX_DEEP_LINK);
        return message;
    }

    /** Distinct placeholder names across title, body and deep link, in order of appearance. */
    public List<String> variablesOf(MessageTemplate template) {
        Set<String> names = new LinkedHashSet<>();
        Stream.of(template.getTitle(), template.getBody(), template.getDeepLink())
                .filter(StringUtils::isNotEmpty)
                .forEach(text -> PLACEHOLDER.matcher(text).results().forEach(match -> names.add(match.group(1))));
        return List.copyOf(names);
    }

    private static Map<String, String> valuesOf(User user, Map<String, Object> variables) {
        Map<String, String> values = new HashMap<>();
        variables.forEach((name, value) -> {
            if (value != null) {
                values.put(name, String.valueOf(value));
            }
        });
        values.put(USER_NAME, user.getName());
        values.put(USER_ID, String.valueOf(user.getId()));
        values.remove(USER_CITY);
        if (StringUtils.isNotEmpty(user.getCity())) {
            values.put(USER_CITY, user.getCity());
        }
        return values;
    }

    private static String fill(String text, Map<String, String> values) {
        if (text == null) {
            return null;
        }
        return PLACEHOLDER.matcher(text).replaceAll(match -> Matcher.quoteReplacement(values.get(match.group(1))));
    }

    private static void checkLength(String field, String text, int max) {
        int length = StringUtils.length(text);
        if (length > max) {
            throw new BadRequestException(RENDERED_MESSAGE_TOO_LONG,
                    "rendered " + field + " is " + length + " characters, max " + max);
        }
    }
}
