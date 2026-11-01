package net.tkgon.mc.iruuRPG.combat;

/**
 * The numbers of a class's combo with one kind of weapon: five normal attacks (the fifth is the finisher; each can hit
 * several times) and five heavy attacks, number 0 - 4 (強1 - 強5, chosen by the buttons: {@link HeavyInput}). All can be set in the
 * config under {@code combat.combo.<plan>} ({@code hit-rate}, {@code heavy-damage-multiplier}, {@code heavy-cooldown-ticks},
 * {@code window-ticks}, {@code normal-gap-ticks}); these are the defaults.
 *
 * <p>Damage: {@code rates[i]} is the damage of each hit of normal attack i + 1 (a fraction of the weapon's damage) and
 * {@code hits[i]} how many hits it has; the heavy attacks use {@code heavyRates}, whose meaning is described by each combo.
 * By design an attack that lands all its hits does 100 - 150% of the weapon's damage (more for what is hard to land
 * or slow, less for what is easy and quick).
 */
public record ComboSpec(String plan, double[] rates, int[] hits, double[] heavyRates, int[] heavyCooldownTicks, int windowTicks) {

    public ComboSpec {
        if (rates.length != 5 || hits.length != 5 || heavyRates.length != 5 || heavyCooldownTicks.length != 5) {
            throw new IllegalArgumentException("a combo has five normal and five heavy attacks");
        }
    }
}
