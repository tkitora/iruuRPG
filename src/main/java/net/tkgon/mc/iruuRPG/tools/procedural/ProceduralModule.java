package net.tkgon.mc.iruuRPG.tools.procedural;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * The procedural tree tool (admins). {@code /proceduraltool} gives a placement tool (a sapling); clicking a block with it opens the settings menu ({@link ProceduralMenu}) for a tree
 * standing on that block. The menu can turn the preview on ({@link TreePreview}: the tree in its blocks, to that player alone, following the settings), and starts the
 * generation: the skeleton of the tree is grown and turned into blocks off the main thread ({@link TreeGenerator}), and the blocks are put in the world through
 * FastAsyncWorldEdit in the player's own edit session ({@link TreeWorldEdit}), so {@code //undo} and {@code //redo} take the whole tree back and put it again. Every part of
 * the tree is the block chosen for it ({@link TreePart}: in the menu or by command, else the config's). Only air and plants are replaced (not terrain), unless the config says
 * otherwise. Like the brush this is a tool, not game logic: it lives in this package, has its own command, item and listeners, and the rest of the plugin only starts it.
 */
public final class ProceduralModule implements Listener {

    static final String PREFIX = "[木] ";

    private final JavaPlugin plugin;
    private final NamespacedKey toolKey;
    /** (t246) The mark of the part wand. */
    private final NamespacedKey wandKey;
    private final TreeStore store;
    private final ProceduralMenu menu;
    /** (t286) The list of presets (loading, and choosing the placing tool's). */
    private final PresetMenu presets;
    /** (t286) The mark of the placing tool, and the presets it puts with their weights ("name=weight;..."). */
    private final NamespacedKey scatterKey;
    private final NamespacedKey scatterListKey;
    private final TreePreview preview;
    private final boolean worldEdit;
    private final Map<UUID, TreeSession> sessions = new HashMap<>();

    /** Starts the tree tool: registers its listeners and its command. Never throws: a failure is logged and the tool is simply not there. */
    public static ProceduralModule start(JavaPlugin plugin) {
        try {
            ProceduralModule module = new ProceduralModule(plugin);
            plugin.getServer().getPluginManager().registerEvents(module, plugin);
            plugin.getServer().getPluginManager().registerEvents(module.menu, plugin);
            plugin.getServer().getPluginManager().registerEvents(module.presets, plugin);
            org.bukkit.command.PluginCommand command = plugin.getCommand("proceduraltool");
            if (command != null) {
                ProceduralCommand handler = new ProceduralCommand(module);
                command.setExecutor(handler);
                command.setTabCompleter(handler);
            }
            return module;
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().warning("Tree tool not started: " + exception);
            return null;
        }
    }

    private ProceduralModule(JavaPlugin plugin) {
        this.plugin = plugin;
        this.toolKey = new NamespacedKey(plugin, "tree_tool");
        this.wandKey = new NamespacedKey(plugin, "house_wand");
        this.store = new TreeStore(plugin);
        store.installSamples();
        this.menu = new ProceduralMenu(this);
        this.presets = new PresetMenu(this);
        this.scatterKey = new NamespacedKey(plugin, "scatter_tool");
        this.scatterListKey = new NamespacedKey(plugin, "scatter_list");
        this.preview = new TreePreview(this);
        this.worldEdit = plugin.getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || plugin.getServer().getPluginManager().isPluginEnabled("WorldEdit");
        if (!worldEdit) plugin.getLogger().warning("Tree tool: WorldEdit / FastAsyncWorldEdit is not there, trees cannot be undone.");
    }

    /** The plugin stops: every preview is taken back and no task is left. */
    public void stop() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (sessions.containsKey(player.getUniqueId())) preview.drop(player);
        }
    }

    JavaPlugin plugin() {
        return plugin;
    }

    TreeStore store() {
        return store;
    }

    ProceduralMenu menu() {
        return menu;
    }

    TreePreview preview() {
        return preview;
    }

    PresetMenu presets() {
        return presets;
    }

    TreeSession session(Player player) {
        return sessions.computeIfAbsent(player.getUniqueId(), ignored -> new TreeSession());
    }

    /** A setting or the place changed: the preview, when it is on, follows after its wait. */
    void changed(Player player) {
        syncBase(session(player));
        preview.touch(player);
    }

    /** The place of the object: above the block clicked (a tree, a rock), or on the face that was clicked (a spike or a crystal, which grow out of it toward the player). */
    private static void syncBase(TreeSession session) {
        if (session.settings.kind().web()) {
            // a web stands on the first of its points
            session.base = session.points.isEmpty() || session.pointsWorld == null ? null
                    : new Location(session.pointsWorld, session.points.get(0)[0], session.points.get(0)[1], session.points.get(0)[2]);
            return;
        }
        if (session.clicked == null) return;
        boolean pointing = session.settings.kind() == ProcKind.SPIKE || session.settings.kind() == ProcKind.CRYSTAL;
        BlockFace face = pointing ? session.face : BlockFace.UP;
        session.base = session.clicked.clone().add(face.getModX(), face.getModY(), face.getModZ());
    }

    // ---- config ---------------------------------------------------------------------------------------------------------

    /** The block of a part when nothing is chosen in the settings: {@code treetool.materials.<part>} of the config, else the part's default. */
    Material material(TreePart part) {
        TreePart base = part.shade() > 0 ? TreePart.LEAF : part.blockOf();
        String name = plugin.getConfig().getString("treetool.materials." + base.key(), base.defaultMaterial().name());
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null && material.isBlock() && !material.isAir() ? material : base.defaultMaterial();
    }

    /**
     * The block of a part for these settings: a shade of the leaf gradient when the leaves have one, else the block chosen for the part (menu or command), else
     * the config's. A name with states ({@code minecraft:oak_log[axis=x]}) gives its material here; the states are in {@link #blockData}.
     */
    Material material(TreePart part, TreeSettings settings) {
        String chosen = chosenName(part, settings);
        Material material = TreeSettings.materialOf(chosen);
        return material != null && material.isBlock() && !material.isAir() ? material : material(part);
    }

    /** The name chosen for the part in the settings (a shade of the gradient for a leaf), or null. */
    private static String chosenName(TreePart part, TreeSettings settings) {
        // (t252, t258: the top half of a slab branch, the other ways of a stair, are the block of their first part)
        part = part.blockOf();
        int shade = part.shade();
        List<String> gradient = settings.leafGradient();
        if (shade >= 0 && !gradient.isEmpty()) return gradient.get(Math.min(shade, gradient.size() - 1));
        return settings.block(shade > 0 ? TreePart.LEAF : part);
    }

    /**
     * The block a part is put in the world (and shown in the preview) as: with the states chosen ({@code [axis=x]} and so on; states the block does not have
     * leave it plain); leaves are persistent unless the states say otherwise, so a tree far from its logs does not decay.
     */
    BlockData blockData(TreePart part, TreeSettings settings) {
        String chosen = chosenName(part, settings);
        Material material = material(part, settings);
        BlockData data = null;
        if (chosen != null && chosen.indexOf('[') > 0 && material == TreeSettings.materialOf(chosen)) {
            try {
                data = Bukkit.createBlockData(chosen);
            } catch (IllegalArgumentException exception) {
                data = null;
            }
        }
        if (data == null) {
            data = material.createBlockData();
            if (data instanceof org.bukkit.block.data.type.Leaves leaves) leaves.setPersistent(true);
        }
        return data;
    }

    /** Keeps the settings inside the height and width the config allows. */
    void limit(TreeSettings settings) {
        int maxHeight = plugin.getConfig().getInt("treetool.max-height", 80);
        int maxWidth = plugin.getConfig().getInt("treetool.max-width", 60);
        if (settings.height() > maxHeight) settings.set(TreeSettings.Key.HEIGHT, maxHeight);
        if (settings.width() > maxWidth) settings.set(TreeSettings.Key.WIDTH, maxWidth);
    }

    // ---- the tool item ----------------------------------------------------------------------------------------------------

    ItemStack toolItem() {
        ItemStack item = new ItemStack(Material.OAK_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("プロシージャルツール", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                text("ブロックを右クリック: そこに置くもの (木・岩・スパイク・結晶) の設定画面を開く。クモの巣のときは、クリックした面の先に点を置く (Shift+クリックで最後の点を消す)", NamedTextColor.GRAY),
                text("遠くのブロックも、見てクリックすれば同じ (t246。届くのは treetool.reach ブロック、初期値 96)", NamedTextColor.GRAY),
                text("左クリック: 置く場所はそのままで設定画面を開く (まだ場所がなければ、右クリックと同じ。クモの巣は左右どちらも点を置く)", NamedTextColor.GRAY),
                text("Shift+空中を右クリック (見える所にブロックがないときは、ただの右クリック): 前回の場所の設定画面を開く", NamedTextColor.GRAY),
                text("設定画面の「生成」で、部位ごとに選んだブロックで作る", NamedTextColor.GRAY),
                text("設定画面の「プレビュー」で、あなたにだけ見える試し置きを確かめられる", NamedTextColor.GRAY),
                text("作った木は //undo で戻せる", NamedTextColor.GRAY),
                text("/proceduraltool off でこのツールを消す", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    boolean isTool(ItemStack item) {
        if (item == null || item.getType() != Material.OAK_SAPLING || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(toolKey, PersistentDataType.BYTE);
    }

    void give(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTool(item)) {
                player.sendMessage(PREFIX + "もう持っています。ブロックを右クリックすると、そこに置く設定画面が開きます (左クリックは、場所はそのままで設定画面)。");
                return;
            }
        }
        player.getInventory().addItem(toolItem()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "プロシージャルツールを渡しました。ブロックを右クリックすると、そこに置くもの (木・岩・スパイク・結晶) の設定画面が開きます。左クリックは、置く場所を変えずに設定画面を開きます。");
    }

    void take(Player player) {
        preview.off(player, false);
        int removed = 0;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isTool(contents[slot])) {
                player.getInventory().setItem(slot, null);
                removed++;
            }
        }
        player.sendMessage(PREFIX + (removed > 0 ? "ツールを片づけました。" : "ツールは持っていません。"));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (isScatter(event.getItem())) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("iruurpg.admin")) {
                event.getPlayer().sendMessage(PREFIX + "この配置ツールは管理者だけが使えます。");
                return;
            }
            scatter(event.getPlayer(), event.getItem(), event.getAction(), event.getClickedBlock(), event.getBlockFace());
            return;
        }
        if (isWand(event.getItem())) {
            event.setCancelled(true);
            if (!event.getPlayer().hasPermission("iruurpg.admin")) {
                event.getPlayer().sendMessage(PREFIX + "この杖は管理者だけが使えます。");
                return;
            }
            wand(event.getPlayer(), session(event.getPlayer()), event.getAction());
            return;
        }
        if (!isTool(event.getItem())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.hasPermission("iruurpg.admin")) {
            player.sendMessage(PREFIX + "このツールは管理者だけが使えます。");
            return;
        }
        TreeSession session = session(player);
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        if (menuOnly(left, session.settings.kind().web(), session.base != null && player.getWorld().equals(session.base.getWorld()))) {
            // (t263: a left click opens the menu of the place there is and leaves the place as it was; the right click chooses the place)
            menu.open(player);
            return;
        }
        Block target = event.getClickedBlock();
        BlockFace targetFace = event.getBlockFace();
        boolean air = action == Action.RIGHT_CLICK_AIR || action == Action.LEFT_CLICK_AIR;
        if (air && !(action == Action.RIGHT_CLICK_AIR && player.isSneaking())) {
            // (t246: a click in the air is a click on the block in sight, however far within the reach)
            HousePointer.Hit hit = sight(player, session, false);
            if (hit != null) {
                target = player.getWorld().getBlockAt(hit.x(), hit.y(), hit.z());
                targetFace = target.getFace(player.getWorld().getBlockAt(hit.beforeX(), hit.beforeY(), hit.beforeZ()));
                air = false;
            }
        }
        if (session.settings.kind().web() && !air) {
            webPoint(player, session, target, targetFace);
            return;
        }
        if (!air) {
            Block clicked = target;
            if (clicked == null) return;
            session.clicked = clicked.getLocation();
            session.face = targetFace == null || targetFace == BlockFace.SELF ? BlockFace.UP : targetFace;
            syncBase(session);
            if (session.base != null) {
                org.bukkit.util.Vector toEye = player.getEyeLocation().toVector().subtract(session.base.toVector().add(new org.bukkit.util.Vector(0.5, 0.5, 0.5)));
                session.aim = toEye.lengthSquared() < 1.0E-6 ? null : toEye.normalize();
            }
            changed(player);
            menu.open(player);
        } else if (action == Action.RIGHT_CLICK_AIR) {
            if (session.base == null && !session.settings.kind().web()) {
                player.sendMessage(PREFIX + "置きたい場所のブロックをクリックしてください (遠くても見えていれば届きます)。");
                return;
            }
            menu.open(player);
        }
    }

    /**
     * Whether a click with the tool only opens the menu, the place staying as it was: a left click once there is a place in this world (t263). A web is left out (a
     * click there puts a point and opens no menu), and a left click before there is a place chooses it, as a right click does. Pure, for the tests.
     */
    static boolean menuOnly(boolean left, boolean web, boolean placed) {
        return left && !web && placed;
    }

    @EventHandler
    public void onHeld(org.bukkit.event.player.PlayerItemHeldEvent event) {
        refreshHighlightLater(event.getPlayer());
    }

    @EventHandler
    public void onSwap(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        refreshHighlightLater(event.getPlayer());
    }

    // ---- the placing tool (t286) ------------------------------------------------------------------------------------------------

    ItemStack scatterItem(Map<String, Integer> chosen) {
        ItemStack item = new ItemStack(Material.BIRCH_SAPLING);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("配置ツール", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(scatterKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(scatterListKey, PersistentDataType.STRING, encode(chosen));
        item.setItemMeta(meta);
        describeScatter(item, chosen);
        return item;
    }

    private void describeScatter(ItemStack item, Map<String, Integer> chosen) {
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(text("右クリック: 下のプリセットのどれかを、重みの割合で、クリックしたブロックの上にランダムなシードで置く (//undo で戻せる)", NamedTextColor.GRAY));
        lore.add(text("遠くのブロックも、見てクリックすれば同じ", NamedTextColor.GRAY));
        lore.add(text("左クリック: プリセットと重みを選ぶ画面", NamedTextColor.GRAY));
        int total = 0;
        for (int weight : chosen.values()) total += weight;
        for (Map.Entry<String, Integer> entry : chosen.entrySet()) {
            lore.add(text("・" + entry.getKey() + " × " + entry.getValue() + String.format(java.util.Locale.ROOT, " (%.0f%%)", 100.0 * entry.getValue() / Math.max(1, total)), NamedTextColor.WHITE));
        }
        if (chosen.isEmpty()) lore.add(text("(まだプリセットを選んでいない)", NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        item.setItemMeta(meta);
    }

    boolean isScatter(ItemStack item) {
        if (item == null || item.getType() != Material.BIRCH_SAPLING || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(scatterKey, PersistentDataType.BYTE);
    }

    /** The presets a placing tool puts, with their weights. */
    Map<String, Integer> scatterList(ItemStack item) {
        String text = item.getItemMeta().getPersistentDataContainer().get(scatterListKey, PersistentDataType.STRING);
        return decode(text);
    }

    static String encode(Map<String, Integer> chosen) {
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, Integer> entry : chosen.entrySet()) {
            if (!out.isEmpty()) out.append(';');
            out.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return out.toString();
    }

    static Map<String, Integer> decode(String text) {
        Map<String, Integer> out = new java.util.LinkedHashMap<>();
        if (text == null || text.isEmpty()) return out;
        for (String pair : text.split(";")) {
            int equals = pair.lastIndexOf('=');
            if (equals <= 0) continue;
            try {
                int weight = Integer.parseInt(pair.substring(equals + 1));
                if (weight > 0) out.put(pair.substring(0, equals), weight);
            } catch (NumberFormatException ignored) {
                // (a broken entry is left out)
            }
        }
        return out;
    }

    /** Gives a placing tool of these presets, or writes them into the one in hand. */
    void giveScatter(Player player, Map<String, Integer> chosen) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (isScatter(hand)) {
            ItemMeta meta = hand.getItemMeta();
            meta.getPersistentDataContainer().set(scatterListKey, PersistentDataType.STRING, encode(chosen));
            hand.setItemMeta(meta);
            describeScatter(hand, chosen);
            player.sendMessage(PREFIX + "手に持っている配置ツールの、プリセットを変えました (" + chosen.size() + " 種類)。");
            return;
        }
        player.getInventory().addItem(scatterItem(chosen)).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "配置ツールを渡しました (" + chosen.size() + " 種類)。右クリックで置く / 左クリックでプリセットを選ぶ。");
    }

    /** One of a placing tool's presets, by weight (null when there is none). Pure, for the tests. */
    static String pick(Map<String, Integer> chosen, Random random) {
        int total = 0;
        for (int weight : chosen.values()) total += Math.max(0, weight);
        if (total <= 0) return null;
        int roll = random.nextInt(total);
        for (Map.Entry<String, Integer> entry : chosen.entrySet()) {
            roll -= Math.max(0, entry.getValue());
            if (roll < 0) return entry.getKey();
        }
        return null;
    }

    /** A click with the placing tool: the left one opens the choosing of its presets, the right one puts one of them where it is clicked (or the block in sight). */
    private void scatter(Player player, ItemStack item, Action action, Block clicked, BlockFace face) {
        TreeSession session = session(player);
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            session.scatter.clear();
            session.scatter.putAll(scatterList(item));
            presets.open(player, PresetMenu.Mode.SCATTER);
            return;
        }
        Block target = clicked;
        BlockFace targetFace = face;
        if (target == null) {
            HousePointer.Hit hit = sight(player, session, false);
            if (hit == null) {
                player.sendMessage(PREFIX + "置く場所のブロックを見て、右クリックしてください。");
                return;
            }
            target = player.getWorld().getBlockAt(hit.x(), hit.y(), hit.z());
            targetFace = target.getFace(player.getWorld().getBlockAt(hit.beforeX(), hit.beforeY(), hit.beforeZ()));
        }
        if (session.busy) {
            player.sendMessage(PREFIX + "前のものをまだ作っています。終わるまで待ってください。");
            return;
        }
        Random random = new Random();
        String name = pick(scatterList(item), random);
        if (name == null) {
            player.sendMessage(PREFIX + "配置ツールにプリセットがありません。左クリックで選んでください。");
            return;
        }
        TreeSettings settings = store.load(name);
        if (settings == null) {
            player.sendMessage(PREFIX + "プリセットが読めません (消された?): " + name);
            return;
        }
        if (settings.kind().web()) {
            player.sendMessage(PREFIX + "クモの巣は配置ツールでは置けません: " + name);
            return;
        }
        limit(settings);
        boolean pointing = settings.kind() == ProcKind.SPIKE || settings.kind() == ProcKind.CRYSTAL;
        BlockFace grow = pointing && targetFace != null && targetFace != BlockFace.SELF ? targetFace : BlockFace.UP;
        Location base = target.getLocation().add(grow.getModX(), grow.getModY(), grow.getModZ());
        org.bukkit.util.Vector toEye = player.getEyeLocation().toVector().subtract(base.toVector().add(new org.bukkit.util.Vector(0.5, 0.5, 0.5)));
        org.bukkit.util.Vector aim = toEye.lengthSquared() < 1.0E-6 ? null : toEye.normalize();
        long seed = random.nextLong() & Long.MAX_VALUE;
        player.sendMessage(PREFIX + "配置: " + name + " (" + settings.kind().label() + (settings.kind().tree() ? ", " + algorithmLabel(settings.algorithm()) : "") + ")");
        build(player, session, settings, base, aim, seed, null);
    }

    // ---- the block in sight, and the part wand (t246) ------------------------------------------------------------------------

    /** How far the tool and the wand reach: the block in sight up to this many blocks ({@code treetool.reach}). */
    double reach() {
        return Math.max(8.0, Math.min(256.0, plugin.getConfig().getDouble("treetool.reach", 96.0)));
    }

    /** The block the player looks at within the reach (with {@code preview}, the blocks of the preview they are shown too), or null. */
    HousePointer.Hit sight(Player player, TreeSession session, boolean preview) {
        Location eye = player.getEyeLocation();
        World world = player.getWorld();
        org.bukkit.util.Vector direction = eye.getDirection();
        boolean shown = preview && session.preview && world.equals(session.shownWorld);
        return HousePointer.ray(eye.getX(), eye.getY(), eye.getZ(), direction.getX(), direction.getY(), direction.getZ(), reach(),
                shown ? (x, y, z) -> TreePreview.shows(session, x, y, z) : null,
                (x, y, z) -> y >= world.getMinHeight() && y < world.getMaxHeight() && world.isChunkLoaded(x >> 4, z >> 4) && solid(world.getBlockAt(x, y, z)));
    }

    private static boolean solid(Block block) {
        return block.getType().isSolid() || block.isLiquid();
    }

    ItemStack wandItem() {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("パーツの杖", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                text("家の土台のプレビューや地面を見て (遠くても) 使う。持っている間、選んだパーツはステンドグラスで見える", NamedTextColor.GRAY),
                text("左クリック: 指したパーツを選ぶ (土台を指すと土台)", NamedTextColor.GRAY),
                text("右クリック: 選んだパーツを、指した所へ動かす (いちばん近い面に付く)", NamedTextColor.GRAY),
                text("Shift+右クリック: 選んだパーツと同じものを、指した所に足す (選んでいなければ棟)", NamedTextColor.GRAY),
                text("Shift+左クリック: 選んだパーツを、指した所まで引き伸ばす (土台なら土台の幅と奥行き)", NamedTextColor.GRAY),
                text("/proceduraltool wand でもらえる", NamedTextColor.DARK_GRAY)));
        meta.getPersistentDataContainer().set(wandKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(wandKey, PersistentDataType.BYTE);
    }

    void giveWand(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isWand(item)) {
                player.sendMessage(PREFIX + "パーツの杖はもう持っています。");
                return;
            }
        }
        player.getInventory().addItem(wandItem()).values().forEach(left -> player.getWorld().dropItem(player.getLocation(), left));
        player.sendMessage(PREFIX + "パーツの杖を渡しました。家のプレビューを見て、左クリックで選ぶ / 右クリックで動かす / Shift+右クリックで足す / Shift+左クリックで引き伸ばす。");
    }

    /** The part of the house shown in glass now: the one chosen, while the player holds the wand or has the parts page open; -1 none. */
    int highlight(Player player, TreeSession session) {
        if (session.settings.kind() != ProcKind.HOUSE) return -1;
        int index = session.module;
        if (index < 0 || index >= session.settings.modules().size()) return -1;
        return isWand(player.getInventory().getItemInMainHand()) || menu.onParts(player) ? index : -1;
    }

    /** The glass follows the part chosen (next tick, once the item in hand or the page open has changed). */
    void refreshHighlightLater(Player player) {
        TreeSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.preview || session.settings.kind() != ProcKind.HOUSE) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && session.preview && highlight(player, session) != session.highlighted) preview.touch(player);
        });
    }

    /** The point {@code (x, y, z)} of the world in the house's own coordinates ({@code a}, height over its ground floor, {@code b}). */
    private static double[] local(TreeSession session, TreeSettings s, int x, int y, int z) {
        HouseShape.Placement placement = HouseShape.Placement.of(session.aim, s.getInt(TreeSettings.Key.HOUSE_ROTATION), s.getInt(TreeSettings.Key.HOUSE_WIDTH),
                s.getInt(TreeSettings.Key.HOUSE_DEPTH));
        int[] ab = placement.local(x - session.base.getBlockX(), z - session.base.getBlockZ());
        return new double[]{ab[0], y - session.base.getBlockY() - s.getInt(TreeSettings.Key.HOUSE_RAISE), ab[1]};
    }

    /** A click with the part wand: choose (left), move (right), add (Shift+right) or stretch (Shift+left) at the place in sight. */
    private void wand(Player player, TreeSession session, Action action) {
        TreeSettings s = session.settings;
        if (s.kind() != ProcKind.HOUSE || session.base == null || session.base.getWorld() == null || !session.base.getWorld().equals(player.getWorld())) {
            player.sendMessage(PREFIX + "パーツの杖は、家の土台を置いてから使います (ツールでブロックをクリックして、作るものを「家の土台」に)。");
            return;
        }
        if (!session.preview) {
            preview.on(player);
            player.sendMessage(PREFIX + "家が見えるように、プレビューをオンにしました。もう一度クリックしてください。");
            return;
        }
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean sneak = player.isSneaking();
        HousePointer.Hit hit = sight(player, session, true);
        if (hit == null) {
            player.sendMessage(PREFIX + "届く所 (" + (int) reach() + " ブロック) に、家も地面も見えません。");
            return;
        }
        List<HouseModule> modules = s.modules();
        if (session.module >= modules.size()) session.module = modules.size() - 1;
        if (left && !sneak) {
            choose(player, session, hit);
            return;
        }
        double[] at = local(session, s, hit.beforeX(), hit.beforeY(), hit.beforeZ());
        HouseShape.House house = new HouseShape.House(s);
        HouseModule chosen = session.module >= 0 ? modules.get(session.module) : null;
        String done;
        if (left) {
            if (chosen == null) {
                HousePointer.stretchBase(s, at[0], at[2]);
                done = "土台を 幅 " + s.getInt(TreeSettings.Key.HOUSE_WIDTH) + " / 奥行き " + s.getInt(TreeSettings.Key.HOUSE_DEPTH) + " にしました";
            } else {
                HousePointer.stretch(house, chosen, at[0], at[1], at[2]);
                done = (session.module + 1) + ". " + chosen.type.label + " を 幅 " + chosen.width + " / 奥行き " + chosen.depth + " にしました";
            }
        } else if (sneak) {
            if (modules.size() >= TreeSettings.MAX_MODULES) {
                player.sendMessage(PREFIX + "パーツは " + TreeSettings.MAX_MODULES + " 個までです。");
                return;
            }
            HouseModule made;
            if (chosen != null) {
                made = chosen.copy();
                made.on = true;
            } else {
                // (nothing chosen: a volume as high as the house, the block a plan of several blocks is made of)
                made = new HouseModule(HouseModule.Type.VOLUME);
                made.height = s.getInt(TreeSettings.Key.HOUSE_FLOORS);
            }
            HousePointer.put(house, made, at[0], at[1], at[2]);
            modules.add(made);
            session.module = modules.size() - 1;
            done = "足しました: " + modules.size() + ". " + made.type.label + " (" + HouseModule.sideLabel(made.side) + " / 横 " + made.along + " / 壁から " + made.offset + ")";
        } else {
            if (chosen == null) {
                player.sendMessage(PREFIX + "動かすパーツを選んでください (杖でパーツを指して左クリック、または追加パーツのページ)。");
                return;
            }
            HousePointer.put(house, chosen, at[0], at[1], at[2]);
            done = "動かしました: " + (session.module + 1) + ". " + chosen.type.label + " (" + HouseModule.sideLabel(chosen.side) + " / 横 " + chosen.along + " / 壁から "
                    + chosen.offset + (chosen.type.usesLevel() ? " / " + chosen.level + " 階から" : "") + ")";
        }
        changed(player);
        menu.refresh(player);
        player.sendMessage(PREFIX + done + "。");
    }

    /** Chooses the part of the preview's block in sight (its base: the base). */
    private void choose(Player player, TreeSession session, HousePointer.Hit hit) {
        TreeSession.HouseView view = session.view;
        if (!hit.preview() || view == null || !view.world().equals(player.getWorld())) {
            player.sendMessage(PREFIX + "選ぶには、プレビューの家を見てクリックしてください。");
            return;
        }
        int[] ab = view.placement().local(hit.x() - view.x(), hit.z() - view.z());
        int y = hit.y() - view.y();
        Integer owner = view.owners().get(TreeVoxelizer.pack(ab[0], y, ab[1]));
        // (a house turned by other than a right angle: a block of the house next to the one shown)
        for (int da = -1; da <= 1 && owner == null; da++) {
            for (int db = -1; db <= 1 && owner == null; db++) owner = view.owners().get(TreeVoxelizer.pack(ab[0] + da, y, ab[1] + db));
        }
        List<HouseModule> modules = session.settings.modules();
        if (owner == null || owner >= modules.size()) {
            session.module = -1;
            player.sendMessage(PREFIX + "土台を選びました (Shift+左クリックで土台の大きさ、Shift+右クリックで棟を足す)。");
        } else {
            session.module = owner;
            HouseModule chosen = modules.get(owner);
            player.sendMessage(PREFIX + "選びました: " + (owner + 1) + ". " + chosen.type.label + " (右クリックで動かす / Shift+左クリックで引き伸ばす / Shift+右クリックで同じものを足す)。");
        }
        menu.refresh(player);
        refreshHighlightLater(player);
    }


    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        preview.drop(event.getPlayer());
        sessions.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        TreeSession session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null && session.preview) preview.off(event.getPlayer(), true);
    }

    // ---- the points of a web --------------------------------------------------------------------------------------------------

    /**
     * A click on a block with the tool while a web is being made: the point is the air next to the face that was clicked (sneaking takes the last point away instead). The
     * points are shown to the player as sparks for a moment; the menu opens with a right click in the air.
     */
    private void webPoint(Player player, TreeSession session, Block clicked, BlockFace face) {
        if (player.isSneaking()) {
            if (session.points.isEmpty()) {
                player.sendMessage(PREFIX + "消す点がありません。");
                return;
            }
            int[] removed = session.points.remove(session.points.size() - 1);
            changed(player);
            markPoints(player, session);
            player.sendMessage(PREFIX + "最後の点を消しました (" + removed[0] + ", " + removed[1] + ", " + removed[2] + ")。残り " + session.points.size() + " 点。");
            return;
        }
        if (clicked == null) return;
        Block at = clicked.getRelative(face == null || face == BlockFace.SELF ? BlockFace.UP : face);
        if (!at.getType().isAir()) {
            player.sendMessage(PREFIX + "その面の先が空気ではないので、点にできません。");
            return;
        }
        if (session.pointsWorld != null && !session.pointsWorld.equals(at.getWorld())) session.points.clear();
        int max = plugin.getConfig().getInt("treetool.web-max-points", 16);
        if (session.points.size() >= max) {
            player.sendMessage(PREFIX + "点は " + max + " 個までです (Shift+クリックで最後の点を消せます)。");
            return;
        }
        for (int[] point : session.points) {
            if (point[0] == at.getX() && point[1] == at.getY() && point[2] == at.getZ()) {
                player.sendMessage(PREFIX + "そこはもう点です。");
                return;
            }
        }
        session.pointsWorld = at.getWorld();
        session.points.add(new int[]{at.getX(), at.getY(), at.getZ()});
        changed(player);
        markPoints(player, session);
        player.sendMessage(PREFIX + "点 " + session.points.size() + " を置きました (" + at.getX() + ", " + at.getY() + ", " + at.getZ() + ")。"
                + (session.points.size() < 2 ? "あと1点以上選んでください。" : "空中を右クリックで設定画面 (プレビュー・生成)。") + " Shift+クリックで最後の点を消す。");
    }

    /** Sparks at the points, to the player alone: the first is green, the rest white. */
    void markPoints(Player player, TreeSession session) {
        if (session.pointsWorld == null || !session.pointsWorld.equals(player.getWorld())) return;
        for (int index = 0; index < session.points.size(); index++) {
            int[] point = session.points.get(index);
            Location at = new Location(session.pointsWorld, point[0] + 0.5, point[1] + 0.5, point[2] + 0.5);
            if (index == 0) player.spawnParticle(org.bukkit.Particle.VILLAGER_HAPPY, at, 8, 0.25, 0.25, 0.25, 0.0);
            else player.spawnParticle(org.bukkit.Particle.END_ROD, at, 6, 0.2, 0.2, 0.2, 0.01);
        }
    }

    /** Takes all the points away. */
    void clearPoints(Player player) {
        TreeSession session = session(player);
        session.points.clear();
        session.pointsWorld = null;
        changed(player);
        player.sendMessage(PREFIX + "点を全部消しました。");
    }

    /**
     * Reads what a web needs from the world, on the main thread: the points and the blocks round them (a box round the points, {@code web-reach} + 3 blocks wider each way).
     * Null, with a message, when there are fewer than two points or the box is too big ({@code treetool.web-max-volume}).
     */
    WebInput webInput(Player player, TreeSession session, TreeSettings settings, boolean tell) {
        if (session.points.size() < 2 || session.pointsWorld == null) {
            if (tell) player.sendMessage(PREFIX + "クモの巣は、点を2つ以上選んでください (ツールでブロックをクリック)。");
            return null;
        }
        int margin = settings.getInt(TreeSettings.Key.WEB_REACH) + 3;
        int[] min = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] max = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] point : session.points) {
            for (int axis = 0; axis < 3; axis++) {
                min[axis] = Math.min(min[axis], point[axis] - margin);
                max[axis] = Math.max(max[axis], point[axis] + margin);
            }
        }
        min[1] = Math.max(min[1], session.pointsWorld.getMinHeight());
        max[1] = Math.min(max[1], session.pointsWorld.getMaxHeight() - 1);
        long volume = WebTerrain.volume(min, max);
        long cap = plugin.getConfig().getLong("treetool.web-max-volume", 400000L);
        if (volume > cap) {
            if (tell) player.sendMessage(PREFIX + "点が離れすぎています (" + volume + " > " + cap + " ブロックの範囲)。近づけるか、点を減らしてください。");
            return null;
        }
        List<int[]> points = new ArrayList<>();
        for (int[] point : session.points) points.add(point.clone());
        return new WebInput(points, WebTerrain.sample(session.pointsWorld, min, max));
    }

    // ---- the random houses (t185) ---------------------------------------------------------------------------------------------

    /** How many random houses are kept to go back to. */
    static final int MAX_DESIGNS = 20;

    /**
     * Makes a random house by the pattern and the rules of the settings ({@link HouseDesigner}; with {@code partsOnly} only its parts, on the base as it
     * is), off the main thread; the settings become it, and the house before is kept to go back to. {@code seed} 0: a new one.
     */
    void design(Player player, boolean partsOnly, long seed) {
        TreeSession session = session(player);
        if (session.settings.kind() != ProcKind.HOUSE) {
            player.sendMessage(PREFIX + "おまかせ生成は、作るものが「家の土台」のときに使えます。");
            return;
        }
        if (session.designing) {
            player.sendMessage(PREFIX + "前の案をまだ作っています。");
            return;
        }
        TreeSettings from = session.settings.copy();
        long used = seed > 0L ? seed : 1L + new Random().nextInt(999_999);
        session.designing = true;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            HouseDesigner.Result result;
            try {
                result = HouseDesigner.design(from, used, partsOnly);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("house design failed: " + exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.designing = false;
                    player.sendMessage(PREFIX + "案を作れませんでした (ログを見てください)。");
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                session.designing = false;
                if (!player.isOnline()) return;
                keepDesign(session, result.settings());
                changed(player);
                menu.refresh(player);
                player.sendMessage(PREFIX + "案 " + (session.design + 1) + " / " + session.designs.size() + ": " + result.pattern().label + (partsOnly ? " (パーツだけ)" : "")
                        + "、追加パーツ " + result.settings().modules().size() + " 個、seed " + used + "。同じ家は /proceduraltool random " + result.pattern().id
                        + (partsOnly ? " parts " : " ") + used + (session.preview ? "" : "  (プレビューで見られます)"));
            });
        });
    }

    /** The new house becomes the settings; the settings as they were (with what was changed by hand since) stay in the list to go back to. */
    private static void keepDesign(TreeSession session, TreeSettings made) {
        if (session.designs.isEmpty()) session.designs.add(session.settings.copy());
        else if (session.design >= 0 && session.design < session.designs.size()) session.designs.set(session.design, session.settings.copy());
        session.designs.add(made.copy());
        while (session.designs.size() > MAX_DESIGNS) session.designs.remove(0);
        session.design = session.designs.size() - 1;
        session.settings = made;
        session.autoSeed = false;
        session.module = -1;
    }

    /** Goes to the house before or after in the list ({@code step} -1 or 1), keeping what was changed by hand in this one; false when there is none. */
    boolean stepDesign(Player player, int step) {
        TreeSession session = session(player);
        int next = session.design + step;
        if (session.design < 0 || session.design >= session.designs.size() || next < 0 || next >= session.designs.size()) return false;
        session.designs.set(session.design, session.settings.copy());
        session.design = next;
        session.settings = session.designs.get(next).copy();
        session.autoSeed = false;
        session.module = -1;
        changed(player);
        return true;
    }

    // ---- generating ---------------------------------------------------------------------------------------------------------

    /** Grows the tree of the player's settings on their base block and puts it in the world. */
    void generate(Player player) {
        TreeSession session = session(player);
        if (session.base == null || session.base.getWorld() == null) {
            player.sendMessage(PREFIX + "置きたい場所のブロックをクリックしてください。");
            return;
        }
        if (session.busy) {
            player.sendMessage(PREFIX + "前の木をまだ作っています。終わるまで待ってください。");
            return;
        }
        TreeSettings settings = session.settings.copy();
        limit(settings);
        WebInput webInput = null;
        if (settings.kind().web()) {
            webInput = webInput(player, session, settings, true);
            if (webInput == null) return;
        }
        WebInput web = webInput;
        // (with the preview on the seed is fixed: the tree put in the world is the one that was shown)
        long seed = settings.seed() != 0L ? settings.seed() : new Random().nextLong() & Long.MAX_VALUE;
        Location base = session.base.clone();
        boolean wasPreview = session.preview;
        if (wasPreview) preview.off(player, false);
        player.sendMessage(PREFIX + "作っています… (" + session.settings.kind().label() + ", " + algorithmLabel(settings.algorithm()) + ", 高さ " + settings.height() + " 幅 " + settings.width() + ")");
        build(player, session, settings, base, session.aim == null ? null : session.aim.clone(), seed, web);
    }

    /**
     * Grows {@code settings} at {@code base} off the main thread (the terrain read first, where it is needed) and puts it in the world as one edit; the player's session is
     * busy until it is in. The settings' own button and the placing tool (t286) both come here.
     */
    void build(Player player, TreeSession session, TreeSettings settings, Location base, org.bukkit.util.Vector aim, long seed, WebInput web) {
        session.busy = true;
        TreeGround ground;
        try {
            ground = (settings.kind().web() || settings.kind().tree()) && !OrnateTree.readsGround(settings) ? null : TreeGround.sample(base, TreeGenerator.groundRadius(settings));
        } catch (RuntimeException exception) {
            ground = null;
        }
        TreeGround terrain = ground;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Long, TreePart> blocks;
            try {
                blocks = TreeGenerator.build(settings, seed, terrain, aim, web);
            } catch (RuntimeException exception) {
                boolean tooBig = exception instanceof TreeVoxelizer.TooBig;
                if (!tooBig) plugin.getLogger().warning("tree generation failed: " + exception);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    session.busy = false;
                    player.sendMessage(PREFIX + (tooBig ? "大きすぎて作れません (" + TreeVoxelizer.LIMIT + " ブロックを超える)。設定を小さくしてください。" : "生成に失敗しました (ログを見てください)。"));
                });
                return;
            }
            Map<Long, TreePart> result = blocks;
            Bukkit.getScheduler().runTask(plugin, () -> place(player, session, settings, base, result, seed));
        });
    }

    /**
     * Puts the blocks in the world. The blocks that can be replaced are found a few thousand a tick (reading the world is the part that must stay on the main thread), then
     * all of them go through one WorldEdit edit session of the player, so that one //undo takes the tree back. Without WorldEdit they are set directly, as they are found.
     */
    private void place(Player player, TreeSession session, TreeSettings settings, Location base, Map<Long, TreePart> blocks, long seed) {
        int cap = plugin.getConfig().getInt("treetool.max-blocks", 60000);
        if (blocks.size() > cap) {
            session.busy = false;
            player.sendMessage(PREFIX + "ブロックが多すぎます (" + blocks.size() + " > " + cap + ")。設定 (木は高さ・枝の数・葉の大きさ、ほかは高さ・幅) を小さくしてください。");
            return;
        }
        World world = base.getWorld();
        List<Long> order = new ArrayList<>(blocks.keySet());
        order.sort((a, b) -> {
            int y = Integer.compare(TreeVoxelizer.unpackY(a), TreeVoxelizer.unpackY(b));
            if (y != 0) return y;
            int x = Integer.compare(TreeVoxelizer.unpackX(a), TreeVoxelizer.unpackX(b));
            return x != 0 ? x : Integer.compare(TreeVoxelizer.unpackZ(a), TreeVoxelizer.unpackZ(b));
        });
        Map<TreePart, BlockData> materials = new EnumMap<>(TreePart.class);
        for (TreePart part : TreePart.values()) materials.put(part, blockData(part, settings));
        if (blocks instanceof OrnateTree.Blocks ornate) ornate.useParts(materials);
        boolean replaceSolid = plugin.getConfig().getBoolean("treetool.replace-solid", false);
        int perTick = Math.max(200, plugin.getConfig().getInt("treetool.blocks-per-tick", 4000));
        List<TreeWorldEdit.Change> changes = new ArrayList<>();
        Map<TreePart, Integer> counts = new EnumMap<>(TreePart.class);
        List<TreePart> kindParts = settings.kind().parts();
        boolean webKind = settings.kind().web();

        new BukkitRunnable() {
            private int next;
            private int direct;

            @Override
            public void run() {
                try {
                    int end = Math.min(order.size(), next + perTick);
                    for (; next < end; next++) {
                        long key = order.get(next);
                        int x = base.getBlockX() + TreeVoxelizer.unpackX(key);
                        int y = base.getBlockY() + TreeVoxelizer.unpackY(key);
                        int z = base.getBlockZ() + TreeVoxelizer.unpackZ(key);
                        if (y < world.getMinHeight() || y >= world.getMaxHeight()) continue;
                        if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                        Block block = world.getBlockAt(x, y, z);
                        if (webKind ? !block.getType().isAir() : !replaceable(block, replaceSolid)) continue;
                        TreePart part = blocks.get(key);
                        counts.merge(part.shade() > 0 ? TreePart.LEAF : part.blockOf(), 1, Integer::sum);
                        // (t252: fences and walls joined to the wood next to them, top slabs)
                        BlockData data = ThinTree.shape(materials.get(part), part, blocks, TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key));
                        if (worldEdit) {
                            changes.add(new TreeWorldEdit.Change(x, y, z, data));
                        } else {
                            block.setBlockData(data, false);
                            direct++;
                        }
                    }
                    if (next >= order.size()) {
                        cancel();
                        finish();
                    }
                } catch (RuntimeException exception) {
                    cancel();
                    session.busy = false;
                    plugin.getLogger().warning("tree placing stopped: " + exception);
                    player.sendMessage(PREFIX + "置いている途中で失敗しました (ログを見てください)。");
                }
            }

            private void finish() {
                int count = direct;
                boolean undoable = worldEdit;
                if (worldEdit) {
                    try {
                        count = TreeWorldEdit.apply(player, world, changes);
                    } catch (RuntimeException | LinkageError exception) {
                        plugin.getLogger().warning("Tree: WorldEdit failed, putting the tree without undo: " + exception);
                        for (TreeWorldEdit.Change change : changes) world.getBlockAt(change.x(), change.y(), change.z()).setBlockData(change.data(), false);
                        count = changes.size();
                        undoable = false;
                    }
                }
                session.busy = false;
                StringBuilder text = new StringBuilder(PREFIX + "できました (" + count + "ブロック, seed " + seed + "): ");
                for (TreePart part : kindParts) {
                    text.append(part.label()).append(' ').append(counts.getOrDefault(part, 0)).append("  ");
                }
                player.sendMessage(text.toString().trim());
                player.sendMessage(PREFIX + (undoable ? "気に入らなければ //undo (やり直しは //redo)。" : "WorldEdit がないので戻せません。") + "seed を固定すると同じ木になります。");
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * (t295) Writes what the player's settings make (its seed fixed by the preview, else the settings', else a new one; its front toward the player as the
     * place they chose says) as a Sponge schematic into WorldEdit's schematics folder ({@code //schem load <name>}, then {@code //paste} puts it where you stand as
     * the tool would), else into {@code plugins/iruuRPG/schematics/}. Made off the main thread; the blocks' states read on it.
     */
    void exportSchematic(Player player, String name) {
        TreeSession session = session(player);
        TreeSettings settings = session.settings.copy();
        if (settings.kind().web()) {
            player.sendMessage(PREFIX + "クモの巣は .schem にできません。");
            return;
        }
        limit(settings);
        long seed = settings.seed() != 0L ? settings.seed() : new Random().nextLong() & Long.MAX_VALUE;
        TreeGround ground = null;
        if (session.base != null && session.base.getWorld() != null && (!settings.kind().tree() || OrnateTree.readsGround(settings))) {
            try {
                ground = TreeGround.sample(session.base, TreeGenerator.groundRadius(settings));
            } catch (RuntimeException exception) {
                ground = null;
            }
        }
        TreeGround terrain = ground;
        org.bukkit.util.Vector aim = session.aim == null ? null : session.aim.clone();
        java.io.File folder = schematicFolder();
        player.sendMessage(PREFIX + "書き出しています… (" + settings.kind().label() + ", seed " + seed + ")");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<Long, TreePart> blocks;
            try {
                blocks = TreeGenerator.build(settings, seed, terrain, aim, null);
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("schematic generation failed: " + exception);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(PREFIX + "作れませんでした (ログを見てください)。"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Map<TreePart, BlockData> materials = new EnumMap<>(TreePart.class);
                for (TreePart part : TreePart.values()) materials.put(part, blockData(part, settings));
                if (blocks instanceof OrnateTree.Blocks ornate) ornate.useParts(materials);
                Map<Long, String> states = new HashMap<>();
                for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
                    long key = entry.getKey();
                    BlockData data = ThinTree.shape(materials.get(entry.getValue()), entry.getValue(), blocks, TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key),
                            TreeVoxelizer.unpackZ(key));
                    states.put(key, data.getAsString());
                }
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    java.io.File file = new java.io.File(folder, name + ".schem");
                    String done;
                    try {
                        if (!folder.isDirectory() && !folder.mkdirs()) throw new java.io.IOException("no folder " + folder);
                        java.nio.file.Files.write(file.toPath(), SchematicWriter.write(states));
                        done = "書き出しました: " + file.getPath() + " (" + states.size() + " ブロック, seed " + seed + ")。//schem load " + name + " → //paste で、立っている所に置けます";
                    } catch (java.io.IOException | RuntimeException exception) {
                        plugin.getLogger().warning("schematic not written: " + exception);
                        done = "書き出せませんでした (ログを見てください)。";
                    }
                    String message = done;
                    Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(PREFIX + message));
                });
            });
        });
    }

    /** WorldEdit's schematics folder (FastAsyncWorldEdit's or WorldEdit's), else the plugin's own. */
    private java.io.File schematicFolder() {
        for (String name : new String[]{"FastAsyncWorldEdit", "WorldEdit"}) {
            org.bukkit.plugin.Plugin other = plugin.getServer().getPluginManager().getPlugin(name);
            if (other != null) return new java.io.File(other.getDataFolder(), "schematics");
        }
        return new java.io.File(plugin.getDataFolder(), "schematics");
    }

    static boolean replaceable(Block block, boolean replaceSolid) {
        Material type = block.getType();
        if (type == Material.BEDROCK || type == Material.BARRIER) return false;
        if (replaceSolid) return true;
        if (block.isLiquid()) return false;
        return type.isAir() || block.isReplaceable();
    }

    /** Takes the last edit of the player back: the same as //undo (a tree, or a stroke of the brush). */
    void undo(Player player) {
        if (!worldEdit) {
            player.sendMessage(PREFIX + "WorldEdit がないので戻せません。");
            return;
        }
        player.performCommand("/undo");
    }

    void redo(Player player) {
        if (!worldEdit) {
            player.sendMessage(PREFIX + "WorldEdit がないので使えません。");
            return;
        }
        player.performCommand("/redo");
    }

    /** The word the tool is called by in messages, for the menu and the command. */
    static String algorithmLabel(String algorithm) {
        return switch (algorithm) {
            case "tiered" -> "段の枝 (針葉樹・雪)";
            case "palm" -> "ヤシ";
            case "thin" -> "細い木 (壁・フェンス・ハーフの枝)";
            case "silhouette" -> "樹形 (丸・しだれ・針葉樹・流れ・傘)";
            case "ornate" -> "装飾の木 (オーク・盆栽・トウヒ・白樺・柳・桜・アカシア)";
            default -> "樹冠 (フラクタル)";
        };
    }
}
