package net.tkgon.mc.iruuRPG.sync;

import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * What a reload found: counts, and the problems in the yml files, one per line as "file: id: reason". The problems
 * come from two places: warnings the registries log while loading (caught while the reload runs; the log keeps them
 * too), and checks run over the files afterwards (duplicate ids, references to items that do not exist, unknown
 * materials and entity types, unknown stat keys, missing required fields). Written to plugins/iruuRPG/reload-report.txt.
 */
public final class ReloadReport {

    /** Stat sections inside "stats:" that hold element stats, not stat keys. */
    private static final Set<String> ELEMENT_SECTIONS = Set.of("element-stats", "elements", "element", "damage", "damage-percent", "resist");

    private final List<String> header = new ArrayList<>();
    private final List<String> problems = new ArrayList<>();
    private final List<String> logged = new ArrayList<>();

    public List<String> problems() {
        return problems;
    }

    public int warningCount() {
        return problems.size() + logged.size();
    }

    // ---- catching the registries' own warnings ------------------------------------------------------------------

    /** Runs {@code reload} while collecting WARNING/SEVERE lines from {@code logger} (they still reach the log). */
    public void capture(Logger logger, Runnable reload) {
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() < Level.WARNING.intValue()) return;
                String message = record.getMessage() == null ? "" : record.getMessage();
                // unknown stat keys are reported with their file by the checks below
                if (message.contains("unknown stat key")) return;
                if (record.getThrown() != null) message += " (" + record.getThrown() + ")";
                logged.add(message);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            reload.run();
        } catch (RuntimeException e) {
            logged.add("読み直しの途中で例外: " + e);
            logger.log(Level.WARNING, "reload failed", e);
        } finally {
            logger.removeHandler(handler);
        }
    }

    // ---- checking the files --------------------------------------------------------------------------------------

    public void check(File dataFolder, RpgItemRegistry itemRegistry) {
        checkItems(new File(dataFolder, "items"));
        checkMobs(new File(dataFolder, "mobs"), itemRegistry);
        checkRecipes(new File(dataFolder, "crafting"), itemRegistry);
    }

    private void checkItems(File folder) {
        Map<String, List<String>> seen = new LinkedHashMap<>();
        for (File file : sorted(folder)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (Map.Entry<String, ConfigurationSection> entry : entries(yaml, "items", "material", file).entrySet()) {
                String id = entry.getKey();
                ConfigurationSection section = entry.getValue();
                seen.computeIfAbsent(id, key -> new ArrayList<>()).add(file.getName());
                String material = section.getString("material");
                if (material == null || material.isBlank()) {
                    add(file, id, "material がない");
                } else if (Material.matchMaterial(material.split(":")[0].trim().toUpperCase(Locale.ROOT)) == null) {
                    add(file, id, "存在しない素材 (material) '" + material.split(":")[0].trim() + "'");
                }
                if (!section.isSet("name")) add(file, id, "name がない");
                checkStats(file, id, section.getConfigurationSection("stats"));
            }
        }
        duplicates("items", seen);
    }

    private void checkMobs(File folder, RpgItemRegistry itemRegistry) {
        Map<String, List<String>> seen = new LinkedHashMap<>();
        for (File file : sorted(folder)) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            for (Map.Entry<String, ConfigurationSection> entry : entries(yaml, "mobs", "type", file).entrySet()) {
                String id = entry.getKey();
                ConfigurationSection section = entry.getValue();
                seen.computeIfAbsent(id, key -> new ArrayList<>()).add(file.getName());
                String type = section.getString("type", section.getString("entity-type"));
                if (type == null || type.isBlank()) {
                    add(file, id, "type がない (ゾンビになる)");
                } else {
                    try {
                        EntityType.valueOf(type.trim().replace('-', '_').toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        add(file, id, "存在しない種類 (type) '" + type + "'");
                    }
                }
                checkStats(file, id, section.getConfigurationSection("stats"));
                checkArea(file, id, section.get("area"));

                ConfigurationSection equipment = section.getConfigurationSection("equipment");
                if (equipment != null) {
                    for (String slot : equipment.getKeys(false)) {
                        if (slot.equalsIgnoreCase("drop-chances")) continue;
                        ConfigurationSection piece = equipment.getConfigurationSection(slot);
                        String itemId = piece != null
                                ? piece.getString("item", piece.getString("item-id", piece.getString("rpg-item")))
                                : equipment.getString(slot);
                        if (piece != null && piece.isSet("material") && Material.matchMaterial(piece.getString("material", "")) == null) {
                            add(file, id, "装備 " + slot + ": 存在しない素材 '" + piece.getString("material") + "'");
                        }
                        if (itemId == null || itemId.isBlank()) continue;
                        if (piece == null && Material.matchMaterial(itemId) != null) continue; // a plain vanilla material
                        if (itemRegistry.find(itemId.trim()).isEmpty()) add(file, id, "装備 " + slot + ": 存在しないアイテム '" + itemId + "'");
                    }
                }

                for (String itemId : dropIds(section)) {
                    if (itemRegistry.find(itemId).isEmpty()) add(file, id, "ドロップ: 存在しないアイテム '" + itemId + "'");
                }
            }
        }
        duplicates("mobs", seen);
    }

    private List<String> dropIds(ConfigurationSection mob) {
        List<String> ids = new ArrayList<>();
        ConfigurationSection drops = mob.getConfigurationSection("drops");
        if (drops != null) {
            for (String key : drops.getKeys(false)) {
                if (!key.equalsIgnoreCase("drop-chances")) ids.add(key);
            }
            return ids;
        }
        for (Map<?, ?> entry : mob.getMapList("drops")) {
            for (String key : List.of("item", "item-id", "id", "rpg-item")) {
                Object value = entry.get(key);
                if (value != null) {
                    ids.add(String.valueOf(value));
                    break;
                }
            }
        }
        for (String raw : mob.getStringList("drops")) ids.add(raw);
        return ids;
    }

    private void checkRecipes(File folder, RpgItemRegistry itemRegistry) {
        Map<String, List<String>> seen = new LinkedHashMap<>();
        for (File file : sorted(folder)) {
            ConfigurationSection recipes = YamlConfiguration.loadConfiguration(file).getConfigurationSection("recipes");
            if (recipes == null) continue;
            for (String id : recipes.getKeys(false)) {
                ConfigurationSection recipe = recipes.getConfigurationSection(id);
                if (recipe == null) continue;
                seen.computeIfAbsent(id, key -> new ArrayList<>()).add(file.getName());
                String result = recipe.getString("result", "");
                if (result.isBlank()) {
                    add(file, id, "result がない");
                } else if (itemRegistry.find(result).isEmpty()) {
                    add(file, id, "完成品 (result) が存在しないアイテム '" + result + "'");
                }
                ConfigurationSection ingredients = recipe.getConfigurationSection("ingredients");
                if (ingredients == null || ingredients.getKeys(false).isEmpty()) {
                    add(file, id, "ingredients がない");
                } else {
                    for (String itemId : ingredients.getKeys(false)) {
                        if (itemRegistry.find(itemId).isEmpty()) add(file, id, "素材 (ingredients) が存在しないアイテム '" + itemId + "'");
                    }
                }
            }
        }
        duplicates("crafting", seen);
    }

    /** area: a name or a list of names. Names that are not dungeon ids are fine (they are field names). */
    private void checkArea(File file, String id, Object area) {
        if (area == null || area instanceof String) return;
        if (area instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof String)) add(file, id, "area のリストに文字でないもの '" + entry + "' がある (無視する)");
            }
            return;
        }
        add(file, id, "area は文字か文字のリストで書く ('" + area + "' は無視する)");
    }

    private void checkStats(File file, String id, ConfigurationSection stats) {
        if (stats == null) return;
        for (String key : stats.getKeys(false)) {
            if (stats.isConfigurationSection(key) || ELEMENT_SECTIONS.contains(key.toLowerCase(Locale.ROOT))) continue;
            if (StatType.fromConfigKey(key).isEmpty()) add(file, id, "未知のステータスキー '" + key + "'");
        }
    }

    /** Ids defined in more than one file: the last file (by name) is the one that is used. */
    private void duplicates(String folder, Map<String, List<String>> seen) {
        for (Map.Entry<String, List<String>> entry : seen.entrySet()) {
            List<String> files = entry.getValue();
            if (files.size() < 2) continue;
            String used = files.get(files.size() - 1);
            problems.add(folder + "/" + used + ": " + entry.getKey() + ": 同じ id が複数のファイルにある (" + String.join(", ", files) + ")。採用: " + used);
        }
    }

    /** The definitions of a file: under the given section, a whole-file definition, or top-level sections. */
    private Map<String, ConfigurationSection> entries(YamlConfiguration yaml, String sectionName, String marker, File file) {
        Map<String, ConfigurationSection> result = new LinkedHashMap<>();
        ConfigurationSection section = yaml.getConfigurationSection(sectionName);
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(id);
                if (entry != null) result.put(id, entry);
            }
            return result;
        }
        if (yaml.contains(marker)) {
            String name = file.getName();
            result.put(name.substring(0, name.lastIndexOf('.')), yaml);
            return result;
        }
        for (String id : yaml.getKeys(false)) {
            ConfigurationSection entry = yaml.getConfigurationSection(id);
            if (entry != null) result.put(id, entry);
        }
        return result;
    }

    private File[] sorted(File folder) {
        File[] files = folder.listFiles(file -> file.isFile()
                && (file.getName().toLowerCase(Locale.ROOT).endsWith(".yml") || file.getName().toLowerCase(Locale.ROOT).endsWith(".yaml")));
        if (files == null) return new File[0];
        Arrays.sort(files, (left, right) -> left.getName().compareToIgnoreCase(right.getName()));
        return files;
    }

    private void add(File file, String id, String reason) {
        problems.add(file.getParentFile().getName() + "/" + file.getName() + ": " + id + ": " + reason);
    }

    // ---- writing -------------------------------------------------------------------------------------------------

    public void header(String before, String after, String trigger, int items, int mobs, int recipes, int skills) {
        header.add("iruuRPG 読み直しレポート");
        header.add("時刻: " + ZonedDateTime.now(ZoneId.of("Asia/Tokyo")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + " (日本時間)");
        header.add("きっかけ: " + trigger);
        header.add("コミット: " + shortSha(before) + " -> " + shortSha(after));
        header.add("件数: items " + items + " / mobs " + mobs + " / recipes " + recipes + " / iskill " + skills);
        header.add("警告: " + warningCount() + " 件");
    }

    public List<String> lines() {
        List<String> lines = new ArrayList<>(header);
        if (!problems.isEmpty()) {
            lines.add("");
            lines.add("[検査] (ファイル: id: 理由)");
            for (String problem : problems) lines.add("- " + problem);
        }
        if (!logged.isEmpty()) {
            lines.add("");
            lines.add("[読み込み中のログ]");
            for (String line : logged) lines.add("- " + line);
        }
        return lines;
    }

    public void write(File file) throws IOException {
        Files.write(file.toPath(), lines(), StandardCharsets.UTF_8);
    }

    private static String shortSha(String sha) {
        if (sha == null || sha.isBlank()) return "(なし)";
        return sha.length() > 7 ? sha.substring(0, 7) : sha;
    }
}
