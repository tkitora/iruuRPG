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
 * The keys of the skills (the redesign of 2026-11-02): Q and then the normal button (小), the other button (中) or Q (大); F util, shift + F the
 * ULT; the other button alone the weapon's skill. The heavy attacks (小・中・大 until the classes' moves are made) cool down on their own.
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
        assertNull(SkillInput.pressQ(id, 10));
        assertEquals(skill(SkillSlots.Slot.SMALL), SkillInput.click(id, N, 15));
        assertNull(SkillInput.pressQ(id, 20));
        assertEquals(skill(SkillSlots.Slot.MEDIUM), SkillInput.click(id, O, 22));
        assertNull(SkillInput.pressQ(id, 30));
        assertEquals(SkillSlots.Slot.LARGE, SkillInput.pressQ(id, 31));
        // F is util, shift + F the ULT, at once; a Q that waited is let go
        assertEquals(SkillSlots.Slot.UTIL, SkillInput.pressF(id, false));
        assertEquals(SkillSlots.Slot.ULT, SkillInput.pressF(id, true));
        assertNull(SkillInput.pressQ(id, 40));
        assertEquals(SkillSlots.Slot.UTIL, SkillInput.pressF(id, false));
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, 41).kind());
        // too late: the second Q is a new first one, the click what it is alone
        assertNull(SkillInput.pressQ(id, 100));
        assertNull(SkillInput.pressQ(id, 121));
        assertEquals(SkillSlots.Slot.LARGE, SkillInput.pressQ(id, 122));
        assertNull(SkillInput.pressQ(id, 200));
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, 221).kind());
        // with no Q before it: the normal button is the normal attack, the other the weapon's skill; the same click again in its tick is nothing
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, 300).kind());
        assertEquals(SkillInput.Kind.NOTHING, SkillInput.click(id, N, 300).kind());
        assertEquals(SkillInput.Kind.ITEM_SKILL, SkillInput.click(id, O, 310).kind());
        SkillInput.forget(id);
    }

    @Test
    void aHeldOtherButtonIsOnePressOfTheWeaponsSkill() {
        plugin();
        UUID id = UUID.randomUUID();
        int cast = 0;
        // the client repeats a held button every 4 ticks
        for (int tick = 0; tick < 40; tick += 4) if (SkillInput.click(id, O, tick).kind() == SkillInput.Kind.ITEM_SKILL) cast++;
        assertEquals(1, cast);
        // let go, and pressed again
        assertEquals(SkillInput.Kind.ITEM_SKILL, SkillInput.click(id, O, 60).kind());
        // Q, and the other button is 中 even right after it
        assertNull(SkillInput.pressQ(id, 62));
        assertEquals(skill(SkillSlots.Slot.MEDIUM), SkillInput.click(id, O, 63));
        SkillInput.forget(id);
    }

    @Test
    void theSwingThatComesWithQIsNotItsSecondKey() {
        // t144: the client swings the main hand with every drop (Q); the server would take it for a left click in the air in the same tick
        plugin();
        UUID id = UUID.randomUUID();
        assertNull(SkillInput.pressQ(id, 100));
        ClickSignals.dropKey(id, 100);
        // the swing in the drop's tick, or the next two, is no click: the listener does not hand it to the keys, so Q waits on
        assertFalse(ClickSignals.leftPossible(id, 100));
        assertFalse(ClickSignals.leftPossible(id, 102));
        // the second Q is Q, Q (大), and its own swing is no click either
        ClickSignals.dropKey(id, 104);
        assertEquals(SkillSlots.Slot.LARGE, SkillInput.pressQ(id, 104));
        assertFalse(ClickSignals.leftPossible(id, 105));
        // a real left click later is the normal attack again
        assertTrue(ClickSignals.leftPossible(id, 110));
        assertEquals(SkillInput.Kind.NORMAL_ATTACK, SkillInput.click(id, N, 110).kind());
        // and Q, then a real left click (3 ticks after the drop or more) is Q, left: 小 with a melee weapon
        assertNull(SkillInput.pressQ(id, 200));
        ClickSignals.dropKey(id, 200);
        assertTrue(ClickSignals.leftPossible(id, 203));
        assertEquals(skill(SkillSlots.Slot.SMALL), SkillInput.click(id, N, 203));
        SkillInput.forget(id);
    }

    @Test
    void theKeysAreNamedAsTheyArePressedWithTheWeapon() {
        // the buttons change sides with a ranged weapon (its normal button is the right one)
        assertEquals("Q→左", SkillInput.keys(SkillSlots.Slot.SMALL, true));
        assertEquals("Q→右", SkillInput.keys(SkillSlots.Slot.SMALL, false));
        assertEquals("Q→右", SkillInput.keys(SkillSlots.Slot.MEDIUM, true));
        assertEquals("Q→左", SkillInput.keys(SkillSlots.Slot.MEDIUM, false));
        assertEquals("Q→Q", SkillInput.keys(SkillSlots.Slot.LARGE, false));
        assertEquals("F", SkillInput.keys(SkillSlots.Slot.UTIL, true));
        assertEquals("Shift+F", SkillInput.keys(SkillSlots.Slot.ULT, true));
        assertEquals("右クリック", SkillInput.itemSkillKeys(true));
        assertEquals("左クリック", SkillInput.itemSkillKeys(false));
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
