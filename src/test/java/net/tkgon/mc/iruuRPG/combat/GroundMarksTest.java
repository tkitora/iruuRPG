package net.tkgon.mc.iruuRPG.combat;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundMarksTest {

    private static void near(double expected, double actual) {
        assertEquals(expected, actual, 1.0E-4);
    }

    @Test
    void aFlatPieceFacesUpAndPointsItsTopAlongItsWay() {
        double[][] ways = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}, {Math.sqrt(0.5), Math.sqrt(0.5)}};
        for (double[] way : ways) {
            Quaternionf turn = GroundMarks.flatAlong(way[0], way[1]);
            Vector3f face = turn.transform(new Vector3f(0, 0, 1));
            near(0, face.x); near(1, face.y); near(0, face.z);
            Vector3f top = turn.transform(new Vector3f(0, 1, 0));
            near(way[0], top.x); near(0, top.y); near(way[1], top.z);
            Vector3f side = turn.transform(new Vector3f(1, 0, 0));
            near(0, side.y);
            near(0, side.x * way[0] + side.z * way[1]);
        }
    }

    @Test
    void theGlyphCentreLandsOnThePiece() {
        GroundMarks.Glyph glyph = new GroundMarks.Glyph("■", 0.15, -0.0125, 0.1375, 1.0, 150);
        var shape = GroundMarks.transformation(glyph, 0.6, 0.9, 0, 1);
        // a point at the glyph's centre (local) goes to the entity's place
        Vector3f centre = new Vector3f((float) glyph.centerX(), (float) glyph.centerY(), 0);
        centre.mul(shape.getScale());
        shape.getLeftRotation().transform(centre);
        centre.add(shape.getTranslation());
        near(0, centre.x); near(0, centre.y); near(0, centre.z);
        near(0.6 / 0.15, shape.getScale().x);
        near(0.9 / 0.15, shape.getScale().y);
    }

    /** Whether a point (forward, right) is inside one of the pieces (each a rectangle turned by its angle). */
    private static boolean covered(List<GroundMarks.Cell> cells, double f, double r) {
        for (GroundMarks.Cell c : cells) {
            double df = f - c.forward(), dr = r - c.right();
            double along = df * Math.cos(c.turn()) + dr * Math.sin(c.turn());
            double across = -df * Math.sin(c.turn()) + dr * Math.cos(c.turn());
            if (Math.abs(along) <= c.along() / 2.0 + 1.0E-6 && Math.abs(across) <= c.across() / 2.0 * 1.12 + 1.0E-6) return true;
        }
        return false;
    }

    @Test
    void aCircleIsTiledWithoutHolesAndKeepsInsideItsEdge() {
        List<GroundMarks.Cell> cells = GroundMarks.circle(2.0, 4.0, 0.75, 160);
        assertTrue(cells.size() <= 160);
        for (double a = 0; a < Math.PI * 2; a += 0.21) {
            for (double d = 0.05; d < 3.9; d += 0.17) assertTrue(covered(cells, 2.0 + Math.cos(a) * d, Math.sin(a) * d), "hole at " + a + " / " + d);
        }
        for (GroundMarks.Cell c : cells) assertTrue(Math.hypot(c.forward() - 2.0, c.right()) <= 4.0);
    }

    @Test
    void aFanOpensForwardOnly() {
        List<GroundMarks.Cell> cells = GroundMarks.fan(5.0, 90.0, 0.75, 160);
        for (GroundMarks.Cell c : cells) assertTrue(Math.abs(Math.atan2(c.right(), c.forward())) <= Math.toRadians(45.0) + 1.0E-6);
        assertTrue(covered(cells, 3.0, 0.0));
        assertTrue(covered(cells, 3.0, 2.5));
    }

    @Test
    void aBoxIsAGridAheadAndBigAreasUseBiggerPieces() {
        List<GroundMarks.Cell> cells = GroundMarks.box(1.0, 6.0, 2.0, 0.75, 160);
        assertEquals(8 * 3, cells.size());
        for (GroundMarks.Cell c : cells) { assertTrue(c.forward() > 1.0 && c.forward() < 7.0); assertTrue(Math.abs(c.right()) < 1.0); }
        assertTrue(GroundMarks.circle(0.0, 20.0, 0.75, 160).size() <= 160);
        assertTrue(GroundMarks.box(0.0, 30.0, 10.0, 0.75, 160).size() <= 160);
    }
}
