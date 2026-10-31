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
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Chest GUI for choosing (or changing) the player's class: a large menu with the classes side by side (each in a column
 * of its color, with the words of classes/*.yml), then a confirmation screen. The tutorial opens it with a callback that
 * runs after the choice.
 */
public final class ClassSelectMenu implements Listener {

    private static final Component TITLE = Component.text("iruuRPG クラス選択");
    private static final Component CONFIRM_TITLE = Component.text("このクラスでいいですか？");
    private static final int SIZE = 54;
    private static final int ROW = 9;
    private static final int HEADER_SLOT = 4;
    private static final int BACK_SLOT = 45;
    /** The first slot of the row of classes (the third row), centered for up to seven classes. */
    private static final int CLASS_ROW = 18;
    private static final int MAX_CLASSES = 7;
    private static final int CONFIRM_SIZE = 27;
    private static final int CONFIRM_YES = 11;
    private static final int CONFIRM_ICON = 13;
    private static final int CONFIRM_NO = 15;

    private final EquipmentService equipmentService;
    private final ClassService classService;
    private final PlayerProfileManager profileManager;
    private final PlayerBars playerBars;
    private MainMenu mainMenu;
    private Consumer<Player> onClassChanged;

    /** What runs when a player picks a class other than the one they had (the effects of the old class end). */
    public void setOnClassChanged(Consumer<Player> onClassChanged) {
        this.onClassChanged = onClassChanged;
    }

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
        open(player, null);
    }

    /**
     * Opens the menu with a callback that runs after a class is chosen and confirmed (the menu is closed by then).
     * Used by the tutorial, where choosing the class is a step of the flow.
     */
    public void open(Player player, Consumer<String> onSelected) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        List<ClassDefinition> classes = sortedClasses();
        if (classes.isEmpty()) {
            player.sendMessage("[iruuRPG] 選べるクラスがありません。");
            return;
        }

        // The back button is only offered after a class was chosen (the first choice must not be skipped)
        // and never inside the tutorial, where choosing the class is a step of the flow.
        boolean canGoBack = profile.classChosen() && mainMenu != null && onSelected == null;
        int start = CLASS_ROW + (ROW - classes.size()) / 2;
        Holder holder = new Holder(player.getUniqueId(), Screen.SELECT, classes.stream().map(ClassDefinition::id).toList(), start, onSelected);
        Inventory inventory = Bukkit.createInventory(holder, SIZE, TITLE);
        holder.setInventory(inventory);

        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) inventory.setItem(slot, filler);
        inventory.setItem(HEADER_SLOT, header());
        for (int index = 0; index < classes.size(); index++) {
            ClassDefinition definition = classes.get(index);
            int slot = start + index;
            ItemStack color = pane(ClassInfo.glass(definition.id()));
            inventory.setItem(slot - ROW, color);
            inventory.setItem(slot + ROW, color);
            inventory.setItem(slot + 2 * ROW, color);
            inventory.setItem(slot, ClassInfo.icon(definition, selectLines(definition, profile)));
        }
        if (canGoBack) inventory.setItem(BACK_SLOT, MenuButtons.back());
        player.openInventory(inventory);
    }

    /** True while the player looks at this menu (the list or the confirmation): the tutorial must not open it again then. */
    public boolean isViewing(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder
                && holder.ownerId().equals(player.getUniqueId());
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!holder.ownerId().equals(player.getUniqueId())) return;
        int slot = event.getRawSlot();

        if (holder.screen() == Screen.CONFIRM) {
            String classId = holder.classIds().get(0);
            if (slot == CONFIRM_YES) {
                choose(player, classId, holder.onSelected());
            } else if (slot == CONFIRM_NO) {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 0.8f);
                open(player, holder.onSelected());
            }
            return;
        }

        if (slot == BACK_SLOT && mainMenu != null && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
            mainMenu.open(player);
            return;
        }
        int index = slot - holder.start();
        if (index < 0 || index >= holder.classIds().size()) return;

        String classId = holder.classIds().get(index);
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (profile.classChosen() && classId.equals(profile.classId()) && holder.onSelected() == null) {
            player.sendMessage("[iruuRPG] すでにこのクラスです。");
            return;
        }
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        openConfirm(player, classId, holder.onSelected());
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) {
            event.setCancelled(true);
        }
    }

    private void openConfirm(Player player, String classId, Consumer<String> onSelected) {
        ClassDefinition definition = classService.selectableClasses().stream()
                .filter(candidate -> candidate.id().equals(classId)).findFirst().orElse(null);
        if (definition == null) {
            player.sendMessage("[iruuRPG] そのクラスは選べません。");
            return;
        }
        PlayerProfile profile = profileManager.getOrCreate(player);
        Holder holder = new Holder(player.getUniqueId(), Screen.CONFIRM, List.of(classId), 0, onSelected);
        Inventory inventory = Bukkit.createInventory(holder, CONFIRM_SIZE, CONFIRM_TITLE);
        holder.setInventory(inventory);

        ItemStack color = pane(ClassInfo.glass(classId));
        for (int slot = 0; slot < CONFIRM_SIZE; slot++) inventory.setItem(slot, color);
        inventory.setItem(CONFIRM_ICON, ClassInfo.icon(definition, List.of()));
        List<Component> yes = new ArrayList<>();
        yes.add(text("「" + definition.name() + "」になる", NamedTextColor.WHITE));
        if (profile.classChosen()) {
            yes.add(text("スキルツリーはリセットされます", NamedTextColor.RED));
        }
        inventory.setItem(CONFIRM_YES, button(Material.LIME_CONCRETE, "決定", NamedTextColor.GREEN, yes));
        inventory.setItem(CONFIRM_NO, button(Material.RED_CONCRETE, "選びなおす", NamedTextColor.RED,
                List.of(text("クラスの一覧に戻る", NamedTextColor.GRAY))));
        player.openInventory(inventory);
    }

    private void choose(Player player, String classId, Consumer<String> onSelected) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        boolean changed = !classId.equals(profile.classId());
        if (!classService.select(profile, classId)) {
            player.sendMessage("[iruuRPG] そのクラスは選べません。");
            return;
        }

        if (changed && onClassChanged != null) onClassChanged.accept(player);
        PlayerProfile refreshed = equipmentService.recalculate(player);
        playerBars.sync(player, refreshed);
        profileManager.save(player);
        player.closeInventory();
        String name = classService.currentClass(profile).map(ClassDefinition::name).orElse(classId);
        player.sendMessage("[iruuRPG] クラスを「" + name + "」に設定しました。");
        if (onSelected != null) {
            onSelected.accept(classId);
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }
    }

    /** The classes in {@link ClassInfo#ORDER}, then any other selectable class by id. */
    private List<ClassDefinition> sortedClasses() {
        return classService.selectableClasses().stream()
                .sorted(Comparator.comparingInt((ClassDefinition definition) -> {
                    int at = ClassInfo.ORDER.indexOf(definition.id());
                    return at < 0 ? Integer.MAX_VALUE : at;
                }).thenComparing(ClassDefinition::id))
                .limit(MAX_CLASSES)
                .toList();
    }

    private List<Component> selectLines(ClassDefinition definition, PlayerProfile profile) {
        List<Component> lines = new ArrayList<>();
        if (profile.classChosen() && definition.id().equals(profile.classId())) {
            lines.add(text("現在のクラス", NamedTextColor.GREEN));
        } else {
            lines.add(text("クリックで選ぶ (このあと確認があります)", NamedTextColor.GRAY));
            if (profile.classChosen()) lines.add(text("変更するとスキルツリーはリセットされます", NamedTextColor.RED));
        }
        return lines;
    }

    private ItemStack header() {
        List<Component> lore = new ArrayList<>();
        lore.add(text("5つのクラスから1つを選ぼう", NamedTextColor.WHITE));
        lore.add(text("カーソルを合わせると説明が出ます", NamedTextColor.GRAY));
        lore.add(text("クラスはあとからメインメニューで変えられます", NamedTextColor.GRAY));
        return button(Material.LECTERN, "クラス選択", NamedTextColor.GOLD, lore);
    }

    private static ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" "));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private enum Screen { SELECT, CONFIRM }

    private static final class Holder implements MenuHolder {
        private final UUID ownerId;
        private final Screen screen;
        private final List<String> classIds;
        private final int start;
        private final Consumer<String> onSelected;
        private Inventory inventory;

        private Holder(UUID ownerId, Screen screen, List<String> classIds, int start, Consumer<String> onSelected) {
            this.ownerId = ownerId;
            this.screen = screen;
            this.classIds = classIds;
            this.start = start;
            this.onSelected = onSelected;
        }

        private UUID ownerId() {
            return ownerId;
        }

        private Screen screen() {
            return screen;
        }

        private List<String> classIds() {
            return classIds;
        }

        private int start() {
            return start;
        }

        private Consumer<String> onSelected() {
            return onSelected;
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
