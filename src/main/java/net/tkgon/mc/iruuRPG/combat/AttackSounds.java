package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sounds of the attacks. There are no custom sound files, so every sound is a few quiet vanilla sounds layered and
 * pitched (a whoosh, a bright chime, an element accent) to get something that does not sound like the plain vanilla
 * combat noises. Volume overall: {@code attack-sounds.volume}; {@code attack-sounds.enabled: false} turns them off.
 */
public final class AttackSounds {

    private final JavaPlugin plugin;

    public AttackSounds(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---- basic attacks --------------------------------------------------------------------------------------

    public void swing(Location at, Element element) {
        play(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, 1.6f);
        play(at, Sound.ITEM_TRIDENT_THROW, 0.18f, 1.9f);
        accent(at, element, 0.2f);
    }

    public void rangeShot(Location at, Element element) {
        play(at, Sound.ENTITY_BLAZE_SHOOT, 0.22f, 2.0f);
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.9f);
        play(at, Sound.ITEM_CROSSBOW_SHOOT, 0.22f, 1.7f);
        accent(at, element, 0.25f);
    }

    public void hit(Location at, Element element, boolean critical) {
        play(at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.5f, 1.3f);
        play(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.22f, 1.5f);
        accent(at, element, 0.15f);
        if (critical) {
            play(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 1.2f);
            play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 2.0f);
        }
    }

    public void impact(Location at, Element element) {
        play(at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.4f, 1.6f);
        accent(at, element, 0.25f);
    }

    public void boltLaunch(Location at, Element element) {
        play(at, Sound.ENTITY_BLAZE_SHOOT, 0.3f, 1.9f);
        play(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.25f, 1.8f);
        accent(at, element, 0.25f);
    }

    // ---- skills ---------------------------------------------------------------------------------------------

    public void sweepMelee(Location at, Element element) {
        play(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.75f);
        play(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.35f, 1.5f);
        play(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.25f, 1.9f);
        accent(at, element, 0.35f);
    }

    public void sweepWave(Location at, Element element) {
        play(at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.3f);
        play(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.9f);
        play(at, Sound.ENTITY_GENERIC_EXPLODE, 0.12f, 2.0f);
        accent(at, element, 0.3f);
    }

    public void healBurst(Location at) {
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.5f);
        play(at, Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.3f, 1.6f);
        later(3, () -> play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 2.0f));
    }

    public void healRing(Location at) {
        play(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.7f);
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
        later(4, () -> play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.8f));
    }

    public void dash(Location at, Element element) {
        play(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.6f);
        play(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.22f, 2.0f);
        accent(at, element, 0.25f);
    }

    public void strikeMelee(Location at, Element element) {
        play(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 0.9f);
        play(at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 0.7f);
        play(at, Sound.ENTITY_GENERIC_EXPLODE, 0.15f, 1.8f);
        accent(at, element, 0.4f);
    }

    public void strikeCharge(Location at) {
        play(at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f);
        play(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.4f);
    }

    public void strikeBolt(Location at, Element element) {
        play(at, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.3f);
        play(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.3f, 1.4f);
        accent(at, element, 0.35f);
    }

    /** One slash cut: two quick whooshes, the second a little higher and brighter (the cross). */
    public void slashCut(Location at, Element element) {
        play(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.8f);
        play(at, Sound.ITEM_TRIDENT_THROW, 0.3f, 2.0f);
        later(2, () -> {
            play(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2.0f);
            play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 2.0f);
            accent(at, element, 0.3f);
        });
    }

    public void slashLaunch(Location at, Element element) {
        play(at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
        play(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.3f, 1.6f);
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.8f);
        accent(at, element, 0.3f);
    }

    public void slashImpact(Location at, Element element) {
        play(at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.6f, 1.2f);
        play(at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.3f, 1.4f);
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 2.0f);
        accent(at, element, 0.3f);
    }

    // ---- deployed objects -----------------------------------------------------------------------------------

    public void deployStart(Location at) {
        play(at, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        play(at, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.4f, 1.4f);
    }

    public void deployPulse(Location at) {
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.4f);
        play(at, Sound.BLOCK_BEACON_POWER_SELECT, 0.15f, 1.8f);
    }

    public void deployCrush(Location at, Element element) {
        play(at, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.2f);
        play(at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 0.7f);
        play(at, Sound.BLOCK_AMETHYST_CLUSTER_HIT, 0.5f, 0.9f);
        accent(at, element, 0.4f);
    }

    public void deployDashFinish(Location at, Element element) {
        play(at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.2f);
        play(at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35f, 1.5f);
        play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
        accent(at, element, 0.3f);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    /** A short flavor sound that goes with the element. */
    private void accent(Location at, Element element, float volume) {
        if (element == null) return;
        switch (element) {
            case RED -> play(at, Sound.ITEM_FIRECHARGE_USE, volume, 1.3f);
            case BLUE -> play(at, Sound.BLOCK_GLASS_BREAK, volume * 0.7f, 1.9f);
            case WHITE -> play(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, 1.6f);
            case GREEN -> play(at, Sound.ITEM_BONE_MEAL_USE, volume, 1.4f);
            case ORANGE -> play(at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, volume, 1.4f);
        }
    }

    private void later(long ticks, Runnable action) {
        if (!enabled() || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, action, ticks);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("attack-sounds.enabled", true);
    }

    private void play(Location at, Sound sound, float volume, float pitch) {
        if (at == null || !enabled()) return;
        World world = at.getWorld();
        if (world == null) return;
        float master = (float) Math.max(0.0, plugin.getConfig().getDouble("attack-sounds.volume", 1.0));
        float jitter = (float) ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
        world.playSound(at, sound, SoundCategory.PLAYERS, Math.min(1.0f, volume * master), Math.max(0.5f, Math.min(2.0f, pitch + jitter)));
    }
}
