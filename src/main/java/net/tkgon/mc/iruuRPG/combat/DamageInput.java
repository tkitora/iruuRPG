package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;

public record DamageInput(
        PlayerProfile attacker,
        PlayerProfile victim,
        RpgItemDefinition weapon,
        double defenseReduction
) {

    public DamageInput {
        defenseReduction = Math.max(0.0, defenseReduction);
    }

    public DamageInput(PlayerProfile attacker, PlayerProfile victim, RpgItemDefinition weapon) {
        this(attacker, victim, weapon, 0.0);
    }
}
