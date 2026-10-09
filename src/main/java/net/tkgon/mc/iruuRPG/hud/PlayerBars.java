package net.tkgon.mc.iruuRPG.hud;

import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;

public final class PlayerBars {

    public void sync(Player player, PlayerProfile profile) {
        syncHealth(player, profile);
        syncFood(player, profile);
    }

    private void syncHealth(Player player, PlayerProfile profile) {
        if (player.isDead()) return;

        AttributeInstance maxHealthAttribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        double minecraftMaxHealth = maxHealthAttribute != null ? maxHealthAttribute.getValue() : 20.0;
        double ratio = profile.currentHp() / profile.maxHp();

        double shownHealth = clamp(minecraftMaxHealth * ratio, 0.5, minecraftMaxHealth);
        if (profile.currentHp() <= 0.0) {
            shownHealth = 0.0;
        }

        player.setHealth(shownHealth);
    }

    private void syncFood(Player player, PlayerProfile profile) {
        double ratio = profile.currentMp() / profile.maxMp();
        int foodLevel = (int) Math.round(clamp(ratio, 0.0, 1.0) * 20.0);

        player.setFoodLevel(foodLevel);
        player.setSaturation(0.0f);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
