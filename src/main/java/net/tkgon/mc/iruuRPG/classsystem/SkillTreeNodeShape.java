package net.tkgon.mc.iruuRPG.classsystem;

public record SkillTreeNodeShape(
        String id,
        int order,
        String parentId
) {

    public SkillTreeNodeShape {
        id = id == null ? "" : id;
        parentId = parentId == null || parentId.isBlank() || parentId.equalsIgnoreCase("null") ? null : parentId;
    }
}
