package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

/** Lets the tutorial cap damage, lock attacks and handle hits on its practice target. */
public interface TutorialDamageHook {

    /** True while the player is inside the tutorial (damage is capped below 100). */
    boolean isActive(Player player);

    /** True while attacks are locked (the player cannot attack at all). */
    boolean isAttackLocked(Player player);

    /** True if the entity is the tutorial's NPC (it can never be hurt). */
    boolean isNpc(LivingEntity entity);

    /** The player tried to hit the NPC. */
    void onNpcHit(Player attacker);

    /**
     * True for any entity the tutorial spawned (NPC, rat). Nobody but the tutorial player can hurt them, and nothing
     * (damage, debuffs, area effects) may ever land on them from other players.
     */
    boolean isProtected(LivingEntity entity);

    /** True if the entity is the NPC or the rat of this player's own tutorial run. */
    boolean isOwnTarget(Player player, LivingEntity entity);

    /** True if the entity is the tutorial's practice rat. */
    boolean isRat(LivingEntity entity);

    /** Called for every hit on the rat; the tutorial decides when it dies. */
    void onRatHit(Player attacker, LivingEntity rat);
}
