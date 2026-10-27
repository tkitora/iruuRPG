package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The item patch on the server's own data (a copy of plugins/iruuRPG/ or of the yml repository):
 * {@code ./gradlew itemPatchTest -PitemPatchData=<folder with items/, iskill/, skills/>}. Skipped when no folder is given.
 */
@Tag("server")
class ItemPatchRealDataTest {

    @Test
    void theServersItemsPassTheWholePatch() throws IOException {
        String source = System.getProperty("itemPatch.data", "");
        assumeTrue(!source.isBlank() && new File(source, "items").isDirectory(), "no -PitemPatchData folder given");
        TestServer.start();

        // a copy, so nothing the registries write (defaults, ...) touches the data
        Path folder = Files.createTempDirectory("itempatch-real");
        copy(Path.of(source), folder);
        JavaPlugin plugin = new TestServer.TestPlugin(folder.toFile());
        ItemSkillRegistry itemSkills = new ItemSkillRegistry(plugin);
        itemSkills.reload();
        RpgItemRegistry registry = new RpgItemRegistry(plugin);
        registry.reload();
        SkillRegistry skills = new SkillRegistry(plugin);
        skills.reload();
        registry.setGeneralSkills(skills);
        ItemIdentifier identifier = new ItemIdentifier(new ItemKeys(plugin));
        RpgItemFactory factory = new RpgItemFactory(plugin, identifier, itemSkills);
        factory.setGeneralSkills(skills);
        List<String> logged = new ArrayList<>();
        ItemPatcher patcher = new ItemPatcher(registry, factory, identifier, () -> plugin.getConfig().getString("rarity.mark", "*"), logged::add);

        Map<String, String> fingerprints = patcher.computeFingerprints();
        System.out.println("items: " + registry.all().size() + " definitions, " + fingerprints.size() + " fingerprints; " + logged);
        assertTrue(fingerprints.size() > 0);
        // one line that must be the same on every run (fingerprints that change between restarts would patch for nothing)
        System.out.println("items: fingerprints digest " + Integer.toHexString(new java.util.TreeMap<>(fingerprints).toString().hashCode()));

        ItemPatchService service = new ItemPatchService(plugin, patcher);
        assertTrue(!service.refresh().isEmpty(), "the first time, every item counts as changed");
        assertTrue(patcher.enabled());

        // every item: made now = up to date; made before the fingerprints = only stamped (the first deploy touches nothing else)
        patcher.takeCounts();
        int crafted = 0;
        for (RpgItemDefinition definition : registry.all().values()) {
            if (!fingerprints.containsKey(definition.id())) continue;
            ItemStack made = factory.create(definition);
            assertNull(patcher.patch(made), definition.id());
            assertTrue(patcher.patch(withoutStamp(made, identifier)) != null, definition.id());

            StatSet bonus = new StatSet();
            bonus.add(StatType.STRENGTH, 3.0);
            ItemStack craft = factory.createCrafted(definition, definition.rarity(), bonus, new ElementStatSet());
            identifier.setRarity(craft, definition.rarity());
            assertNull(patcher.patch(craft), definition.id());
            assertTrue(patcher.patch(withoutStamp(craft, identifier)) != null, definition.id());
            crafted++;
        }
        ItemPatcher.Counts counts = patcher.takeCounts();
        System.out.println("items: " + counts);
        assertEquals(0, counts.rebuilt, "nothing is rebuilt when the yaml did not change");
        assertEquals(0, counts.failed);
        assertEquals(fingerprints.size() + crafted, counts.stamped);
    }

    private static ItemStack withoutStamp(ItemStack item, ItemIdentifier identifier) {
        ItemStack copy = item.clone();
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().remove(identifier.keys().itemRev());
        copy.setItemMeta(meta);
        return copy;
    }

    private static void copy(Path from, Path to) throws IOException {
        for (String name : List.of("items", "iskill", "skills")) {
            Path dir = from.resolve(name);
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.walk(dir)) {
                for (Path file : (Iterable<Path>) files::iterator) {
                    Path target = to.resolve(from.relativize(file).toString());
                    if (Files.isDirectory(file)) Files.createDirectories(target);
                    else Files.copy(file, target);
                }
            }
        }
    }
}
