package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sounds of the attacks. There are no custom sound files, so every sound is a few quiet vanilla sounds layered and
 * pitched (a whoosh, a bright chime, an element accent) to get something that does not sound like the plain vanilla
 * combat noises. Volume overall: {@code attack-sounds.volume}; {@code attack-sounds.enabled: false} turns them off.
 */
public final class AttackSounds {

    /** Farther than this nobody hears an attack sound (a sound's own range is 16 blocks at full volume). */
    private static final double HEARING_RANGE = 24.0;

    private final JavaPlugin plugin;
    private final SoundSettings settings;

    public AttackSounds(JavaPlugin plugin, SoundSettings settings) {
        this.plugin = plugin;
        this.settings = settings;
    }

    // ---- basic attacks --------------------------------------------------------------------------------------

    public void swing(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.MELEE;
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.45f, 1.6f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_THROW, 0.18f, 1.9f);
        accent(owner, kind, at, element, 0.2f);
    }

    public void rangeShot(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.RANGED;
        play(owner, kind, at, Sound.ENTITY_BLAZE_SHOOT, 0.22f, 2.0f);
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.9f);
        play(owner, kind, at, Sound.ITEM_CROSSBOW_SHOOT, 0.22f, 1.7f);
        accent(owner, kind, at, element, 0.25f);
    }

    /** An arrow loosed from a bow (the ranger's shots). */
    public void arrowShot(Player owner, Location at) {
        SoundKind kind = SoundKind.RANGED;
        play(owner, kind, at, Sound.ENTITY_ARROW_SHOOT, 0.7f, 1.25f);
        play(owner, kind, at, Sound.ITEM_CROSSBOW_SHOOT, 0.15f, 1.6f);
    }

    public void hit(Player owner, Location at, Element element, boolean critical) {
        SoundKind kind = SoundKind.HIT;
        // a dull body thud: bone knock + a wet slap under the player-attack punch (no bright chime)
        play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.6f, 0.75f);
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.35f, 0.85f);
        play(owner, kind, at, Sound.BLOCK_SLIME_BLOCK_HIT, 0.25f, 0.7f);
        accent(owner, kind, at, element, 0.1f);
        if (critical) {
            play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.55f, 0.9f);
            play(owner, kind, at, Sound.ITEM_TRIDENT_HIT, 0.3f, 1.3f);
        }
    }

    public void impact(Player owner, Location at, Element element) {
        SoundKind kind = owner == null ? SoundKind.ENEMY : SoundKind.HIT;
        play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.45f, 1.0f);
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.3f, 1.2f);
        accent(owner, kind, at, element, 0.2f);
    }

    public void boltLaunch(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.ENEMY;
        play(owner, kind, at, Sound.ENTITY_BLAZE_SHOOT, 0.3f, 1.9f);
        play(owner, kind, at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.25f, 1.8f);
        accent(owner, kind, at, element, 0.25f);
    }

    // ---- skills ---------------------------------------------------------------------------------------------

    public void sweepMelee(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.75f);
        play(owner, kind, at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.35f, 1.5f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.25f, 1.9f);
        accent(owner, kind, at, element, 0.35f);
    }

    public void sweepWave(Player owner, Location at, Element element) {
        sweepWave(owner, SoundKind.SKILL, at, element);
    }

    public void deploySweep(Player owner, Location at, Element element) {
        sweepWave(owner, SoundKind.DEPLOY, at, element);
    }

    private void sweepWave(Player owner, SoundKind kind, Location at, Element element) {
        play(owner, kind, at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.3f);
        play(owner, kind, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.9f);
        play(owner, kind, at, Sound.ENTITY_GENERIC_EXPLODE, 0.12f, 2.0f);
        accent(owner, kind, at, element, 0.3f);
    }

    public void healBurst(Player owner, Location at) {
        SoundKind kind = SoundKind.HEAL;
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.5f);
        play(owner, kind, at, Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.3f, 1.6f);
        later(3, () -> play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 2.0f));
    }

    public void healRing(Player owner, Location at) {
        SoundKind kind = SoundKind.HEAL;
        play(owner, kind, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.7f);
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.2f);
        later(4, () -> play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.8f));
    }

    public void dash(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.6f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.22f, 2.0f);
        accent(owner, kind, at, element, 0.25f);
    }

    public void strikeMelee(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 0.9f);
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.6f, 0.7f);
        play(owner, kind, at, Sound.ENTITY_GENERIC_EXPLODE, 0.15f, 1.8f);
        accent(owner, kind, at, element, 0.4f);
    }

    public void strikeCharge(Player owner, Location at) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.2f);
        play(owner, kind, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.3f, 1.4f);
    }

    public void strikeBolt(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ENTITY_BLAZE_SHOOT, 0.5f, 1.3f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.3f, 1.4f);
        accent(owner, kind, at, element, 0.35f);
    }

    /** One slash cut: two quick whooshes, the second a little higher and brighter (the cross). */
    public void slashCut(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1.8f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_THROW, 0.3f, 2.0f);
        later(2, () -> {
            play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 2.0f);
            play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.35f, 2.0f);
            accent(owner, kind, at, element, 0.3f);
        });
    }

    public void slashLaunch(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.4f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.3f, 1.6f);
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.8f);
        accent(owner, kind, at, element, 0.3f);
    }

    public void slashImpact(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.SKILL;
        play(owner, kind, at, Sound.ITEM_TRIDENT_HIT, 0.45f, 1.1f);
        play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.4f, 1.1f);
        play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.4f, 0.9f);
        accent(owner, kind, at, element, 0.3f);
    }

    // ---- skills made from templates (a sound style each; made by enemies / the test command, so the "enemy" kind) --

    public void skillWindup(Player owner, Location at, net.tkgon.mc.iruuRPG.skill.SoundStyle style, Element element) {
        if (style == net.tkgon.mc.iruuRPG.skill.SoundStyle.NONE) return;
        SoundKind kind = owner == null ? SoundKind.ENEMY : SoundKind.SKILL;
        play(owner, kind, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 0.8f);
        play(owner, kind, at, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.4f, 0.7f);
        accent(owner, kind, at, element, 0.3f);
    }

    public void skillCast(Player owner, Location at, net.tkgon.mc.iruuRPG.skill.SoundStyle style, Element element) {
        SoundKind kind = owner == null ? SoundKind.ENEMY : SoundKind.SKILL;
        switch (style) {
            case HEAVY -> {
                play(owner, kind, at, Sound.ENTITY_GENERIC_EXPLODE, 0.45f, 1.1f);
                play(owner, kind, at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.7f, 0.6f);
                play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.6f, 0.6f);
                accent(owner, kind, at, element, 0.4f);
            }
            case SHARP -> {
                play(owner, kind, at, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 0.7f);
                play(owner, kind, at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 1.1f);
                play(owner, kind, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.25f, 1.5f);
                accent(owner, kind, at, element, 0.3f);
            }
            case MAGIC -> {
                play(owner, kind, at, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.6f, 1.2f);
                play(owner, kind, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.4f, 1.6f);
                play(owner, kind, at, Sound.ENTITY_BLAZE_SHOOT, 0.2f, 1.7f);
                accent(owner, kind, at, element, 0.3f);
            }
            case NONE -> {
            }
        }
    }

    public void skillImpact(Player owner, Location at, net.tkgon.mc.iruuRPG.skill.SoundStyle style, Element element) {
        SoundKind kind = owner == null ? SoundKind.ENEMY : SoundKind.SKILL;
        switch (style) {
            case HEAVY -> play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.6f, 0.7f);
            case SHARP -> play(owner, kind, at, Sound.ITEM_TRIDENT_HIT, 0.4f, 1.2f);
            case MAGIC -> {
                play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.4f, 1.0f);
                play(owner, kind, at, Sound.ENTITY_GENERIC_EXPLODE, 0.12f, 2.0f);
            }
            case NONE -> {
            }
        }
    }

    // ---- deployed objects -----------------------------------------------------------------------------------

    public void deployStart(Player owner, Location at) {
        SoundKind kind = SoundKind.DEPLOY;
        play(owner, kind, at, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.6f);
        play(owner, kind, at, Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.4f, 1.4f);
    }

    public void deployPulse(Player owner, Location at) {
        SoundKind kind = SoundKind.DEPLOY;
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 1.4f);
        play(owner, kind, at, Sound.BLOCK_BEACON_POWER_SELECT, 0.15f, 1.8f);
    }

    public void deployCrush(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.DEPLOY;
        play(owner, kind, at, Sound.ENTITY_GENERIC_EXPLODE, 0.4f, 1.2f);
        play(owner, kind, at, Sound.ENTITY_IRON_GOLEM_ATTACK, 0.6f, 0.7f);
        play(owner, kind, at, Sound.BLOCK_BONE_BLOCK_HIT, 0.6f, 0.7f);
        accent(owner, kind, at, element, 0.4f);
    }

    public void deployDashFinish(Player owner, Location at, Element element) {
        SoundKind kind = SoundKind.DEPLOY;
        play(owner, kind, at, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5f, 1.2f);
        play(owner, kind, at, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35f, 1.5f);
        play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.6f);
        accent(owner, kind, at, element, 0.3f);
    }

    // ---- helpers --------------------------------------------------------------------------------------------

    /** A short flavor sound that goes with the element. */
    private void accent(Player owner, SoundKind kind, Location at, Element element, float volume) {
        if (element == null) return;
        switch (element) {
            case RED -> play(owner, kind, at, Sound.ITEM_FIRECHARGE_USE, volume, 1.3f);
            case BLUE -> play(owner, kind, at, Sound.BLOCK_GLASS_BREAK, volume * 0.7f, 1.9f);
            case WHITE -> play(owner, kind, at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume, 1.6f);
            case GREEN -> play(owner, kind, at, Sound.ITEM_BONE_MEAL_USE, volume, 1.4f);
            case ORANGE -> play(owner, kind, at, Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, volume, 1.4f);
        }
    }

    private void later(long ticks, Runnable action) {
        if (!enabled() || !plugin.isEnabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, action, ticks);
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("attack-sounds.enabled", true);
    }

    /**
     * Plays a sound for everybody near enough to hear it, each at their own setting: the loudness of this kind, and
     * (for sounds that other players make) the "hear others" switch. {@code owner} is who made the sound, null for
     * enemies.
     */
    private void play(Player owner, SoundKind kind, Location at, Sound sound, float volume, float pitch) {
        if (at == null || !enabled()) return;
        World world = at.getWorld();
        if (world == null) return;
        float master = (float) Math.max(0.0, plugin.getConfig().getDouble("attack-sounds.volume", 1.0));
        float jitter = (float) ThreadLocalRandom.current().nextDouble(-0.04, 0.04);
        float tuned = Math.max(0.5f, Math.min(2.0f, pitch + jitter));
        for (Player listener : world.getNearbyPlayers(at, HEARING_RANGE)) {
            if (owner != null && !listener.equals(owner) && !settings.hearOthers(listener)) continue;
            float factor = settings.volume(listener, kind) / 100.0f;
            if (factor <= 0.0f) continue;
            listener.playSound(at, sound, SoundCategory.PLAYERS, Math.min(1.0f, volume * master * factor), tuned);
        }
    }

    /** A sample of one kind, played for the player only (when they change its loudness in the settings). */
    public void preview(Player player, SoundKind kind) {
        Location at = player.getLocation().add(0.0, 1.0, 0.0);
        Element element = Element.WHITE;
        switch (kind) {
            case MELEE -> swing(player, at, element);
            case RANGED -> rangeShot(player, at, element);
            case SKILL -> sweepMelee(player, at, element);
            case HEAL -> healBurst(player, at);
            case DEPLOY -> deployPulse(player, at);
            case HIT -> hit(player, at, element, false);
            case ENEMY -> boltLaunch(null, at, element);
        }
    }
}
