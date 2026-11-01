package net.tkgon.mc.iruuRPG.hud;

import net.tkgon.mc.iruuRPG.combat.SkillSlots.Slot;
import net.tkgon.mc.iruuRPG.markpack.HudGlyphs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The label over an icon is the keys as they are pressed with the weapon in hand (the redesign of 2026-11-02). */
class PlayerHudKeysTest {

    @Test
    void theButtonsOfALabelAreTheWeaponsOwn() {
        // 小 = Q, the normal button: the left one with a melee weapon, the right one with a ranged one; 中 the other
        assertEquals(HudGlyphs.keyLabel("ql"), PlayerHud.keyGlyph(Slot.SMALL, true));
        assertEquals(HudGlyphs.keyLabel("qr"), PlayerHud.keyGlyph(Slot.SMALL, false));
        assertEquals(HudGlyphs.keyLabel("qr"), PlayerHud.keyGlyph(Slot.MEDIUM, true));
        assertEquals(HudGlyphs.keyLabel("ql"), PlayerHud.keyGlyph(Slot.MEDIUM, false));
        assertEquals(HudGlyphs.keyLabel("qq"), PlayerHud.keyGlyph(Slot.LARGE, false));
        assertEquals(HudGlyphs.keyLabel("f"), PlayerHud.keyGlyph(Slot.UTIL, true));
        assertEquals(HudGlyphs.keyLabel("sf"), PlayerHud.keyGlyph(Slot.ULT, false));
        // the weapon's skill: its other button
        assertEquals(HudGlyphs.keyLabel("r"), PlayerHud.itemKeyGlyph(true));
        assertEquals(HudGlyphs.keyLabel("l"), PlayerHud.itemKeyGlyph(false));
    }
}
