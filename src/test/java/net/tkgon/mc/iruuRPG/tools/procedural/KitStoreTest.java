package net.tkgon.mc.iruuRPG.tools.procedural;

import net.tkgon.mc.iruuRPG.tools.procedural.plot.Kit;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** (t313) The kit's parts written into the folder once, read back through the yaml as they were, changed by hand, not brought back when deleted. */
class KitStoreTest {

    @Test
    void theKitGoesThroughItsFolder(@TempDir Path data) throws Exception {
        JavaPlugin plugin = Mockito.mock(JavaPlugin.class);
        Mockito.when(plugin.getDataFolder()).thenReturn(data.toFile());
        Mockito.when(plugin.getLogger()).thenReturn(Logger.getLogger("test"));
        KitStore store = new KitStore(plugin);
        store.start();
        File folder = data.resolve("buildkit").toFile();
        int parts = Kit.builtin().all().size();
        assertEquals(parts, folder.listFiles((dir, name) -> name.endsWith(".yml")).length);
        // (each part read back as it was written)
        for (Kit.Part part : Kit.builtin().all()) assertEquals(part.toMap(), PlotShape.kit().get(part.id).toMap(), part.id);
        // a part changed in its file is read as changed
        Path chimney = folder.toPath().resolve("chimney.yml");
        Files.writeString(chimney, Files.readString(chimney).replace("above: 2", "above: 4"));
        store.reload();
        assertEquals(4.0, PlotShape.kit().get("chimney").number("above", 0));
        // a deleted part goes to the trash, its file not written again at the next start
        assertTrue(store.delete("chimney"));
        assertFalse(chimney.toFile().exists());
        store.start();
        assertFalse(chimney.toFile().exists());
        assertNotNull(PlotShape.kit().get("chimney"), "the kit's own part stays as it is in the code");
        // a broken file is left out, the rest read
        Files.writeString(folder.toPath().resolve("broken.yml"), "id: [unclosed");
        assertTrue(store.reload() >= parts - 1);
        PlotShape.useKit(Kit.builtin());
    }
}
