package net.tkgon.mc.iruuRPG.combat;

/** The common part of a {@link ClassCombo}: the class, the weapon type and the numbers. */
abstract class BaseCombo implements ClassCombo {

    private final String classId;
    private final AttackType type;
    private final ComboSpec spec;

    BaseCombo(String classId, AttackType type, ComboSpec spec) {
        this.classId = classId;
        this.type = type;
        this.spec = spec;
    }

    @Override
    public String classId() {
        return classId;
    }

    @Override
    public AttackType type() {
        return type;
    }

    @Override
    public ComboSpec spec() {
        return spec;
    }
}
