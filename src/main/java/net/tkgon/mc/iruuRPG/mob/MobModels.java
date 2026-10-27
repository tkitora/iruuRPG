package net.tkgon.mc.iruuRPG.mob;

import org.bukkit.entity.LivingEntity;

import java.util.Map;

/**
 * What the mob service asks of the model feature (net.tkgon.mc.iruuRPG.model), if there is one: dress a mob in its model, play an animation on it, take the model off. The
 * mob service holds one of these or nothing, so the model feature can be taken out without touching the mobs.
 */
public interface MobModels {

    /** Dresses the mob in the model of its `model:` section (id, scale): its own body is hidden and the model follows it. */
    void attach(LivingEntity entity, Map<String, Object> section);

    /** Plays the animation once on the mob's model (if it has one and the model has that animation); false otherwise. */
    boolean play(LivingEntity entity, String animation);

    /** How long the animation is, in seconds (0 when the mob has no model or the model has no such animation). */
    double length(LivingEntity entity, String animation);

    /** How high the top of the mob's model is above its feet (blocks); 0 when it has no model (the game's own name of the mob is then drawn as usual). */
    default double height(LivingEntity entity) {
        return 0.0;
    }

    /** The mob flinches: its model plays `hurt` unless it is in the middle of another one-shot animation. True when it plays (the mob then stands still for a moment). */
    default boolean hurt(LivingEntity entity) {
        return false;
    }

    /** Takes the model off the mob. */
    void detach(LivingEntity entity);
}
