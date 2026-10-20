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

    /**
     * A detached profile of the player as if they held {@code held} in the main hand instead of what they hold now
     * (the armor, the class and the base stats are the same). Used for the off-hand weapon's hit of a dual-wielder.
     */
    public PlayerProfile profileHolding(Player player, ItemStack held) {
        PlayerProfile live = recalculate(player);
        StatSet stats = new StatSet();
        stats.addAll(live.finalStats());
        ElementStatSet elements = new ElementStatSet();
        elements.addAll(live.finalElementStats());

        StatSet mainStats = new StatSet();
        ElementStatSet mainElements = new ElementStatSet();
        accumulate(player, player.getInventory().getItemInMainHand(), mainStats, mainElements);
        stats.addAll(mainStats.scaled(-1.0));
        elements.addAll(mainElements.scaled(-1.0));

        accumulate(player, held, stats, elements);

        PlayerProfile detached = new PlayerProfile(player.getUniqueId(), live.maxHp(), live.maxMp());
        detached.setLevel(live.level());
        detached.baseStats().replaceWith(stats);
        detached.baseElementStats().replaceWith(elements);
        detached.recalculate();
        detached.setCurrentHp(live.currentHp());
        detached.setCurrentMp(live.currentMp());
        return detached;
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

        if (classUnusable(player, definition)) return false;
        PlayerProfile profile = profileManager.getOrCreate(player);
        return definition.requiredLevel() <= profile.level();
    }

    /** True when the item is unusable (a gray mark) to the player's class (its `classes:`): it cannot be used, and gives no stats. */
    public boolean classUnusable(Player player, RpgItemDefinition definition) {
        if (definition == null || !definition.marked()) return false;
        return !definition.usableByClass(classIdOf(player));
    }

    /** The internal id of the player's class ("" without one). */
    public String classIdOf(Player player) {
        if (classService == null) return "";
        return classService.currentClass(profileManager.getOrCreate(player)).map(definition -> definition.id()).orElse("");
    }

    private void accumulate(Player player, ItemStack item, StatSet targetStats, ElementStatSet targetElementStats) {
        itemIdentifier.itemId(item)
                .flatMap(itemRegistry::find)
                .filter(definition -> canUse(player, definition))
                .ifPresent(definition -> {
                    accumulate(definition, targetStats, targetElementStats);
                    targetStats.addAll(itemIdentifier.bonusStats(item)); // random stats of a crafted item
                    targetElementStats.addAll(itemIdentifier.bonusElementStats(item));
                });
    }

    private void accumulate(RpgItemDefinition definition, StatSet targetStats, ElementStatSet targetElementStats) {
        targetStats.addAll(definition.stats());
        targetElementStats.addAll(definition.elementStats());
    }
}
