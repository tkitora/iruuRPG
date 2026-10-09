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
import net.tkgon.mc.iruuRPG.classsystem.SpendNodeDefinition;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
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

/**
 * Skill menu: a row of SP spend nodes (left click +1 level, right click -1, shift+left +5)
 * and a row of milestone nodes that unlock for free as SP is spent.
 */
public final class SkillTreeMenu implements Listener {

    private static final Component TITLE = Component.text("iruuRPG スキル");
    private static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int RESET_SLOT = 49;
    private static final int SPEND_LABEL_SLOT = 9;
    private static final int MILESTONE_LABEL_SLOT = 27;
    private static final int SPEND_CENTER_SLOT = 22;
    private static final int MILESTONE_ROW_START = 36;
    private static final int BULK_LEVELS = 5;
    private static final int ROW = 9;

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

        Holder holder = new Holder(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);
        render(inventory, profile);
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!isSkillTree(event.getView())) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!isOwner(event.getView(), player)) return;
        if (event.getRawSlot() < 0 || event.getRawSlot() >= SIZE) return;

        PlayerProfile profile = profileManager.getOrCreate(player);
        int slot = event.getRawSlot();
        if (slot == RESET_SLOT) {
            classService.reset(profile);
            sync(player, profile);
            player.sendMessage("[iruuRPG] スキルポイントをリセットしました。");
            open(player);
            return;
        }

        ClassDefinition definition = classService.currentClass(profile).orElse(null);
        if (definition == null) return;
        List<SpendNodeDefinition> spendNodes = definition.spendNodes();
        int start = spendStartSlot(spendNodes.size());
        int times = event.isShiftClick() ? BULK_LEVELS : 1;
        boolean up;
        int index;
        if (slot >= start && slot < start + spendNodes.size()) {
            index = slot - start;
            up = event.isLeftClick();
            if (!event.isLeftClick() && !event.isRightClick()) return;
        } else if (slot >= start - ROW && slot < start - ROW + spendNodes.size()) {
            index = slot - (start - ROW);
            up = true;
        } else if (slot >= start + ROW && slot < start + ROW + spendNodes.size()) {
            index = slot - (start + ROW);
            up = false;
        } else {
            return;
        }

        String nodeId = spendNodes.get(index).id();
        boolean changed = false;
        for (int count = 0; count < times; count++) {
            boolean ok = up ? classService.levelUp(profile, nodeId) : classService.levelDown(profile, nodeId);
            if (!ok) break;
            changed = true;
        }
        if (!changed && up) player.sendMessage("[iruuRPG] スキルポイントが足りません。");
        if (changed) sync(player, profile);
        open(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!isSkillTree(event.getView())) return;

        event.setCancelled(true);
    }

    private void render(Inventory inventory, PlayerProfile profile) {
        fill(inventory);
        ClassDefinition definition = classService.currentClass(profile).orElse(null);
        inventory.setItem(INFO_SLOT, infoItem(profile, definition));
        inventory.setItem(RESET_SLOT, button(Material.BARRIER, "SPをリセット", NamedTextColor.RED,
                List.of(text("全ノードのレベルを0に戻します", NamedTextColor.GRAY), text("(SPは全額戻ります)", NamedTextColor.GRAY))));
        if (definition == null) return;

        inventory.setItem(SPEND_LABEL_SLOT, button(Material.EXPERIENCE_BOTTLE, "SPで強化", NamedTextColor.GREEN, List.of(
                text("1レベル = 1SP。上限なし", NamedTextColor.GRAY),
                text("左クリック: +1  シフト左: +" + BULK_LEVELS, NamedTextColor.GREEN),
                text("右クリック: -1", NamedTextColor.RED))));
        inventory.setItem(MILESTONE_LABEL_SLOT, button(Material.NETHER_STAR, "解放ノード", NamedTextColor.GOLD, List.of(
                text("SPを" + classService.milestoneInterval() + "使うごとに順番に無料で解放", NamedTextColor.GRAY),
                text("解放順は固定です", NamedTextColor.GRAY))));

        List<SpendNodeDefinition> spendNodes = definition.spendNodes();
        int spendStart = spendStartSlot(spendNodes.size());
        for (int index = 0; index < spendNodes.size(); index++) {
            inventory.setItem(spendStart + index, spendItem(profile, spendNodes.get(index)));
            inventory.setItem(spendStart + index - ROW, button(Material.LIME_STAINED_GLASS_PANE, "+1 レベル", NamedTextColor.GREEN,
                    List.of(text(spendNodes.get(index).name() + " を強化 (1SP)", NamedTextColor.GRAY),
                            text("シフト: +" + BULK_LEVELS, NamedTextColor.DARK_GREEN))));
            inventory.setItem(spendStart + index + ROW, button(Material.RED_STAINED_GLASS_PANE, "-1 レベル", NamedTextColor.RED,
                    List.of(text(spendNodes.get(index).name() + " を戻す (1SP返却)", NamedTextColor.GRAY),
                            text("シフト: -" + BULK_LEVELS, NamedTextColor.DARK_RED))));
        }

        List<ClassNodeDefinition> milestones = definition.milestones();
        int milestoneStart = MILESTONE_ROW_START + Math.max(0, (9 - milestones.size()) / 2);
        for (int index = 0; index < milestones.size() && index < 9; index++) {
            inventory.setItem(milestoneStart + index, milestoneItem(profile, milestones.get(index), index));
        }
    }

    private int spendStartSlot(int count) {
        return SPEND_CENTER_SLOT - count / 2;
    }

    private ItemStack infoItem(PlayerProfile profile, ClassDefinition definition) {
        List<Component> lore = new ArrayList<>();
        lore.add(line("クラス", definition == null ? "未設定" : definition.name(), NamedTextColor.GOLD));
        lore.add(line("レベル", String.valueOf(profile.level()), NamedTextColor.AQUA));
        lore.add(line("残りSP", String.valueOf(classService.availablePoints(profile)), NamedTextColor.GREEN));
        lore.add(line("使用SP", classService.spentPoints(profile) + " / " + classService.earnedPoints(profile), NamedTextColor.YELLOW));
        if (definition != null) {
            lore.add(line("解放ノード", classService.unlockedMilestoneCount(profile) + " / " + definition.milestones().size(), NamedTextColor.LIGHT_PURPLE));
        }
        return item(Material.AMETHYST_SHARD, "スキルポイント", NamedTextColor.LIGHT_PURPLE, lore, false);
    }

    private ItemStack spendItem(PlayerProfile profile, SpendNodeDefinition node) {
        int level = profile.classLevel(node.id());
        List<Component> lore = new ArrayList<>();
        lore.add(line("レベル", String.valueOf(level), level > 0 ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        lore.add(line("1レベルあたり", "+" + format(node.perLevel()) + " " + node.stat().key(), NamedTextColor.AQUA));
        lore.add(line("現在の合計", "+" + format(node.perLevel() * level) + " " + node.stat().key(), NamedTextColor.GREEN));
        lore.add(Component.empty());
        lore.add(text("左クリック: +1  シフト左: +" + BULK_LEVELS, NamedTextColor.GREEN));
        lore.add(text("右クリック: -1", NamedTextColor.RED));
        return item(node.icon(), node.name(), level > 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW, lore, level > 0);
    }

    private ItemStack milestoneItem(PlayerProfile profile, ClassNodeDefinition node, int index) {
        boolean unlocked = classService.isMilestoneUnlocked(profile, index);
        int needed = (index + 1) * classService.milestoneInterval();
        NamedTextColor color = unlocked ? NamedTextColor.GREEN : NamedTextColor.GRAY;
        List<Component> lore = new ArrayList<>();
        lore.add(line("状態", unlocked ? "解放済み" : "SP " + needed + " 使用で解放 (あと" + Math.max(0, needed - classService.spentPoints(profile)) + ")", color));
        lore.add(Component.empty());
        if (node.nodeType() == ClassNodeType.ACTIVE) {
            lore.add(line("種別", "アクティブ", NamedTextColor.AQUA));
        } else if (node.effectType() == ClassPassiveEffectType.SKILL) {
            lore.add(line("種別", "スキル効果", NamedTextColor.AQUA));
        } else {
            lore.add(line("種別", "ステータス", NamedTextColor.AQUA));
        }
        node.status().asMap().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().key()))
                .forEach(entry -> lore.add(line(entry.getKey().key(), "+" + format(entry.getValue()), NamedTextColor.GREEN)));
        if (!node.skillName().isBlank()) {
            classSkillRegistry.find(node.skillName()).ifPresent(skill -> appendSkillLore(lore, skill));
        }
        if (node.hotKey() != null) {
            lore.add(line("ホットキー", node.hotKey().key() + " / " + node.hotKey().displayName(), NamedTextColor.YELLOW));
        }
        return item(node.icon(), node.nodeName(), color, lore, unlocked);
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
