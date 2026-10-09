package net.tkgon.mc.iruuRPG.hud;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.Locale;

public final class PlayerHud {

    private final LevelService levelService;

    public PlayerHud(LevelService levelService) {
        this.levelService = levelService;
    }

    public void send(Player player, PlayerProfile profile) {
        player.sendActionBar(build(profile));
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
