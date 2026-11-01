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
 * The heavy attack (強1 - 強5) chosen by the other button and a number key (t119, the default), or by the buttons (t116, style: buttons); each heavy
 * attack cools down on its own, and the chain of normal attacks goes on.
 */
class HeavyInputTest {

    private static final HeavyInput.Button N = HeavyInput.Button.NORMAL;
    private static final HeavyInput.Button O = HeavyInput.Button.OTHER;

    /** Sets the style of the input (the plugin of HeavyInput is static: each test sets its own). */
    private static void style(String style) {
        JavaPlugin plugin = mock(JavaPlugin.class);
        YamlConfiguration config = new YamlConfiguration();
        if (style != null) config.set("combat.heavy-input.style", style);
        when(plugin.getConfig()).thenReturn(config);
        new HeavyInput(plugin, (player, index) -> true);
    }

    @Test
    void twoKeysChooseWhatIsDone() {
        // t138: the style of the keys is the default
        style(null);
        assertTrue(HeavyInput.keyStyle());
        assertFalse(HeavyInput.numberKeys());
        UUID id = UUID.randomUUID();
        java.util.function.Function<KeyBindings.Pattern, KeyBindings.Action> defaults = pattern -> KeyBindings.actionOf(KeyBindings.DEFAULTS, pattern);
        KeyBindings.Key f = KeyBindings.Key.F, q = KeyBindings.Key.Q;
        // F or Q alone waits; the second key does it
        assertNull(HeavyInput.press(id, f, 10, defaults));
        assertEquals(KeyBindings.Action.SKILL1, HeavyInput.press(id, f, 15, defaults));
        assertNull(HeavyInput.press(id, f, 20, defaults));
        assertEquals(KeyBindings.Action.SKILL2.ordinal(), HeavyInput.keyClick(id, O, 22, defaults));
        assertNull(HeavyInput.press(id, f, 30, defaults));
        assertEquals(KeyBindings.Action.SKILL3.ordinal(), HeavyInput.keyClick(id, N, 31, defaults));
        assertNull(HeavyInput.press(id, f, 40, defaults));
        assertEquals(KeyBindings.Action.SKILL4, HeavyInput.press(id, q, 41, defaults));
        assertNull(HeavyInput.press(id, q, 50, defaults));
        assertEquals(KeyBindings.Action.SKILL5.ordinal(), HeavyInput.keyClick(id, O, 51, defaults));
        assertNull(HeavyInput.press(id, q, 60, defaults));
        assertEquals(KeyBindings.Action.SKILL6.ordinal(), HeavyInput.keyClick(id, N, 61, defaults));
        assertNull(HeavyInput.press(id, q, 70, defaults));
        assertEquals(KeyBindings.Action.ULTIMATE, HeavyInput.press(id, f, 71, defaults));
        assertNull(HeavyInput.press(id, q, 80, defaults));
        assertEquals(KeyBindings.Action.ITEM, HeavyInput.press(id, q, 81, defaults));
        // too late: the second key is a new first one
        assertNull(HeavyInput.press(id, f, 100, defaults));
        assertNull(HeavyInput.press(id, f, 121, defaults));
        assertEquals(KeyBindings.Action.SKILL1, HeavyInput.press(id, f, 122, defaults));
        // a click with no key before it: the normal attack, or nothing (the other button); the same click again in its tick is nothing
        assertEquals(HeavyInput.NORMAL_ATTACK, HeavyInput.keyClick(id, N, 200, defaults));
        assertEquals(HeavyInput.NOTHING, HeavyInput.keyClick(id, N, 200, defaults));
        assertEquals(HeavyInput.OTHER_ALONE, HeavyInput.keyClick(id, O, 210, defaults));
        assertNull(HeavyInput.press(id, f, 300, defaults));
        HeavyInput.keyClick(id, N, 400, defaults);
        assertEquals(HeavyInput.NORMAL_ATTACK, HeavyInput.keyClick(id, N, 401, defaults), "the key was too long ago");
        // the player's own choice
        java.util.Map<KeyBindings.Action, KeyBindings.Pattern> own = new java.util.EnumMap<>(KeyBindings.DEFAULTS);
        KeyBindings.swap(own, KeyBindings.Action.ITEM, new KeyBindings.Pattern(f, f));
        assertNull(HeavyInput.press(id, f, 500, pattern -> KeyBindings.actionOf(own, pattern)));
        assertEquals(KeyBindings.Action.ITEM, HeavyInput.press(id, f, 501, pattern -> KeyBindings.actionOf(own, pattern)));
        HeavyInput.forget(id);
    }

    @Test
    void theOtherButtonAndANumberKeyChooseTheHeavyAttack() {
        style("number");
        assertTrue(HeavyInput.numberKeys());
        UUID id = UUID.randomUUID();
        // a number key with no other button before it moves the hotbar
        assertEquals(-1, HeavyInput.numberKey(id, 2, 10));
        // the other button alone is nothing; then key 3 (slot 2) within 20 ticks: 強3
        assertEquals(HeavyInput.NOTHING, HeavyInput.click(id, false, O, 20));
        assertEquals(2, HeavyInput.numberKey(id, 2, 35));
        // the wait is over after the key
        assertEquals(-1, HeavyInput.numberKey(id, 2, 36));
        // too late
        assertEquals(HeavyInput.NOTHING, HeavyInput.click(id, false, O, 100));
        assertEquals(-1, HeavyInput.numberKey(id, 0, 121));
        // keys 6 - 9 are only the hotbar
        HeavyInput.click(id, false, O, 200);
        assertEquals(-1, HeavyInput.numberKey(id, 5, 202));
        // the normal button is the normal attack and ends the wait; shift changes nothing
        HeavyInput.click(id, false, O, 300);
        assertEquals(HeavyInput.NORMAL_ATTACK, HeavyInput.click(id, true, N, 303));
        assertEquals(-1, HeavyInput.numberKey(id, 0, 305));
        HeavyInput.click(id, true, O, 400);
        assertEquals(4, HeavyInput.numberKey(id, 4, 401));
        // no double press of shift in this style (the event handler skips it)
        HeavyInput.forget(id);
    }

    @Test
    void theButtonsChooseTheHeavyAttack() {
        style("buttons");
        assertFalse(HeavyInput.numberKeys());
        UUID id = UUID.randomUUID();
        // other, other: 強1 (index 0); the first alone is nothing
        assertEquals(HeavyInput.NOTHING, HeavyInput.click(id, false, O, 100));
        assertEquals(0, HeavyInput.click(id, false, O, 104));
        // other, normal: 強2
        assertEquals(HeavyInput.NOTHING, HeavyInput.click(id, false, O, 120));
        assertEquals(1, HeavyInput.click(id, false, N, 123));
        // the normal button alone is the normal attack; the other one too late is a new first press
        assertEquals(HeavyInput.NORMAL_ATTACK, HeavyInput.click(id, false, N, 130));
        assertEquals(HeavyInput.NOTHING, HeavyInput.click(id, false, O, 140));
        assertEquals(HeavyInput.NORMAL_ATTACK, HeavyInput.click(id, false, N, 149));
        // shift + other: 強3, shift + normal: 強4
        assertEquals(2, HeavyInput.click(id, true, O, 160));
        assertEquals(3, HeavyInput.click(id, true, N, 170));
        // the same click through a second event of its tick is nothing
        assertEquals(HeavyInput.NORMAL_ATTACK, HeavyInput.click(id, false, N, 180));
        assertEquals(HeavyInput.NOTHING, HeavyInput.click(id, false, N, 180));
        HeavyInput.forget(id);
    }

    @Test
    void aHeldOtherButtonThrows強1AgainAndAgain() {
        style("buttons");
        UUID id = UUID.randomUUID();
        int thrown = 0;
        // the client repeats a held button every 4 ticks
        for (int tick = 0; tick < 40; tick += 4) if (HeavyInput.click(id, false, O, tick) == 0) thrown++;
        assertEquals(5, thrown);
        HeavyInput.forget(id);
    }

    @Test
    void shiftTwiceIs強5ButNotAfterAShiftClick() {
        style("buttons");
        UUID id = UUID.randomUUID();
        assertFalse(HeavyInput.sneakDown(id, 10));
        assertTrue(HeavyInput.sneakDown(id, 15));
        // a press used for shift + click does not count as the first of a pair
        assertFalse(HeavyInput.sneakDown(id, 100));
        HeavyInput.click(id, true, O, 101);
        assertFalse(HeavyInput.sneakDown(id, 105));
        // too slow
        assertFalse(HeavyInput.sneakDown(id, 200));
        assertFalse(HeavyInput.sneakDown(id, 220));
        HeavyInput.forget(id);
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
