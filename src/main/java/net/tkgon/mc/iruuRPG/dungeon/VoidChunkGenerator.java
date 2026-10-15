package net.tkgon.mc.iruuRPG.dungeon;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.ChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/** Generates nothing: dungeon template worlds are built by hand, so anything outside the build stays empty. */
public final class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public @Nullable Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) {
        return new Location(world, 0.5, 65.0, 0.5);
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
