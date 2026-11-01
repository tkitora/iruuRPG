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
        assertEquals(20.0, SkillSlots.cooldownSeconds(section, "assassin", SkillSlots.Slot.LARGE));
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
}
