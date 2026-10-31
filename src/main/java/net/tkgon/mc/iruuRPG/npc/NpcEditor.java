package net.tkgon.mc.iruuRPG.npc;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import net.tkgon.mc.iruuRPG.questline.Questline;
import net.tkgon.mc.iruuRPG.questline.QuestlineData;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import net.tkgon.mc.iruuRPG.questline.QuestlineEngine;
import net.tkgon.mc.iruuRPG.questline.QuestlineService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Editor mode for the NPCs (/npc edit), made like the spawner editor: the hotbar becomes tools (place, set up, move,
 * delete, list, debug, done; slot 7 stays empty to talk to an NPC as a player would), and over every NPC near an editor
 * a label shows its id, name, look and the quest lines and steps that use it (like the BT debug view, only editors see
 * it). The debug tool opens the quest lines of the NPC for a player (yourself or one nearby): status, step, and every
 * flag one by one, which can be changed there (the same as /questadmin). Messages go to the chat.
 */
public final class NpcEditor implements Listener {

    private enum Tool {
        PLACE(Material.VILLAGER_SPAWN_EGG, "NPC を置く", NamedTextColor.GREEN, "ブロックをクリック → 見た目を選んで、その上に置く"),
        SETUP(Material.COMPARATOR, "NPC を設定する", NamedTextColor.AQUA, "NPC をクリック → 名前・見た目・向き・アイドルの言葉・id"),
        MOVE(Material.ENDER_PEARL, "NPC を動かす", NamedTextColor.LIGHT_PURPLE, "NPC をクリックで選ぶ → ブロックをクリックした所へ"),
        DELETE(Material.BARRIER, "NPC を消す", NamedTextColor.DARK_RED, "しゃがんで NPC をクリック → 消す"),
        LIST(Material.BOOK, "NPC の一覧", NamedTextColor.GOLD, "左クリック: テレポート / 右クリック: 設定"),
        DEBUG(Material.SPYGLASS, "クエストのデバッグ", NamedTextColor.YELLOW, "NPC をクリック → その NPC のクエストを、フラグ単位で確認・変更"),
        DONE(Material.LIME_DYE, "エディターを終える", NamedTextColor.WHITE, "元のホットバーに戻る (変更はその場で保存済み)");

        private final Material material;
        private final String name;
        private final NamedTextColor color;
        private final String lore;

        Tool(Material material, String name, NamedTextColor color, String lore) {
            this.material = material;
            this.name = name;
            this.color = color;
            this.lore = lore;
        }
    }

    private static final int TOOL_SLOTS = 8;
    private static final Tool[] HOTBAR = {Tool.PLACE, Tool.SETUP, Tool.MOVE, Tool.DELETE, Tool.LIST, Tool.DEBUG, null, Tool.DONE};
    private static final double SHOW_DISTANCE = 48.0;
    private static final double AIM_DISTANCE = 40.0;
    private static final double AIM_TOLERANCE = 1.2;
    private static final List<String> PROFESSIONS = List.of("NONE", "FARMER", "FISHERMAN", "SHEPHERD", "FLETCHER", "LIBRARIAN", "CARTOGRAPHER",
            "CLERIC", "ARMORER", "WEAPONSMITH", "TOOLSMITH", "BUTCHER", "LEATHERWORKER", "MASON", "NITWIT");
    private static final List<Material> PROFESSION_ICONS = List.of(Material.EMERALD, Material.COMPOSTER, Material.BARREL, Material.LOOM,
            Material.FLETCHING_TABLE, Material.LECTERN, Material.CARTOGRAPHY_TABLE, Material.BREWING_STAND, Material.BLAST_FURNACE,
            Material.GRINDSTONE, Material.SMITHING_TABLE, Material.SMOKER, Material.CAULDRON, Material.STONECUTTER, Material.GREEN_WOOL);
    private static final List<String> VILLAGER_TYPES = List.of("PLAINS", "DESERT", "JUNGLE", "SAVANNA", "SNOW", "SWAMP", "TAIGA");
    private static final List<String> ENTITY_LOOKS = List.of("WANDERING_TRADER", "PIGLIN", "ZOMBIE_VILLAGER", "WITCH", "PILLAGER", "VINDICATOR",
            "EVOKER", "IRON_GOLEM", "SNOW_GOLEM", "ALLAY", "ZOMBIE", "HUSK", "DROWNED", "SKELETON", "STRAY", "PIGLIN_BRUTE", "ENDERMAN", "BLAZE",
            "FOX", "CAT", "WOLF", "HORSE", "DONKEY", "LLAMA", "SHEEP", "COW", "PIG", "CHICKEN", "PANDA", "POLAR_BEAR", "GOAT", "FROG", "AXOLOTL",
            "PARROT", "RABBIT", "CAMEL");

    private static final class Session {
        private final ItemStack[] savedHotbar;
        private Location pending;
        private String changing;
        private String moving;
        private UUID debugTarget;
        private String confirmReset;

        private Session(ItemStack[] savedHotbar) {
            this.savedHotbar = savedHotbar;
        }
    }

    private final JavaPlugin plugin;
    private final NpcService npcs;
    private final QuestlineService questlines;
    private final NamespacedKey toolKey;
    private final File saveFolder;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<String, TextDisplay> labels = new HashMap<>();
    private final Map<String, String> labelTexts = new HashMap<>();
    private Predicate<Player> otherEditor = player -> false;
    private BukkitTask task;

    public NpcEditor(JavaPlugin plugin, NpcService npcs, QuestlineService questlines) {
        this.plugin = plugin;
        this.npcs = npcs;
        this.questlines = questlines;
        this.toolKey = new NamespacedKey(plugin, "npc_tool");
        this.saveFolder = new File(plugin.getDataFolder(), "editor");
        npcs.setEditorClick(this::onNpcClick);
    }

    /** True while the player is in another editor (spawners, dungeons): editors cannot share the hotbar. */
    public void setOtherEditor(Predicate<Player> otherEditor) {
        this.otherEditor = otherEditor;
    }

    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::syncLabels, 20L, 10L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) stop(player, false);
        }
        clearLabels();
    }

    // ---- switching (the hotbar is kept, also in a file) ---------------------------------------------------------------

    public void toggle(Player player) {
        if (editing(player)) {
            stop(player, true);
            return;
        }
        if (otherEditor.test(player)) {
            player.sendMessage(Component.text("[NPC] ほかのエディター (スポナー・ダンジョン) を終えてから使ってください。", NamedTextColor.RED));
            return;
        }
        ItemStack[] saved = new ItemStack[TOOL_SLOTS];
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            saved[slot] = item == null ? null : item.clone();
        }
        sessions.put(player.getUniqueId(), new Session(saved));
        writeBackup(player.getUniqueId(), saved);
        for (int slot = 0; slot < HOTBAR.length; slot++) {
            player.getInventory().setItem(slot, HOTBAR[slot] == null ? null : toolItem(HOTBAR[slot]));
        }
        player.getInventory().setHeldItemSlot(0);
        player.sendMessage(Component.text("[NPC] エディターモード。7番 (空き) を持てば、ふつうに話しかけて試せます。終えるときは 8番、または /npc edit", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        syncLabels();
    }

    public void stop(Player player, boolean announce) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            player.getInventory().setItem(slot, session.savedHotbar[slot] == null ? null : session.savedHotbar[slot].clone());
        }
        deleteBackup(player.getUniqueId());
        if (announce) player.sendMessage(Component.text("[NPC] エディターを終えた。", NamedTextColor.GRAY));
        if (sessions.isEmpty()) clearLabels();
    }

    private File backupFile(UUID id) {
        return new File(saveFolder, "npc-" + id + ".yml");
    }

    private void writeBackup(UUID id, ItemStack[] hotbar) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int slot = 0; slot < hotbar.length; slot++) yaml.set("hotbar." + slot, hotbar[slot]);
        try {
            if (!saveFolder.exists() && !saveFolder.mkdirs()) return;
            yaml.save(backupFile(id));
        } catch (IOException e) {
            plugin.getLogger().warning("npc editor: could not save the hotbar of " + id + ": " + e.getMessage());
        }
    }

    private void deleteBackup(UUID id) {
        File file = backupFile(id);
        if (file.exists() && !file.delete()) plugin.getLogger().warning("npc editor: could not delete " + file.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        File file = backupFile(player.getUniqueId());
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (int slot = 0; slot < TOOL_SLOTS; slot++) player.getInventory().setItem(slot, yaml.getItemStack("hotbar." + slot));
        deleteBackup(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (editing(event.getEntity())) event.getDrops().removeIf(item -> toolOf(item) != null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!editing(player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) stop(player, true);
        });
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onCreativeClick(InventoryCreativeEvent event) {
        if (event.getWhoClicked() instanceof Player player && editing(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && editing(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onDrop(PlayerDropItemEvent event) {
        if (editing(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (editing(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && editing(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (editing(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlace(BlockPlaceEvent event) {
        if (editing(event.getPlayer())) event.setCancelled(true);
    }

    // ---- the tools ----------------------------------------------------------------------------------------------------

    private ItemStack toolItem(Tool tool) {
        ItemStack item = new ItemStack(tool.material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(tool.name, tool.color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text(tool.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private Tool toolOf(ItemStack item) {
        if (item == null || item.getItemMeta() == null) return null;
        String name = item.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        if (name == null) return null;
        try {
            return Tool.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** A click on an NPC with a tool (true = used here; no tool in hand = talk as a player would). */
    private boolean onNpcClick(Player player, Npc npc) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return false;
        Tool tool = toolOf(player.getInventory().getItemInMainHand());
        if (tool == null) return false;
        use(player, session, tool, npc, null);
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Tool tool = toolOf(event.getItem());
        if (tool == null) return;
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;
        Block block = event.getClickedBlock() == null ? null : event.getClickedBlock().getRelative(event.getBlockFace());
        use(player, session, tool, aimed(player), block);
    }

    private void use(Player player, Session session, Tool tool, Npc npc, Block block) {
        switch (tool) {
            case DONE -> stop(player, true);
            case LIST -> openList(player);
            case PLACE -> {
                if (block == null) {
                    hint(player, "ブロックをクリックしてください。");
                    return;
                }
                Location at = block.getLocation().add(0.5, 0, 0.5);
                at.setYaw(player.getLocation().getYaw() + 180f);
                session.pending = at;
                session.changing = null;
                openPicker(player);
            }
            case MOVE -> {
                if (session.moving != null && block != null) {
                    Npc moving = npcs.find(session.moving).orElse(null);
                    session.moving = null;
                    if (moving == null) return;
                    Location at = block.getLocation().add(0.5, 0, 0.5);
                    at.setYaw(moving.yaw());
                    moving.moveTo(at);
                    npcs.changed(moving);
                    done(player, moving.id() + " を動かした。");
                } else if (npc != null) {
                    session.moving = npc.id();
                    hint(player, npc.id() + " を選んだ。動かす先のブロックをクリック。");
                } else {
                    hint(player, "NPC をクリックして選んでください。");
                }
            }
            case SETUP -> {
                if (npc == null) hint(player, "NPC をクリックしてください。");
                else openSetup(player, npc);
            }
            case DELETE -> {
                if (npc == null) {
                    hint(player, "NPC をクリックしてください。");
                } else if (!player.isSneaking()) {
                    hint(player, "消すときは、しゃがんでクリックしてください。");
                } else {
                    npcs.remove(npc);
                    done(player, npc.id() + " を消した。クエストで使われていれば、そのクエストの指定も直してください。");
                }
            }
            case DEBUG -> {
                if (npc == null) hint(player, "NPC をクリックしてください。");
                else openDebug(player, npc);
            }
        }
    }

    /** The NPC the player looks at (the nearest along the line of sight; walls do not block it). */
    private Npc aimed(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        Npc best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Npc npc : npcs.all()) {
            if (!npc.worldName().equals(player.getWorld().getName())) continue;
            Vector to = new Vector(npc.x(), npc.y() + 1.0, npc.z()).subtract(eye.toVector());
            double along = to.dot(direction);
            if (along < 0 || along > AIM_DISTANCE) continue;
            double off = to.clone().subtract(direction.clone().multiply(along)).length();
            if (off <= AIM_TOLERANCE && along < bestAlong) {
                bestAlong = along;
                best = npc;
            }
        }
        return best;
    }

    private void done(Player player, String message) {
        player.sendMessage(Component.text("[NPC] " + message, NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    private void hint(Player player, String message) {
        player.sendMessage(Component.text("[NPC] " + message, NamedTextColor.GRAY));
    }

    /** A chat line whose button puts a command into the chat box, for the text the player types (name, idle lines, id). */
    private void suggest(Player player, String label, String command) {
        player.closeInventory();
        player.sendMessage(Component.text("[NPC] " + label + " ", NamedTextColor.YELLOW)
                .append(Component.text("[ここを押して入力]", NamedTextColor.AQUA, TextDecoration.UNDERLINED).clickEvent(ClickEvent.suggestCommand(command))));
    }

    // ---- labels over the NPCs (editors only) --------------------------------------------------------------------------

    private void syncLabels() {
        if (sessions.isEmpty()) return;
        List<Player> editors = new ArrayList<>();
        for (UUID id : sessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) editors.add(player);
        }
        Set<String> wanted = new LinkedHashSet<>();
        for (Npc npc : npcs.all()) {
            LivingEntity entity = npcs.entity(npc);
            if (entity == null) continue;
            boolean near = editors.stream().anyMatch(p -> p.getWorld().equals(entity.getWorld()) && p.getLocation().distanceSquared(entity.getLocation()) < SHOW_DISTANCE * SHOW_DISTANCE);
            if (!near) continue;
            wanted.add(npc.id());
            String text = labelText(npc);
            Location at = entity.getLocation().add(0, entity.getHeight() + 1.6, 0);
            TextDisplay label = labels.get(npc.id());
            if (label == null || !label.isValid()) {
                label = at.getWorld().spawn(at, TextDisplay.class, display -> {
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setSeeThrough(true);
                    display.setPersistent(false);
                    display.setVisibleByDefault(false);
                    display.addScoreboardTag(NpcService.TAG);
                    display.setBackgroundColor(org.bukkit.Color.fromARGB(150, 0, 0, 0));
                });
                labels.put(npc.id(), label);
                labelTexts.remove(npc.id());
            } else if (label.getLocation().distanceSquared(at) > 0.01) {
                label.teleport(at);
            }
            if (!text.equals(labelTexts.get(npc.id()))) {
                label.text(Component.text(text));
                labelTexts.put(npc.id(), text);
            }
            for (Player editor : editors) editor.showEntity(plugin, label);
        }
        for (String id : new ArrayList<>(labels.keySet())) {
            if (wanted.contains(id)) continue;
            labels.remove(id).remove();
            labelTexts.remove(id);
        }
    }

    private String labelText(Npc npc) {
        StringBuilder text = new StringBuilder("§6[NPC] §f").append(npc.id()).append(" §e").append(npc.name())
                .append("\n§7").append(npc.look().describe());
        List<String> uses = questlines.engine().usesOf(npc.id());
        if (uses.isEmpty()) text.append("\n§8(クエストで使われていない)");
        for (String use : uses) text.append("\n§b").append(use);
        return text.toString();
    }

    private void clearLabels() {
        for (TextDisplay label : labels.values()) label.remove();
        labels.clear();
        labelTexts.clear();
    }

    // ---- menus --------------------------------------------------------------------------------------------------------

    private abstract static class Menu implements MenuHolder {
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final class Picker extends Menu {
    }

    private static final class Setup extends Menu {
        private final String npc;

        private Setup(String npc) {
            this.npc = npc;
        }
    }

    private static final class QuestPicker extends Menu {
        private final String npc;
        private final List<String> lines = new ArrayList<>();

        private QuestPicker(String npc) {
            this.npc = npc;
        }
    }

    private static final class Lister extends Menu {
        private final List<String> ids = new ArrayList<>();
    }

    private static final class Debug extends Menu {
        private final String npc;
        private final List<String> lines = new ArrayList<>();

        private Debug(String npc) {
            this.npc = npc;
        }
    }

    private static final class LineMenu extends Menu {
        private final String npc;
        private final String line;
        private final List<String> steps = new ArrayList<>();
        /** The flags by slot: "line.flag" for the line's own, "flag" for a shared one. */
        private final Map<Integer, String> flags = new HashMap<>();

        private LineMenu(String npc, String line) {
            this.npc = npc;
            this.line = line;
        }
    }

    private Inventory create(Menu menu, int size, String title) {
        Inventory inventory = Bukkit.createInventory(menu, size, Component.text(title));
        menu.inventory = inventory;
        return inventory;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Object holder = event.getView().getTopInventory().getHolder();
        boolean ours = holder instanceof Menu;
        if (!ours && !editing(player)) return;
        event.setCancelled(true);
        if (!ours || event.getRawSlot() < 0 || event.getRawSlot() >= event.getView().getTopInventory().getSize()) return;
        Session session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.closeInventory();
            return;
        }
        int slot = event.getRawSlot();
        ClickType click = event.getClick();
        if (holder instanceof Picker) onPick(player, session, slot);
        else if (holder instanceof Setup setup) onSetup(player, setup, slot, click);
        else if (holder instanceof Lister lister) onList(player, lister, slot, click);
        else if (holder instanceof QuestPicker picker) onQuestPick(player, picker, slot);
        else if (holder instanceof Debug debug) onDebug(player, session, debug, slot);
        else if (holder instanceof LineMenu line) onLine(player, session, line, slot, click);
    }

    private static ItemStack icon(Material material, String name, NamedTextColor color, List<String> lore, boolean glow) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        List<Component> lines = new ArrayList<>();
        for (String line : lore) lines.add(Component.text(line, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lines);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (glow) meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
        item.setItemMeta(meta);
        return item;
    }

    private static Material egg(String entity) {
        Material material = Material.matchMaterial(entity + "_SPAWN_EGG");
        return material == null ? Material.NAME_TAG : material;
    }

    // the look

    private void openPicker(Player player) {
        Picker picker = new Picker();
        Inventory inventory = create(picker, 54, "NPC の見た目を選ぶ");
        for (int index = 0; index < PROFESSIONS.size(); index++) {
            inventory.setItem(index, icon(PROFESSION_ICONS.get(index), "村人: " + PROFESSIONS.get(index), NamedTextColor.GREEN,
                    List.of("村の種類 (服) は、設定の画面で変えられる"), false));
        }
        for (int index = 0; index < ENTITY_LOOKS.size() && 18 + index < 54; index++) {
            inventory.setItem(18 + index, icon(egg(ENTITY_LOOKS.get(index)), ENTITY_LOOKS.get(index), NamedTextColor.AQUA, List.of("動かない生き物として置く"), false));
        }
        inventory.setItem(53, icon(Material.ARMOR_STAND, "自作モデル", NamedTextColor.LIGHT_PURPLE,
                List.of("コマンドで: /npc look <id> model <モデルid> [大きさ]"), false));
        player.openInventory(inventory);
    }

    private void onPick(Player player, Session session, int slot) {
        Npc.Look look = null;
        if (slot < PROFESSIONS.size()) {
            look = Npc.Look.villager(PROFESSIONS.get(slot), "PLAINS");
        } else if (slot >= 18 && slot - 18 < ENTITY_LOOKS.size()) {
            look = Npc.Look.entity(ENTITY_LOOKS.get(slot - 18));
        }
        if (look == null) return;
        player.closeInventory();
        if (session.changing != null) {
            Npc npc = npcs.find(session.changing).orElse(null);
            session.changing = null;
            if (npc == null) return;
            if ("villager".equals(look.type()) && "villager".equals(npc.look().type())) look = Npc.Look.villager(look.profession(), npc.look().villagerType());
            npc.look(look);
            npcs.changed(npc);
            done(player, npc.id() + " の見た目を " + look.describe() + " にした。");
            return;
        }
        if (session.pending == null) return;
        Npc npc = npcs.add(session.pending, look);
        session.pending = null;
        done(player, npc.id() + " を置いた (" + look.describe() + ")。名前や id は、設定の道具で。");
    }

    // set up

    private void openSetup(Player player, Npc npc) {
        Setup setup = new Setup(npc.id());
        Inventory inventory = create(setup, 27, "NPC の設定: " + npc.id());
        inventory.setItem(10, icon(Material.NAME_TAG, "名前: " + npc.name(), NamedTextColor.YELLOW, List.of("クリック: 名前を入力"), false));
        inventory.setItem(11, icon(Material.ARMOR_STAND, "見た目: " + npc.look().describe(), NamedTextColor.AQUA, List.of("クリック: 見た目を選びなおす"), false));
        if ("villager".equals(npc.look().type())) {
            inventory.setItem(12, icon(Material.GRASS_BLOCK, "村の種類: " + npc.look().villagerType(), NamedTextColor.GREEN, List.of("左: 次 / 右: 前"), false));
        } else if ("entity".equals(npc.look().type())) {
            inventory.setItem(12, icon(Material.EGG, "子ども: " + (npc.look().baby() ? "はい" : "いいえ"), NamedTextColor.GREEN, List.of("クリック: 切り替え (子どものいる生き物だけ)"), false));
        }
        inventory.setItem(13, icon(Material.COMPASS, "向き: " + Math.round(npc.yaw()) + "°", NamedTextColor.WHITE,
                List.of("左: 左へ45° / 右: 右へ45°", "しゃがんでクリック: 自分の方を向かせる"), false));
        List<String> idle = new ArrayList<>();
        for (int index = 0; index < npc.idle().size(); index++) idle.add((index + 1) + ". " + npc.idle().get(index));
        if (idle.isEmpty()) idle.add("(なし)");
        idle.add("");
        idle.add("左: 1行足す / しゃがんでクリック: 全部消す");
        inventory.setItem(14, icon(Material.WRITABLE_BOOK, "アイドルの言葉 (クエストが無いとき)", NamedTextColor.WHITE, idle, false));
        inventory.setItem(15, icon(Material.PAPER, "id: " + npc.id(), NamedTextColor.GRAY,
                List.of("クエストの yml は、この id で NPC を指す", "クリック: id を変える (クエスト側も直すこと)"), false));
        List<String> quests = new ArrayList<>();
        for (Questline line : questlines.engine().lines().values()) {
            if (questlines.engine().offeredBy(line, npc.id())) quests.add("・" + line.name());
        }
        if (quests.isEmpty()) quests.add("(なし)");
        quests.add("");
        quests.add("クリック: 一覧から選ぶ");
        inventory.setItem(19, icon(Material.WRITABLE_BOOK, "この NPC が出すクエスト", NamedTextColor.GOLD, quests, false));
        inventory.setItem(16, icon(Material.SPYGLASS, "クエストのデバッグ", NamedTextColor.YELLOW, List.of("この NPC のクエストをフラグ単位で"), false));
        inventory.setItem(22, icon(Material.BARRIER, "この NPC を消す", NamedTextColor.RED, List.of("しゃがんでクリック"), false));
        player.openInventory(inventory);
    }

    private void onSetup(Player player, Setup setup, int slot, ClickType click) {
        Npc npc = npcs.find(setup.npc).orElse(null);
        Session session = sessions.get(player.getUniqueId());
        if (npc == null || session == null) {
            player.closeInventory();
            return;
        }
        switch (slot) {
            case 10 -> suggest(player, npc.id() + " の名前を入力:", "/npc name " + npc.id() + " ");
            case 11 -> {
                session.changing = npc.id();
                openPicker(player);
            }
            case 12 -> {
                if ("villager".equals(npc.look().type())) {
                    int at = Math.max(0, VILLAGER_TYPES.indexOf(npc.look().villagerType()));
                    at = Math.floorMod(at + (click.isRightClick() ? -1 : 1), VILLAGER_TYPES.size());
                    npc.look(Npc.Look.villager(npc.look().profession(), VILLAGER_TYPES.get(at)));
                } else if ("entity".equals(npc.look().type())) {
                    Npc.Look old = npc.look();
                    npc.look(new Npc.Look("entity", null, null, old.entity(), !old.baby(), null, 1.0));
                } else {
                    return;
                }
                npcs.changed(npc);
                openSetup(player, npc);
            }
            case 13 -> {
                if (click.isShiftClick()) {
                    Vector to = player.getLocation().toVector().subtract(new Vector(npc.x(), npc.y(), npc.z()));
                    npc.yaw((float) Math.toDegrees(Math.atan2(-to.getX(), to.getZ())));
                } else {
                    npc.yaw(npc.yaw() + (click.isRightClick() ? 45f : -45f));
                }
                npcs.changed(npc);
                openSetup(player, npc);
            }
            case 14 -> {
                if (click.isShiftClick()) {
                    npc.idle().clear();
                    npcs.save();
                    openSetup(player, npc);
                } else {
                    suggest(player, npc.id() + " のアイドルの言葉を1行入力:", "/npc idle " + npc.id() + " add ");
                }
            }
            case 15 -> suggest(player, npc.id() + " の新しい id (英小文字・数字・_) を入力:", "/npc rename " + npc.id() + " ");
            case 16 -> openDebug(player, npc);
            case 19 -> openQuestPicker(player, npc);
            case 22 -> {
                if (!click.isShiftClick()) return;
                player.closeInventory();
                npcs.remove(npc);
                done(player, npc.id() + " を消した。");
            }
            default -> {
            }
        }
    }

    // the quests an NPC offers (chosen from the list of every quest line; saved in npcs.yml quests:)

    private void openQuestPicker(Player player, Npc npc) {
        QuestPicker picker = new QuestPicker(npc.id());
        Inventory inventory = create(picker, 54, "クエストを選ぶ: " + npc.id());
        List<Questline> all = new ArrayList<>(questlines.engine().lines().values());
        all.sort(Comparator.comparing((Questline line) -> !line.main()).thenComparing(Questline::id));
        for (int index = 0; index < all.size() && index < 45; index++) {
            Questline line = all.get(index);
            picker.lines.add(line.id());
            boolean fixed = npc.id().equals(line.start().npc());
            boolean chosen = npc.quests().contains(line.id());
            List<String> lore = new ArrayList<>();
            lore.add(line.id() + " / " + (line.main() ? "メイン" : "サブ") + (line.exclusive() ? " / ほかと並行しない" : ""));
            if (line.start().npc() != null && !fixed) lore.add("yml の受注先: " + line.start().npc());
            lore.add(fixed ? "yml でこの NPC が受注先 (ここでは外せない)" : chosen ? "この NPC が出す — クリックで外す" : "クリック: この NPC が出すようにする");
            inventory.setItem(index, icon(line.main() ? Material.WRITABLE_BOOK : Material.BOOK, line.name(),
                    fixed || chosen ? NamedTextColor.GREEN : NamedTextColor.WHITE, lore, fixed || chosen));
        }
        inventory.setItem(49, icon(Material.ARROW, "設定に戻る", NamedTextColor.WHITE, List.of(), false));
        player.openInventory(inventory);
    }

    private void onQuestPick(Player player, QuestPicker picker, int slot) {
        Npc npc = npcs.find(picker.npc).orElse(null);
        if (npc == null) return;
        if (slot == 49) {
            openSetup(player, npc);
            return;
        }
        if (slot < 0 || slot >= picker.lines.size()) return;
        String line = picker.lines.get(slot);
        Questline definition = questlines.engine().line(line);
        if (definition == null || npc.id().equals(definition.start().npc())) return;
        if (!npc.quests().remove(line)) npc.quests().add(line);
        npcs.save();
        refresh();
        openQuestPicker(player, npc);
    }

    // the list

    private void openList(Player player) {
        Lister lister = new Lister();
        Inventory inventory = create(lister, 54, "NPC の一覧");
        List<Npc> sorted = new ArrayList<>(npcs.all());
        Location here = player.getLocation();
        sorted.sort(Comparator.comparingDouble(npc -> npc.worldName().equals(here.getWorld().getName())
                ? here.distanceSquared(new Location(here.getWorld(), npc.x(), npc.y(), npc.z())) : Double.MAX_VALUE));
        for (int index = 0; index < sorted.size() && index < 54; index++) {
            Npc npc = sorted.get(index);
            lister.ids.add(npc.id());
            List<String> lore = new ArrayList<>();
            lore.add(npc.look().describe());
            lore.add(npc.worldName() + " " + Math.round(npc.x()) + ", " + Math.round(npc.y()) + ", " + Math.round(npc.z()));
            lore.addAll(questlines.engine().usesOf(npc.id()));
            lore.add("左: テレポート / 右: 設定");
            Material material = "entity".equals(npc.look().type()) ? egg(npc.look().entity()) : Material.VILLAGER_SPAWN_EGG;
            inventory.setItem(index, icon(material, npc.id() + " " + npc.name(), NamedTextColor.YELLOW, lore, false));
        }
        player.openInventory(inventory);
    }

    private void onList(Player player, Lister lister, int slot, ClickType click) {
        if (slot >= lister.ids.size()) return;
        Npc npc = npcs.find(lister.ids.get(slot)).orElse(null);
        if (npc == null) return;
        if (click.isRightClick()) {
            openSetup(player, npc);
            return;
        }
        Location at = npc.location();
        if (at == null) return;
        player.closeInventory();
        player.teleport(at.clone().add(at.getDirection().multiply(2.0)).setDirection(at.getDirection().multiply(-1)));
    }

    // debugging the quests of an NPC

    private Player debugTarget(Player editor, Session session) {
        Player target = session.debugTarget == null ? null : Bukkit.getPlayer(session.debugTarget);
        return target == null ? editor : target;
    }

    private void openDebug(Player player, Npc npc) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        Player target = debugTarget(player, session);
        Debug debug = new Debug(npc.id());
        Inventory inventory = create(debug, 54, "クエストのデバッグ: " + npc.id());
        ItemStack head = icon(Material.PLAYER_HEAD, "対象: " + target.getName(), NamedTextColor.GOLD, List.of("クリック: 近くのプレイヤーへ切り替え"), false);
        if (head.getItemMeta() instanceof SkullMeta skull) {
            skull.setOwningPlayer(target);
            head.setItemMeta(skull);
        }
        inventory.setItem(4, head);
        QuestlineEngine engine = questlines.engine();
        QuestlineData data = questlines.dataOf(target);
        Set<String> lineIds = new LinkedHashSet<>();
        for (String use : engine.usesOf(npc.id())) lineIds.add(use.split(" ")[0]);
        int slot = 9;
        for (String id : lineIds) {
            if (slot >= 45) break;
            Questline line = engine.line(id);
            if (line == null) continue;
            debug.lines.add(id);
            inventory.setItem(slot++, lineIcon(target, engine, data, line));
        }
        if (lineIds.isEmpty()) inventory.setItem(22, icon(Material.GRAY_DYE, "この NPC を使うクエストはありません", NamedTextColor.GRAY, List.of(), false));
        player.openInventory(inventory);
    }

    private ItemStack lineIcon(Player target, QuestlineEngine engine, QuestlineData data, Questline line) {
        LineState state = data.line(line.id());
        List<String> lore = new ArrayList<>();
        lore.add("id: " + line.id());
        if (state == null) {
            lore.add("状態: 未開始");
        } else {
            lore.add("状態: " + state.status.key() + " / ステップ: " + state.step);
            String progress = questlines.progressText(target, line.id());
            if (progress != null) lore.add("進み具合: " + progress);
            for (var flag : state.flags.entrySet()) lore.add("  " + flag.getKey() + " = " + flag.getValue());
        }
        lore.add("クリック: 詳しく (ステップ・フラグを変える)");
        Material material = state == null ? Material.BOOK : switch (state.status) {
            case ACTIVE -> Material.WRITABLE_BOOK;
            case PAUSED -> Material.CLOCK;
            case COMPLETED -> Material.ENCHANTED_BOOK;
            case FAILED -> Material.RED_DYE;
        };
        return icon(material, line.name(), NamedTextColor.AQUA, lore, false);
    }

    private void onDebug(Player player, Session session, Debug debug, int slot) {
        Npc npc = npcs.find(debug.npc).orElse(null);
        if (npc == null) return;
        if (slot == 4) {
            List<Player> near = new ArrayList<>();
            near.add(player);
            for (Player other : player.getWorld().getPlayers()) {
                if (other != player && other.getLocation().distanceSquared(player.getLocation()) < 64 * 64) near.add(other);
            }
            Player current = debugTarget(player, session);
            Player next = near.get((near.indexOf(current) + 1) % near.size());
            session.debugTarget = next.getUniqueId();
            openDebug(player, npc);
            return;
        }
        int index = slot - 9;
        if (index >= 0 && index < debug.lines.size()) openLine(player, npc.id(), debug.lines.get(index));
    }

    private void openLine(Player player, String npcId, String lineId) {
        Session session = sessions.get(player.getUniqueId());
        Questline line = questlines.engine().line(lineId);
        if (session == null || line == null) return;
        Player target = debugTarget(player, session);
        QuestlineData data = questlines.dataOf(target);
        LineState state = data.line(lineId);
        LineMenu menu = new LineMenu(npcId, lineId);
        Inventory inventory = create(menu, 54, lineId + " (" + target.getName() + ")");
        inventory.setItem(4, lineIcon(target, questlines.engine(), data, line));
        int slot = 9;
        for (Questline.Step step : line.steps().values()) {
            if (slot >= 27) break;
            boolean current = state != null && step.id().equals(state.step);
            menu.steps.add(step.id());
            List<String> lore = new ArrayList<>();
            if (step.name() != null) lore.add(step.name());
            if (step.end() != null) lore.add("終わり: " + step.end().name().toLowerCase(Locale.ROOT));
            lore.add(current ? "今のステップ" : "クリック: このステップに移す (入るときの動作も行う)");
            inventory.setItem(slot++, icon(current ? Material.LIME_DYE : step.end() != null ? Material.GOLD_NUGGET : Material.GRAY_DYE,
                    "ステップ: " + step.id(), current ? NamedTextColor.GREEN : NamedTextColor.WHITE, lore, current));
        }
        slot = 27;
        List<String> keys = new ArrayList<>();
        for (String flag : line.flags().keySet()) keys.add(lineId + "." + flag);
        keys.addAll(questlines.engine().book().flags().keySet());
        for (String key : keys) {
            if (slot >= 45) break;
            boolean own = key.startsWith(lineId + ".");
            String flag = own ? key.substring(lineId.length() + 1) : key;
            Questline.FlagSpec spec = own ? line.flags().get(flag) : questlines.engine().book().flags().get(flag);
            Object value = own ? (state == null ? null : state.flags.get(flag)) : data.flags().get(flag);
            boolean number = spec != null && spec.number();
            menu.flags.put(slot, key);
            inventory.setItem(slot++, icon(number ? Material.REPEATER : Material.LEVER, (own ? "" : "共通: ") + flag + " = " + (value == null ? "(未設定)" : value),
                    own ? NamedTextColor.YELLOW : NamedTextColor.GOLD,
                    number ? List.of("左: +1 / 右: -1 / しゃがんでクリック: 消す") : List.of("左: 切り替え / しゃがんでクリック: 消す"), false));
        }
        inventory.setItem(45, icon(Material.ARROW, "戻る", NamedTextColor.WHITE, List.of(), false));
        inventory.setItem(47, icon(Material.EMERALD, "開始する (条件を無視)", NamedTextColor.GREEN, List.of("進行中なら何もしない"), false));
        boolean paused = state != null && state.status == QuestlineData.Status.PAUSED;
        inventory.setItem(48, icon(paused ? Material.LIME_DYE : Material.CLOCK, paused ? "再開する" : "中断する", NamedTextColor.AQUA, List.of(), false));
        boolean confirming = (lineId).equals(session.confirmReset);
        inventory.setItem(50, icon(Material.TNT, confirming ? "もう一度押すとリセット" : "リセット", NamedTextColor.RED,
                List.of("この線と、これに依存する線の状態とフラグを消す", "報酬は取り戻さない"), confirming));
        player.openInventory(inventory);
    }

    private void onLine(Player player, Session session, LineMenu menu, int slot, ClickType click) {
        Player target = debugTarget(player, session);
        if (slot == 45) {
            npcs.find(menu.npc).ifPresent(npc -> openDebug(player, npc));
            return;
        }
        if (slot >= 9 && slot < 9 + menu.steps.size()) {
            questlines.setStep(target, menu.line, menu.steps.get(slot - 9));
        } else if (menu.flags.containsKey(slot)) {
            String key = menu.flags.get(slot);
            boolean own = key.startsWith(menu.line + ".");
            String flag = own ? key.substring(menu.line.length() + 1) : key;
            String lineId = own ? menu.line : null;
            Questline line = questlines.engine().line(menu.line);
            Questline.FlagSpec spec = own ? line.flags().get(flag) : questlines.engine().book().flags().get(flag);
            Object value = questlines.engine().read(questlines.dataOf(target), lineId, flag);
            Object next;
            if (click.isShiftClick()) {
                next = null;
            } else if (spec != null && spec.number()) {
                next = Questline.number(value, 0) + (click.isRightClick() ? -1 : 1);
            } else {
                next = !(value instanceof Boolean bool && bool);
            }
            if (!questlines.writeFlag(target, lineId, flag, next)) {
                hint(player, "このフラグは、クエストを始めてからでないと書けません。");
            }
        } else if (slot == 47) {
            questlines.start(target, menu.line, true);
        } else if (slot == 48) {
            if (!questlines.pause(target, menu.line)) questlines.resume(target, menu.line);
        } else if (slot == 50) {
            if (!menu.line.equals(session.confirmReset)) {
                session.confirmReset = menu.line;
            } else {
                session.confirmReset = null;
                Set<String> ids = new LinkedHashSet<>();
                ids.add(menu.line);
                ids.addAll(questlines.engine().dependents(menu.line));
                questlines.reset(target, ids, false);
                done(player, target.getName() + " の " + String.join(", ", ids) + " をリセットした。");
            }
        } else {
            return;
        }
        openLine(player, menu.npc, menu.line);
    }

    /** For /npc idle and the names typed in the chat: refreshes the label at once. */
    public void refresh() {
        labelTexts.clear();
        syncLabels();
    }
}
