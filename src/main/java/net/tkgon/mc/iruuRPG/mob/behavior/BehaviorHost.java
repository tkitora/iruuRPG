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

    /** The unique id of the mob. */
    default java.util.UUID id() {
        return mob().getUniqueId();
    }

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

    /** The same, turning to follow the player while it winds up when {@code track} (else the mob keeps the direction it started with). */
    default boolean startSkill(int index, Player target, boolean track) {
        return startSkill(index, target);
    }

    default boolean startSkill(String id, Player target, boolean track) {
        return startSkill(id, target);
    }

    /** Starts one of the mob's own skills that is ready and can reach the player from here (the same choice as the mobs without a tree); false when none. */
    default boolean startUsableSkill(Player target, boolean track) {
        return false;
    }

    /** Starts the skill with this id at the player: one of the mob's own `skills:` with that id, else the skill of the skill tool (skills/*.yml); false when there is none or one runs. */
    default boolean startSkill(String id, Player target) {
        return false;
    }

    /** One shot of the mob's ranged attack; false when the mob has none. */
    boolean shoot(Player target, double rate);

    /** The life of the mob, 0 - 1. */
    double hpRatio();

    boolean canSee(Player player);

    /** Where the mob was set (its home). */
    Location home();

    /** An area attack: every player in the area takes {@code rate} times the mob's damage and is pushed away by {@code knockback}; how many were hit. */
    default int areaAttack(Area area, double rate, double knockback) {
        return 0;
    }

    /** Shows the area on the ground for {@code ticks} (a warning before the hit). */
    default void telegraph(Area area, int ticks) {
    }

    /** The id of the kind of mob this is (the mobs of one kind act as a group). */
    default String typeId() {
        return "";
    }

    /** How many other living mobs run by a behavior tree are within {@code radius} blocks (only the same kind when {@code sameType}). */
    default int alliesNear(double radius, boolean sameType) {
        return 0;
    }

    /** Where the other living behavior-tree mobs within {@code radius} blocks are (only the same kind when {@code sameType}). */
    default java.util.List<Location> allyLocations(double radius, boolean sameType) {
        return java.util.List.of();
    }

    /** What the behavior-tree mobs share (attack slots, shared cooldowns). */
    default Coordinator coordinator() {
        return SOLO;
    }

    /** The coordinator of a host that has none: nobody else uses it. */
    Coordinator SOLO = new Coordinator();

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
