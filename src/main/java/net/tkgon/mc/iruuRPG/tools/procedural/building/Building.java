package net.tkgon.mc.iruuRPG.tools.procedural.building;

import java.util.ArrayList;
import java.util.List;

/**
 * (t295) What a building is made of before it is made: its masses ({@link Mass}, the main one first, then the ones added to it, then the towers), the podiums
 * they stand on, and the parts to put on them ({@link Part}). {@link BuildingGenerator} makes the blocks from it.
 */
public final class Building {

    /**
     * A podium (基壇): a plan and its height; its ledge a block out of the walls, topped with stairs facing out. (t302) With a {@code terrace} its top is flat
     * out to its edge, a rail round it when it is 2 or more high.
     */
    public record Podium(Footprint plan, int height, boolean terrace) {

        public Podium(Footprint plan, int height) {
            this(plan, height, false);
        }
    }

    /** The parts that can be put on a building, each where its anchor allows. */
    public enum PartType {
        CHIMNEY, DORMER, BAY_WINDOW, BALCONY, TURRET, PORCH, FINIAL, SAILS
    }

    /** A part to put: on mass {@code mass}, by the numbers of its kind ({@code side}: the way it faces, 0 north .. 3 west; {@code at}: which bay, or which corner; {@code storey}). */
    public record Part(PartType type, int mass, int side, int at, int storey) {
    }

    /** (t302) A part of the house base made on the building (a dormer, a chimney, a balcony, a bay window, stairs): the part, its place in the list, its frame. */
    public record Job(HouseSpec.Module module, int owner, HouseSpec.Frame frame, Mass base) {
    }

    public final List<Mass> masses = new ArrayList<>();
    public final List<Job> jobs = new ArrayList<>();
    public final List<Podium> podiums = new ArrayList<>();
    public final List<Part> parts = new ArrayList<>();
    /** What it was made as (the pattern's name), for the messages. */
    public String pattern = "";

    public Mass add() {
        Mass mass = new Mass(masses.size());
        masses.add(mass);
        return mass;
    }

    public Mass mass(int id) {
        return masses.get(id);
    }

    /** Moves everything so the middle of the main mass's plan is at (0, 0) (the block clicked). */
    public void center() {
        if (masses.isEmpty()) return;
        int[] box = masses.get(0).plan.box();
        int dx = -(box[0] + box[2]) / 2;
        int dz = -(box[1] + box[3]) / 2;
        for (Mass m : masses) m.plan = m.plan.moved(dx, dz);
        podiums.replaceAll(podium -> new Podium(podium.plan().moved(dx, dz), podium.height(), podium.terrace()));
    }
}
