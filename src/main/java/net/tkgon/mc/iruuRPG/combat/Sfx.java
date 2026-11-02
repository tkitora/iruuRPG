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
    /** A cut through the air: a sharp whoosh, a fast wing beat and a glint of steel (t166: no vanilla sweep, which sounded blunt). */
    WIND(new Layer(Sound.ITEM_TRIDENT_THROW, 0.45f, 1.55f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.8f),
            new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.18f, 1.9f)),
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
    REAP(new Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 0.95f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.45f, 0.8f),
            new Layer(Sound.ENTITY_SKELETON_AMBIENT, 0.22f, 1.0f)),
    /** A heavy reap: a phantom's swoop, a skeleton hurt and a low sweep. */
    REAP_HEAVY(new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.6f, 0.65f), new Layer(Sound.ENTITY_SKELETON_HURT, 0.4f, 0.55f),
            new Layer(Sound.ITEM_TRIDENT_THROW, 0.45f, 0.8f)),
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
            new Layer(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.55f, 0.7f), new Layer(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.5f, 0.9f)),
    // the assassin's melee skills of t161: thin steel and shadows (not turned into the wind of the assassin's combo: they are their own)
    /** A thin, quick cut: a high sweep, a chain's rattle and a blade's ring. */
    SHADOW_SLICE(new Layer(Sound.ITEM_TRIDENT_THROW, 0.45f, 1.75f), new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.45f, 1.8f),
            new Layer(Sound.BLOCK_CHAIN_HIT, 0.3f, 1.9f)),
    /** Two blades crossing: a critical hit, a trident in the ground and a lower sweep. */
    SHADOW_CROSS(new Layer(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.3f), new Layer(Sound.ITEM_TRIDENT_HIT_GROUND, 0.45f, 1.6f),
            new Layer(Sound.ITEM_TRIDENT_THROW, 0.4f, 1.3f)),
    /** A rush through the shadows: the illusioner's mirror, a riptide and a swoop. */
    SHADOW_DASH(new Layer(Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.6f, 1.3f), new Layer(Sound.ITEM_TRIDENT_RIPTIDE_2, 0.35f, 1.5f),
            new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.3f, 1.6f)),
    /** Gone from sight: the illusioner's mirror being made, a far teleport and a beacon going out. */
    SHADOW_VANISH(new Layer(Sound.ENTITY_ILLUSIONER_PREPARE_MIRROR, 0.7f, 1.0f), new Layer(Sound.ENTITY_ENDERMAN_TELEPORT, 0.35f, 0.6f),
            new Layer(Sound.BLOCK_BEACON_DEACTIVATE, 0.3f, 1.8f)),
    /** A clone stepping out of the assassin: the mirror moving and a soul let go. */
    SHADOW_CLONE(new Layer(Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.5f, 0.9f), new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 1.2f)),
    /** One cut of the storm of blades: light and high. */
    SHADOW_FLURRY(new Layer(Sound.ITEM_TRIDENT_THROW, 0.25f, 1.95f), new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.2f, 2.0f)),
    /** A leap back: a wing beat and the mirror. */
    SHADOW_STEP(new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.5f, 1.6f), new Layer(Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 0.35f, 1.6f)),
    // the blades of every class (t166): sharp, the air cut, and a hit of its own
    /** A blade swung: a sharp whoosh, a fast wing beat, a faint ring of steel (the normal attack of a melee weapon). */
    BLADE(new Layer(Sound.ITEM_TRIDENT_THROW, 0.45f, 1.7f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.25f, 2.0f),
            new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.15f, 2.0f)),
    /** A heavy blade (the warrior's): a lower whoosh, a big wing and iron. */
    BLADE_HEAVY(new Layer(Sound.ITEM_TRIDENT_THROW, 0.55f, 1.25f), new Layer(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3f, 1.6f),
            new Layer(Sound.ITEM_ARMOR_EQUIP_IRON, 0.35f, 1.3f)),
    /** A blade that cuts into an enemy: a stab, a bright crack and a little metal. */
    HIT_CUT(new Layer(Sound.ITEM_TRIDENT_HIT, 0.5f, 1.45f), new Layer(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.25f, 1.6f),
            new Layer(Sound.BLOCK_CHAIN_BREAK, 0.2f, 1.7f)),
    /** A shot that pierces an enemy: an arrow's hit, a stab and a crystal's tick. */
    HIT_PIERCE(new Layer(Sound.ENTITY_ARROW_HIT, 0.5f, 1.3f), new Layer(Sound.ITEM_TRIDENT_HIT, 0.25f, 1.8f),
            new Layer(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.2f, 1.5f)),
    /** What a critical hit adds: a heavy crit, a deep stab and a sparkle. */
    HIT_CRIT(new Layer(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.6f, 1.1f), new Layer(Sound.ITEM_TRIDENT_HIT, 0.4f, 1.0f),
            new Layer(Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.3f, 1.6f)),
    // the reaper (the mage with a scythe, t167): a long, low cut of the air, souls breathing out, and the sculk's dark clicks
    /** A scythe swung: a low whoosh, a slow wing beat and a soul let out. */
    REAPER_SWING(new Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 0.85f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.35f, 0.7f),
            new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.55f, 0.8f)),
    /** A heavy reap: a phantom's swoop, a deeper whoosh and the vex's cry. */
    REAPER_HEAVY(new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.55f, 0.6f), new Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 0.65f),
            new Layer(Sound.ENTITY_VEX_CHARGE, 0.35f, 0.6f)),
    /** A mark full on an enemy: a sculk sensor's clicks and a soul, high and thin. */
    REAPER_MARK(new Layer(Sound.BLOCK_SCULK_SENSOR_CLICKING, 0.45f, 1.4f), new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.5f, 1.5f)),
    /** The marks reaped: an anchor spent, the sculk's bloom, the vex and the souls torn out. */
    REAPER_REAP(new Layer(Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.45f, 1.3f), new Layer(Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.55f, 1.2f),
            new Layer(Sound.ENTITY_VEX_CHARGE, 0.4f, 0.8f), new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.8f, 0.6f)),
    /** The souls taken in (the reaper healed): a soul, a low chime and the allay's breath. */
    REAPER_SOUL(new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 1.4f), new Layer(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.3f, 0.7f),
            new Layer(Sound.ENTITY_ALLAY_AMBIENT_WITHOUT_ITEM, 0.2f, 0.6f)),
    /** The scythe thrown, turning: a riptide's whirl, a wing beat and a whoosh. */
    REAPER_THROW(new Layer(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.35f, 1.4f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.4f, 0.9f),
            new Layer(Sound.ITEM_TRIDENT_THROW, 0.4f, 0.8f)),
    /** The reaper goes through as a wraith: the vex's rush, a swoop and a soul. */
    REAPER_PHASE(new Layer(Sound.ENTITY_VEX_CHARGE, 0.5f, 0.7f), new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.4f, 1.2f),
            new Layer(Sound.PARTICLE_SOUL_ESCAPE, 0.6f, 1.0f)),
    /** The gate of 大 opens: an end portal's frame, an anchor charging and the warden drawing breath. */
    REAPER_GATE(new Layer(Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.6f, 0.6f), new Layer(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.6f),
            new Layer(Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.25f, 0.7f)),
    /** The great scythe of 大 goes round: the dragon's wing, a deep whoosh, a swoop and the wither's breath. */
    REAPER_GRAND(new Layer(Sound.ENTITY_ENDER_DRAGON_FLAP, 0.6f, 0.6f), new Layer(Sound.ITEM_TRIDENT_THROW, 0.6f, 0.55f),
            new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.5f, 0.5f), new Layer(Sound.ENTITY_WITHER_SHOOT, 0.12f, 0.5f)),
    // the warrior (t168): the blood knight's heavy blade and heartbeat, the floating greatswords and chains, the ward of the lantern
    /** A heavy blade swung: a deep whoosh, a slow wing beat and netherite plates moving. */
    BLOOD_SWING(new Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 0.7f), new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.3f, 0.6f),
            new Layer(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.3f, 0.8f)),
    /** A blade into flesh: a heavy crit, a wet squelch and a ravager's bite. */
    BLOOD_HIT(new Layer(Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.5f, 0.7f), new Layer(Sound.BLOCK_HONEY_BLOCK_BREAK, 0.45f, 0.6f),
            new Layer(Sound.ENTITY_RAVAGER_ATTACK, 0.3f, 1.1f)),
    /** The crimson whirl: a riptide's whirl, a swoop and plates. */
    BLOOD_WHIRL(new Layer(Sound.ITEM_TRIDENT_RIPTIDE_2, 0.5f, 0.75f), new Layer(Sound.ENTITY_PHANTOM_SWOOP, 0.4f, 0.7f),
            new Layer(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.35f, 0.7f)),
    /** A slam: the anvil, the ground breaking, a wet thud and the ravager. */
    BLOOD_SLAM(new Layer(Sound.BLOCK_ANVIL_PLACE, 0.4f, 0.6f), new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.3f, 1.2f),
            new Layer(Sound.BLOCK_SLIME_BLOCK_FALL, 0.5f, 0.6f), new Layer(Sound.ENTITY_RAVAGER_ATTACK, 0.4f, 0.8f)),
    /** Life drunk back from a wound: the witch drinking and the sculk's bloom, faint. */
    BLOOD_DRINK(new Layer(Sound.ENTITY_WITCH_DRINK, 0.4f, 0.8f), new Layer(Sound.BLOCK_SCULK_CATALYST_BLOOM, 0.3f, 1.4f)),
    /** The oath sworn: the ravager's roar, an anchor charging low and a heartbeat. */
    BLOOD_OATH(new Layer(Sound.ENTITY_RAVAGER_ROAR, 0.6f, 0.8f), new Layer(Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.5f, 0.5f),
            new Layer(Sound.ENTITY_WARDEN_HEARTBEAT, 0.9f, 1.0f)),
    /** The oath's heart beating. */
    BLOOD_HEARTBEAT(new Layer(Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 1.1f)),
    /** The oath's blood bursting out: a sonic boom, an explosion, the ravager and a wet crash. */
    BLOOD_BURST(new Layer(Sound.ENTITY_WARDEN_SONIC_BOOM, 0.3f, 1.4f), new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.8f),
            new Layer(Sound.ENTITY_RAVAGER_ROAR, 0.4f, 1.2f), new Layer(Sound.BLOCK_SLIME_BLOCK_BREAK, 0.6f, 0.5f)),
    /** The blade raised to parry: steel ringing. */
    BLOOD_GUARD(new Layer(Sound.ITEM_ARMOR_EQUIP_IRON, 0.6f, 1.2f), new Layer(Sound.ITEM_TRIDENT_RETURN, 0.4f, 1.5f)),
    /** A blow parried: a shield's clang, the anvil high and a trident's strike. */
    BLOOD_PARRY(new Layer(Sound.ITEM_SHIELD_BLOCK, 0.8f, 1.3f), new Layer(Sound.BLOCK_ANVIL_LAND, 0.3f, 1.9f),
            new Layer(Sound.ITEM_TRIDENT_HIT, 0.5f, 1.5f)),
    /** The swords called: a beacon, netherite and a crystal's hum. */
    SWORD_SUMMON(new Layer(Sound.BLOCK_BEACON_POWER_SELECT, 0.35f, 1.6f), new Layer(Sound.ITEM_ARMOR_EQUIP_NETHERITE, 0.4f, 1.2f),
            new Layer(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.3f, 0.9f)),
    /** A sword sent flying: a trident thrown and a riptide's rush. */
    SWORD_FLY(new Layer(Sound.ITEM_TRIDENT_THROW, 0.5f, 1.1f), new Layer(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.25f, 1.6f)),
    /** A sword stabbed into the ground: a trident landing, the anvil and stone. */
    SWORD_IMPACT(new Layer(Sound.ITEM_TRIDENT_HIT_GROUND, 0.7f, 0.8f), new Layer(Sound.BLOCK_ANVIL_LAND, 0.25f, 1.3f),
            new Layer(Sound.BLOCK_STONE_BREAK, 0.4f, 0.8f)),
    /** A great sword landing: an explosion, the anvil low, a trident deep in the ground and the iron golem. */
    SWORD_GRAND(new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 0.7f), new Layer(Sound.BLOCK_ANVIL_LAND, 0.5f, 0.5f),
            new Layer(Sound.ITEM_TRIDENT_HIT_GROUND, 0.8f, 0.5f), new Layer(Sound.ENTITY_IRON_GOLEM_DAMAGE, 0.4f, 0.6f)),
    /** A chain flung: chains placed, mail and a whoosh. */
    CHAIN_THROW(new Layer(Sound.BLOCK_CHAIN_PLACE, 0.7f, 1.0f), new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.6f, 0.8f),
            new Layer(Sound.ITEM_TRIDENT_THROW, 0.3f, 1.4f)),
    /** A chain pulled tight: chains breaking low, an iron door and chains placed. */
    CHAIN_BIND(new Layer(Sound.BLOCK_CHAIN_BREAK, 0.6f, 0.6f), new Layer(Sound.BLOCK_IRON_DOOR_CLOSE, 0.4f, 0.7f),
            new Layer(Sound.BLOCK_CHAIN_PLACE, 0.5f, 0.5f)),
    /** A chain let go: chains breaking, high. */
    CHAIN_BREAK(new Layer(Sound.BLOCK_CHAIN_BREAK, 0.6f, 1.3f), new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.4f, 1.5f)),
    /** The ward's iron wedge struck in: the anvil, the iron golem's blow and deepslate. */
    WARD_WEDGE(new Layer(Sound.BLOCK_ANVIL_LAND, 0.45f, 0.8f), new Layer(Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 0.8f),
            new Layer(Sound.BLOCK_DEEPSLATE_BREAK, 0.5f, 0.7f)),
    /** The fence of chains set: chains placed and an iron trapdoor. */
    WARD_FENCE(new Layer(Sound.BLOCK_CHAIN_PLACE, 0.7f, 0.8f), new Layer(Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.5f, 0.7f)),
    /** The cage shut: an iron door, a beacon and chains. */
    WARD_CAGE(new Layer(Sound.BLOCK_IRON_DOOR_CLOSE, 0.6f, 0.6f), new Layer(Sound.BLOCK_BEACON_ACTIVATE, 0.3f, 1.4f),
            new Layer(Sound.BLOCK_CHAIN_PLACE, 0.5f, 0.6f)),
    /** The bell of judgment. */
    WARD_BELL(new Layer(Sound.BLOCK_BELL_USE, 1.0f, 0.7f), new Layer(Sound.BLOCK_BELL_RESONATE, 0.8f, 1.0f),
            new Layer(Sound.BLOCK_ANVIL_LAND, 0.2f, 0.5f)),
    /** An isolated enemy struck: a small bell and a trident's hit. */
    WARD_ISOLATED(new Layer(Sound.BLOCK_BELL_USE, 0.3f, 1.8f), new Layer(Sound.ITEM_TRIDENT_HIT, 0.4f, 0.9f)),
    // the cleric (t168): the mace and the shield ring with bells and struck iron, the staff with lightning and chimes
    /** A mace swung: a deep whoosh and a faint bell. */
    HOLY_SWING(new Layer(Sound.ITEM_TRIDENT_THROW, 0.45f, 0.75f), new Layer(Sound.BLOCK_BELL_USE, 0.15f, 1.6f)),
    /** The shield striking: a shield's block, the iron golem's blow and a bell. */
    HOLY_BASH(new Layer(Sound.ITEM_SHIELD_BLOCK, 0.8f, 0.8f), new Layer(Sound.ENTITY_IRON_GOLEM_ATTACK, 0.5f, 1.0f),
            new Layer(Sound.BLOCK_BELL_USE, 0.35f, 1.2f)),
    /** A step behind the shield: armor moving and a chime. */
    HOLY_STEP(new Layer(Sound.ITEM_ARMOR_EQUIP_IRON, 0.6f, 1.0f), new Layer(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.4f, 1.4f)),
    /** The shield raised: a beacon, iron armor and a resonating crystal. */
    HOLY_RAISE(new Layer(Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 1.5f), new Layer(Sound.ITEM_ARMOR_EQUIP_IRON, 0.7f, 0.8f),
            new Layer(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.5f, 1.2f)),
    /** A blow taken on the raised shield: a shield's block and a bell, bright. */
    HOLY_BLOCK(new Layer(Sound.ITEM_SHIELD_BLOCK, 0.9f, 1.2f), new Layer(Sound.BLOCK_BELL_USE, 0.4f, 1.9f)),
    /** The hammer of judgment: the anvil, a bell, thunder far off and the ground. */
    HOLY_HAMMER(new Layer(Sound.BLOCK_ANVIL_LAND, 0.5f, 0.7f), new Layer(Sound.BLOCK_BELL_USE, 0.9f, 0.8f),
            new Layer(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.3f, 1.6f), new Layer(Sound.ENTITY_GENERIC_EXPLODE, 0.25f, 1.3f)),
    /** The bastion: a beacon's power, the bell's resonance and a choir-like chime. */
    HOLY_BASTION(new Layer(Sound.BLOCK_BEACON_POWER_SELECT, 0.7f, 0.9f), new Layer(Sound.BLOCK_BELL_RESONATE, 0.8f, 1.2f),
            new Layer(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 0.8f)),
    /** The radiance or the verses full: a chime and an experience orb. */
    HOLY_FULL(new Layer(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.6f), new Layer(Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.3f, 1.4f)),
    /** A blessing: a beacon, chimes and the allay's breath. */
    HOLY_BLESS(new Layer(Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.8f), new Layer(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.2f),
            new Layer(Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.3f, 1.3f)),
    /** A mending light: a level-up chime, soft, and a crystal. */
    HOLY_MEND(new Layer(Sound.ENTITY_PLAYER_LEVELUP, 0.35f, 1.6f), new Layer(Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.6f, 1.5f)),
    /** A chant: the evoker's prayer and a beacon's hum. */
    HOLY_CHANT(new Layer(Sound.ENTITY_EVOKER_PREPARE_SUMMON, 0.45f, 1.3f), new Layer(Sound.BLOCK_BEACON_AMBIENT, 0.8f, 1.4f)),
    /** Lightning called from the staff: a crackle and a cast. */
    STORM_CAST(new Layer(Sound.ENTITY_EVOKER_CAST_SPELL, 0.4f, 1.6f), new Layer(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.25f, 1.9f)),
    /** Lightning striking: a bolt's impact, thunder far off and a crackle. */
    STORM_STRIKE(new Layer(Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.6f, 1.3f), new Layer(Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.2f, 1.8f),
            new Layer(Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.3f, 1.6f)),
    // the ranger (t168): the wire sings and snaps, the bow creaks and twangs, the spirit bow hums
    /** A wire flung: a fishing line cast, a whoosh and a thin metal ring. */
    WIRE_THROW(new Layer(Sound.ENTITY_FISHING_BOBBER_THROW, 0.7f, 1.3f), new Layer(Sound.ITEM_TRIDENT_THROW, 0.3f, 1.8f),
            new Layer(Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.3f, 2.0f)),
    /** A wire pulled taut: the fishing line reeled in, a crossbow loading and a tripwire. */
    WIRE_PULL(new Layer(Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.8f, 1.1f), new Layer(Sound.ITEM_CROSSBOW_LOADING_END, 0.5f, 1.4f),
            new Layer(Sound.BLOCK_TRIPWIRE_ATTACH, 0.6f, 1.2f)),
    /** A wire let go: a tripwire snapping. */
    WIRE_SNAP(new Layer(Sound.BLOCK_TRIPWIRE_DETACH, 0.7f, 1.4f), new Layer(Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.3f, 1.8f)),
    /** The ranger zipping along a wire: a riptide's rush, a line reeled fast and a whoosh. */
    WIRE_ZIP(new Layer(Sound.ITEM_TRIDENT_RIPTIDE_1, 0.4f, 1.6f), new Layer(Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 0.6f, 1.6f),
            new Layer(Sound.ENTITY_PHANTOM_FLAP, 0.3f, 1.7f)),
    /** A bow drawn hard: a crossbow loading and the string creaking. */
    BOW_DRAW(new Layer(Sound.ITEM_CROSSBOW_LOADING_MIDDLE, 0.7f, 0.8f), new Layer(Sound.ITEM_CROSSBOW_LOADING_START, 0.5f, 0.9f)),
    /** An arrow loosed: the bow's twang and a whoosh. */
    BOW_LOOSE(new Layer(Sound.ENTITY_ARROW_SHOOT, 0.8f, 0.9f), new Layer(Sound.ITEM_CROSSBOW_SHOOT, 0.4f, 1.2f),
            new Layer(Sound.ITEM_TRIDENT_THROW, 0.25f, 1.7f)),
    /** The spirit bow set: wood placed, a beacon and the allay. */
    SPIRIT_SET(new Layer(Sound.BLOCK_WOOD_PLACE, 0.9f, 0.9f), new Layer(Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f),
            new Layer(Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.4f, 1.2f)),
    /** The spirit bow's overdrive: a beacon's power, the allay and a fast crossbow. */
    SPIRIT_OVERDRIVE(new Layer(Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, 1.6f), new Layer(Sound.ENTITY_ALLAY_AMBIENT_WITH_ITEM, 0.6f, 1.5f),
            new Layer(Sound.ITEM_CROSSBOW_QUICK_CHARGE_3, 0.6f, 1.2f));

    /** One vanilla sound of a role: its own volume and pitch, scaled by what the caller asks for. */
    public record Layer(Sound sound, float volume, float pitch) {
    }

    private final Layer[] layers;
    /** The config's own layers of a role ({@code sound-design.<ROLE>}, t166: the sounds can be tuned without a new jar). */
    private static volatile java.util.Map<Sfx, Layer[]> overrides = java.util.Map.of();

    Sfx(Layer... layers) {
        this.layers = layers;
    }

    /** The layers the role plays: the config's when it gives some, else its own. */
    public Layer[] layers() {
        Layer[] own = overrides.get(this);
        return own != null ? own : layers;
    }

    /**
     * Reads {@code sound-design} (on start and reload): {@code <ROLE>: ["SOUND_NAME volume pitch", ...]} replaces the layers of that role (a role
     * not named keeps its own; a line that cannot be read is told to {@code warn} and left out).
     */
    public static void configure(org.bukkit.configuration.ConfigurationSection section, java.util.function.Consumer<String> warn) {
        java.util.Map<Sfx, Layer[]> read = new java.util.EnumMap<>(Sfx.class);
        if (section != null) {
            for (String key : section.getKeys(false)) {
                Sfx role;
                try {
                    role = Sfx.valueOf(key.toUpperCase(java.util.Locale.ROOT));
                } catch (IllegalArgumentException exception) {
                    warn.accept("sound-design." + key + ": no such sound role (" + java.util.Arrays.toString(values()) + ")");
                    continue;
                }
                java.util.List<Layer> layers = new java.util.ArrayList<>();
                for (String line : section.getStringList(key)) {
                    Layer layer = layer(line);
                    if (layer == null) warn.accept("sound-design." + key + ": '" + line + "' is not \"SOUND_NAME volume pitch\"");
                    else layers.add(layer);
                }
                if (!layers.isEmpty()) read.put(role, layers.toArray(new Layer[0]));
            }
        }
        overrides = read;
    }

    /** "ITEM_TRIDENT_THROW 0.5 1.7" as a layer (the sound's name as the game's Sound, any case, "minecraft:" or dots allowed), or null. */
    static Layer layer(String line) {
        String[] parts = line == null ? new String[0] : line.trim().split("\\s+");
        if (parts.length != 3) return null;
        String name = parts[0].toUpperCase(java.util.Locale.ROOT).replace("MINECRAFT:", "").replace('.', '_');
        try {
            Sound sound = Sound.valueOf(name);
            return new Layer(sound, Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /** Plays the role at {@code at}: every layer at its own volume and pitch times {@code volume} and {@code pitch}. */
    public void play(Location at, float volume, float pitch) {
        World world = at.getWorld();
        if (world == null) return;
        for (Layer layer : layers()) {
            world.playSound(at, layer.sound(), SoundCategory.PLAYERS, layer.volume() * volume, Math.max(0.5f, Math.min(2.0f, layer.pitch() * pitch)));
        }
    }
}
