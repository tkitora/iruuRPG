package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A query of the environment (like Unreal's EQS) that chooses where a mob goes. A generator makes candidate places (a ring or a grid round the mob, its target or its home),
 * tests give each a score or throw it out (distance, line of sight, whether the mob can walk there, how crowded it is, noise), and the query takes the best place (or one of
 * the best few at random). The candidates stand on the ground (a place with no ground is dropped). Run from {@code move-to} or the {@code run-eqs} service.
 */
final class EqsQuery {

    record Test(String type, String to, String prefer, double value, double weight, double min, double max, boolean filter, boolean visible) {
    }

    final String name;
    private final String generator;
    private final String around;
    private final double radius;
    private final int points;
    private final int rings;
    private final double spacing;
    private final List<Test> tests;
    private final boolean randomTop;
    private final int topN;

    EqsQuery(String name, String generator, String around, double radius, int points, int rings, double spacing, List<Test> tests, boolean randomTop, int topN) {
        this.name = name;
        this.generator = generator;
        this.around = around;
        this.radius = radius;
        this.points = Math.max(4, Math.min(48, points));
        this.rings = Math.max(1, Math.min(4, rings));
        this.spacing = Math.max(1.0, spacing);
        this.tests = tests;
        this.randomTop = randomTop;
        this.topN = Math.max(1, topN);
    }

    /** The place chosen, or null when nothing fits. */
    Location run(Ctx c) {
        Mob mob = c.host.mob();
        Location origin = place(c, around);
        if (origin == null || origin.getWorld() == null) return null;
        List<Location> candidates = new ArrayList<>();
        switch (generator) {
            case "grid" -> {
                for (double x = -radius; x <= radius; x += spacing) {
                    for (double z = -radius; z <= radius; z += spacing) {
                        if (x * x + z * z > radius * radius) continue;
                        candidates.add(origin.clone().add(x, 0.0, z));
                    }
                }
            }
            case "self" -> candidates.add(origin.clone());
            default -> {
                double turn = c.random.nextDouble() * Math.PI * 2.0;
                for (int ring = 1; ring <= rings; ring++) {
                    double r = radius * ring / rings;
                    for (int index = 0; index < points; index++) {
                        double angle = turn + Math.PI * 2.0 * index / points;
                        candidates.add(origin.clone().add(Math.cos(angle) * r, 0.0, Math.sin(angle) * r));
                    }
                }
            }
        }
        // stand them on the ground
        List<Location> standing = new ArrayList<>();
        for (Location candidate : candidates) {
            Location ground = ground(candidate);
            if (ground != null) standing.add(ground);
        }
        int count = standing.size();
        if (count == 0) return null;
        double[] score = new double[count];
        boolean[] kept = new boolean[count];
        java.util.Arrays.fill(kept, true);
        boolean pathable = false;
        Player target = c.bb.target;
        for (Test test : tests) {
            switch (test.type()) {
                case "distance" -> {
                    Location ref = place(c, test.to());
                    if (ref == null) continue;
                    double[] raw = new double[count];
                    double low = Double.MAX_VALUE;
                    double high = -Double.MAX_VALUE;
                    for (int index = 0; index < count; index++) {
                        raw[index] = standing.get(index).distance(ref);
                        low = Math.min(low, raw[index]);
                        high = Math.max(high, raw[index]);
                        if (test.filter() && (raw[index] < test.min() || raw[index] > test.max())) kept[index] = false;
                    }
                    double span = Math.max(1.0E-6, high - low);
                    double deviation = 0.0;
                    if (test.prefer().equals("value")) for (double v : raw) deviation = Math.max(deviation, Math.abs(v - test.value()));
                    for (int index = 0; index < count; index++) {
                        double s = switch (test.prefer()) {
                            case "far" -> (raw[index] - low) / span;
                            case "value" -> 1.0 - Math.abs(raw[index] - test.value()) / Math.max(1.0E-6, deviation);
                            default -> 1.0 - (raw[index] - low) / span;
                        };
                        score[index] += s * test.weight();
                    }
                }
                case "line-of-sight" -> {
                    if (target == null) continue;
                    Location eye = target.getEyeLocation();
                    World world = eye.getWorld();
                    for (int index = 0; index < count; index++) {
                        Location from = standing.get(index).clone().add(0.0, 1.5, 0.0);
                        Vector way = eye.toVector().subtract(from.toVector());
                        double length = way.length();
                        boolean clear = length < 1.0E-6 || world.rayTraceBlocks(from, way.normalize(), length, org.bukkit.FluidCollisionMode.NEVER, true) == null;
                        if (clear == test.visible()) score[index] += test.weight();
                        else if (test.filter()) kept[index] = false;
                    }
                }
                case "crowd" -> {
                    double r = test.value() > 0 ? test.value() : 3.0;
                    for (int index = 0; index < count; index++) {
                        int crowd = c.host.crowdNear(standing.get(index), r);
                        if (test.filter() && crowd > test.max()) kept[index] = false;
                        score[index] += (1.0 - Math.min(1.0, crowd / 3.0)) * test.weight();
                    }
                }
                case "random" -> {
                    for (int index = 0; index < count; index++) score[index] += c.random.nextDouble() * test.weight();
                }
                case "spread" -> {
                    // the angle round the target: the farther from the allies' angles, the better (value = the difference that counts as far enough, degrees)
                    Location ref = place(c, test.to().equals("self") ? "target" : test.to());
                    if (ref == null) continue;
                    List<Location> allies = c.host.allyLocations(24.0, true);
                    double ideal = test.value() > 0 ? test.value() : 60.0;
                    for (int index = 0; index < count; index++) {
                        double angle = Math.toDegrees(Math.atan2(standing.get(index).getZ() - ref.getZ(), standing.get(index).getX() - ref.getX()));
                        double nearest = 180.0;
                        for (Location ally : allies) {
                            double difference = Math.abs(((Math.toDegrees(Math.atan2(ally.getZ() - ref.getZ(), ally.getX() - ref.getX())) - angle) % 360.0 + 540.0) % 360.0 - 180.0);
                            nearest = Math.min(nearest, difference);
                        }
                        if (test.filter() && nearest < test.min()) kept[index] = false;
                        score[index] += Math.min(1.0, nearest / ideal) * test.weight();
                    }
                }
                case "ally-distance" -> {
                    List<Location> allies = c.host.allyLocations(24.0, true);
                    double[] raw = new double[count];
                    double low = Double.MAX_VALUE;
                    double high = -Double.MAX_VALUE;
                    for (int index = 0; index < count; index++) {
                        double nearest = 24.0;
                        for (Location ally : allies) nearest = Math.min(nearest, standing.get(index).distance(ally));
                        raw[index] = nearest;
                        low = Math.min(low, nearest);
                        high = Math.max(high, nearest);
                        if (test.filter() && (nearest < test.min() || nearest > test.max())) kept[index] = false;
                    }
                    double span = Math.max(1.0E-6, high - low);
                    for (int index = 0; index < count; index++) {
                        double s = test.prefer().equals("near") ? 1.0 - (raw[index] - low) / span : (raw[index] - low) / span;
                        score[index] += s * test.weight();
                    }
                }
                case "pathable" -> pathable = true;
                default -> {
                }
            }
        }
        List<Integer> order = new ArrayList<>();
        for (int index = 0; index < count; index++) if (kept[index]) order.add(index);
        order.sort(Comparator.comparingDouble((Integer index) -> score[index]).reversed());
        if (order.isEmpty()) {
            record(c, standing, score, kept, null);
            return null;
        }
        // the places the mob can walk to: only the best few are asked of the pathfinder
        List<Location> best = new ArrayList<>();
        int asked = 0;
        for (int index : order) {
            Location place = standing.get(index);
            if (pathable) {
                if (asked++ >= 8) break;
                if (mob.getPathfinder().findPath(place) == null) continue;
            }
            best.add(place);
            if (best.size() >= (randomTop ? topN : 1)) break;
        }
        if (best.isEmpty()) {
            record(c, standing, score, kept, null);
            return null;
        }
        Location chosen = best.get(randomTop ? c.random.nextInt(best.size()) : 0);
        record(c, standing, score, kept, chosen);
        return chosen;
    }

    /** Hands the candidates, scaled to 0 - 1 among the kept ones, to the debug view. */
    private void record(Ctx c, List<Location> standing, double[] score, boolean[] kept, Location chosen) {
        if (c.eqsSink == null) return;
        double low = Double.MAX_VALUE;
        double high = -Double.MAX_VALUE;
        for (int index = 0; index < score.length; index++) {
            if (!kept[index]) continue;
            low = Math.min(low, score[index]);
            high = Math.max(high, score[index]);
        }
        double span = Math.max(1.0E-6, high - low);
        List<EqsResult.Candidate> list = new ArrayList<>();
        for (int index = 0; index < score.length; index++) {
            list.add(new EqsResult.Candidate(standing.get(index), kept[index] ? (score[index] - low) / span : 0.0, kept[index]));
        }
        c.eqsSink.accept(new EqsResult(name, c.now, list, chosen));
    }

    private static Location place(Ctx c, String which) {
        return switch (which) {
            case "target" -> c.bb.target == null ? null : c.bb.target.getLocation();
            case "home" -> c.host.home();
            default -> c.host.mob().getLocation();
        };
    }

    /** The place on the ground at the column of {@code at}: a solid block with two blocks of air above it, within 3 above to 5 below. Null when none. */
    private static Location ground(Location at) {
        World world = at.getWorld();
        if (world == null) return null;
        int x = at.getBlockX();
        int z = at.getBlockZ();
        if (!world.isChunkLoaded(x >> 4, z >> 4)) return null;
        for (int dy = 3; dy >= -5; dy--) {
            int y = at.getBlockY() + dy;
            if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) continue;
            Block floor = world.getBlockAt(x, y - 1, z);
            if (!floor.getType().isSolid()) continue;
            if (!world.getBlockAt(x, y, z).isPassable() || !world.getBlockAt(x, y + 1, z).isPassable()) continue;
            return new Location(world, x + 0.5, y, z + 0.5);
        }
        return null;
    }

    /** Reads a query from its yaml map; null (with a message in {@code errors}) when it is not usable. */
    static EqsQuery parse(String name, Map<?, ?> map, List<String> errors) {
        Map<?, ?> generatorMap = map.get("generator") instanceof Map<?, ?> m ? m : Map.of();
        String generator = Cfg.text(generatorMap, "type", "ring");
        if (!List.of("ring", "grid", "self").contains(generator)) {
            errors.add("eqs " + name + ": generator type must be ring / grid / self (" + generator + ")");
            return null;
        }
        List<Test> tests = new ArrayList<>();
        if (map.get("tests") instanceof List<?> list) {
            for (Object entry : list) {
                if (!(entry instanceof Map<?, ?> t)) continue;
                String type = Cfg.text(t, "type", "");
                if (!List.of("distance", "line-of-sight", "pathable", "crowd", "random", "spread", "ally-distance").contains(type)) {
                    errors.add("eqs " + name + ": unknown test '" + type + "'");
                    continue;
                }
                String preferText = Cfg.text(t, "prefer", "near");
                boolean hasValue = t.get("value") instanceof Number;
                tests.add(new Test(type, Cfg.text(t, "to", Cfg.text(t, "around", "self")), hasValue ? "value" : preferText, Cfg.number(t, "value", 0.0), Cfg.number(t, "weight", 1.0),
                        Cfg.number(t, "min", 0.0), Cfg.number(t, "max", Double.MAX_VALUE), Cfg.flag(t, "filter", type.equals("pathable")), Cfg.flag(t, "visible", true)));
            }
        }
        String pick = Cfg.text(map, "pick", "best");
        return new EqsQuery(name, generator, Cfg.text(generatorMap, "around", "self"), Cfg.number(generatorMap, "radius", 6.0), (int) Cfg.number(generatorMap, "points", 12.0),
                (int) Cfg.number(generatorMap, "rings", 1.0), Cfg.number(generatorMap, "spacing", 2.0), tests, pick.equals("random-top"), (int) Cfg.number(map, "top", 3.0));
    }
}
