package net.tkgon.mc.iruuRPG.hud;

import net.tkgon.mc.iruuRPG.combat.KeyBindings;
import net.tkgon.mc.iruuRPG.combat.KeyBindings.Action;
import net.tkgon.mc.iruuRPG.markpack.HudGlyphs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** t138: the label over an icon is the keys as they are pressed with the weapon in hand. */
class PlayerHudKeysTest {

    @Test
    void theButtonsOfALabelAreTheWeaponsOwn() {
        // skill 2 = F, the other button: the right one with a melee weapon, the left one with a ranged one
        assertEquals(HudGlyphs.keyLabel(0, 3), PlayerHud.keyGlyph(KeyBindings.DEFAULTS.get(Action.SKILL2), true));
        assertEquals(HudGlyphs.keyLabel(0, 2), PlayerHud.keyGlyph(KeyBindings.DEFAULTS.get(Action.SKILL2), false));
        // skill 6 = Q, the normal button
        assertEquals(HudGlyphs.keyLabel(1, 2), PlayerHud.keyGlyph(KeyBindings.DEFAULTS.get(Action.SKILL6), true));
        assertEquals(HudGlyphs.keyLabel(1, 3), PlayerHud.keyGlyph(KeyBindings.DEFAULTS.get(Action.SKILL6), false));
        assertEquals(HudGlyphs.keyLabel(0, 1), PlayerHud.keyGlyph(KeyBindings.DEFAULTS.get(Action.SKILL4), true));
        assertEquals(HudGlyphs.keyLabel(1, 0), PlayerHud.keyGlyph(KeyBindings.DEFAULTS.get(Action.ULTIMATE), false));
    }
}
