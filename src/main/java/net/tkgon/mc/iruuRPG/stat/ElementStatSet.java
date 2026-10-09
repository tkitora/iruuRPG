package net.tkgon.mc.iruuRPG.stat;

import java.util.EnumMap;
import java.util.Map;

public final class ElementStatSet {

    private final EnumMap<Element, Double> damage = new EnumMap<>(Element.class);
    private final EnumMap<Element, Double> damagePercent = new EnumMap<>(Element.class);
    private final EnumMap<Element, Double> resist = new EnumMap<>(Element.class);

    public double damage(Element element) {
        return damage.getOrDefault(element, 0.0);
    }

    public double damagePercent(Element element) {
        return damagePercent.getOrDefault(element, 0.0);
    }

    public double resist(Element element) {
        return resist.getOrDefault(element, 0.0);
    }

    public void setDamage(Element element, double value) {
        putOrRemove(damage, element, value);
    }

    public void setDamagePercent(Element element, double value) {
        putOrRemove(damagePercent, element, value);
    }

    public void setResist(Element element, double value) {
        putOrRemove(resist, element, value);
    }

    public void addDamage(Element element, double value) {
        setDamage(element, damage(element) + value);
    }

    public void addDamagePercent(Element element, double value) {
        setDamagePercent(element, damagePercent(element) + value);
    }

    public void addResist(Element element, double value) {
        setResist(element, resist(element) + value);
    }

    public void addAll(ElementStatSet other) {
        for (Element element : Element.values()) {
            addDamage(element, other.damage(element));
            addDamagePercent(element, other.damagePercent(element));
            addResist(element, other.resist(element));
        }
    }

    public void replaceWith(ElementStatSet other) {
        damage.clear();
        damagePercent.clear();
        resist.clear();
        damage.putAll(other.damage);
        damagePercent.putAll(other.damagePercent);
        resist.putAll(other.resist);
    }

    public void clear() {
        damage.clear();
        damagePercent.clear();
        resist.clear();
    }

    public ElementStatSet copy() {
        ElementStatSet copy = new ElementStatSet();
        copy.damage.putAll(damage);
        copy.damagePercent.putAll(damagePercent);
        copy.resist.putAll(resist);
        return copy;
    }

    public Map<Element, Double> damageView() {
        return Map.copyOf(damage);
    }

    public Map<Element, Double> damagePercentView() {
        return Map.copyOf(damagePercent);
    }

    public Map<Element, Double> resistView() {
        return Map.copyOf(resist);
    }

    public boolean isEmpty() {
        return damage.isEmpty() && damagePercent.isEmpty() && resist.isEmpty();
    }

    private static void putOrRemove(EnumMap<Element, Double> target, Element element, double value) {
        if (Math.abs(value) < 1.0E-9) {
            target.remove(element);
        } else {
            target.put(element, value);
        }
    }
}
