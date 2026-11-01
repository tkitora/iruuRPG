package net.tkgon.mc.iruuRPG.tools.procedural;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tkgon.mc.iruuRPG.gui.MenuHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/**
 * The settings screen of the tree tool, in pages by what is being set: the basics (place, algorithm, seed, blocks), the trunk and roots, the branches and the leaves. A
 * row of tabs on top moves between the pages, and the bottom row (close, preview, generate, undo and redo) is the same on all of them. Every number is a button (left click
 * up, right click down, shift for bigger steps); every change is passed to the preview, which follows it after its wait. Settings cannot be saved from here (a stray click
 * must not overwrite a saved tree): saving is {@code /treetool save <name>}; loading is here.
 */
final class ProceduralMenu implements Listener {

    private static final int SIZE = 54;
    /** Where the settings of a page go. */
    private static final int[] GRID = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};

    // the bottom row (the same on every page)
    private static final int CLOSE = 45;
    private static final int PREVIEW = 47;
    private static final int GENERATE = 49;
    private static final int UNDO = 51;
    private static final int REDO = 52;

    // the basic page
    private static final int INFO = 10;
    private static final int KIND = 11;
    private static final int ALGORITHM = 12;
    private static final int CROWN = 13;
    private static final int SEED = 14;
    private static final int LOAD = 16;
    private static final int RESET = 25;
    private static final int[] LEGEND_SLOTS = {37, 38, 39, 40, 41};
    // the leaf page: the leaf gradient from the bottom of a clump to its top, and its help
    private static final int[] SHADE_SLOTS = {28, 29, 30, 31, 32, 33};
    private static final int SHADE_HELP = 34;

    /** The pages: the slot of the tab, its icon and name, and the numbers it holds. */
    enum Page {
        BASIC(2, Material.OAK_SAPLING, "基本", new TreeSettings.Key[]{TreeSettings.Key.HEIGHT, TreeSettings.Key.WIDTH}),
        TRUNK(3, Material.OAK_LOG, "幹・根", new TreeSettings.Key[]{}),
        BRANCH(4, Material.STICK, "枝", new TreeSettings.Key[]{}),
        LEAF(5, Material.OAK_LEAVES, "葉", new TreeSettings.Key[]{}),
        /** The settings of everything that is not a tree (which of them depends on the kind). */
        SHAPE(3, Material.COBBLESTONE, "形", new TreeSettings.Key[]{});

        final int tab;
        final Material icon;
        final String label;
        final TreeSettings.Key[] keys;

        Page(int tab, Material icon, String label, TreeSettings.Key[] keys) {
            this.tab = tab;
            this.icon = icon;
            this.label = label;
            this.keys = keys;
        }

        /** Whether the page is there for these settings: a tree has the pages of a tree (a palm has no branches), the rest have the basics and their shape. */
        boolean shownFor(TreeSettings settings) {
            ProcKind kind = settings.kind();
            return switch (this) {
                case BASIC -> true;
                case SHAPE -> !kind.tree();
                case BRANCH -> kind.tree() && !settings.grows("palm");
                default -> kind.tree();
            };
        }

        /** The numbers of the page for these settings (the kind, and for a tree its algorithm). */
        TreeSettings.Key[] keysFor(TreeSettings settings) {
            ProcKind kind = settings.kind();
            // (a web has no height and width: the points decide how big it is; a tree is as big as its own numbers make it)
            if (this == BASIC && (kind.web() || kind.tree())) return new TreeSettings.Key[]{};
            if (kind.tree() && this != BASIC) return treeKeys(settings.algorithm(), this);
            if (this != SHAPE) return keys;
            return switch (kind) {
                case WEB -> new TreeSettings.Key[]{TreeSettings.Key.WEB_RINGS, TreeSettings.Key.WEB_SPOKES, TreeSettings.Key.WEB_SAG, TreeSettings.Key.WEB_IRREG, TreeSettings.Key.WEB_REACH};
                case ROCK -> new TreeSettings.Key[]{TreeSettings.Key.COUNT, TreeSettings.Key.ROUGHNESS, TreeSettings.Key.LAYERS, TreeSettings.Key.MOSS};
                case SPIKE -> new TreeSettings.Key[]{TreeSettings.Key.SPIKE_SIDES, TreeSettings.Key.SPIKE_TWIST, TreeSettings.Key.SPIKE_STAR, TreeSettings.Key.SPIKE_TAPER, TreeSettings.Key.CURVE,
                        TreeSettings.Key.ROUGHNESS, TreeSettings.Key.LAYERS};
                case CRYSTAL -> new TreeSettings.Key[]{TreeSettings.Key.COUNT, TreeSettings.Key.CRYSTAL_FACETS, TreeSettings.Key.CRYSTAL_TIP, TreeSettings.Key.CRYSTAL_THICK, TreeSettings.Key.CRYSTAL_VARIANCE,
                        TreeSettings.Key.SPREAD, TreeSettings.Key.ROUGHNESS};
                default -> new TreeSettings.Key[]{};
            };
        }
    }

    /** The numbers of a page of a tree grown by {@code algorithm}. */
    static TreeSettings.Key[] treeKeys(String algorithm, Page page) {
        return switch (algorithm) {
            case "tiered" -> switch (page) {
                case TRUNK -> new TreeSettings.Key[]{TreeSettings.Key.TIER_HEIGHT, TreeSettings.Key.TIER_STUMP, TreeSettings.Key.TIER_RADIUS, TreeSettings.Key.TIER_TWIST,
                        TreeSettings.Key.TIER_BRAIDED, TreeSettings.Key.TIER_BRAID_INTENSITY, TreeSettings.Key.TIER_BRAID_WOBBLE, TreeSettings.Key.TIER_ROOT_FLARE,
                        TreeSettings.Key.TIER_WIND, TreeSettings.Key.TIER_WIND_DIR};
                case BRANCH -> new TreeSettings.Key[]{TreeSettings.Key.TIER_SPACING, TreeSettings.Key.TIER_BRANCHES, TreeSettings.Key.TIER_BRANCH_LENGTH, TreeSettings.Key.TIER_SAG};
                case LEAF -> new TreeSettings.Key[]{TreeSettings.Key.TIER_LEAF_SIZE, TreeSettings.Key.TIER_LEAF_DENSITY, TreeSettings.Key.TIER_WILLOW, TreeSettings.Key.TIER_WILLOW_SCALE,
                        TreeSettings.Key.TIER_SNOW, TreeSettings.Key.TIER_SNOW_AMOUNT};
                default -> new TreeSettings.Key[]{};
            };
            case "palm" -> switch (page) {
                case TRUNK -> new TreeSettings.Key[]{TreeSettings.Key.PALM_HEIGHT, TreeSettings.Key.PALM_RADIUS, TreeSettings.Key.PALM_S_CURVE, TreeSettings.Key.PALM_SPLIT_CHANCE,
                        TreeSettings.Key.PALM_SPLIT_DIST, TreeSettings.Key.PALM_ROOT_REACH};
                case LEAF -> new TreeSettings.Key[]{TreeSettings.Key.PALM_LAYERS, TreeSettings.Key.PALM_FRONDS, TreeSettings.Key.PALM_FROND_LENGTH, TreeSettings.Key.PALM_FROND_THICK,
                        TreeSettings.Key.PALM_DROOP, TreeSettings.Key.PALM_PITCH};
                default -> new TreeSettings.Key[]{};
            };
            default -> switch (page) {
                case TRUNK -> new TreeSettings.Key[]{TreeSettings.Key.CANOPY_TRUNK_HEIGHT, TreeSettings.Key.CANOPY_RADIUS, TreeSettings.Key.CANOPY_TWIST, TreeSettings.Key.CANOPY_UPPER_TWIST,
                        TreeSettings.Key.CANOPY_CENTERING, TreeSettings.Key.CANOPY_ROOT_COUNT, TreeSettings.Key.CANOPY_ROOT_LENGTH, TreeSettings.Key.CANOPY_ROOT_HEIGHT,
                        TreeSettings.Key.CANOPY_ROOT_SCALE};
                case BRANCH -> new TreeSettings.Key[]{TreeSettings.Key.CANOPY_LENGTH, TreeSettings.Key.CANOPY_LENGTH_MULT, TreeSettings.Key.CANOPY_DEPTH, TreeSettings.Key.CANOPY_BRANCHES,
                        TreeSettings.Key.CANOPY_SPREAD, TreeSettings.Key.CANOPY_STAGGER, TreeSettings.Key.CANOPY_RADIUS_MULT, TreeSettings.Key.CANOPY_LEVELS,
                        TreeSettings.Key.CANOPY_LEVEL_DIST, TreeSettings.Key.CANOPY_UPPER_SCALE, TreeSettings.Key.CANOPY_GRAVITY, TreeSettings.Key.CANOPY_WIND,
                        TreeSettings.Key.CANOPY_WIND_DIR};
                case LEAF -> new TreeSettings.Key[]{TreeSettings.Key.CANOPY_LEAF_SIZE, TreeSettings.Key.CANOPY_LEAF_START, TreeSettings.Key.CANOPY_ELLIPSOID, TreeSettings.Key.CANOPY_WILLOW,
                        TreeSettings.Key.CANOPY_WILLOW_SCALE, TreeSettings.Key.CANOPY_BETA_LEAVES};
                default -> new TreeSettings.Key[]{};
            };
        };
    }

    private static final Map<TreeSettings.Key, Material> ICONS = new EnumMap<>(TreeSettings.Key.class);

    static {
        ICONS.put(TreeSettings.Key.HEIGHT, Material.LADDER);
        ICONS.put(TreeSettings.Key.WIDTH, Material.LEAD);
        ICONS.put(TreeSettings.Key.CURVE, Material.CHAIN);
        ICONS.put(TreeSettings.Key.SPIKE_SIDES, Material.HONEYCOMB);
        ICONS.put(TreeSettings.Key.SPIKE_TWIST, Material.TWISTING_VINES);
        ICONS.put(TreeSettings.Key.SPIKE_STAR, Material.NETHER_STAR);
        ICONS.put(TreeSettings.Key.CRYSTAL_TIP, Material.AMETHYST_SHARD);
        ICONS.put(TreeSettings.Key.CRYSTAL_FACETS, Material.AMETHYST_BLOCK);
        ICONS.put(TreeSettings.Key.CRYSTAL_THICK, Material.BUDDING_AMETHYST);
        ICONS.put(TreeSettings.Key.CRYSTAL_VARIANCE, Material.LARGE_AMETHYST_BUD);
        ICONS.put(TreeSettings.Key.WEB_RINGS, Material.STRING);
        ICONS.put(TreeSettings.Key.WEB_SPOKES, Material.TRIPWIRE_HOOK);
        ICONS.put(TreeSettings.Key.WEB_SAG, Material.LEAD);
        ICONS.put(TreeSettings.Key.WEB_IRREG, Material.COBWEB);
        ICONS.put(TreeSettings.Key.WEB_REACH, Material.SPYGLASS);
        ICONS.put(TreeSettings.Key.CANOPY_TRUNK_HEIGHT, Material.LADDER);
        ICONS.put(TreeSettings.Key.CANOPY_LENGTH, Material.BAMBOO);
        ICONS.put(TreeSettings.Key.CANOPY_LENGTH_MULT, Material.STICK);
        ICONS.put(TreeSettings.Key.CANOPY_LEVELS, Material.SCAFFOLDING);
        ICONS.put(TreeSettings.Key.CANOPY_LEVEL_DIST, Material.CHAIN);
        ICONS.put(TreeSettings.Key.CANOPY_UPPER_SCALE, Material.BIRCH_SAPLING);
        ICONS.put(TreeSettings.Key.CANOPY_RADIUS, Material.OAK_LOG);
        ICONS.put(TreeSettings.Key.CANOPY_RADIUS_MULT, Material.STRIPPED_OAK_LOG);
        ICONS.put(TreeSettings.Key.CANOPY_DEPTH, Material.FERN);
        ICONS.put(TreeSettings.Key.CANOPY_BRANCHES, Material.OAK_FENCE);
        ICONS.put(TreeSettings.Key.CANOPY_SPREAD, Material.COMPASS);
        ICONS.put(TreeSettings.Key.CANOPY_LEAF_SIZE, Material.OAK_LEAVES);
        ICONS.put(TreeSettings.Key.CANOPY_LEAF_START, Material.AZALEA_LEAVES);
        ICONS.put(TreeSettings.Key.CANOPY_STAGGER, Material.RABBIT_FOOT);
        ICONS.put(TreeSettings.Key.CANOPY_TWIST, Material.TWISTING_VINES);
        ICONS.put(TreeSettings.Key.CANOPY_UPPER_TWIST, Material.WEEPING_VINES);
        ICONS.put(TreeSettings.Key.CANOPY_CENTERING, Material.LIGHTNING_ROD);
        ICONS.put(TreeSettings.Key.CANOPY_ROOT_COUNT, Material.MANGROVE_ROOTS);
        ICONS.put(TreeSettings.Key.CANOPY_ROOT_LENGTH, Material.HANGING_ROOTS);
        ICONS.put(TreeSettings.Key.CANOPY_ROOT_HEIGHT, Material.MUDDY_MANGROVE_ROOTS);
        ICONS.put(TreeSettings.Key.CANOPY_ROOT_SCALE, Material.ROOTED_DIRT);
        ICONS.put(TreeSettings.Key.CANOPY_GRAVITY, Material.ANVIL);
        ICONS.put(TreeSettings.Key.CANOPY_WIND, Material.FEATHER);
        ICONS.put(TreeSettings.Key.CANOPY_WIND_DIR, Material.RECOVERY_COMPASS);
        ICONS.put(TreeSettings.Key.CANOPY_ELLIPSOID, Material.FLOWERING_AZALEA_LEAVES);
        ICONS.put(TreeSettings.Key.CANOPY_WILLOW, Material.VINE);
        ICONS.put(TreeSettings.Key.CANOPY_WILLOW_SCALE, Material.GLOW_LICHEN);
        ICONS.put(TreeSettings.Key.CANOPY_BETA_LEAVES, Material.CLOCK);
        ICONS.put(TreeSettings.Key.TIER_HEIGHT, Material.LADDER);
        ICONS.put(TreeSettings.Key.TIER_STUMP, Material.SPRUCE_LOG);
        ICONS.put(TreeSettings.Key.TIER_RADIUS, Material.OAK_LOG);
        ICONS.put(TreeSettings.Key.TIER_TWIST, Material.TWISTING_VINES);
        ICONS.put(TreeSettings.Key.TIER_BRAIDED, Material.CHAIN);
        ICONS.put(TreeSettings.Key.TIER_BRAID_INTENSITY, Material.LEAD);
        ICONS.put(TreeSettings.Key.TIER_BRAID_WOBBLE, Material.STRING);
        ICONS.put(TreeSettings.Key.TIER_ROOT_FLARE, Material.MANGROVE_ROOTS);
        ICONS.put(TreeSettings.Key.TIER_SPACING, Material.SCAFFOLDING);
        ICONS.put(TreeSettings.Key.TIER_BRANCHES, Material.OAK_FENCE);
        ICONS.put(TreeSettings.Key.TIER_BRANCH_LENGTH, Material.BAMBOO);
        ICONS.put(TreeSettings.Key.TIER_SAG, Material.WEEPING_VINES);
        ICONS.put(TreeSettings.Key.TIER_LEAF_SIZE, Material.SPRUCE_LEAVES);
        ICONS.put(TreeSettings.Key.TIER_LEAF_DENSITY, Material.AZALEA_LEAVES);
        ICONS.put(TreeSettings.Key.TIER_WILLOW, Material.VINE);
        ICONS.put(TreeSettings.Key.TIER_WILLOW_SCALE, Material.GLOW_LICHEN);
        ICONS.put(TreeSettings.Key.TIER_SNOW, Material.SNOW_BLOCK);
        ICONS.put(TreeSettings.Key.TIER_SNOW_AMOUNT, Material.SNOWBALL);
        ICONS.put(TreeSettings.Key.TIER_WIND, Material.FEATHER);
        ICONS.put(TreeSettings.Key.TIER_WIND_DIR, Material.RECOVERY_COMPASS);
        ICONS.put(TreeSettings.Key.PALM_HEIGHT, Material.LADDER);
        ICONS.put(TreeSettings.Key.PALM_RADIUS, Material.JUNGLE_LOG);
        ICONS.put(TreeSettings.Key.PALM_ROOT_REACH, Material.MANGROVE_ROOTS);
        ICONS.put(TreeSettings.Key.PALM_S_CURVE, Material.TWISTING_VINES);
        ICONS.put(TreeSettings.Key.PALM_SPLIT_CHANCE, Material.RABBIT_FOOT);
        ICONS.put(TreeSettings.Key.PALM_SPLIT_DIST, Material.SHEARS);
        ICONS.put(TreeSettings.Key.PALM_LAYERS, Material.SCAFFOLDING);
        ICONS.put(TreeSettings.Key.PALM_FRONDS, Material.JUNGLE_LEAVES);
        ICONS.put(TreeSettings.Key.PALM_FROND_LENGTH, Material.BAMBOO);
        ICONS.put(TreeSettings.Key.PALM_FROND_THICK, Material.BIG_DRIPLEAF);
        ICONS.put(TreeSettings.Key.PALM_DROOP, Material.WEEPING_VINES);
        ICONS.put(TreeSettings.Key.PALM_PITCH, Material.COMPASS);
    }

    private final ProceduralModule tool;

    ProceduralMenu(ProceduralModule tool) {
        this.tool = tool;
    }

    private static final class Holder implements MenuHolder {
        private Inventory inventory;
        private Page page = Page.BASIC;
        /** The block slot waiting for a block from the player's inventory: "part:<PART>" or "shade:<index>"; null when none. */
        private String picking;

        @Override
        public @NotNull Inventory getInventory() {
            return inventory;
        }
    }

    void open(Player player) {
        Holder holder = new Holder();
        Inventory inventory = Bukkit.createInventory(holder, SIZE, Component.text("プロシージャルツール"));
        holder.inventory = inventory;
        // (it opens on the page that was open last, when that page is there for what is being made)
        TreeSession opened = tool.session(player);
        try {
            Page last = Page.valueOf(opened.lastPage);
            if (last.shownFor(opened.settings)) holder.page = last;
        } catch (IllegalArgumentException ignored) {
            holder.page = Page.BASIC;
        }
        render(player, holder);
        player.openInventory(inventory);
    }

    private static Component text(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack item(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    static String crystalTypeLabel(String type) {
        return switch (type) {
            case "prism" -> "大きな1本";
            case "blades" -> "薄い刃";
            default -> "かたまり";
        };
    }

    private static String number(TreeSettings.Key key, double value) {
        if (key.toggle()) return value >= 0.5 ? "オン" : "オフ";
        return key.whole() ? Integer.toString((int) Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        TreeSession session = tool.session(player);
        TreeSettings settings = session.settings;
        Page page = holder.page;
        session.lastPage = page.name();
        inventory.clear();
        ItemStack frame = item(Material.GRAY_STAINED_GLASS_PANE, " ", NamedTextColor.DARK_GRAY, List.of());
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot < 9 || slot >= 45 || slot % 9 == 0 || slot % 9 == 8) inventory.setItem(slot, frame);
        }
        // the tabs
        for (Page tab : Page.values()) {
            if (!tab.shownFor(settings)) continue;
            boolean here = tab == page;
            ItemStack icon = item(tab.icon, (here ? "▶ " : "") + tab.label, here ? NamedTextColor.GOLD : NamedTextColor.WHITE, List.of(
                    text(here ? "いま開いているページ" : "クリックで開く", NamedTextColor.GRAY)));
            if (here) {
                ItemMeta meta = icon.getItemMeta();
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                icon.setItemMeta(meta);
            }
            inventory.setItem(tab.tab, icon);
        }

        if (page == Page.BASIC) renderBasic(player, inventory, session, settings);
        else inventory.setItem(INFO, item(page.icon, page.label + "の設定", NamedTextColor.GOLD, List.of(
                text("左クリック: 増やす / 右クリック: 減らす", NamedTextColor.GRAY),
                text("Shift を押しながらで、大きく動かす", NamedTextColor.GRAY),
                text("プレビュー中は、変えて 0.75 秒後に木が変わる", NamedTextColor.GRAY))));
        if (page == Page.LEAF) renderShades(inventory, holder, settings);

        TreeSettings.Key[] shownKeys = page.keysFor(settings);
        for (int index = 0; index < shownKeys.length; index++) {
            TreeSettings.Key key = shownKeys[index];
            List<Component> lore = new ArrayList<>();
            lore.add(text(key.description(), NamedTextColor.GRAY));
            if (key.toggle()) {
                lore.add(text("現在: " + number(key, settings.get(key)), NamedTextColor.WHITE));
                lore.add(text("クリックで切り替え", NamedTextColor.YELLOW));
            } else {
                lore.add(text("現在: " + number(key, settings.get(key)) + "  (" + number(key, key.min()) + " 〜 " + number(key, key.max()) + ")", NamedTextColor.WHITE));
                lore.add(text("左クリック +" + number(key, key.step()) + " / 右クリック -" + number(key, key.step()), NamedTextColor.YELLOW));
                lore.add(text("Shift で " + number(key, key.bigStep()) + " ずつ", NamedTextColor.YELLOW));
            }
            inventory.setItem(GRID[index], item(ICONS.getOrDefault(key, Material.PAPER), key.label() + ": " + number(key, settings.get(key)), NamedTextColor.GREEN, lore));
        }

        if (page == Page.SHAPE && settings.kind() == ProcKind.CRYSTAL) {
            List<Component> typeLore = new ArrayList<>();
            typeLore.add(text("かたまり: 岩の足元から、何本かの結晶が外へ広がる", NamedTextColor.GRAY));
            typeLore.add(text("大きな1本: 面のはっきりした太い結晶が1本", NamedTextColor.GRAY));
            typeLore.add(text("薄い刃: 平たく細い欠片のかたまり", NamedTextColor.GRAY));
            typeLore.add(text("左クリック: 次の作り方 / 右クリック: 前の作り方", NamedTextColor.YELLOW));
            inventory.setItem(GRID[13], item(Material.AMETHYST_CLUSTER, "結晶の作り方: " + crystalTypeLabel(settings.crystalType()), NamedTextColor.GOLD, typeLore));
        }
        // the bottom row, the same on every page
        inventory.setItem(CLOSE, item(Material.ARROW, "閉じる", NamedTextColor.YELLOW, List.of()));
        boolean on = session.preview;
        inventory.setItem(PREVIEW, item(on ? Material.LIME_STAINED_GLASS : Material.GRAY_STAINED_GLASS, "プレビュー: " + (on ? "オン" : "オフ"), on ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                text("オンにすると、選んだブロックのままの試し置きが見える (あなたにだけ)", NamedTextColor.GRAY),
                text("シードを固定して、設定の変更が0.75秒後に反映される", NamedTextColor.GRAY),
                text("重い設定のとき前の処理が終わっていなければ、その回は見送る", NamedTextColor.GRAY),
                text("クリックで切り替え", NamedTextColor.YELLOW))));
        inventory.setItem(GENERATE, item(Material.LIME_WOOL, "生成する", NamedTextColor.GREEN, List.of(
                text("この設定の木を、決めた場所に作る", NamedTextColor.GRAY),
                text(settings.kind().tree() ? "大きさは、幹・枝・葉のページの数字で決まる" : "高さ " + settings.height() + " / 幅 " + settings.width() + " の箱の中に収まる", NamedTextColor.GRAY),
                text("空気と草花だけを置き換える", NamedTextColor.GRAY),
                text("クリックで生成 (作った木は //undo で戻せる)", NamedTextColor.YELLOW))));
        inventory.setItem(UNDO, item(Material.BARRIER, "元に戻す (//undo)", NamedTextColor.RED, List.of(
                text("直前の編集を戻す。木もブラシも、FAWE の履歴で戻る", NamedTextColor.GRAY))));
        inventory.setItem(REDO, item(Material.SPECTRAL_ARROW, "やり直し (//redo)", NamedTextColor.AQUA, List.of(
                text("戻した編集をもう一度行う", NamedTextColor.GRAY))));
    }

    private void renderBasic(Player player, Inventory inventory, TreeSession session, TreeSettings settings) {
        Location base = session.base;
        ProcKind kind = settings.kind();
        if (kind.web()) {
            inventory.setItem(INFO, item(Material.COMPASS, "巣の点", NamedTextColor.GOLD, List.of(
                    text(session.points.size() + " 点 (最初の点: " + (base == null ? "なし" : base.getBlockX() + ", " + base.getBlockY() + ", " + base.getBlockZ()) + ")", NamedTextColor.WHITE),
                    text("ツールでブロックをクリックして点を足す。2点以上で生成できる", NamedTextColor.GRAY),
                    text("空気のブロックにだけ置く (ほかのブロックは上書きしない)", NamedTextColor.GRAY))));
        } else inventory.setItem(INFO, item(Material.COMPASS, "置く場所", NamedTextColor.GOLD, List.of(
                text(base == null ? "まだ決まっていません (ブロックをクリック)" : base.getWorld().getName() + " " + base.getBlockX() + ", " + base.getBlockY() + ", " + base.getBlockZ(), NamedTextColor.WHITE),
                text("クリックしたブロックの真上が、足元 (逆さのスパイク・結晶は、クリックしたブロックの真下から垂れる)", NamedTextColor.GRAY),
                text("木の根・岩の足元は、地面があればそれに沿う", NamedTextColor.GRAY))));
        // one item for what is made: click for the next, right click for the one before
        List<Component> kindLore = new ArrayList<>();
        kindLore.add(text(kind.description(), NamedTextColor.GRAY));
        for (ProcKind candidate : ProcKind.values()) kindLore.add(text((candidate == kind ? "▶ " : "   ") + candidate.label(), candidate == kind ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        kindLore.add(text("左クリック: 次の種類 / 右クリック: 前の種類 (種類を変えると、大きさなどがその種類の初期値になる)", NamedTextColor.YELLOW));
        inventory.setItem(KIND, item(kind.icon(), "作るもの: " + kind.label(), NamedTextColor.GOLD, kindLore));
        if (kind.tree()) renderTreeChoices(inventory, settings);
        if (kind.web()) renderWebChoices(inventory, session, settings);
        renderRest(inventory, holder(inventory), session, settings);
    }

    static String webShapeLabel(String shape) {
        return switch (shape) {
            case "sheet" -> "棚網 (平らな面)";
            case "bowl" -> "皿網 (皿のようにたるむ)";
            case "funnel" -> "漏斗網 (すぼまる筒)";
            case "irregular" -> "不規則網 (からまった糸)";
            case "strand" -> "条網 (点から点への帯)";
            default -> "円網 (放射糸とらせん)";
        };
    }

    private void renderWebChoices(Inventory inventory, TreeSession session, TreeSettings settings) {
        List<Component> shapeLore = new ArrayList<>();
        shapeLore.add(text("円網: 点を結ぶ枠に、中心から放射糸とらせんを張る", NamedTextColor.GRAY));
        shapeLore.add(text("棚網: 枠の中に、交差する糸の平らな面", NamedTextColor.GRAY));
        shapeLore.add(text("皿網: 棚網が中央に向かって皿のようにたるむ (たるみ = 深さ)", NamedTextColor.GRAY));
        shapeLore.add(text("漏斗網: 口から一番低い点へすぼまる筒。輪と縦糸", NamedTextColor.GRAY));
        shapeLore.add(text("不規則網: 点と、そのまわりの空間の節をつなぐ、からまった糸", NamedTextColor.GRAY));
        shapeLore.add(text("条網: 点から点へ、並行な糸の帯と横糸", NamedTextColor.GRAY));
        shapeLore.add(text("左クリック: 次の形 / 右クリック: 前の形", NamedTextColor.YELLOW));
        inventory.setItem(ALGORITHM, item(Material.COBWEB, "巣の形: " + webShapeLabel(settings.webShape()), NamedTextColor.AQUA, shapeLore));
        List<Component> pointLore = new ArrayList<>();
        pointLore.add(text("ツールでブロックをクリックすると、その面の先の空気が点になる (Shift+クリックで最後の点を消す)", NamedTextColor.GRAY));
        pointLore.add(text("2点以上で巣ができる。最初の点が巣の基準 (地形の壁・地面・天井には、点のまわりで自動で糸を固定する)", NamedTextColor.GRAY));
        for (int index = 0; index < Math.min(8, session.points.size()); index++) {
            int[] point = session.points.get(index);
            pointLore.add(text((index == 0 ? "◎ " : "・ ") + point[0] + ", " + point[1] + ", " + point[2], NamedTextColor.WHITE));
        }
        pointLore.add(text("左クリック: 最後の点を消す / 右クリック: 全部消す", NamedTextColor.YELLOW));
        inventory.setItem(CROWN, item(Material.LEAD, "点: " + session.points.size() + " 個", NamedTextColor.AQUA, pointLore));
    }

    private void renderTreeChoices(Inventory inventory, TreeSettings settings) {
        inventory.setItem(ALGORITHM, item(Material.KNOWLEDGE_BOOK, "アルゴリズム: " + ProceduralModule.algorithmLabel(settings.algorithm()), NamedTextColor.AQUA, List.of(
                text("樹冠: 枝が同じ規則で分かれ続けるフラクタル。樹冠を重ねる・柳のつる・Beta 1.7.3 の葉", NamedTextColor.GRAY),
                text("段の枝: 幹に段ごとに枝が出る (針葉樹など)。ねじれ・編み込みの幹・雪・柳のつる", NamedTextColor.GRAY),
                text("ヤシ: S字の幹、てっぺんに葉の層。二股・支柱の根", NamedTextColor.GRAY),
                text("左クリック: 次 / 右クリック: 前 (数字は、アルゴリズムごとに別々に残る)", NamedTextColor.YELLOW))));
    }

    private static Holder holder(Inventory inventory) {
        return (Holder) inventory.getHolder();
    }

    private void renderRest(Inventory inventory, Holder holder, TreeSession session, TreeSettings settings) {
        inventory.setItem(SEED, item(Material.WHEAT_SEEDS, "シード: " + (settings.seed() == 0L ? "毎回ランダム" : Long.toString(settings.seed())) + (session.preview ? " (プレビュー中は固定)" : ""), NamedTextColor.AQUA, List.of(
                text("同じシードと設定なら、同じ木ができる", NamedTextColor.GRAY),
                text("左クリック: 新しい番号に固定", NamedTextColor.YELLOW),
                text("右クリック: 毎回ランダムに戻す (プレビュー中は、新しい番号で固定)", NamedTextColor.YELLOW))));
        List<String> saved = tool.store().list();
        List<Component> loadLore = new ArrayList<>();
        loadLore.add(text("保存した木の設定を読み込む (" + saved.size() + "件)", NamedTextColor.GRAY));
        for (int index = 0; index < Math.min(8, saved.size()); index++) {
            loadLore.add(text((saved.get(index).equals(settings.name()) ? "▶ " : "  ") + saved.get(index), NamedTextColor.WHITE));
        }
        loadLore.add(text("左クリック: 次へ / 右クリック: 前へ (その場で読み込む)", NamedTextColor.YELLOW));
        inventory.setItem(LOAD, item(Material.BOOKSHELF, "読み込み", NamedTextColor.AQUA, loadLore));
        inventory.setItem(RESET, item(Material.TNT, "設定を初期値に戻す", NamedTextColor.RED, List.of(
                text("数字・シード・選んだブロックを初期値にする (アルゴリズムはそのまま)", NamedTextColor.GRAY),
                text("(保存は、誤クリックを防ぐため画面からはできません: /treetool save <名前>)", NamedTextColor.DARK_GRAY))));
        List<TreePart> parts = legendParts(settings);
        for (int index = 0; index < parts.size() && index < LEGEND_SLOTS.length; index++) {
            TreePart part = parts.get(index);
            Material block = tool.material(part, settings);
            List<Component> lore = new ArrayList<>();
            if (part == TreePart.LEAF) {
                List<String> gradient = settings.leafGradient();
                if (gradient.isEmpty()) {
                    lore.add(text("ブロック: " + block.getKey().getKey() + (settings.block(part) == null ? " (初期値)" : ""), NamedTextColor.WHITE));
                } else {
                    lore.add(text("葉のグラデーション (下 → 上):", NamedTextColor.WHITE));
                    for (int shade = 0; shade < gradient.size(); shade++) lore.add(text("  " + (shade + 1) + ". " + gradient.get(shade).toLowerCase(Locale.ROOT), NamedTextColor.WHITE));
                }
                lore.add(text("左クリック: 葉のページで、ブロックとグラデーションを選ぶ", NamedTextColor.YELLOW));
                lore.add(text("右クリック: グラデーションを消して、初期値に戻す", NamedTextColor.YELLOW));
            } else {
                lore.add(text("ブロック: " + block.getKey().getKey() + (settings.block(part) == null ? " (初期値)" : ""), NamedTextColor.WHITE));
                lore.add(text("左クリックのあと、自分のインベントリのブロックをクリックで選ぶ", NamedTextColor.YELLOW));
                lore.add(text("右クリック: 初期値に戻す (/proceduraltool block " + part.key() + " <ブロック名> でも選べる)", NamedTextColor.YELLOW));
            }
            boolean waiting = ("part:" + part.name()).equals(holder.picking);
            if (waiting) lore.add(0, text("▶ インベントリのブロックをクリックしてください", NamedTextColor.GOLD));
            inventory.setItem(LEGEND_SLOTS[index], glint(item(icon(block), part.label() + ": " + block.getKey().getKey(), waiting ? NamedTextColor.GOLD : NamedTextColor.WHITE, lore), waiting));
        }
    }

    /** The parts shown with their blocks on the basic page (only those the algorithm of a tree makes: snow on a tiered tree, no branches on a palm). */
    private static List<TreePart> legendParts(TreeSettings settings) {
        List<TreePart> parts = new ArrayList<>(settings.kind().parts());
        if (settings.kind().tree()) {
            if (!settings.grows("tiered")) parts.remove(TreePart.SNOW);
            if (settings.grows("palm")) parts.remove(TreePart.BRANCH);
        }
        return parts;
    }

    /** The leaf gradient on the leaf page: a slot a shade (bottom to top), one more to add a shade, and a help item. */
    private void renderShades(Inventory inventory, Holder holder, TreeSettings settings) {
        List<String> gradient = settings.leafGradient();
        for (int index = 0; index < SHADE_SLOTS.length; index++) {
            boolean waiting = ("shade:" + index).equals(holder.picking);
            if (index < gradient.size()) {
                Material block = Material.matchMaterial(gradient.get(index));
                if (block == null) block = Material.BARRIER;
                List<Component> lore = new ArrayList<>();
                if (waiting) lore.add(text("▶ インベントリのブロックをクリックしてください", NamedTextColor.GOLD));
                lore.add(text(index == 0 ? "葉の塊の一番下" : index == gradient.size() - 1 ? "葉の塊の一番上" : "下から " + (index + 1) + " 段目", NamedTextColor.GRAY));
                lore.add(text("左クリックのあと、インベントリのブロックをクリック: 選び直す", NamedTextColor.YELLOW));
                lore.add(text("右クリック: この段を消す", NamedTextColor.YELLOW));
                inventory.setItem(SHADE_SLOTS[index], glint(item(icon(block), "葉 " + (index + 1) + "段目: " + block.getKey().getKey(), waiting ? NamedTextColor.GOLD : NamedTextColor.GREEN, lore), waiting));
            } else if (index == gradient.size()) {
                List<Component> lore = new ArrayList<>();
                if (waiting) lore.add(text("▶ インベントリのブロックをクリックしてください", NamedTextColor.GOLD));
                lore.add(text("左クリックのあと、インベントリのブロックをクリックで、上に1段足す", NamedTextColor.YELLOW));
                lore.add(text("(手に持てないブロックは /proceduraltool block leaf <ブロック名,...>)", NamedTextColor.DARK_GRAY));
                inventory.setItem(SHADE_SLOTS[index], glint(item(Material.LIME_STAINED_GLASS_PANE, "＋ 葉の段を足す", waiting ? NamedTextColor.GOLD : NamedTextColor.GREEN, lore), waiting));
            }
        }
        Material leaf = tool.material(TreePart.LEAF, settings);
        inventory.setItem(SHADE_HELP, item(Material.FLOWERING_AZALEA_LEAVES, "葉のグラデーション", NamedTextColor.GOLD, List.of(
                text("葉の玉の中の高さで、下の段から上の段へブロックが変わる", NamedTextColor.GRAY),
                text("例: 下 = ツツジの葉、上 = 花の咲いたツツジの葉 / シダ / 草", NamedTextColor.GRAY),
                text(gradient.isEmpty() ? "いまは1種類: " + leaf.getKey().getKey() : "いま " + gradient.size() + " 段", NamedTextColor.WHITE),
                text("左の段の並びが、下 → 上", NamedTextColor.GRAY))));
    }

    /** The item that shows a block in the menu: the block itself, or something near it for a block that is no item (a potted plant shows the pot). */
    private static Material icon(Material block) {
        if (block.isItem() && !block.isAir()) return block;
        if (block.name().startsWith("POTTED_")) return Material.FLOWER_POT;
        return Material.PAPER;
    }

    private static ItemStack glint(ItemStack item, boolean on) {
        if (!on) return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != null && event.getClickedInventory() != holder.inventory) {
            if (holder.picking != null) pick(player, holder, event.getCurrentItem());
            return;
        }
        if (event.getClickedInventory() != holder.inventory) return;
        TreeSession session = tool.session(player);
        TreeSettings settings = session.settings;
        int slot = event.getSlot();
        ClickType click = event.getClick();
        boolean left = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT;
        boolean right = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT;
        boolean big = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT;
        if (!left && !right) return;
        // a click anywhere else in the menu stops waiting for a block
        String waited = holder.picking;
        holder.picking = null;
        if (blockClick(player, holder, settings, slot, left, waited)) return;

        for (Page tab : Page.values()) {
            if (tab.shownFor(settings) && tab.tab == slot) {
                holder.page = tab;
                render(player, holder);
                return;
            }
        }
        TreeSettings.Key[] pageKeys = holder.page.keysFor(settings);
        for (int index = 0; index < pageKeys.length; index++) {
            if (GRID[index] != slot) continue;
            TreeSettings.Key key = pageKeys[index];
            if (key.toggle()) settings.set(key, settings.on(key) ? 0 : 1);
            else settings.adjust(key, (left ? 1 : -1) * (big ? key.bigStep() : key.step()));
            tool.limit(settings);
            tool.changed(player);
            render(player, holder);
            return;
        }
        if (holder.page == Page.SHAPE && settings.kind() == ProcKind.CRYSTAL && slot == GRID[13]) {
            settings.cycleCrystalType(left);
            tool.changed(player);
            render(player, holder);
            return;
        }
        switch (slot) {
            case PREVIEW -> tool.preview().toggle(player);
            case GENERATE -> {
                player.closeInventory();
                tool.generate(player);
                return;
            }
            case UNDO -> {
                player.closeInventory();
                tool.undo(player);
                return;
            }
            case REDO -> {
                player.closeInventory();
                tool.redo(player);
                return;
            }
            case CLOSE -> {
                player.closeInventory();
                return;
            }
            default -> {
                if (holder.page != Page.BASIC) return;
                if (!basicClick(player, session, settings, slot, left)) return;
                // (a new kind has other pages: back to the basics)
                if (!holder.page.shownFor(settings)) holder.page = Page.BASIC;
                tool.changed(player);
            }
        }
        render(player, holder);
    }

    /**
     * A click on a block slot (the parts on the basic page, the leaf gradient on the leaf page); false when it was none. A left click waits for a block from the
     * player's inventory (a second left click stops waiting); a right click goes back to the default (a part) or takes the shade away.
     */
    private boolean blockClick(Player player, Holder holder, TreeSettings settings, int slot, boolean left, String waited) {
        String target = null;
        if (holder.page == Page.BASIC) {
            List<TreePart> parts = legendParts(settings);
            for (int index = 0; index < parts.size() && index < LEGEND_SLOTS.length; index++) {
                if (LEGEND_SLOTS[index] != slot) continue;
                TreePart part = parts.get(index);
                if (part == TreePart.LEAF && settings.kind().tree()) {
                    if (left) {
                        holder.page = Page.LEAF;
                    } else {
                        settings.setLeafGradient(List.of());
                        settings.setBlock(TreePart.LEAF, null);
                        tool.changed(player);
                    }
                    render(player, holder);
                    return true;
                }
                target = "part:" + part.name();
                if (!left) {
                    settings.setBlock(part, null);
                    tool.changed(player);
                }
            }
        } else if (holder.page == Page.LEAF) {
            int shades = settings.leafGradient().size();
            for (int index = 0; index < SHADE_SLOTS.length; index++) {
                if (SHADE_SLOTS[index] != slot || index > shades) continue;
                target = "shade:" + index;
                if (!left && index < shades) {
                    settings.removeLeafShade(index);
                    tool.changed(player);
                }
            }
        }
        if (target == null) return false;
        if (left && !target.equals(waited)) {
            holder.picking = target;
            player.sendMessage(ProceduralModule.PREFIX + "インベントリ (画面の下) のブロックをクリックすると、それに決まります。");
        }
        render(player, holder);
        return true;
    }

    /** The player clicked an item of their own inventory while a block slot waits: its block goes there. */
    private void pick(Player player, Holder holder, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        Material type = item.getType();
        if (!type.isBlock()) {
            player.sendMessage(ProceduralModule.PREFIX + type.getKey().getKey() + " は置けるブロックではありません。");
            return;
        }
        TreeSettings settings = tool.session(player).settings;
        String target = holder.picking;
        holder.picking = null;
        if (target.startsWith("part:")) {
            settings.setBlock(TreePart.valueOf(target.substring(5)), type.name());
        } else if (target.startsWith("shade:")) {
            settings.setLeafShade(Integer.parseInt(target.substring(6)), type.name());
        }
        player.sendMessage(ProceduralModule.PREFIX + "ブロックを選びました: " + type.getKey().getKey());
        tool.changed(player);
        render(player, holder);
    }

    /** A click on a button of the basic page; false when it was none. */
    private boolean basicClick(Player player, TreeSession session, TreeSettings settings, int slot, boolean left) {
        switch (slot) {
            case KIND -> {
                ProcKind[] kinds = ProcKind.values();
                settings.setKind(kinds[Math.floorMod(settings.kind().ordinal() + (left ? 1 : -1), kinds.length)]);
                tool.limit(settings);
            }
            case ALGORITHM -> {
                if (settings.kind().web()) {
                    settings.cycleWebShape(left);
                } else {
                    if (!settings.kind().tree()) return false;
                    settings.cycleAlgorithm(left);
                }
            }
            case CROWN -> {
                if (settings.kind().web()) {
                    if (left) {
                        if (!session.points.isEmpty()) session.points.remove(session.points.size() - 1);
                    } else {
                        session.points.clear();
                        session.pointsWorld = null;
                    }
                    tool.markPoints(player, session);
                } else {
                    return false;
                }
            }
            case SEED -> {
                // (with the preview on the seed is never "random": the right click gives a new fixed one then)
                if (left || session.preview) {
                    settings.setSeed(1 + (new Random().nextLong() & 0xFFFFFFL));
                    session.autoSeed = false;
                } else {
                    settings.setSeed(0L);
                }
            }
            case LOAD -> {
                List<String> saved = tool.store().list();
                if (saved.isEmpty()) {
                    player.sendMessage("[木] 保存した設定がありません。/treetool save <名前> で保存できます。");
                    return false;
                }
                int current = saved.indexOf(settings.name());
                int next = Math.floorMod(current + (left ? 1 : -1), saved.size());
                TreeSettings loaded = tool.store().load(saved.get(next));
                if (loaded == null) {
                    player.sendMessage("[木] 読み込めませんでした: " + saved.get(next));
                    return false;
                }
                tool.limit(loaded);
                session.settings = loaded;
                session.autoSeed = false;
            }
            case RESET -> {
                TreeSettings fresh = new TreeSettings();
                fresh.setName(settings.name());
                fresh.setKind(settings.kind());
                fresh.setAlgorithm(settings.algorithm());
                session.settings = fresh;
                session.autoSeed = false;
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }
}
