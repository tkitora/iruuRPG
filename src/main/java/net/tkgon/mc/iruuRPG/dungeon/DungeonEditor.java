package net.tkgon.mc.iruuRPG.dungeon;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
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
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Editor mode for dungeon templates (/dungeon edit &lt;id&gt;): the hotbar becomes a fixed set of tools, and the dungeon is
 * set up by clicking instead of typing commands. Building mode (the normal one) stays free; the editor can be switched
 * on and off at any time. The real hotbar is kept (also in a file, in case the server stops) and given back when
 * leaving the editor, the template world, or the server.
 *
 * Tools (slots 1-5, 8 = done; slot 9 keeps the menu item): place a mob (pick from a list, optionally as the boss;
 * sneak = the last mob again), an entrance (the dungeon's entry, the boss room trigger, the boss room, the reward room:
 * chosen from a small list; sneak = the last kind again), a chest (road chest or clear chest, the same way), the exit,
 * and delete (a placed mob or a road chest). Done: click = back to building, sneak-click = save and go back.
 */
public final class DungeonEditor implements Listener {

    private enum Tool {
        MOB(Material.ZOMBIE_SPAWN_EGG, "モブを置く", NamedTextColor.GREEN,
                "ブロックをクリック → 一覧から選んで、その上に置く", "一覧の「ボスとして置く」でボスに", "しゃがんでクリック: 前と同じモブをもう一度"),
        ENTRANCE(Material.OAK_DOOR, "入口を置く", NamedTextColor.GREEN,
                "ブロックをクリック → 種類を選ぶ (その上、向きは今の向き)",
                "ダンジョンの入口 / ボス部屋への入口 / ボス部屋 / 報酬部屋", "しゃがんでクリック: 前と同じ種類をもう一度"),
        CHEST(Material.CHEST, "宝箱を置く", NamedTextColor.GOLD,
                "ブロックをクリック → 種類を選ぶ (宝箱をクリックならその宝箱)",
                "道中の宝箱 (いくつでも) / クリアの宝箱 (報酬部屋)", "しゃがんでクリック: 前と同じ種類をもう一度"),
        EXIT(Material.ENDER_EYE, "出口を置く", NamedTextColor.AQUA,
                "ブロックをクリック → その上を脱出口にする", "ボスを倒すと通れる"),
        DELETE(Material.BARRIER, "置いたものを消す", NamedTextColor.DARK_RED,
                "置いたモブ (名札の下) をクリック → 消す", "道中の宝箱をクリック → 消す / ブロックをクリック: その近くのモブを消す"),
        DONE(Material.LIME_DYE, "エディターを終える", NamedTextColor.WHITE,
                "クリック: 建築モードに戻る (このワールドのまま)", "しゃがんでクリック: 保存して元の場所へ戻る");

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

    private static final int TOOL_SLOTS = 8; // slots 0-7; slot 8 is the menu item
    /** Hotbar slots 1-8 (index 0-7); empty slots stay empty. */
    private static final Tool[] HOTBAR = {Tool.MOB, Tool.ENTRANCE, Tool.CHEST, Tool.EXIT, Tool.DELETE, null, null, Tool.DONE};

    /** The kinds the entrance and chest tools ask for. */
    private enum Kind {
        ENTRY("ダンジョンの入口", Material.OAK_DOOR, NamedTextColor.GREEN),
        BOSS_TRIGGER("ボス部屋への入口", Material.TRIPWIRE_HOOK, NamedTextColor.RED),
        BOSS_ROOM("ボス部屋 (移動先)", Material.CRIMSON_DOOR, NamedTextColor.RED),
        REWARD_ROOM("報酬部屋", Material.GOLD_BLOCK, NamedTextColor.GOLD),
        ROAD_CHEST("道中の宝箱", Material.CHEST, NamedTextColor.YELLOW),
        CLEAR_CHEST("クリアの宝箱 (報酬部屋)", Material.ENDER_CHEST, NamedTextColor.GOLD);

        private final String label;
        private final Material icon;
        private final NamedTextColor color;

        Kind(String label, Material icon, NamedTextColor color) {
            this.label = label;
            this.icon = icon;
            this.color = color;
        }
    }

    private static final Kind[] ENTRANCE_KINDS = {Kind.ENTRY, Kind.BOSS_TRIGGER, Kind.BOSS_ROOM, Kind.REWARD_ROOM};
    private static final Kind[] CHEST_KINDS = {Kind.ROAD_CHEST, Kind.CLEAR_CHEST};

    private static final class Session {
        private final String dungeonId;
        private final ItemStack[] savedHotbar;
        private String lastMob;
        private Location pending;
        private boolean asBoss;
        private float pendingYaw;
        private Block pendingBlock;
        private Kind lastEntrance;
        private Kind lastChest;

        private Session(String dungeonId, ItemStack[] savedHotbar) {
            this.dungeonId = dungeonId;
            this.savedHotbar = savedHotbar;
        }
    }

    private final JavaPlugin plugin;
    private final DungeonService service;
    private final DungeonRegistry registry;
    private final RpgMobRegistry mobRegistry;
    private final NamespacedKey toolKey;
    private final File saveFolder;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public DungeonEditor(JavaPlugin plugin, DungeonService service, RpgMobRegistry mobRegistry) {
        this.plugin = plugin;
        this.service = service;
        this.registry = service.registry();
        this.mobRegistry = mobRegistry;
        this.toolKey = new NamespacedKey(plugin, "dungeon_tool");
        this.saveFolder = new File(plugin.getDataFolder(), "editor");
    }

    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    // ---- switching -------------------------------------------------------------------------------------------------

    /** /dungeon edit [id]: go to the template (if needed) and switch the editor on; inside the template it toggles. */
    public void toggle(Player player, String id) {
        Session current = sessions.get(player.getUniqueId());
        if (current != null && (id == null || id.equals(current.dungeonId))) {
            stop(player, true);
            return;
        }
        if (id == null) {
            String world = player.getWorld().getName();
            if (!world.startsWith("dungeon_template_")) {
                player.sendMessage(Component.text("[エディター] /dungeon edit <ダンジョンのid> で始めてください。", NamedTextColor.RED));
                return;
            }
            id = world.substring("dungeon_template_".length());
        }
        DungeonDefinition definition = registry.find(id).orElse(null);
        if (definition == null) {
            player.sendMessage(Component.text("[エディター] そのダンジョンはありません: " + id, NamedTextColor.RED));
            return;
        }
        if (current != null) stop(player, false);
        if (!player.getWorld().getName().equals(definition.templateWorldName())) service.build(player, definition);
        if (!player.getWorld().getName().equals(definition.templateWorldName())) return; // could not open the template
        start(player, definition);
    }

    private void start(Player player, DungeonDefinition definition) {
        ItemStack[] saved = new ItemStack[TOOL_SLOTS];
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            saved[slot] = item == null ? null : item.clone();
        }
        Session session = new Session(definition.id(), saved);
        sessions.put(player.getUniqueId(), session);
        writeBackup(player.getUniqueId(), saved);
        renderHotbar(player);
        player.getInventory().setHeldItemSlot(0);
        service.refreshMarkers(definition.id());
        player.sendMessage(Component.text("[エディター] 「" + definition.name() + "」のエディターモード。ホットバーの道具で設定する。"
                + "建築はエディターを終えてから (8番の道具、または /dungeon edit)。", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
    }

    private void renderHotbar(Player player) {
        for (int slot = 0; slot < HOTBAR.length; slot++) {
            player.getInventory().setItem(slot, HOTBAR[slot] == null ? null : toolItem(HOTBAR[slot]));
        }
    }

    /** Leaves the editor and gives the hotbar back. */
    public void stop(Player player, boolean announce) {
        Session session = sessions.remove(player.getUniqueId());
        if (session == null) return;
        restore(player, session.savedHotbar);
        deleteBackup(player.getUniqueId());
        if (announce) player.sendMessage(Component.text("[エディター] 建築モードに戻った。(/dungeon edit でまたエディターへ)", NamedTextColor.GRAY));
    }

    public void shutdown() {
        for (UUID id : new ArrayList<>(sessions.keySet())) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) stop(player, false);
        }
    }

    private void restore(Player player, ItemStack[] hotbar) {
        for (int slot = 0; slot < TOOL_SLOTS; slot++) {
            player.getInventory().setItem(slot, hotbar[slot] == null ? null : hotbar[slot].clone());
        }
    }

    // ---- the hotbar is kept in a file while editing (the server may stop) --------------------------------------------

    private File backupFile(UUID id) {
        return new File(saveFolder, id + ".yml");
    }

    private void writeBackup(UUID id, ItemStack[] hotbar) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (int slot = 0; slot < hotbar.length; slot++) yaml.set("hotbar." + slot, hotbar[slot]);
        try {
            if (!saveFolder.exists() && !saveFolder.mkdirs()) return;
            yaml.save(backupFile(id));
        } catch (IOException e) {
            plugin.getLogger().warning("dungeon editor: could not save the hotbar of " + id + ": " + e.getMessage());
        }
    }

    private void deleteBackup(UUID id) {
        File file = backupFile(id);
        if (file.exists() && !file.delete()) plugin.getLogger().warning("dungeon editor: could not delete " + file.getName());
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

    /** Dying while editing: the tools are not dropped; the real hotbar comes back after the respawn. */
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

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && event.getFrom().getName().equals("dungeon_template_" + session.dungeonId)) {
            stop(event.getPlayer(), true);
        }
    }

    // ---- the hotbar is fixed while editing -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOW)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && editing(player)) {
            event.setCancelled(true);
            if (event.getView().getTopInventory().getHolder() instanceof Picker picker) onPick(player, picker, event.getRawSlot());
            if (event.getView().getTopInventory().getHolder() instanceof Choice choice) onChoice(player, choice, event.getRawSlot());
        }
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
        event.setCancelled(true); // a tool never places or uses anything itself
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        DungeonDefinition definition = registry.find(session.dungeonId).orElse(null);
        if (definition == null) return;
        if (tool == Tool.DONE) {
            if (player.isSneaking()) {
                stop(player, false);
                service.saveTemplate(player, definition);
            } else {
                stop(player, true);
            }
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null) {
            // (clicking a marker also sends a click into the air; the marker click handles it)
            if (tool != Tool.DELETE) hint(player, "ブロックをクリックしてください。");
            return;
        }
        BlockFace face = event.getBlockFace();
        Location above = clicked.getRelative(face).getLocation().add(0.5, 0.0, 0.5);
        session.pending = above;
        session.pendingYaw = player.getLocation().getYaw();
        session.pendingBlock = clicked.getType() == Material.CHEST ? clicked : clicked.getRelative(face);
        switch (tool) {
            case MOB -> {
                if (player.isSneaking() && session.lastMob != null) {
                    place(player, session, session.lastMob);
                } else {
                    openPicker(player, session);
                }
            }
            case ENTRANCE -> {
                if (player.isSneaking() && session.lastEntrance != null) {
                    apply(player, session, session.lastEntrance);
                } else {
                    openChoice(player, "どの入口にする?", ENTRANCE_KINDS);
                }
            }
            case CHEST -> {
                if (player.isSneaking() && session.lastChest != null) {
                    apply(player, session, session.lastChest);
                } else {
                    openChoice(player, "どの宝箱にする?", CHEST_KINDS);
                }
            }
            case EXIT -> {
                registry.setExit(definition, above);
                done(player, definition, "ここを脱出口にした。");
            }
            case DELETE -> {
                int road = definition.roadChestAt(clicked.getX(), clicked.getY(), clicked.getZ());
                if (road >= 0) {
                    registry.removeRoadChest(definition, road);
                    clicked.setType(Material.AIR);
                    done(player, definition, "道中の宝箱 #" + (road + 1) + " を消した。");
                } else {
                    deleteNear(player, definition, clicked.getLocation().add(0.5, 1.0, 0.5));
                }
            }
            default -> {
            }
        }
    }

    /** Sets the chosen kind of entrance or chest at the clicked spot. */
    private void apply(Player player, Session session, Kind kind) {
        DungeonDefinition definition = registry.find(session.dungeonId).orElse(null);
        if (definition == null || session.pending == null) return;
        Location at = session.pending.clone();
        at.setYaw(session.pendingYaw);
        switch (kind) {
            case ENTRY -> {
                registry.setEntry(definition, at);
                session.lastEntrance = kind;
                done(player, definition, "ここをダンジョンの入口にした。");
            }
            case BOSS_TRIGGER -> {
                registry.setBossTrigger(definition, at);
                session.lastEntrance = kind;
                done(player, definition, "ここをボス部屋への入口にした。(パーティー人数-1 人が集まると移動)");
            }
            case BOSS_ROOM -> {
                registry.setBossRoom(definition, at);
                session.lastEntrance = kind;
                done(player, definition, "ここをボス部屋 (移動先) にした。");
            }
            case REWARD_ROOM -> {
                registry.setRewardRoom(definition, at);
                session.lastEntrance = kind;
                done(player, definition, "ここを報酬部屋にした。");
            }
            case ROAD_CHEST -> {
                session.pendingBlock.setType(Material.CHEST);
                registry.addRoadChest(definition, session.pendingBlock.getLocation());
                session.lastChest = kind;
                DungeonDefinition updated = registry.find(definition.id()).orElse(definition);
                done(player, updated, "道中の宝箱 #" + updated.roadChests().size() + " を置いた。");
            }
            case CLEAR_CHEST -> {
                session.pendingBlock.setType(Material.CHEST);
                registry.setChest(definition, session.pendingBlock.getLocation());
                session.lastChest = kind;
                done(player, definition, "ここにクリアの宝箱を置いた。");
            }
        }
    }

    // ---- choosing a kind (entrance / chest) --------------------------------------------------------------------------

    private static final class Choice implements MenuHolder {
        private final Map<Integer, Kind> kinds = new HashMap<>();
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void openChoice(Player player, String title, Kind[] kinds) {
        Choice choice = new Choice();
        choice.inventory = Bukkit.createInventory(choice, 27, Component.text(title));
        int[] slots = kinds.length == 2 ? new int[]{11, 15} : new int[]{10, 12, 14, 16};
        for (int index = 0; index < kinds.length && index < slots.length; index++) {
            Kind kind = kinds[index];
            choice.inventory.setItem(slots[index], button(kind.icon, kind.label, kind.color, List.of(text("クリックで、ここに置く", NamedTextColor.GRAY))));
            choice.kinds.put(slots[index], kind);
        }
        choice.inventory.setItem(22, button(Material.ARROW, "やめる", NamedTextColor.GRAY, List.of()));
        player.openInventory(choice.inventory);
    }

    private void onChoice(Player player, Choice choice, int slot) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (slot == 22) {
            player.closeInventory();
            return;
        }
        Kind kind = choice.kinds.get(slot);
        if (kind == null) return;
        player.closeInventory();
        apply(player, session, kind);
    }

    /** Clicking a placed mob's marker (right click). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMarkerClick(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (markerClick(event.getPlayer(), service.markerOf(event.getRightClicked()))) event.setCancelled(true);
    }

    /** Hitting a placed mob's marker (left click) works the same. */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMarkerHit(PrePlayerAttackEntityEvent event) {
        if (markerClick(event.getPlayer(), service.markerOf(event.getAttacked()))) event.setCancelled(true);
    }

    private boolean markerClick(Player player, DungeonService.MarkerRef ref) {
        if (ref == null) return false;
        Session session = sessions.get(player.getUniqueId());
        DungeonDefinition definition = registry.find(ref.dungeonId()).orElse(null);
        if (definition == null || ref.index() >= definition.spawns().size()) return true;
        DungeonDefinition.MobSpawn spawn = definition.spawns().get(ref.index());
        Tool tool = session == null ? null : toolOf(player.getInventory().getItemInMainHand());
        if (tool == Tool.DELETE) {
            registry.removeSpawn(definition, ref.index());
            done(player, definition, "#" + (ref.index() + 1) + " " + spawn.mobId() + " を消した。");
        } else {
            player.sendActionBar(Component.text("#" + (ref.index() + 1) + " " + spawn.mobId()
                    + (spawn.mobId().equals(definition.boss()) ? " (ボス)" : ""), NamedTextColor.YELLOW));
        }
        return true;
    }

    private void deleteNear(Player player, DungeonDefinition definition, Location near) {
        int best = -1;
        double bestDistance = 1.5 * 1.5;
        for (int index = 0; index < definition.spawns().size(); index++) {
            double distance = definition.spawns().get(index).in(near.getWorld()).distanceSquared(near);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = index;
            }
        }
        if (best < 0) {
            hint(player, "近くに置いたモブがない。(名札の下をクリックしても消せる)");
            return;
        }
        String mob = definition.spawns().get(best).mobId();
        registry.removeSpawn(definition, best);
        done(player, definition, "#" + (best + 1) + " " + mob + " を消した。");
    }

    private void place(Player player, Session session, String mobId) {
        DungeonDefinition definition = registry.find(session.dungeonId).orElse(null);
        if (definition == null || session.pending == null) return;
        registry.addSpawn(definition, mobId, session.pending);
        session.lastMob = mobId;
        if (session.asBoss) {
            registry.setBoss(registry.find(session.dungeonId).orElse(definition), mobId);
        }
        done(player, registry.find(session.dungeonId).orElse(definition),
                "#" + (definition.spawns().size() + 1) + " " + mobId + " を置いた。" + (session.asBoss ? " (ボス)" : "") + " しゃがんでクリックで同じモブをもう一度");
    }

    private void done(Player player, DungeonDefinition definition, String message) {
        service.refreshMarkers(definition.id());
        player.sendActionBar(Component.text(message, NamedTextColor.GREEN));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    private void hint(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.GRAY));
    }

    // ---- the mob list ---------------------------------------------------------------------------------------------------

    // top row: tag buttons; rows 2-5: mobs; bottom row: cancel, pages, the boss toggle
    private static final int CANCEL = 45;
    private static final int PREV = 47;
    private static final int BOSS_TOGGLE = 49;
    private static final int NEXT = 51;
    private static final int FIRST_MOB_SLOT = 9;
    private static final int MOBS_PER_PAGE = 36;
    private static final String TAG_HERE = "@here";
    private static final String TAG_ALL = "@all";
    private static final String TAG_NONE = "@none";

    private static final class Picker implements MenuHolder {
        private final Map<Integer, String> mobs = new HashMap<>();   // slot -> mob id
        private final Map<Integer, String> tags = new HashMap<>();   // slot -> tag key
        private String tag;
        private int page;
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void openPicker(Player player, Session session) {
        Picker picker = new Picker();
        picker.inventory = Bukkit.createInventory(picker, 54, Component.text("置くモブを選ぶ"));
        boolean anyHere = mobRegistry.all().values().stream().anyMatch(mob -> mob.inArea(session.dungeonId));
        picker.tag = anyHere ? TAG_HERE : TAG_ALL;
        renderPicker(picker, session);
        player.openInventory(picker.inventory);
    }

    /** Every area tag used by some mob, sorted. */
    private List<String> areaTags() {
        java.util.TreeSet<String> tags = new java.util.TreeSet<>();
        for (RpgMobDefinition mob : mobRegistry.all().values()) tags.addAll(mob.areas());
        return new ArrayList<>(tags);
    }

    private boolean matches(RpgMobDefinition mob, String tag, Session session) {
        return switch (tag) {
            case TAG_ALL -> true;
            case TAG_HERE -> mob.inArea(session.dungeonId);
            case TAG_NONE -> mob.areas().isEmpty();
            default -> mob.inArea(tag);
        };
    }

    private void renderPicker(Picker picker, Session session) {
        Inventory inventory = picker.inventory;
        inventory.clear();
        picker.mobs.clear();
        picker.tags.clear();

        // top row: この ダンジョン / すべて / each area / タグなし (as many as fit)
        List<String> keys = new ArrayList<>(List.of(TAG_HERE, TAG_ALL));
        List<String> areas = areaTags();
        areas.remove(session.dungeonId); // already "このダンジョン"
        int room = 9 - keys.size() - 1;
        for (int index = 0; index < areas.size() && index < room; index++) keys.add(areas.get(index));
        keys.add(TAG_NONE);
        for (int slot = 0; slot < keys.size(); slot++) {
            String key = keys.get(slot);
            boolean selected = key.equals(picker.tag);
            long count = mobRegistry.all().values().stream().filter(mob -> matches(mob, key, session)).count();
            Material icon = switch (key) {
                case TAG_HERE -> Material.SPAWNER;
                case TAG_ALL -> Material.BOOKSHELF;
                case TAG_NONE -> Material.PAPER;
                default -> Material.NAME_TAG;
            };
            String label = switch (key) {
                case TAG_HERE -> "このダンジョン (" + session.dungeonId + ")";
                case TAG_ALL -> "すべて";
                case TAG_NONE -> "タグなし";
                default -> "エリア: " + key;
            };
            ItemStack button = button(icon, label, selected ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                    text(count + " 体", NamedTextColor.WHITE),
                    text(selected ? "表示中" : "クリックで、このモブだけ表示", selected ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            if (selected) {
                ItemMeta meta = button.getItemMeta();
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                button.setItemMeta(meta);
            }
            inventory.setItem(slot, button);
            picker.tags.put(slot, key);
        }
        // (areas beyond the top row's room still show under "すべて")

        // the mobs of the chosen tag, by level
        List<RpgMobDefinition> shown = new ArrayList<>();
        for (RpgMobDefinition mob : mobRegistry.all().values()) {
            if (matches(mob, picker.tag, session)) shown.add(mob);
        }
        shown.sort((left, right) -> left.level() != right.level() ? Integer.compare(left.level(), right.level()) : left.id().compareTo(right.id()));
        int pages = Math.max(1, (shown.size() + MOBS_PER_PAGE - 1) / MOBS_PER_PAGE);
        picker.page = Math.max(0, Math.min(picker.page, pages - 1));
        for (int index = 0; index < MOBS_PER_PAGE; index++) {
            int at = picker.page * MOBS_PER_PAGE + index;
            if (at >= shown.size()) break;
            RpgMobDefinition mob = shown.get(at);
            Material egg = Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
            List<Component> lore = new ArrayList<>();
            lore.add(text("id: " + mob.id(), NamedTextColor.GRAY));
            lore.add(text("Lv " + mob.level() + " / HP " + Math.round(mob.stats().get(StatType.MAX_HP)), NamedTextColor.WHITE));
            lore.add(text("エリア: " + (mob.areas().isEmpty() ? "タグなし" : String.join(", ", mob.areas())), NamedTextColor.AQUA));
            if (mob.id().equals(session.lastMob)) lore.add(text("(前に置いたモブ)", NamedTextColor.YELLOW));
            inventory.setItem(FIRST_MOB_SLOT + index, button(egg == null ? Material.PAPER : egg, mob.name(), NamedTextColor.GREEN, lore));
            picker.mobs.put(FIRST_MOB_SLOT + index, mob.id());
        }
        if (shown.isEmpty()) {
            inventory.setItem(22, button(Material.GRAY_DYE, "この分類のモブはいない", NamedTextColor.GRAY, List.of()));
        }

        // bottom row
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < 54; slot++) inventory.setItem(slot, frame);
        inventory.setItem(CANCEL, button(Material.ARROW, "やめる", NamedTextColor.GRAY, List.of()));
        if (picker.page > 0) inventory.setItem(PREV, button(Material.SPECTRAL_ARROW, "前のページ", NamedTextColor.YELLOW, List.of()));
        if (picker.page < pages - 1) inventory.setItem(NEXT, button(Material.SPECTRAL_ARROW, "次のページ", NamedTextColor.YELLOW,
                List.of(text("ページ " + (picker.page + 1) + " / " + pages, NamedTextColor.GRAY))));
        renderBossToggle(inventory, session);
    }

    private void renderBossToggle(Inventory inventory, Session session) {
        inventory.setItem(BOSS_TOGGLE, button(session.asBoss ? Material.WITHER_SKELETON_SKULL : Material.SKELETON_SKULL,
                session.asBoss ? "ボスとして置く: オン" : "ボスとして置く: オフ", session.asBoss ? NamedTextColor.RED : NamedTextColor.GRAY,
                List.of(text("オンで置くと、そのモブがボスになる (倒れたらクリア)", NamedTextColor.GRAY), text("クリックで切り替え", NamedTextColor.GRAY))));
    }

    private void onPick(Player player, Picker picker, int slot) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (slot == CANCEL) {
            player.closeInventory();
        } else if (slot == BOSS_TOGGLE) {
            session.asBoss = !session.asBoss;
            renderBossToggle(picker.inventory, session);
        } else if (slot == PREV || slot == NEXT) {
            picker.page += slot == NEXT ? 1 : -1;
            renderPicker(picker, session);
        } else if (picker.tags.containsKey(slot)) {
            picker.tag = picker.tags.get(slot);
            picker.page = 0;
            renderPicker(picker, session);
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
