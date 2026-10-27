package org.pronto.pronto.engine;

import org.junit.jupiter.api.Test;
import org.pronto.pronto.exception.InvalidRuleException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExpressionCompilerTest {

    private final ExpressionCompiler compiler = new ExpressionCompiler();

    @Test
    void acceptsBooleanExpressionsOverFacts() {
        String[] valid = {
            "localHour >= 22 or localHour < 8",
            "marketingSentToday >= 3",
            "message.campaignCode != null and hoursSinceLastSameCampaign < 24",
            "user.city.startsWith('BLR') or localDayOfWeek == 'SUNDAY'",
            "variables['cartValue'] != null and variables['cartValue'] < 199",
            "variables['name']?.length() > 3",
            "user.city.startsWith('Ben')",
            "{1,2}.contains(user.hubId)",
            "variables.containsKey('tier') or user.name.toLowerCase().trim().isEmpty()",
        };
        for (String expression : valid) {
            assertThatCode(() -> compiler.validate(expression)).as(expression).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsSandboxEscapes() {
        String[] escapes = {
            "T(java.lang.Runtime).getRuntime().exec('id') != null",
            "new java.io.File('x').exists()",
            "''.getClass() != null",
            "user.getClass().getName() == 'x'",
            "''.class != null",
            "user.class.name == 'x'",
            "@decisionService != null",
            "variables['x'] = 1",
            "'a'.repeat(100000000).length() > 1",
            "user.name.replaceAll('(a+)+$', '').isEmpty()",
            "user.name.matches('x')",
            "user.name.split(',').length > 0",
        };
        for (String expression : escapes) {
            assertThatThrownBy(() -> compiler.validate(expression)).as(expression)
                    .isInstanceOf(InvalidRuleException.class);
        }
    }

    @Test
    void rejectsNonBooleanSyntaxErrorsUnguardedVariablesAndOversizedInput() {
        String[] invalid = {"localHour + 1", "localHour >", "variables['x'].length() > 1", "", "true or ".repeat(200)};
        for (String expression : invalid) {
            assertThatThrownBy(() -> compiler.validate(expression)).as(expression)
                    .isInstanceOf(InvalidRuleException.class);
        }
    }
}
