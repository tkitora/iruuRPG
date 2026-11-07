package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (t305) The presets of a kind the tool no longer has (the building of t295 left on the server) are not read nor listed, and stay in the folder. */
class TreeStoreTest {

    @Test
    void presetsOfAKindTakenOutAreLeftAlone(@TempDir Path data) throws Exception {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(data.toFile());
        Path trees = Files.createDirectories(data.resolve("trees"));
        Files.writeString(trees.resolve("building_castle.yml"), "settings:\n  kind: building\n  seed: 3\n");
        Files.writeString(trees.resolve("oak.yml"), "settings:\n  kind: tree\n  seed: 5\n");
        TreeStore store = new TreeStore(plugin);

        assertNull(store.load("building_castle"));
        assertNull(store.info("building_castle"));
        assertEquals(List.of("oak"), store.list());
        TreeSettings oak = store.load("oak");
        assertNotNull(oak);
        assertEquals(ProcKind.TREE, oak.kind());
        assertTrue(new File(trees.toFile(), "building_castle.yml").isFile());
        // (it can still be put in the trash by name)
        assertTrue(store.delete("building_castle"));
    }
}
