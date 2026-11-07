package net.tkgon.mc.iruuRPG.tools.procedural.plot;

/**
 * (t313) What a block of the building is for. Each role has its own block in the palette (the block chosen for it); the shape the block comes in (a stair, a
 * slab, a log lying or standing ...) goes with the block ({@link Cell}). Where two meet, the lower {@code priority} stays.
 */
public enum Role {

    /** The posts of the timber frame (logs standing), the stilts. */
    FRAME(0),
    /** The beams: the course on the top of each storey, the sill and the lintel of a window, the beams under a raised floor. */
    BEAM(0),
    /** The timbers of a panel's pattern (a stud, a brace, the lines of a gable). */
    BRACE(1),
    /** The stone foundation under the walls and its ledge. */
    PLINTH(0),
    /** A stone panel, a chimney's stack and the corners of stone. */
    STONE(1),
    /** The plaster between the timbers, in three colours. */
    INFILL(3),
    INFILL_2(3),
    INFILL_3(3),
    WINDOW(4),
    DOOR(2),
    /** The floors inside and the boards of balconies, porches and landings. */
    FLOOR(2),
    /** Under a jetty, a balcony and the eaves: upside-down stairs. */
    CORBEL(1),
    ROOF(2),
    /** The edges of the roof: its eaves and its verges. */
    EAVE(1),
    /** The ridge and the hips. */
    RIDGE(1),
    /** Where a roof meets a higher wall. */
    FLASHING(2),
    CHIMNEY(0),
    /** The rails of balconies, galleries and stairs. */
    RAIL(3),
    /** The steps of outside stairs, a ladder. */
    STAIR(2),
    /** A finial, a weathervane. */
    FINIAL(1),
    /** The sails of a windmill. */
    SAIL(3),
    /** A bell and the like hung at an anchor. */
    BELL(0);

    private final int priority;

    Role(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }

    /** The role of a name ({@code infill_2}, {@code INFILL-2}), or null. */
    public static Role byName(String name) {
        if (name == null) return null;
        String clean = name.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        for (Role role : values()) if (role.name().equals(clean)) return role;
        return null;
    }

    /** Whether it is part of a roof (the roof's look, stairs or full blocks, is set for these). */
    public boolean roof() {
        return this == ROOF || this == EAVE || this == RIDGE || this == FLASHING;
    }
}
