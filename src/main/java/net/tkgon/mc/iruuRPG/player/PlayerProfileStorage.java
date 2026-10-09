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

    private final JavaPlugin plugin;
    private final File playerFolder;

    public PlayerProfileStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerFolder = new File(plugin.getDataFolder(), "players");
        if (!playerFolder.exists() && !playerFolder.mkdirs()) {
            plugin.getLogger().warning("Could not create player data folder: " + playerFolder.getAbsolutePath());
        }
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

        ConfigurationSection baseStats = yaml.getConfigurationSection("base-stats");
        if (baseStats != null) {
            for (String key : baseStats.getKeys(false)) {
                StatType.fromConfigKey(key)
                        .ifPresent(type -> profile.baseStats().set(type, baseStats.getDouble(key, 0.0)));
            }
        }

        profile.recalculate();
        profile.setCurrentHp(yaml.getDouble("current-hp", profile.maxHp()));
        profile.setCurrentMp(yaml.getDouble("current-mp", profile.maxMp()));
        return profile;
    }

    public void save(PlayerProfile profile) {
        File file = file(profile.uuid());
        YamlConfiguration yaml = new YamlConfiguration();

        yaml.set("level", profile.level());
        yaml.set("xp", profile.xp());
        yaml.set("current-hp", profile.currentHp());
        yaml.set("current-mp", profile.currentMp());
        yaml.set("class.id", profile.classId());
        yaml.set("class.skill-points", profile.skillPoints());
        yaml.set("class.spent-points", profile.spentSkillPoints());
        yaml.set("class.scroll", profile.classScroll());
        yaml.set("class.nodes", profile.classNodes().stream().sorted().toList());
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
        double defaultMaxHp = plugin.getConfig().getDouble("player.default-max-hp", 20.0);
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

    private void loadClassData(PlayerProfile profile, ConfigurationSection section) {
        if (section == null) return;

        profile.setClassId(section.getString("id", section.getString("class", "")));
        profile.setSkillPoints(section.getInt("skill-points", 0));
        profile.setSpentSkillPoints(section.getInt("spent-points", 0));
        profile.setClassScroll(section.getInt("scroll", 0));
        profile.classNodes().clear();
        profile.classNodes().addAll(section.getStringList("nodes"));

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
