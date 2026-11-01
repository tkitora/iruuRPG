package net.tkgon.mc.iruuRPG.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.listener.ClassSkillListener;
import net.tkgon.mc.iruuRPG.combat.HeavyInput;
import net.tkgon.mc.iruuRPG.combat.ItemSkillService;
import net.tkgon.mc.iruuRPG.combat.KeyBindings;
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
 * <p>With the gauges, a weapon whose heavy attacks have cooldowns of their own shows a row of their icons (強1 - 強5, {@link HudGlyphs}) above the
 * line, centred over the ranged gauge: an icon is greyed and shaded from the top while its heavy attack cools down, with the seconds left on it.
 * In the style of the keys (t138) each icon has the label of the two keys that do it above it (the player's own choice); while a first key waits, the labels that go
 * on from it are yellow and the others grey (and the gauges show). Half an icon after the skills come the icons of the special (the class's skill) and the weapon's
 * skill (t140), with their keys and cooldowns; each is greyed under a lock when there is none (the class has no special yet, the weapon no skill).
 */
public final class PlayerHud {

    private static final int GAUGE_LENGTH = 10;
    private static final TextColor READY = TextColor.color(0x55ff55);
    private static final TextColor EMPTY = TextColor.color(0x3a3a3a);

    private final LevelService levelService;
    private final JavaPlugin plugin;
    private AttackService attackService;
    private ClassSkillListener classSkills;
    private KeyBindings keyBindings;
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

    /** What each player's two keys do (the labels of the style of the keys). */
    public void setKeyBindings(KeyBindings keyBindings) {
        this.keyBindings = keyBindings;
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
        // a first key waits for its second: the labels show which keys go on from it
        if (HeavyInput.keyStyle() && HeavyInput.pendingKey(player) != null) {
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
        RpgItemDefinition weapon = heavyIconsOn() ? attackService.weaponInMainHand(player) : null;
        for (int index = 0; weapon != null && index < AttackService.HEAVY_ATTACKS; index++) {
            if (attackService.heavyCooldown(player, weapon, index)[0] > 0L) {
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

    private KeyBindings.Pattern patternOf(Player player, KeyBindings.Action action) {
        return keyBindings == null ? KeyBindings.DEFAULTS.get(action) : keyBindings.patternOf(player, action);
    }

    /** The color of a label: white, or while a first key waits, yellow when the label goes on from it and grey when not. */
    private static int keyColor(Player player, KeyBindings.Pattern pattern) {
        KeyBindings.Key waiting = HeavyInput.pendingKey(player);
        if (waiting == null) return 0xFFFFFF;
        return pattern.first() == waiting ? 0xFFE14D : 0x707070;
    }

    /** The glyph of a label ({@link HudGlyphs#keyLabel}) as the keys are pressed with the weapon ({@code melee}: its normal button is the left one). */
    static char keyGlyph(KeyBindings.Pattern pattern, boolean melee) {
        int first = pattern.first() == KeyBindings.Key.F ? 0 : 1;
        int second = switch (pattern.second()) {
            case F -> 0;
            case Q -> 1;
            case OTHER -> melee ? 3 : 2;
            case NORMAL -> melee ? 2 : 3;
        };
        return HudGlyphs.keyLabel(first, second);
    }

    /** {@code hud.heavy-icons} (default true): the row of the heavy attacks' icons with the gauges. */
    private boolean heavyIconsOn() {
        return plugin.getConfig().getBoolean("hud.heavy-icons", true);
    }

    /**
     * The icons of the heavy attacks of the weapon in hand (none when they have no cooldowns of their own: a deployed weapon's, the usual chain's), and after them in the
     * style of the keys the special's and the weapon skill's (t140); null with no weapon in hand. They take no room in the line.
     */
    private Component heavyIcons(Player player, PlayerProfile profile) {
        RpgItemDefinition weapon = heavyIconsOn() ? attackService.weaponInMainHand(player) : null;
        if (weapon == null || !weapon.isWeaponLike()) return null;
        boolean melee = weapon.attackType() == AttackType.MELEE;
        java.util.List<HudGlyphs.Icon> extras = new java.util.ArrayList<>();
        if (HeavyInput.keyStyle()) {
            KeyBindings.Pattern special = patternOf(player, KeyBindings.Action.ULTIMATE);
            long[] specialCooldown = classSkills == null ? null : classSkills.specialCooldown(player);
            extras.add(extra(HudGlyphs.special(profile.classId()), specialCooldown, special, melee, player));
            KeyBindings.Pattern item = patternOf(player, KeyBindings.Action.ITEM);
            long[] itemCooldown = itemSkills == null || !itemSkills.hasKeySkill(weapon) ? null : itemSkills.hudCooldown(player, weapon.attackType());
            extras.add(extra(HudGlyphs.itemSkill(), itemCooldown, item, melee, player));
        }
        if (!attackService.heavyIconsShown(player, weapon)) return extras.isEmpty() ? null : HudGlyphs.row(java.util.List.of(), extras);
        String classId = attackService.heavyIconClass(player, weapon);
        java.util.List<HudGlyphs.Icon> icons = new java.util.ArrayList<>();
        for (int index = 0; index < AttackService.HEAVY_ATTACKS; index++) {
            long[] cooldown = attackService.heavyCooldown(player, weapon, index);
            double share = cooldown[0] <= 0L || cooldown[1] <= 0L ? 0.0 : Math.min(1.0, (double) cooldown[0] / cooldown[1]);
            int seconds = (int) Math.ceil(cooldown[0] / 1000.0);
            if (!HeavyInput.keyStyle()) {
                icons.add(new HudGlyphs.Icon(classId, index + 1, share, seconds));
                continue;
            }
            // (t138: the keys of skill index + 1 above its icon)
            KeyBindings.Pattern pattern = patternOf(player, KeyBindings.Action.values()[index]);
            icons.add(new HudGlyphs.Icon(classId, index + 1, share, seconds, keyGlyph(pattern, melee), keyColor(player, pattern)));
        }
        return HudGlyphs.row(icons, extras);
    }

    /** The icon of the special or the weapon's skill: {@code cooldown} {left, whole} in milliseconds, or null when there is none (locked). */
    private static HudGlyphs.Icon extra(char glyph, long[] cooldown, KeyBindings.Pattern pattern, boolean melee, Player player) {
        if (cooldown == null) return new HudGlyphs.Icon(glyph, 0.0, 0, keyGlyph(pattern, melee), keyColor(player, pattern), true);
        double share = cooldown[0] <= 0L || cooldown[1] <= 0L ? 0.0 : Math.min(1.0, (double) cooldown[0] / cooldown[1]);
        return new HudGlyphs.Icon(glyph, share, (int) Math.ceil(cooldown[0] / 1000.0), keyGlyph(pattern, melee), keyColor(player, pattern), false);
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
