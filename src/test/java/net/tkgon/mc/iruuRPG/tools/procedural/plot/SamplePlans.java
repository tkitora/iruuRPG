package net.tkgon.mc.iruuRPG.tools.procedural.plot;

/** (t313) A few plans made by hand, for the pictures and the tests. */
final class SamplePlans {

    private SamplePlans() {
    }

    static int count() {
        return 4;
    }

    static String name(int index) {
        return switch (index) {
            case 0 -> "single";
            case 1 -> "main-wing";
            case 2 -> "cross";
            default -> "tower";
        };
    }

    static Plan plan(int index) {
        Plan plan = new Plan();
        plan.module = 4;
        plan.siteWidth = 33;
        plan.siteDepth = 29;
        switch (index) {
            case 0 -> {
                Plan.Lot main = new Plan.Lot("main", Plan.Shape.RECT, 4, 4, 13, 9, 2);
                main.floors.get(0).door = 2;
                main.floors.get(1).jetty[0] = 1;
                main.floors.get(1).jetty[2] = 1;
                main.roof.pitch = 1;
                main.roof.dormers = 2;
                main.roof.chimney = 1;
                main.porch = true;
                plan.lots.add(main);
            }
            case 1 -> {
                Plan.Lot main = new Plan.Lot("main", Plan.Shape.RECT, 4, 4, 17, 9, 2);
                main.floors.get(0).door = 2;
                main.floors.get(1).jetty[2] = 1;
                main.roof.chimney = 4;
                main.floors.get(1).door = 0;
                main.floors.get(1).stairs = true;
                Plan.Lot wing = new Plan.Lot("wing", Plan.Shape.RECT, 20, 4, 9, 9, 1);
                wing.roof.type = Plan.RoofType.SHED;
                plan.lots.add(main);
                plan.lots.add(wing);
            }
            case 2 -> {
                Plan.Lot main = new Plan.Lot("main", Plan.Shape.RECT, 4, 8, 21, 9, 2);
                main.floors.get(0).door = 2;
                main.roof.dormers = 2;
                Plan.Lot cross = new Plan.Lot("cross", Plan.Shape.RECT, 8, 16, 9, 9, 2);
                cross.floors.get(0).door = 2;
                cross.floors.get(1).balcony[2] = true;
                cross.floors.get(1).door = 2;
                cross.roof.special = Plan.Special.BELL;
                plan.lots.add(main);
                plan.lots.add(cross);
            }
            default -> {
                Plan.Lot main = new Plan.Lot("main", Plan.Shape.RECT, 4, 8, 17, 9, 2);
                main.floors.get(0).door = 2;
                main.roof.type = Plan.RoofType.HIP;
                Plan.Lot tower = new Plan.Lot("tower", Plan.Shape.ROUND, 20, 4, 9, 9, 4);
                tower.roof.type = Plan.RoofType.CONE;
                tower.roof.pitch = 3;
                for (Plan.Floor floor : tower.floors) floor.skirt = true;
                tower.roof.finial = true;
                main.foundation = Plan.Foundation.STILTS;
                main.base = 4;
                main.floors.get(0).balcony[2] = true;
                main.floors.get(0).balcony[1] = true;
                main.floors.get(0).balcony[3] = true;
                plan.lots.add(main);
                plan.lots.add(tower);
            }
        }
        return plan;
    }
}
