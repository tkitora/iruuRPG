package net.tkgon.mc.iruuRPG.combat.clone;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Predicate;

/**
 * The body of an assassin's clone when it is an entity with the owner's skin (see {@link CloneEntity}). The caller moves it
 * every tick, makes it swing at the moment of a blow and removes it at the end. {@link #entity} gives null when the entity
 * cannot be made (switched off in the config, or the server's internals are not what the build expects): the caller then
 * draws the figure of particles it always could.
 */
public interface CloneBody {

    void moveTo(double x, double y, double z, double yawDegrees);

    void swing();

    void remove();

    /** Whether the clone entity is used at all (combat.clone-entity, default true; it also stays off after one failure). */
    static boolean enabled(JavaPlugin plugin) {
        return !Failure.happened && plugin.getConfig().getBoolean("combat.clone-entity", true);
    }

    /** A clone of {@code owner} at {@code at}, shown to the players {@code visibleTo} accepts within {@code range}; null when it cannot be made. */
    static CloneBody entity(JavaPlugin plugin, Player owner, Location at, double range, Predicate<Player> visibleTo) {
        if (!enabled(plugin)) return null;
        try {
            CloneEntity entity = new CloneEntity(owner, at, range, visibleTo);
            return new CloneBody() {
                @Override
                public void moveTo(double x, double y, double z, double yawDegrees) {
                    entity.moveTo(x, y, z, (float) yawDegrees);
                }

                @Override
                public void swing() {
                    entity.swing();
                }

                @Override
                public void remove() {
                    entity.remove();
                }
            };
        } catch (Throwable problem) {
            // (a missing class or method of the server's internals is an Error, not an Exception)
            if (!Failure.happened) {
                Failure.happened = true;
                plugin.getLogger().warning("assassin clone: the entity could not be made (" + problem + "); the figure of particles is used instead");
            }
            return null;
        }
    }

    /** Takes away every clone entity still shown (the plugin stops). */
    static void removeAll() {
        try {
            CloneEntity.removeAll();
        } catch (Throwable ignored) {
            // the entity was never made, or the server is already gone
        }
    }

    /** Set once the entity failed, so the plugin does not try (and log) at every clone. */
    final class Failure {
        static volatile boolean happened;

        private Failure() {
        }
    }
}
