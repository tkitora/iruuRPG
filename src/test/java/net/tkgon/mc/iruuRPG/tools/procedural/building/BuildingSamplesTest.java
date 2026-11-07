package net.tkgon.mc.iruuRPG.tools.procedural.building;

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
import java.util.List;
import java.util.Map;

/**
 * (t295) Pictures of buildings of the building tool, to look at what the rules make: {@code ./gradlew test --tests '*BuildingSamplesTest'
 * -PbuildingSamples=<folder>} draws, for every pattern (or those in {@code -PbuildingSamplePatterns=0,1,2}), a few seeds as PNGs: the front seen flat (the
 * nearer, the lighter), and the octants of every block from the front right and the front left (so the stairs and slabs show). {@code -PbuildingSampleSet=
 * roofType=1,pitch=2} sets numbers of {@link BuildParams} by name. Without the folder it does nothing.
 */
class BuildingSamplesTest {

    @Test
    void samples() throws Exception {
        String folder = System.getProperty("building.samples", "");
        if (folder.isBlank()) return;
        File out = new File(folder);
        out.mkdirs();
        String only = System.getProperty("building.samples.patterns", "");
        int seeds = Integer.getInteger("building.samples.seeds", 3);
        String set = System.getProperty("building.samples.set", "");
        for (int pattern = 0; pattern <= 6; pattern++) {
            if (!only.isBlank() && !List.of(only.split(",")).contains(Integer.toString(pattern))) continue;
            for (long seed = 1; seed <= seeds; seed++) {
                BuildParams p = new BuildParams();
                p.pattern = pattern;
                for (String pair : set.isBlank() ? new String[0] : set.split(",")) {
                    String[] kv = pair.split("=");
                    java.lang.reflect.Field field = BuildParams.class.getField(kv[0].trim());
                    if (field.getType() == int.class) field.setInt(p, Integer.parseInt(kv[1].trim()));
                    else if (field.getType() == double.class) field.setDouble(p, Double.parseDouble(kv[1].trim()));
                    else if (field.getType() == boolean.class) field.setBoolean(p, Boolean.parseBoolean(kv[1].trim()));
                }
                long started = System.nanoTime();
                BuildingGenerator.Result result = BuildingGenerator.generate(p, seed, Ground.FLAT);
                long millis = (System.nanoTime() - started) / 1_000_000;
                String name = "p" + pattern + "-" + seed;
                String title = Archetypes.name(pattern) + " seed " + seed + "  score " + String.format("%.2f", result.score()) + "  roof " + String.format("%.2f", result.roofShare())
                        + "  " + result.grid().size() + " blocks  " + millis + " ms  tries " + result.tries();
                ImageIO.write(picture(result.grid(), title, result.problems()), "png", new File(out, name + ".png"));
            }
        }
    }

    static BufferedImage picture(SemanticVoxelGrid grid, String title, List<String> problems) {
        BufferedImage right = view(grid, false);
        BufferedImage left = view(grid, true);
        BufferedImage front = front(grid);
        int width = front.getWidth() + right.getWidth() + left.getWidth() + 40;
        int height = Math.max(front.getHeight(), Math.max(right.getHeight(), left.getHeight())) + 60;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xF4F1EA));
        g.fillRect(0, 0, width, height);
        g.drawImage(front, 10, 50, null);
        g.drawImage(right, 20 + front.getWidth(), 50, null);
        g.drawImage(left, 30 + front.getWidth() + right.getWidth(), 50, null);
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        g.drawString(title, 10, 18);
        g.setColor(problems.isEmpty() ? new Color(0x2E7D32) : new Color(0xC62828));
        g.drawString(problems.isEmpty() ? "checks: ok" : "checks: " + String.join(" / ", problems), 10, 38);
        g.dispose();
        return image;
    }

    private static final int F = 8;

    /** The front (+z) seen flat: the nearest octant of every half-block, lighter the nearer. */
    private static BufferedImage front(SemanticVoxelGrid grid) {
        int[] box = grid.bounds();
        int w = (box[3] - box[0] + 1) * 2;
        int h = (box[4] - box[1] + 1) * 2;
        int[][] nearest = new int[w][h];
        Color[][] colors = new Color[w][h];
        for (int[] row : nearest) java.util.Arrays.fill(row, Integer.MIN_VALUE);
        for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
            long key = entry.getKey();
            Cell cell = entry.getValue();
            int mask = mask(cell);
            for (int o = 0; o < 8; o++) {
                if ((mask >> o & 1) == 0) continue;
                int hx = (SemanticVoxelGrid.x(key) - box[0]) * 2 + (o & 1);
                int hy = (SemanticVoxelGrid.y(key) - box[1]) * 2 + (o >> 1 & 1);
                int hz = SemanticVoxelGrid.z(key) * 2 + (o >> 2 & 1);
                if (hz > nearest[hx][hy]) {
                    nearest[hx][hy] = hz;
                    colors[hx][hy] = color(cell);
                }
            }
        }
        int maxZ = box[5] * 2 + 1;
        BufferedImage image = new BufferedImage(w * F / 2, h * F / 2, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(0xE8E4DA));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                if (colors[x][y] == null) continue;
                double shade = Math.max(0.35, 1.0 - (maxZ - nearest[x][y]) * 0.06);
                g.setColor(shade(colors[x][y], shade));
                g.fillRect(x * F / 2, (h - 1 - y) * F / 2, F / 2, F / 2);
            }
        }
        g.dispose();
        return image;
    }

    private static final int A = 7;
    private static final int B = 4;
    private static final int C = 8;

    /** The octants as little cubes, seen from the front right (or the front left, {@code mirror}) and above. */
    private static BufferedImage view(SemanticVoxelGrid grid, boolean mirror) {
        int[] box = grid.bounds();
        List<int[]> octants = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
            long key = entry.getKey();
            int mask = mask(entry.getValue());
            for (int o = 0; o < 8; o++) {
                if ((mask >> o & 1) == 0) continue;
                int x = SemanticVoxelGrid.x(key) * 2 + (o & 1);
                if (mirror) x = -x;
                octants.add(new int[]{x, SemanticVoxelGrid.y(key) * 2 + (o >> 1 & 1), SemanticVoxelGrid.z(key) * 2 + (o >> 2 & 1)});
                colors.add(color(entry.getValue()));
            }
        }
        java.util.Set<Long> filled = new java.util.HashSet<>();
        for (int[] o : octants) filled.add(SemanticVoxelGrid.pack(o[0], o[1], o[2]));
        Integer[] order = new Integer[octants.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> {
            int[] p = octants.get(a);
            int[] q = octants.get(b);
            int s = Integer.compare(p[0] + p[2], q[0] + q[2]);
            return s != 0 ? s : Integer.compare(p[1], q[1]);
        });
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (int[] o : octants) {
            int sx = (o[0] - o[2]) * A;
            int sy = (o[0] + o[2]) * B - o[1] * C;
            minX = Math.min(minX, sx);
            maxX = Math.max(maxX, sx);
            minY = Math.min(minY, sy);
            maxY = Math.max(maxY, sy);
        }
        int pad = 20;
        BufferedImage image = new BufferedImage(Math.max(10, maxX - minX + 2 * pad), Math.max(10, maxY - minY + 2 * pad + C), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0xE8E4DA));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        int ox = pad - minX;
        int oy = pad - minY + C;
        for (int index : order) {
            int[] o = octants.get(index);
            Color color = colors.get(index);
            int sx = (o[0] - o[2]) * A + ox;
            int sy = (o[0] + o[2]) * B - o[1] * C + oy;
            boolean top = !filled.contains(SemanticVoxelGrid.pack(o[0], o[1] + 1, o[2]));
            boolean rightFace = !filled.contains(SemanticVoxelGrid.pack(o[0] + 1, o[1], o[2]));
            boolean frontFace = !filled.contains(SemanticVoxelGrid.pack(o[0], o[1], o[2] + 1));
            // the top: (x, y+1, z) (x+1) (x+1, z+1) (z+1)
            if (top) poly(g, shade(color, 1.0), sx, sy - C, sx + A, sy - C + B, sx, sy - C + 2 * B, sx - A, sy - C + B);
            if (rightFace) poly(g, shade(color, 0.8), sx + A, sy - C + B, sx + A, sy + B, sx, sy + 2 * B, sx, sy - C + 2 * B);
            if (frontFace) poly(g, shade(color, 0.62), sx - A, sy - C + B, sx, sy - C + 2 * B, sx, sy + 2 * B, sx - A, sy + B);
        }
        g.dispose();
        return image;
    }

    private static void poly(Graphics2D g, Color color, int... xy) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < xy.length; i += 2) polygon.addPoint(xy[i], xy[i + 1]);
        g.setColor(color);
        g.fillPolygon(polygon);
        g.setColor(shade(color, 0.85));
        g.drawPolygon(polygon);
    }

    /** The octants drawn for a block: its own, the whole block for a wall, a fence, a trapdoor or a rod (so they show). */
    private static int mask(Cell cell) {
        int mask = cell.octants();
        if (mask == 0) {
            // (a post in the middle: the octants of the lower and upper halves on the near corner only)
            mask = cell.form() == Cell.Form.TRAPDOOR ? 0x0F : 0xFF;
        }
        return mask;
    }

    private static Color shade(Color color, double factor) {
        return new Color(Math.min(255, (int) (color.getRed() * factor)), Math.min(255, (int) (color.getGreen() * factor)), Math.min(255, (int) (color.getBlue() * factor)));
    }

    static Color color(Cell cell) {
        return switch (cell.role()) {
            case PLINTH -> new Color(0x9A9A96);
            case POST -> new Color(0x5A3E26);
            case BEAM -> new Color(0x3E2A18);
            case INFILL -> new Color(0xE9DEC2);
            case WINDOW -> new Color(0x8FC3E0);
            case CORBEL -> new Color(0x7A5534);
            case FLOOR -> new Color(0xA88A5A);
            case ROOF -> new Color(0x4A5470);
            case ROOF_2 -> new Color(0x6A4A70);
            case ROOF_3 -> new Color(0x3A6A70);
            case EAVE -> new Color(0x2E1F12);
            case VERGE -> new Color(0x8A5A2E);
            case RIDGE -> new Color(0xB04030);
            case HIP -> new Color(0x2A2E40);
            case CHIMNEY -> new Color(0xA0503A);
            case RAIL -> new Color(0x6E4A2A);
            case FINIAL -> new Color(0xD4AF37);
        };
    }
}
