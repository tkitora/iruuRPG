package net.tkgon.mc.iruuRPG.tools.procedural.plot;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * (t313, spec 6/6) How good a building looks, by the rules of the spec: its roof's share of its height (from the ground to the ridge) near 0.4 to 0.55, its
 * outline stepped clearly (the lots' eaves and ridges at heights of their own), its front balanced left and right; less for each step of one block and each
 * problem the checks found.
 */
final class Score {

    private Score() {
    }

    static double of(Layout layout, Roofs.Result roofs, Grid grid, List<String> problems) {
        return roofShare(layout, roofs) + outline(layout, roofs) + balance(grid, layout) - 0.5 * Checks.steps(layout, roofs) - 0.25 * problems.size();
    }

    /** The roof's share of the highest lot's height (ridge over eaves, against ridge over the ground). */
    static double share(Layout layout, Roofs.Result roofs) {
        Roofs.LotRoof highest = null;
        for (Roofs.LotRoof roof : roofs.roofs) if (roof.ridgeRow > Integer.MIN_VALUE && (highest == null || roof.ridgeRow > highest.ridgeRow)) highest = roof;
        if (highest == null) return 0;
        double total = highest.ridgeRow + 1;
        return total <= 0 ? 0 : Math.max(0, highest.ridgeRow - highest.geo.eave + 1) / total;
    }

    /** 1 inside 0.4 to 0.55, less the further out. */
    private static double roofShare(Layout layout, Roofs.Result roofs) {
        double share = share(layout, roofs);
        double off = share < 0.4 ? 0.4 - share : share > 0.55 ? share - 0.55 : 0;
        return Math.max(0, 1.0 - off * 5.0);
    }

    /** How clearly the outline steps: the different heights of eaves and ridges (two blocks apart or more count), up to 1. */
    private static double outline(Layout layout, Roofs.Result roofs) {
        if (layout.lots.size() <= 1) return 0.5;
        TreeSet<Integer> heights = new TreeSet<>();
        for (Roofs.LotRoof roof : roofs.roofs) {
            heights.add(roof.geo.eave);
            if (roof.ridgeRow > Integer.MIN_VALUE) heights.add(roof.ridgeRow);
        }
        int distinct = 0;
        Integer last = null;
        for (int h : heights) {
            if (last == null || h - last >= 2) distinct++;
            last = h;
        }
        return Math.min(1.0, distinct / (2.0 * layout.lots.size()));
    }

    /** How evenly the front's blocks lie either side of the site's middle (1: evenly). */
    private static double balance(Grid grid, Layout layout) {
        double middle = (layout.plan.siteWidth - 1) / 2.0;
        double left = 0;
        double right = 0;
        for (Map.Entry<Long, Cell> entry : grid.cells().entrySet()) {
            int x = Grid.x(entry.getKey());
            if (x < middle) left += middle - x;
            else right += x - middle;
        }
        double total = left + right;
        return total <= 0 ? 1 : 1.0 - Math.abs(left - right) / total;
    }
}
