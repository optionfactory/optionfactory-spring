package net.optionfactory.spring.upstream.expressions;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.standard.SpelExpressionParser;

public class OverlayEvaluationContextTest {

    private static OverlayEvaluationContext root() {
        return new Expressions(null, null).context();
    }

    @Test
    public void overlayVariablesShadowTheParentOnes() {
        final var parent = root();
        parent.setVariable("a", "parent");
        parent.setVariable("b", "parent");
        final var overlay = parent.createOverlay("a", "overlay");
        Assertions.assertEquals("overlay", overlay.lookupVariable("a"), "an overlay variable must shadow the parent one");
        Assertions.assertEquals("parent", overlay.lookupVariable("b"), "parent variables must be visible through the overlay");
        Assertions.assertEquals("parent", parent.lookupVariable("a"), "the parent must not see overlay variables");
    }

    @Test
    public void variablesSetOnTheParentAfterwardsAreVisibleInTheOverlay() {
        final var parent = root();
        final var overlay = parent.createOverlay();
        parent.setVariable("late", "value");
        Assertions.assertEquals("value", overlay.lookupVariable("late"), "overlays share the parent maps, not a copy");
    }

    @Test
    public void settingNullRemovesOnlyFromTheTopmostOverlay() {
        final var parent = root();
        parent.setVariable("a", "parent");
        final var overlay = parent.createOverlay("a", "overlay");
        overlay.setVariable("a", null);
        Assertions.assertEquals("parent", overlay.lookupVariable("a"), "a null value cannot hide a parent variable");
        Assertions.assertNull(overlay.lookupVariable("missing"), "unknown variables must resolve to null");
    }

    @Test
    public void assignmentsAreRejected() {
        final var ctx = root();
        final var expression = new SpelExpressionParser().parseExpression("#a = 1");
        Assertions.assertThrows(SpelEvaluationException.class, () -> expression.getValue(ctx), "expressions must not be able to assign variables");
    }

    @Test
    public void typesAndMethodsAreAvailableWithoutABeanFactory() {
        final var ctx = root();
        final var got = new SpelExpressionParser().parseExpression("T(java.lang.Math).max(1, 2)").getValue(ctx);
        Assertions.assertEquals(2, got, "type references and method calls must work without a bean factory");
    }
}
