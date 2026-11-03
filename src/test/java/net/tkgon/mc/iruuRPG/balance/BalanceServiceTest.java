package net.tkgon.mc.iruuRPG.balance;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.tkgon.mc.iruuRPG.combat.ComboSpec;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The balance of the yml repository (balance/<class>.yml) over the config, its ranges and the list of numbers for the tool site. */
class BalanceServiceTest {

    @TempDir
    File folder;

    /** The server's config: no combo numbers of its own (as on the server), the jar's config.yml as its defaults. */
    private static YamlConfiguration config() {
        YamlConfiguration jar = YamlConfiguration.loadConfiguration(new File("src/main/resources/config.yml"));
        YamlConfiguration server = new YamlConfiguration();
        server.setDefaults(jar);
        return server;
    }

    private void write(String name, String text) throws IOException {
        File dir = new File(folder, "balance");
        dir.mkdirs();
        Files.writeString(new File(dir, name).toPath(), text, StandardCharsets.UTF_8);
    }

    @Test
    void theRangesFollowTheKeys() {
        assertEquals(new BalanceService.Rule(1, 9, true), BalanceService.rule("small-charges", 2));
        assertEquals(new BalanceService.Rule(0, 1200, true), BalanceService.rule("small-draw-ticks", 50));
        assertEquals(new BalanceService.Rule(0, 600, false), BalanceService.rule("large-seconds", 22));
        assertEquals(new BalanceService.Rule(0, 1, false), BalanceService.rule("draw-guard", 0.25));
        assertEquals(new BalanceService.Rule(0, 20, false), BalanceService.rule("small-rate", 0.5));
        assertEquals(new BalanceService.Rule(1, 20, true), BalanceService.rule("hit-counts", 1));
        assertEquals(new BalanceService.Rule(0.02, 5, false), BalanceService.rule("hit-rate", 1));
        // anything else: 0 to the larger of 100 and ten times the default
        assertEquals(new BalanceService.Rule(0, 300, false), BalanceService.rule("something", 30));
        assertEquals(2.0, BalanceService.number(2, BalanceService.rule("small-charges", 2)));
        assertNull(BalanceService.number(2.5, BalanceService.rule("small-charges", 2)));
        assertNull(BalanceService.number("x", BalanceService.rule("small-rate", 1)));
        assertNull(BalanceService.numbers(List.of(1, 2, 3), 5, BalanceService.rule("hit-counts", 1)));
        assertEquals(List.of(1.0, 1.0, 2.0, 2.0, 1.0), BalanceService.numbers(List.of(1, 1, 2, 2, 1), 5, BalanceService.rule("hit-counts", 1)));
    }

    @Test
    void theFilesAreLaidOverTheConfigAndTheBadNumbersAreLeftOut() throws IOException {
        write("warrior.yml", """
                combo:
                  warrior_melee:
                    small-rate: 0.7
                    small-seconds: 999
                    no-such-key: 1
                    hit-rate: [0.5, 0.6, 0.4, 0.5, 1.2]
                    hit-counts: [1, 1, 3, 2, 1]
                  mage_range:
                    small-rate: 1.0
                cooldowns:
                  ult: 90
                  nope: 3
                ult:
                  lifesteal: 0.3
                  heal: oops
                """);
        write("nobody.yml", "combo: {}\n");
        write("mage.yml", "combo: [not: yml\n");
        List<String> warnings = new ArrayList<>();
        BalanceService balance = new BalanceService(folder, warnings::add);
        YamlConfiguration config = config();
        int laid = balance.apply(config);
        // laid: small-rate, hit-rate, hit-counts, cooldowns.ult
        assertEquals(4, laid);
        assertEquals(0.7, config.getDouble("combat.combo.warrior_melee.small-rate"), 1.0E-9);
        assertEquals(List.of(0.5, 0.6, 0.4, 0.5, 1.2), config.getList("combat.combo.warrior_melee.hit-rate"));
        assertEquals(List.of(1, 1, 3, 2, 1), config.getList("combat.combo.warrior_melee.hit-counts"));
        assertEquals(90.0, config.getDouble("combat.skill-slots.cooldown-seconds.warrior.ult"), 1.0E-9);
        // left out, the config's (the jar's) numbers stay: out of range, unknown, another class's combo
        assertEquals(6.0, config.getDouble("combat.combo.warrior_melee.small-seconds"), 1.0E-9);
        assertFalse(config.contains("combat.combo.warrior_melee.no-such-key"));
        assertEquals(config.getDefaults().getDouble("combat.combo.mage_range.small-rate"), config.getDouble("combat.combo.mage_range.small-rate"), 1.0E-9);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("small-seconds")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no-such-key")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("mage_range")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("nope")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("nobody.yml")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("mage.yml")));
        assertTrue(warnings.stream().anyMatch(w -> w.contains("ult.heal")));
        // the ULT: only the keys its file has
        assertEquals(Map.of("lifesteal", 0.3), balance.ultValues("warrior_strike", Map.of("lifesteal", 0.15, "guard", 0.3)));
        assertEquals(Map.of(), balance.ultValues("warrior_strike", Map.of("guard", 0.3)));
        // a second load starts again from the config (the file now gives nothing)
        write("warrior.yml", "combo: {}\n");
        YamlConfiguration fresh = config();
        assertEquals(0, balance.apply(fresh));
        assertEquals(0.5, fresh.getDouble("combat.combo.warrior_melee.small-rate"), 1.0E-9);
    }

    @Test
    void theListOfTheNumbersIsJsonWithTheConfigsAndTheValuesInUse() throws IOException {
        write("warrior.yml", "combo:\n  warrior_melee:\n    small-rate: 0.7\n");
        BalanceService balance = new BalanceService(folder, warning -> { });
        YamlConfiguration config = config();
        balance.apply(config);
        Map<String, ComboSpec> specs = Map.of("warrior_melee", new ComboSpec("warrior_melee",
                new double[]{0.6, 0.7, 0.45, 0.55, 1.3}, new int[]{1, 1, 2, 2, 1}, new double[6], new int[6], 40));
        JsonObject schema = JsonParser.parseString(balance.schemaJson(config, specs, null)).getAsJsonObject();
        JsonArray classes = schema.getAsJsonArray("classes");
        assertEquals(5, classes.size());
        JsonObject warrior = classes.get(0).getAsJsonObject();
        assertEquals("warrior", warrior.get("id").getAsString());
        JsonObject melee = warrior.getAsJsonArray("groups").get(0).getAsJsonObject();
        assertEquals("warrior_melee", melee.get("plan").getAsString());
        assertEquals("近接", melee.get("weapon").getAsString());
        JsonObject hitRate = melee.getAsJsonArray("fields").get(0).getAsJsonObject();
        assertEquals("hit-rate", hitRate.get("key").getAsString());
        assertEquals(1.3, hitRate.getAsJsonArray("default").get(4).getAsDouble(), 1.0E-9);
        JsonObject smallRate = null;
        for (var field : melee.getAsJsonArray("fields")) if (field.getAsJsonObject().get("key").getAsString().equals("small-rate")) smallRate = field.getAsJsonObject();
        assertEquals(0.5, smallRate.get("config").getAsDouble(), 1.0E-9);
        assertEquals(0.7, smallRate.get("value").getAsDouble(), 1.0E-9);
        assertFalse(smallRate.get("label").getAsString().isBlank(), "the comment of config.yml is its label");
        // the cooldowns of every class (ULT 100 s by default)
        boolean cooldowns = false;
        for (var group : warrior.getAsJsonArray("groups")) {
            JsonObject g = group.getAsJsonObject();
            if (!g.get("section").getAsString().equals("cooldowns")) continue;
            cooldowns = true;
            assertEquals(5, g.getAsJsonArray("fields").size());
            assertEquals(100.0, g.getAsJsonArray("fields").get(4).getAsJsonObject().get("value").getAsDouble(), 1.0E-9);
        }
        assertTrue(cooldowns);
    }
}
