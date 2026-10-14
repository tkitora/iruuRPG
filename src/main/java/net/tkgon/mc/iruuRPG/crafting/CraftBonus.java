package net.tkgon.mc.iruuRPG.crafting;

import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementKind;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatLabels;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** What a craft rolled: normal stats and element stats. */
public record CraftBonus(StatSet stats, ElementStatSet elements) {

    public static CraftBonus empty() {
        return new CraftBonus(new StatSet(), new ElementStatSet());
    }

    /** Number of enhanced stats. */
    public int count() {
        return lines().size();
    }

    public boolean isEmpty() {
        return count() == 0;
    }

    /** Display lines such as "筋力 +3" / "赤属性ダメージ +50". */
    public List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (Map.Entry<StatType, Double> entry : stats.asMap().entrySet()) {
            lines.add(StatLabels.name(entry.getKey()) + " " + StatLabels.signed(entry.getKey(), entry.getValue()));
        }
        for (ElementKind kind : ElementKind.values()) {
            for (Element element : Element.values()) {
                double value = kind.get(elements, element);
                if (Math.abs(value) < 1.0E-9) continue;
                lines.add(kind.name(element) + " " + StatLabels.signedElement(value, kind.percent()));
            }
        }
        return lines;
    }
}
