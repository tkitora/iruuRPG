package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassInfoTest {

    /** Every class of the jar has its introduction and, for each kind of weapon, all five normals and five heavies. */
    @Test
    void everyClassHasItsWordsAndAFullComboGuide() {
        for (String id : ClassInfo.ORDER) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File("src/main/resources/classes/" + id + ".yml"));
            assertEquals(id, yaml.getString("id"));
            assertFalse(yaml.getString("catchphrase", "").isBlank(), id + " catchphrase");
            assertEquals(1, yaml.getStringList("description").size(), id + " description");
            assertFalse(yaml.getStringList("features").isEmpty(), id + " features");
            for (String kind : List.of("melee", "range", "deploy")) {
                List<String> lines = yaml.getStringList("combo-guide." + kind);
                for (int stage = 1; stage <= 5; stage++) {
                    String label = "弱" + stage + ":";
                    assertEquals(1, lines.stream().filter(line -> line.startsWith(label)).count(), id + " " + kind + " " + label);
                }
                // スキル1 - スキル6 (t138: the heavy attacks 強1 - 強5 are skills 1 - 5, and skill 6 is new)
                for (int heavy = 1; heavy <= 6; heavy++) {
                    String label = "スキル" + heavy + ":";
                    assertEquals(1, lines.stream().filter(line -> line.startsWith(label)).count(), id + " " + kind + " " + label);
                }
                for (String line : lines) {
                    assertEquals(line.chars().filter(c -> c == '【').count(), line.chars().filter(c -> c == '】').count(), id + ": " + line);
                }
            }
        }
    }

    @Test
    void guideLineColorsTheLabelAndTheEffects() {
        List<TextComponent> parts = flatten(ClassInfo.guideLine("強3: 溜めて一撃【打ち上げ】"));
        assertEquals("強3:", parts.get(0).content());
        assertEquals(NamedTextColor.GOLD, parts.get(0).color());
        assertTrue(parts.stream().anyMatch(part -> part.content().equals("【打ち上げ】") && NamedTextColor.AQUA.equals(part.color())));
        assertTrue(parts.stream().anyMatch(part -> part.content().equals(" 溜めて一撃") && NamedTextColor.WHITE.equals(part.color())));
        assertEquals(NamedTextColor.YELLOW, flatten(ClassInfo.guideLine("弱1: 斬る")).get(0).color());
        assertEquals(NamedTextColor.GOLD, flatten(ClassInfo.guideLine("スキル6: 何か")).get(0).color());
    }

    private static List<TextComponent> flatten(Component component) {
        List<TextComponent> out = new ArrayList<>();
        if (component instanceof TextComponent text && !text.content().isEmpty()) out.add(text);
        for (Component child : component.children()) out.addAll(flatten(child));
        return out;
    }
}
