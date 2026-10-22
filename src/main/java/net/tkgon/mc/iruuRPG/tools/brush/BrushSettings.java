package net.tkgon.mc.iruuRPG.tools.brush;

import org.bukkit.Material;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Everything one player has set on the brush: the mode and shape, the size, the pattern, the masks and the sun. Kept in memory while the player is online. */
final class BrushSettings {

    /** A block of the pattern and how often it is chosen against the others. */
    static final class Entry {
        Material material;
        int weight;

        Entry(Material material, int weight) {
            this.material = material;
            this.weight = weight;
        }
    }

    BrushMode mode = BrushMode.PAINT;
    BrushShape shape = BrushShape.SPHERE;
    int radius = 6;
    /** Paint: the share of the blocks in the brush that are painted at the centre (0.1 - 1). Terraform: the height in blocks at the centre (1 - 12). */
    double strength = 1.0;
    int height = 2;
    /** 0 = the whole brush is the same, 1 = the effect fades to nothing at the edge. */
    double falloff = 0.3;
    boolean surfaceOnly = true;
    boolean noise;
    double noiseScale = 3.0;
    boolean preview;
    final List<Entry> pattern = new ArrayList<>();

    // masks: each is off, or on (it lets pass what it describes) or on and inverted (it lets pass what it does not describe)
    boolean blockMaskOn;
    boolean blockMaskInvert;
    final Set<Material> blockMask = new LinkedHashSet<>();
    boolean angleMaskOn;
    boolean angleMaskInvert;
    int angleMin;
    int angleMax = 45;
    boolean shadeMaskOn;
    /** false: only the shaded surfaces pass; true (inverted): only the lit ones. */
    boolean shadeMaskInvert;

    // the sun: compass degrees (0 = north, 90 = east) and the height above the horizon
    int sunAzimuth = 135;
    int sunElevation = 45;
    /** How dark a fully shaded surface is made (0.1 - 0.8). */
    double shadeDepth = 0.4;
    /** Whether a block in the way (a wall, a tree) casts a shadow, not only the turn of the surface away from the sun. */
    boolean castShadows = true;

    /** The direction from the surface toward the sun. */
    Vector sunVector() {
        double azimuth = Math.toRadians(sunAzimuth);
        double elevation = Math.toRadians(sunElevation);
        return new Vector(Math.sin(azimuth) * Math.cos(elevation), Math.sin(elevation), -Math.cos(azimuth) * Math.cos(elevation)).normalize();
    }

    Entry find(Material material) {
        for (Entry entry : pattern) if (entry.material == material) return entry;
        return null;
    }

    int totalWeight() {
        int total = 0;
        for (Entry entry : pattern) total += entry.weight;
        return total;
    }

    Set<Material> patternMaterials() {
        Set<Material> set = new LinkedHashSet<>();
        for (Entry entry : pattern) set.add(entry.material);
        return set;
    }
}
