package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A repeating task that cancels itself when it throws, so a bug in a visual effect can
 * neither loop forever nor flood the log.
 */
abstract class SafeTask extends BukkitRunnable {

    private final JavaPlugin plugin;

    SafeTask(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    protected abstract void tick();

    @Override
    public final void run() {
        try {
            tick();
        } catch (RuntimeException exception) {
            cancel();
            plugin.getLogger().warning("Effect task stopped after an error: " + exception);
        }
    }
}
