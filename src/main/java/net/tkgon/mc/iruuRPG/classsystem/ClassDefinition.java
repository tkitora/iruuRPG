package net.tkgon.mc.iruuRPG.classsystem;

import org.bukkit.Material;

import java.util.Map;

public record ClassDefinition(
        String id,
        String name,
        Material icon,
        Map<String, ClassNodeDefinition> nodes
) {

    public ClassDefinition {
        id = id == null ? "" : id;
        name = name == null || name.isBlank() ? id : name;
        icon = icon == null ? Material.BOOK : icon;
        nodes = nodes == null ? Map.of() : Map.copyOf(nodes);
    }

    public ClassNodeDefinition node(String nodeId) {
        return nodes.get(nodeId);
    }
}
