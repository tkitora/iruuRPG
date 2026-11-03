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
    private static final int[] LEGEND_SLOTS = {37, 38, 39, 40, 41, 42, 43, 28, 29, 30, 31, 32, 33, 34};
    // the leaf page: the leaf gradient from the bottom of a clump to its top, and its help
    private static final int[] SHADE_SLOTS = {28, 29, 30, 31, 32, 33};
    private static final int SHADE_HELP = 34;
    // the page of the parts added on a house (t181): the list (and the button that adds one), then the numbers of the one chosen
    private static final int[] MODULE_SLOTS = {11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int M_TYPE = 28;
    private static final int M_SIDE = 29;
    private static final int M_ALONG = 30;
    private static final int M_OFFSET = 31;
    private static final int M_WIDTH = 32;
    private static final int M_DEPTH = 33;
    private static final int M_HEIGHT = 34;
    private static final int M_LEVEL = 37;
    private static final int M_ON = 38;
    private static final int M_COPY = 39;
    private static final int M_DELETE = 41;
    /** (t246) the button that gives the part wand (on the bottom row of the parts page). */
    private static final int M_WAND = 46;
    /** (t190) the numbers only some kinds have (a volume's roof, ridge and jetty; a tower's shape and jetty; the way stairs go up). */
    private static final int[] M_EXTRA = {40, 42, 43};
    // the roof page of a roof of steps (t185): the steps from the eaves to the ridge, the shapes to choose from, the section of this house
    private static final int[] STEP_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41};
    private static final int STEP_PRESET = 15;
    private static final int STEP_VIEW = 16;
    // the page of the random houses (t185)
    private static final int D_BACK = 11;
    private static final int D_NEW = 12;
    private static final int D_PARTS = 13;
    private static final int D_NEXT = 14;
    private static final int D_GUIDE = 16;

    /** The pages: the slot of the tab, its icon and name, and the numbers it holds. */
    enum Page {
        BASIC(2, Material.OAK_SAPLING, "基本", new TreeSettings.Key[]{TreeSettings.Key.HEIGHT, TreeSettings.Key.WIDTH}),
        TRUNK(3, Material.OAK_LOG, "幹・根", new TreeSettings.Key[]{}),
        BRANCH(4, Material.STICK, "枝", new TreeSettings.Key[]{}),
        LEAF(5, Material.OAK_LEAVES, "葉", new TreeSettings.Key[]{}),
        /** The settings of everything that is not a tree (which of them depends on the kind). */
        SHAPE(3, Material.COBBLESTONE, "形", new TreeSettings.Key[]{}),
        /** (t181) the roof of a house. */
        ROOF(4, Material.RED_WOOL, "屋根", new TreeSettings.Key[]{TreeSettings.Key.HOUSE_ROOF, TreeSettings.Key.HOUSE_PITCH, TreeSettings.Key.HOUSE_OVERHANG,
                TreeSettings.Key.HOUSE_RIDGE, TreeSettings.Key.HOUSE_RIDGE_BEAM, TreeSettings.Key.HOUSE_SKIRT}),
        /** (t181) the parts added on a house (HouseModule): its own buttons, no numbers of the settings. */
        MODULES(5, Material.SPRUCE_TRAPDOOR, "追加パーツ", new TreeSettings.Key[]{}),
        /** (t185) the random houses (HouseDesigner): the rules, and the buttons that make one and go back. */
        DESIGN(6, Material.NETHER_STAR, "おまかせ生成", new TreeSettings.Key[]{TreeSettings.Key.HOUSE_GEN_PATTERN, TreeSettings.Key.HOUSE_GEN_SIZE,
                TreeSettings.Key.HOUSE_GEN_DETAIL, TreeSettings.Key.HOUSE_GEN_SYMMETRY, TreeSettings.Key.HOUSE_GEN_TOWERS, TreeSettings.Key.HOUSE_GEN_PLAN, TreeSettings.Key.HOUSE_GEN_OVERLAP,
                TreeSettings.Key.HOUSE_GEN_SHAPE, TreeSettings.Key.HOUSE_GEN_ROOF, TreeSettings.Key.HOUSE_GEN_LOOK}),
        /** (t190) the walls of a house: stone, timber, the depth of the walls and the windows. */
        WALLS(7, Material.STRIPPED_SPRUCE_LOG, "壁", new TreeSettings.Key[]{TreeSettings.Key.HOUSE_STONE, TreeSettings.Key.HOUSE_PLINTH, TreeSettings.Key.HOUSE_TIMBER,
                TreeSettings.Key.HOUSE_RECESS, TreeSettings.Key.HOUSE_WINDOWS, TreeSettings.Key.HOUSE_GLASS});

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
                case ROOF, MODULES, DESIGN, WALLS -> kind == ProcKind.HOUSE;
                case BRANCH -> kind.tree() && !settings.grows("palm");
                default -> kind.tree();
            };
        }

        /** The numbers of the page for these settings (the kind, and for a tree its algorithm). */
        TreeSettings.Key[] keysFor(TreeSettings settings) {
            ProcKind kind = settings.kind();
            // (a web has no height and width: the points decide how big it is; a tree is as big as its own numbers make it)
            if (this == BASIC && (kind.web() || kind.tree() || kind == ProcKind.HOUSE || kind == ProcKind.LOBBY)) return new TreeSettings.Key[]{};
            if (kind.tree() && this != BASIC) return treeKeys(settings.algorithm(), this);
            // (a roof of steps has one more: how its steps fit the house)
            if (this == ROOF && stepped(settings)) {
                return new TreeSettings.Key[]{TreeSettings.Key.HOUSE_ROOF, TreeSettings.Key.HOUSE_PITCH, TreeSettings.Key.HOUSE_OVERHANG, TreeSettings.Key.HOUSE_RIDGE,
                        TreeSettings.Key.HOUSE_RIDGE_BEAM, TreeSettings.Key.HOUSE_SKIRT, TreeSettings.Key.HOUSE_STEP_FIT};
            }
            if (this != SHAPE) return keys;
            return switch (kind) {
                case WEB -> new TreeSettings.Key[]{TreeSettings.Key.WEB_RINGS, TreeSettings.Key.WEB_SPOKES, TreeSettings.Key.WEB_SAG, TreeSettings.Key.WEB_IRREG, TreeSettings.Key.WEB_REACH};
                case ROCK -> new TreeSettings.Key[]{TreeSettings.Key.COUNT, TreeSettings.Key.ROUGHNESS, TreeSettings.Key.LAYERS, TreeSettings.Key.MOSS};
                case SPIKE -> new TreeSettings.Key[]{TreeSettings.Key.SPIKE_SIDES, TreeSettings.Key.SPIKE_TWIST, TreeSettings.Key.SPIKE_STAR, TreeSettings.Key.SPIKE_TAPER, TreeSettings.Key.CURVE,
                        TreeSettings.Key.ROUGHNESS, TreeSettings.Key.LAYERS};
                case CRYSTAL -> new TreeSettings.Key[]{TreeSettings.Key.COUNT, TreeSettings.Key.CRYSTAL_FACETS, TreeSettings.Key.CRYSTAL_TIP, TreeSettings.Key.CRYSTAL_THICK, TreeSettings.Key.CRYSTAL_VARIANCE,
                        TreeSettings.Key.SPREAD, TreeSettings.Key.ROUGHNESS};
                case HOUSE -> new TreeSettings.Key[]{TreeSettings.Key.HOUSE_WIDTH, TreeSettings.Key.HOUSE_DEPTH, TreeSettings.Key.HOUSE_FLOORS, TreeSettings.Key.HOUSE_STOREY,
                        TreeSettings.Key.HOUSE_PILLARS, TreeSettings.Key.HOUSE_PILLAR_MIRROR, TreeSettings.Key.HOUSE_ROTATION, TreeSettings.Key.HOUSE_JETTY,
                        TreeSettings.Key.HOUSE_JETTY_SIDES, TreeSettings.Key.HOUSE_BRACKETS, TreeSettings.Key.HOUSE_DOOR, TreeSettings.Key.HOUSE_FOUNDATION,
                        TreeSettings.Key.HOUSE_RAISE, TreeSettings.Key.HOUSE_TERRACE};
                case LOBBY -> new TreeSettings.Key[]{TreeSettings.Key.LOBBY_RADIUS, TreeSettings.Key.LOBBY_SHAPE, TreeSettings.Key.LOBBY_HOUSES, TreeSettings.Key.LOBBY_GAP,
                        TreeSettings.Key.LOBBY_ORGANIC, TreeSettings.Key.LOBBY_CENTER, TreeSettings.Key.LOBBY_BEDS, TreeSettings.Key.LOBBY_TREES, TreeSettings.Key.LOBBY_PATHS,
                        TreeSettings.Key.LOBBY_TERRAIN, TreeSettings.Key.HOUSE_GEN_PATTERN, TreeSettings.Key.HOUSE_GEN_DETAIL};
                default -> new TreeSettings.Key[]{};
            };
        }
    }

    /** Whether the house's roof is one of steps (9, 10: its section from the steps). */
    static boolean stepped(TreeSettings settings) {
        int roof = settings.getInt(TreeSettings.Key.HOUSE_ROOF);
        return settings.kind() == ProcKind.HOUSE && (roof == 9 || roof == 10);
    }

    /** The numbers of a page of a tree grown by {@code algorithm}. */
    static TreeSettings.Key[] treeKeys(String algorithm, Page page) {
        return switch (algorithm) {
            case "tiered" -> switch (page) {
                case TRUNK -> new TreeSettings.Key[]{TreeSettings.Key.TIER_HEIGHT, TreeSettings.Key.TIER_STUMP, TreeSettings.Key.TIER_RADIUS, TreeSettings.Key.TIER_TWIST,
                        TreeSettings.Key.TIER_BRAIDED, TreeSettings.Key.TIER_BRAID_INTENSITY, TreeSettings.Key.TIER_BRAID_WOBBLE, TreeSettings.Key.TIER_ROOT_FLARE,
                        TreeSettings.Key.TIER_WIND, TreeSettings.Key.TIER_WIND_DIR};
                case BRANCH -> new TreeSettings.Key[]{TreeSettings.Key.TIER_SPACING, TreeSettings.Key.TIER_BRANCHES, TreeSettings.Key.TIER_STAGGER, TreeSettings.Key.TIER_BRANCH_LENGTH,
                        TreeSettings.Key.TIER_SAG};
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
        ICONS.put(TreeSettings.Key.HOUSE_WIDTH, Material.LEAD);
        ICONS.put(TreeSettings.Key.HOUSE_DEPTH, Material.LEAD);
        ICONS.put(TreeSettings.Key.HOUSE_FLOORS, Material.LADDER);
        ICONS.put(TreeSettings.Key.HOUSE_STOREY, Material.SCAFFOLDING);
        ICONS.put(TreeSettings.Key.HOUSE_PILLARS, Material.BROWN_WOOL);
        ICONS.put(TreeSettings.Key.HOUSE_ROOF, Material.RED_WOOL);
        ICONS.put(TreeSettings.Key.HOUSE_PITCH, Material.OAK_STAIRS);
        ICONS.put(TreeSettings.Key.HOUSE_OVERHANG, Material.OAK_SLAB);
        ICONS.put(TreeSettings.Key.HOUSE_WINDOWS, Material.GLASS_PANE);
        ICONS.put(TreeSettings.Key.HOUSE_DOOR, Material.OAK_DOOR);
        ICONS.put(TreeSettings.Key.HOUSE_FOUNDATION, Material.STONE_BRICKS);
        ICONS.put(TreeSettings.Key.HOUSE_RIDGE, Material.END_ROD);
        ICONS.put(TreeSettings.Key.HOUSE_ROTATION, Material.COMPASS);
        ICONS.put(TreeSettings.Key.HOUSE_JETTY, Material.DARK_OAK_SLAB);
        ICONS.put(TreeSettings.Key.HOUSE_STONE, Material.COBBLESTONE);
        ICONS.put(TreeSettings.Key.HOUSE_TIMBER, Material.STRIPPED_SPRUCE_LOG);
        ICONS.put(TreeSettings.Key.HOUSE_PILLAR_MIRROR, Material.ARMOR_STAND);
        ICONS.put(TreeSettings.Key.HOUSE_RAISE, Material.STONE_BRICK_STAIRS);
        ICONS.put(TreeSettings.Key.HOUSE_TERRACE, Material.STONE_BRICK_SLAB);
        ICONS.put(TreeSettings.Key.HOUSE_JETTY_SIDES, Material.SPRUCE_SLAB);
        ICONS.put(TreeSettings.Key.HOUSE_BRACKETS, Material.SPRUCE_FENCE);
        ICONS.put(TreeSettings.Key.HOUSE_RECESS, Material.SPRUCE_TRAPDOOR);
        ICONS.put(TreeSettings.Key.HOUSE_GLASS, Material.LIGHT_BLUE_STAINED_GLASS);
        ICONS.put(TreeSettings.Key.HOUSE_PLINTH, Material.STONE_BRICKS);
        ICONS.put(TreeSettings.Key.HOUSE_SKIRT, Material.BRICK_SLAB);
        ICONS.put(TreeSettings.Key.HOUSE_RIDGE_BEAM, Material.DARK_OAK_LOG);
        ICONS.put(TreeSettings.Key.LOBBY_RADIUS, Material.LIGHT_GRAY_CONCRETE);
        ICONS.put(TreeSettings.Key.LOBBY_SHAPE, Material.CLAY_BALL);
        ICONS.put(TreeSettings.Key.LOBBY_HOUSES, Material.BRICKS);
        ICONS.put(TreeSettings.Key.LOBBY_GAP, Material.LEAD);
        ICONS.put(TreeSettings.Key.LOBBY_ORGANIC, Material.MOSS_BLOCK);
        ICONS.put(TreeSettings.Key.LOBBY_CENTER, Material.WATER_BUCKET);
        ICONS.put(TreeSettings.Key.LOBBY_BEDS, Material.POPPY);
        ICONS.put(TreeSettings.Key.LOBBY_TREES, Material.OAK_SAPLING);
        ICONS.put(TreeSettings.Key.LOBBY_PATHS, Material.DIRT_PATH);
        ICONS.put(TreeSettings.Key.LOBBY_TERRAIN, Material.GRASS_BLOCK);
        ICONS.put(TreeSettings.Key.HOUSE_STEP_FIT, Material.SCAFFOLDING);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_PATTERN, Material.PAINTING);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_SIZE, Material.PISTON);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_DETAIL, Material.CHEST);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_SYMMETRY, Material.ARMOR_STAND);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_TOWERS, Material.CHISELED_STONE_BRICKS);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_OVERLAP, Material.SLIME_BLOCK);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_SHAPE, Material.LEAD);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_ROOF, Material.RED_WOOL);
        ICONS.put(TreeSettings.Key.HOUSE_GEN_LOOK, Material.STRIPPED_SPRUCE_LOG);
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
        ICONS.put(TreeSettings.Key.TIER_STAGGER, Material.BAMBOO);
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

    /** A setting whose numbers stand for names (the shape of the roof, the way of the ridge, the rules of the random houses). */
    private static boolean named(TreeSettings.Key key) {
        return switch (key) {
            case HOUSE_ROOF, HOUSE_RIDGE, HOUSE_GEN_PATTERN, HOUSE_GEN_SIZE, HOUSE_GEN_SYMMETRY, HOUSE_GEN_TOWERS, HOUSE_GEN_PLAN, HOUSE_GEN_OVERLAP, LOBBY_SHAPE, LOBBY_CENTER -> true;
            default -> false;
        };
    }

    private static String number(TreeSettings.Key key, double value) {
        if (key.toggle()) return value >= 0.5 ? "オン" : "オフ";
        if (key == TreeSettings.Key.HOUSE_ROOF) return HouseShape.roofName((int) Math.round(value));
        if (key == TreeSettings.Key.HOUSE_RIDGE) return HouseShape.ridgeName((int) Math.round(value));
        if (key == TreeSettings.Key.HOUSE_ROTATION) return (int) Math.round(value) + "°";
        int whole = (int) Math.round(value);
        if (key == TreeSettings.Key.HOUSE_GEN_PATTERN) return HouseDesigner.Pattern.of(whole).label;
        if (key == TreeSettings.Key.HOUSE_GEN_SIZE) return HouseDesigner.sizeName(whole);
        if (key == TreeSettings.Key.HOUSE_GEN_SYMMETRY) return HouseDesigner.symmetryName(whole);
        if (key == TreeSettings.Key.HOUSE_GEN_TOWERS) return HouseDesigner.towersName(whole);
        if (key == TreeSettings.Key.HOUSE_GEN_PLAN) return HouseDesigner.planName(whole);
        if (key == TreeSettings.Key.HOUSE_GEN_OVERLAP) return HouseDesigner.overlapName(whole);
        if (key == TreeSettings.Key.LOBBY_SHAPE) return LobbyShape.shapeName(whole);
        if (key == TreeSettings.Key.LOBBY_CENTER) return LobbyShape.centerName(whole);
        return key.whole() ? Integer.toString((int) Math.round(value)) : String.format(Locale.ROOT, "%.2f", value);
    }

    private void render(Player player, Holder holder) {
        Inventory inventory = holder.inventory;
        TreeSession session = tool.session(player);
        TreeSettings settings = session.settings;
        Page page = holder.page;
        session.lastPage = page.name();
        // (t246: the parts page opened or left, a part chosen: the glass follows)
        tool.refreshHighlightLater(player);
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
        else if (page == Page.MODULES) renderModules(inventory, session, settings);
        else if (page == Page.DESIGN) renderDesign(inventory, session, settings);
        else inventory.setItem(INFO, item(page.icon, page.label + "の設定", NamedTextColor.GOLD, List.of(
                text("左クリック: 増やす / 右クリック: 減らす", NamedTextColor.GRAY),
                text("Shift を押しながらで、大きく動かす", NamedTextColor.GRAY),
                text("プレビュー中は、変えて 0.75 秒後に木が変わる", NamedTextColor.GRAY))));
        if (page == Page.LEAF) renderShades(inventory, holder, settings);
        if (page == Page.ROOF && stepped(settings)) renderSteps(inventory, settings);

        TreeSettings.Key[] shownKeys = page.keysFor(settings);
        for (int index = 0; index < shownKeys.length; index++) {
            TreeSettings.Key key = shownKeys[index];
            List<Component> lore = new ArrayList<>();
            lore.add(text(key.description(), NamedTextColor.GRAY));
            if (key.toggle()) {
                lore.add(text("現在: " + number(key, settings.get(key)), NamedTextColor.WHITE));
                lore.add(text("クリックで切り替え", NamedTextColor.YELLOW));
            } else if (named(key)) {
                // (a setting whose numbers are names: a click goes to the next name, not "+ a name")
                lore.add(text("現在: " + number(key, settings.get(key)) + "  (" + number(key, key.min()) + " 〜 " + number(key, key.max()) + ")", NamedTextColor.WHITE));
                lore.add(text("左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW));
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
        if (holder.page == Page.MODULES) {
            if (slot == M_WAND) {
                tool.giveWand(player);
                return;
            }
            if (moduleClick(session, settings, slot, left, big)) {
                tool.changed(player);
                render(player, holder);
                return;
            }
        }
        if (holder.page == Page.ROOF && stepped(settings) && stepClick(settings, slot, left, big)) {
            tool.changed(player);
            render(player, holder);
            return;
        }
        if (holder.page == Page.DESIGN) {
            switch (slot) {
                case D_NEW, D_PARTS -> {
                    // (the house comes a moment later, off the main thread: the menu is shown again then)
                    tool.design(player, slot == D_PARTS, 0L);
                    return;
                }
                case D_BACK, D_NEXT -> {
                    if (!tool.stepDesign(player, slot == D_NEXT ? 1 : -1)) player.sendMessage(ProceduralModule.PREFIX + (slot == D_NEXT ? "次の案はありません。" : "前の案はありません。"));
                    render(player, holder);
                    return;
                }
                default -> {
                }
            }
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

    /** Shows the menu again when the player has it open (a random house came in). */
    void refresh(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder) render(player, holder);
    }

    /** (t246) Whether the player has the parts page of the menu open (the part chosen is then shown in glass). */
    boolean onParts(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder() instanceof Holder holder && holder.page == Page.MODULES;
    }

    @EventHandler
    public void onClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder && event.getPlayer() instanceof Player player) tool.refreshHighlightLater(player);
    }

    /**
     * The page of the random houses (t185): the rules under (the pattern, the size, how many parts, the same both ways or not, the towers, how much the
     * parts may cover each other, whether the roof and the look are chosen too), and over them the buttons: a new house, new parts on this base, and back
     * and forth through the houses made (the one shown keeps what was changed by hand).
     */
    private void renderDesign(Inventory inventory, TreeSession session, TreeSettings settings) {
        HouseDesigner.Pattern pattern = HouseDesigner.Pattern.of(settings.getInt(TreeSettings.Key.HOUSE_GEN_PATTERN));
        boolean any = !session.designs.isEmpty() && session.design >= 0;
        inventory.setItem(INFO, item(Material.NETHER_STAR, "おまかせ生成", NamedTextColor.GOLD, List.of(
                text("パターンと下の決まりで、家の土台・屋根・追加パーツを組み合わせて作る", NamedTextColor.GRAY),
                text("パーツは重なりを調べて置く。毎回 " + HouseDesigner.TRIES + " 通り作って、いちばんよいものを残す", NamedTextColor.GRAY),
                text("できた家は、手で作った家と同じ。形・屋根・追加パーツのページで、そのまま続きを編集できる", NamedTextColor.GRAY),
                text(any ? "いまの案: " + (session.design + 1) + " / " + session.designs.size() : "まだ案はありません", NamedTextColor.WHITE),
                text(settings.designSeed() > 0L ? "この家の seed: " + settings.designSeed() + " (/proceduraltool random <パターン> <seed> で同じ家)" : "この家は、おまかせ生成で作ったものではありません", NamedTextColor.WHITE))));
        boolean back = any && session.design > 0;
        boolean next = any && session.design < session.designs.size() - 1;
        inventory.setItem(D_BACK, item(back ? Material.SPECTRAL_ARROW : Material.GRAY_DYE, "◀ 前の案", back ? NamedTextColor.AQUA : NamedTextColor.GRAY, List.of(
                text("ひとつ前に作った案に戻る (いまの案は、手で変えた所ごと残る)", NamedTextColor.GRAY),
                text("最初の案の前は、おまかせ生成を使う前の設定", NamedTextColor.GRAY))));
        inventory.setItem(D_NEW, item(Material.NETHER_STAR, "新しい案を出す", NamedTextColor.GREEN, List.of(
                text("「" + pattern.label + "」で、土台・屋根・追加パーツを全部作る", NamedTextColor.GRAY),
                text("屋根・外観のおまかせをオフにすると、その部分は今のまま", NamedTextColor.GRAY),
                text("気に入らなければ何度でも。戻るのは ◀ (" + ProceduralModule.MAX_DESIGNS + " 案まで)", NamedTextColor.GRAY),
                text("クリックで作る", NamedTextColor.YELLOW))));
        inventory.setItem(D_PARTS, item(Material.SPRUCE_TRAPDOOR, "パーツだけ振り直す", NamedTextColor.GREEN, List.of(
                text("土台 (形・屋根) は今のままで、追加パーツだけを「" + pattern.label + "」の決まりで作り直す", NamedTextColor.GRAY),
                text("いまの追加パーツは、新しいものに置き換わる (◀ で戻せる)", NamedTextColor.GRAY),
                text("クリックで作る", NamedTextColor.YELLOW))));
        inventory.setItem(D_NEXT, item(next ? Material.SPECTRAL_ARROW : Material.GRAY_DYE, "次の案 ▶", next ? NamedTextColor.AQUA : NamedTextColor.GRAY, List.of(
                text("◀ で戻ったあと、後の案へ進む", NamedTextColor.GRAY))));
        List<Component> guide = new ArrayList<>();
        for (HouseDesigner.Pattern each : HouseDesigner.Pattern.values()) {
            guide.add(text((each == pattern ? "▶ " : "   ") + each.label, each == pattern ? NamedTextColor.GOLD : NamedTextColor.WHITE));
            guide.add(text("      " + each.description, NamedTextColor.GRAY));
        }
        guide.add(text("パターンは下の段の絵で選ぶ (左クリック: 次 / 右クリック: 前)", NamedTextColor.YELLOW));
        inventory.setItem(D_GUIDE, item(Material.BOOK, "パターン: " + pattern.label, NamedTextColor.GOLD, guide));
    }

    /**
     * The steps of a roof of steps on the roof page (t185): one item a step from the eaves to the ridge (as many as the step is high), one to add a step, the
     * shapes to choose from, and the section the steps give this house.
     */
    private void renderSteps(Inventory inventory, TreeSettings settings) {
        List<Integer> steps = settings.roofSteps();
        for (int index = 0; index < STEP_SLOTS.length; index++) {
            if (index < steps.size()) {
                int value = steps.get(index);
                ItemStack step = item(value == 0 ? Material.GRAY_STAINED_GLASS_PANE : Material.RED_WOOL, "段 " + (index + 1) + ": " + value, NamedTextColor.GREEN, List.of(
                        text(index == 0 ? "軒の列 (2 以上で、軒がその分だけ下へ垂れる)" : index == steps.size() - 1 ? "棟の列 (いちばん上)" : "軒から " + (index + 1) + " 列目", NamedTextColor.GRAY),
                        text("その列で屋根が何ブロック上がるか (見える段の高さ)", NamedTextColor.GRAY),
                        text("左クリック +1 / 右クリック -1 (0 〜 " + TreeSettings.MAX_STEP + ")", NamedTextColor.YELLOW),
                        text("Shift+左クリック: この段を複製 / Shift+右クリック: この段を消す", NamedTextColor.YELLOW)));
                step.setAmount(Math.max(1, value));
                inventory.setItem(STEP_SLOTS[index], step);
            } else if (index == steps.size()) {
                inventory.setItem(STEP_SLOTS[index], item(Material.LIME_STAINED_GLASS_PANE, "＋ 段を足す", NamedTextColor.GREEN, List.of(
                        text("棟の側に 1 段足す (いまの棟の段と同じ高さ)", NamedTextColor.GRAY),
                        text("クリックで足す (最大 " + TreeSettings.MAX_STEPS + " 段)", NamedTextColor.YELLOW))));
            }
        }
        List<Component> presets = new ArrayList<>();
        presets.add(text("決まった段の形から選ぶ (選んだあと、下の段で細かく変えられる)", NamedTextColor.GRAY));
        for (HouseShape.StepPreset preset : HouseShape.STEP_PRESETS) {
            boolean here = preset.steps().equals(steps);
            presets.add(text((here ? "▶ " : "   ") + preset.label() + ": " + joined(preset.steps()), here ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        }
        presets.add(text("左クリック: 次の形 / 右クリック: 前の形", NamedTextColor.YELLOW));
        presets.add(text("(コマンド: /proceduraltool set house-roof-steps 1 1 1 2 3 3 3 2 1 1 1 のように断面全体でも)", NamedTextColor.DARK_GRAY));
        inventory.setItem(STEP_PRESET, item(Material.BOOK, "段の形を選ぶ", NamedTextColor.GOLD, presets));
        inventory.setItem(STEP_VIEW, item(Material.PAINTING, "この家の屋根の断面", NamedTextColor.GOLD, section(settings)));
    }

    private static String joined(List<Integer> values) {
        StringBuilder text = new StringBuilder();
        for (int value : values) text.append(text.isEmpty() ? "" : " ").append(value);
        return text.toString();
    }

    /** The section of the house's roof of steps, across its ridge: the steps of each column, the heights, and a picture of it. */
    private static List<Component> section(TreeSettings settings) {
        HouseShape.Steps steps = HouseShape.Steps.of(settings.roofSteps(), settings.on(TreeSettings.Key.HOUSE_STEP_FIT));
        int width = settings.getInt(TreeSettings.Key.HOUSE_WIDTH);
        int depth = settings.getInt(TreeSettings.Key.HOUSE_DEPTH) + 2 * Math.min(3, Math.max(0, settings.getInt(TreeSettings.Key.HOUSE_FLOORS) - 1) * settings.getInt(TreeSettings.Key.HOUSE_JETTY));
        int overhang = settings.getInt(TreeSettings.Key.HOUSE_OVERHANG);
        int spanA = width - 1 + 2 * overhang;
        int spanB = depth - 1 + 2 * overhang;
        int ridge = settings.getInt(TreeSettings.Key.HOUSE_RIDGE);
        boolean alongA = ridge == 1 || ridge == 0 && spanA >= spanB;
        int span = settings.getInt(TreeSettings.Key.HOUSE_ROOF) == 10 ? Math.min(spanA, spanB) : alongA ? spanB : spanA;
        int reach = span / 2;
        int[] heights = new int[span + 1];
        StringBuilder shown = new StringBuilder();
        StringBuilder high = new StringBuilder();
        for (int column = 0; column <= span; column++) {
            int d = Math.min(column, span - column);
            heights[column] = steps.rise(d, reach);
            int step = d == 0 ? steps.at(0, reach) : heights[column] - steps.rise(d - 1, reach);
            shown.append(column == 0 ? "" : " ").append(step);
            high.append(column == 0 ? "" : " ").append(heights[column]);
        }
        List<Component> lore = new ArrayList<>();
        lore.add(text("棟と直角に切った断面 (軒の出を含めて " + (span + 1) + " 列)", NamedTextColor.GRAY));
        lore.add(text("段: " + shown, NamedTextColor.WHITE));
        lore.add(text("高さ: " + high, NamedTextColor.WHITE));
        int top = 0;
        for (int h : heights) top = Math.max(top, h);
        int scale = Math.max(1, (int) Math.ceil(top / 12.0));
        for (int row = top / scale; row >= 0; row--) {
            StringBuilder line = new StringBuilder();
            for (int h : heights) line.append(h >= row * scale ? "█" : "░");
            lore.add(text(line.toString(), NamedTextColor.RED));
        }
        if (scale > 1) lore.add(text("(高いので、1 行 = " + scale + " ブロック)", NamedTextColor.DARK_GRAY));
        return lore;
    }

    /** A click on the steps of a roof of steps (the roof page); false when it was on none of them. */
    private static boolean stepClick(TreeSettings settings, int slot, boolean left, boolean big) {
        List<Integer> steps = new ArrayList<>(settings.roofSteps());
        if (slot == STEP_PRESET) {
            List<HouseShape.StepPreset> presets = HouseShape.STEP_PRESETS;
            int current = -1;
            for (int index = 0; index < presets.size(); index++) if (presets.get(index).steps().equals(steps)) current = index;
            int next = current < 0 ? (left ? 0 : presets.size() - 1) : Math.floorMod(current + (left ? 1 : -1), presets.size());
            settings.setRoofSteps(presets.get(next).steps());
            return true;
        }
        for (int index = 0; index < STEP_SLOTS.length; index++) {
            if (STEP_SLOTS[index] != slot) continue;
            if (index < steps.size()) {
                if (big && left) {
                    if (steps.size() >= TreeSettings.MAX_STEPS) return false;
                    steps.add(index + 1, steps.get(index));
                } else if (big) {
                    if (steps.size() <= 1) return false;
                    steps.remove(index);
                } else {
                    steps.set(index, Math.max(0, Math.min(TreeSettings.MAX_STEP, steps.get(index) + (left ? 1 : -1))));
                }
            } else if (index == steps.size() && steps.size() < TreeSettings.MAX_STEPS) {
                steps.add(steps.get(steps.size() - 1));
            } else {
                return false;
            }
            settings.setRoofSteps(steps);
            return true;
        }
        return false;
    }

    /** The item that stands for a kind of part of a house. */
    private static Material moduleIcon(HouseModule.Type type) {
        return switch (type) {
            case DORMER -> Material.GLASS_PANE;
            case CHIMNEY -> Material.CAMPFIRE;
            case TOWER -> Material.CHISELED_STONE_BRICKS;
            case WING -> Material.OAK_PLANKS;
            case PORCH -> Material.OAK_FENCE;
            case BALCONY -> Material.SPRUCE_FENCE;
            case BAY -> Material.GLASS;
            case VOLUME -> Material.BRICKS;
            case STAIR -> Material.STONE_BRICK_STAIRS;
        };
    }

    /** (t190) A number only some kinds of part have, with its slot's icon, its name and how a click steps it. */
    private enum ModuleExtra {
        ROOF(Material.RED_WOOL, "屋根の形", "棟の屋根 (家と同じ / 平ら / 切妻 / … / 尖り寄棟)"),
        RIDGE(Material.END_ROD, "棟の向き", "棟の屋根の棟が、付けた面と平行か直角か (自動は長い辺に沿う)"),
        JETTY(Material.DARK_OAK_SLAB, "張り出し", "棟の上の階の張り出し (家と同じ / 0〜3)。塔では、いちばん上の階の張り出し"),
        SHAPE(Material.CHISELED_STONE_BRICKS, "塔の形", "丸 / 八角 / 四角 (四角はピラミッドの屋根)"),
        SPIRE(Material.POINTED_DRIPSTONE, "屋根の高さ", "塔の屋根 (家の屋根なり / まっすぐな尖り屋根: 塔の幅の約 1.1 倍・1.5 倍・1.9 倍の高さ。t239)"),
        UP(Material.STONE_BRICK_STAIRS, "上がる向き", "外から壁を見て、右へ上がるか左へ上がるか");

        final Material icon;
        final String label;
        final String description;

        ModuleExtra(Material icon, String label, String description) {
            this.icon = icon;
            this.label = label;
            this.description = description;
        }

        static List<ModuleExtra> of(HouseModule.Type type) {
            return switch (type) {
                case VOLUME -> List.of(ROOF, RIDGE, JETTY);
                case TOWER -> List.of(SHAPE, JETTY, SPIRE);
                case STAIR -> List.of(UP);
                default -> List.of();
            };
        }

        String text(HouseModule module) {
            return switch (this) {
                case ROOF -> module.roof < 0 ? "家と同じ" : HouseShape.roofName(module.roof);
                case RIDGE -> HouseShape.ridgeName(module.ridge);
                case JETTY -> module.jetty < 0 ? "家と同じ" : Integer.toString(module.jetty);
                case SHAPE -> switch (module.shape) {
                    case 1 -> "八角";
                    case 2 -> "四角";
                    default -> "丸";
                };
                case UP -> module.shape == 1 ? "左へ" : "右へ";
                case SPIRE -> switch (module.spire) {
                    case 1 -> "尖り屋根 低 (幅の約 1.1 倍)";
                    case 2 -> "尖り屋根 中 (幅の約 1.5 倍)";
                    case 3 -> "尖り屋根 高 (幅の約 1.9 倍)";
                    default -> "家の屋根なり";
                };
            };
        }

        void step(HouseModule module, int sign) {
            switch (this) {
                case ROOF -> module.roof = Math.floorMod(module.roof + 1 + sign, 12) - 1;
                case RIDGE -> module.ridge = Math.floorMod(module.ridge + sign, 3);
                case JETTY -> module.jetty = module.type == HouseModule.Type.TOWER ? Math.floorMod(module.jetty + sign, 3) : Math.floorMod(module.jetty + 1 + sign, 5) - 1;
                case SHAPE -> module.shape = Math.floorMod(module.shape + sign, 3);
                case UP -> module.shape = 1 - Math.max(0, Math.min(1, module.shape));
                case SPIRE -> module.spire = Math.floorMod(module.spire + sign, 4);
            }
        }
    }

    /** The place of a part, in words: its side, how far along it and out from it. */
    private static String modulePlace(HouseModule module) {
        return HouseModule.sideLabel(module.side) + " / 横 " + signed(module.along) + " / 壁から " + signed(module.offset);
    }

    private static String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    /**
     * The page of the parts added on a house (t181): the list of them (left click chooses one, right click turns it off and on) with a button that adds
     * one, and under it the numbers of the one chosen: its kind, side, place, size and storey, and buttons to turn it off, to copy it (mirrored) and to
     * take it away. The base of the house is never changed here: every part is made again on it from the list.
     */
    private void renderModules(Inventory inventory, TreeSession session, TreeSettings settings) {
        List<HouseModule> modules = settings.modules();
        if (session.module >= modules.size()) session.module = modules.size() - 1;
        inventory.setItem(INFO, item(Material.SPRUCE_TRAPDOOR, "追加パーツ (" + modules.size() + " / " + TreeSettings.MAX_MODULES + ")", NamedTextColor.GOLD, List.of(
                text("家の土台に、ドーマー窓・煙突・塔・翼棟・ポーチ・バルコニー・出窓・棟・外階段を足す", NamedTextColor.GRAY),
                text("土台は書き換えない: パーツは毎回、土台の上に作り直す (消す・オフにすると元どおり)", NamedTextColor.GRAY),
                text("プレビュー中は、変えて 0.75 秒後に家が変わる。このページを開いている間と杖を持っている間、選んだパーツはステンドグラスで見える", NamedTextColor.GRAY),
                text("上の列: 左クリックで選ぶ / 右クリックでオン・オフ。下の列: 選んだパーツの数字", NamedTextColor.YELLOW))));
        inventory.setItem(M_WAND, item(Material.BLAZE_ROD, "パーツの杖", NamedTextColor.LIGHT_PURPLE, List.of(
                text("家のプレビューや地面を指して (遠くても)、パーツを選ぶ・動かす・足す・引き伸ばす", NamedTextColor.GRAY),
                text("棟を足して引き伸ばせば、土台の形 (L 字・T 字・ずらし など) を目で見ながら作れる", NamedTextColor.GRAY),
                text("クリックで杖を受け取る", NamedTextColor.YELLOW))));
        for (int index = 0; index < modules.size() && index < MODULE_SLOTS.length; index++) {
            HouseModule module = modules.get(index);
            boolean chosen = index == session.module;
            List<Component> lore = new ArrayList<>();
            lore.add(text(modulePlace(module), NamedTextColor.WHITE));
            lore.add(text("幅 " + module.width + " / 奥行き " + module.depth + " / " + module.type.heightLabel() + " " + module.height
                    + (module.type.usesLevel() ? " / " + module.level + " 階から" : ""), NamedTextColor.WHITE));
            lore.add(text(module.on ? "オン" : "オフ (作らない)", module.on ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(text("左クリック: 選ぶ / 右クリック: オン・オフ", NamedTextColor.YELLOW));
            String name = (chosen ? "▶ " : "") + (index + 1) + ". " + module.type.label + (module.on ? "" : " (オフ)");
            inventory.setItem(MODULE_SLOTS[index], glint(item(moduleIcon(module.type), name, chosen ? NamedTextColor.GOLD : module.on ? NamedTextColor.WHITE : NamedTextColor.GRAY, lore), chosen));
        }
        if (modules.size() < TreeSettings.MAX_MODULES) {
            inventory.setItem(MODULE_SLOTS[modules.size()], item(Material.LIME_STAINED_GLASS_PANE, "＋ パーツを足す", NamedTextColor.GREEN, List.of(
                    text("ドーマー窓を正面に1つ足す (種類は下の段で変えられる)", NamedTextColor.GRAY),
                    text("クリックで足す", NamedTextColor.YELLOW))));
        }
        if (modules.isEmpty()) return;
        if (session.module < 0) {
            inventory.setItem(M_TYPE, item(Material.GRAY_DYE, "パーツを選んでいません", NamedTextColor.GRAY, List.of(
                    text("上の列でパーツを左クリック、または杖でプレビューのパーツを指して左クリック", NamedTextColor.GRAY),
                    text("杖で土台を指して左クリックすると、土台を選んだことになる (Shift+左クリックで土台の大きさ)", NamedTextColor.GRAY))));
            return;
        }
        HouseModule module = modules.get(session.module);
        List<Component> typeLore = new ArrayList<>();
        typeLore.add(text(module.type.description(), NamedTextColor.GRAY));
        for (HouseModule.Type type : HouseModule.Type.values()) typeLore.add(text((type == module.type ? "▶ " : "   ") + type.label, type == module.type ? NamedTextColor.GOLD : NamedTextColor.WHITE));
        typeLore.add(text("左クリック: 次の種類 / 右クリック: 前の種類 (大きさはその種類の初期値になる)", NamedTextColor.YELLOW));
        inventory.setItem(M_TYPE, item(moduleIcon(module.type), "種類: " + module.type.label, NamedTextColor.GOLD, typeLore));
        inventory.setItem(M_SIDE, item(Material.COMPASS, "面: " + HouseModule.sideLabel(module.side), NamedTextColor.GREEN, List.of(
                text("どの面に付けるか (右・左は、正面から見て)", NamedTextColor.GRAY),
                text("左クリック: 次の面 / 右クリック: 前の面", NamedTextColor.YELLOW))));
        inventory.setItem(M_ALONG, moduleNumber(Material.LEAD, "横の位置", module.along, "その面のまん中から横へずらす (外からその面を見て右が +)", 1, 5));
        inventory.setItem(M_OFFSET, moduleNumber(Material.PISTON, "壁からの位置", module.offset, "壁から外へずらす (マイナスで家の中へ。塔は初期値で半分めり込む)", 1, 3));
        inventory.setItem(M_WIDTH, moduleNumber(Material.OAK_SLAB, "幅", module.width, switch (module.type) {
            case TOWER -> "直径";
            case STAIR -> "階段の幅 (1〜3)";
            default -> "面に沿った幅";
        }, 1, 4));
        inventory.setItem(M_DEPTH, moduleNumber(Material.OAK_PLANKS, "奥行き", module.depth, switch (module.type) {
            case DORMER -> "屋根の中へ入る長さ (足りなければ屋根に届くまで伸びる)";
            case VOLUME -> "壁の線 (壁からの位置) から外へ出る長さ";
            case TOWER, STAIR -> "(この種類では使わない)";
            default -> "壁から外へ出る長さ";
        }, 1, 4));
        inventory.setItem(M_HEIGHT, moduleNumber(Material.LADDER, module.type.heightLabel(), module.height, "高さの数字の意味は種類で違う", 1, 5));
        if (module.type == HouseModule.Type.VOLUME) {
            // (t195: a volume's floor, by the storey and by the block: its own foundation)
            inventory.setItem(M_LEVEL, item(Material.SCAFFOLDING, "床の高さ: " + module.level + " 階" + (module.raise == 0 ? "" : " " + signed(module.raise) + " ブロック"), NamedTextColor.GREEN, List.of(
                    text("棟の床が、何階の高さから、さらに何ブロック上 (− で下) か", NamedTextColor.GRAY),
                    text("1 階のまま上げ下げすると、棟ごとに高さの違う土台に載る (土台の高さ・テラスは家と同じ設定)", NamedTextColor.GRAY),
                    text("階を上げると、家に載る・宙に張り出す", NamedTextColor.GRAY),
                    text("左クリック +1 / 右クリック -1 ブロック (Shift で 1 階ずつ)", NamedTextColor.YELLOW))));
        } else {
            inventory.setItem(M_LEVEL, moduleNumber(Material.SCAFFOLDING, "階", module.level, switch (module.type) {
                case STAIR -> "上がった先の階 (1 = 2階)";
                default -> module.type.usesLevel() ? "何階から始まるか (0 = 1階)" : "(この種類では使わない)";
            }, 1, 1));
        }
        inventory.setItem(M_ON, item(module.on ? Material.LIME_DYE : Material.GRAY_DYE, module.on ? "オン" : "オフ", module.on ? NamedTextColor.GREEN : NamedTextColor.GRAY, List.of(
                text("オフにすると、リストに残したまま作らない", NamedTextColor.GRAY),
                text("クリックで切り替え", NamedTextColor.YELLOW))));
        inventory.setItem(M_COPY, item(Material.PAPER, "左右反転して複製", NamedTextColor.AQUA, List.of(
                text("同じパーツを、面のまん中をはさんで反対側に足す (まん中にあるときは横に並べる)", NamedTextColor.GRAY),
                text("クリックで複製", NamedTextColor.YELLOW))));
        List<ModuleExtra> extras = ModuleExtra.of(module.type);
        for (int index = 0; index < extras.size(); index++) {
            ModuleExtra extra = extras.get(index);
            inventory.setItem(M_EXTRA[index], item(extra.icon, extra.label + ": " + extra.text(module), NamedTextColor.GREEN, List.of(
                    text(extra.description, NamedTextColor.GRAY),
                    text("左クリック: 次 / 右クリック: 前", NamedTextColor.YELLOW))));
        }
        inventory.setItem(M_DELETE, item(Material.BARRIER, "このパーツを消す", NamedTextColor.RED, List.of(
                text("リストから消す (土台は変わらない)", NamedTextColor.GRAY),
                text("Shift+クリックで消す (誤クリック防止)", NamedTextColor.YELLOW))));
    }

    private static ItemStack moduleNumber(Material icon, String label, int value, String description, int step, int bigStep) {
        return item(icon, label + ": " + value, NamedTextColor.GREEN, List.of(
                text(description, NamedTextColor.GRAY),
                text("左クリック +" + step + " / 右クリック -" + step + " (Shift で " + bigStep + " ずつ)", NamedTextColor.YELLOW)));
    }

    /** A click on the page of the parts of a house; false when it was on none of its buttons. */
    private static boolean moduleClick(TreeSession session, TreeSettings settings, int slot, boolean left, boolean big) {
        List<HouseModule> modules = settings.modules();
        for (int index = 0; index < MODULE_SLOTS.length; index++) {
            if (MODULE_SLOTS[index] != slot) continue;
            if (index < modules.size()) {
                if (left) session.module = index;
                else modules.get(index).on = !modules.get(index).on;
                return true;
            }
            if (index == modules.size() && modules.size() < TreeSettings.MAX_MODULES) {
                modules.add(new HouseModule(HouseModule.Type.DORMER));
                session.module = modules.size() - 1;
                return true;
            }
            return false;
        }
        if (session.module < 0 || session.module >= modules.size()) return false;
        HouseModule module = modules.get(session.module);
        int sign = left ? 1 : -1;
        HouseModule.Type[] types = HouseModule.Type.values();
        List<ModuleExtra> extras = ModuleExtra.of(module.type);
        for (int index = 0; index < extras.size(); index++) {
            if (M_EXTRA[index] != slot) continue;
            extras.get(index).step(module, sign);
            module.limit();
            return true;
        }
        switch (slot) {
            case M_TYPE -> module.setType(types[Math.floorMod(module.type.ordinal() + sign, types.length)]);
            case M_SIDE -> module.side += sign;
            case M_ALONG -> module.along += sign * (big ? 5 : 1);
            case M_OFFSET -> module.offset += sign * (big ? 3 : 1);
            case M_WIDTH -> module.width += sign * (big ? 4 : 1);
            case M_DEPTH -> module.depth += sign * (big ? 4 : 1);
            case M_HEIGHT -> module.height += sign * (big ? 5 : 1);
            case M_LEVEL -> {
                if (module.type == HouseModule.Type.VOLUME && !big) module.raise += sign;
                else module.level += sign;
            }
            case M_ON -> module.on = !module.on;
            case M_COPY -> {
                if (modules.size() >= TreeSettings.MAX_MODULES) return false;
                HouseModule copy = module.copy();
                copy.along = module.along != 0 ? -module.along : module.along + module.width + 1;
                copy.limit();
                modules.add(session.module + 1, copy);
                session.module++;
            }
            case M_DELETE -> {
                if (!big) return false;
                modules.remove(session.module);
                session.module = Math.min(session.module, modules.size() - 1);
            }
            default -> {
                return false;
            }
        }
        module.limit();
        return true;
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
