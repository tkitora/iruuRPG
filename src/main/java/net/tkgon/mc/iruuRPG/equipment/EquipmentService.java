package net.tkgon.mc.iruuRPG.equipment;

import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class EquipmentService {

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
        return profile;
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
