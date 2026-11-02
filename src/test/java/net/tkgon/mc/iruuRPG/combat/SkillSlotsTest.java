package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The five skills of a class (the redesign of 2026-11-02): their cooldowns, a class's own move, and what a skill does before its move is made. */
class SkillSlotsTest {

    private static YamlConfiguration jarConfig() {
        return YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
    }

    @Test
    void theCooldownsAreTheDesignsAndTheConfigCanChangeThem() {
        var section = jarConfig().getConfigurationSection("combat.skill-slots.cooldown-seconds");
        // 小 7 / 中 12 / 大 25 / util 5 / ULT 100, and the classes' own
        assertEquals(7.0, SkillSlots.cooldownSeconds(section, "mage", SkillSlots.Slot.SMALL));
        assertEquals(25.0, SkillSlots.cooldownSeconds(section, "mage", SkillSlots.Slot.LARGE));
        assertEquals(100.0, SkillSlots.cooldownSeconds(section, "mage", SkillSlots.Slot.ULT));
        assertEquals(10.0, SkillSlots.cooldownSeconds(section, "warrior", SkillSlots.Slot.MEDIUM));
        assertEquals(30.0, SkillSlots.cooldownSeconds(section, "warrior", SkillSlots.Slot.LARGE));
        assertEquals(15.0, SkillSlots.cooldownSeconds(section, "healer", SkillSlots.Slot.UTIL));
        assertEquals(3.0, SkillSlots.cooldownSeconds(section, "gifted", SkillSlots.Slot.SMALL));
        assertEquals(7.0, SkillSlots.cooldownSeconds(section, "gifted", SkillSlots.Slot.MEDIUM));
        assertEquals(20.0, SkillSlots.cooldownSeconds(section, "gifted", SkillSlots.Slot.LARGE));
        assertEquals(24.0, SkillSlots.cooldownSeconds(section, "assassin", SkillSlots.Slot.LARGE));
        assertEquals(5.0, SkillSlots.cooldownSeconds(section, "assassin", SkillSlots.Slot.UTIL));
        // the design's without a config, and for a player with no class
        for (SkillSlots.Slot slot : SkillSlots.Slot.values()) {
            for (String classId : List.of("warrior", "mage", "healer", "gifted", "assassin")) {
                assertEquals(SkillSlots.cooldownSeconds(section, classId, slot), SkillSlots.cooldownSeconds(null, classId, slot), classId + " " + slot);
            }
        }
        assertEquals(12.0, SkillSlots.cooldownSeconds(section, null, SkillSlots.Slot.MEDIUM));
        // the config's: the class's, else its default
        YamlConfiguration config = new YamlConfiguration();
        config.set("default.small", 4);
        config.set("mage.small", 2.5);
        assertEquals(2.5, SkillSlots.cooldownSeconds(config, "mage", SkillSlots.Slot.SMALL));
        assertEquals(4.0, SkillSlots.cooldownSeconds(config, "warrior", SkillSlots.Slot.SMALL));
        assertEquals(30.0, SkillSlots.cooldownSeconds(config, "warrior", SkillSlots.Slot.LARGE));
    }

    @Test
    void aClassesOwnMoveCoolsDownForItsSkillsCooldown() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(jarConfig());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        SkillSlots slots = new SkillSlots(plugin, ignored -> "warrior", ignored -> 0.5);
        List<SkillSlots.Slot> fallen = new ArrayList<>();
        slots.setFallback(new SkillSlots.Fallback() {
            @Override
            public boolean use(Player who, SkillSlots.Slot slot, net.tkgon.mc.iruuRPG.item.RpgItemDefinition weapon) {
                fallen.add(slot);
                return slot != SkillSlots.Slot.UTIL;
            }

            @Override
            public long[] cooldown(Player who, SkillSlots.Slot slot, net.tkgon.mc.iruuRPG.item.RpgItemDefinition weapon) {
                return slot == SkillSlots.Slot.UTIL ? null : new long[]{1000L, 2000L};
            }
        });
        int[] done = {0};
        slots.register("warrior", SkillSlots.Slot.LARGE, (who, weapon) -> ++done[0] > 0);
        assertTrue(slots.has("warrior", SkillSlots.Slot.LARGE));
        assertFalse(slots.has("mage", SkillSlots.Slot.LARGE));

        // 大 is the class's own: done, and it cools down for 30 s x 0.5
        slots.use(player, SkillSlots.Slot.LARGE, null);
        assertEquals(1, done[0]);
        long[] cooldown = slots.cooldown(player, SkillSlots.Slot.LARGE, null);
        assertEquals(15_000L, cooldown[1]);
        assertTrue(cooldown[0] > 14_000L && cooldown[0] <= 15_000L, String.valueOf(cooldown[0]));
        // the others are what they were: the fallback's use and cooldown (util has nothing: the player is told)
        slots.use(player, SkillSlots.Slot.SMALL, null);
        slots.use(player, SkillSlots.Slot.UTIL, null);
        assertEquals(List.of(SkillSlots.Slot.SMALL, SkillSlots.Slot.UTIL), fallen);
        verify(player).sendMessage("[iruuRPG] util のスキルは、まだありません。");
        assertArrayEquals(new long[]{1000L, 2000L}, slots.cooldown(player, SkillSlots.Slot.SMALL, null));
        assertNull(slots.cooldown(player, SkillSlots.Slot.UTIL, null));
    }

    @Test
    void aMoveThatWasNotDoneStartsNoCooldown() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(jarConfig());
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        SkillSlots slots = new SkillSlots(plugin, ignored -> "mage", ignored -> 1.0);
        slots.register("MAGE", SkillSlots.Slot.SMALL, (who, weapon) -> false);
        slots.use(player, SkillSlots.Slot.SMALL, null);
        assertArrayEquals(new long[]{0L, 0L}, slots.cooldown(player, SkillSlots.Slot.SMALL, null));
        // a cooldown runs out
        UUID other = UUID.randomUUID();
        SkillSlots.Kept sword = new SkillSlots.Kept(AttackType.MELEE, SkillSlots.Slot.SMALL);
        slots.start(other, sword, 7000L, 1000L);
        assertArrayEquals(new long[]{2000L, 7000L}, slots.cooldown(other, sword, 6000L));
        assertArrayEquals(new long[]{0L, 0L}, slots.cooldown(other, sword, 8000L));
        // each kind of weapon has its own: 小 of the sword cooling down leaves 小 of the bow ready
        slots.start(other, sword, 7000L, 10_000L);
        assertArrayEquals(new long[]{0L, 0L}, slots.cooldown(other, new SkillSlots.Kept(AttackType.RANGE, SkillSlots.Slot.SMALL), 11_000L));
        assertEquals(AttackType.MELEE, SkillSlots.kindOf(null));
    }

    /** t157: a skill with charges and a re-activation goes by nekorozv's states: the re-activation first, then a charge, then nothing. */
    @Test
    void theReActivationComesFirstThenACharge() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(jarConfig());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        SkillSlots slots = new SkillSlots(plugin, ignored -> "mage", ignored -> 1.0);
        int[] used = {0};
        int[] recast = {0};
        slots.register("mage", SkillSlots.Slot.SMALL, new SkillSlots.Move() {
            @Override
            public boolean use(Player who, net.tkgon.mc.iruuRPG.item.RpgItemDefinition weapon) {
                used[0]++;
                return true;
            }

            @Override
            public boolean recast(Player who, net.tkgon.mc.iruuRPG.item.RpgItemDefinition weapon) {
                recast[0]++;
                return true;
            }
        }, new SkillSlots.Mechanics(2, 3.0, 0.0));
        SkillSlots.Slot small = SkillSlots.Slot.SMALL;
        SkillSlots.Kept kept = new SkillSlots.Kept(AttackType.MELEE, small);
        SkillSlots.Mechanics mechanics = slots.mechanics(id, "mage", small);
        // full: 2 charges, nothing refills
        SkillSlots.View full = slots.view(id, kept, mechanics, 0L);
        assertEquals(2, full.charges());
        assertTrue(full.charged());
        assertEquals(-1.0, full.refill());
        // 1. a charge: the move, its re-activation opens, the refill of the charge starts (mage 小: 7 s)
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 0L));
        SkillSlots.View after = slots.view(id, kept, mechanics, 1000L);
        assertEquals(1, after.charges());
        assertTrue(after.recasting());
        assertEquals(1.0 / 7.0, after.refill(), 1e-9);
        assertArrayEquals(new long[]{0L, 0L}, after.cooldown(), "a charge is left: no cooldown");
        // 2. the same keys again within 3 s: the second part, no charge used, the re-activation closes
        assertEquals(SkillSlots.Done.RECAST, slots.use(player, small, null, 1000L));
        assertEquals(1, recast[0]);
        assertEquals(1, slots.view(id, kept, mechanics, 1000L).charges());
        assertFalse(slots.view(id, kept, mechanics, 1000L).recasting());
        // a new first part with the last charge
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 1500L));
        // the re-activation comes first even with no charge left
        assertEquals(SkillSlots.Done.RECAST, slots.use(player, small, null, 2000L));
        // 3. no charge and no re-activation: nothing; all come back 7 s after the last was used (t161: none refills meanwhile)
        assertEquals(SkillSlots.Done.NOTHING, slots.use(player, small, null, 2500L));
        assertEquals(2, used[0]);
        SkillSlots.View empty = slots.view(id, kept, mechanics, 2500L);
        assertEquals(0, empty.charges());
        assertArrayEquals(new long[]{6000L, 7000L}, empty.cooldown());
        assertEquals(0, slots.view(id, kept, mechanics, 7000L).charges(), "the charge that was refilling does not come back alone");
        // 4. both back at 8.5 s: a new first part
        assertEquals(2, slots.view(id, kept, mechanics, 8500L).charges());
        assertEquals(-1.0, slots.view(id, kept, mechanics, 8500L).refill());
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 8500L));
        // its re-activation ends after 3 s
        assertFalse(slots.view(id, kept, mechanics, 11_501L).recasting());
        assertEquals(2, recast[0]);
    }

    /** t161: while one is left the missing charges come back one at a time; once none is left, all come back together after the wait for them. */
    @Test
    void anEmptySkillWaitsForAllItsChargesAtOnce() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(jarConfig());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        SkillSlots slots = new SkillSlots(plugin, ignored -> "assassin", ignored -> 1.0);
        // (assassin's 小: 3 charges, 3 s each, 8 s when all are used; only with a melee weapon)
        slots.register("assassin", SkillSlots.Slot.SMALL, (who, weapon) -> true,
                kind -> kind == AttackType.MELEE ? new SkillSlots.Mechanics(3, 0.0, 3.0, 8.0) : SkillSlots.Mechanics.NONE);
        SkillSlots.Slot small = SkillSlots.Slot.SMALL;
        SkillSlots.Kept kept = new SkillSlots.Kept(AttackType.MELEE, small);
        SkillSlots.Mechanics mechanics = slots.mechanics(id, "assassin", small, AttackType.MELEE);
        assertEquals(3, mechanics.charges());
        assertEquals(SkillSlots.Mechanics.NONE, slots.mechanics(id, "assassin", small, AttackType.RANGE));
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 0L));
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 1000L));
        assertEquals(1, slots.view(id, kept, mechanics, 2000L).charges());
        // the first missing one is back 3 s after the first was used, and the next refills from then
        assertEquals(2, slots.view(id, kept, mechanics, 3000L).charges());
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 3500L));
        assertEquals(SkillSlots.Done.USED, slots.use(player, small, null, 4000L));
        // none left: no charge comes back at 6 s; the wait of 8 s is its cooldown (greyed, the seconds), and the gauge goes with it
        SkillSlots.View empty = slots.view(id, kept, mechanics, 6000L);
        assertEquals(0, empty.charges());
        assertArrayEquals(new long[]{6000L, 8000L}, empty.cooldown());
        assertEquals(0.25, empty.refill(), 1e-9);
        assertEquals(SkillSlots.Done.NOTHING, slots.use(player, small, null, 6000L));
        // all three at 12 s, nothing refilling
        SkillSlots.View full = slots.view(id, kept, mechanics, 12_000L);
        assertEquals(3, full.charges());
        assertEquals(-1.0, full.refill());
        assertArrayEquals(new long[]{0L, 0L}, full.cooldown());
    }

    @Test
    void aSkillCanBeTriedWithChargesAndItsSecondPartIsTheMoveAgain() {
        JavaPlugin plugin = mock(JavaPlugin.class);
        when(plugin.getConfig()).thenReturn(jarConfig());
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        SkillSlots slots = new SkillSlots(plugin, ignored -> "warrior", ignored -> 1.0);
        int[] used = {0};
        slots.register("warrior", SkillSlots.Slot.LARGE, (who, weapon) -> ++used[0] > 0);
        assertEquals(SkillSlots.Mechanics.NONE, slots.mechanics(id, "warrior", SkillSlots.Slot.LARGE));
        slots.trial(id, SkillSlots.Slot.LARGE, new SkillSlots.Mechanics(3, 2.0, 1.5));
        assertEquals(SkillSlots.Done.USED, slots.use(player, SkillSlots.Slot.LARGE, null, 0L));
        // (a move with no second part, tried: the move again)
        assertEquals(SkillSlots.Done.RECAST, slots.use(player, SkillSlots.Slot.LARGE, null, 500L));
        assertEquals(2, used[0]);
        SkillSlots.Kept kept = new SkillSlots.Kept(AttackType.MELEE, SkillSlots.Slot.LARGE);
        SkillSlots.Mechanics trial = slots.mechanics(id, "warrior", SkillSlots.Slot.LARGE);
        assertEquals(2, slots.view(id, kept, trial, 500L).charges());
        // its own refill time: 1.5 s
        assertEquals(3, slots.view(id, kept, trial, 1500L).charges());
        // back to the move's own: one charge with the 30 s cooldown of the warrior's 大
        slots.endTrials(id);
        assertEquals(SkillSlots.Mechanics.NONE, slots.mechanics(id, "warrior", SkillSlots.Slot.LARGE));
        assertEquals(SkillSlots.Done.USED, slots.use(player, SkillSlots.Slot.LARGE, null, 2000L));
        assertArrayEquals(new long[]{30_000L, 30_000L}, slots.view(id, kept, SkillSlots.Mechanics.NONE, 2000L).cooldown());
        assertEquals(SkillSlots.Done.NOTHING, slots.use(player, SkillSlots.Slot.LARGE, null, 3000L));
        // the mechanics stay in their bounds
        assertEquals(9, new SkillSlots.Mechanics(50, -1, -1).charges());
        assertFalse(new SkillSlots.Mechanics(0, 0, 0).recasts());
    }
}
