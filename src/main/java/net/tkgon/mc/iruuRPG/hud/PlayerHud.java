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
 * while a Q waits for its second key, the labels that go on from it (小・中・大) are yellow and the others grey (and the gauges show). A deployed
 * weapon shows only the ULT and its own skill. While the ULT can be re-activated, a bright rim blinks on its icon (and the gauges show).
 *
 * <p>Re-activation and charges (t157, {@link SkillSlots.View}): while a skill's re-activation is open, its icon has a colored rim and its keys are
 * yellow (as after a Q); a skill of more than one charge shows the charges left at the icon's lower right (green when full, red when none) and,
 * while one refills, a thin gauge of that refill under the icon. A skill that cannot be used now (no charge left and no re-activation open) is
 * greyed and shaded with the seconds to its next charge in its middle, as a cooling skill always was (t158); one that can (a charge left, or its
 * re-activation open) has no shade.
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
        // a Q waits for its second key (the labels show which keys go on from it), or the ULT can be re-activated (its icon blinks)
        if (SkillInput.waiting(player) || ultRecastable(player)) {
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
            SkillSlots.View view = skillSlots.view(player, slot, weapon);
            // (a cooldown, an open re-activation, or a charge refilling)
            if (view.cooldown() != null && (view.cooldown()[0] > 0L || view.recasting() || view.charged() && view.refill() >= 0.0)) {
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
        if (!gaugesShown(player)) return plainBar(player, profile);

        Component bar = Component.text("HP ", NamedTextColor.RED)
                .append(Component.text(format(profile.currentHp()), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxHp()), NamedTextColor.RED))
                .append(wound(player))
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

    /** The color of a label that can be pressed now: yellow (the keys after a Q, a skill whose re-activation is open). */
    static final int KEY_NOW = 0xFFE14D;

    /** The color of a label: white, or while a Q waits, yellow when the label goes on from it (小・中・大) and grey when not. */
    private static int keyColor(Player player, boolean afterFirst) {
        if (!SkillInput.waiting(player)) return 0xFFFFFF;
        return afterFirst ? KEY_NOW : 0x707070;
    }

    /** The glyph of the keys of a skill by default with a melee weapon ({@link HudGlyphs#keyLabel}). */
    static char keyGlyph(SkillSlots.Slot slot) {
        return keyGlyph(slot, false, true);
    }

    /**
     * The glyph of the keys of a skill (t164): the first key (F, or Q when swapped: t162) and a button for 小・中, twice for 大; util the weapon's
     * other button (right with a melee weapon, left with another); the ULT shift and the second key.
     */
    static char keyGlyph(SkillSlots.Slot slot, boolean swapped, boolean melee) {
        String first = swapped ? "q" : "f";
        String second = swapped ? "f" : "q";
        return HudGlyphs.keyLabel(switch (slot) {
            case SMALL -> first + "l";
            case MEDIUM -> first + "r";
            case LARGE -> first + first;
            case UTIL -> melee ? "r" : "l";
            case ULT -> "s" + second;
        });
    }

    /** The glyph of the key of the weapon's skill: the second key alone (Q, or F when swapped). */
    static char itemKeyGlyph(boolean swapped) {
        return HudGlyphs.keyLabel(swapped ? "f" : "q");
    }

    /** How long the ULT's rim is on, and then off, while it can be re-activated. */
    private static final int BLINK_TICKS = 5;

    private boolean ultRecastable(Player player) {
        return classSkills != null && classSkills.ultRecastable(player);
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
            // (a deployed weapon has no 小・中・大・util: only the ULT and its own skill)
            if (weapon.attackType() == AttackType.DEPLOY && slot != SkillSlots.Slot.ULT) continue;
            boolean afterFirst = slot.ordinal() <= SkillSlots.Slot.LARGE.ordinal();
            // (each kind of weapon has its own icons and cooldowns of 小・中・大・util)
            SkillSlots.View view = skillSlots.view(player, slot, weapon);
            HudGlyphs.Icon icon = icon(HudGlyphs.skill(profile.classId(), HudGlyphs.kindIndex(weapon.attackType()), slot.ordinal()), view,
                    keyGlyph(slot, SkillInput.swapped(player), melee), view.recasting() ? KEY_NOW : keyColor(player, afterFirst));
            // the ULT that can be re-activated now blinks: its bright rim is on and off every 5 ticks
            if (slot == SkillSlots.Slot.ULT && ultRecastable(player)) {
                icon = new HudGlyphs.Icon(icon.glyph(), icon.coolingShare(), icon.seconds(), icon.key(), icon.keyColor(), icon.locked(),
                        org.bukkit.Bukkit.getCurrentTick() / BLINK_TICKS % 2 == 0);
            }
            icons.add(icon);
        }
        HudGlyphs.Icon item;
        if (itemSkills != null && itemSkills.hasKeySkill(weapon)) {
            item = icon(HudGlyphs.itemSkill(), itemSkills.hudCooldown(player, weapon.attackType()), itemKeyGlyph(SkillInput.swapped(player)), keyColor(player, false));
        } else {
            // (a weapon with no skill: the locked icon of its own, t140)
            item = new HudGlyphs.Icon(HudGlyphs.itemSkillLocked(), 0.0, 0, itemKeyGlyph(SkillInput.swapped(player)), keyColor(player, false), false);
        }
        return HudGlyphs.row(icons, java.util.List.of(item));
    }

    /**
     * The icon of a skill (t157): greyed and shaded with the seconds left only while it cannot be used (no charge left and no re-activation open,
     * t158: as a cooling skill always was; {@link SkillSlots.View#cooldown} is the time to the next charge then), the rim of an open re-activation,
     * the charges left and the refill's gauge.
     */
    static HudGlyphs.Icon icon(char glyph, SkillSlots.View view, char key, int keyColor) {
        long[] cooldown = view.cooldown();
        // (its re-activation can be done now: not greyed)
        if (cooldown != null && view.recasting()) cooldown = new long[]{0L, 0L};
        HudGlyphs.Icon plain = icon(glyph, cooldown, key, keyColor);
        return new HudGlyphs.Icon(plain.glyph(), plain.coolingShare(), plain.seconds(), plain.key(), plain.keyColor(), plain.locked(), false,
                view.recasting(), view.charges(), view.maxCharges(), view.refill());
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

    /** The warrior's wound that their melee hits can still drink back (t168), as "+N" in dark red after the HP; nothing when none. */
    private Component wound(Player player) {
        double wound = player == null ? 0.0 : net.tkgon.mc.iruuRPG.combat.WarriorSkills.woundShown(player);
        if (wound < 0.5) return Component.empty();
        return Component.text(" +" + format(Math.round(wound)), NamedTextColor.DARK_RED);
    }

    public Component build(PlayerProfile profile) {
        return plainBar(null, profile);
    }

    /** The bar without the gauges: HP (and the warrior's wound), MP, DEF, Lv and XP. */
    private Component plainBar(Player player, PlayerProfile profile) {
        long requiredXp = levelService.requiredXpForNextLevel(profile.level());
        String xpText = requiredXp <= 0L
                ? "MAX"
                : format(profile.xp()) + "/" + format(requiredXp);

        return Component.text("HP ", NamedTextColor.RED)
                .append(Component.text(format(profile.currentHp()), NamedTextColor.RED))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(profile.maxHp()), NamedTextColor.RED))
                .append(wound(player))
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
