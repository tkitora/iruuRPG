package net.tkgon.mc.iruuRPG.dungeon;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import net.tkgon.mc.iruuRPG.mob.RpgMobDefinition;
import net.tkgon.mc.iruuRPG.mob.RpgMobRegistry;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The wall tool of the dungeon editor: groups of blocks that vanish together when a condition is met in a run
 * (enough kills, enough players gathered, or one chosen placed mob killed).
 *
 * Click a block = add it to the wall being edited (click it again = take it out); sneak-click two blocks = add every
 * block in the box between them; click the air = the list of walls (new wall, choose one to edit, its condition).
 */
final class DungeonWallEditor {

    /** At most this many blocks are added by one box. */
    static final int MAX_BOX = 1024;

    private static final class State {
        private int editing = -1;
        private Block corner;
    }

    private final DungeonRegistry registry;
    private final DungeonService service;
    private final RpgMobRegistry mobRegistry;
    private final Map<UUID, State> states = new HashMap<>();

    DungeonWallEditor(DungeonService service, RpgMobRegistry mobRegistry) {
        this.service = service;
        this.registry = service.registry();
        this.mobRegistry = mobRegistry;
    }

    void forget(Player player) {
        states.remove(player.getUniqueId());
    }

    private State state(Player player) {
        return states.computeIfAbsent(player.getUniqueId(), id -> new State());
    }

    /** The wall being edited, or -1 (also when it was deleted meanwhile). */
    private int editing(Player player, DungeonDefinition definition) {
        State state = state(player);
        if (state.editing >= registry.walls(definition.id()).size()) state.editing = -1;
        return state.editing;
    }

    // ---- the tool ---------------------------------------------------------------------------------------------------------

    /** A right click with the wall tool (clicked = null: the air). */
    void use(Player player, DungeonDefinition definition, Block clicked) {
        int editing = editing(player, definition);
        if (clicked == null || editing < 0) {
            if (clicked != null) note(player, "先に、編集する壁を選ぶ (または新しく作る)");
            openList(player, definition);
            return;
        }
        State state = state(player);
        if (player.isSneaking()) {
            if (state.corner == null || !state.corner.getWorld().equals(clicked.getWorld())) {
                state.corner = clicked;
                note(player, "角を 1 つ決めた。もう 1 つの角をしゃがんでクリック");
                return;
            }
            Block first = state.corner;
            state.corner = null;
            addBox(player, definition, editing, first, clicked);
            return;
        }
        DungeonWall.Pos pos = pos(clicked);
        int owner = registry.wallAt(definition.id(), pos);
        if (owner >= 0 && owner != editing) {
            // a block belongs to one wall only: it moves to the wall being edited
            registry.setWall(definition, owner, registry.walls(definition.id()).get(owner).toggle(pos));
        }
        DungeonWall wall = registry.walls(definition.id()).get(editing).toggle(pos);
        registry.setWall(definition, editing, wall);
        changed(player, definition, (wall.has(pos) ? "壁 #" + (editing + 1) + " にブロックを足した" : "壁 #" + (editing + 1) + " からブロックを外した")
                + " (" + wall.blocks().size() + " ブロック)" + (owner >= 0 && owner != editing ? " ← 壁 #" + (owner + 1) + " から移した" : ""));
    }

    private void addBox(Player player, DungeonDefinition definition, int editing, Block first, Block second) {
        World world = first.getWorld();
        int minX = Math.min(first.getX(), second.getX()), maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY()), maxY = Math.max(first.getY(), second.getY());
        int minZ = Math.min(first.getZ(), second.getZ()), maxZ = Math.max(first.getZ(), second.getZ());
        long volume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        if (volume > MAX_BOX) {
            note(player, "範囲が大きすぎる (" + volume + " / 最大 " + MAX_BOX + " ブロック)");
            return;
        }
        List<DungeonWall> walls = registry.walls(definition.id());
        List<DungeonWall.Pos> blocks = new ArrayList<>(walls.get(editing).blocks());
        int added = 0;
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (world.getBlockAt(x, y, z).getType().isAir()) continue;
                    DungeonWall.Pos pos = new DungeonWall.Pos(x, y, z);
                    if (blocks.contains(pos)) continue;
                    int owner = registry.wallAt(definition.id(), pos);
                    if (owner >= 0 && owner != editing) registry.setWall(definition, owner, registry.walls(definition.id()).get(owner).toggle(pos));
                    blocks.add(pos);
                    added++;
                }
            }
        }
        DungeonWall wall = registry.walls(definition.id()).get(editing).withBlocks(blocks);
        registry.setWall(definition, editing, wall);
        changed(player, definition, "壁 #" + (editing + 1) + " に " + added + " ブロックを足した (" + wall.blocks().size() + " ブロック)");
    }

    /** The delete tool on a block: takes it out of its wall. False when the block is in no wall. */
    boolean delete(Player player, DungeonDefinition definition, Block clicked) {
        DungeonWall.Pos pos = pos(clicked);
        int owner = registry.wallAt(definition.id(), pos);
        if (owner < 0) return false;
        DungeonWall wall = registry.walls(definition.id()).get(owner).toggle(pos);
        registry.setWall(definition, owner, wall);
        changed(player, definition, "壁 #" + (owner + 1) + " からブロックを外した (" + wall.blocks().size() + " ブロック)");
        return true;
    }

    private static DungeonWall.Pos pos(Block block) {
        return new DungeonWall.Pos(block.getX(), block.getY(), block.getZ());
    }

    private void changed(Player player, DungeonDefinition definition, String message) {
        service.refreshMarkers(definition.id());
        note(player, message);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.6f);
    }

    /** Short feedback as a subtitle (the action bar is the status display). */
    private static void note(Player player, String message) {
        player.showTitle(Title.title(Component.empty(), Component.text(message, NamedTextColor.GREEN),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1500), Duration.ofMillis(300))));
    }

    // ---- the list of walls -----------------------------------------------------------------------------------------------

    private static final int LIST_CLOSE = 45;
    private static final int LIST_STOP = 47;
    private static final int LIST_NEW = 49;
    private static final int WALLS_SHOWN = 45;

    /** The editor's menus of this tool. */
    static final class Menu implements MenuHolder {
        private final String dungeonId;
        private final Kind kind;
        private final int wall;
        private Inventory inventory;

        private Menu(String dungeonId, Kind kind, int wall) {
            this.dungeonId = dungeonId;
            this.kind = kind;
            this.wall = wall;
        }

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    private enum Kind { LIST, WALL, TARGET }

    void openList(Player player, DungeonDefinition definition) {
        Menu menu = new Menu(definition.id(), Kind.LIST, -1);
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text("消える壁"));
        List<DungeonWall> walls = registry.walls(definition.id());
        int editing = editing(player, definition);
        for (int index = 0; index < walls.size() && index < WALLS_SHOWN; index++) {
            DungeonWall wall = walls.get(index);
            List<Component> lore = new ArrayList<>();
            lore.add(text("条件: " + wall.describe(targetName(definition, wall.target())), NamedTextColor.WHITE));
            lore.add(text(wall.blocks().size() + " ブロック", NamedTextColor.GRAY));
            lore.add(text(index == editing ? "編集中 (クリックしたブロックがこの壁に入る)" : "クリック: 条件の設定・この壁を編集する",
                    index == editing ? NamedTextColor.GREEN : NamedTextColor.GRAY));
            ItemStack item = button(icon(wall.when()), "壁 #" + (index + 1), DungeonService.wallColor(index), lore);
            if (index == editing) glow(item);
            menu.inventory.setItem(index, item);
        }
        ItemStack frame = button(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 45; slot < 54; slot++) menu.inventory.setItem(slot, frame);
        menu.inventory.setItem(LIST_CLOSE, button(Material.ARROW, "閉じる", NamedTextColor.GRAY, List.of()));
        if (editing >= 0) {
            menu.inventory.setItem(LIST_STOP, button(Material.BARRIER, "壁の編集をやめる", NamedTextColor.GRAY,
                    List.of(text("ブロックをクリックしても、どの壁にも入らなくなる", NamedTextColor.GRAY))));
        }
        if (walls.size() < WALLS_SHOWN) {
            menu.inventory.setItem(LIST_NEW, button(Material.LIME_DYE, "新しい壁を作る", NamedTextColor.GREEN, List.of(
                    text("作ったあと、条件を決めて、ブロックをクリックで足す", NamedTextColor.GRAY),
                    text("しゃがんで 2 つの角をクリック: その箱の中のブロックをまとめて足す", NamedTextColor.GRAY))));
        }
        player.openInventory(menu.inventory);
    }

    // ---- one wall: its condition -----------------------------------------------------------------------------------------

    private static final int[] CONDITION_SLOTS = {10, 11, 12};
    private static final int VALUE = 14;
    private static final int EDIT = 16;
    private static final int BACK = 18;
    private static final int DELETE = 26;

    private void openWall(Player player, DungeonDefinition definition, int index) {
        List<DungeonWall> walls = registry.walls(definition.id());
        if (index < 0 || index >= walls.size()) {
            openList(player, definition);
            return;
        }
        DungeonWall wall = walls.get(index);
        Menu menu = new Menu(definition.id(), Kind.WALL, index);
        menu.inventory = Bukkit.createInventory(menu, 27, Component.text("壁 #" + (index + 1) + " の条件"));
        DungeonWall.Condition[] conditions = DungeonWall.Condition.values();
        for (int i = 0; i < conditions.length; i++) {
            DungeonWall.Condition condition = conditions[i];
            boolean selected = wall.when() == condition;
            ItemStack item = button(icon(condition), condition.label(), selected ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                    List.of(text(explain(condition), NamedTextColor.GRAY), text(selected ? "選んでいる" : "クリックで、これにする", selected ? NamedTextColor.GREEN : NamedTextColor.GRAY)));
            if (selected) glow(item);
            menu.inventory.setItem(CONDITION_SLOTS[i], item);
        }
        menu.inventory.setItem(VALUE, valueButton(definition, wall));
        menu.inventory.setItem(EDIT, button(Material.BRICKS, "この壁のブロックを置く", NamedTextColor.GREEN, List.of(
                text("今 " + wall.blocks().size() + " ブロック", NamedTextColor.WHITE),
                text("クリックで閉じて、この壁を編集中にする", NamedTextColor.GRAY),
                text("ブロックをクリック: 足す / もう一度: 外す", NamedTextColor.GRAY),
                text("しゃがんで 2 つの角をクリック: 箱の中をまとめて足す", NamedTextColor.GRAY))));
        menu.inventory.setItem(BACK, button(Material.ARROW, "壁の一覧へ", NamedTextColor.GRAY, List.of()));
        menu.inventory.setItem(DELETE, button(Material.LAVA_BUCKET, "この壁を消す", NamedTextColor.DARK_RED,
                List.of(text("シフト+クリックで消す (ブロックそのものは残る)", NamedTextColor.GRAY))));
        player.openInventory(menu.inventory);
    }

    private ItemStack valueButton(DungeonDefinition definition, DungeonWall wall) {
        return switch (wall.when()) {
            case KILLS -> button(Material.IRON_SWORD, "倒す数: " + wall.count() + " 体", NamedTextColor.GOLD, List.of(
                    text("ボス以外の敵を、合わせてこの数倒すと開く", NamedTextColor.GRAY),
                    text("置いた敵 (確率のものも含む): " + definition.spawns().size() + " 体", NamedTextColor.GRAY),
                    text("左クリック +1 / 右クリック -1 / シフトで ±5", NamedTextColor.YELLOW)));
            case GATHER -> button(Material.PLAYER_HEAD, "集まる人数: " + wall.count() + " 人", NamedTextColor.GOLD, List.of(
                    text("この人数が壁のそば (" + (int) DungeonWall.GATHER_RANGE + " ブロック) に立つと開く", NamedTextColor.GRAY),
                    text("中にいる人がそれより少ないときは、立てる人全員", NamedTextColor.GRAY),
                    text("1 回に入れる人数: " + definition.maxPlayers() + " 人", NamedTextColor.GRAY),
                    text("左クリック +1 / 右クリック -1", NamedTextColor.YELLOW)));
            case TARGET -> {
                List<Component> lore = new ArrayList<>();
                lore.add(text(wall.target() < 0 ? "まだ決めていない (このままだと開かない)" : "#" + (wall.target() + 1) + " " + targetName(definition, wall.target()),
                        wall.target() < 0 ? NamedTextColor.RED : NamedTextColor.WHITE));
                if (wall.target() >= 0 && wall.target() < definition.spawns().size() && !definition.spawns().get(wall.target()).always()) {
                    lore.add(text("出る確率 " + definition.spawns().get(wall.target()).chance() + "%: 出なかった回は開かない", NamedTextColor.AQUA));
                }
                lore.add(text("クリックで、置いたモブから選ぶ", NamedTextColor.YELLOW));
                yield button(Material.TARGET, "倒す 1 体", NamedTextColor.GOLD, lore);
            }
        };
    }

    // ---- one wall: choosing its target -----------------------------------------------------------------------------------

    private static final int TARGET_BACK = 49;

    private void openTarget(Player player, DungeonDefinition definition, int index) {
        Menu menu = new Menu(definition.id(), Kind.TARGET, index);
        menu.inventory = Bukkit.createInventory(menu, 54, Component.text("壁 #" + (index + 1) + ": 倒す 1 体を選ぶ"));
        DungeonWall wall = registry.walls(definition.id()).get(index);
        List<DungeonDefinition.MobSpawn> spawns = definition.spawns();
        for (int i = 0; i < spawns.size() && i < 45; i++) {
            DungeonDefinition.MobSpawn spawn = spawns.get(i);
            RpgMobDefinition mob = mobRegistry.find(spawn.mobId()).orElse(null);
            Material egg = mob == null ? null : Material.matchMaterial(mob.entityType().name() + "_SPAWN_EGG");
            List<Component> lore = new ArrayList<>();
            lore.add(text("id: " + spawn.mobId(), NamedTextColor.GRAY));
            lore.add(text(String.format(java.util.Locale.ROOT, "(%.1f, %.1f, %.1f)", spawn.x(), spawn.y(), spawn.z()), NamedTextColor.GRAY));
            if (spawn.mobId().equals(definition.boss())) lore.add(text("ボス (倒すとクリア)", NamedTextColor.RED));
            if (!spawn.always()) lore.add(text("出る確率 " + spawn.chance() + "%: 出なかった回は開かない", NamedTextColor.AQUA));
            ItemStack item = button(egg == null ? Material.PAPER : egg, "#" + (i + 1) + " " + (mob == null ? spawn.mobId() : mob.name()),
                    i == wall.target() ? NamedTextColor.GREEN : NamedTextColor.YELLOW, lore);
            if (i == wall.target()) glow(item);
            menu.inventory.setItem(i, item);
        }
        if (spawns.isEmpty()) menu.inventory.setItem(22, button(Material.GRAY_DYE, "まだモブを置いていない", NamedTextColor.GRAY, List.of()));
        menu.inventory.setItem(TARGET_BACK, button(Material.ARROW, "戻る", NamedTextColor.GRAY, List.of()));
        player.openInventory(menu.inventory);
    }

    // ---- clicks in the menus ---------------------------------------------------------------------------------------------

    void onClick(Player player, Menu menu, int slot, ClickType click) {
        DungeonDefinition definition = registry.find(menu.dungeonId).orElse(null);
        if (definition == null) {
            player.closeInventory();
            return;
        }
        List<DungeonWall> walls = registry.walls(definition.id());
        switch (menu.kind) {
            case LIST -> {
                if (slot == LIST_CLOSE) {
                    player.closeInventory();
                } else if (slot == LIST_STOP && editing(player, definition) >= 0) {
                    state(player).editing = -1;
                    openList(player, definition);
                } else if (slot == LIST_NEW && walls.size() < WALLS_SHOWN) {
                    registry.setWall(definition, walls.size(), new DungeonWall(DungeonWall.Condition.KILLS, 5, -1, List.of()));
                    state(player).editing = walls.size();
                    openWall(player, definition, walls.size());
                } else if (slot >= 0 && slot < walls.size() && slot < WALLS_SHOWN) {
                    openWall(player, definition, slot);
                }
            }
            case WALL -> {
                if (menu.wall >= walls.size()) {
                    openList(player, definition);
                    return;
                }
                DungeonWall wall = walls.get(menu.wall);
                for (int i = 0; i < CONDITION_SLOTS.length; i++) {
                    if (slot != CONDITION_SLOTS[i]) continue;
                    DungeonWall.Condition condition = DungeonWall.Condition.values()[i];
                    DungeonWall changed = wall.withWhen(condition);
                    // a sensible count when switching between kills and players
                    if (condition == DungeonWall.Condition.GATHER && wall.when() != condition) changed = changed.withCount(Math.min(wall.count(), definition.maxPlayers()));
                    save(player, definition, menu.wall, changed);
                    return;
                }
                if (slot == VALUE) {
                    switch (wall.when()) {
                        case KILLS -> save(player, definition, menu.wall, wall.withCount(wall.count() + step(click, 5)));
                        case GATHER -> save(player, definition, menu.wall, wall.withCount(Math.min(definition.maxPlayers(), wall.count() + step(click, 1))));
                        case TARGET -> openTarget(player, definition, menu.wall);
                    }
                } else if (slot == EDIT) {
                    state(player).editing = menu.wall;
                    player.closeInventory();
                    note(player, "壁 #" + (menu.wall + 1) + " を編集中: ブロックをクリックで足す");
                } else if (slot == BACK) {
                    openList(player, definition);
                } else if (slot == DELETE && click.isShiftClick()) {
                    registry.removeWall(definition, menu.wall);
                    State state = state(player);
                    if (state.editing == menu.wall) state.editing = -1;
                    else if (state.editing > menu.wall) state.editing--;
                    service.refreshMarkers(definition.id());
                    openList(player, definition);
                }
            }
            case TARGET -> {
                if (slot == TARGET_BACK) {
                    openWall(player, definition, menu.wall);
                } else if (slot >= 0 && slot < definition.spawns().size() && slot < 45 && menu.wall < walls.size()) {
                    registry.setWall(definition, menu.wall, walls.get(menu.wall).withTarget(slot));
                    service.refreshMarkers(definition.id());
                    openWall(player, definition, menu.wall);
                }
            }
        }
    }

    /** +1 / -1 by left / right click; the shift click moves by {@code big}. */
    private static int step(ClickType click, int big) {
        int size = click.isShiftClick() ? big : 1;
        return click.isRightClick() ? -size : size;
    }

    private void save(Player player, DungeonDefinition definition, int index, DungeonWall wall) {
        registry.setWall(definition, index, wall);
        service.refreshMarkers(definition.id());
        openWall(player, definition, index);
    }

    // ---- items ------------------------------------------------------------------------------------------------------------

    private String targetName(DungeonDefinition definition, int target) {
        if (target < 0 || target >= definition.spawns().size()) return "?";
        String mobId = definition.spawns().get(target).mobId();
        return mobRegistry.find(mobId).map(RpgMobDefinition::name).orElse(mobId);
    }

    private static Material icon(DungeonWall.Condition condition) {
        return switch (condition) {
            case KILLS -> Material.IRON_SWORD;
            case GATHER -> Material.PLAYER_HEAD;
            case TARGET -> Material.TARGET;
        };
    }

    private static String explain(DungeonWall.Condition condition) {
        return switch (condition) {
            case KILLS -> "ボス以外の敵を、決めた数だけ倒すと開く";
            case GATHER -> "決めた人数が、壁のそばに集まると開く";
            case TARGET -> "置いたモブのうち、選んだ 1 体を倒すと開く";
        };
    }

    private static void glow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
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
