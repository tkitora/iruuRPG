package net.tkgon.mc.iruuRPG.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.listener.ClassSkillListener;
import net.tkgon.mc.iruuRPG.combat.ItemSkillService;
import net.tkgon.mc.iruuRPG.combat.SkillInput;
import net.tkgon.mc.iruuRPG.combat.SkillSlots;
import net.tkgon.mc.iruuRPG.markpack.HudGlyphs;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * The action bar: HP, MP and (when a normal attack is cooling down) three gauges of the normal attack's cooldown,
 * melee (red), ranged (blue) and deployed (white). Each gauge is {@code [||||||||||]}: green when the normal attack can
 * be used, otherwise its progress in a light shade of the type's color. (The weapon skill's cooldown is the item's own
 * cooldown overlay, not shown here.) {@code hud.cooldown-gauges}: {@code auto} (only while a normal attack or a heavy attack is cooling down or damage is
 * being dealt, else the usual status line with defense, level and XP), {@code always}, or {@code off}.
 *
 * <p>With the gauges and a weapon in hand, a row of the icons of the class's skills (小・中・大・util・ULT, {@link SkillSlots}; {@link HudGlyphs})
 * stands above the line, centred over the ranged gauge, and half an icon after them the weapon's skill (t140). An icon is greyed and shaded from
 * the top while its skill cools down, with the seconds left on it, and greyed under a lock when there is nothing on it (a skill whose move is not
 * made yet, a ULT not unlocked, a weapon with no skill). Each has the label of its keys above it, as they are pressed with the weapon in hand;
 * while a Q waits for its second key, the labels that go on from it (小・中・大) are yellow and the others grey (and the gauges show).
 */
public final class PlayerHud {

    private static final int GAUGE_LENGTH = 10;
    private static final TextColor READY = TextColor.color(0x55ff55);
    private static final TextColor EMPTY = TextColor.color(0x3a3a3a);

    private final LevelService levelService;
    private final JavaPlugin plugin;
    private AttackService attackService;
    private ClassSkillListener classSkills;
    private SkillSlots skillSlots;
    private ItemSkillService itemSkills;

    public PlayerHud(JavaPlugin plugin, LevelService levelService) {
        this.plugin = plugin;
        this.levelService = levelService;
    }

    /** Where the class skill's cooldown comes from. */
    public void setClassSkills(ClassSkillListener classSkills) {
        this.classSkills = classSkills;
    }

    /** Where the weapon's skill and its cooldown come from (its icon, t140). */
    public void setItemSkills(ItemSkillService itemSkills) {
        this.itemSkills = itemSkills;
    }

    /** Where the skills and their cooldowns come from (their icons). */
    public void setSkillSlots(SkillSlots skillSlots) {
        this.skillSlots = skillSlots;
    }

    /** Where the normal attack cooldowns come from. */
    public void setCooldowns(AttackService attackService) {
        this.attackService = attackService;
    }

    public void send(Player player, PlayerProfile profile) {
        player.sendActionBar(build(player, profile));
    }

    /** True while the gauges are on the bar: one of the three is cooling down, or {@code always}. */
    public boolean gaugesShown(Player player) {
        String mode = plugin.getConfig().getString("hud.cooldown-gauges", "auto").toLowerCase(Locale.ROOT);
        if (attackService == null || mode.equals("off")) return false;
        if (mode.equals("always")) return true;
        long now = System.currentTimeMillis();
        // a Q waits for its second key: the labels show which keys go on from it
        if (SkillInput.qWaiting(player)) {
            lastActive.put(player.getUniqueId(), now);
            return true;
        }
        // damage is being dealt (the DPS is not 0: also when only skills hit), or the class skill's total is up
        var meter = attackService.damageMeter();
        if (meter.dps(player.getUniqueId()) > 0.0 || meter.skillTotalShown(player.getUniqueId())) {
            lastActive.put(player.getUniqueId(), now);
            return true;
        }
        for (AttackType type : TYPES) {
            if (attackService.normalCooldown(player, type)[0] > 0L) {
                lastActive.put(player.getUniqueId(), now);
                return true;
            }
        }
        RpgItemDefinition weapon = heavyIconsOn() && skillSlots != null ? attackService.weaponInMainHand(player) : null;
        for (SkillSlots.Slot slot : SkillSlots.Slot.values()) {
            // (not for the ULT's long cooldown)
            if (weapon == null || !weapon.isWeaponLike() || slot == SkillSlots.Slot.ULT) continue;
            long[] cooldown = skillSlots.cooldown(player, slot, weapon);
            if (cooldown != null && cooldown[0] > 0L) {
                lastActive.put(player.getUniqueId(), now);
                return true;
            }
        }
        // stay on the gauges a moment after the last cooldown, so the bar does not flip back and forth between swings
        Long last = lastActive.get(player.getUniqueId());
        return last != null && now - last < HOLD_MILLIS;
    }

    private static final long HOLD_MILLIS = 1500L;
    private final java.util.Map<java.util.UUID, Long> lastActive = new java.util.HashMap<>();

    private static final AttackType[] TYPES = {AttackType.MELEE, AttackType.RANGE, AttackType.DEPLOY};

    public Component build(Player player, PlayerProfile profile) {
        if (!gaugesShown(player)) return build(profile);

        Component bar = Component.text("HP ", NamedTextColor.RED)
                .append(Component.text(format(profile.currentHp()), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxHp()), NamedTextColor.RED))
                .append(Component.text("  MP ", NamedTextColor.AQUA))
                .append(Component.text(format(profile.currentMp()), NamedTextColor.AQUA))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxMp()), NamedTextColor.AQUA))
                .append(Component.text("  "));
        Component icons = heavyIcons(player, profile);
        for (AttackType type : TYPES) {
            bar = bar.append(gauge(player, type, type == AttackType.RANGE ? icons : null)).append(Component.text(" "));
        }
        // in the middle: the class skill's cooldown, or what has been dealt while it is up; at the right end: the DPS
        bar = bar.append(classSkill(player)).append(Component.text("  "));
        double dps = attackService.damageMeter().dps(player.getUniqueId());
        return bar.append(Component.text("DPS ", NamedTextColor.GOLD)).append(Component.text(format(Math.round(dps)), NamedTextColor.GOLD));
    }

    private Component classSkill(Player player) {
        var meter = attackService.damageMeter();
        if (meter.skillTotalShown(player.getUniqueId())) {
            boolean up = meter.skillActive(player.getUniqueId());
            return Component.text(up ? "与ダメ " : "計 ", NamedTextColor.YELLOW)
                    .append(Component.text(format(Math.round(meter.skillTotal(player.getUniqueId()))), up ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
        }
        ClassSkillListener.HudStatus status = classSkills == null ? null : classSkills.hudStatus(player);
        if (status == null) return Component.empty();
        // (its keys are over its icon since t140)
        if (status.remainingMillis() <= 0L) return Component.text(status.name() + " OK", NamedTextColor.GREEN);
        return Component.text(status.name() + " ", NamedTextColor.GRAY)
                .append(Component.text(String.format(Locale.ROOT, "%.1f", status.remainingMillis() / 1000.0) + "s", NamedTextColor.WHITE));
    }

    /** The color of a label: white, or while a Q waits, yellow when the label goes on from it (小・中・大) and grey when not. */
    private static int keyColor(Player player, boolean afterQ) {
        if (!SkillInput.qWaiting(player)) return 0xFFFFFF;
        return afterQ ? 0xFFE14D : 0x707070;
    }

    /** The glyph of the keys of a skill ({@link HudGlyphs#keyLabel}) as they are pressed with the weapon ({@code melee}: its normal button is the left one). */
    static char keyGlyph(SkillSlots.Slot slot, boolean melee) {
        return HudGlyphs.keyLabel(switch (slot) {
            case SMALL -> melee ? "ql" : "qr";
            case MEDIUM -> melee ? "qr" : "ql";
            case LARGE -> "qq";
            case UTIL -> "f";
            case ULT -> "sf";
        });
    }

    /** The glyph of the button of the weapon's skill: its other button, the right one with a melee weapon and the left one with another. */
    static char itemKeyGlyph(boolean melee) {
        return HudGlyphs.keyLabel(melee ? "r" : "l");
    }

    /** {@code hud.heavy-icons} (default true): the row of the skills' icons with the gauges. */
    private boolean heavyIconsOn() {
        return plugin.getConfig().getBoolean("hud.heavy-icons", true);
    }

    /** The icons of the class's skills and, half an icon after them, the weapon's skill (t140); null with no weapon in hand. They take no room in the line. */
    private Component heavyIcons(Player player, PlayerProfile profile) {
        RpgItemDefinition weapon = heavyIconsOn() && skillSlots != null ? attackService.weaponInMainHand(player) : null;
        if (weapon == null || !weapon.isWeaponLike()) return null;
        boolean melee = weapon.attackType() == AttackType.MELEE;
        java.util.List<HudGlyphs.Icon> icons = new java.util.ArrayList<>();
        for (SkillSlots.Slot slot : SkillSlots.Slot.values()) {
            boolean afterQ = slot.ordinal() <= SkillSlots.Slot.LARGE.ordinal();
            icons.add(icon(HudGlyphs.skill(profile.classId(), slot.ordinal()), skillSlots.cooldown(player, slot, weapon), keyGlyph(slot, melee), keyColor(player, afterQ)));
        }
        HudGlyphs.Icon item;
        if (itemSkills != null && itemSkills.hasKeySkill(weapon)) {
            item = icon(HudGlyphs.itemSkill(), itemSkills.hudCooldown(player, weapon.attackType()), itemKeyGlyph(melee), keyColor(player, false));
        } else {
            // (a weapon with no skill: the locked icon of its own, t140)
            item = new HudGlyphs.Icon(HudGlyphs.itemSkillLocked(), 0.0, 0, itemKeyGlyph(melee), keyColor(player, false), false);
        }
        return HudGlyphs.row(icons, java.util.List.of(item));
    }

    /** An icon: {@code cooldown} {left, whole} in milliseconds, or null when there is nothing on it (locked). */
    private static HudGlyphs.Icon icon(char glyph, long[] cooldown, char key, int keyColor) {
        if (cooldown == null) return new HudGlyphs.Icon(glyph, 0.0, 0, key, keyColor, true);
        double share = cooldown[0] <= 0L || cooldown[1] <= 0L ? 0.0 : Math.min(1.0, (double) cooldown[0] / cooldown[1]);
        return new HudGlyphs.Icon(glyph, share, (int) Math.ceil(cooldown[0] / 1000.0), key, keyColor, false);
    }

    /**
     * One gauge: the brackets in the type's color, the bars green (ready) or the normal attack's progress in a light shade. {@code middle}: put at
     * its middle (the icons of the heavy attacks), or null.
     */
    private Component gauge(Player player, AttackType type, Component middle) {
        long[] normal = attackService.normalCooldown(player, type);
        TextColor base = typeColor(type);
        Component gauge = Component.text("[", base);
        // the gauge shows the step of its combo in its middle: [|||||3||||] (melee: the melee chain, ranged: the mage's chain)
        int step = attackService.comboStage(player, type);
        int digitAt = GAUGE_LENGTH / 2;
        boolean ready = normal[0] <= 0L || normal[1] <= 0L;
        int filled = ready ? GAUGE_LENGTH
                : (int) Math.max(0, Math.min(GAUGE_LENGTH, Math.round((1.0 - Math.min(1.0, (double) normal[0] / normal[1])) * GAUGE_LENGTH)));
        TextColor filledColor = ready ? READY : lighter(base);
        for (int index = 0; index < GAUGE_LENGTH; index++) {
            if (middle != null && index == digitAt) gauge = gauge.append(middle);
            if (step > 0 && index == digitAt) {
                gauge = gauge.append(Component.text(String.valueOf(step), NamedTextColor.WHITE).decorate(net.kyori.adventure.text.format.TextDecoration.BOLD));
            } else {
                gauge = gauge.append(Component.text("|", index < filled ? filledColor : EMPTY));
            }
        }
        return gauge.append(Component.text("]", base));
    }

    private TextColor typeColor(AttackType type) {
        return switch (type) {
            case MELEE -> NamedTextColor.RED;
            case RANGE -> NamedTextColor.BLUE;
            default -> NamedTextColor.WHITE;
        };
    }

    /** The light shade of a type's color: the normal attack's cooldown. */
    private TextColor lighter(TextColor color) {
        if (color == NamedTextColor.RED) return TextColor.color(0xff9a9a);
        if (color == NamedTextColor.BLUE) return TextColor.color(0x9ab4ff);
        return TextColor.color(0xe6e6e6);
    }

    public Component build(PlayerProfile profile) {
        long requiredXp = levelService.requiredXpForNextLevel(profile.level());
        String xpText = requiredXp <= 0L
                ? "MAX"
                : format(profile.xp()) + "/" + format(requiredXp);

        return Component.text("HP ", NamedTextColor.RED)
                .append(Component.text(format(profile.currentHp()), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxHp()), NamedTextColor.RED))
                .append(Component.text("  MP ", NamedTextColor.AQUA))
                .append(Component.text(format(profile.currentMp()), NamedTextColor.AQUA))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxMp()), NamedTextColor.AQUA))
                .append(Component.text("  DEF ", NamedTextColor.YELLOW))
                .append(Component.text(format(profile.finalStats().get(StatType.DEFENSE)), NamedTextColor.YELLOW))
                .append(Component.text("  Lv ", NamedTextColor.GREEN))
                .append(Component.text(String.valueOf(profile.level()), NamedTextColor.GREEN))
                .append(Component.text("  XP ", NamedTextColor.GREEN))
                .append(Component.text(xpText, NamedTextColor.GREEN));
    }

    private String format(long value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        return format.format(value);
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }
}
