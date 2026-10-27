package org.pronto.pronto.template;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.entity.MessageTemplate;
import org.pronto.pronto.entity.User;
import org.pronto.pronto.exception.BadRequestException;
import org.pronto.pronto.exception.MissingTemplateVariableException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateRendererTest {

    private final TemplateRenderer renderer = new TemplateRenderer();

    @Test
    void rendersSystemAndRequestVariablesInTitleBodyAndDeepLink() {
        RenderedMessage message = renderer.render(
                template("Hi {{user_name}}", "Order #{{ order_id }} by {{eta}} in {{user_city}}", "app://o/{{order_id}}"),
                user("Bengaluru"), Map.of("order_id", "A1", "eta", "5pm"));

        assertThat(message.getTitle()).isEqualTo("Hi Aryant");
        assertThat(message.getBody()).isEqualTo("Order #A1 by 5pm in Bengaluru");
        assertThat(message.getDeepLink()).isEqualTo("app://o/A1");
    }

    @Test
    void requestCannotOverrideSystemVariables() {
        RenderedMessage message = renderer.render(template("Hi {{user_name}} #{{user_id}}", "b", null),
                user("Bengaluru"), Map.of("user_name", "Mallory", "user_id", "999"));

        assertThat(message.getTitle()).isEqualTo("Hi Aryant #7");
        assertThatThrownBy(() -> renderer.render(template("t", "in {{user_city}}", null), user(null),
                Map.of("user_city", "Spoofed")))
                .isInstanceOf(MissingTemplateVariableException.class);
    }

    @Test
    void reportsEveryMissingVariable() {
        assertThatThrownBy(() -> renderer.render(
                template("Hi {{user_name}}", "Order #{{order_id}} by {{eta}}", "app://o/{{order_id}}"),
                user("Bengaluru"), Map.of()))
                .isInstanceOfSatisfying(MissingTemplateVariableException.class,
                        e -> assertThat(e.getMissing()).containsExactly("order_id", "eta"));
    }

    @Test
    void valuesAreInsertedLiterally() {
        RenderedMessage message = renderer.render(template("t", "Flat {{discount}} off", null),
                user("Bengaluru"), Map.of("discount", "$40 \\1"));

        assertThat(message.getBody()).isEqualTo("Flat $40 \\1 off");
    }

    @Test
    void renderedTextOverColumnLimitIsRejected() {
        assertThatThrownBy(() -> renderer.render(template("t", "Order {{order_id}}", null),
                user("Bengaluru"), Map.of("order_id", "x".repeat(2000))))
                .isInstanceOfSatisfying(BadRequestException.class,
                        e -> assertThat(e.getCode()).isEqualTo(TemplateRenderer.RENDERED_MESSAGE_TOO_LONG));
    }

    @Test
    void listsRequiredVariablesOnce() {
        assertThat(renderer.variablesOf(template("{{a}} {{ b }}", "{{a}}", "x/{{c}}"))).containsExactly("a", "b", "c");
    }

    private static MessageTemplate template(String title, String body, String deepLink) {
        MessageTemplate template = new MessageTemplate();
        template.setTitle(title);
        template.setBody(body);
        template.setDeepLink(deepLink);
        return template;
    }

    private static User user(String city) {
        User user = new User();
        user.setId(7L);
        user.setName("Aryant");
        user.setCity(city);
        return user;
    }
}
