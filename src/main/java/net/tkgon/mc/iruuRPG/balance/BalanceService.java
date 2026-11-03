package net.tkgon.mc.iruuRPG.balance;

import net.tkgon.mc.iruuRPG.classsystem.ClassSkillRegistry;
import net.tkgon.mc.iruuRPG.combat.ComboSpec;
import net.tkgon.mc.iruuRPG.combat.SkillSlots;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The classes' balance numbers kept in the yml repository (the user's wish of 2026-11-03): {@code balance/<class>.yml} in the data folder (the
 * clone the yml sync pulls), laid over the config in memory each time the data is loaded (start, {@code /iruurpg reload}, the yml sync's reload
 * about a minute after a commit), so they change without a restart. config.yml is never written: what a file does not give stays the config's (and
 * what the config does not give, the code's default).
 *
 * <p>A file, e.g. {@code balance/warrior.yml}:
 * <pre>
 * combo:                       # combat.combo.&lt;plan&gt;.&lt;key&gt; of the class's plans (warrior_melee, warrior_range, ...)
 *   warrior_melee:
 *     small-rate: 0.5
 *     hit-rate: [0.6, 0.7, 0.45, 0.55, 1.3]
 * cooldowns:                   # combat.skill-slots.cooldown-seconds.&lt;class&gt;.&lt;slot&gt; (small, medium, large, util, ult)
 *   ult: 100
 * ult:                         # the values of the class's ULT (cskill/&lt;id&gt;.yml values:)
 *   lifesteal: 0.2
 * </pre>
 * Only the numbers the plugin knows are taken: a combo key the jar's config.yml has (a number), {@code hit-rate} / {@code hit-counts} of a combo
 * (five numbers), a slot's cooldown, a value the ULT's file has. Each is kept within its range ({@link #rule}); anything else, or a number out of
 * its range, is left out with a warning (the reload report shows it), so a bad file never stops the plugin.
 *
 * <p>After each load the list of the numbers (their place, the config's value, the value in use, the comment of config.yml as their label and
 * their range) is written to {@code balance-schema.json} in the data folder, for the tool site's tab that edits them.
 */
public final class BalanceService {

    /** The classes and the plans of their combos (the config's {@code combat.combo.<plan>}), in the order the tool site shows them. */
    public static final Map<String, List<String>> PLANS = linked(
            "warrior", List.of("warrior_melee", "warrior", "warrior_range", "warrior_deploy"),
            "mage", List.of("mage", "mage_range", "mage_deploy", "mage_melee"),
            "healer", List.of("cleric_melee", "cleric_range", "cleric_deploy"),
            "gifted", List.of("ranger_melee", "ranger_range", "ranger_deploy"),
            "assassin", List.of("assassin_melee", "assassin_range", "assassin_deploy"));
    /** Each class's ULT (its cskill file). */
    public static final Map<String, String> ULTS = Map.of("warrior", "warrior_strike", "mage", "meteor", "healer", "sanctuary",
            "gifted", "instinct_release", "assassin", "satsujin");
    /** The classes' names shown on the site. */
    static final Map<String, String> NAMES = Map.of("warrior", "戦士", "mage", "メイジ", "healer", "クレリック", "gifted", "レンジャー", "assassin", "アサシン");
    /** The slots whose cooldowns a file may set. */
    static final List<String> SLOTS = List.of("small", "medium", "large", "util", "ult");

    /** The range a number may take, and whether it must be whole. */
    public record Rule(double min, double max, boolean whole) {
        boolean accepts(double value) {
            return Double.isFinite(value) && value >= min - 1.0E-9 && value <= max + 1.0E-9 && (!whole || Math.abs(value - Math.rint(value)) < 1.0E-9);
        }
    }

    private final File dataFolder;
    private final Consumer<String> warn;
    /** The ULT values of the files, by cskill id (normalized keys). */
    private Map<String, Map<String, Double>> ultValues = Map.of();
    /** What the config had before the files were laid over it, by path (the "config's value" the site shows). */
    private final Map<String, Object> before = new HashMap<>();

    public BalanceService(File dataFolder, Consumer<String> warn) {
        this.dataFolder = dataFolder;
        this.warn = warn;
    }

    // ---- the ranges -------------------------------------------------------------------------------------------------------

    /**
     * The range of a number by its key ({@code base}: its default, which widens the general range): charges 1 - 9, a count 1 to the larger of 50
     * and ten times the default, ticks 0 - 1200 (whole),
     * seconds 0 - 600, a guard or a cut 0 - 1, a share 0 - 5, a rate or a bonus 0 - 20, a percent 0 - 100, a radius, a range or a reach 0 - 64;
     * anything else 0 (or the default when it is below) to the larger of 100 and ten times the default.
     */
    public static Rule rule(String key, double base) {
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("hit-counts")) return new Rule(1, 20, true);
        if (k.equals("hit-rate")) return new Rule(0.02, 5, false);
        if (k.endsWith("charges")) return new Rule(1, 9, true);
        if (k.endsWith("-count") || k.equals("count")) return new Rule(1, Math.max(50.0, Math.abs(base) * 10.0), true);
        if (k.endsWith("-ticks") || k.endsWith("ticks")) return new Rule(0, 1200, true);
        if (k.endsWith("-seconds") || k.endsWith("seconds")) return new Rule(0, 600, false);
        if (k.endsWith("-guard") || k.endsWith("-cut") || k.equals("guard")) return new Rule(0, 1, false);
        if (k.endsWith("-share")) return new Rule(0, 5, false);
        if (k.endsWith("-rate") || k.endsWith("-bonus") || k.endsWith("rate")) return new Rule(0, 20, false);
        if (k.endsWith("-slow") || k.endsWith("-might") || k.endsWith("-protection") || k.endsWith("-percent") || k.endsWith("-pin")) return new Rule(0, 100, false);
        if (k.endsWith("-radius") || k.endsWith("-range") || k.endsWith("-reach") || k.endsWith("-length") || k.endsWith("radius")) return new Rule(0, 64, false);
        return new Rule(Math.min(0.0, base), Math.max(100.0, Math.abs(base) * 10.0), false);
    }

    // ---- laying the files over the config ----------------------------------------------------------------------------------

    /**
     * Reads {@code balance/*.yml} and lays their numbers over {@code config} (in memory). Never throws: a file that cannot be read, or a number
     * that is not allowed, is left out with a warning. How many numbers were laid.
     */
    public int apply(FileConfiguration config) {
        before.clear();
        Map<String, Map<String, Double>> ults = new HashMap<>();
        int laid = 0;
        File folder = new File(dataFolder, "balance");
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File file : files) {
                try {
                    laid += applyFile(config, file, ults);
                } catch (RuntimeException e) {
                    warn.accept("balance/" + file.getName() + ": could not be read (" + e.getMessage() + "); left out");
                }
            }
        }
        ultValues = Map.copyOf(ults);
        return laid;
    }

    private int applyFile(FileConfiguration config, File file, Map<String, Map<String, Double>> ults) {
        String classId = file.getName().replaceFirst("(?i)\\.yml$", "").toLowerCase(Locale.ROOT);
        String where = "balance/" + file.getName();
        if (!PLANS.containsKey(classId)) {
            warn.accept(where + ": no such class (" + String.join(", ", PLANS.keySet()) + "); left out");
            return 0;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
        } catch (IOException | org.bukkit.configuration.InvalidConfigurationException e) {
            warn.accept(where + ": not yml (" + firstLine(e.getMessage()) + "); left out");
            return 0;
        }
        Configuration defaults = config.getDefaults();
        int laid = 0;
        for (String top : yaml.getKeys(false)) {
            if (!List.of("combo", "cooldowns", "ult", "version").contains(top)) warn.accept(where + ": '" + top + "' is not combo, cooldowns or ult; left out");
        }
        ConfigurationSection combo = yaml.getConfigurationSection("combo");
        if (combo != null) {
            for (String plan : combo.getKeys(false)) {
                if (!PLANS.get(classId).contains(plan)) {
                    warn.accept(where + ": combo." + plan + " is not a combo of " + classId + " (" + String.join(", ", PLANS.get(classId)) + "); left out");
                    continue;
                }
                ConfigurationSection section = combo.getConfigurationSection(plan);
                if (section == null) continue;
                for (String key : section.getKeys(false)) {
                    String path = "combat.combo." + plan + "." + key;
                    Object value = section.get(key);
                    if (key.equals("hit-rate") || key.equals("hit-counts")) {
                        List<Double> list = numbers(value, 5, rule(key, 1.0));
                        if (list == null) {
                            warn.accept(where + ": combo." + plan + "." + key + " must be five numbers within " + range(rule(key, 1.0)) + "; left out");
                            continue;
                        }
                        laid += lay(config, path, key.equals("hit-counts") ? whole(list) : list);
                        continue;
                    }
                    if (defaults == null || !(defaults.get(path) instanceof Number base)) {
                        warn.accept(where + ": combo." + plan + "." + key + " is not a number of config.yml; left out");
                        continue;
                    }
                    Double number = number(value, rule(key, base.doubleValue()));
                    if (number == null) {
                        warn.accept(where + ": combo." + plan + "." + key + " = " + value + " is not a number within " + range(rule(key, base.doubleValue())) + "; left out");
                        continue;
                    }
                    laid += lay(config, path, base instanceof Integer || base instanceof Long ? (Object) (int) Math.round(number) : number);
                }
            }
        }
        ConfigurationSection cooldowns = yaml.getConfigurationSection("cooldowns");
        if (cooldowns != null) {
            for (String slot : cooldowns.getKeys(false)) {
                Rule rule = new Rule(0, 600, false);
                Double number = SLOTS.contains(slot) ? number(cooldowns.get(slot), rule) : null;
                if (number == null) {
                    warn.accept(where + ": cooldowns." + slot + " must be one of " + SLOTS + " with seconds within " + range(rule) + "; left out");
                    continue;
                }
                laid += lay(config, "combat.skill-slots.cooldown-seconds." + classId + "." + slot, number);
            }
        }
        ConfigurationSection ult = yaml.getConfigurationSection("ult");
        if (ult != null) {
            Map<String, Double> values = new HashMap<>();
            for (String key : ult.getKeys(false)) {
                String normalized = key.trim().toLowerCase(Locale.ROOT).replace('_', '-');
                Rule rule = rule(normalized, 1.0);
                Double number = number(ult.get(key), new Rule(Math.min(0.0, rule.min()), Math.max(rule.max(), 10000.0), false));
                if (number == null) {
                    warn.accept(where + ": ult." + key + " = " + ult.get(key) + " is not a number; left out");
                    continue;
                }
                values.put(normalized, number);
            }
            if (!values.isEmpty()) ults.put(ULTS.get(classId), values);
        }
        return laid;
    }

    private int lay(FileConfiguration config, String path, Object value) {
        if (!before.containsKey(path)) before.put(path, config.get(path));
        config.set(path, value);
        return 1;
    }

    /** {@code value} as a number within {@code rule}, else null. Pure, for the tests. */
    static Double number(Object value, Rule rule) {
        if (!(value instanceof Number number)) return null;
        double d = number.doubleValue();
        return rule.accepts(d) ? d : null;
    }

    /** {@code value} as {@code size} numbers within {@code rule}, else null. Pure, for the tests. */
    static List<Double> numbers(Object value, int size, Rule rule) {
        if (!(value instanceof List<?> list) || list.size() != size) return null;
        List<Double> out = new ArrayList<>();
        for (Object item : list) {
            Double d = number(item, rule);
            if (d == null) return null;
            out.add(d);
        }
        return out;
    }

    private static List<Integer> whole(List<Double> list) {
        List<Integer> out = new ArrayList<>();
        for (Double d : list) out.add((int) Math.round(d));
        return out;
    }

    private static String range(Rule rule) {
        return rule.min() + " - " + rule.max() + (rule.whole() ? " (whole)" : "");
    }

    /**
     * The ULT values the files give for class skill {@code id} (keys normalized as the class skills' are), to lay over its file's values
     * ({@link ClassSkillRegistry#applyBalance}); only the keys the skill's file has are kept (the others are warned once).
     */
    public Map<String, Double> ultValues(String id, Map<String, Double> fileValues) {
        Map<String, Double> given = ultValues.get(id == null ? "" : id.toLowerCase(Locale.ROOT));
        if (given == null || given.isEmpty()) return Map.of();
        Map<String, Double> out = new HashMap<>();
        for (Map.Entry<String, Double> entry : given.entrySet()) {
            if (fileValues.containsKey(entry.getKey())) out.put(entry.getKey(), entry.getValue());
            else warn.accept("balance: ult." + entry.getKey() + " is not a value of " + id + " (" + String.join(", ", new java.util.TreeSet<>(fileValues.keySet())) + "); left out");
        }
        return out;
    }

    // ---- the list of the numbers for the tool site ---------------------------------------------------------------------------

    /**
     * Writes {@code balance-schema.json} in the data folder: for each class, its combos' numbers (the jar's config.yml has them, and
     * {@code hit-rate} / {@code hit-counts} of each combo with a spec), its slots' cooldowns and its ULT's values; each with its place, the
     * config's value (before the files), the value in use, the comment of config.yml, its range. Never throws (a failure is warned).
     */
    public void writeSchema(FileConfiguration config, Map<String, ComboSpec> specs, ClassSkillRegistry cskills) {
        try {
            String json = schemaJson(config, specs, cskills);
            File out = new File(dataFolder, "balance-schema.json");
            File temp = new File(dataFolder, "balance-schema.json.tmp");
            Files.writeString(temp.toPath(), json, StandardCharsets.UTF_8);
            Files.move(temp.toPath(), out.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            warn.accept("balance: could not write balance-schema.json (" + e.getMessage() + ")");
        }
    }

    String schemaJson(FileConfiguration config, Map<String, ComboSpec> specs, ClassSkillRegistry cskills) {
        Configuration defaults = config.getDefaults();
        StringBuilder json = new StringBuilder("{\"version\":1,\"classes\":[");
        boolean firstClass = true;
        for (Map.Entry<String, List<String>> klass : PLANS.entrySet()) {
            String classId = klass.getKey();
            if (!firstClass) json.append(',');
            firstClass = false;
            json.append("{\"id\":").append(quote(classId)).append(",\"name\":").append(quote(NAMES.get(classId))).append(",\"groups\":[");
            boolean firstGroup = true;
            for (String plan : klass.getValue()) {
                List<String> fields = new ArrayList<>();
                ComboSpec spec = specs.get(plan);
                if (spec != null) {
                    fields.add(listField(config, plan, "hit-rate", "通常1〜5: 1回の倍率 (武器ダメージ比)", toList(spec.rates())));
                    fields.add(listField(config, plan, "hit-counts", "通常1〜5: 当たる回数", toList(spec.hits())));
                }
                ConfigurationSection section = defaults == null ? null : defaults.getConfigurationSection("combat.combo." + plan);
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        String path = "combat.combo." + plan + "." + key;
                        if (!(defaults.get(path) instanceof Number base)) continue;
                        fields.add(field("combo", plan, key, path, label(defaults, path), base, valueBefore(config, path), config.get(path), rule(key, base.doubleValue())));
                    }
                }
                if (fields.isEmpty()) continue;
                if (!firstGroup) json.append(',');
                firstGroup = false;
                json.append("{\"section\":\"combo\",\"plan\":").append(quote(plan)).append(",\"weapon\":").append(quote(weapon(plan)))
                        .append(",\"label\":").append(quote(label(defaults, "combat.combo." + plan))).append(",\"fields\":[").append(String.join(",", fields)).append("]}");
            }
            // the slots' cooldowns
            List<String> cooldowns = new ArrayList<>();
            ConfigurationSection slotDefaults = defaults == null ? null : defaults.getConfigurationSection("combat.skill-slots.cooldown-seconds");
            for (String slot : SLOTS) {
                String path = "combat.skill-slots.cooldown-seconds." + classId + "." + slot;
                double base = SkillSlots.cooldownSeconds(slotDefaults, classId, SkillSlots.Slot.valueOf(slot.toUpperCase(Locale.ROOT)));
                Object now = config.contains(path) ? config.get(path) : base;
                cooldowns.add(field("cooldowns", "", slot, path, slotLabel(slot), base, before.containsKey(path) ? orElse(before.get(path), base) : now, now, new Rule(0, 600, false)));
            }
            json.append(firstGroup ? "" : ",").append("{\"section\":\"cooldowns\",\"plan\":\"\",\"weapon\":\"\",\"label\":")
                    .append(quote("スキルのクールタイム (秒)。クラスの技が自分の秒を持つ武器 (近接・遠距離など) では、そちらが使われる")).append(",\"fields\":[")
                    .append(String.join(",", cooldowns)).append("]}");
            // the ULT's values
            String ult = ULTS.get(classId);
            Map<String, Double> fileValues = cskills == null ? Map.of() : cskills.fileValues(ult);
            if (!fileValues.isEmpty()) {
                List<String> fields = new ArrayList<>();
                Map<String, Double> laid = ultValues.getOrDefault(ult, Map.of());
                for (String key : new java.util.TreeSet<>(fileValues.keySet())) {
                    double base = fileValues.get(key);
                    Rule rule = rule(key, base);
                    fields.add(field("ult", ult, key, "cskill/" + ult + ".yml values." + key, "", base, base, laid.getOrDefault(key, base),
                            new Rule(Math.min(0.0, rule.min()), Math.max(rule.max(), 10000.0), false)));
                }
                json.append(",{\"section\":\"ult\",\"plan\":").append(quote(ult)).append(",\"weapon\":\"\",\"label\":")
                        .append(quote("ULT (cskill/" + ult + ".yml の values)")).append(",\"fields\":[").append(String.join(",", fields)).append("]}");
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    private String listField(FileConfiguration config, String plan, String key, String label, List<Double> spec) {
        String path = "combat.combo." + plan + "." + key;
        Object before = valueBefore(config, path);
        Object now = config.get(path);
        return field("combo", plan, key, path, label, spec, before == null ? spec : before, now == null ? spec : now, rule(key, 1.0));
    }

    private Object valueBefore(FileConfiguration config, String path) {
        return before.containsKey(path) ? before.get(path) : config.get(path);
    }

    private static Object orElse(Object value, Object fallback) {
        return value == null ? fallback : value;
    }

    private static String field(String section, String plan, String key, String path, String label, Object base, Object config, Object value, Rule rule) {
        return "{\"section\":" + quote(section) + ",\"plan\":" + quote(plan) + ",\"key\":" + quote(key) + ",\"path\":" + quote(path)
                + ",\"label\":" + quote(label) + ",\"default\":" + json(base) + ",\"config\":" + json(orElse(config, base)) + ",\"value\":" + json(orElse(value, base))
                + ",\"min\":" + json(rule.min()) + ",\"max\":" + json(rule.max()) + ",\"whole\":" + rule.whole() + "}";
    }

    /** The comment of config.yml on {@code path} (the one at the end of its line, else the lines above it), "" when none. */
    private static String label(Configuration defaults, String path) {
        if (defaults == null) return "";
        try {
            List<String> inline = defaults.getInlineComments(path);
            List<String> lines = inline == null || inline.isEmpty() ? defaults.getComments(path) : inline;
            if (lines == null) return "";
            return String.join(" ", lines.stream().filter(line -> line != null && !line.isBlank()).map(String::trim).toList());
        } catch (RuntimeException | LinkageError e) {
            return "";
        }
    }

    private static String slotLabel(String slot) {
        return switch (slot) {
            case "small" -> "小";
            case "medium" -> "中";
            case "large" -> "大";
            case "util" -> "util";
            default -> "ULT";
        };
    }

    /** The kind of weapon a plan is for, as the site shows it. */
    static String weapon(String plan) {
        if (plan.endsWith("_melee") || plan.equals("warrior")) return "近接";
        if (plan.endsWith("_range") || plan.equals("mage")) return "遠距離";
        if (plan.endsWith("_deploy")) return "ランタン";
        return "";
    }

    private static List<Double> toList(double[] values) {
        List<Double> out = new ArrayList<>();
        for (double v : values) out.add(v);
        return out;
    }

    private static List<Double> toList(int[] values) {
        List<Double> out = new ArrayList<>();
        for (int v : values) out.add((double) v);
        return out;
    }

    static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number number) {
            double d = number.doubleValue();
            if (!Double.isFinite(d)) return "null";
            return d == Math.rint(d) && Math.abs(d) < 1.0E15 ? Long.toString((long) d) : Double.toString(d);
        }
        if (value instanceof Boolean b) return b.toString();
        if (value instanceof List<?> list) {
            List<String> parts = new ArrayList<>();
            for (Object item : list) parts.add(json(item));
            return "[" + String.join(",", parts) + "]";
        }
        return quote(String.valueOf(value));
    }

    static String quote(String text) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : (text == null ? "" : text).toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static String firstLine(String message) {
        if (message == null) return "";
        int at = message.indexOf('\n');
        return at < 0 ? message : message.substring(0, at);
    }

    private static Map<String, List<String>> linked(Object... pairs) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            @SuppressWarnings("unchecked") List<String> plans = (List<String>) pairs[i + 1];
            out.put((String) pairs[i], plans);
        }
        return java.util.Collections.unmodifiableMap(out);
    }
}
