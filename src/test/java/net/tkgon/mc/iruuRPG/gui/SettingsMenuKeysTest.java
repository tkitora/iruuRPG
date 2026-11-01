package net.tkgon.mc.iruuRPG.gui;

import net.tkgon.mc.iruuRPG.combat.KeyBindings;
import net.tkgon.mc.iruuRPG.combat.KeyBindings.Key;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** t138: the settings of the keys name each pattern as it is pressed with a melee and with a ranged weapon. */
class SettingsMenuKeysTest {

    @Test
    void aPatternWithAButtonNamesBothWeapons() {
        assertEquals("F→F", SettingsMenu.patternText(new KeyBindings.Pattern(Key.F, Key.F)));
        assertEquals("Q→Q", SettingsMenu.patternText(new KeyBindings.Pattern(Key.Q, Key.Q)));
        assertEquals("F→右 (近接) / F→左 (遠距離)", SettingsMenu.patternText(new KeyBindings.Pattern(Key.F, Key.OTHER)));
        assertEquals("Q→左 (近接) / Q→右 (遠距離)", SettingsMenu.patternText(new KeyBindings.Pattern(Key.Q, Key.NORMAL)));
    }
}
