package net.tkgon.mc.iruuRPG.equipment;

import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EquipmentService {

    private static final double VANILLA_WALK_SPEED = 0.1;
    private static final double MIN_MOVE_SPEED_PERCENT = -90.0;
    private static final double MAX_MOVE_SPEED = 1.0;

    private final PlayerProfileManager profileManager;
    private final ItemIdentifier itemIdentifier;
    private final RpgItemRegistry itemRegistry;
    private ClassService classService;

    public EquipmentService(PlayerProfileManager profileManager, ItemIdentifier itemIdentifier, RpgItemRegistry itemRegistry) {
        this.profileManager = profileManager;
        this.itemIdentifier = itemIdentifier;
        this.itemRegistry = itemRegistry;
    }

    public void setClassService(ClassService classService) {
        this.classService = classService;
    }

    /** The cached profile without recalculating (null if not loaded). */
    public PlayerProfile profile(Player player) {
        return profileManager.get(player).orElse(null);
    }

    public PlayerProfile recalculate(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        StatSet equipmentStats = new StatSet();
        ElementStatSet equipmentElementStats = new ElementStatSet();

        for (ItemStack item : player.getInventory().getArmorContents()) {
            accumulate(player, item, equipmentStats, equipmentElementStats);
        }
        accumulate(player, player.getInventory().getItemInMainHand(), equipmentStats, equipmentElementStats);

        if (classService != null) {
            classService.applyClassStats(player, profile);
        }
        profile.replaceEquipmentStats(equipmentStats, equipmentElementStats);
        applyMoveSpeed(player, profile);
        return profile;
    }

    /** Move Speed % scales the vanilla walking speed (base 0.1). */
    private void applyMoveSpeed(Player player, PlayerProfile profile) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) return;

        double percent = Math.max(MIN_MOVE_SPEED_PERCENT, profile.finalStats().get(StatType.MOVE_SPEED));
        double value = Math.min(MAX_MOVE_SPEED, VANILLA_WALK_SPEED * (1.0 + percent / 100.0));
        if (Math.abs(attribute.getBaseValue() - value) > 1.0E-9) {
            attribute.setBaseValue(value);
        }
    }

    public boolean canUse(Player player, ItemStack item) {
        return itemIdentifier.itemId(item)
                .flatMap(itemRegistry::find)
                .map(definition -> canUse(player, definition))
                .orElse(true);
    }

    public boolean canUse(Player player, RpgItemDefinition definition) {
        if (definition == null) return true;

        PlayerProfile profile = profileManager.getOrCreate(player);
        return definition.requiredLevel() <= profile.level();
    }

    private void accumulate(Player player, ItemStack item, StatSet targetStats, ElementStatSet targetElementStats) {
        itemIdentifier.itemId(item)
                .flatMap(itemRegistry::find)
                .filter(definition -> canUse(player, definition))
                .ifPresent(definition -> accumulate(definition, targetStats, targetElementStats));
    }

    private void accumulate(RpgItemDefinition definition, StatSet targetStats, ElementStatSet targetElementStats) {
        targetStats.addAll(definition.stats());
        targetElementStats.addAll(definition.elementStats());
    }
}
