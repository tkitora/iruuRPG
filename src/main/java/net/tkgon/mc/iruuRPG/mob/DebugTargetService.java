package net.tkgon.mc.iruuRPG.mob;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.text.NumberFormat;
import java.util.Locale;

public final class DebugTargetService {

    private final JavaPlugin plugin;
    private final NamespacedKey targetKey;

    public DebugTargetService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.targetKey = new NamespacedKey(plugin, "debug_target");
    }

    public Zombie spawn(Player player) {
        Location location = spawnLocation(player);
        Zombie target = player.getWorld().spawn(location, Zombie.class, this::configure);
        updateName(target);
        return target;
    }

    public boolean isDebugTarget(Entity entity) {
        return entity.getPersistentDataContainer().has(targetKey, PersistentDataType.BYTE);
    }

    public void updateName(LivingEntity target) {
        double health = Math.max(0.0, target.getHealth());
        double maxHealth = maxHealth(target);
        target.customName(Component.text("iruuRPG 的 ", NamedTextColor.RED)
                .append(Component.text(format(health), NamedTextColor.YELLOW))
                .append(Component.text("/", NamedTextColor.GRAY))
                .append(Component.text(format(maxHealth), NamedTextColor.YELLOW))
                .append(Component.text(" HP", NamedTextColor.RED)));
        target.setCustomNameVisible(true);
    }

    private void configure(Zombie target) {
        double maxHealth = Math.max(1.0, plugin.getConfig().getDouble("debug.target.max-health", 1000.0));

        target.getPersistentDataContainer().set(targetKey, PersistentDataType.BYTE, (byte) 1);
        target.setAI(false);
        target.setGravity(false);
        target.setSilent(true);
        target.setPersistent(true);
        target.setRemoveWhenFarAway(false);
        target.setCanPickupItems(false);
        target.setAdult();
        target.setFireTicks(0);

        AttributeInstance maxHealthAttribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttribute != null) {
            maxHealthAttribute.setBaseValue(maxHealth);
        }

        AttributeInstance knockback = target.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
        if (knockback != null) {
            knockback.setBaseValue(1.0);
        }

        AttributeInstance movement = target.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (movement != null) {
            movement.setBaseValue(0.0);
        }

        target.setHealth(Math.min(maxHealth, maxHealth(target)));
    }

    private Location spawnLocation(Player player) {
        Vector direction = player.getLocation().getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = new Vector(0.0, 0.0, 1.0);
        } else {
            direction.normalize();
        }

        Location location = player.getLocation().add(direction.multiply(
                plugin.getConfig().getDouble("debug.target.spawn-distance", 3.0)
        ));
        location.setPitch(0.0f);
        location.setYaw(player.getLocation().getYaw() + 180.0f);
        return location;
    }

    private double maxHealth(LivingEntity target) {
        AttributeInstance attribute = target.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : Math.max(1.0, target.getHealth());
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }
}
