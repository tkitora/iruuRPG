package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.plot.Cell;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Grid;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Kit;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Painter;
import net.tkgon.mc.iruuRPG.tools.procedural.plot.Role;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * (t313, spec 3/6) The parts of the kit in the server's folder {@code plugins/iruuRPG/buildkit/}, one yaml each: the kit's own parts written there once (so they
 * can be changed there, and are not brought back when deleted by hand), the parts registered in the game beside them. Read at the start and by
 * {@code /proceduraltool kit reload}; what is read becomes the kit the buildings are made of. Registering takes the blocks between two corners in the game:
 * a block of a role's block (as the palette has it) is kept as that role (so it takes the palette), any other as it is; its front is the side toward the
 * player. Never throws: what cannot be read is left out with a warning.
 */
final class KitStore {

    private final JavaPlugin plugin;

    KitStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    File folder() {
        File folder = new File(plugin.getDataFolder(), "buildkit");
        if (!folder.exists() && !folder.mkdirs()) plugin.getLogger().warning("Could not create " + folder);
        return folder;
    }

    /** Writes the kit's own parts into the folder (each once) and reads the folder. */
    void start() {
        try {
            install();
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Build kit: parts not written: " + exception);
        }
        reload();
    }

    /** Each part of the kit's own not written before (the names written are kept in {@code buildkit/.installed}). */
    private void install() {
        File marker = new File(folder(), ".installed");
        Set<String> done = new HashSet<>();
        try {
            if (marker.isFile()) done.addAll(java.nio.file.Files.readAllLines(marker.toPath()));
        } catch (IOException exception) {
            plugin.getLogger().warning("Build kit: " + exception);
            return;
        }
        int added = 0;
        for (Kit.Part part : Kit.builtin().all()) {
            if (done.contains(part.id)) continue;
            File file = new File(folder(), part.id + ".yml");
            if (!file.exists() && write(part, file)) added++;
            done.add(part.id);
        }
        try {
            java.nio.file.Files.write(marker.toPath(), new ArrayList<>(done));
        } catch (IOException exception) {
            plugin.getLogger().warning("Build kit: " + exception);
        }
        if (added > 0) plugin.getLogger().info("Build kit: " + added + " parts written to buildkit/");
    }

    /** Reads the folder into the kit (the kit's own parts as a start, the folder's over them); how many parts were read. Never throws. */
    int reload() {
        Kit kit = Kit.builtin();
        int read = 0;
        try {
            File[] files = folder().listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null) {
                java.util.Arrays.sort(files);
                for (File file : files) {
                    try {
                        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                        Map<String, Object> map = new LinkedHashMap<>(yaml.getValues(false));
                        Kit.Part part = Kit.Part.fromMap(map);
                        if (part == null) continue;
                        part.builtin = Kit.builtin().get(part.id) != null;
                        kit.add(part);
                        read++;
                    } catch (RuntimeException exception) {
                        plugin.getLogger().warning("Build kit: " + file.getName() + " not read: " + exception);
                    }
                }
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Build kit: folder not read: " + exception);
        }
        PlotShape.useKit(kit);
        return read;
    }

    private boolean write(Kit.Part part, File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of("建物 (区画) の部品キットの部品 (iruuRPG の /proceduraltool kit で編集できる)。"));
        for (Map.Entry<String, Object> entry : part.toMap().entrySet()) yaml.set(entry.getKey(), entry.getValue());
        try {
            yaml.save(file);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Build kit: " + part.id + " not written: " + exception);
            return false;
        }
    }

    /** Writes a part into the folder and reads the kit again; false when it cannot be written. */
    boolean save(Kit.Part part) {
        boolean done = write(part, new File(folder(), part.id + ".yml"));
        reload();
        return done;
    }

    /** Moves a part's file into {@code buildkit/trash/} (with the time in its name) and reads the kit again; false when there is none. */
    boolean delete(String id) {
        File file = new File(folder(), id + ".yml");
        if (!file.isFile()) return false;
        File trash = new File(folder(), "trash");
        if (!trash.isDirectory() && !trash.mkdirs()) return false;
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        boolean moved = file.renameTo(new File(trash, id + "-" + stamp + ".yml"));
        reload();
        return moved;
    }

    // ---- registering in the game ----------------------------------------------------------------------------------------------------

    /** The way a player faces, flat (0 north, 1 east, 2 south, 3 west). */
    static int facing(Player player) {
        double yaw = Math.floorMod((long) Math.round(player.getLocation().getYaw()), 360);
        return (int) Math.floorMod(Math.round(yaw / 90.0) + 2, 4);
    }

    /**
     * A part from the blocks between the two corners: its front toward the player (u to their right, v up, d into it from the front layer). A block of a role's
     * block is kept as that role, with its shape and its states turned into the part's frame; any other as it is, turned the same way.
     */
    Kit.Part capture(Player player, TreeSession session, ProceduralModule tool, String id, Kit.Category category, List<String> tags) {
        World world = session.kitWorld;
        int[] a = session.kitFrom;
        int[] b = session.kitTo;
        int x0 = Math.min(a[0], b[0]);
        int y0 = Math.min(a[1], b[1]);
        int z0 = Math.min(a[2], b[2]);
        int x1 = Math.max(a[0], b[0]);
        int y1 = Math.max(a[1], b[1]);
        int z1 = Math.max(a[2], b[2]);
        // (the part's front faces the player: its "out" is the way from the part to them, the opposite of the way they face)
        int out = (facing(player) + 2) % 4;
        Frame frame = new Frame(out, x0, y0, z0, x1, z1);
        Map<Material, Role> roles = new HashMap<>();
        for (TreePart part : ProcKind.PLOT.parts()) roles.putIfAbsent(tool.material(part, session.settings), role(part));
        Kit.Part part = new Kit.Part();
        part.id = id;
        part.category = category;
        part.kind = Kit.Kind.TEMPLATE;
        for (String tag : tags) part.tags.add(tag.toLowerCase(Locale.ROOT));
        part.sizeU = frame.sizeU();
        part.sizeV = y1 - y0 + 1;
        part.sizeD = frame.sizeD();
        part.minW = part.maxW = part.sizeU;
        part.minH = part.maxH = part.sizeV;
        int turns = Math.floorMod(2 - out, 4);
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType().isAir()) continue;
                    int[] local = frame.local(x, y, z);
                    BlockData data = block.getBlockData().clone();
                    data.rotate(rotation(turns));
                    Role role = roles.get(block.getType());
                    Kit.Block entry = role == null ? new Kit.Block(null, null, null, data.getAsString()) : new Kit.Block(role, form(data), props(data), null);
                    part.blocks.put(Kit.Part.key(local[0], local[1], local[2]), entry);
                }
            }
        }
        // (where it was taken, so anchors can be pointed at afterwards)
        part.params.put("origin", List.of(x0, y0, z0, x1, z1, out));
        part.params.put("world", world.getName());
        return part;
    }

    /** The place of a world block in a captured part's frame ({u, v, d}), or null when the part has no origin. */
    static int[] local(Kit.Part part, int x, int y, int z) {
        if (!(part.params.get("origin") instanceof List<?> origin) || origin.size() < 6) return null;
        int[] o = new int[6];
        for (int i = 0; i < 6; i++) o[i] = ((Number) origin.get(i)).intValue();
        return new Frame(o[5], o[0], o[1], o[2], o[3], o[4]).local(x, y, z);
    }

    /** The frame of a box whose front faces {@code out}: u to the right of one looking at it, v up, d in from the front. */
    private record Frame(int out, int x0, int y0, int z0, int x1, int z1) {
        int sizeU() {
            return out % 2 == 0 ? x1 - x0 + 1 : z1 - z0 + 1;
        }

        int sizeD() {
            return out % 2 == 0 ? z1 - z0 + 1 : x1 - x0 + 1;
        }

        int[] local(int x, int y, int z) {
            return switch (out) {
                // front south: u east (+x), d north (-z) from the south face
                case 2 -> new int[]{x - x0, y - y0, z1 - z};
                case 0 -> new int[]{x1 - x, y - y0, z - z0};
                case 1 -> new int[]{z1 - z, y - y0, x1 - x};
                default -> new int[]{z - z0, y - y0, x - x0};
            };
        }
    }

    private static org.bukkit.block.structure.StructureRotation rotation(int turns) {
        return switch (Math.floorMod(turns, 4)) {
            case 1 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90;
            case 2 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180;
            case 3 -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90;
            default -> org.bukkit.block.structure.StructureRotation.NONE;
        };
    }

    static Role role(TreePart part) {
        return Role.valueOf(part.name().substring("PLOT_".length()));
    }

    /** The shape a block is in, as the kit keeps it. */
    private static Cell.Form form(BlockData data) {
        if (data instanceof org.bukkit.block.data.type.Stairs) return Cell.Form.STAIRS;
        if (data instanceof org.bukkit.block.data.type.Slab) return Cell.Form.SLAB;
        if (data instanceof org.bukkit.block.data.type.Wall) return Cell.Form.WALL;
        if (data instanceof org.bukkit.block.data.type.Fence) return Cell.Form.FENCE;
        if (data instanceof org.bukkit.block.data.type.TrapDoor) return Cell.Form.TRAPDOOR;
        if (data instanceof org.bukkit.block.data.type.Door) return Cell.Form.DOOR;
        if (data instanceof org.bukkit.block.data.Orientable) return Cell.Form.LOG;
        return Cell.Form.FULL;
    }

    /** The states of a block the kit keeps for its shape (facing, half, type, axis, open). */
    private static String props(BlockData data) {
        String state = data.getAsString();
        int open = state.indexOf('[');
        if (open < 0) return "";
        List<String> keep = new ArrayList<>();
        for (String pair : state.substring(open + 1, state.length() - 1).split(",")) {
            String key = pair.substring(0, Math.max(0, pair.indexOf('=')));
            if (List.of("facing", "half", "type", "axis", "open").contains(key)) keep.add(pair);
        }
        return String.join(",", keep);
    }

    // ---- showing a part ------------------------------------------------------------------------------------------------------------

    /**
     * Shows a part to the player for {@code seconds} (to them only: nothing in the world changes) on the block in sight, its front toward them, {@code w} wide and
     * {@code h} high (a wall panel or a 9-slice drawn to that size).
     */
    void preview(Player player, ProceduralModule tool, Kit.Part part, int w, int h, Location at, int seconds) {
        int out = (facing(player) + 2) % 4;
        Grid grid = new Grid();
        Painter.Canvas canvas = new Painter.Canvas() {
            @Override
            public void put(int u, int v, int d, Cell cell) {
                grid.put(u, v, d, cell);
            }

            @Override
            public void literal(int u, int v, int d, String state) {
                grid.literal(u, v, d, state);
            }

            @Override
            public void clear(int u, int v, int d) {
                grid.clear(u, v, d);
            }

            @Override
            public int out() {
                return 2;
            }

            @Override
            public int right() {
                return 1;
            }
        };
        if (part.category == Kit.Category.GABLE) Painter.gable(part, canvas, w, u -> Math.max(0, Math.min(u, w - 1 - u)) + 1, Role.INFILL);
        else Painter.panel(part, canvas, w, h, Role.INFILL, 0, false);
        grid.finish();
        // (the part as the tool's blocks, turned to face the player)
        PlotShape.Blocks blocks = new PlotShape.Blocks();
        int turns = Math.floorMod(out - 2, 4);
        Map<TreePart, BlockData> looks = new HashMap<>();
        for (TreePart tp : ProcKind.PLOT.parts()) looks.put(tp, tool.blockData(tp, tool.session(player).settings));
        Map<org.bukkit.Location, BlockData> changes = new LinkedHashMap<>();
        for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
            long key = entry.getKey();
            int[] turned = turn(Grid.x(key) - w / 2, Grid.z(key), turns);
            long world = TreeVoxelizer.pack(turned[0], Grid.y(key), turned[1]);
            blocks.put(world, PlotShape.part(entry.getValue().role()));
            blocks.cells.put(world, entry.getValue().turned(turns));
            String state = grid.literalAt(key);
            if (state != null) blocks.literals.put(world, state);
        }
        blocks.turns = turns;
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            long key = entry.getKey();
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            BlockData data = blocks.data(looks.get(entry.getValue()), entry.getValue(), x, y, z);
            if (data != null) changes.put(at.clone().add(x, y + 1, z), data);
        }
        for (Map.Entry<org.bukkit.Location, BlockData> entry : changes.entrySet()) player.sendBlockChange(entry.getKey(), entry.getValue());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            for (org.bukkit.Location location : changes.keySet()) player.sendBlockChange(location, location.getBlock().getBlockData());
        }, Math.max(20L, seconds * 20L));
    }

    /** {@code (x, z)} turned {@code turns} quarters clockwise (south to west). */
    private static int[] turn(int x, int z, int turns) {
        int tx = x;
        int tz = z;
        for (int t = 0; t < Math.floorMod(turns, 4); t++) {
            int next = -tz;
            tz = tx;
            tx = next;
        }
        return new int[]{tx, tz};
    }
}
