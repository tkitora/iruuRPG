package net.tkgon.mc.iruuRPG.itempack;

import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DamageKind;
import net.tkgon.mc.iruuRPG.item.ItemSkillType;
import net.tkgon.mc.iruuRPG.item.ItemVisualOptions;
import net.tkgon.mc.iruuRPG.item.Rarity;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemPackServiceTest {

    private static RpgItemDefinition item(String id, Material material, String texture) {
        return new RpgItemDefinition(id, material, new ItemVisualOptions(false, null, null, texture), id, 1, Rarity.COMMON, Element.RED,
                DamageKind.PHYSICAL, AttackType.MELEE, ItemSkillType.NONE, "", Map.of(), null, new StatSet(), new ElementStatSet(), List.of());
    }

    @Test
    void onlyTheItemsWithATextureWearALook() {
        List<ItemPackBuilder.Look> looks = ItemPackService.looks(List.of(
                item("bone_dagger", Material.IRON_SWORD, "bone"), item("plain_sword", Material.IRON_SWORD, null)));
        assertEquals(1, looks.size());
        assertEquals(new ItemPackBuilder.Look("bone_dagger", "IRON_SWORD", "bone"), looks.get(0));
    }

    @Test
    void flatItemsAreThePicturesAndBlocksAreNot() {
        assertTrue(ItemPackService.flat("bone"));
        assertTrue(ItemPackService.flat("wooden_sword"));
        assertTrue(ItemPackService.flat("iron_chestplate"));
        assertFalse(ItemPackService.flat("stone"));
        assertFalse(ItemPackService.flat("sea_lantern"));
        assertFalse(ItemPackService.flat("shield"));
        assertFalse(ItemPackService.flat("not_an_item"));
    }
}
