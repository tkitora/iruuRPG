package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.SkillSlots;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.entity.Player;

/**
 * What a skill of {@link SkillSlots} does while its class has no move of its own (the redesign of 2026-11-02 is made class by class): 小・中・大
 * throw the weapon's skills 1 - 3 (the heavy attacks of t116 - t138) with their own cooldowns, the ULT is the class's special (its active skill of
 * F), util has nothing.
 */
public final class SkillFallback implements SkillSlots.Fallback {

    private final AttackService attackService;
    private final ClassSkillListener classSkills;

    public SkillFallback(AttackService attackService, ClassSkillListener classSkills) {
        this.attackService = attackService;
        this.classSkills = classSkills;
    }

    @Override
    public boolean use(Player player, SkillSlots.Slot slot, RpgItemDefinition weapon) {
        return switch (slot) {
            case ULT -> {
                if (!classSkills.special(player)) player.sendMessage("[iruuRPG] ULT は、まだありません (クラスの解放ノードで解放されます)。");
                yield true;
            }
            case UTIL -> false;
            default -> attackService.heavyByKey(player, slot.heavyIndex());
        };
    }

    @Override
    public long[] cooldown(Player player, SkillSlots.Slot slot, RpgItemDefinition weapon) {
        return switch (slot) {
            case ULT -> classSkills.specialCooldown(player);
            case UTIL -> null;
            default -> attackService.hasHeavies(player, weapon) ? attackService.heavyCooldown(player, weapon, slot.heavyIndex()) : null;
        };
    }
}
