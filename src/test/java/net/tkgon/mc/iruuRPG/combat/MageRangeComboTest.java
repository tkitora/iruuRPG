package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.flow.FlowArgs;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffect;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffects;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mage's ranged combo: as much damage in a round of normal attacks as the ranger's bow, the reach of a spike, and the sphere of heavy 4 closing in. */
class MageRangeComboTest {

    private static double round(ComboSpec spec) {
        double sum = 0.0;
        for (int index = 0; index < 5; index++) sum += spec.rates()[index] * spec.hits()[index];
        return sum;
    }

    @Test
    void aRoundOfNormalAttacksDoesWhatTheRangersDoes() {
        double mage = round(MageMoves.SPEC);
        double ranger = round(new RangerRangeCombo().spec());
        assertEquals(ranger, mage, ranger * 0.05, "mage " + mage + " / ranger " + ranger);
        assertEquals(MeleeCombo.MAGE, MageMoves.SPEC.plan());
        // heavy 0 is the echo: no damage and no cooldown of the chain's own (the echo keeps its own)
        assertEquals(0.0, MageMoves.SPEC.heavyRates()[0]);
        assertEquals(0, MageMoves.SPEC.heavyCooldownTicks()[0]);
    }

    @Test
    void aSpikeReachesAlongItsLeaningAxis() {
        Vector base = new Vector(0.0, 64.0, 0.0);
        Vector axis = new Vector(0.0, Math.cos(Math.toRadians(36.0)), Math.sin(Math.toRadians(36.0)));
        // a point on the axis half way up, one beside the foot, one past the tip
        assertEquals(0.0, MageMoves.distanceToLine(base.clone().add(axis.clone().multiply(1.25)), base, axis, 2.5), 1.0E-9);
        assertEquals(1.0, MageMoves.distanceToLine(base.clone().add(new Vector(1.0, 0.0, 0.0)), base, axis, 2.5), 1.0E-9);
        assertEquals(1.0, MageMoves.distanceToLine(base.clone().add(axis.clone().multiply(3.5)), base, axis, 2.5), 1.0E-9);
    }

    @Test
    void theCollapseFlowClosesIn() {
        FlowEffect flow = FlowEffects.find("collapse");
        assertNotNull(flow);
        FlowArgs args = FlowArgs.parse(flow.params(), List.of("from=4", "to=0.6", "shrink=20"));
        var field = flow.field(args, 7L);
        double[] out = new double[3];
        // on a shell of 4 at the start the pull is small; at the end the same point is pulled in hard
        field.sample(4.0, 0.0, 0.0, 0, out);
        double early = out[0];
        field.sample(4.0, 0.0, 0.0, 20, out);
        assertTrue(out[0] < early - 0.5, "early " + early + " late " + out[0]);
        assertTrue(out[0] < 0.0);
    }
}
