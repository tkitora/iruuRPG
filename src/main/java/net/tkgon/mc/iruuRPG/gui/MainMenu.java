package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.classsystem.ClassDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.player.LevelService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class MainMenu implements Listener {

    private static final int SIZE = 54;
    private static final Component TITLE = Component.text("iruuRPG メニュー");

    private final EquipmentService equipmentService;
    private final LevelService levelService;
    private final StatsMenu statsMenu;
    private final SkillTreeMenu skillTreeMenu;

    private final ClassService classService;
    private final ClassSelectMenu classSelectMenu;

    public MainMenu(
            EquipmentService equipmentService,
            LevelService levelService,
            StatsMenu statsMenu,
            SkillTreeMenu skillTreeMenu,
            ClassService classService,
            ClassSelectMenu classSelectMenu
    ) {
        this.classService = classService;
        this.classSelectMenu = classSelectMenu;
        this.equipmentService = equipmentService;
        this.levelService = levelService;
        this.statsMenu = statsMenu;
        this.skillTreeMenu = skillTreeMenu;
    }

    public void open(Player player) {
        PlayerProfile profile = equipmentService.recalculate(player);
        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        fill(inventory);
        inventory.setItem(4, playerIcon(player, profile));
        inventory.setItem(20, levelUpButton(profile));
        inventory.setItem(22, button(Material.COMPASS, "ステータス", NamedTextColor.AQUA, List.of(
                Component.text("現在の能力値を確認します", NamedTextColor.GRAY)
        )));
        inventory.setItem(24, button(Material.NETHER_STAR, "スキル", NamedTextColor.LIGHT_PURPLE, List.of(
                Component.text("スキルポイントを振り分けます", NamedTextColor.GRAY),
                Component.text("残りSP: " + classService.availablePoints(profile), NamedTextColor.GREEN),
                Component.text("解放ノードの確認もここから", NamedTextColor.GRAY)
        )));
        inventory.setItem(31, button(Material.LECTERN, "クラス変更", NamedTextColor.GOLD, List.of(
                Component.text("現在: " + currentClassName(profile), NamedTextColor.WHITE),
                Component.text("別のクラスを選び直します", NamedTextColor.GRAY),
                Component.text("変更するとSPの振り分けはリセットされます", NamedTextColor.RED)
        )));

        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!isMainMenu(event.getView())) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isOwner(event.getView(), player)) return;

        switch (event.getRawSlot()) {
            case 20 -> {
                if (!levelService.tryLevelUp(player)) {
                    player.sendMessage("[iruuRPG] まだレベルアップに必要な経験値が足りません。");
                }
                open(player);
            }
            case 22 -> statsMenu.open(player);
            case 24 -> skillTreeMenu.open(player);
            case 31 -> classSelectMenu.open(player);
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!isMainMenu(event.getView())) return;

        event.setCancelled(true);
    }

    private String currentClassName(PlayerProfile profile) {
        return classService.currentClass(profile).map(ClassDefinition::name).orElse("未設定");
    }

    private boolean isMainMenu(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof Holder;
    }

    private boolean isOwner(InventoryView view, Player player) {
        InventoryHolder holder = view.getTopInventory().getHolder();
        return holder instanceof Holder menuHolder && menuHolder.ownerId().equals(player.getUniqueId());
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) {
                inventory.setItem(slot, filler);
            }
        }
    }

    private ItemStack playerIcon(Player player, PlayerProfile profile) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof SkullMeta skullMeta) {
            skullMeta.setOwningPlayer(player);
        }
        if (meta != null) {
            meta.displayName(Component.text(player.getName(), NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    line("Lv", String.valueOf(profile.level()), NamedTextColor.YELLOW),
                    line("XP", xpText(profile), NamedTextColor.GREEN),
                    line("HP", format(profile.currentHp()) + "/" + format(profile.maxHp()), NamedTextColor.RED),
                    line("MP", format(profile.currentMp()) + "/" + format(profile.maxMp()), NamedTextColor.AQUA)
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack levelUpButton(PlayerProfile profile) {
        boolean ready = levelService.canLevelUp(profile);
        Material material = ready ? Material.EXPERIENCE_BOTTLE : Material.GRAY_DYE;
        NamedTextColor color = ready ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(ready ? "クリックでレベルアップ" : "必要経験値に到達していません", ready ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(line("XP", xpText(profile), NamedTextColor.GREEN));
        lore.add(line("次のLv", profile.level() >= levelService.levelCap() ? "MAX" : String.valueOf(profile.level() + 1), NamedTextColor.YELLOW));
        lore.add(Component.empty());
        lore.add(Component.text("レベルアップ時にHP/MPを全回復し、演出を再生します", NamedTextColor.GRAY));
        return button(material, "レベルアップ", color, lore);
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        return item(material, name, color, lore);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(component -> component.decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component line(String label, String value, NamedTextColor color) {
        return Component.text(label + ": ", color)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private String xpText(PlayerProfile profile) {
        long required = levelService.requiredXpForNextLevel(profile.level());
        if (required <= 0L) {
            return "MAX";
        }
        return format(profile.xp()) + "/" + format(required);
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

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private Inventory inventory;

        private Holder(UUID ownerId) {
            this.ownerId = ownerId;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }
}
