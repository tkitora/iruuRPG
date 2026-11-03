package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t220: the looks of the travel and dungeon menus (menus/*.yml of the yml repository). */
class MenuLooksTest {

    @TempDir
    File folder;

    private void write(String name, String text) throws IOException {
        File dir = new File(folder, "menus");
        dir.mkdirs();
        Files.writeString(new File(dir, name).toPath(), text, StandardCharsets.UTF_8);
    }

    @Test
    void colorsAndIconsAreReadOrLeftOut() {
        assertEquals(NamedTextColor.AQUA, MenuLooks.color("aqua"));
        assertEquals(NamedTextColor.DARK_GRAY, MenuLooks.color("DARK_GRAY"));
        assertEquals(TextColor.fromHexString("#ff8800"), MenuLooks.color("#FF8800"));
        assertNull(MenuLooks.color("#ff88"));
        assertNull(MenuLooks.color("sky"));
        assertEquals(Material.ENDER_EYE, MenuLooks.icon("ender_eye"));
        assertEquals(Material.SPAWNER, MenuLooks.icon("minecraft:spawner"));
        assertNull(MenuLooks.icon("no_such_item"));
        assertNull(MenuLooks.icon("air"));
    }

    @Test
    void theOrderPutsTheNumberedFirstAndKeepsTheRestAsTheyWere() {
        List<String> ids = List.of("a", "b", "c", "d");
        // c is 1, a is 2; b and d have none: after them, as they were
        assertEquals(List.of("c", "a", "b", "d"), MenuLooks.ordered(ids, id -> id.equals("c") ? Integer.valueOf(1) : id.equals("a") ? Integer.valueOf(2) : null));
        assertEquals(ids, MenuLooks.ordered(ids, id -> null));
    }

    @Test
    void theFilesGiveTheLooksAndABadValueIsLeftOut() throws IOException {
        write("travel.yml", """
                title: 旅の扉
                entries:
                  atras:
                    order: 2
                    name: アトラス村
                    color: gold
                    icon: compass
                    lore: [港から北へ, 鍛冶屋がある]
                  delta:
                    order: 1
                    color: nope
                    icon: not_an_item
                unknown: {name: "？？？", icon: gray_dye}
                """);
        write("dungeons.yml", "entries:\n  cave:\n    recommended-level: 5\n    lore: 小さな洞窟\n");
        List<String> warnings = new ArrayList<>();
        MenuLooks looks = new MenuLooks(folder, warnings::add);
        looks.reload();
        assertEquals("旅の扉", looks.travelTitle("ファストトラベル"));
        assertEquals("ダンジョン", looks.dungeonTitle("ダンジョン"));
        MenuLooks.Look atras = looks.travel("atras");
        assertEquals("アトラス村", atras.name());
        assertEquals(NamedTextColor.GOLD, atras.color());
        assertEquals(Material.COMPASS, atras.icon());
        assertEquals(List.of("港から北へ", "鍛冶屋がある"), atras.lore());
        // a bad colour and icon are left out (the rest stays), with a warning each
        MenuLooks.Look delta = looks.travel("delta");
        assertEquals(1, delta.order());
        assertNull(delta.color());
        assertNull(delta.icon());
        assertEquals(2, warnings.size(), warnings.toString());
        assertEquals("？？？", looks.travelUnknown().name());
        assertEquals(5, looks.dungeon("cave").recommendedLevel());
        assertEquals(List.of("小さな洞窟"), looks.dungeon("cave").lore());
        // an entry not in the file: as before
        assertEquals(MenuLooks.Look.NONE, looks.travel("nowhere"));
        assertEquals(List.of("delta", "atras", "x"), looks.travelOrder(List.of("atras", "x", "delta"), id -> id));
        // a broken file: as before, and said
        write("travel.yml", "entries: [broken\n");
        looks.reload();
        assertEquals("ファストトラベル", looks.travelTitle("ファストトラベル"));
        assertEquals(MenuLooks.Look.NONE, looks.travel("atras"));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("travel.yml")), warnings.toString());
    }
}
