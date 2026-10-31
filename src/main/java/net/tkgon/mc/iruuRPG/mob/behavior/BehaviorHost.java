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

    /** Turns the head toward a place, leaving the body to its walk (a mob circling its target keeps an eye on it). */
    default void lookAt(Location at) {
        face(at);
    }

    /**
     * Keeps the mob turned toward {@code target} while it walks: it steps sideways or backwards on its path instead of turning to it (a model turns with it, a mob without
     * one at least looks). Called again on every step of the tree; {@code null} lets the body follow its walk again.
     */
    default void faceWhileMoving(org.bukkit.entity.Entity target) {
        if (target != null) lookAt(target.getLocation().add(0.0, target.getHeight() * 0.85, 0.0));
    }

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

    /**
     * Calls up to {@code count} mobs of the kind {@code mobId} round the mob, within {@code radius} blocks, so that no more
     * than {@code maxAlive} of the ones it called live at once; they go after {@code target} (may be null). They go when it
     * dies or is gone. How many came (0 when as many as allowed live already); -1 when there is no such mob or no place.
     */
    default int summon(String mobId, int count, double radius, int maxAlive, Player target) {
        return -1;
    }

    /** Plays the animation of the mob's model once (the model feature); false when the mob has no model or the model has no such animation. */
    default boolean playAnimation(String name) {
        return false;
    }

    /** The length of the animation in seconds (0 when there is none). */
    default double animationSeconds(String name) {
        return 0.0;
    }

    /** Whether the mob flies now (`navigation: fly`, or `set-navigation`): it moves through the air of its room instead of walking. */
    default boolean flying() {
        return false;
    }

    /** Makes the mob fly (no gravity, its own steering) or walk again (gravity, the vanilla path finder): `set-navigation`. False when it cannot. */
    default boolean setFlying(boolean fly) {
        return false;
    }

    /** For a flying mob: a place at or right round {@code at} where it fits in the air, inside its room; null when there is none (and always for a walking mob). */
    default Location airSpot(Location at) {
        return null;
    }

    /** Whether the mob can get to {@code at}: walking, the vanilla path finder finds a path; flying, there is a way through the air of its room. */
    default boolean canReach(Location at) {
        return mob().getPathfinder().findPath(at) != null;
    }

    /** Other mobs of the same kind of behavior near {@code at} (for tests that keep away from the crowd). */
    int crowdNear(Location at, double radius);

    /**
     * A guard while a node with the decorator {@code guard} runs: the damage the mob takes is cut by {@code reduction}
     * (0.6 = 60 %), a player hitting it is pushed back a little ({@code power} sideways, {@code lift} up; at most once per
     * {@code recoilTicks}), and it does not flinch.
     */
    record Guard(double reduction, double power, double lift, int recoilTicks) {
    }

    /** Raises the guard (null lowers it). */
    default void guard(Guard guard) {
    }
}
