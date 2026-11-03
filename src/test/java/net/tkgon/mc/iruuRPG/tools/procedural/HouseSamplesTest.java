package net.tkgon.mc.iruuRPG.tools.procedural;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * (t239) Pictures of random houses, to look at what the rules make: {@code ./gradlew test --tests '*HouseSamplesTest' -PhouseSamples=<folder>} draws, for
 * every pattern (or those in {@code -PhouseSamplePatterns=storybook,tower}) and size, a few seeds as PNGs (the front seen flat, and from the front right
 * and the front left) with their numbers beside them. {@code -PhouseSampleShape=21,13,3,5,6} keeps that shape (width, depth, storeys, storey height,
 * pillar spacing; "形もおまかせ" off) instead of the sizes; {@code -PhouseSampleSet=house-gen-plan=2,house-gen-symmetry=1} sets any numbers of the settings (t246).
 * Without the folder it does nothing.
 */
class HouseSamplesTest {

    @Test
    void samples() throws IOException {
        String folder = System.getProperty("house.samples", "");
        if (folder.isBlank()) return;
        File out = new File(folder);
        out.mkdirs();
        String only = System.getProperty("house.samples.patterns", "");
        int seeds = Integer.getInteger("house.samples.seeds", 4);
        String shape = System.getProperty("house.samples.shape", "");
        String set = System.getProperty("house.samples.set", "");
        for (HouseDesigner.Pattern pattern : HouseDesigner.Pattern.values()) {
            if (pattern == HouseDesigner.Pattern.ANY || !only.isBlank() && !List.of(only.split(",")).contains(pattern.id)) continue;
            for (int size = shape.isBlank() ? 0 : 1; size <= (shape.isBlank() ? 2 : 1); size++) {
                for (long seed = 1; seed <= seeds; seed++) {
                    TreeSettings rules = new TreeSettings();
                    rules.setKind(ProcKind.HOUSE);
                    rules.set(TreeSettings.Key.HOUSE_GEN_PATTERN, pattern.ordinal());
                    rules.set(TreeSettings.Key.HOUSE_GEN_SIZE, size);
                    rules.set(TreeSettings.Key.HOUSE_GEN_DETAIL, 2);
                    for (String pair : set.isBlank() ? new String[0] : set.split(",")) {
                        String[] kv = pair.split("=");
                        for (TreeSettings.Key key : TreeSettings.Key.values()) if (key.id().equals(kv[0].trim())) rules.set(key, Double.parseDouble(kv[1].trim()));
                    }
                    if (!shape.isBlank()) {
                        String[] n = shape.split(",");
                        rules.set(TreeSettings.Key.HOUSE_GEN_SHAPE, 0);
                        TreeSettings.Key[] keys = {TreeSettings.Key.HOUSE_WIDTH, TreeSettings.Key.HOUSE_DEPTH, TreeSettings.Key.HOUSE_FLOORS, TreeSettings.Key.HOUSE_STOREY,
                                TreeSettings.Key.HOUSE_PILLARS};
                        for (int k = 0; k < Math.min(n.length, keys.length); k++) rules.set(keys[k], Double.parseDouble(n[k].trim()));
                    }
                    HouseDesigner.Result result = HouseDesigner.design(rules, seed, false);
                    Map<Long, TreePart> blocks = ProceduralShapes.build(ProcKind.HOUSE, result.settings(), 1L, new Random(1), null, null);
                    String name = pattern.id + "-" + (shape.isBlank() ? HouseDesigner.sizeName(size) : "shape") + "-" + seed;
                    ImageIO.write(picture(blocks, name + " (" + result.settings().modules().size() + " parts)"), "png", new File(out, name + ".png"));
                    StringBuilder text = new StringBuilder();
                    for (TreeSettings.Key key : TreeSettings.Key.values()) if (key.id().startsWith("house-") && !key.id().startsWith("house-gen")) text.append(key.id()).append(": ").append(result.settings().get(key)).append('\n');
                    text.append("steps: ").append(result.settings().roofSteps()).append('\n');
                    for (HouseModule module : result.settings().modules()) text.append(module.toMap()).append('\n');
                    java.nio.file.Files.writeString(new File(out, name + ".txt").toPath(), text);
                }
            }
        }
    }

    /** The blocks seen from the front (flat, nearer darker-edged), and drawn as cubes from above at the front right and the front left (the front is +z). */
    static BufferedImage picture(Map<Long, TreePart> blocks, String title) {
        Map<Long, TreePart> left = new HashMap<>();
        for (Map.Entry<Long, TreePart> entry : blocks.entrySet()) {
            long key = entry.getKey();
            left.put(TreeVoxelizer.pack(-TreeVoxelizer.unpackX(key), TreeVoxelizer.unpackY(key), TreeVoxelizer.unpackZ(key)), entry.getValue());
        }
        BufferedImage f = front(blocks);
        BufferedImage a = view(blocks);
        BufferedImage b = view(left);
        BufferedImage t = top(blocks);
        BufferedImage both = new BufferedImage(f.getWidth() + a.getWidth() + b.getWidth() + t.getWidth(),
                Math.max(Math.max(f.getHeight(), t.getHeight()), Math.max(a.getHeight(), b.getHeight())) + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = both.createGraphics();
        g.setColor(new Color(0xd8e4ee));
        g.fillRect(0, 0, both.getWidth(), both.getHeight());
        g.drawImage(f, 0, both.getHeight() - f.getHeight(), null);
        g.drawImage(a, f.getWidth(), 20, null);
        g.drawImage(b, f.getWidth() + a.getWidth(), 20, null);
        g.drawImage(t, f.getWidth() + a.getWidth() + b.getWidth(), 20, null);
        g.setColor(Color.DARK_GRAY);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        g.drawString(title, 6, 15);
        g.dispose();
        return both;
    }

    private static final int C = 10;
    private static final int F = 10;

    /** (t250) Seen from above, the front down: the highest block of each column, darker the lower it is (a hole in a roof shows as what is under it). */
    private static BufferedImage top(Map<Long, TreePart> blocks) {
        Map<Long, long[]> highest = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (long key : blocks.keySet()) {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            long column = ((long) x << 32) | (z & 0xffffffffL);
            long[] seen = highest.get(column);
            if (seen == null || y > seen[0]) highest.put(column, new long[]{y, key});
        }
        if (highest.isEmpty()) return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        BufferedImage image = new BufferedImage((maxX - minX + 1) * F + 20, (maxZ - minZ + 1) * F + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xd8e4ee));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int span = Math.max(1, maxY - minY);
        for (Map.Entry<Long, long[]> entry : highest.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int z = (int) (long) entry.getKey();
            double shade = 0.45 + 0.55 * (entry.getValue()[0] - minY) / span;
            Color color = color(blocks.get(entry.getValue()[1]));
            g.setColor(new Color((int) (color.getRed() * shade), (int) (color.getGreen() * shade), (int) (color.getBlue() * shade)));
            g.fillRect(10 + (x - minX) * F, 10 + (z - minZ) * F, F, F);
        }
        g.dispose();
        return image;
    }

    /** The front (+z) seen flat: each block the nearest of its row, darker the farther back it is. */
    private static BufferedImage front(Map<Long, TreePart> blocks) {
        Map<Long, long[]> nearest = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : blocks.keySet()) {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
            long column = ((long) x << 32) | (y & 0xffffffffL);
            long[] seen = nearest.get(column);
            if (seen == null || z > seen[0]) nearest.put(column, new long[]{z, key});
        }
        if (nearest.isEmpty()) return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        int minY = 0;
        for (long key : blocks.keySet()) minY = Math.min(minY, TreeVoxelizer.unpackY(key));
        BufferedImage image = new BufferedImage((maxX - minX + 1) * F + 20, (maxY - minY + 1) * F + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xd8e4ee));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int span = Math.max(1, maxZ - minZ);
        for (Map.Entry<Long, long[]> entry : nearest.entrySet()) {
            int x = (int) (entry.getKey() >> 32);
            int y = (int) (long) entry.getKey();
            double shade = 1.0 - 0.55 * (maxZ - entry.getValue()[0]) / span;
            Color color = color(blocks.get(entry.getValue()[1]));
            g.setColor(new Color((int) (color.getRed() * shade), (int) (color.getGreen() * shade), (int) (color.getBlue() * shade)));
            g.fillRect(10 + (x - minX) * F, 10 + (maxY - y) * F, F, F);
        }
        g.dispose();
        return image;
    }

    private static BufferedImage view(Map<Long, TreePart> blocks) {
        List<long[]> cubes = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (long key : blocks.keySet()) {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            cubes.add(new long[]{x, y, z, key});
            int sx = (x - z) * C;
            int sy = (x + z) * C / 2 - y * C;
            minX = Math.min(minX, sx - C * 2);
            maxX = Math.max(maxX, sx + C * 2);
            minY = Math.min(minY, sy - C * 2);
            maxY = Math.max(maxY, sy + C * 2);
        }
        if (cubes.isEmpty()) return new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        cubes.sort((p, q) -> Long.compare(p[0] + p[1] + p[2], q[0] + q[1] + q[2]));
        BufferedImage image = new BufferedImage(maxX - minX + 20, maxY - minY + 20, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0xd8e4ee));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int ox = -minX + 10;
        int oy = -minY + 10;
        for (long[] cube : cubes) {
            int x = (int) cube[0];
            int y = (int) cube[1];
            int z = (int) cube[2];
            Color color = color(blocks.get(cube[3]));
            if (!blocks.containsKey(TreeVoxelizer.pack(x, y + 1, z))) {
                face(g, ox, oy, color, 1.0, new int[][]{{x, y + 1, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}});
            }
            if (!blocks.containsKey(TreeVoxelizer.pack(x + 1, y, z))) {
                face(g, ox, oy, color, 0.78, new int[][]{{x + 1, y, z}, {x + 1, y + 1, z}, {x + 1, y + 1, z + 1}, {x + 1, y, z + 1}});
            }
            if (!blocks.containsKey(TreeVoxelizer.pack(x, y, z + 1))) {
                face(g, ox, oy, color, 0.6, new int[][]{{x, y, z + 1}, {x + 1, y, z + 1}, {x + 1, y + 1, z + 1}, {x, y + 1, z + 1}});
            }
        }
        g.dispose();
        return image;
    }

    private static void face(Graphics2D g, int ox, int oy, Color color, double shade, int[][] corners) {
        Polygon polygon = new Polygon();
        for (int[] p : corners) polygon.addPoint(ox + (p[0] - p[2]) * C, oy + (p[0] + p[2]) * C / 2 - p[1] * C);
        g.setColor(new Color((int) (color.getRed() * shade), (int) (color.getGreen() * shade), (int) (color.getBlue() * shade)));
        g.fillPolygon(polygon);
    }

    private static Color color(TreePart part) {
        if (part == null) return Color.MAGENTA;
        return switch (part) {
            case HOUSE_PILLAR -> new Color(0x6b4a2b);
            case HOUSE_BEAM -> new Color(0x8a5a32);
            case HOUSE_ROOF -> new Color(0x5a5f6e);
            case HOUSE_WALL -> new Color(0xeee6d4);
            case HOUSE_STONE -> new Color(0x9a9a96);
            case HOUSE_FLOOR -> new Color(0xc9b48c);
            case HOUSE_WINDOW -> new Color(0x8fc6e8);
            // (t252: the parts of a tree, for the thin trees)
            case TRUNK, BRANCH, ROOT -> new Color(0x6e5034);
            case BRANCH_WALL -> new Color(0x4a4a52);
            case BRANCH_FENCE -> new Color(0xc49a5c);
            case BRANCH_SLAB -> new Color(0xe8d070);
            case BRANCH_SLAB_TOP -> new Color(0xe89a40);
            case BRANCH_STAIR, BRANCH_STAIR_EAST, BRANCH_STAIR_SOUTH, BRANCH_STAIR_WEST, BRANCH_STAIR_TOP, BRANCH_STAIR_TOP_EAST, BRANCH_STAIR_TOP_SOUTH,
                 BRANCH_STAIR_TOP_WEST -> new Color(0xa0522d);
            case TRUNK_X, TRUNK_Z -> new Color(0x6e5034);
            case SNOW -> new Color(0xf4f4f4);
            default -> new Color(0x7fa060);
        };
    }
}
