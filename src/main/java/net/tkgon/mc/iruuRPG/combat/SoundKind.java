package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Material;

/** The groups of attack sounds a player can turn up or down in the settings menu. */
public enum SoundKind {
    MELEE("近接攻撃", Material.IRON_SWORD, "通常の近接攻撃の素振り"),
    RANGED("遠距離攻撃", Material.BOW, "通常の遠距離攻撃の発射"),
    SKILL("スキル", Material.NETHER_STAR, "範囲攻撃・ダッシュ・打撃・斬撃などのスキル"),
    HEAL("回復", Material.GLISTERING_MELON_SLICE, "回復の音"),
    DEPLOY("設置物", Material.ARMOR_STAND, "設置した物の音"),
    HIT("命中音", Material.TARGET, "攻撃が当たった音・着弾"),
    ENEMY("敵の攻撃", Material.SKELETON_SKULL, "敵の遠距離攻撃の音");

    private final String label;
    private final Material icon;
    private final String description;

    SoundKind(String label, Material icon, String description) {
        this.label = label;
        this.icon = icon;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public Material icon() {
        return icon;
    }

    public String description() {
        return description;
    }
}
