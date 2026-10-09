package net.tkgon.mc.iruuRPG.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.classsystem.ClassDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Chest GUI for choosing (or changing) the player's class. */
public final class ClassSelectMenu implements Listener {

    private static final Component TITLE = Component.text("iruuRPG クラス選択");
    private static final int ROW_SIZE = 9;
    private static final int MAX_CLASSES = 45;

    private final EquipmentService equipmentService;
    private final ClassService classService;
    private final PlayerProfileManager profileManager;
    private final PlayerBars playerBars;
    private MainMenu mainMenu;

    public void setMainMenu(MainMenu mainMenu) {
        this.mainMenu = mainMenu;
    }

    public ClassSelectMenu(
            EquipmentService equipmentService,
            ClassService classService,
            PlayerProfileManager profileManager,
            PlayerBars playerBars
    ) {
        this.equipmentService = equipmentService;
        this.classService = classService;
        this.profileManager = profileManager;
        this.playerBars = playerBars;
    }

    public void open(Player player) {
        open(player, null, null);
    }

    /**
     * Opens the menu with custom descriptions (class id -> line) and a callback that runs after a class is chosen.
     * Used by the tutorial, where the NPC explains each class.
     */
    public void open(Player player, Map<String, String> speechByClassId, Consumer<String> onSelected) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        List<ClassDefinition> classes = sortedClasses();
        if (classes.isEmpty()) {
            player.sendMessage("[iruuRPG] 選べるクラスがありません。");
            return;
        }

        // The back button is only offered after a class was chosen (the first choice must not be skipped)
        // and never inside the tutorial, where choosing the class is a step of the flow.
        boolean canGoBack = profile.classChosen() && mainMenu != null && onSelected == null;
        int classRows = Math.max(1, (classes.size() + ROW_SIZE - 1) / ROW_SIZE);
        int rows = classRows + (canGoBack ? 1 : 0);
        Holder holder = new Holder(player.getUniqueId(), classes.stream().map(ClassDefinition::id).toList(), speechByClassId, onSelected, canGoBack ? classRows * ROW_SIZE : -1);
        Inventory inventory = Bukkit.createInventory(holder, rows * ROW_SIZE, TITLE);
        holder.setInventory(inventory);
        for (int slot = 0; slot < classes.size(); slot++) {
            inventory.setItem(slot, icon(classes.get(slot), profile, speechByClassId == null ? null : speechByClassId.get(classes.get(slot).id())));
        }
        if (canGoBack) {
            inventory.setItem(classRows * ROW_SIZE, MenuButtons.back());
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.ownerId().equals(player.getUniqueId())) return;
        if (event.getRawSlot() == holder.backSlot() && holder.backSlot() >= 0 && mainMenu != null) {
            mainMenu.open(player);
            return;
        }
        if (event.getRawSlot() < 0 || event.getRawSlot() >= holder.classIds().size()) return;

        String classId = holder.classIds().get(event.getRawSlot());
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (!classService.select(profile, classId)) {
            player.sendMessage("[iruuRPG] そのクラスは選べません。");
            return;
        }

        PlayerProfile refreshed = equipmentService.recalculate(player);
        playerBars.sync(player, refreshed);
        profileManager.save(player);
        player.closeInventory();
        String name = classService.currentClass(profile).map(ClassDefinition::name).orElse(classId);
        player.sendMessage("[iruuRPG] クラスを「" + name + "」に設定しました。");
        if (holder.onSelected() != null) {
            holder.onSelected().accept(classId);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private List<ClassDefinition> sortedClasses() {
        return classService.selectableClasses().stream()
                .sorted(Comparator.comparing(ClassDefinition::id))
                .limit(MAX_CLASSES)
                .toList();
    }

    private ItemStack icon(ClassDefinition definition, PlayerProfile profile, String speech) {
        ItemStack item = new ItemStack(definition.icon());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(Component.text(definition.name(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        List<String> lines = speech == null ? definition.description() : List.of(speech);
        for (String line : lines) {
            lore.add(Component.text(line, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        }
        if (!lines.isEmpty()) lore.add(Component.empty());
        lore.add(Component.text("クリックでこのクラスを選択", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        if (profile.classChosen() && definition.id().equals(profile.classId())) {
            lore.add(Component.text("現在のクラス", NamedTextColor.GREEN)
                    .decoration(TextDecoration.ITALIC, false));
        } else if (profile.classChosen()) {
            lore.add(Component.text("変更するとスキルツリーはリセットされます", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private final List<String> classIds;
        private final Map<String, String> speech;
        private final Consumer<String> onSelected;
        private final int backSlot;
        private Inventory inventory;

        private Holder(UUID ownerId, List<String> classIds, Map<String, String> speech, Consumer<String> onSelected, int backSlot) {
            this.ownerId = ownerId;
            this.classIds = classIds;
            this.speech = speech;
            this.onSelected = onSelected;
            this.backSlot = backSlot;
        }

        private int backSlot() {
            return backSlot;
        }

        private Consumer<String> onSelected() {
            return onSelected;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private List<String> classIds() {
            return classIds;
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
