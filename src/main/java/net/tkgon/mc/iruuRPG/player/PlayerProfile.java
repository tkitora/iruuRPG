package net.tkgon.mc.iruuRPG.player;

import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerProfile {

    private final UUID uuid;
    private int level = 1;
    private long xp;
    private double currentHp;
    private double currentMp;
    private String classId = "";
    private boolean classChosen;
    private final Map<String, Integer> classLevels = new HashMap<>();
    private final Map<String, String> activeClassSkills = new HashMap<>();

    private final StatSet baseStats = new StatSet();
    private final StatSet equipmentStats = new StatSet();
    private final StatSet classStats = new StatSet();
    private final StatSet buffStats = new StatSet();
    private StatSet finalStats = new StatSet();

    private final ElementStatSet baseElementStats = new ElementStatSet();
    private final ElementStatSet equipmentElementStats = new ElementStatSet();
    private final ElementStatSet buffElementStats = new ElementStatSet();
    private ElementStatSet finalElementStats = new ElementStatSet();

    public PlayerProfile(UUID uuid, double defaultMaxHp, double defaultMaxMp) {
        this.uuid = uuid;
        this.currentHp = defaultMaxHp;
        this.currentMp = defaultMaxMp;
        baseStats.set(StatType.MAX_HP, defaultMaxHp);
        baseStats.set(StatType.MAX_MP, defaultMaxMp);
        recalculate();
    }

    public UUID uuid() {
        return uuid;
    }

    public int level() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public long xp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0, xp);
    }

    public double currentHp() {
        return currentHp;
    }

    public void setCurrentHp(double currentHp) {
        this.currentHp = clamp(currentHp, 0.0, maxHp());
    }

    public double currentMp() {
        return currentMp;
    }

    public void setCurrentMp(double currentMp) {
        this.currentMp = clamp(currentMp, 0.0, maxMp());
    }

    public String classId() {
        return classId;
    }

    public void setClassId(String classId) {
        this.classId = classId == null ? "" : classId.trim().toLowerCase();
    }

    public boolean classChosen() {
        return classChosen;
    }

    public void setClassChosen(boolean classChosen) {
        this.classChosen = classChosen;
    }

    /** Levels invested per spend node (1 SP per level). */
    public Map<String, Integer> classLevels() {
        return classLevels;
    }

    public int classLevel(String nodeId) {
        return classLevels.getOrDefault(nodeId, 0);
    }

    public int spentSkillPoints() {
        return classLevels.values().stream().mapToInt(Integer::intValue).sum();
    }

    public Map<String, String> activeClassSkills() {
        return activeClassSkills;
    }

    public StatSet baseStats() {
        return baseStats;
    }

    public StatSet equipmentStats() {
        return equipmentStats;
    }

    public StatSet classStats() {
        return classStats;
    }

    public StatSet buffStats() {
        return buffStats;
    }

    public StatSet finalStats() {
        return finalStats;
    }

    public ElementStatSet baseElementStats() {
        return baseElementStats;
    }

    public ElementStatSet equipmentElementStats() {
        return equipmentElementStats;
    }

    public ElementStatSet buffElementStats() {
        return buffElementStats;
    }

    public ElementStatSet finalElementStats() {
        return finalElementStats;
    }

    public void replaceEquipmentStats(StatSet stats, ElementStatSet elementStats) {
        equipmentStats.replaceWith(stats);
        equipmentElementStats.replaceWith(elementStats);
        recalculate();
    }

    public double maxHp() {
        return Math.max(1.0, finalStats.get(StatType.MAX_HP));
    }

    public double maxMp() {
        return Math.max(1.0, finalStats.get(StatType.MAX_MP));
    }

    public void recalculate() {
        StatSet nextStats = baseStats.copy();
        nextStats.addAll(equipmentStats);
        nextStats.addAll(classStats);
        nextStats.addAll(buffStats);
        finalStats = nextStats;

        ElementStatSet nextElementStats = baseElementStats.copy();
        nextElementStats.addAll(equipmentElementStats);
        nextElementStats.addAll(buffElementStats);
        finalElementStats = nextElementStats;

        currentHp = clamp(currentHp, 0.0, maxHp());
        currentMp = clamp(currentMp, 0.0, maxMp());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
