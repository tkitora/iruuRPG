package net.tkgon.mc.iruuRPG.tutorial;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** One line of the tutorial script: something said or narrated, and/or an action. */
record TutorialStep(String say, String narrate, String action, Map<String, Object> args, int ticks) {

    boolean hasSay() {
        return say != null && !say.isBlank();
    }

    boolean hasNarrate() {
        return narrate != null && !narrate.isBlank();
    }

    /** The script plus its extra lines (nudge, class speeches, class weapons). */
    record Script(List<TutorialStep> steps, YamlConfiguration yaml) {
    }

    static Script load(org.bukkit.plugin.java.JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "tutorial/debug.yml");
        if (!file.exists()) {
            plugin.saveResource("tutorial/debug.yml", false);
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        if (!yaml.contains("steps")) {
            try (Reader reader = new InputStreamReader(plugin.getResource("tutorial/debug.yml"), StandardCharsets.UTF_8)) {
                yaml = YamlConfiguration.loadConfiguration(reader);
            } catch (Exception e) {
                plugin.getLogger().warning("tutorial: could not read the bundled script: " + e.getMessage());
            }
        }

        List<TutorialStep> steps = new ArrayList<>();
        for (Map<?, ?> raw : yaml.getMapList("steps")) {
            String say = raw.get("say") == null ? null : String.valueOf(raw.get("say"));
            String narrate = raw.get("narrate") == null ? null : String.valueOf(raw.get("narrate"));
            int ticks = raw.get("ticks") instanceof Number number ? number.intValue() : -1;

            String action = null;
            Map<String, Object> args = new HashMap<>();
            Object doValue = raw.get("do");
            if (doValue instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    args.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                action = args.get("action") == null ? null : String.valueOf(args.get("action"));
            } else if (doValue != null) {
                action = String.valueOf(doValue);
            }
            steps.add(new TutorialStep(say, narrate, action, args, ticks));
        }
        return new Script(steps, yaml);
    }
}
