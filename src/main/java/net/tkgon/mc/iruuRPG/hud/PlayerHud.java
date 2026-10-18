package net.tkgon.mc.iruuRPG.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.AttackType;
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
 * cooldown overlay, not shown here.) {@code hud.cooldown-gauges}: {@code auto} (only while one is cooling down, else the
 * usual status line with defense, level and XP), {@code always}, or {@code off}.
 */
public final class PlayerHud {

    private static final int GAUGE_LENGTH = 10;
    private static final TextColor READY = TextColor.color(0x55ff55);
    private static final TextColor EMPTY = TextColor.color(0x3a3a3a);

    private final LevelService levelService;
    private final JavaPlugin plugin;
    private AttackService attackService;

    public PlayerHud(JavaPlugin plugin, LevelService levelService) {
        this.plugin = plugin;
        this.levelService = levelService;
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
        for (AttackType type : TYPES) {
            if (attackService.normalCooldown(player, type)[0] > 0L) {
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
        for (AttackType type : TYPES) {
            bar = bar.append(gauge(player, type)).append(Component.text(" "));
        }
        return bar;
    }

    /** One gauge: the brackets in the type's color, the bars green (ready) or the normal attack's progress in a light shade. */
    private Component gauge(Player player, AttackType type) {
        long[] normal = attackService.normalCooldown(player, type);
        TextColor base = typeColor(type);
        Component gauge = Component.text("[", base);
        if (normal[0] <= 0L || normal[1] <= 0L) {
            gauge = gauge.append(Component.text("|".repeat(GAUGE_LENGTH), READY));
        } else {
            double progress = 1.0 - Math.min(1.0, (double) normal[0] / normal[1]);
            int filled = (int) Math.max(0, Math.min(GAUGE_LENGTH, Math.round(progress * GAUGE_LENGTH)));
            gauge = gauge.append(Component.text("|".repeat(filled), lighter(base)))
                    .append(Component.text("|".repeat(GAUGE_LENGTH - filled), EMPTY));
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
