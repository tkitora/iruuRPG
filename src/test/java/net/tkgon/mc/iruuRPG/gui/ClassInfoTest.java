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
                // スキル1 - スキル6 (t138: the heavy attacks 強1 - 強5 are skills 1 - 5, and skill 6 is new); a deployed weapon has none since the
                // redesign of 2026-11-02 (only the ULT and its own skill); skills of their own (the assassin's melee, t161) tell 小・中・大・util instead
                boolean own = net.tkgon.mc.iruuRPG.combat.SlotMoves.reworked(id, kind.equals("melee") ? net.tkgon.mc.iruuRPG.combat.AttackType.MELEE
                        : kind.equals("range") ? net.tkgon.mc.iruuRPG.combat.AttackType.RANGE : net.tkgon.mc.iruuRPG.combat.AttackType.DEPLOY);
                for (int heavy = 1; heavy <= 6; heavy++) {
                    String label = "スキル" + heavy + ":";
                    assertEquals(kind.equals("deploy") || own ? 0 : 1, lines.stream().filter(line -> line.startsWith(label)).count(), id + " " + kind + " " + label);
                }
                if (own) {
                    for (String label : List.of("小:", "中:", "大:", "util:")) {
                        assertEquals(1, lines.stream().filter(line -> line.startsWith(label)).count(), id + " " + kind + " " + label);
                    }
                }
                for (String line : lines) {
                    assertEquals(line.chars().filter(c -> c == '【').count(), line.chars().filter(c -> c == '】').count(), id + ": " + line);
                }
            }
        }
    }

    @Test
    void theControlsAreTheKeysOfTheSkills() {
        // the default of t164: F → left / right / F, util the other button (it changes sides with a ranged weapon), shift + Q the ULT, Q the weapon's skill
        List<String> melee = ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.MELEE, "warrior");
        assertEquals("左クリック: 通常 (弱1→弱5)", melee.get(0));
        assertEquals("F→左: 小 / F→右: 中 / F→F: 大", melee.get(1));
        assertEquals("右クリック: util / Shift+Q: ULT", melee.get(2));
        assertEquals("Q: アイテムスキル", melee.get(3));
        List<String> ranged = ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.RANGE, "warrior");
        assertEquals("F→左: 小 / F→右: 中 / F→F: 大", ranged.get(1));
        assertEquals("左クリック: util / Shift+Q: ULT", ranged.get(2));
        assertTrue(ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.DEPLOY, "warrior").stream()
                .anyMatch(line -> line.contains("Shift+Q: ULT") && line.contains("Shift+F: 設置を消す")));
        // Q and F swapped (t162): the keys of the guide follow
        assertEquals("Q→左: 小 / Q→右: 中 / Q→Q: 大", ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.MELEE, "warrior", true).get(1));
        assertEquals("F: アイテムスキル", ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.MELEE, "warrior", true).get(3));
        assertTrue(ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.DEPLOY, "warrior", true).stream()
                .anyMatch(line -> line.contains("Shift+F: ULT") && line.contains("Shift+Q: 設置を消す")));
        // which skills of the guide they throw: a class whose moves are not made yet, and the mage (its own with a ranged weapon since t168 and with
        // a scythe since t167, told by the guide itself)
        List<String> notYet = ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.MELEE, "nobody");
        assertEquals("小・中・大 は、いまは下のスキル1〜3", notYet.get(notYet.size() - 1));
        List<String> mage = ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.RANGE, "mage");
        assertEquals("Q: アイテムスキル", mage.get(mage.size() - 1));
        List<String> mageMelee = ClassInfo.controls(net.tkgon.mc.iruuRPG.combat.AttackType.MELEE, "mage");
        assertEquals("Q: アイテムスキル", mageMelee.get(mageMelee.size() - 1));
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
