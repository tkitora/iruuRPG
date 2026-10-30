package net.tkgon.mc.iruuRPG.spawner;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import net.tkgon.mc.iruuRPG.mob.RpgMobDefinition;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Editor mode for the field spawners (/spawner edit): the hotbar becomes a fixed set of tools, every spawner near the
 * player is shown (a label, a column of particles and a ring as wide as its trigger radius), and spawners are placed,
 * set up and deleted by clicking. The real hotbar is kept (also in a file) and given back when leaving the editor.
 *
 * Tools (slots 1-4, 8 = done): place a spawner (click a block, pick the mob; sneak = the last mob again), set up a
 * spawner (look at it and click: the mob, how many appear, how many live at once, the trigger radius), delete one,
 * and the list of every spawner (left click = teleport there, right click = set it up, sneak + right click = delete; a filter
 * shows only the ones inside a block, which can be deleted one by one or all at once).
 */
public final class FieldSpawnerEditor implements Listener {

    private enum Tool {
        PLACE(Material.SPAWNER, "スポナーを置く", NamedTextColor.GREEN,
                "ブロックをクリック → 一覧からモブを選んで、その上に置く", "しゃがんでクリック: 前と同じモブをもう一度"),
        SETUP(Material.COMPARATOR, "スポナーを設定する", NamedTextColor.AQUA,
                "スポナーの方を向いてクリック → 設定の画面", "モブ / 湧く数 / 同時に生きられる数 / 反応する距離"),
        DELETE(Material.BARRIER, "スポナーを消す", NamedTextColor.DARK_RED,
                "スポナーの方を向いてクリック → 消す"),
        LIST(Material.BOOK, "スポナーの一覧", NamedTextColor.GOLD,
                "クリック → 全部のスポナーの一覧", "左クリック: そこへテレポート / 右クリック: 設定 / しゃがんで右クリック: 消す"),
        DONE(Material.LIME_DYE, "エディターを終える", NamedTextColor.WHITE,
                "クリック: 元のホットバーに戻る (変更はその場で保存済み)");

        private final Material material;
        private final String name;
        private final NamedTextColor color;
        private final String[] lore;

        Tool(Material material, String name, NamedTextColor color, String... lore) {
            this.material = material;
            this.name = name;
            this.color = color;
            this.lore = lore;
        }
    }

    private static final int TOOL_SLOTS = 8;
    private static final Tool[] HOTBAR = {Tool.PLACE, Tool.SETUP, Tool.DELETE, Tool.LIST, null, null, null, Tool.DONE};
    private static final double SHOW_DISTANCE = 48.0;
    private static final double AIM_DISTANCE = 40.0;
    private static final double AIM_TOLERANCE = 1.3;

    private static final class Session {
        private final ItemStack[] savedHotbar;
        private String lastMob;
        private Location pending;
        /** The spawner whose mob the list is choosing (null: a new spawner goes to {@code pending}). */
        private String changing;

        private Session(ItemStack[] savedHotbar) {
            this.savedHotbar = savedHotbar;
        }
    }

    private final JavaPlugin plugin;
    private final FieldSpawnerService service;
    private final RpgMobRegistry mobRegistry;
    private final NamespacedKey toolKey;
    private final File saveFolder;
    private final Map<UUID, Session> sessions = new HashMap<>();
    /** The label over each spawner shown now (spawner id -> label) and its text, to change it only when it changed. */
    private final Map<String, TextDisplay> labels = new HashMap<>();
    private final Map<String, String> labelTexts = new HashMap<>();
    private BukkitTask task;
    private int ticks;
    private Predicate<Player> otherEditor = player -> false;

    public FieldSpawnerEditor(JavaPlugin plugin, FieldSpawnerService service, RpgMobRegistry mobRegistry) {
        this.plugin = plugin;
        this.service = service;
        this.mobRegistry = mobRegistry;
        this.toolKey = new NamespacedKey(plugin, "spawner_tool");
        this.saveFolder = new File(plugin.getDataFolder(), "editor");
    }

    /** True while the player is in another editor (the dungeon editor): two editors cannot share the hotbar. */
    public void setOtherEditor(Predicate<Player> otherEditor) {
        this.otherEditor = otherEditor;
    }

    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10L, 10L);
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

    // ---- switching -------------------------------------------------------------------------------------------------

    public void toggle(Player player) {
        if (editing(player)) {
            stop(player, true);
            return;
        }
        if (otherEditor.test(player)) {
            player.sendMessage(Component.text("[スポナー] ダンジョンのエディターを終えてから使ってください。", NamedTextColor.RED));
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
        player.sendMessage(Component.text("[スポナー] エディターモード。ホットバーの道具で、スポナーを置く・設定する・消す。"
                + "終えるときは 8番の道具、または /spawner edit", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        syncLabels();
    }

    public void stop(Player player, boolean announce) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        restore(player, session.savedHotbar);
        deleteBackup(player.getUniqueId());
        if (announce) player.sendMessage(Component.text("[スポナー] エディターを終えた。", NamedTextColor.GRAY));
        if (sessions.isEmpty()) clearLabels();
    }

    private void restore(Player player, ItemStack[] hotbar) {
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            player.getInventory().setItem(slot, hotbar[slot] == null ? null : hotbar[slot].clone());
        }
    }

    // ---- the hotbar is kept in a file while editing (the server may stop) ----------------------------------------------

    private File backupFile(UUID id) {
        return new File(saveFolder, "spawner-" + id + ".yml");
    }

    private void writeBackup(UUID id, ItemStack[] hotbar) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int slot = 0; slot < hotbar.length; slot++) yaml.set("hotbar." + slot, hotbar[slot]);
        try {
            if (!saveFolder.exists() && !saveFolder.mkdirs()) return;
            yaml.save(backupFile(id));
        } catch (IOException e) {
            plugin.getLogger().warning("spawner editor: could not save the hotbar of " + id + ": " + e.getMessage());
        }
    }

    private void deleteBackup(UUID id) {
        File file = backupFile(id);
        if (file.exists() && !file.delete()) plugin.getLogger().warning("spawner editor: could not delete " + file.getName());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // the server stopped while this player was editing: give the hotbar back
        Player player = event.getPlayer();
        File file = backupFile(player.getUniqueId());
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ItemStack[] hotbar = new ItemStack[TOOL_SLOTS];
        for (int slot = 0; slot < TOOL_SLOTS; slot++) hotbar[slot] = yaml.getItemStack("hotbar." + slot);
        restore(player, hotbar);
        deleteBackup(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        stop(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        if (!editing(event.getEntity())) return;
        event.getDrops().removeIf(item -> toolOf(item) != null);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!editing(player)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) stop(player, true);
        });
    }

    // ---- the hotbar is fixed while editing -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !editing(player)) return;
        event.setCancelled(true);
        if (event.getView().getTopInventory().getHolder() instanceof Picker picker) onPick(player, picker, event.getRawSlot());
        if (event.getView().getTopInventory().getHolder() instanceof Lister lister) onList(player, lister, event.getRawSlot(), event.getClick());
        if (event.getView().getTopInventory().getHolder() instanceof Setup setup) onSetup(player, setup, event.getRawSlot(), event.getClick().isRightClick(), event.getClick().isShiftClick());
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

    // ---- using the tools ----------------------------------------------------------------------------------------------

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

        switch (tool) {
            case DONE -> stop(player, true);
            case PLACE -> {
                Block clicked = event.getClickedBlock();
                if (clicked == null) {
                    hint(player, "ブロックをクリックしてください。");
                    return;
                }
                session.pending = clicked.getRelative(event.getBlockFace()).getLocation().add(0.5, 0.0, 0.5);
                session.changing = null;
                if (player.isSneaking() && session.lastMob != null) {
                    place(player, session, session.lastMob);
                } else {
                    openPicker(player);
                }
            }
            case SETUP -> {
                FieldSpawner target = aimed(player);
                if (target == null) {
                    hint(player, "スポナーの方を向いてクリックしてください。");
                    return;
                }
                openSetup(player, target);
            }
            case LIST -> openList(player);
            case DELETE -> {
                FieldSpawner target = aimed(player);
                if (target == null) {
                    hint(player, "スポナーの方を向いてクリックしてください。");
                    return;
                }
                service.remove(target);
                syncLabels();
                done(player, target.id() + " (" + target.mobId() + ") を消した。");
            }
        }
    }

    /** The spawner the player is looking at (the nearest one along the line of sight; walls do not block it). */
    private FieldSpawner aimed(Player player) {
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection();
        FieldSpawner best = null;
        double bestAlong = Double.MAX_VALUE;
        for (FieldSpawner spawner : service.all()) {
            if (!spawner.worldName().equals(player.getWorld().getName())) continue;
            Vector to = new Vector(spawner.x(), spawner.y() + 0.9, spawner.z()).subtract(eye.toVector());
            double along = to.dot(direction);
            if (along < 0 || along > AIM_DISTANCE) continue;
            double off = to.clone().subtract(direction.clone().multiply(along)).length();
            if (off <= AIM_TOLERANCE && along < bestAlong) {
                bestAlong = along;
                best = spawner;
            }
        }
        return best;
    }

    private void place(Player player, Session session, String mobId) {
        if (session.pending == null) return;
        if (session.changing != null) {
            FieldSpawner spawner = service.find(session.changing).orElse(null);
            session.changing = null;
            if (spawner == null) return;
            spawner.mobId(mobId);
            service.reset(spawner);
            service.save();
            session.lastMob = mobId;
            syncLabels();
            done(player, spawner.id() + " のモブを " + mobId + " にした。");
            return;
        }
        FieldSpawner spawner = service.add(session.pending, mobId);
        session.lastMob = mobId;
        syncLabels();
        done(player, spawner.id() + " " + mobId + " を置いた。しゃがんでクリックで同じモブをもう一度");
    }

    private void done(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    private void hint(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.GRAY));
    }

    // ---- showing the spawners ---------------------------------------------------------------------------------------------

    private void tick() {
        if (sessions.isEmpty()) return;
        ticks++;
        if (ticks % 2 == 0) syncLabels();
        for (UUID id : sessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            for (FieldSpawner spawner : service.all()) {
                if (!spawner.worldName().equals(player.getWorld().getName())) continue;
                Location at = new Location(player.getWorld(), spawner.x(), spawner.y(), spawner.z());
                if (at.distanceSquared(player.getLocation()) > SHOW_DISTANCE * SHOW_DISTANCE) continue;
                player.spawnParticle(Particle.VILLAGER_HAPPY, at.clone().add(0, 0.8, 0), 5, 0.2, 0.6, 0.2, 0.0);
                if (ticks % 2 == 0) ring(player, at, spawner.radius());
            }
        }
    }

    /** The trigger radius as a ring on the ground around the spawner (dots at the spawner's height). */
    private void ring(Player player, Location center, int radius) {
        int dots = Math.max(24, radius * 3);
        for (int index = 0; index < dots; index++) {
            double angle = Math.PI * 2.0 * index / dots;
            Location at = center.clone().add(Math.cos(angle) * radius, 0.3, Math.sin(angle) * radius);
            if (at.distanceSquared(player.getLocation()) > 40.0 * 40.0) continue;
            player.spawnParticle(Particle.END_ROD, at, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    private String labelText(FieldSpawner spawner) {
        String name = mobRegistry.find(spawner.mobId()).map(RpgMobDefinition::name).orElse("? 不明なモブ");
        return spawner.id() + " " + name + "\n" + spawner.mobId() + "\n" + spawner.min() + "~" + spawner.max() + "体 / 同時" + spawner.maxAlive()
                + " / 半径" + spawner.radius();
    }

    /** Labels over the spawners near an editor; only editors see them. */
    private void syncLabels() {
        List<Player> editors = new ArrayList<>();
        for (UUID id : sessions.keySet()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) editors.add(player);
        }
        if (editors.isEmpty()) {
            clearLabels();
            return;
        }
        java.util.Set<String> wanted = new java.util.HashSet<>();
        for (FieldSpawner spawner : service.all()) {
            World world = Bukkit.getWorld(spawner.worldName());
            if (world == null) continue;
            Location at = spawner.in(world);
            boolean near = false;
            for (Player editor : editors) {
                if (editor.getWorld().equals(world) && editor.getLocation().distanceSquared(at) <= SHOW_DISTANCE * SHOW_DISTANCE) near = true;
            }
            if (!near || !world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)) continue;
            wanted.add(spawner.id());
            String text = labelText(spawner);
            TextDisplay label = labels.get(spawner.id());
            if (label == null || !label.isValid()) {
                label = world.spawn(at.clone().add(0, 2.0, 0), TextDisplay.class, display -> {
                    display.setBillboard(Display.Billboard.CENTER);
                    display.setPersistent(false);
                    display.setSeeThrough(true);
                    display.setVisibleByDefault(false);
                });
                labels.put(spawner.id(), label);
                labelTexts.remove(spawner.id());
            }
            if (!text.equals(labelTexts.get(spawner.id()))) {
                label.text(Component.text(text, NamedTextColor.YELLOW, TextDecoration.BOLD));
                labelTexts.put(spawner.id(), text);
            }
            for (Player editor : editors) editor.showEntity(plugin, label);
        }
        for (String id : new ArrayList<>(labels.keySet())) {
            if (wanted.contains(id)) continue;
            Entity label = labels.remove(id);
            labelTexts.remove(id);
            if (label != null && label.isValid()) label.remove();
        }
    }

    private void clearLabels() {
        for (TextDisplay label : labels.values()) {
            if (label.isValid()) label.remove();
        }
        labels.clear();
        labelTexts.clear();
    }

    // ---- the settings of one spawner ------------------------------------------------------------------------------------

    private static final class Setup implements MenuHolder {
        private String spawnerId;
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final int SLOT_MOB = 4;
    private static final int SLOT_MIN = 11;
    private static final int SLOT_MAX = 15;
    private static final int SLOT_ALIVE = 20;
    private static final int SLOT_RADIUS = 24;
    private static final int SLOT_DELETE = 31;
    private static final int SLOT_CLOSE = 35;

    private void openSetup(Player player, FieldSpawner spawner) {
        Setup holder = new Setup();
        holder.spawnerId = spawner.id();
        holder.inventory = Bukkit.createInventory(holder, 36, Component.text("スポナー " + spawner.id() + " の設定"));
        renderSetup(holder, spawner);
        player.openInventory(holder.inventory);
    }

    private void renderSetup(Setup holder, FieldSpawner spawner) {
        Inventory inventory = holder.inventory;
        inventory.clear();
        RpgMobDefinition mob = mobRegistry.find(spawner.mobId()).orElse(null);
        Material egg = mob == null ? null : Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
        inventory.setItem(SLOT_MOB, button(egg == null ? Material.PAPER : egg, "モブ: " + (mob == null ? spawner.mobId() + " (不明)" : mob.name()),
                NamedTextColor.GREEN, List.of(text("id: " + spawner.mobId(), NamedTextColor.GRAY), text("クリックで、別のモブにする", NamedTextColor.YELLOW))));
        valueRow(inventory, SLOT_MIN, Material.RABBIT_FOOT, "一度に湧く数 (最小)", spawner.min(), "体", "左クリック +1 / 右クリック -1");
        valueRow(inventory, SLOT_MAX, Material.RABBIT_HIDE, "一度に湧く数 (最大)", spawner.max(), "体", "左クリック +1 / 右クリック -1");
        valueRow(inventory, SLOT_ALIVE, Material.SKELETON_SKULL, "同時に生きられる数", spawner.maxAlive(), "体", "左クリック +1 / 右クリック -1");
        valueRow(inventory, SLOT_RADIUS, Material.ENDER_EYE, "反応する距離", spawner.radius(), "ブロック", "左クリック +4 / 右クリック -4 / Shift で ±16 (" + FieldSpawner.MIN_RADIUS + "〜" + FieldSpawner.MAX_RADIUS + ")");
        inventory.setItem(SLOT_DELETE, button(Material.BARRIER, "このスポナーを消す", NamedTextColor.RED, List.of()));
        inventory.setItem(SLOT_CLOSE, button(Material.ARROW, "閉じる", NamedTextColor.GRAY, List.of()));
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < 36; slot++) {
            if (inventory.getItem(slot) == null) inventory.setItem(slot, frame);
        }
    }

    private void valueRow(Inventory inventory, int slot, Material icon, String name, int value, String unit, String how) {
        inventory.setItem(slot, button(icon, name + ": " + value + unit, NamedTextColor.AQUA, List.of(text(how, NamedTextColor.GRAY))));
    }

    private void onSetup(Player player, Setup holder, int slot, boolean rightClick, boolean shift) {
        FieldSpawner spawner = service.find(holder.spawnerId).orElse(null);
        Session session = sessions.get(player.getUniqueId());
        if (spawner == null || session == null) {
            player.closeInventory();
            return;
        }
        int step = rightClick ? -1 : 1;
        switch (slot) {
            case SLOT_MOB -> {
                session.changing = spawner.id();
                session.pending = spawner.in(player.getWorld());
                player.closeInventory();
                openPicker(player);
                return;
            }
            case SLOT_MIN -> spawner.min(spawner.min() + step);
            case SLOT_MAX -> spawner.max(spawner.max() + step);
            case SLOT_ALIVE -> spawner.maxAlive(spawner.maxAlive() + step);
            case SLOT_RADIUS -> spawner.radius(spawner.radius() + step * (shift ? 16 : 4));
            case SLOT_DELETE -> {
                player.closeInventory();
                service.remove(spawner);
                syncLabels();
                done(player, spawner.id() + " (" + spawner.mobId() + ") を消した。");
                return;
            }
            case SLOT_CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> {
                return;
            }
        }
        service.save();
        syncLabels();
        renderSetup(holder, spawner);
    }

    // ---- the list of every spawner --------------------------------------------------------------------------------------

    private static final class Lister implements MenuHolder {
        /** slot -> spawner id */
        private final Map<Integer, String> spawners = new HashMap<>();
        private int page;
        /** Only the spawners that sit inside a block. */
        private boolean buriedOnly;
        /** The bulk delete button was pressed once; the next press deletes. */
        private boolean confirmBulk;
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private static final int LIST_PER_PAGE = 45;
    private static final int BULK = 53;

    /** True when the spawner's point is inside a block (a mob cannot stand there); a place that is not loaded is never counted. */
    private boolean buried(FieldSpawner spawner) {
        World world = Bukkit.getWorld(spawner.worldName());
        if (world == null) return false;
        int x = (int) Math.floor(spawner.x());
        int y = (int) Math.floor(spawner.y());
        int z = (int) Math.floor(spawner.z());
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return false;
        return !world.getBlockAt(x, y, z).isPassable();
    }

    private List<FieldSpawner> buriedOf(List<FieldSpawner> spawners) {
        List<FieldSpawner> buried = new ArrayList<>();
        for (FieldSpawner spawner : spawners) {
            if (buried(spawner)) buried.add(spawner);
        }
        return buried;
    }

    private void openList(Player player) {
        Lister lister = new Lister();
        lister.inventory = Bukkit.createInventory(lister, 54, Component.text("スポナーの一覧"));
        renderList(player, lister);
        player.openInventory(lister.inventory);
    }

    /** Spawners of the player's world first (the nearest first), then the others. */
    private List<FieldSpawner> listed(Player player) {
        List<FieldSpawner> all = new ArrayList<>(service.all());
        String here = player.getWorld().getName();
        Location at = player.getLocation();
        all.sort((left, right) -> {
            boolean leftHere = left.worldName().equals(here);
            boolean rightHere = right.worldName().equals(here);
            if (leftHere != rightHere) return leftHere ? -1 : 1;
            if (leftHere) return Double.compare(distanceSquared(left, at), distanceSquared(right, at));
            int byWorld = left.worldName().compareTo(right.worldName());
            return byWorld != 0 ? byWorld : left.id().compareTo(right.id());
        });
        return all;
    }

    private double distanceSquared(FieldSpawner spawner, Location at) {
        double dx = spawner.x() - at.getX();
        double dy = spawner.y() - at.getY();
        double dz = spawner.z() - at.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private void renderList(Player player, Lister lister) {
        Inventory inventory = lister.inventory;
        inventory.clear();
        lister.spawners.clear();
        List<FieldSpawner> everything = listed(player);
        List<FieldSpawner> buried = buriedOf(everything);
        List<FieldSpawner> all = lister.buriedOnly ? buried : everything;
        int pages = Math.max(1, (all.size() + LIST_PER_PAGE - 1) / LIST_PER_PAGE);
        lister.page = Math.max(0, Math.min(lister.page, pages - 1));
        String here = player.getWorld().getName();
        for (int index = 0; index < LIST_PER_PAGE; index++) {
            int at = lister.page * LIST_PER_PAGE + index;
            if (at >= all.size()) break;
            FieldSpawner spawner = all.get(at);
            RpgMobDefinition mob = mobRegistry.find(spawner.mobId()).orElse(null);
            Material egg = mob == null ? null : Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
            boolean sameWorld = spawner.worldName().equals(here);
            List<Component> lore = new ArrayList<>();
            lore.add(text("id: " + spawner.mobId() + (mob == null ? " (不明なモブ)" : " / Lv " + mob.level()), NamedTextColor.GRAY));
            lore.add(text(spawner.worldName() + " (" + Math.round(spawner.x()) + ", " + Math.round(spawner.y()) + ", " + Math.round(spawner.z()) + ")", NamedTextColor.WHITE));
            if (sameWorld) lore.add(text("ここから " + Math.round(Math.sqrt(distanceSquared(spawner, player.getLocation()))) + " ブロック", NamedTextColor.AQUA));
            lore.add(text(spawner.min() + "~" + spawner.max() + "体 / 同時 " + spawner.maxAlive() + " / 反応する距離 " + spawner.radius()
                    + " / 今 " + spawner.aliveCount() + " 体", NamedTextColor.GRAY));
            lore.add(text("左クリック: テレポート / 右クリック: 設定", NamedTextColor.YELLOW));
            lore.add(text("しゃがんで右クリック: 消す", NamedTextColor.RED));
            inventory.setItem(index, button(egg == null ? Material.PAPER : egg, spawner.id() + " " + (mob == null ? spawner.mobId() : mob.name()),
                    sameWorld ? NamedTextColor.GREEN : NamedTextColor.GRAY, lore));
            lister.spawners.put(index, spawner.id());
        }
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, frame);
        inventory.setItem(CANCEL, button(Material.ARROW, "閉じる", NamedTextColor.GRAY, List.of()));
        if (lister.page > 0) inventory.setItem(PREV, button(Material.SPECTRAL_ARROW, "前のページ", NamedTextColor.YELLOW, List.of()));
        if (lister.page < pages - 1) inventory.setItem(NEXT, button(Material.SPECTRAL_ARROW, "次のページ", NamedTextColor.YELLOW,
                List.of(text("ページ " + (lister.page + 1) + " / " + pages, NamedTextColor.GRAY))));
        inventory.setItem(FILTER, button(lister.buriedOnly ? Material.STONE : Material.BOOK,
                lister.buriedOnly ? "絞り込み中: ブロックに埋まっている " + all.size() + " か所" : "スポナー " + all.size() + " か所 (埋まっている " + buried.size() + " か所)",
                NamedTextColor.GOLD, List.of(
                        text(lister.buriedOnly ? "クリック: 全部の表示にもどす" : "クリック: ブロックに埋まっているものだけ表示", NamedTextColor.YELLOW),
                        text("まだ読み込まれていない場所は調べられません", NamedTextColor.GRAY))));
        if (lister.buriedOnly && !all.isEmpty()) {
            inventory.setItem(BULK, button(Material.TNT, lister.confirmBulk ? "もう一度押すと、いま絞り込んでいる " + all.size() + " か所を全部消す" : "絞り込んだ " + all.size() + " か所を一括で消す",
                    NamedTextColor.RED, List.of(
                            text(lister.confirmBulk ? "本当に消す場合は、もう一度クリック" : "クリックして、確認のあと消します", NamedTextColor.YELLOW),
                            text("1 つずつ消すなら、しゃがんで右クリック", NamedTextColor.GRAY))));
        }
        if (all.isEmpty()) inventory.setItem(22, button(Material.PAPER,
                lister.buriedOnly ? "埋まっているスポナーはありません" : "スポナーはまだありません", NamedTextColor.GRAY, List.of()));
    }

    private void onList(Player player, Lister lister, int slot, org.bukkit.event.inventory.ClickType click) {
        if (slot == CANCEL) {
            player.closeInventory();
            return;
        }
        if (slot == PREV || slot == NEXT) {
            lister.page += slot == NEXT ? 1 : -1;
            lister.confirmBulk = false;
            renderList(player, lister);
            return;
        }
        if (slot == FILTER) {
            lister.buriedOnly = !lister.buriedOnly;
            lister.confirmBulk = false;
            lister.page = 0;
            renderList(player, lister);
            return;
        }
        if (slot == BULK && lister.buriedOnly) {
            if (!lister.confirmBulk) {
                lister.confirmBulk = true;
            } else {
                List<FieldSpawner> targets = buriedOf(listed(player));
                for (FieldSpawner target : targets) service.remove(target);
                syncLabels();
                lister.confirmBulk = false;
                done(player, "ブロックに埋まっているスポナーを " + targets.size() + " か所消した。");
            }
            renderList(player, lister);
            return;
        }
        if (lister.confirmBulk) {
            lister.confirmBulk = false;
            renderList(player, lister);
        }
        String id = lister.spawners.get(slot);
        FieldSpawner spawner = id == null ? null : service.find(id).orElse(null);
        if (spawner == null) return;

        if (click.isRightClick() && click.isShiftClick()) {
            service.remove(spawner);
            syncLabels();
            done(player, spawner.id() + " (" + spawner.mobId() + ") を消した。");
            renderList(player, lister);
        } else if (click.isRightClick()) {
            // opened on the next tick: this click is still being processed
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline() && editing(player)) openSetup(player, spawner);
            });
        } else if (click.isLeftClick()) {
            World world = Bukkit.getWorld(spawner.worldName());
            if (world == null) {
                hint(player, "そのワールドは、いま読み込まれていません。");
                return;
            }
            player.closeInventory();
            Location target = spawner.in(world).add(0.0, 0.2, 0.0);
            target.setYaw(player.getLocation().getYaw());
            target.setPitch(player.getLocation().getPitch());
            player.teleport(target);
            done(player, spawner.id() + " (" + spawner.mobId() + ") へテレポートした。");
        }
    }

    // ---- the mob list ---------------------------------------------------------------------------------------------------

    private static final int PER_PAGE = 45;
    private static final int CANCEL = 45;
    private static final int PREV = 47;
    private static final int FILTER = 49;
    private static final int NEXT = 51;
    private static final String ALL = "@all";

    private static final class Picker implements MenuHolder {
        private final Map<Integer, String> mobs = new HashMap<>();
        private String tag = ALL;
        private int page;
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void openPicker(Player player) {
        Picker picker = new Picker();
        picker.inventory = Bukkit.createInventory(picker, 54, Component.text("スポナーのモブを選ぶ"));
        renderPicker(picker);
        player.openInventory(picker.inventory);
    }

    private List<String> tags() {
        TreeSet<String> tags = new TreeSet<>();
        for (RpgMobDefinition mob : mobRegistry.all().values()) tags.addAll(mob.areas());
        List<String> list = new ArrayList<>();
        list.add(ALL);
        list.addAll(tags);
        return list;
    }

    private void renderPicker(Picker picker) {
        Inventory inventory = picker.inventory;
        inventory.clear();
        picker.mobs.clear();
        List<RpgMobDefinition> shown = new ArrayList<>();
        for (RpgMobDefinition mob : mobRegistry.all().values()) {
            if (picker.tag.equals(ALL) || mob.inArea(picker.tag)) shown.add(mob);
        }
        shown.sort((left, right) -> left.level() != right.level() ? Integer.compare(left.level(), right.level()) : left.id().compareTo(right.id()));
        int pages = Math.max(1, (shown.size() + PER_PAGE - 1) / PER_PAGE);
        picker.page = Math.max(0, Math.min(picker.page, pages - 1));
        for (int index = 0; index < PER_PAGE; index++) {
            int at = picker.page * PER_PAGE + index;
            if (at >= shown.size()) break;
            RpgMobDefinition mob = shown.get(at);
            Material egg = Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
            inventory.setItem(index, button(egg == null ? Material.PAPER : egg, mob.name(), NamedTextColor.GREEN, List.of(
                    text("id: " + mob.id(), NamedTextColor.GRAY),
                    text("Lv " + mob.level() + " / HP " + Math.round(mob.stats().get(StatType.MAX_HP)), NamedTextColor.WHITE),
                    text("エリア: " + (mob.areas().isEmpty() ? "タグなし" : String.join(", ", mob.areas())), NamedTextColor.AQUA))));
            picker.mobs.put(index, mob.id());
        }
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, frame);
        inventory.setItem(CANCEL, button(Material.ARROW, "やめる", NamedTextColor.GRAY, List.of()));
        if (picker.page > 0) inventory.setItem(PREV, button(Material.SPECTRAL_ARROW, "前のページ", NamedTextColor.YELLOW, List.of()));
        if (picker.page < pages - 1) inventory.setItem(NEXT, button(Material.SPECTRAL_ARROW, "次のページ", NamedTextColor.YELLOW,
                List.of(text("ページ " + (picker.page + 1) + " / " + pages, NamedTextColor.GRAY))));
        inventory.setItem(FILTER, button(Material.NAME_TAG, "分類: " + (picker.tag.equals(ALL) ? "すべて" : "エリア " + picker.tag), NamedTextColor.GOLD,
                List.of(text(shown.size() + " 体", NamedTextColor.WHITE), text("クリックで、次の分類へ", NamedTextColor.GRAY))));
    }

    private void onPick(Player player, Picker picker, int slot) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (slot == CANCEL) {
            session.changing = null;
            player.closeInventory();
        } else if (slot == PREV || slot == NEXT) {
            picker.page += slot == NEXT ? 1 : -1;
            renderPicker(picker);
        } else if (slot == FILTER) {
            List<String> tags = tags();
            picker.tag = tags.get((Math.max(0, tags.indexOf(picker.tag)) + 1) % tags.size());
            picker.page = 0;
            renderPicker(picker);
        } else if (picker.mobs.containsKey(slot)) {
            player.closeInventory();
            place(player, session, picker.mobs.get(slot));
        }
    }

    // ---- items ------------------------------------------------------------------------------------------------------------

    private ItemStack toolItem(Tool tool) {
        List<Component> lore = new ArrayList<>();
        for (String line : tool.lore) lore.add(text(line, NamedTextColor.GRAY));
        ItemStack item = button(tool.material, tool.name, tool.color, lore);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, tool.name());
            item.setItemMeta(meta);
        }
        return item;
    }

    private static ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ITEM_SPECIFICS);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
