package net.tkgon.mc.iruuRPG.combat;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;

/**
 * Sound roles of the combos: a few vanilla sounds layered and pitched into one effect. The server is Paper 1.20.4 (no breeze, mace
 * or wolf armor sounds yet), so the roles that those sounds would play are made of what exists: the allay for magic, a low sweep
 * with wing beats for wind, struck iron for metal, breaking shields and glass for a crack or a cut. When the server moves to
 * 1.21 the layers of {@link #WIND_BURST} (breeze), {@link #METAL_HEAVY} (mace) and {@link #CRACK} (wolf armor) are where the real
 * ones go.
 */
public enum Sfx {

    /** A bright spell: the allay's gift and hum over a chime. */
    MAGIC(new Layer(Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.55f, 1.5f), new Layer(Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.35f, 1.9f),
            new Layer(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.25f, 1.6f)),
    /** A darker spell: the allay thrown and hurt, pitched down. */
    MAGIC_DARK(new Layer(Sound.ENTITY_ALLAY_ITEM_THROWN, 0.6f, 0.7f), new Layer(Sound.ENTITY_ALLAY_HURT, 0.3f, 0.55f),
            new Layer(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.25f, 0.8f)),
    /** A sweep through the air: a low swing with wing beats. */
    WIND(new Layer(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 0.75f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.7f),
            new Layer(Sound.ITEM_TRIDENT_THROW, 0.2f, 1.1f)),
    /** A gust that bursts: a swoop and a riptide with a thump under it. */
    WIND_BURST(new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.5f, 1.3f), new Layer(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4f, 1.1f),
            new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.12f, 1.9f)),
    /** A blow of metal: iron struck, a shield taking it. */
    METAL(new Layer(Sound.ITEM_SHIELD_BLOCK, 0.5f, 0.85f), new Layer(Sound.BLOCK_ANVIL_LAND, 0.22f, 1.6f),
            new Layer(Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.35f, 0.8f)),
    /** A smash: the anvil and the ground behind the metal. */
    METAL_HEAVY(new Layer(Sound.BLOCK_ANVIL_PLACE, 0.45f, 0.7f), new Layer(Sound.ITEM_SHIELD_BLOCK, 0.6f, 0.65f),
            new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.4f), new Layer(Sound.BLOCK_NETHERITE_BLOCK_BREAK, 0.5f, 0.8f)),
    /** A cut and a crack: a shield and an item breaking, glass splitting. */
    CRACK(new Layer(Sound.ITEM_SHIELD_BREAK, 0.35f, 1.7f), new Layer(Sound.ENTITY_ITEM_BREAK, 0.45f, 1.5f),
            new Layer(Sound.BLOCK_GLASS_BREAK, 0.25f, 1.8f)),
    /** A scythe reaping: a sweep, a phantom's wing and a skeleton's rattle. */
    REAP(new Layer(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.55f, 0.55f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.45f, 0.8f),
            new Layer(Sound.ENTITY_SKELETON_AMBIENT, 0.22f, 1.0f)),
    /** A heavy reap: a phantom's swoop, a skeleton hurt and a low sweep. */
    REAP_HEAVY(new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.6f, 0.65f), new Layer(Sound.ENTITY_SKELETON_HURT, 0.4f, 0.55f),
            new Layer(Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 0.5f)),
    /** Souls let go: a phantom's cry and bones, thin and cold. */
    SOUL(new Layer(Sound.ENTITY_PHANTOM_AMBIENT, 0.4f, 1.1f), new Layer(Sound.ENTITY_SKELETON_AMBIENT, 0.3f, 0.8f),
            new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.5f, 0.9f)),
    // the mage's ranged combo: heavier magic, the power heard as air pushed aside and energy gathering
    /** A bolt let go: an evoker's spell, a thrown trident's rush of air and a wing beat, over a resonating crystal. */
    MAGE_CAST(new Layer(Sound.ENTITY_EVOKER_CAST_SPELL, 0.45f, 1.35f), new Layer(Sound.ITEM_TRIDENT_THROW, 0.4f, 0.75f),
            new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.3f), new Layer(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f, 1.5f)),
    /** A bolt that lands: crystal breaking, a dull thump and the spell's echo. */
    MAGE_IMPACT(new Layer(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.55f, 1.05f), new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.18f, 1.75f),
            new Layer(Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.25f, 0.9f)),
    /** Air torn by a big spell: the dragon's wing, a riptide and a swoop. */
    MAGE_WIND(new Layer(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.4f, 1.2f), new Layer(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35f, 1.3f),
            new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.3f, 1.5f)),
    /** Energy gathering: a beacon, a respawn anchor charging, a riptide and the warden drawing breath. */
    MAGE_SURGE(new Layer(Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 1.3f), new Layer(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.55f, 1.15f),
            new Layer(Sound.ITEM_TRIDENT_RIPTIDE_3, 0.35f, 1.2f), new Layer(Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.2f, 1.6f)),
    /** Magic out of the ground: dripstone falling, crystal and deepslate breaking, the evoker's fangs. */
    MAGE_GROUND(new Layer(Sound.BLOCK_POINTED_DRIPSTONE_LAND, 0.6f, 0.7f), new Layer(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.55f, 0.7f),
            new Layer(Sound.BLOCK_DEEPSLATE_BREAK, 0.55f, 0.6f), new Layer(Sound.ENTITY_EVOKER_FANGS_ATTACK, 0.45f, 0.9f)),
    /** A great burst: an explosion, the warden's sonic boom, the dragon's wing and a respawn anchor spent. */
    MAGE_BLAST(new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 0.8f), new Layer(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.35f, 1.3f),
            new Layer(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.55f, 0.7f), new Layer(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 0.9f));

    /** One vanilla sound of a role: its own volume and pitch, scaled by what the caller asks for. */
    public record Layer(Sound sound, float volume, float pitch) {
    }

    private final Layer[] layers;

    Sfx(Layer... layers) {
        this.layers = layers;
    }

    /** Plays the role at {@code at}: every layer at its own volume and pitch times {@code volume} and {@code pitch}. */
    public void play(Location at, float volume, float pitch) {
        World world = at.getWorld();
        if (world == null) return;
        for (Layer layer : layers) {
            world.playSound(at, layer.sound(), SoundCategory.PLAYERS, layer.volume() * volume, Math.max(0.5f, Math.min(2.0f, layer.pitch() * pitch)));
        }
    }
}
