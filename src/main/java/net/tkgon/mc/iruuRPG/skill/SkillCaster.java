package net.tkgon.mc.iruuRPG.skill;

import net.tkgon.mc.iruuRPG.stat.Element;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Whoever casts a skill (an enemy, or the test command's stand-in). The runner only talks to this, so every template
 * works the same for all of them.
 */
public interface SkillCaster {

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

    /** The players this skill can hit (alive, in the world, and fair game). */
    List<Player> candidates();

    /** Whether status effects and knockback are really applied (false for a test). */
    boolean applyEffects();

    /** Hurts a player: the caster's normal damage times {@code rate}. */
    void damage(Player victim, double rate);
}
