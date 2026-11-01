package net.tkgon.mc.iruuRPG.combat;

import java.util.Map;

/**
 * The classes' moves of their skills ({@link SkillSlots}; the redesign of 2026-11-02), made class by class (a class not here still does what it
 * did before, {@code SkillFallback}). A class's moves are made for its favorite weapon; with another weapon its skills follow the template:
 * 小・中・大・util throw that weapon's skills 1 - 4 (until those are made too). Each cools down for its skill's time, not the heavy attack's.
 */
public final class SlotMoves {

    /**
     * The skills (heavy attacks 0 - 5 = skills 1 - 6 of t138) that 小・中・大・util throw with the class's favorite weapon. The mage (a ranged
     * weapon): 小 the chain of lightning (skill 6), 中 the crystal (skill 4: the crystal spikes), 大 the black hole (skill 5: the shrinking
     * sphere), util the re-activation (skill 1). The ranger ("gifted", a ranged weapon): 小 the piercing shot that sets off its traps and mines
     * (skill 2), 中 the rapid shots (skill 6), 大 the rain of arrows (skill 5), util the backstep shot that strengthens the arrows after it (skill 1).
     * The warrior (a melee weapon): 小 the colossus strike, a great downward cut ahead (skill 4), 中 the whirlwind (skill 3), 大 the shield bash
     * that leaves the shields round it (skill 1), util the shield rush with no push (skill 5). The cleric ("healer", a melee weapon): 小 the shield
     * bash (skill 2), 中 the pillar of light at the aim (skill 4), 大 the spinning prayer round it (skill 3), util the sanctuary that heals and hurts
     * (skill 1). The assassin (a melee weapon): 小 the crossing clones that cut the line ahead twice (skill 4), 中 the decoy that strikes the enemies
     * near it and stays (skill 1), 大 the blade storm (skill 3), util the shadow run that leaves a decoy (skill 6).
     */
    private static final Map<String, Own> OWN = Map.of(
            "mage", new Own(AttackType.RANGE, new int[]{5, 3, 4, 0}),
            "gifted", new Own(AttackType.RANGE, new int[]{1, 5, 4, 0}),
            "warrior", new Own(AttackType.MELEE, new int[]{3, 2, 0, 4}),
            "healer", new Own(AttackType.MELEE, new int[]{1, 3, 2, 0}),
            "assassin", new Own(AttackType.MELEE, new int[]{3, 0, 2, 5}));

    /** A class's favorite kind of weapon, and the heavy attacks its 小・中・大・util throw with it. */
    record Own(AttackType favorite, int[] heavies) {
    }

    private SlotMoves() {
    }

    /** Registers the moves of every class made so far. */
    public static void register(SkillSlots slots, AttackService attackService) {
        for (Map.Entry<String, Own> entry : OWN.entrySet()) {
            for (SkillSlots.Slot slot : new SkillSlots.Slot[]{SkillSlots.Slot.SMALL, SkillSlots.Slot.MEDIUM, SkillSlots.Slot.LARGE, SkillSlots.Slot.UTIL}) {
                Own own = entry.getValue();
                slots.register(entry.getKey(), slot, (player, weapon) ->
                        weapon != null && attackService.slotHeavy(player, weapon, heavyOf(own, slot, weapon.attackType())));
            }
        }
    }

    /** The heavy attack {@code slot} throws with a kind of weapon: the class's own with its favorite, else the template's (skills 1 - 4). */
    static int heavyOf(Own own, SkillSlots.Slot slot, AttackType kind) {
        return kind == own.favorite() ? own.heavies()[slot.ordinal()] : slot.ordinal();
    }

    /** The skills (1 - 6 of t138) that 小・中・大・util of a class throw with its favorite weapon (the pictures of their icons), or null as {@link #skillNumbers}. */
    public static int[] favoriteSkillNumbers(String classId) {
        Own own = classId == null ? null : OWN.get(classId);
        return own == null ? null : skillNumbers(classId, own.favorite());
    }

    /**
     * The skills (1 - 6 of t138) that 小・中・大・util of a class throw with a kind of weapon, or null when the class's moves are not made yet (its
     * 小・中・大 throw skills 1 - 3 and util has nothing).
     */
    public static int[] skillNumbers(String classId, AttackType kind) {
        Own own = classId == null ? null : OWN.get(classId);
        if (own == null) return null;
        int[] numbers = new int[4];
        for (int index = 0; index < numbers.length; index++) numbers[index] = heavyOf(own, SkillSlots.Slot.values()[index], kind) + 1;
        return numbers;
    }
}
