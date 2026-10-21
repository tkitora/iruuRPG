package net.tkgon.mc.iruuRPG.vfx;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Works the points of a large effect out off the main thread and draws them on it: {@code work} runs on an async task
 * (it must only do arithmetic on its own data, never touch the world or a player) and returns the numbers; {@code draw}
 * runs on the main thread in the next tick with them. For an effect that is a few hundred points or more.
 */
public final class FxAsync {

    private FxAsync() {
    }

    public static void run(Plugin plugin, Supplier<float[]> work, Consumer<float[]> draw) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            float[] result;
            try {
                result = work.get();
            } catch (RuntimeException exception) {
                plugin.getLogger().warning("vfx: async work failed: " + exception);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> draw.accept(result));
        });
    }
}
