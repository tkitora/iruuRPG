package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
import net.tkgon.mc.iruuRPG.skill.SkillTemplate;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stacks on a weapon without a skill (they grow from normal attacks), and the damage-only skill template. */
class HitStacksAndDamageSkillTest {

    private static TestServer.TestPlugin plugin;
    private static SkillRegistry skills;
    private static RpgItemRegistry items;

    @BeforeAll
    static void load() throws IOException {
        TestServer.start();
        File data = Files.createTempDirectory("hitstacks").toFile();
        plugin = new TestServer.TestPlugin(data);
        File skillFolder = new File(data, "skills");
        skillFolder.mkdirs();
        Files.writeString(new File(skillFolder, "test.yml").toPath(), String.join("\n",
                "skills:",
                "  test_poke:",
                "    name: \"一突き\"",
                "    template: damage",
                "    effects: {damage-rate: 2.0}",
                "  test_ring:",
                "    template: damage",
                "    shape: {mode: around}",
                ""), StandardCharsets.UTF_8);
        skills = new SkillRegistry(plugin);
        skills.reload();

        File itemFolder = new File(data, "items");
        itemFolder.mkdirs();
        Files.writeString(new File(itemFolder, "test.yml").toPath(), String.join("\n",
                "items:",
                "  hit_sword:",
                "    material: IRON_SWORD",
                "    name: \"連撃の剣\"",
                "    attack-type: melee",
                "    stats: {weapon-damage: 10}",
                "    stacks: {name: \"連撃\", max: 10, on-max: test_poke, gain-on: [hit]}",
                "  cast_sword:",
                "    material: IRON_SWORD",
                "    name: \"空の剣\"",
                "    attack-type: melee",
                "    stats: {weapon-damage: 10}",
                "    stacks: {max: 5, on-max: test_poke}",
                ""), StandardCharsets.UTF_8);
        items = new RpgItemRegistry(plugin);
        items.setGeneralSkills(skills);
        items.reload();
    }

    @Test
    void aWeaponWithoutASkillKeepsStacksThatGrowFromNormalAttacks() {
        WeaponStackSpec stacks = items.find("hit_sword").orElseThrow().stacks();
        assertNotNull(stacks);
        assertTrue(stacks.gainsOn("hit"));
        assertEquals(10, stacks.max());
        // stacks that grow only when the weapon's own skill is cast cannot grow without one
        assertNull(items.find("cast_sword").orElseThrow().stacks());
    }

    @Test
    void theLoreOfAWeaponWithoutASkillShowsItsStacks() {
        RpgItemFactory factory = new RpgItemFactory(plugin, new ItemIdentifier(new ItemKeys(plugin)), new ItemSkillRegistry(plugin));
        factory.setGeneralSkills(skills);
        ItemStack sword = factory.create(items.find("hit_sword").orElseThrow());
        StringBuilder lore = new StringBuilder();
        for (Component line : sword.getItemMeta().lore()) lore.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        assertTrue(lore.toString().contains("連撃"), lore.toString());
        assertTrue(lore.toString().contains("通常攻撃が当たるたびに1たまり、10で「一突き」が発動する"), lore.toString());
    }

    @Test
    void theDamageTemplateHitsAtOnceAndMayHitOnlyTheOneAimedAt() {
        SkillSpec poke = skills.forItem("test", "test_poke", Map.of());
        assertNotNull(poke);
        assertEquals(SkillTemplate.DAMAGE, poke.template());
        assertEquals(SkillSpec.DamageMode.AIMED, poke.extras().damageMode());
        assertEquals(0, poke.windupTicks());
        assertEquals(0.0, poke.radius(), 1.0E-9);
        assertEquals(2.0, poke.damageRate(), 1.0E-9);
        assertTrue(poke.statuses().isEmpty());
        assertNull(poke.knockback());

        // around the caster: a circle of 4 when no radius is given
        SkillSpec ring = skills.forItem("test", "test_ring", Map.of());
        assertEquals(SkillSpec.DamageMode.AROUND, ring.extras().damageMode());
        assertEquals(4.0, ring.radius(), 1.0E-9);

        // overrides: a radius of 0 and no windup are allowed only for the damage template
        Map<String, Object> wide = new LinkedHashMap<>();
        wide.put("radius", 0);
        wide.put("windup-ticks", 0);
        SkillSpec strike = skills.parse("test", new LinkedHashMap<>(Map.<String, Object>of("template", "strike", "radius", 0, "windup-ticks", 0)), null, "");
        assertEquals(1.0, strike.radius(), 1.0E-9);
        assertEquals(4, strike.windupTicks());
        SkillSpec still = skills.forItem("test", "test_ring", wide);
        assertEquals(0.0, still.radius(), 1.0E-9);
        assertEquals(SkillSpec.DamageMode.AROUND, still.extras().damageMode());
    }
}
