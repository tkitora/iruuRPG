package net.tkgon.mc.iruuRPG.stat;

/** The three element stats an item can have, per color: damage, damage %, resist %. */
public enum ElementKind {
    DAMAGE("damage", "属性ダメージ", false),
    DAMAGE_PERCENT("damage-percent", "属性ダメージ", true),
    RESIST("resist", "属性防御", true);

    private final String key;
    private final String label;
    private final boolean percent;

    ElementKind(String key, String label, boolean percent) {
        this.key = key;
        this.label = label;
        this.percent = percent;
    }

    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public boolean percent() {
        return percent;
    }

    public double get(ElementStatSet set, Element element) {
        return switch (this) {
            case DAMAGE -> set.damage(element);
            case DAMAGE_PERCENT -> set.damagePercent(element);
            case RESIST -> set.resist(element);
        };
    }

    public void add(ElementStatSet set, Element element, double value) {
        switch (this) {
            case DAMAGE -> set.addDamage(element, value);
            case DAMAGE_PERCENT -> set.addDamagePercent(element, value);
            case RESIST -> set.addResist(element, value);
        }
    }

    /** Display name such as "赤属性ダメージ". */
    public String name(Element element) {
        return element.displayName() + label;
    }
}
