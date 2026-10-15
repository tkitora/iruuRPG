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
 * Tools (slots 1-8; slot 9 keeps the menu item):
 * 1 mob (click a block, pick from the list; sneak = the last mob again), 2 boss (click a placed mob), 3 delete (click a
 * placed mob), 4 entry, 5 exit, 6 reward room, 7 reward chest, 8 done (click: back to building; sneak: save and go back).
 */
public final class DungeonEditor implements Listener {

    private enum Tool {
        MOB(Material.ZOMBIE_SPAWN_EGG, "モブを置く", NamedTextColor.GREEN,
                "ブロックをクリック → 一覧から選んで、その上に置く", "しゃがんでクリック: 前と同じモブをもう一度"),
        BOSS(Material.WITHER_SKELETON_SKULL, "ボスにする", NamedTextColor.RED,
                "置いたモブ (名札の下) をクリック → そのモブをボスにする", "このモブが倒れたらクリア"),
        DELETE(Material.BARRIER, "置いたモブを消す", NamedTextColor.DARK_RED,
                "置いたモブ (名札の下) をクリック → 消す", "ブロックをクリック: その近くのモブを消す"),
        ENTRY(Material.OAK_DOOR, "入口にする", NamedTextColor.GREEN,
                "ブロックをクリック → その上を入口にする", "向きは、いま向いている方向"),
        EXIT(Material.ENDER_EYE, "脱出口にする", NamedTextColor.AQUA,
                "ブロックをクリック → その上を脱出口にする", "ボスを倒すと通れる"),
        ROOM(Material.GOLD_BLOCK, "報酬部屋にする", NamedTextColor.GOLD,
                "ブロックをクリック → その上を報酬部屋にする", "クリアの5秒後に全員がここへ"),
        CHEST(Material.CHEST, "報酬の宝箱を置く", NamedTextColor.GOLD,
                "ブロックをクリック → そこに宝箱を置く", "宝箱をクリック: その宝箱を使う"),
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

    private static final class Session {
        private final String dungeonId;
        private final ItemStack[] savedHotbar;
        private String lastMob;
        private Location pending;
        private boolean asBoss;

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
        Tool[] tools = Tool.values();
        for (int slot = 0; slot < TOOL_SLOTS; slot++) player.getInventory().setItem(slot, toolItem(tools[slot]));
        player.getInventory().setHeldItemSlot(0);
        service.refreshMarkers(definition.id());
        player.sendMessage(Component.text("[エディター] 「" + definition.name() + "」のエディターモード。ホットバーの道具で設定する。"
                + "建築はエディターを終えてから (8番の道具、または /dungeon edit)。", NamedTextColor.YELLOW));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
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
            if (tool != Tool.BOSS && tool != Tool.DELETE) hint(player, "ブロックをクリックしてください。");
            return;
        }
        BlockFace face = event.getBlockFace();
        Location above = clicked.getRelative(face).getLocation().add(0.5, 0.0, 0.5);
        switch (tool) {
            case MOB -> {
                session.pending = above;
                if (player.isSneaking() && session.lastMob != null) {
                    place(player, session, session.lastMob);
                } else {
                    openPicker(player, session);
                }
            }
            case BOSS -> hint(player, "置いたモブ (名札の下) をクリックしてください。");
            case DELETE -> deleteNear(player, definition, clicked.getLocation().add(0.5, 1.0, 0.5));
            case ENTRY -> {
                above.setYaw(player.getLocation().getYaw());
                registry.setEntry(definition, above);
                done(player, definition, "ここを入口にした。");
            }
            case EXIT -> {
                registry.setExit(definition, above);
                done(player, definition, "ここを脱出口にした。");
            }
            case ROOM -> {
                above.setYaw(player.getLocation().getYaw());
                registry.setRewardRoom(definition, above);
                done(player, definition, "ここを報酬部屋にした。");
            }
            case CHEST -> {
                Block chest = clicked.getType() == Material.CHEST ? clicked : clicked.getRelative(face);
                chest.setType(Material.CHEST);
                registry.setChest(definition, chest.getLocation());
                done(player, definition, "ここに報酬の宝箱を置いた。");
            }
            default -> {
            }
        }
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
        } else if (tool == Tool.BOSS) {
            registry.setBoss(definition, spawn.mobId());
            done(player, definition, spawn.mobId() + " をボスにした。");
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

    // ---- the mob list -------------------------------------------------------------------------------------------------

    private static final int BOSS_TOGGLE = 49;
    private static final int CANCEL = 45;

    private static final class Picker implements MenuHolder {
        private final List<String> ids = new ArrayList<>();
        private Inventory inventory;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private void openPicker(Player player, Session session) {
        Picker picker = new Picker();
        Inventory inventory = Bukkit.createInventory(picker, 54, Component.text("置くモブを選ぶ"));
        picker.inventory = inventory;
        List<RpgMobDefinition> mobs = new ArrayList<>(mobRegistry.all().values());
        mobs.sort((left, right) -> left.level() != right.level() ? Integer.compare(left.level(), right.level()) : left.id().compareTo(right.id()));
        for (RpgMobDefinition mob : mobs) {
            if (picker.ids.size() >= 45) break;
            Material egg = Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
            List<Component> lore = new ArrayList<>();
            lore.add(text("id: " + mob.id(), NamedTextColor.GRAY));
            lore.add(text("Lv " + mob.level() + " / HP " + Math.round(mob.stats().get(StatType.MAX_HP)), NamedTextColor.WHITE));
            if (mob.id().equals(session.lastMob)) lore.add(text("(前に置いたモブ)", NamedTextColor.YELLOW));
            inventory.setItem(picker.ids.size(), button(egg == null ? Material.PAPER : egg, mob.name(), NamedTextColor.GREEN, lore));
            picker.ids.add(mob.id());
        }
        renderBossToggle(inventory, session);
        inventory.setItem(CANCEL, button(Material.ARROW, "やめる", NamedTextColor.GRAY, List.of()));
        player.openInventory(inventory);
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
        } else if (slot >= 0 && slot < picker.ids.size()) {
            player.closeInventory();
            place(player, session, picker.ids.get(slot));
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
