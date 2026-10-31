package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.markpack.MarkGlyphs;
import org.bukkit.Color;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundMarksTest {

    private static final GroundMarks.Style RIMMED = new GroundMarks.Style(Color.RED, 90, Color.WHITE, 210, 0.3);
    private static final GroundMarks.Style PLAIN = new GroundMarks.Style(Color.RED, 90, Color.WHITE, 0, 0.0);

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
            // local +x is the right of the way (right of facing +z is -x)
            Vector3f side = turn.transform(new Vector3f(1, 0, 0));
            near(-way[1], side.x); near(0, side.y); near(way[0], side.z);
        }
    }

    @Test
    void theGlyphCentreLandsOnThePieceAndItIsStretchedToIt() {
        var shape = GroundMarks.transformation(0.6, 0.9, 0, 1);
        Vector3f centre = new Vector3f((float) MarkGlyphs.CENTER_X, 0, 0);
        centre.mul(shape.getScale());
        shape.getLeftRotation().transform(centre);
        centre.add(shape.getTranslation());
        near(0, centre.x); near(0, centre.y); near(0, centre.z);
        near(0.6 / MarkGlyphs.GLYPH, shape.getScale().x);
        near(0.9 / MarkGlyphs.GLYPH, shape.getScale().y);
    }

    @Test
    void aCircleIsOneOrTwoPiecesWithTheNearestRim() {
        assertEquals(1, GroundMarks.circle(0, 4, PLAIN).size());
        List<GroundMarks.Piece> pieces = GroundMarks.circle(2, 4, RIMMED);
        assertEquals(2, pieces.size());
        near(8, pieces.get(0).cell().across());
        near(2, pieces.get(0).cell().forward());
        assertEquals(MarkGlyphs.disc(MarkGlyphs.Part.FILL, MarkGlyphs.nearestRim(0.3 / 4)), pieces.get(0).glyph());
        assertEquals(MarkGlyphs.disc(MarkGlyphs.Part.RIM, MarkGlyphs.nearestRim(0.3 / 4)), pieces.get(1).glyph());
        assertEquals(Color.WHITE, pieces.get(1).color());
    }

    @Test
    void aFanIsWedgesSideBySideWithStraightRimsOnlyOnItsOwnSides() {
        assertEquals(List.of(90, 10), MarkGlyphs.split(100));
        assertEquals(List.of(90, 30), MarkGlyphs.split(120));
        assertEquals(List.of(30, 5, 2), MarkGlyphs.split(37));
        assertEquals(2, GroundMarks.fan(5, 90, RIMMED).size());
        List<GroundMarks.Piece> pieces = GroundMarks.fan(5, 100, RIMMED);
        assertEquals(4, pieces.size());
        int rim = MarkGlyphs.nearestRim(0.3 / 5);
        assertEquals(MarkGlyphs.wedge(90, MarkGlyphs.Part.FILL, rim, MarkGlyphs.Sides.FIRST), pieces.get(0).glyph());
        assertEquals(MarkGlyphs.wedge(10, MarkGlyphs.Part.FILL, rim, MarkGlyphs.Sides.SECOND), pieces.get(2).glyph());
        near(Math.toRadians(-50), pieces.get(0).cell().turn());
        near(Math.toRadians(40), pieces.get(2).cell().turn());
        assertEquals(1, GroundMarks.circle(0, 5, PLAIN).size());
        assertEquals(2, GroundMarks.fan(5, 360, RIMMED).size());
    }

    @Test
    void aBoxIsItsInsideAndFourStripsThatDoNotOverlap() {
        List<GroundMarks.Piece> pieces = GroundMarks.box(1, 6, 2, RIMMED);
        assertEquals(5, pieces.size());
        double area = 0;
        for (GroundMarks.Piece piece : pieces) area += piece.cell().across() * piece.cell().along();
        near(12, area);   // the pieces add up to the box: no overlap, no gap
        assertEquals(1, GroundMarks.box(1, 6, 2, PLAIN).size());
    }

    @Test
    void theInsideAndTheRimOfAGlyphFitTogether() {
        for (MarkGlyphs.Glyph fill : MarkGlyphs.all()) {
            if (fill.part() != MarkGlyphs.Part.FILL) continue;
            MarkGlyphs.Glyph rim = MarkGlyphs.all().stream().filter(g -> g.kind() == fill.kind() && g.angle() == fill.angle() && g.part() == MarkGlyphs.Part.RIM
                    && g.rim() == fill.rim() && g.sides() == fill.sides()).findFirst().orElseThrow();
            MarkGlyphs.Glyph whole = MarkGlyphs.all().stream().filter(g -> g.kind() == fill.kind() && g.angle() == fill.angle() && g.part() == MarkGlyphs.Part.WHOLE)
                    .findFirst().orElseThrow();
            for (double u = -0.97; u < 1; u += 0.13) {
                for (double v = -0.97; v < 1; v += 0.13) {
                    boolean in = MarkGlyphs.outside(fill, u, v) < 0, onRim = MarkGlyphs.outside(rim, u, v) < 0, inWhole = MarkGlyphs.outside(whole, u, v) < 0;
                    assertTrue(!(in && onRim), "overlap " + fill + " at " + u + "," + v);
                    assertEquals(inWhole, in || onRim, "gap " + fill + " at " + u + "," + v);
                }
            }
        }
    }

    @Test
    void theFontPackHasEveryGlyphAndStaysSmall() throws Exception {
        byte[] pack = MarkGlyphs.pack();
        assertTrue(pack.length < 4_000_000, "pack " + pack.length + " bytes");
        try (var zip = new java.util.zip.ZipInputStream(new java.io.ByteArrayInputStream(pack))) {
            int pictures = 0;
            boolean font = false;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.getName().startsWith("assets/iruurpg/textures/font/marks/")) pictures++;
                if (entry.getName().equals("assets/iruurpg/font/marks.json")) font = true;
            }
            assertEquals(MarkGlyphs.all().size(), pictures);
            assertTrue(font);
        }
        // every glyph's last column has some alpha (so the game gives it its full width)
        for (MarkGlyphs.Glyph glyph : List.of(MarkGlyphs.all().get(0), MarkGlyphs.all().get(5), MarkGlyphs.all().get(40))) {
            assertTrue((MarkGlyphs.image(glyph).getRGB(MarkGlyphs.SIZE - 1, 0) >>> 24) > 0);
        }
    }
}
