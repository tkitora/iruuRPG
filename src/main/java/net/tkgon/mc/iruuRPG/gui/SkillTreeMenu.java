package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.classsystem.ClassDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassNodeDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassNodeType;
import net.tkgon.mc.iruuRPG.classsystem.ClassPassiveEffectType;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.classsystem.ClassSkillDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassSkillRegistry;
import net.tkgon.mc.iruuRPG.classsystem.SkillTreeNodeShape;
import net.tkgon.mc.iruuRPG.classsystem.SkillTreeShape;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.StatType;
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
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class SkillTreeMenu implements Listener {

    private static final Component TITLE = Component.text("iruuRPG スキルツリー");

    private final EquipmentService equipmentService;
    private final ClassService classService;
    private final ClassSkillRegistry classSkillRegistry;
    private final PlayerProfileManager profileManager;
    private final PlayerBars playerBars;

    public SkillTreeMenu(
            EquipmentService equipmentService,
            ClassService classService,
            ClassSkillRegistry classSkillRegistry,
            PlayerProfileManager profileManager,
            PlayerBars playerBars
    ) {
        this.equipmentService = equipmentService;
        this.classService = classService;
        this.classSkillRegistry = classSkillRegistry;
        this.profileManager = profileManager;
        this.playerBars = playerBars;
    }

    public void open(Player player) {
        PlayerProfile profile = equipmentService.recalculate(player);
        classService.ensureProfile(profile);
        profile.setClassScroll(Math.min(profile.classScroll(), classService.shape().maxScrollOffset()));

        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, classService.shape().size(), TITLE);
        holder.setInventory(inventory);
        render(inventory, player, profile);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!isSkillTree(event.getView())) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isOwner(event.getView(), player)) return;
        if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;

        PlayerProfile profile = profileManager.getOrCreate(player);
        SkillTreeShape shape = classService.shape();
        int slot = event.getRawSlot();
        if (slot == shape.scrollUpSlot()) {
            profile.setClassScroll(Math.min(shape.maxScrollOffset(), profile.classScroll() + 1));
            open(player);
            return;
        }
        if (slot == shape.scrollDownSlot()) {
            profile.setClassScroll(Math.max(0, profile.classScroll() - 1));
            open(player);
            return;
        }
        if (slot == shape.resetSlot()) {
            classService.reset(profile);
            sync(player, profile);
            player.sendMessage("[iruuRPG] スキルツリーをリセットしました。");
            open(player);
            return;
        }

        String nodeId = nodeIdAtSlot(profile, slot);
        if (nodeId == null) return;

        if (event.isLeftClick()) {
            if (!classService.acquire(player, profile, nodeId)) {
                player.sendMessage("[iruuRPG] このノードはまだ取得できません。");
            } else {
                sync(player, profile);
            }
            open(player);
            return;
        }

        if (event.isRightClick()) {
            if (!classService.release(player, profile, nodeId)) {
                player.sendMessage("[iruuRPG] このノードは解除できません。");
            } else {
                sync(player, profile);
            }
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!isSkillTree(event.getView())) return;

        event.setCancelled(true);
    }

    private void render(Inventory inventory, Player player, PlayerProfile profile) {
        fill(inventory);
        ClassDefinition classDefinition = classService.currentClass(profile).orElse(null);
        SkillTreeShape shape = classService.shape();
        List<SkillTreeNodeShape> nodes = shape.orderedNodes();

        inventory.setItem(shape.scrollUpSlot(), button(Material.ARROW, "上へ", NamedTextColor.AQUA, List.of(
                text("上位ノード側へスクロール", NamedTextColor.GRAY)
        )));
        inventory.setItem(shape.scrollDownSlot(), button(Material.ARROW, "下へ", NamedTextColor.AQUA, List.of(
                text("下位ノード側へスクロール", NamedTextColor.GRAY)
        )));
        inventory.setItem(shape.resetSlot(), button(Material.BARRIER, "リセット", NamedTextColor.RED, List.of(
                text("取得済みノードをすべて解除", NamedTextColor.GRAY),
                text("消費ポイントは返却されます", NamedTextColor.GRAY)
        )));
        inventory.setItem(shape.pointSlot(), pointItem(profile, classDefinition));

        int start = Math.max(0, Math.min(profile.classScroll(), Math.max(0, nodes.size() - shape.visibleRows())));
        int end = Math.min(nodes.size(), start + shape.visibleRows());
        List<SkillTreeNodeShape> visible = nodes.subList(start, end);
        for (int index = 0; index < visible.size(); index++) {
            SkillTreeNodeShape nodeShape = visible.get(index);
            int slot = nodeSlot(index);
            ClassNodeDefinition node = classDefinition == null ? null : classDefinition.node(nodeShape.id());
            inventory.setItem(slot, nodeItem(profile, classDefinition, nodeShape, node));
        }
    }

    private ItemStack nodeItem(PlayerProfile profile, ClassDefinition classDefinition, SkillTreeNodeShape shape, ClassNodeDefinition node) {
        boolean acquired = profile.classNodes().contains(shape.id());
        boolean available = classService.canAcquire(profile, shape.id());
        Material material = node == null ? Material.GRAY_DYE : node.icon();
        NamedTextColor color = acquired ? NamedTextColor.GREEN : available ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        String name = node == null ? shape.id() : node.nodeName();
        List<Component> lore = new ArrayList<>();
        lore.add(line("ID", shape.id(), NamedTextColor.DARK_AQUA));
        lore.add(line("状態", acquired ? "取得済み" : available ? "取得可能" : "未解放", color));
        lore.add(line("Cost", node == null ? "-" : String.valueOf(node.cost()), NamedTextColor.GOLD));
        if (shape.parentId() != null) {
            lore.add(line("前提", shape.parentId(), NamedTextColor.GRAY));
        }
        if (node != null) {
            lore.add(Component.empty());
            lore.add(line("種別", node.nodeType() == ClassNodeType.ACTIVE ? "active" : "passive", NamedTextColor.AQUA));
            if (node.nodeType() == ClassNodeType.PASSIVE && node.effectType() == ClassPassiveEffectType.STATUS) {
                node.status().asMap().entrySet().stream()
                        .sorted(Comparator.comparing(entry -> entry.getKey().key()))
                        .forEach(entry -> lore.add(line(entry.getKey().key(), format(entry.getValue()), NamedTextColor.GREEN)));
            }
            if (!node.skillName().isBlank()) {
                lore.add(line("skill", node.skillName(), NamedTextColor.LIGHT_PURPLE));
                classSkillRegistry.find(node.skillName()).ifPresent(skill -> appendSkillLore(lore, skill));
            }
            if (node.hotKey() != null) {
                lore.add(line("hot_key", node.hotKey().key() + " / " + node.hotKey().displayName(), NamedTextColor.YELLOW));
            }
        }
        lore.add(Component.empty());
        lore.add(text("左クリック: 取得", NamedTextColor.GREEN));
        lore.add(text("右クリック: 解除", NamedTextColor.RED));

        return item(material, name, color, lore, acquired);
    }

    private void appendSkillLore(List<Component> lore, ClassSkillDefinition skill) {
        lore.add(line("スキル名", skill.name(), NamedTextColor.LIGHT_PURPLE));
        if (skill.cost() > 0.0) {
            lore.add(line("MP", format(skill.cost()), NamedTextColor.AQUA));
        }
        if (skill.cooldownTicks() > 0) {
            lore.add(line("CD", format(skill.cooldownTicks() / 20.0) + "秒", NamedTextColor.YELLOW));
        }
        for (String description : skill.description()) {
            lore.add(text(description, NamedTextColor.GRAY));
        }
    }

    private ItemStack pointItem(PlayerProfile profile, ClassDefinition classDefinition) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("クラス", classDefinition == null ? "未設定" : classDefinition.name(), NamedTextColor.GOLD));
        lore.add(line("残りSP", String.valueOf(profile.skillPoints()), NamedTextColor.GREEN));
        lore.add(line("使用SP", String.valueOf(profile.spentSkillPoints()), NamedTextColor.YELLOW));
        lore.add(line("取得ノード", String.valueOf(profile.classNodes().size()), NamedTextColor.AQUA));
        return item(Material.AMETHYST_SHARD, "スキルポイント", NamedTextColor.LIGHT_PURPLE, lore, false);
    }

    private String nodeIdAtSlot(PlayerProfile profile, int slot) {
        SkillTreeShape shape = classService.shape();
        List<SkillTreeNodeShape> nodes = shape.orderedNodes();
        int start = Math.max(0, Math.min(profile.classScroll(), Math.max(0, nodes.size() - shape.visibleRows())));
        for (int index = 0; index < shape.visibleRows(); index++) {
            if (slot != nodeSlot(index)) continue;
            int nodeIndex = start + index;
            return nodeIndex >= 0 && nodeIndex < nodes.size() ? nodes.get(nodeIndex).id() : null;
        }
        return null;
    }

    private int nodeSlot(int index) {
        return (4 - index) * 9 + 4;
    }

    private void sync(Player player, PlayerProfile profile) {
        PlayerProfile refreshed = equipmentService.recalculate(player);
        playerBars.sync(player, refreshed);
        profileManager.save(player);
    }

    private boolean isSkillTree(InventoryView view) {
        return view.getTopInventory().getHolder() instanceof Holder;
    }

    private boolean isOwner(InventoryView view, Player player) {
        InventoryHolder holder = view.getTopInventory().getHolder();
        return holder instanceof Holder menuHolder && menuHolder.ownerId().equals(player.getUniqueId());
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        return item(material, name, color, lore, false);
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore, boolean glint) {
        ItemStack item = new ItemStack(material == null ? Material.PAPER : material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(component -> component.decoration(TextDecoration.ITALIC, false)).toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            if (glint) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private Component line(String label, String value, NamedTextColor color) {
        return Component.text(label + ": ", color)
                .append(Component.text(value, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false);
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    private String format(double value) {
        if (Math.abs(value - Math.rint(value)) < 1.0E-9) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    private static final class Holder implements InventoryHolder {
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
