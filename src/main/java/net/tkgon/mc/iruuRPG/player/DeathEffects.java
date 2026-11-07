package net.tkgon.mc.iruuRPG.player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.tkgon.mc.iruuRPG.combat.Sfx;
import net.tkgon.mc.iruuRPG.markpack.HudGlyphs;
import net.tkgon.mc.iruuRPG.vfx.Fx;
import net.tkgon.mc.iruuRPG.vfx.Gradient;
import net.tkgon.mc.iruuRPG.vfx.Palettes;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.time.Duration;

/**
 * (t291) What a player's death looks and sounds like, now that they come back at once (no death screen):
 * <ul>
 *     <li>where they fell, for everyone near: a soul leaving the body (a puff of smoke, soul flames rising and narrowing for half a second, a ring of
 *     soul-blue dust on the ground) and {@link Sfx#DEATH};</li>
 *     <li>on their own screen as they come back: the world black at once, fading back ({@code death-effects.dark-ticks} black, then
 *     {@code fade-ticks} of fading: a title of the HUD font's black veil, {@link HudGlyphs#veils}), {@code death-effects.title} under it, and
 *     {@link Sfx#DEATH_SELF} for them alone;</li>
 *     <li>as the screen clears, where they came back, for everyone near: a ring of gold at their feet, a gold spiral rising round them, a few totem
 *     sparks, and {@link Sfx#REVIVE}.</li>
 * </ul>
 * A player who comes back as a spectator (downed in a dungeon: the dungeon shows its own title) gets only the first part, and so does a respawn that was
 * not a death (out of the End). The sounds are the roles' ({@code sound-design} can change them). Switched off by {@code death-effects.enabled: false}.
 */
public final class DeathEffects implements Listener {

    private static final Gradient SOUL = Gradient.of(Color.fromRGB(30, 70, 90), Color.fromRGB(80, 190, 215), Color.fromRGB(220, 255, 255));

    private final JavaPlugin plugin;

    public DeathEffects(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("death-effects.enabled", true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled()) return;
        Location at = event.getEntity().getLocation().clone();
        Sfx.DEATH.play(at, 1.0f, 1.0f);
        soulLeaves(at);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        if (!enabled() || event.getRespawnReason() != PlayerRespawnEvent.RespawnReason.DEATH) return;
        Player player = event.getPlayer();
        // (after the respawn is done, and after the dungeon's own task that makes a downed player a spectator)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.isDead() || player.getGameMode() == GameMode.SPECTATOR) return;
            int dark = Math.max(0, plugin.getConfig().getInt("death-effects.dark-ticks", 8));
            int fade = Math.max(1, plugin.getConfig().getInt("death-effects.fade-ticks", 30));
            String words = plugin.getConfig().getString("death-effects.title", "力尽きた…");
            Component veil = Component.text(HudGlyphs.veils()).font(HudGlyphs.FONT).color(TextColor.color(0x000000));
            player.showTitle(Title.title(veil, Component.text(words == null ? "" : words, NamedTextColor.GRAY),
                    Title.Times.times(Duration.ZERO, Duration.ofMillis(dark * 50L), Duration.ofMillis(fade * 50L))));
            Sfx.DEATH_SELF.playTo(player, 1.0f, 1.0f);
            // (back as the black thins out)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR) comeBack(player);
            }, dark + fade / 3L);
        });
    }

    /** A soul leaving at {@code at}: smoke, a ring of soul dust on the ground, soul flames rising and drawing in over ten ticks. */
    private void soulLeaves(Location at) {
        World world = at.getWorld();
        Fx fx = Fx.of(null, world, at);
        if (!fx.active()) return;
        fx.particle(Particle.SMOKE_NORMAL, at.getX(), at.getY() + 0.8, at.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
        fx.flatRing(at.clone().add(0, 0.1, 0), 0.9, 28, SOUL, 0.4, 0.5, 1.2);
        for (int tick = 0; tick < 10; tick += 2) {
            int step = tick;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Fx later = Fx.of(null, world, at);
                double rise = 0.4 + step * 0.22;
                double radius = 0.5 * (1.0 - step / 12.0);
                for (int arm = 0; arm < 3; arm++) {
                    double angle = step * 0.6 + arm * Math.PI * 2.0 / 3.0;
                    later.particle(Particle.SOUL_FIRE_FLAME, at.getX() + Math.cos(angle) * radius, at.getY() + rise, at.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
                }
                later.particle(Particle.SOUL, at.getX(), at.getY() + rise, at.getZ(), 2, 0.15, 0.1, 0.15, 0.02);
            }, tick);
        }
    }

    /** Coming back: {@link Sfx#REVIVE}, a ring of gold at the feet, a few totem sparks and a gold spiral rising round the player over 16 ticks. */
    private void comeBack(Player player) {
        Location at = player.getLocation().clone();
        World world = at.getWorld();
        Sfx.REVIVE.play(at, 1.0f, 1.0f);
        Fx fx = Fx.of(null, world, at);
        if (!fx.active()) return;
        fx.flatRing(at.clone().add(0, 0.1, 0), 1.0, 32, Palettes.GOLD, 0.5, 0.5, 1.3);
        fx.particle(Particle.TOTEM, at.getX(), at.getY() + 1.0, at.getZ(), 18, 0.3, 0.5, 0.3, 0.25);
        for (int tick = 0; tick < 16; tick += 2) {
            int step = tick;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                Location centre = player.getLocation();
                Fx later = Fx.of(null, centre.getWorld(), centre);
                later.spiral(centre.clone().add(0, step * 0.13, 0), new Vector(1, 0, 0), new Vector(0, 0, 1), new Vector(0, 1, 0),
                        0.75, 0.6, 0.35, step * 0.7, 0.3, 8, Palettes.GOLD, 1.0);
                if (step % 4 == 0) later.particle(Particle.END_ROD, centre.getX(), centre.getY() + 0.3 + step * 0.13, centre.getZ(), 2, 0.4, 0.1, 0.4, 0.01);
            }, tick);
        }
    }
}
