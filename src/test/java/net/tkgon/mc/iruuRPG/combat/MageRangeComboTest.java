package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.vfx.flow.FlowArgs;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffect;
import net.tkgon.mc.iruuRPG.vfx.flow.FlowEffects;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The mage's ranged combo: as much damage in a round of normal attacks as the ranger's bow, and the flow of a collapse closing in. */
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
