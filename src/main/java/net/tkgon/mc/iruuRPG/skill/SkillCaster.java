package net.tkgon.mc.iruuRPG.skill;

import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Whoever casts a skill (an enemy, or the test command's stand-in). The runner only talks to this, so every template
 * works the same for all of them.
 */
public interface SkillCaster {

    /** The player who casts it (their sounds are theirs: volume settings apply); null for enemies. */
    default Player owner() {
        return null;
    }

    World world();

    /** Feet position, with the yaw it is facing. */
    Location location();

    /** Eye position (where bolts and beams leave from). */
    Location eye();

    /** False once the caster is gone (dead, removed): the skill stops. */
    boolean valid();

    /** The element whose colors a skill with no colors of its own uses. */
    Element element();

    /** Stops the caster in place for the skill (movement off). */
    void freeze();

    /** Gives the caster its movement back. */
    void release();

    void face(Location at);

    void swing();

    /** Pushes the caster along (the dash template); movement must be on for it to work. */
    void lunge(Vector velocity);

    /** Whether the caster is in the air with no gravity (a flying mob): a dash then goes straight at the aim, up or down, with no hop. */
    default boolean airborne() {
        return false;
    }

    /** Moves the caster to {@code to} at once (the teleport template); it keeps its own yaw and pitch unless {@code to} has others. */
    void teleport(Location to);

    /** What this skill can hit (alive, in the world, and fair game): players for an enemy, enemies for a player. */
    List<? extends LivingEntity> candidates();

    /** The allies this skill can help (players for a player; none for an enemy). */
    default List<? extends LivingEntity> allyCandidates() {
        return List.of();
    }

    /** True for one of this caster's allies (a hit on them heals instead of hurting). */
    default boolean isAlly(LivingEntity entity) {
        return false;
    }

    /** Heals an ally by {@code ratio} of their max HP. */
    default void heal(LivingEntity ally, double ratio) {
    }

    /** Whether status effects and knockback are really applied (false for a test). */
    boolean applyEffects();

    /** Hurts one of the candidates: the caster's normal damage times {@code rate}. */
    void damage(LivingEntity victim, double rate);
}
