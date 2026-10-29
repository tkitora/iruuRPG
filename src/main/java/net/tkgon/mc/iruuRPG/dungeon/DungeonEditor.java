package net.tkgon.mc.iruuRPG.dungeon;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.bgm.Bgm;
import net.tkgon.mc.iruuRPG.bgm.BgmMenus;
import net.tkgon.mc.iruuRPG.bgm.BgmSettings;
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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
 * Tools (slots 1-6, 8 = done; slot 9 keeps the menu item): place a mob (pick from a list, optionally as the boss;
 * sneak = the last mob again), an entrance (the dungeon's entry, the boss room trigger, the boss room, the reward room:
 * chosen from a small list; sneak = the last kind again), a chest (road chest or clear chest, the same way), the exit,
 * delete (a placed mob or a road chest), and the music (a song for the way, up to 3 for the boss fight: a screen, with a listen).
 * Done: click = back to building, sneak-click = save and go back.
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
        BGM(Material.JUKEBOX, "BGMを設定する", NamedTextColor.LIGHT_PURPLE,
                "クリック: 道中の曲 (1つ) と、ボス戦の曲 (3つまで) を決める",
                "ボス戦の曲は1つ目が必須。2つ目以降は、ボスが次のフェーズに入ったときの曲", "曲は一覧から選ぶ (右クリックで試聴)"),
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
    private static final Tool[] HOTBAR = {Tool.MOB, Tool.ENTRANCE, Tool.CHEST, Tool.EXIT, Tool.DELETE, Tool.BGM, null, Tool.DONE};

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
        private String lastTable;

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
    private BgmSettings bgmSettings;

    public DungeonEditor(JavaPlugin plugin, DungeonService service, RpgMobRegistry mobRegistry) {
        this.plugin = plugin;
        this.service = service;
        this.registry = service.registry();
        this.mobRegistry = mobRegistry;
        this.toolKey = new NamespacedKey(plugin, "dungeon_tool");
        this.saveFolder = new File(plugin.getDataFolder(), "editor");
    }

    /** The music tool needs the BGM (the songs and the listen); without it the tool says so. */
    public void setBgmSettings(BgmSettings bgmSettings) {
        this.bgmSettings = bgmSettings;
    }

    private Bgm bgm() {
        return bgmSettings == null ? Bgm.NONE : bgmSettings.bgm();
    }

    public boolean editing(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    private java.util.function.Predicate<Player> otherEditor = player -> false;

    /** True while the player is in another editor (the field spawner editor): two editors cannot share the hotbar. */
    public void setOtherEditor(java.util.function.Predicate<Player> otherEditor) {
        this.otherEditor = otherEditor;
    }

    // ---- switching -------------------------------------------------------------------------------------------------

    /** /dungeon edit [id]: go to the template (if needed) and switch the editor on; inside the template it toggles. */
    public void toggle(Player player, String id) {
        Session current = sessions.get(player.getUniqueId());
        if (current != null && (id == null || id.equals(current.dungeonId))) {
            stop(player, true);
            return;
        }
        if (current == null && otherEditor.test(player)) {
            player.sendMessage(Component.text("[エディター] スポナーのエディターを終えてから使ってください。", NamedTextColor.RED));
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
            if (event.getView().getTopInventory().getHolder() instanceof Tables tables) onTable(player, tables, event.getRawSlot());
            if (event.getView().getTopInventory().getHolder() instanceof BgmScreen screen) onBgm(player, screen, event.getRawSlot(), event.getClick());
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
        if (tool == Tool.BGM) {
            openBgm(player, definition);
            return;
        }
        if (tool == Tool.DONE) {
            warnBgm(player, definition);
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
                    if (session.lastChest == Kind.ROAD_CHEST) {
                        placeRoadChest(player, session, session.lastTable == null ? definition.defaultChestTable() : session.lastTable);
                    } else {
                        apply(player, session, session.lastChest);
                    }
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
            case ROAD_CHEST -> openTables(player, definition); // the table is chosen next
            case CLEAR_CHEST -> {
                session.pendingBlock.setType(Material.CHEST);
                registry.setChest(definition, session.pendingBlock.getLocation());
                session.lastChest = kind;
                done(player, definition, "ここにクリアの宝箱を置いた。");
            }
        }
    }

    /** Places (or re-tables) the road chest at the clicked block with this table. */
    private void placeRoadChest(Player player, Session session, String table) {
        DungeonDefinition definition = registry.find(session.dungeonId).orElse(null);
        if (definition == null || session.pendingBlock == null) return;
        session.pendingBlock.setType(Material.CHEST);
        boolean existed = definition.roadChestAt(session.pendingBlock.getX(), session.pendingBlock.getY(), session.pendingBlock.getZ()) >= 0;
        registry.setRoadChest(definition, session.pendingBlock.getLocation(), table);
        session.lastChest = Kind.ROAD_CHEST;
        session.lastTable = table;
        DungeonDefinition updated = registry.find(definition.id()).orElse(definition);
        int index = updated.roadChestAt(session.pendingBlock.getX(), session.pendingBlock.getY(), session.pendingBlock.getZ());
        done(player, updated, (existed ? "宝箱 #" + (index + 1) + " の表を " : "宝箱 #" + (index + 1) + " を置いた。表: ") + table + (existed ? " にした。" : ""));
    }

    // ---- choosing a loot table for a road chest ------------------------------------------------------------------------

    private static final class Tables implements MenuHolder {
        private final Map<Integer, String> tables = new HashMap<>();
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void openTables(Player player, DungeonDefinition definition) {
        Tables holder = new Tables();
        holder.inventory = Bukkit.createInventory(holder, 54, Component.text("宝箱の中身 (表) を選ぶ"));
        List<String> names = new ArrayList<>(DungeonLoot.tableNames(plugin));
        names.remove(definition.defaultChestTable());
        names.add(0, definition.defaultChestTable());
        for (int index = 0; index < names.size() && index < 45; index++) {
            String name = names.get(index);
            boolean exists = new File(plugin.getDataFolder(), "loot/" + name + ".yml").exists();
            List<Component> lore = new ArrayList<>();
            lore.add(text("loot/" + name + ".yml", NamedTextColor.GRAY));
            if (index == 0) lore.add(text("このダンジョンの道中の宝箱の基本の表", NamedTextColor.YELLOW));
            if (!exists) lore.add(text("ファイルが無い (開けると空)", NamedTextColor.RED));
            holder.inventory.setItem(index, button(exists ? Material.CHEST : Material.GRAY_DYE, name, exists ? NamedTextColor.GOLD : NamedTextColor.GRAY, lore));
            holder.tables.put(index, name);
        }
        holder.inventory.setItem(49, button(Material.ARROW, "やめる", NamedTextColor.GRAY, List.of()));
        player.openInventory(holder.inventory);
    }

    private void onTable(Player player, Tables holder, int slot) {
        Session session = sessions.get(player.getUniqueId());
        if (session == null) return;
        if (slot == 49) {
            player.closeInventory();
            return;
        }
        String table = holder.tables.get(slot);
        if (table == null) return;
        player.closeInventory();
        placeRoadChest(player, session, table);
    }


    // ---- the music: a song for the way, up to 3 for the boss fight ----------------------------------------------------------------

    private static final int ROUTE_SLOT = 10;
    private static final int BOSS_SLOT = 12; // 12, 13, 14
    private static final int HELP_SLOT = 16;
    private static final int PICK_PREVIOUS = 45;
    private static final int PICK_CLEAR = 47;
    private static final int PICK_BACK = 49;
    private static final int PICK_NEXT = 53;

    /** The screen of the music: the main one (the four slots), or the list of songs for one of them. */
    private static final class BgmScreen implements MenuHolder {
        private final boolean picking;
        /** 0 = the song for the way, 1-3 = the songs of the boss fight. */
        private final int target;
        private final int page;
        private final List<Bgm.SongInfo> songs;
        private Inventory inventory;

        private BgmScreen(boolean picking, int target, int page, List<Bgm.SongInfo> songs) {
            this.picking = picking;
            this.target = target;
            this.page = page;
            this.songs = songs;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void openBgm(Player player, DungeonDefinition definition) {
        if (bgm().songs().isEmpty()) {
            hint(player, "BGMの曲がありません (BGMが動いていない、または曲が無い)。/bgm songs で確かめてください。");
            return;
        }
        BgmScreen screen = new BgmScreen(false, 0, 0, List.of());
        screen.inventory = Bukkit.createInventory(screen, 27, Component.text("BGM - " + definition.name()));
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < 27; slot++) screen.inventory.setItem(slot, frame);
        screen.inventory.setItem(ROUTE_SLOT, songSlot(definition.bgmRoute(), "道中の曲", false, List.of(
                text("入ったときから、ループで流れる", NamedTextColor.GRAY),
                text("ボス戦が始まると、ボス戦の曲に変わる", NamedTextColor.GRAY))));
        List<String> boss = definition.bgmBoss();
        String[] meaning = {"ボス戦のはじめから、ループで流れる", "ボスが次のフェーズに入ったときの曲", "さらに次のフェーズに入ったときの曲"};
        for (int index = 0; index < DungeonDefinition.BOSS_SONGS; index++) {
            String song = index < boss.size() ? boss.get(index) : null;
            screen.inventory.setItem(BOSS_SLOT + index, songSlot(song, "ボス戦の曲 " + (index + 1) + (index == 0 ? " (必須)" : ""), index == 0 && boss.isEmpty(), List.of(
                    text(meaning[index], NamedTextColor.GRAY),
                    text(index == 0 ? "ボス戦の曲は、最低1つ必要" : index > boss.size() ? "前の枠から順に決める" : "フェーズが無いボスは、1つ目だけが流れる", NamedTextColor.GRAY))));
        }
        screen.inventory.setItem(HELP_SLOT, button(Material.PAPER, "しくみ", NamedTextColor.YELLOW, List.of(
                text("ボス戦: ボス部屋へ移動したとき、またはボスが傷ついた・狙ってきたとき始まる", NamedTextColor.GRAY),
                text("フェーズ: ボスの行動 (BT) の黒板の phase が 2, 3 になると次の曲", NamedTextColor.GRAY),
                text("ボスを倒すと、フェードアウトして終わる", NamedTextColor.GRAY),
                text("曲が無いダンジョンは、今までどおり無音", NamedTextColor.GRAY))));
        screen.inventory.setItem(22, button(Material.ARROW, "閉じる", NamedTextColor.GRAY, List.of(text("変更は、その場で保存される", NamedTextColor.GRAY))));
        player.openInventory(screen.inventory);
    }

    private ItemStack songSlot(String songId, String label, boolean missing, List<Component> info) {
        Bgm.SongInfo song = songId == null ? null : bgm().song(songId);
        List<Component> lore = new ArrayList<>();
        if (songId == null) {
            lore.add(text(missing ? "未設定 (必須)" : "未設定", missing ? NamedTextColor.RED : NamedTextColor.GRAY));
        } else if (song == null) {
            lore.add(text("曲が見つかりません: " + songId, NamedTextColor.RED));
        } else {
            lore.add(text((song.category().isEmpty() ? song.title() : song.shortTitle()) + "  " + song.length(), NamedTextColor.GREEN));
            if (!song.category().isEmpty()) lore.add(text(song.code(), NamedTextColor.YELLOW));
        }
        lore.addAll(info);
        lore.add(text("左クリック: 曲を選ぶ", NamedTextColor.YELLOW));
        if (songId != null) lore.add(text("右クリック: 外す", NamedTextColor.YELLOW));
        return button(songId == null ? Material.GRAY_DYE : Material.JUKEBOX, label, songId == null ? (missing ? NamedTextColor.RED : NamedTextColor.GRAY) : NamedTextColor.LIGHT_PURPLE, lore);
    }

    private void openPick(Player player, int target, int page) {
        List<Bgm.SongInfo> songs = bgm().songs();
        int shown = Math.max(0, Math.min(page, BgmMenus.pages(songs.size()) - 1));
        BgmScreen screen = new BgmScreen(true, target, shown, songs);
        String label = target == 0 ? "道中の曲" : "ボス戦の曲 " + target;
        screen.inventory = Bukkit.createInventory(screen, 54, Component.text(label + "を選ぶ"));
        fillPick(screen, player);
        player.openInventory(screen.inventory);
    }

    /** Puts the songs of the page and the buttons in the screen (again, in place, when the listen changes: opening a new screen would end the listen). */
    private void fillPick(BgmScreen screen, Player player) {
        int pages = BgmMenus.pages(screen.songs.size());
        screen.inventory.clear();
        String playing = bgm().previewing(player);
        List<Bgm.SongInfo> list = BgmMenus.page(screen.songs, screen.page);
        for (int index = 0; index < list.size(); index++) {
            Bgm.SongInfo song = list.get(index);
            screen.inventory.setItem(index, BgmMenus.item(song, song.id().equals(playing), List.of(
                    text("左クリック: この曲にする", NamedTextColor.YELLOW), text("右クリック: 試聴 (もう一度で止める)", NamedTextColor.YELLOW))));
        }
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < 54; slot++) screen.inventory.setItem(slot, frame);
        if (screen.page > 0) screen.inventory.setItem(PICK_PREVIOUS, button(Material.ARROW, "前のページ", NamedTextColor.WHITE, List.of(text(screen.page + " / " + pages, NamedTextColor.GRAY))));
        if (screen.page < pages - 1) screen.inventory.setItem(PICK_NEXT, button(Material.ARROW, "次のページ", NamedTextColor.WHITE, List.of(text((screen.page + 2) + " / " + pages, NamedTextColor.GRAY))));
        screen.inventory.setItem(PICK_CLEAR, button(Material.BARRIER, "この枠を空にする", NamedTextColor.RED, List.of(text(screen.target == 0 ? "道中の曲を外す" : "この曲を外す (後ろの曲は前につまる)", NamedTextColor.GRAY))));
        screen.inventory.setItem(PICK_BACK, button(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of(text("試聴を止めて、BGMの設定へ", NamedTextColor.GRAY))));
    }

    private void onBgm(Player player, BgmScreen screen, int slot, ClickType click) {
        Session session = sessions.get(player.getUniqueId());
        DungeonDefinition definition = session == null ? null : registry.find(session.dungeonId).orElse(null);
        if (definition == null) return;
        if (!screen.picking) {
            if (slot == 22) {
                player.closeInventory();
                return;
            }
            int target = slot == ROUTE_SLOT ? 0 : slot >= BOSS_SLOT && slot < BOSS_SLOT + DungeonDefinition.BOSS_SONGS ? slot - BOSS_SLOT + 1 : -1;
            if (target < 0) return;
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                setSong(player, definition, target, null);
                return;
            }
            if (target > definition.bgmBoss().size() + 1) {
                hint(player, "ボス戦の曲は、前の枠から順に決めてください。");
                return;
            }
            openPick(player, target, 0);
            return;
        }
        if (slot == PICK_BACK) {
            bgm().stopPreview(player);
            openBgm(player, definition);
        } else if (slot == PICK_PREVIOUS && screen.page > 0) {
            openPick(player, screen.target, screen.page - 1);
        } else if (slot == PICK_NEXT && screen.page < BgmMenus.pages(screen.songs.size()) - 1) {
            openPick(player, screen.target, screen.page + 1);
        } else if (slot == PICK_CLEAR) {
            bgm().stopPreview(player);
            setSong(player, definition, screen.target, null);
        } else if (slot >= 0 && slot < BgmMenus.PER_PAGE) {
            List<Bgm.SongInfo> list = BgmMenus.page(screen.songs, screen.page);
            if (slot >= list.size()) return;
            Bgm.SongInfo song = list.get(slot);
            if (click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) {
                if (song.id().equals(bgm().previewing(player))) bgm().stopPreview(player); else bgm().preview(player, song.id());
                fillPick(screen, player);
            } else {
                bgm().stopPreview(player);
                setSong(player, definition, screen.target, song.id());
            }
        }
    }

    /** Puts a song in a slot (null: empties it); the boss songs stay together at the front. */
    private void setSong(Player player, DungeonDefinition definition, int target, String songId) {
        String route = definition.bgmRoute();
        List<String> boss = new ArrayList<>(definition.bgmBoss());
        if (target == 0) {
            route = songId;
        } else if (songId == null) {
            if (target - 1 < boss.size()) boss.remove(target - 1);
        } else if (target - 1 < boss.size()) {
            boss.set(target - 1, songId);
        } else {
            boss.add(songId);
        }
        registry.setBgm(definition, route, boss);
        DungeonDefinition updated = registry.find(definition.id()).orElse(definition);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.6f, songId == null ? 0.8f : 1.4f);
        warnBgm(player, updated);
        openBgm(player, updated);
    }

    /** The boss fight needs a song when the dungeon has music at all. */
    private void warnBgm(Player player, DungeonDefinition definition) {
        if (definition.bgmRoute() != null && definition.bgmBoss().isEmpty()) {
            player.sendMessage(Component.text("[エディター] BGM: ボス戦の曲が未設定です (最低1つ必要)。ボス戦になっても、道中の曲のままになります。", NamedTextColor.RED));
        }
    }

    /** Closing the list of songs ends the listen. */
    @EventHandler
    public void onBgmClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && event.getInventory().getHolder() instanceof BgmScreen screen && screen.picking) bgm().stopPreview(player);
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
