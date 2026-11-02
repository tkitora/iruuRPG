package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The keys of the skills (the redesign of 2026-11-02, the default of t164): the first key (F) and then the left button (小), the right one (中)
 * or the first key again (大); the second key (Q) the weapon's skill, shift + it the ULT; the other button alone util.
 */
class SkillInputTest {

    private static final SkillInput.Button N = SkillInput.Button.NORMAL;
    private static final SkillInput.Button O = SkillInput.Button.OTHER;

    /** The plugin of SkillInput is static: each test sets its own (with the default config). */
    private static void plugin() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        new SkillInput(plugin);
    }

    private static SkillInput.Click skill(SkillSlots.Slot slot) {
        return new SkillInput.Click(SkillInput.Kind.SKILL, slot);
    }

    @Test
    void qAndASecondKeyChooseTheSkill() {
        plugin();
        UUID id = UUID.randomUUID();
        // Q waits; then the normal button is 小, the other 中, Q again 大
        assertNull(SkillInput.pressFirst(id, 10));
        assertEquals(skill(SkillSlots.Slot.SMALL), SkillInput.click(id, N, false, 15));
        assertNull(SkillInput.pressFirst(id, 20));
        assertEquals(skill(SkillSlots.Slot.MEDIUM), SkillInput.click(id, O, true, 22));
        assertNull(SkillInput.pressFirst(id, 30));
        assertEquals(SkillSlots.Slot.LARGE, SkillInput.pressFirst(id, 31));
        // the second key is the weapon's skill, shift + it the ULT, at once; a first key that waited is let go
        assertEquals(SkillInput.Kind.ITEM_SKILL, SkillInput.pressSecond(id, false).kind());
        assertEquals(skill(SkillSlots.Slot.ULT), SkillInput.pressSecond(id, true));
        assertNull(SkillInput.pressFirst(id, 40));
        assertEquals(SkillInput.Kind.ITEM_SKILL, SkillInput.pressSecond(id, false).kind());
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, false, 41).kind());
        // too late: the second Q is a new first one, the click what it is alone
        assertNull(SkillInput.pressFirst(id, 100));
        assertNull(SkillInput.pressFirst(id, 121));
        assertEquals(SkillSlots.Slot.LARGE, SkillInput.pressFirst(id, 122));
        assertNull(SkillInput.pressFirst(id, 200));
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, false, 221).kind());
        // with no first key before it: the normal button is the normal attack, the other util (t164); the same click again in its tick is nothing
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, false, 300).kind());
        assertEquals(SkillInput.Kind.NOTHING, SkillInput.click(id, N, false, 300).kind());
        assertEquals(skill(SkillSlots.Slot.UTIL), SkillInput.click(id, O, true, 310));
        SkillInput.forget(id);
    }

    @Test
    void theKeysOfTheSkillsAreTheSameWithARangedWeapon() {
        // t160: with a ranged weapon the right button is the normal one, yet Q → left is 小 and Q → right 中, as with a melee weapon
        plugin();
        UUID id = UUID.randomUUID();
        assertNull(SkillInput.pressFirst(id, 10));
        assertEquals(skill(SkillSlots.Slot.SMALL), SkillInput.click(id, O, false, 12));
        assertNull(SkillInput.pressFirst(id, 20));
        assertEquals(skill(SkillSlots.Slot.MEDIUM), SkillInput.click(id, N, true, 22));
        // with no first key: the right button is its normal attack, the left one util
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, true, 40).kind());
        assertEquals(skill(SkillSlots.Slot.UTIL), SkillInput.click(id, O, false, 50));
        SkillInput.forget(id);
    }

    @Test
    void aHeldOtherButtonIsOnePressOfUtil() {
        plugin();
        UUID id = UUID.randomUUID();
        int cast = 0;
        // the client repeats a held button every 4 ticks
        for (int tick = 0; tick < 40; tick += 4) if (skill(SkillSlots.Slot.UTIL).equals(SkillInput.click(id, O, true, tick))) cast++;
        assertEquals(1, cast);
        // let go, and pressed again
        assertEquals(skill(SkillSlots.Slot.UTIL), SkillInput.click(id, O, true, 60));
        // Q, and the other button is 中 even right after it
        assertNull(SkillInput.pressFirst(id, 62));
        assertEquals(skill(SkillSlots.Slot.MEDIUM), SkillInput.click(id, O, true, 63));
        SkillInput.forget(id);
    }

    @Test
    void theSwingThatComesWithQIsNotItsSecondKey() {
        // t144: the client swings the main hand with every drop (Q); the server would take it for a left click in the air in the same tick
        plugin();
        UUID id = UUID.randomUUID();
        assertNull(SkillInput.pressFirst(id, 100));
        ClickSignals.dropKey(id, 100);
        // the swing in the drop's tick, or the next two, is no click: the listener does not hand it to the keys, so Q waits on
        assertFalse(ClickSignals.leftPossible(id, 100));
        assertFalse(ClickSignals.leftPossible(id, 102));
        // the second Q is Q, Q (大), and its own swing is no click either
        ClickSignals.dropKey(id, 104);
        assertEquals(SkillSlots.Slot.LARGE, SkillInput.pressFirst(id, 104));
        assertFalse(ClickSignals.leftPossible(id, 105));
        // a real left click later is the normal attack again
        assertTrue(ClickSignals.leftPossible(id, 110));
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, false, 110).kind());
        // and Q, then a real left click (3 ticks after the drop or more) is Q, left: 小 with a melee weapon
        assertNull(SkillInput.pressFirst(id, 200));
        ClickSignals.dropKey(id, 200);
        assertTrue(ClickSignals.leftPossible(id, 203));
        assertEquals(skill(SkillSlots.Slot.SMALL), SkillInput.click(id, N, false, 203));
        SkillInput.forget(id);
    }

    @Test
    void theKeysAreNamedAsTheyArePressedWithTheWeapon() {
        // the default of t164: F → left / right / F, the other button util, shift + Q the ULT, Q the weapon's skill
        assertEquals("F→左", SkillInput.keys(SkillSlots.Slot.SMALL));
        assertEquals("F→右", SkillInput.keys(SkillSlots.Slot.MEDIUM));
        assertEquals("F→F", SkillInput.keys(SkillSlots.Slot.LARGE));
        assertEquals("右クリック", SkillInput.keys(SkillSlots.Slot.UTIL));
        assertEquals("左クリック", SkillInput.keys(SkillSlots.Slot.UTIL, false, false));
        assertEquals("Shift+Q", SkillInput.keys(SkillSlots.Slot.ULT));
        assertEquals("Q", SkillInput.itemSkillKeys(false));
        assertEquals("Shift+F", SkillInput.removeKeys(false));
        // swapped (t162): Q waits for the second key, F is the weapon's skill and shift + F the ULT
        assertEquals("Q→左", SkillInput.keys(SkillSlots.Slot.SMALL, true));
        assertEquals("Q→右", SkillInput.keys(SkillSlots.Slot.MEDIUM, true));
        assertEquals("Q→Q", SkillInput.keys(SkillSlots.Slot.LARGE, true));
        assertEquals("右クリック (近接) / 左クリック (遠距離)", SkillInput.keys(SkillSlots.Slot.UTIL, true));
        assertEquals("Shift+F", SkillInput.keys(SkillSlots.Slot.ULT, true));
        assertEquals("F", SkillInput.itemSkillKeys(true));
        assertEquals("Shift+Q", SkillInput.removeKeys(true));
        assertFalse(SkillInput.swapped(java.util.UUID.randomUUID()));
    }

    @Test
    void eachHeavyAttackCoolsDownOnItsOwnAndTheChainGoesOn() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        MeleeCombo combo = new MeleeCombo(plugin);
        ComboSpec spec = new RangerMeleeCombo().spec();
        combo.register(spec);
        String plan = spec.plan();
        // 強1 keeps the combo's own cooldown; 強2 - 強5, which had none, get 3 - 6 s
        assertEquals(spec.heavyCooldownTicks()[0], combo.heavyCooldownTicks(plan, 0));
        assertEquals(60, combo.heavyCooldownTicks(plan, 1));
        assertEquals(120, combo.heavyCooldownTicks(plan, 4));
        // the mage's 強1 (the re-activation) stays without one
        combo.register(MageMoves.SPEC);
        assertEquals(0, combo.heavyCooldownTicks(MeleeCombo.MAGE, 0));

        combo.next(player, plan);
        combo.next(player, plan);
        MeleeCombo.Step heavy = combo.heavyByInput(player, plan, 2, false);
        assertNotNull(heavy);
        assertEquals(2, heavy.heavyIndex());
        assertTrue(heavy.heavy());
        assertNull(combo.heavyByInput(player, plan, 2, false), "強3 cools down");
        assertNotNull(combo.heavyByInput(player, plan, 3, false), "強4 does not wait for 強3");
        long[] cooldown = combo.heavyCooldown(player, plan, 2);
        assertTrue(cooldown[0] > 0 && cooldown[1] == 80 * 50L, cooldown[0] + " / " + cooldown[1]);
        assertArrayEquals(new long[]{0L, 0L}, combo.heavyCooldown(player, plan, 1));
        // the chain goes on after the heavy attacks: the next normal attack is the third
        assertEquals(3, combo.next(player, plan).stage());
    }
}
