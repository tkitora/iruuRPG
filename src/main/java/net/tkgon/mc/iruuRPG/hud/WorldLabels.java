package net.tkgon.mc.iruuRPG.hud;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Text labels fixed in the world (over fast travel points and dungeon gates). The label entities are not saved with
 * the world, so they vanish whenever their chunk unloads (nobody nearby) and on every restart. This keeps the list of
 * labels that should exist and puts each one back as soon as its chunk is loaded again (on the chunk load, and every
 * five seconds as a safety net), so they are always there when somebody comes by.
 */
public final class WorldLabels implements Listener {

    /** Where a label is and what it says. */
    public record Spec(String world, double x, double y, double z, Component text, boolean seeThrough) {
    }

    private static final class Entry {
        private final Spec spec;
        private UUID entity;

        private Entry(Spec spec) {
            this.spec = spec;
        }
    }

    private final JavaPlugin plugin;
    private final Map<String, List<Entry>> groups = new HashMap<>();
    private BukkitTask task;

    public WorldLabels(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkAll, 100L, 100L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (String group : new ArrayList<>(groups.keySet())) removeGroup(group);
    }

    /** Replaces all the labels of a group (e.g. "travel", "gates") with these. */
    public void set(String group, List<Spec> specs) {
        removeGroup(group);
        List<Entry> entries = new ArrayList<>();
        for (Spec spec : specs) entries.add(new Entry(spec));
        groups.put(group, entries);
        for (Entry entry : entries) ensure(entry);
    }

    private void removeGroup(String group) {
        List<Entry> old = groups.remove(group);
        if (old == null) return;
        for (Entry entry : old) {
            if (entry.entity == null) continue;
            Entity existing = Bukkit.getEntity(entry.entity);
            if (existing != null) existing.remove();
        }
    }

    private void checkAll() {
        for (List<Entry> entries : groups.values()) {
            for (Entry entry : entries) ensure(entry);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        World world = event.getWorld();
        int chunkX = event.getChunk().getX();
        int chunkZ = event.getChunk().getZ();
        // the entities of the chunk are not in yet while it loads: look again on the next tick
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (List<Entry> entries : groups.values()) {
                for (Entry entry : entries) {
                    Spec spec = entry.spec;
                    if (!spec.world().equals(world.getName())) continue;
                    if (((int) Math.floor(spec.x())) >> 4 != chunkX || ((int) Math.floor(spec.z())) >> 4 != chunkZ) continue;
                    ensure(entry);
                }
            }
        });
    }

    /** Spawns the label if its chunk is loaded and it is not there. */
    private void ensure(Entry entry) {
        Spec spec = entry.spec;
        World world = Bukkit.getWorld(spec.world());
        if (world == null) return;
        if (!world.isChunkLoaded(((int) Math.floor(spec.x())) >> 4, ((int) Math.floor(spec.z())) >> 4)) return;
        if (entry.entity != null) {
            Entity existing = Bukkit.getEntity(entry.entity);
            if (existing != null && existing.isValid()) return;
        }
        TextDisplay display = world.spawn(new org.bukkit.Location(world, spec.x(), spec.y(), spec.z()), TextDisplay.class, spawned -> {
            spawned.setBillboard(Display.Billboard.CENTER);
            spawned.setPersistent(false);
            spawned.setSeeThrough(spec.seeThrough());
            spawned.text(spec.text());
        });
        entry.entity = display.getUniqueId();
    }
}
