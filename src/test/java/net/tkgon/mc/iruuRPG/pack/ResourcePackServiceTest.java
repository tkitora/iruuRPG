package net.tkgon.mc.iruuRPG.pack;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackServiceTest {

    private static List<String> names(String yaml) throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString(yaml);
        return ResourcePackService.resolve(config.getConfigurationSection("resourcepack.packs")).stream().map(ResourcePackService.Pack::name).toList();
    }

    @Test
    void thePackOfTheItemsIsOfferedEvenWhenAnOlderConfigDoesNotListIt() throws Exception {
        // the server's config.yml as it was before the items' pack: only server and models
        List<String> packs = names("resourcepack:\n  packs:\n    server:\n      file: server.zip\n    models:\n      file: iruumodel.zip\n");
        assertEquals(List.of("server", "models", "items", "marks", "skills"), packs);
    }

    @Test
    void aConfigWithoutAPacksSectionStillOffersTheBuiltInPacks() throws Exception {
        assertEquals(List.of("server", "models", "items", "marks", "skills"), names("resourcepack:\n  enabled: true\n"));
    }

    @Test
    void anEntryOfTheConfigStillWins() throws Exception {
        List<String> off = names("resourcepack:\n  packs:\n    items:\n      enabled: false\n");
        assertTrue(off.contains("server") && off.contains("models"));
        assertEquals(4, off.size(), "a pack switched off in the config is not offered");

        YamlConfiguration config = new YamlConfiguration();
        config.loadFromString("resourcepack:\n  packs:\n    items:\n      file: other.zip\n      required: true\n");
        ResourcePackService.Pack items = ResourcePackService.resolve(config.getConfigurationSection("resourcepack.packs")).stream()
                .filter(pack -> pack.name().equals("items")).findFirst().orElseThrow();
        assertEquals("other.zip", items.file());
        assertTrue(items.required());
    }

    @Test
    void aFileNameThatLeavesTheFolderIsSkipped() throws Exception {
        List<String> packs = names("resourcepack:\n  packs:\n    evil:\n      file: ../x.zip\n");
        assertEquals(List.of("server", "models", "items", "marks", "skills"), packs);
    }
}
