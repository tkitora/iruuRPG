package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.craftbukkit.v1_20_R3.CraftRegistry;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemFactory;
import org.bukkit.craftbukkit.v1_20_R3.util.CraftMagicNumbers;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The item patch on real items (the server's own item and data code from the dev bundle): an item whose yaml changed is
 * built again with the new name and text, and keeps everything that is its own.
 */
@Tag("server")
class ItemPatcherTest {

    private static File data;
    private static TestPlugin plugin;

    static final class TestPlugin extends JavaPlugin {
        @SuppressWarnings("deprecation")
        TestPlugin(File folder) {
            super(new JavaPluginLoader(Bukkit.getServer()), new PluginDescriptionFile("iruuRPG", "1.0", "net.tkgon.mc.iruuRPG.Test"),
                    folder, new File(folder, "iruuRPG.jar"));
        }
    }

    private RpgItemRegistry registry;
    private RpgItemFactory factory;
    private ItemIdentifier identifier;
    private ItemPatcher patcher;

    @BeforeAll
    static void server() throws IOException {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess access = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        // a server that answers only what the item code asks (answering by name: stubbing with when() would load Registry before there is a server)
        Logger logger = Logger.getLogger("test");
        Server server = Mockito.mock(Server.class, call -> switch (call.getMethod().getName()) {
            case "getItemFactory" -> CraftItemFactory.instance();
            case "getUnsafe" -> CraftMagicNumbers.INSTANCE;
            case "getRegistry" -> registry(call.getArgument(0), access);
            case "getLogger" -> logger;
            case "getName", "getVersion" -> "test";
            case "getBukkitVersion" -> "1.20.4-R0.1-SNAPSHOT";
            default -> Mockito.RETURNS_DEFAULTS.answer(call);
        });
        if (Bukkit.getServer() == null) Bukkit.setServer(server);
        CraftRegistry.setMinecraftRegistry(access);
        data = Files.createTempDirectory("itempatch").toFile();
        plugin = new TestPlugin(data);
    }

    /** The server's registry for the built-in kinds (enchantments, ...); an empty one for the kinds that come from data packs (not used here). */
    private static Object registry(Class<? extends org.bukkit.Keyed> kind, RegistryAccess access) {
        try {
            return CraftRegistry.createRegistry(kind, access);
        } catch (IllegalStateException missing) {
            return Mockito.mock(org.bukkit.Registry.class);
        }
    }

    @BeforeEach
    void items() throws IOException {
        writeItems("剣", "古い説明");
        registry = new RpgItemRegistry(plugin);
        registry.reload();
        identifier = new ItemIdentifier(new ItemKeys(plugin));
        factory = new RpgItemFactory(plugin, identifier, new ItemSkillRegistry(plugin));
        patcher = new ItemPatcher(registry, factory, identifier, () -> plugin.getConfig().getString("rarity.mark", "*"), message -> { });
        patcher.setFingerprints(patcher.computeFingerprints());
    }

    private static void writeItems(String swordName, String description) throws IOException {
        File folder = new File(data, "items");
        folder.mkdirs();
        Files.writeString(new File(folder, "test.yml").toPath(), String.join("\n",
                "items:",
                "  test_sword:",
                "    material: IRON_SWORD",
                "    name: \"" + swordName + "\"",
                "    rarity: uncommon",
                "    required-level: 5",
                "    stats: {weapon-damage: 10}",
                "    description: [\"" + description + "\"]",
                "  test_stone:",
                "    material: STONE",
                "    name: \"石\"",
                ""), StandardCharsets.UTF_8);
    }

    /** The yaml changes and is read again (as the server's reload does). */
    private void changeYaml(String swordName, String description) throws IOException {
        writeItems(swordName, description);
        registry.reload();
        patcher.setFingerprints(patcher.computeFingerprints());
    }

    private static String name(ItemStack item) {
        return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
    }

    private static String lore(ItemStack item) {
        StringBuilder out = new StringBuilder();
        for (Component line : item.getItemMeta().lore()) out.append(PlainTextComponentSerializer.plainText().serialize(line)).append('\n');
        return out.toString();
    }

    private RpgItemDefinition sword() {
        return registry.find("test_sword").orElseThrow();
    }

    @Test
    void anItemMadeNowIsUpToDate() {
        ItemStack sword = factory.create(sword());
        assertNull(patcher.patch(sword), "a new item carries the current fingerprint");
    }

    @Test
    void onlyTheChangedItemIsBuiltAgain() throws IOException {
        ItemStack sword = factory.create(sword());
        ItemStack stone = factory.create(registry.find("test_stone").orElseThrow());
        changeYaml("新しい剣", "新しい説明");
        assertNull(patcher.patch(stone), "the stone's yaml did not change");
        ItemStack patched = patcher.patch(sword);
        assertNotNull(patched);
        assertTrue(name(patched).startsWith("新しい剣"), name(patched));
        assertTrue(lore(patched).contains("新しい説明"), lore(patched));
        assertTrue(!lore(patched).contains("古い説明"), lore(patched));
        assertNull(patcher.patch(patched), "patched once, up to date afterwards");
    }

    @Test
    void aCraftedItemKeepsWhatIsItsOwn() throws IOException {
        StatSet bonus = new StatSet();
        bonus.add(StatType.STRENGTH, 7.0);
        bonus.add(StatType.CRIT_CHANCE, 3.0);
        ElementStatSet elements = new ElementStatSet();
        net.tkgon.mc.iruuRPG.stat.ElementKind.values()[0].add(elements, Element.RED, 50.0);
        Rarity rarity = sword().rarity().up(1);
        ItemStack crafted = factory.createCrafted(sword(), rarity, bonus, elements);
        identifier.setBonusElementStats(crafted, elements);
        identifier.setRarity(crafted, rarity);
        // things another feature may keep on an item: a key of its own, enchantments, more than one
        NamespacedKey owner = new NamespacedKey(plugin, "owner");
        ItemMeta meta = crafted.getItemMeta();
        meta.getPersistentDataContainer().set(owner, PersistentDataType.STRING, "alice");
        meta.addEnchant(Enchantment.DAMAGE_ALL, 3, true);
        crafted.setItemMeta(meta);
        crafted.setAmount(2);

        changeYaml("新しい剣", "新しい説明");
        ItemStack patched = patcher.patch(crafted);
        assertNotNull(patched);

        assertEquals(Map.of(StatType.STRENGTH, 7.0, StatType.CRIT_CHANCE, 3.0), identifier.bonusStats(patched).asMap());
        assertEquals(50.0, net.tkgon.mc.iruuRPG.stat.ElementKind.values()[0].get(identifier.bonusElementStats(patched), Element.RED), 1.0E-9);
        assertEquals(rarity, identifier.rarity(patched).orElseThrow());
        assertEquals("alice", patched.getItemMeta().getPersistentDataContainer().get(owner, PersistentDataType.STRING));
        assertEquals(3, patched.getItemMeta().getEnchantLevel(Enchantment.DAMAGE_ALL));
        assertEquals(2, patched.getAmount());
        assertEquals("test_sword", identifier.itemId(patched).orElseThrow());
        // the new text, with the crafted section still there
        assertTrue(name(patched).startsWith("新しい剣"), name(patched));
        assertTrue(lore(patched).contains("新しい説明"), lore(patched));
        assertTrue(lore(patched).contains("クラフト品"), lore(patched));
        assertTrue(lore(patched).contains("▲"), lore(patched));
    }

    @Test
    void aCraftedItemFromBeforeKeepsTheRarityOfItsName() throws IOException {
        // crafted before rarities were stored: only the name tells it ("剣 [***]")
        StatSet bonus = new StatSet();
        bonus.add(StatType.STRENGTH, 1.0);
        Rarity rarity = Rarity.fromTier(3);
        ItemStack old = factory.createCrafted(sword(), rarity, bonus, new ElementStatSet());
        ItemMeta meta = old.getItemMeta();
        meta.getPersistentDataContainer().remove(identifier.keys().itemRev());
        meta.getPersistentDataContainer().remove(identifier.keys().rarity());
        old.setItemMeta(meta);
        assertTrue(identifier.rarity(old).isEmpty());

        ItemStack patched = patcher.patch(old);
        assertNotNull(patched, "an item without a fingerprint is built again once");
        assertEquals(rarity, identifier.rarity(patched).orElseThrow());
        assertTrue(name(patched).endsWith("]"), name(patched));
        assertEquals(1.0, identifier.bonusStats(patched).asMap().get(StatType.STRENGTH), 1.0E-9);
    }

    @Test
    void anItemWhoseYamlIsGoneIsLeftAlone() throws IOException {
        ItemStack stone = factory.create(registry.find("test_stone").orElseThrow());
        File folder = new File(data, "items");
        Files.writeString(new File(folder, "test.yml").toPath(), "items:\n  test_sword:\n    material: IRON_SWORD\n    name: \"剣\"\n", StandardCharsets.UTF_8);
        registry.reload();
        patcher.setFingerprints(patcher.computeFingerprints());
        assertNull(patcher.patch(stone));
        assertNull(patcher.patch(new ItemStack(Material.DIRT)), "not an RPG item");
    }

    @Test
    void fingerprintsAreStable() {
        Map<String, String> first = patcher.computeFingerprints();
        Map<String, String> second = patcher.computeFingerprints();
        assertEquals(first, second);
        assertTrue(first.keySet().containsAll(Set.of("test_sword", "test_stone")), first.keySet().toString());
        assertTrue(List.copyOf(first.values()).stream().allMatch(value -> value.length() == 16));
    }

    @Test
    void theVersionGoesUpOnlyWhenAnItemChanged() throws IOException {
        new File(data, "item-patch.yml").delete();
        ItemPatchService service = new ItemPatchService(plugin, patcher);
        List<ItemStack> market = new java.util.ArrayList<>(List.of(factory.create(sword())));
        service.addKeeper(patch -> {
            int changed = 0;
            for (int index = 0; index < market.size(); index++) {
                ItemStack patched = patch.apply(market.get(index));
                if (patched == null) continue;
                market.set(index, patched);
                changed++;
            }
            return changed;
        });

        assertTrue(service.refresh().containsAll(Set.of("test_sword", "test_stone")), "the first time, every item counts as changed");
        assertEquals(1, service.version());
        assertTrue(service.refresh().isEmpty(), "nothing changed");
        assertEquals(1, service.version());

        writeItems("新しい剣", "新しい説明");
        registry.reload();
        assertEquals(Set.of("test_sword"), service.refresh());
        assertEquals(2, service.version());
        assertTrue(name(market.get(0)).startsWith("新しい剣"), "the market's item was patched at once");

        // the version and fingerprints are kept across a restart
        ItemPatchService restarted = new ItemPatchService(plugin, patcher);
        assertTrue(restarted.refresh().isEmpty());
        assertEquals(2, restarted.version());
    }

    @Test
    void anItemFromBeforeThatIsAlreadyCurrentIsOnlyStamped() {
        ItemStack old = factory.create(sword());
        ItemMeta meta = old.getItemMeta();
        meta.getPersistentDataContainer().remove(identifier.keys().itemRev());
        old.setItemMeta(meta);
        old.setAmount(3);

        patcher.takeCounts();
        ItemStack patched = patcher.patch(old);
        ItemPatcher.Counts counts = patcher.takeCounts();
        assertNotNull(patched, "the fingerprint is added");
        assertEquals(1, counts.stamped);
        assertEquals(0, counts.rebuilt);
        // nothing else changed: the same as the old item with the stamp, and it stacks with an item made now
        ItemStack expected = old.clone();
        ItemMeta stamped = expected.getItemMeta();
        stamped.getPersistentDataContainer().set(identifier.keys().itemRev(), PersistentDataType.STRING, patcher.fingerprints().get("test_sword"));
        expected.setItemMeta(stamped);
        assertEquals(expected, patched);
        assertTrue(patched.isSimilar(factory.create(sword())), "stacks with a new one");
    }

    @Test
    void aChangedItemCountsAsRebuilt() throws IOException {
        ItemStack sword = factory.create(sword());
        changeYaml("新しい剣", "新しい説明");
        patcher.takeCounts();
        assertNotNull(patcher.patch(sword));
        ItemPatcher.Counts counts = patcher.takeCounts();
        assertEquals(1, counts.rebuilt);
        assertEquals(0, counts.stamped);
    }

    @Test
    void aFailureLeavesTheItemAsItWas() {
        // a crafted item from before (no rarity stored) whose name cannot be read, with the mark setting broken
        List<String> logged = new java.util.ArrayList<>();
        ItemPatcher broken = new ItemPatcher(registry, factory, identifier, () -> {
            throw new IllegalStateException("broken setting");
        }, logged::add);
        broken.setFingerprints(broken.computeFingerprints());
        ItemStack old = factory.createCrafted(sword(), Rarity.fromTier(2), new StatSet(), new ElementStatSet());
        ItemMeta meta = old.getItemMeta();
        meta.getPersistentDataContainer().remove(identifier.keys().itemRev());
        meta.getPersistentDataContainer().remove(identifier.keys().rarity());
        old.setItemMeta(meta);
        ItemStack before = old.clone();

        assertNull(broken.patch(old), "left as it was");
        assertEquals(before, old);
        assertEquals(1, broken.takeCounts().failed);
        assertTrue(logged.stream().anyMatch(line -> line.contains("test_sword")), logged.toString());
        patcher.setFingerprints(patcher.computeFingerprints()); // the factory stamps for the shared patcher again
    }

    @Test
    void aRarityThatCannotBeReadIsCounted() {
        StatSet bonus = new StatSet();
        bonus.add(StatType.STRENGTH, 1.0);
        bonus.add(StatType.MAGIC, 1.0);
        ItemStack old = factory.createCrafted(sword(), sword().rarity().up(1), bonus, new ElementStatSet());
        ItemMeta meta = old.getItemMeta();
        meta.getPersistentDataContainer().remove(identifier.keys().itemRev());
        meta.getPersistentDataContainer().remove(identifier.keys().rarity());
        meta.displayName(Component.text("剣 [??]")); // marks of another setting
        old.setItemMeta(meta);

        patcher.takeCounts();
        ItemStack patched = patcher.patch(old);
        assertNotNull(patched);
        assertEquals(1, patcher.takeCounts().rarityUnread);
        // worked out from the bonuses as the craft does: two bonuses, one tier up
        assertEquals(sword().rarity().up(1), identifier.rarity(patched).orElseThrow());
    }

    @Test
    void whenOffNothingIsPatchedOrStamped() throws IOException {
        ItemStack sword = factory.create(sword());
        changeYaml("新しい剣", "新しい説明");
        patcher.setEnabled(false);
        try {
            assertNull(patcher.patch(sword));
            ItemStack made = factory.create(sword());
            assertNull(made.getItemMeta().getPersistentDataContainer().get(identifier.keys().itemRev(), PersistentDataType.STRING),
                    "new items carry no fingerprint while off (they stack with the old ones)");
        } finally {
            patcher.setEnabled(true);
        }
        assertNotNull(patcher.patch(sword));
    }
}
