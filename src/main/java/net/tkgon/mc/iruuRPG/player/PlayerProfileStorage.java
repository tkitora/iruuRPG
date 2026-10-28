package net.tkgon.mc.iruuRPG.player;

import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

public final class PlayerProfileStorage {

    /** The version of how the base max HP is stored (2: the initial HP is player.initial-max-hp; 3: everybody starts again from it, once). */
    private static final int HP_FORMAT = 3;

    private final JavaPlugin plugin;
    private final File playerFolder;

    public PlayerProfileStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerFolder = new File(plugin.getDataFolder(), "players");
        if (!playerFolder.exists() && !playerFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create player data folder: " + playerFolder.getAbsolutePath());
        }
    }

    /**
     * The base max HP of every saved player that is not yet in base-hp-format 3 becomes player.initial-max-hp, in the files at once (a player is also migrated when loaded; this
     * is for the players who are not online). The current HP is left as it is: loading clamps it to the new maximum. Answers how many files changed.
     */
    public int migrateStoredHp() {
        double initialHp = plugin.getConfig().getDouble("player.initial-max-hp", 20.0);
        int changed = sweepHp(playerFolder, initialHp, message -> plugin.getLogger().warning(message));
        if (changed > 0) plugin.getLogger().info("players: the base max HP of " + changed + " saved player(s) set to " + initialHp);
        return changed;
    }

    static int sweepHp(File folder, double initialHp, java.util.function.Consumer<String> warn) {
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return 0;
        int changed = 0;
        for (File file : files) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                if (yaml.getInt("base-hp-format", 1) >= HP_FORMAT) continue;
                yaml.set("base-stats.max-hp", initialHp);
                yaml.set("base-hp-format", HP_FORMAT);
                yaml.save(file);
                changed++;
            } catch (IOException | RuntimeException e) {
                warn.accept("players: could not migrate " + file.getName() + ": " + e.getMessage());
            }
        }
        return changed;
    }

    public PlayerProfile load(UUID uuid) {
        PlayerProfile profile = createDefaultProfile(uuid);
        File file = file(uuid);

        if (!file.exists()) {
            return profile;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        profile.setLevel(yaml.getInt("level", profile.level()));
        profile.setXp(yaml.getLong("xp", profile.xp()));
        loadClassData(profile, yaml.getConfigurationSection("class"));
        loadQuestData(profile, yaml.getConfigurationSection("quest"));
        profile.setCraftXp(yaml.getLong("craft.xp", 0L));

        ConfigurationSection baseStats = yaml.getConfigurationSection("base-stats");
        if (baseStats != null) {
            for (String key : baseStats.getKeys(false)) {
                StatType.fromConfigKey(key)
                        .ifPresent(type -> profile.baseStats().set(type, baseStats.getDouble(key, 0.0)));
            }
        }

        // base-hp-format 3: the base max HP of every player is set to player.initial-max-hp once (the earlier 200 is gone); saved again, it is not touched
        boolean raiseHp = yaml.getInt("base-hp-format", 1) < HP_FORMAT;
        double initialHp = plugin.getConfig().getDouble("player.initial-max-hp", 20.0);
        if (raiseHp) {
            profile.baseStats().set(StatType.MAX_HP, initialHp);
        }

        profile.recalculate();
        profile.setCurrentHp(raiseHp ? profile.maxHp() : yaml.getDouble("current-hp", profile.maxHp()));
        profile.setCurrentMp(yaml.getDouble("current-mp", profile.maxMp()));
        return profile;
    }

    public void save(PlayerProfile profile) {
        File file = file(profile.uuid());
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("base-hp-format", HP_FORMAT);
        yaml.set("level", profile.level());
        yaml.set("xp", profile.xp());
        yaml.set("current-hp", profile.currentHp());
        yaml.set("current-mp", profile.currentMp());
        yaml.set("craft.xp", profile.craftXp());
        yaml.set("quest.currency", profile.currency());
        yaml.set("quest.sub-day", profile.subQuestDay());
        yaml.set("quest.completed", new java.util.ArrayList<>(profile.completedQuests()));
        yaml.set("quest.sub-completed", new java.util.ArrayList<>(profile.completedSubQuests()));
        for (var entry : profile.questProgress().entrySet()) {
            yaml.set("quest.active." + entry.getKey(), entry.getValue());
        }
        yaml.set("class.id", profile.classId());
        yaml.set("class.chosen", profile.classChosen());
        for (var entry : profile.classLevels().entrySet()) {
            yaml.set("class.levels." + entry.getKey(), entry.getValue());
        }
        for (var entry : profile.activeClassSkills().entrySet()) {
            yaml.set("class.active-skills." + entry.getKey(), entry.getValue());
        }

        for (StatType type : StatType.values()) {
            if (!type.isPersistentBaseStat()) continue;

            double value = profile.baseStats().get(type);
            if (Math.abs(value) >= 1.0E-9) {
                yaml.set("base-stats." + type.key(), value);
            }
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save player data: " + profile.uuid(), e);
        }
    }

    private PlayerProfile createDefaultProfile(UUID uuid) {
        double defaultMaxHp = plugin.getConfig().getDouble("player.initial-max-hp", 20.0);
        double defaultHpRegen = plugin.getConfig().getDouble("player.default-hp-regen-per-3s", 1.0);
        double defaultMaxMp = plugin.getConfig().getDouble("player.default-max-mp", 20.0);
        double defaultMpRegen = plugin.getConfig().getDouble("player.default-mp-regen-per-3s", 5.0);

        PlayerProfile profile = new PlayerProfile(uuid, defaultMaxHp, defaultMaxMp);
        profile.baseStats().set(StatType.HP_REGEN, defaultHpRegen);
        profile.baseStats().set(StatType.MP_REGEN, defaultMpRegen);
        profile.baseStats().set(StatType.CRIT_CHANCE, 15.0);
        profile.baseStats().set(StatType.CRIT_DAMAGE, 50.0);
        profile.recalculate();
        return profile;
    }

    private void loadQuestData(PlayerProfile profile, ConfigurationSection section) {
        if (section == null) return;

        profile.setCurrency(section.getLong("currency", 0L));
        profile.setSubQuestDay(section.getString("sub-day", ""));
        profile.completedQuests().clear();
        profile.completedQuests().addAll(section.getStringList("completed"));
        profile.completedSubQuests().clear();
        profile.completedSubQuests().addAll(section.getStringList("sub-completed"));
        profile.questProgress().clear();
        ConfigurationSection active = section.getConfigurationSection("active");
        if (active != null) {
            for (String key : active.getKeys(false)) {
                profile.questProgress().put(key, active.getInt(key, 0));
            }
        }
    }

    private void loadClassData(PlayerProfile profile, ConfigurationSection section) {
        if (section == null) return;

        profile.setClassId(section.getString("id", section.getString("class", "")));
        profile.setClassChosen(section.getBoolean("chosen", false));
        profile.classLevels().clear();
        ConfigurationSection levels = section.getConfigurationSection("levels");
        if (levels != null) {
            for (String key : levels.getKeys(false)) {
                int value = levels.getInt(key, 0);
                if (value > 0) {
                    profile.classLevels().put(key, value);
                }
            }
        }

        profile.activeClassSkills().clear();
        ConfigurationSection activeSkills = section.getConfigurationSection("active-skills");
        if (activeSkills != null) {
            for (String key : activeSkills.getKeys(false)) {
                String value = activeSkills.getString(key);
                if (value != null && !value.isBlank()) {
                    profile.activeClassSkills().put(key, value);
                }
            }
        }
    }

    private File file(UUID uuid) {
        return new File(playerFolder, uuid + ".yml");
    }
}
