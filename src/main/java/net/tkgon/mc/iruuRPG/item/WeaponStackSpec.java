package net.tkgon.mc.iruuRPG.item;

/**
 * The stacks of a special weapon (the `stacks:` section of an item): every time the weapon's skill is cast it gains
 * {@code gain} stacks of its own; at {@code max} the stacks are spent and the skill {@code onMax} is cast as well.
 * Stacks fade away when the weapon's skill is not cast for {@code decaySeconds} (0 = they never fade).
 */
public record WeaponStackSpec(String name, int gain, int max, String onMax, double decaySeconds) {

    public WeaponStackSpec {
        name = name == null || name.isBlank() ? "スタック" : name;
        gain = Math.max(1, Math.min(20, gain));
        max = Math.max(2, Math.min(30, max));
        onMax = onMax == null ? "" : onMax.trim().toLowerCase(java.util.Locale.ROOT);
        decaySeconds = Math.max(0.0, Math.min(300.0, decaySeconds));
    }
}
