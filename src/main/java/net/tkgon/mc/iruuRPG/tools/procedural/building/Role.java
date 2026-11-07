package net.tkgon.mc.iruuRPG.tools.procedural.building;

/**
 * (t295) The role of a block of a building: what it is for, not what it is made of. The building is made of roles only ({@link SemanticVoxelGrid}); when it is
 * put, every role is one block chosen for it (one colour a role: the gradients and the fine decoration are done by hand). {@code tag} is the name of the
 * role in the design ({@code frame_post} and so on); the lower the {@code priority}, the stronger the role where two meet.
 */
public enum Role {

    PLINTH("plinth", 1),
    POST("frame_post", 0),
    BEAM("frame_beam", 1),
    INFILL("infill", 3),
    WINDOW("window", 4),
    CORBEL("corbel", 2),
    FLOOR("floor", 5),
    /** The roof, and its stripes: {@code roof_band_0}, {@code _1}, {@code _2} (by height or by face, see {@link Roofs}). */
    ROOF("roof_band_0", 6),
    ROOF_2("roof_band_1", 6),
    ROOF_3("roof_band_2", 6),
    /** The eaves' edge: a row of upside-down stairs and a log lying along it under them. */
    EAVE("eave", 2),
    /** The verge: the stairs along the sloping edge of a gable, one block out. */
    VERGE("verge", 2),
    RIDGE("ridge", 2),
    HIP("hip", 5),
    CHIMNEY("chimney", 0),
    /** Rails (fences and trapdoors) of balconies, and the lattice of a windmill's sails. */
    RAIL("rail", 3),
    /** The ornament on the top of a spire (a wall and a lightning rod). */
    FINIAL("finial", 0);

    private final String tag;
    private final int priority;

    Role(String tag, int priority) {
        this.tag = tag;
        this.priority = priority;
    }

    public String tag() {
        return tag;
    }

    public int priority() {
        return priority;
    }

    /** Whether the role is one of the roof's stripes. */
    public boolean roof() {
        return this == ROOF || this == ROOF_2 || this == ROOF_3;
    }

    /** The stripe {@code band} of the roof (0, 1, 2 and round again). */
    public static Role band(int band) {
        return switch (Math.floorMod(band, 3)) {
            case 1 -> ROOF_2;
            case 2 -> ROOF_3;
            default -> ROOF;
        };
    }
}
