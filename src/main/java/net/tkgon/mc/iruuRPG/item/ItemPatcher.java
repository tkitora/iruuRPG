package net.tkgon.mc.iruuRPG.item;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementKind;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Brings items that were made before their yaml changed up to date. Each item definition has a fingerprint (a hash of
 * the plain item the factory builds from it: name, lore, material, looks, the skill text...), and every item carries
 * the fingerprint it was built from ({@code item_rev}). An item whose fingerprint is not the current one is built
 * again from the current definition, and everything that is its own is carried over: all of its persistent data
 * (the crafted bonus stats and elements, and any other key), its rarity if crafted, its amount and the enchantments
 * that are not the definition's glint.
 */
public final class ItemPatcher {

    private final RpgItemRegistry registry;
    private final RpgItemFactory factory;
    private final ItemIdentifier identifier;
    private final Supplier<String> rarityMark;
    private final java.util.function.Consumer<String> log;
    private Map<String, String> fingerprints = Map.of();
    private boolean enabled = true;

    public ItemPatcher(RpgItemRegistry registry, RpgItemFactory factory, ItemIdentifier identifier, Supplier<String> rarityMark,
                       java.util.function.Consumer<String> log) {
        this.registry = registry;
        this.factory = factory;
        this.identifier = identifier;
        this.rarityMark = rarityMark;
        this.log = log == null ? message -> { } : log;
    }

    /** The fingerprint of every item definition as it is now (call after the items, skills and config are loaded). */
    public Map<String, String> computeFingerprints() {
        Map<String, String> out = new HashMap<>();
        for (RpgItemDefinition definition : registry.all().values()) {
            out.put(definition.id(), fingerprint(factory.createUnstamped(definition)));
        }
        return out;
    }

    /** The fingerprints items are compared with. */
    public void setFingerprints(Map<String, String> fingerprints) {
        this.fingerprints = Map.copyOf(fingerprints);
        stampNewItems();
    }

    /** Off (config item-patch.enabled: false): nothing is patched or stamped, items stay as they are. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        stampNewItems();
    }

    public boolean enabled() {
        return enabled;
    }

    private void stampNewItems() {
        // the items made from now on carry the fingerprint, so they stack with the checked ones and are never patched for nothing
        Map<String, String> current = this.fingerprints;
        factory.setRevision(enabled ? current::get : null);
    }

    public Map<String, String> fingerprints() {
        return fingerprints;
    }

    static String fingerprint(ItemStack plain) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(plain.serializeAsBytes());
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            // never on a Java runtime; fall back to something that still changes with the item
            return Integer.toHexString(java.util.Arrays.hashCode(plain.serializeAsBytes()));
        }
    }

    /** What the patch did since the counts were last taken (for the log). */
    public static final class Counts {
        /** Built again: the yaml changed what the item shows. */
        public int rebuilt;
        /** Only stamped with the fingerprint: the item was already what the yaml makes (an item from before the fingerprints). */
        public int stamped;
        /** Building it again failed: the item was left as it was. */
        public int failed;
        /** Crafted items from before the rarity was stored whose name marks could not be read (the rarity was worked out from the bonuses). */
        public int rarityUnread;

        public void add(Counts other) {
            rebuilt += other.rebuilt;
            stamped += other.stamped;
            failed += other.failed;
            rarityUnread += other.rarityUnread;
        }

        public boolean any() {
            return rebuilt + stamped + failed + rarityUnread > 0;
        }

        @Override
        public String toString() {
            return "rebuilt " + rebuilt + ", stamped only " + stamped + ", failed " + failed + ", crafted rarity not readable " + rarityUnread;
        }
    }

    private Counts counts = new Counts();

    /** The counts since the last call, and starts new ones. */
    public Counts takeCounts() {
        Counts out = counts;
        counts = new Counts();
        return out;
    }

    /**
     * The item brought up to date, or null when it is not an RPG item, its definition is gone, or it is up to date.
     * When the item is already what the current yaml makes (only the fingerprint is missing), the answer is the same item
     * with the fingerprint added and nothing else touched. If building it fails, the item is left as it is (null).
     */
    public ItemStack patch(ItemStack item) {
        if (!enabled || item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        String id = identifier.itemId(item).orElse(null);
        if (id == null) return null;
        try {
            return patch(item, id);
        } catch (RuntimeException e) {
            counts.failed++;
            log.accept("items: could not bring an item '" + id + "' up to date, left as it was: " + e);
            return null;
        }
    }

    private ItemStack patch(ItemStack item, String id) {
        String want = fingerprints.get(id);
        RpgItemDefinition definition = registry.find(id).orElse(null);
        if (want == null || definition == null) return null; // an item whose yaml is gone is left as it is

        ItemMeta old = item.getItemMeta();
        PersistentDataContainer oldData = old.getPersistentDataContainer();
        if (want.equals(oldData.get(identifier.keys().itemRev(), PersistentDataType.STRING))) return null;

        boolean crafted = oldData.has(identifier.keys().bonusStats(), PersistentDataType.STRING);
        Rarity rarity = null;
        ItemStack fresh;
        if (crafted) {
            StatSet bonus = identifier.bonusStats(item);
            ElementStatSet elements = identifier.bonusElementStats(item);
            rarity = craftedRarity(item, old, definition, bonus, elements);
            fresh = factory.createCrafted(definition, rarity, bonus, elements);
        } else {
            fresh = factory.create(definition);
        }

        ItemMeta meta = fresh.getItemMeta();
        if (meta == null) return null;
        // everything the item keeps in its data stays (crafted stats, and any key added later); the factory's keys are the same ones
        oldData.copyTo(meta.getPersistentDataContainer(), true);
        // enchantments put on the item (LUCK is the definition's glint, which the factory decides)
        for (Map.Entry<Enchantment, Integer> entry : old.getEnchants().entrySet()) {
            if (entry.getKey().equals(Enchantment.LUCK) || meta.hasEnchant(entry.getKey())) continue;
            meta.addEnchant(entry.getKey(), entry.getValue(), true);
        }
        stamp(meta, want, rarity);
        fresh.setItemMeta(meta);
        fresh.setAmount(item.getAmount());

        // the same item with only the stamp: if that is what the rebuild gives, it is all that changes
        ItemStack stamped = item.clone();
        ItemMeta stampedMeta = stamped.getItemMeta();
        stamp(stampedMeta, want, rarity);
        stamped.setItemMeta(stampedMeta);
        if (stamped.equals(fresh)) {
            counts.stamped++;
            return stamped;
        }
        counts.rebuilt++;
        return fresh;
    }

    private void stamp(ItemMeta meta, String want, Rarity rarity) {
        meta.getPersistentDataContainer().set(identifier.keys().itemRev(), PersistentDataType.STRING, want);
        if (rarity != null) meta.getPersistentDataContainer().set(identifier.keys().rarity(), PersistentDataType.INTEGER, rarity.tier());
    }

    /** Patches the array in place; answers how many stacks changed. */
    public int patchAll(ItemStack[] contents) {
        if (contents == null) return 0;
        int changed = 0;
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack patched = patch(contents[slot]);
            if (patched == null) continue;
            contents[slot] = patched;
            changed++;
        }
        return changed;
    }

    /**
     * A crafted item's rarity: the one stored on it; for an item crafted before rarities were stored, the marks of its
     * name ("[**]"); if those cannot be read, the craft rule (one tier above the base for every two bonuses).
     */
    Rarity craftedRarity(ItemStack item, ItemMeta meta, RpgItemDefinition definition, StatSet bonus, ElementStatSet elements) {
        Rarity stored = identifier.rarity(item).orElse(null);
        if (stored != null) return stored;
        Integer marks = meta.hasDisplayName() ? rarityMarks(PlainTextComponentSerializer.plainText().serialize(meta.displayName()), rarityMark.get()) : null;
        if (marks != null) return Rarity.fromTier(marks);
        counts.rarityUnread++;
        log.accept("items: the rarity of a crafted '" + definition.id() + "' could not be read from its name; worked out from its bonuses");
        return definition.rarity().up(bonusCount(bonus, elements) / 2);
    }

    /** The number of marks in "name [***]", 0 when the name has none, null when they cannot be told. */
    static Integer rarityMarks(String name, String mark) {
        if (name == null) return null;
        String trimmed = name.strip();
        if (mark == null || mark.isEmpty()) return null;
        if (!trimmed.endsWith("]")) return 0;
        int open = trimmed.lastIndexOf('[');
        if (open < 0) return 0;
        String inside = trimmed.substring(open + 1, trimmed.length() - 1);
        if (inside.isEmpty() || !inside.replace(mark, "").isEmpty()) return null;
        return inside.length() / mark.length();
    }

    private static int bonusCount(StatSet bonus, ElementStatSet elements) {
        int count = bonus.asMap().size();
        for (ElementKind kind : ElementKind.values()) {
            for (Element element : Element.values()) {
                if (Math.abs(kind.get(elements, element)) >= 1.0E-9) count++;
            }
        }
        return count;
    }
}
