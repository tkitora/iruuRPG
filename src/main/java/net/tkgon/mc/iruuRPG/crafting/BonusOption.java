package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementKind;
import net.tkgon.mc.iruuRPG.stat.StatLabels;
import net.tkgon.mc.iruuRPG.stat.StatType;

/** One thing a craft can enhance: a normal stat, or one element stat of one color. */
public record BonusOption(StatType stat, ElementKind kind, Element element) {

    public static BonusOption ofStat(StatType stat) {
        return new BonusOption(stat, null, null);
    }

    public static BonusOption ofElement(ElementKind kind, Element element) {
        return new BonusOption(null, kind, element);
    }

    public boolean isElement() {
        return stat == null;
    }

    public String label() {
        return isElement() ? kind.name(element) : StatLabels.name(stat);
    }

    public boolean percent() {
        return isElement() ? kind.percent() : StatLabels.isPercent(stat);
    }

    /** The key under craft-caps.stat-max in config.yml, e.g. "strength" or "element-damage-percent". */
    public String capKey() {
        return isElement() ? "element-" + kind.key() : stat.key();
    }
}
