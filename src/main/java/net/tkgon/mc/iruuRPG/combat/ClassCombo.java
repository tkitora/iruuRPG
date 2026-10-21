package net.tkgon.mc.iruuRPG.combat;

/**
 * The combo of one class with one kind of weapon (the class's internal id and the weapon's attack type): what each of
 * its five normal attacks and five heavy attacks does. The chain, the cooldowns, the clicks and the "favorite weapon" rule
 * are handled by {@link ComboHub} and {@link AttackService}; a combo only draws and hits.
 */
public interface ClassCombo {

    /** The internal id of the class (gifted = the ranger, healer = the cleric). */
    String classId();

    AttackType type();

    ComboSpec spec();

    /** Normal attack {@code step.stage()} (1 - 5). */
    void normal(MoveKit kit, MeleeCombo.Step step);

    /** Heavy attack {@code step.heavyIndex()} (0 - 4): the number of normal attacks thrown before it. */
    void heavy(MoveKit kit, MeleeCombo.Step step);
}
