package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;

public record DamageInput(
        PlayerProfile attacker,
        PlayerProfile victim,
        RpgItemDefinition weapon
) {
}
