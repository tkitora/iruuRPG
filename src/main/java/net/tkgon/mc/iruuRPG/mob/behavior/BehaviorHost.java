package net.tkgon.mc.iruuRPG.mob.behavior;

import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * What a behavior tree can ask of the mob it runs: move, face, hit, shoot, use a skill, look for a player. The mob service implements it, so the tree does not know how the
 * mob is made (the same hit, the same skills and the same shots as the mobs that use the older behavior).
 */
public interface BehaviorHost {

    Mob mob();

    /** The current game tick. */
    int tick();

    /** The nearest player within {@code range} that this mob may go after, or null. */
    Player findTarget(double range);

    /** Whether the player is still one this mob may go after (alive, online, not too far). */
    boolean valid(Player player, double range);

    /** Walks toward {@code at} at {@code speed} (1 = the mob's normal pace); false when there is no path. */
    boolean moveTo(Location at, double speed);

    void stopMoving();

    boolean hasPath();

    void face(Location at);

    /** One melee hit of {@code rate} times the mob's damage on the player (the mob swings). */
    void attack(Player target, double rate);

    int skillCount();

    /** Starts the skill {@code index} of the mob at the player; false when one is running already or it cannot start. */
    boolean startSkill(int index, Player target);

    boolean skillRunning();

    /** One shot of the mob's ranged attack; false when the mob has none. */
    boolean shoot(Player target, double rate);

    /** The life of the mob, 0 - 1. */
    double hpRatio();

    boolean canSee(Player player);

    /** Where the mob was set (its home). */
    Location home();

    /** Plays the animation of the mob's model once (the model feature); false when the mob has no model or the model has no such animation. */
    default boolean playAnimation(String name) {
        return false;
    }

    /** The length of the animation in seconds (0 when there is none). */
    default double animationSeconds(String name) {
        return 0.0;
    }

    /** Other mobs of the same kind of behavior near {@code at} (for tests that keep away from the crowd). */
    int crowdNear(Location at, double radius);
}
