package net.tkgon.mc.iruuRPG.hud;

import net.tkgon.mc.iruuRPG.combat.SkillSlots;
import net.tkgon.mc.iruuRPG.combat.SkillSlots.Slot;
import net.tkgon.mc.iruuRPG.markpack.HudGlyphs;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The label over an icon is the keys as they are pressed with the weapon in hand (the redesign of 2026-11-02). */
class PlayerHudKeysTest {

    @Test
    void theButtonsOfALabelAreTheWeaponsOwn() {
        // the default of t164: F → left / right / F, util the other button, shift + Q the ULT, Q the weapon's skill
        assertEquals(HudGlyphs.keyLabel("fl"), PlayerHud.keyGlyph(Slot.SMALL));
        assertEquals(HudGlyphs.keyLabel("fr"), PlayerHud.keyGlyph(Slot.MEDIUM));
        assertEquals(HudGlyphs.keyLabel("ff"), PlayerHud.keyGlyph(Slot.LARGE));
        assertEquals(HudGlyphs.keyLabel("r"), PlayerHud.keyGlyph(Slot.UTIL, false, true));
        assertEquals(HudGlyphs.keyLabel("l"), PlayerHud.keyGlyph(Slot.UTIL, false, false));
        assertEquals(HudGlyphs.keyLabel("sq"), PlayerHud.keyGlyph(Slot.ULT));
        assertEquals(HudGlyphs.keyLabel("q"), PlayerHud.itemKeyGlyph(false));
        // Q and F swapped (t162)
        assertEquals(HudGlyphs.keyLabel("ql"), PlayerHud.keyGlyph(Slot.SMALL, true, true));
        assertEquals(HudGlyphs.keyLabel("qr"), PlayerHud.keyGlyph(Slot.MEDIUM, true, false));
        assertEquals(HudGlyphs.keyLabel("qq"), PlayerHud.keyGlyph(Slot.LARGE, true, true));
        assertEquals(HudGlyphs.keyLabel("sf"), PlayerHud.keyGlyph(Slot.ULT, true, true));
        assertEquals(HudGlyphs.keyLabel("f"), PlayerHud.itemKeyGlyph(true));
    }

    /** t158: a skill is greyed with the seconds in its middle only while it cannot be used (no charge, no open re-activation). */
    @Test
    void anIconIsGreyedOnlyWhileItsSkillCannotBeUsed() {
        char glyph = HudGlyphs.icon("mage", 1);
        // a charge left: no shade, its charges and the refill show
        HudGlyphs.Icon some = PlayerHud.icon(glyph, new SkillSlots.View(new long[]{0L, 0L}, 1, 3, 0.5, false), (char) 0, 0xFFFFFF);
        assertEquals(0.0, some.coolingShare());
        assertEquals(1, some.charges());
        // no charge and no re-activation: greyed, shaded, the seconds to the next charge in its middle
        HudGlyphs.Icon none = PlayerHud.icon(glyph, new SkillSlots.View(new long[]{2500L, 5000L}, 0, 3, 0.5, false), (char) 0, 0xFFFFFF);
        assertEquals(0.5, none.coolingShare());
        assertEquals(3, none.seconds());
        assertEquals(0, none.charges());
        assertEquals(0.5, none.refill());
        // no charge but its re-activation open: it can be used, no shade, the rim
        HudGlyphs.Icon recast = PlayerHud.icon(glyph, new SkillSlots.View(new long[]{2500L, 5000L}, 0, 3, 0.5, true), (char) 0, 0xFFFFFF);
        assertEquals(0.0, recast.coolingShare());
        assertTrue(recast.recast());
        // a skill of one charge: its cooldown as before, but not while its re-activation is open
        assertEquals(0.4, PlayerHud.icon(glyph, new SkillSlots.View(new long[]{4000L, 10_000L}, 0, 1, 0.6, false), (char) 0, 0xFFFFFF).coolingShare(), 1e-9);
        assertEquals(0.0, PlayerHud.icon(glyph, new SkillSlots.View(new long[]{4000L, 10_000L}, 0, 1, 0.6, true), (char) 0, 0xFFFFFF).coolingShare());
        // nothing on it: locked
        assertTrue(PlayerHud.icon(glyph, new SkillSlots.View(null, 1, 1, -1.0, false), (char) 0, 0xFFFFFF).locked());
    }
}
