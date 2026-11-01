package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.combat.KeyBindings.Action;
import net.tkgon.mc.iruuRPG.combat.KeyBindings.Key;
import net.tkgon.mc.iruuRPG.combat.KeyBindings.Pattern;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** t138: the 8 patterns of two keys and the 8 actions they do, each player's own choice. */
class KeyBindingsTest {

    @Test
    void theDefaultsAreTheAskedOnes() {
        assertEquals(new Pattern(Key.F, Key.F), KeyBindings.DEFAULTS.get(Action.SKILL1));
        assertEquals("F→右", KeyBindings.DEFAULTS.get(Action.SKILL2).label(true));
        assertEquals("F→左", KeyBindings.DEFAULTS.get(Action.SKILL2).label(false));
        assertEquals("F→左", KeyBindings.DEFAULTS.get(Action.SKILL3).label(true));
        assertEquals("F→右", KeyBindings.DEFAULTS.get(Action.SKILL3).label(false));
        assertEquals("F→Q", KeyBindings.DEFAULTS.get(Action.SKILL4).label(true));
        assertEquals("Q→右", KeyBindings.DEFAULTS.get(Action.SKILL5).label(true));
        assertEquals("Q→左", KeyBindings.DEFAULTS.get(Action.SKILL5).label(false));
        assertEquals("Q→左", KeyBindings.DEFAULTS.get(Action.SKILL6).label(true));
        assertEquals("Q→F", KeyBindings.DEFAULTS.get(Action.ULTIMATE).label(true));
        assertEquals("Q→Q", KeyBindings.DEFAULTS.get(Action.ITEM).label(true));
        assertEquals(8, new HashSet<>(KeyBindings.DEFAULTS.values()).size());
        assertEquals(List.of("F→F: スキル1 / F→右: スキル2 / F→左: スキル3", "F→Q: スキル4 / Q→右: スキル5 / Q→左: スキル6", "Q→F: 必殺 / Q→Q: アイテムスキル"),
                KeyBindings.describe(KeyBindings.DEFAULTS, true));
        assertEquals(4, Action.SKILL5.heavyIndex());
        assertEquals(5, Action.SKILL6.heavyIndex());
        assertEquals(-1, Action.ITEM.heavyIndex());
    }

    @Test
    void aChoiceSwapsWithTheActionThatHadThePattern() {
        Map<Action, Pattern> bindings = new EnumMap<>(KeyBindings.DEFAULTS);
        KeyBindings.swap(bindings, Action.ITEM, new Pattern(Key.F, Key.F));
        assertEquals(new Pattern(Key.F, Key.F), bindings.get(Action.ITEM));
        assertEquals(new Pattern(Key.Q, Key.Q), bindings.get(Action.SKILL1));
        assertEquals(8, new HashSet<>(bindings.values()).size());
        assertEquals(Action.ITEM, KeyBindings.actionOf(bindings, new Pattern(Key.F, Key.F)));
    }

    @Test
    void theSavedTextGoesBothWays() {
        Map<Action, Pattern> bindings = new EnumMap<>(KeyBindings.DEFAULTS);
        KeyBindings.swap(bindings, Action.SKILL6, new Pattern(Key.F, Key.OTHER));
        String text = KeyBindings.format(bindings);
        assertEquals("FF,QN,FN,FQ,QO,FO,QF,QQ", text);
        assertEquals(bindings, KeyBindings.parse(text));
        assertEquals(KeyBindings.DEFAULTS, KeyBindings.parse("ff,fo,fn,fq,qo,qn,qf,qq"));
        // not 8 different patterns: nothing
        assertNull(KeyBindings.parse("FF,FF,FN,FQ,QO,QN,QF,QQ"));
        assertNull(KeyBindings.parse("FF,FO"));
        assertNull(KeyBindings.parse("FF,FO,FN,FQ,QO,QN,QF,XX"));
        assertNull(KeyBindings.parse(null));
    }
}
