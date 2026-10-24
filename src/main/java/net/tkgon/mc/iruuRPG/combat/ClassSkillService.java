package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassSkillDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.vfx.Curves;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Combat behavior of class active skills (cskill/*.yml). Skills without an entry here only show their message. */
public final class ClassSkillService {

    public static final String WARRIOR_STRIKE = "warrior_strike";
    public static final String WARRIOR_STRIKE_UPGRADE = "warrior_strike_upgrade";
    public static final String INSTINCT_RELEASE = "instinct_release";
    public static final String SATSUJIN = "satsujin";
    public static final String SATSUJIN_UPGRADE = "satsujin_upgrade";
    public static final String SANCTUARY = "sanctuary";
    public static final String SANCTUARY_UPGRADE = "sanctuary_upgrade";
    public static final String ICE_LANCE = "ice_lance";
    public static final String ICE_LANCE_UPGRADE = "ice_lance_upgrade";
    public static final String METEOR = "meteor";
    public static final String METEOR_UPGRADE = "meteor_upgrade";

    private static final double BOLT_HEIGHT = 14.0;
    private static final int BOLT_FLASHES = 3;
    private static final long BOLT_FLASH_INTERVAL_TICKS = 2L;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final ClassService classService;
    private final PlayerProfileManager profileManager;
    private StatusEffectService statusEffectService;
    private ClassEffectService classEffectService;

    public ClassSkillService(JavaPlugin plugin, AttackService attackService, ClassService classService, PlayerProfileManager profileManager) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.classService = classService;
        this.profileManager = profileManager;
        this.meteor = new MeteorSkill(plugin, attackService);
    }

    private net.tkgon.mc.iruuRPG.skill.SkillRegistry generalSkills;
    private net.tkgon.mc.iruuRPG.skill.SkillRunner skillRunner;

    /** Skills made from templates (skills/*.yml) cast by a class key. */
    private final MeteorSkill meteor;

    public void setGeneralSkills(net.tkgon.mc.iruuRPG.skill.SkillRegistry generalSkills, net.tkgon.mc.iruuRPG.skill.SkillRunner skillRunner) {
        this.meteor.setBeam(generalSkills, skillRunner);
        this.generalSkills = generalSkills;
        this.skillRunner = skillRunner;
    }

    private boolean isGeneral(ClassSkillDefinition skill) {
        return generalSkills != null && skillRunner != null && generalSkills.all().containsKey(skill.id().toLowerCase());
    }

    private Precheck precheckGeneral(Player player) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike()
                || (weapon.attackType() != AttackType.MELEE && weapon.attackType() != AttackType.RANGE)) {
            return Precheck.rejected("近接か遠距離の武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }
        return Precheck.ready(null, weapon);
    }

    private void executeGeneral(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        net.tkgon.mc.iruuRPG.skill.SkillSpec spec = generalSkills.all().get(skill.id().toLowerCase());
        if (spec == null) return;
        skillRunner.start(new net.tkgon.mc.iruuRPG.skill.PlayerSkillCaster(plugin, attackService, player, weapon, spec), spec,
                () -> net.tkgon.mc.iruuRPG.skill.PlayerSkillCaster.aimPoint(player, spec.range()), () -> {
                });
    }

    public void setClassEffectService(ClassEffectService classEffectService) {
        this.classEffectService = classEffectService;
    }

    public void setStatusEffectService(StatusEffectService statusEffectService) {
        this.statusEffectService = statusEffectService;
    }

    /** What the skill needs before it may spend MP/cooldown; null means the skill has no combat effect yet. */
    public Precheck precheck(Player player, ClassSkillDefinition skill) {
        if (WARRIOR_STRIKE.equalsIgnoreCase(skill.id())) {
            return precheckSanctuary(player);
        }
        if (ICE_LANCE.equalsIgnoreCase(skill.id()) || METEOR.equalsIgnoreCase(skill.id())) {
            return precheckIceLance(player);
        }
        if (INSTINCT_RELEASE.equalsIgnoreCase(skill.id())) {
            return Precheck.ready(null, null);
        }
        if (SANCTUARY.equalsIgnoreCase(skill.id())) {
            return precheckSanctuary(player);
        }
        if (SATSUJIN.equalsIgnoreCase(skill.id())) {
            return precheckSatsujin(player, skill);
        }
        if (isGeneral(skill)) {
            return precheckGeneral(player);
        }
        return Precheck.noEffect();
    }

    /** Runs the effect decided in {@link #precheck}. */
    public void execute(Player player, ClassSkillDefinition skill, Precheck precheck) {
        ParticleFx.mark(player);
        if (INSTINCT_RELEASE.equalsIgnoreCase(skill.id())) {
            if (classEffectService != null) {
                classEffectService.activateInstinct(player, skill.values().getOrDefault("duration-seconds", 10.0), skill.values());
            }
            return;
        }
        if (precheck.weapon() == null) return;
        // the assassin's frenzy gives status effects with every hit (for as long as it lasts); every other class skill, once per enemy
        if (!SATSUJIN.equalsIgnoreCase(skill.id())) attackService.beginAttack(player);
        if (isGeneral(skill)) {
            executeGeneral(player, skill, precheck.weapon());
            return;
        }
        if (WARRIOR_STRIKE.equalsIgnoreCase(skill.id())) {
            warriorSurge(player, skill, precheck.weapon());
        } else if (ICE_LANCE.equalsIgnoreCase(skill.id())) {
            iceLance(player, skill, precheck.weapon());
        } else if (METEOR.equalsIgnoreCase(skill.id())) {
            meteor.cast(player, skill.values(), precheck.weapon(), classService.hasSkill(profileManager.getOrCreate(player), METEOR_UPGRADE));
        } else if (SANCTUARY.equalsIgnoreCase(skill.id())) {
            sanctuary(player, skill, precheck.weapon());
        } else if (SATSUJIN.equalsIgnoreCase(skill.id())) {
            satsujin(player, skill);
        }
    }

    private Precheck precheckSatsujin(Player player, ClassSkillDefinition skill) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike() || weapon.attackType() != AttackType.MELEE) {
            return Precheck.rejected("近接武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }
        return Precheck.ready(null, weapon);
    }

    private Precheck precheckSanctuary(Player player) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike()) {
            return Precheck.rejected("武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }
        return Precheck.ready(null, weapon);
    }

    private Precheck precheckIceLance(Player player) {
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike() || weapon.attackType() != AttackType.RANGE) {
            return Precheck.rejected("遠距離武器を持っていないと使えません。");
        }
        if (!attackService.canUseItem(player, weapon)) {
            attackService.sendLevelRequirement(player, weapon);
            return Precheck.rejected(null);
        }
        return Precheck.ready(null, weapon);
    }

    private LivingEntity findTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(
                eye,
                eye.getDirection(),
                range,
                0.6,
                entity -> isValidTarget(player, entity)
        );
        return result == null || !(result.getHitEntity() instanceof LivingEntity living) ? null : living;
    }

    private boolean isValidTarget(Player player, Entity entity) {
        if (!(entity instanceof LivingEntity living)) return false;
        if (entity.equals(player) || entity instanceof Player || entity instanceof ArmorStand) return false;
        return !DeployService.isDeployEntity(living) && !living.isDead() && attackService.isAttackTarget(living, player);
    }

    /**
     * The warrior's surge: for some seconds (8; 12 when upgraded) the warrior is wrapped in lightning. Every damage they deal gives back a share as
     * health (8%; 14% when upgraded: {@link ClassEffectService#activateSurge}), and every enemy the warrior hits sends a chain of lightning on to the ones near it
     * ({@link #chainLightning}), so the surge adds damage to the warrior's blows and the damage heals.
     */
    private void warriorSurge(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        boolean upgraded = classService.hasSkill(profile, WARRIOR_STRIKE_UPGRADE);
        double baseSeconds = upgraded ? skill.values().getOrDefault("upgrade-duration-seconds", 12.0) : skill.values().getOrDefault("duration-seconds", 8.0);
        PlayerProfile snapshot = attackService.snapshotAttacker(player);
        double seconds = baseSeconds * Math.max(0.1, 1.0 + snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.DURATION) / 100.0);
        // whatever the duration stat adds, the surge does not last longer than max-seconds
        seconds = Math.min(seconds, skill.values().getOrDefault("max-seconds", 5.0));
        double lifesteal = upgraded ? skill.values().getOrDefault("upgrade-lifesteal", 0.14) : skill.values().getOrDefault("lifesteal", 0.08);
        double boltRate = upgraded ? skill.values().getOrDefault("upgrade-bolt-rate", 0.9) : skill.values().getOrDefault("bolt-rate", 0.6);
        if (classEffectService == null) return;
        classEffectService.activateSurge(player, seconds, upgraded ? 2 : 1, lifesteal);
        // the lightning is no longer called down by itself: every enemy the warrior hits sends a chain of lightning on to the ones near it
        classEffectService.setSurgeChain(player, first -> chainLightning(player, skill, weapon, first, boltRate, upgraded));

        World world = player.getWorld();
        Location base = player.getLocation();
        world.playSound(base, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.9f, 1.3f);
        world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.9f);
        // it opens with a crown of bolts round the warrior and two rings of light on the ground
        for (int index = 0; index < 4; index++) {
            double angle = Math.PI * 0.5 * index + Math.PI * 0.25;
            Location head = base.clone().add(Math.cos(angle) * 2.2, 1.0, Math.sin(angle) * 2.2);
            SkillFx.bolt(player, head.clone().add(0.0, 9.0, 0.0), head, 9, 0.8, SkillFx.LIGHTNING, false);
        }
        SkillFx.groundRing(player, base, 2.4, SkillFx.LIGHTNING, 0.7, 1.2);
        SkillFx.groundRing(player, base, 3.6, SkillFx.LIGHTNING, 0.9, 1.2);
        SkillFx.burst(player, base.clone().add(0.0, 1.0, 0.0), 2.4, SkillFx.LIGHTNING);

        int total = (int) Math.round(seconds * 20.0);
        new SafeTask(plugin) {
            private int tick;

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (tick >= total || !player.isOnline() || player.isDead() || classEffectService.surgeLevel(player) == 0) {
                    classEffectService.endSurge(player);
                    cancel();
                    return;
                }
                drawSurgeAura(player, tick);
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /** Lightning turning round the warrior's body (two strands up the length of it) and a crackle of sparks. */
    private void drawSurgeAura(Player player, int tick) {
        Location feet = player.getLocation();
        Vector up = new Vector(0.0, 1.0, 0.0);
        SkillFx.helix(player, feet, up, 0.0, 2.2, 2.2, 0.75, SkillFx.LIGHTNING, tick * 0.35);
        if (tick % 6 == 0) {
            SkillFx.groundRing(player, feet, 1.4, SkillFx.LIGHTNING, 0.6, 1.0);
            ParticleFx.to(player.getWorld()).spawnParticle(Particle.ELECTRIC_SPARK, feet.clone().add(0.0, 1.0, 0.0), 3, 0.5, 0.7, 0.5, 0.2);
        }
    }

    /**
     * Chain lightning from the enemy the warrior just hit: it jumps to the nearest enemy within {@code chain-range} blocks (6) that it has not touched, up to 3 times (4 when
     * upgraded), two ticks between jumps, each jump {@code chain-decay} (75%) as strong as the one before; the first is {@code rate} times the weapon's damage. The hits
     * of the chain are the warrior's, so they give back health as the other hits do, but they do not start another chain.
     */
    private void chainLightning(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon, LivingEntity first, double rate, boolean upgraded) {
        if (first == null || !first.isValid()) return;
        int jumps = upgraded ? 4 : 3;
        double range = skill.values().getOrDefault("chain-range", 6.0);
        double decay = skill.values().getOrDefault("chain-decay", 0.75);
        java.util.Set<UUID> touched = new java.util.HashSet<>();
        touched.add(first.getUniqueId());
        SkillFx.burst(ParticleFx.owner(), first.getLocation().add(0.0, first.getHeight() * 0.5, 0.0), 1.2, SkillFx.LIGHTNING);
        chainStep(player, weapon, first, touched, jumps, range, decay, rate, upgraded, 0);
    }

    private void chainStep(Player player, RpgItemDefinition weapon, LivingEntity from, java.util.Set<UUID> touched, int left, double range, double decay, double rate, boolean big, int jump) {
        if (left <= 0 || !player.isOnline()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            LivingEntity best = null;
            double bestDistance = range * range;
            Location center = from.getLocation().add(0.0, from.getHeight() * 0.5, 0.0);
            for (Entity entity : from.getWorld().getNearbyEntities(center, range, range, range)) {
                if (!isValidTarget(player, entity) || touched.contains(entity.getUniqueId())) continue;
                double distance = entity.getLocation().distanceSquared(center);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = (LivingEntity) entity;
                }
            }
            if (best == null) return;
            touched.add(best.getUniqueId());
            Location to = best.getLocation().add(0.0, best.getHeight() * 0.5, 0.0);
            // an arc of lightning from one enemy to the next, a zigzag between them with a flash at each end
            SkillFx.trail(ParticleFx.owner(), center, to, SkillFx.LIGHTNING, 1.2);
            player.getWorld().playSound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.35f, 1.5f + 0.1f * jump);
            SkillFx.burst(ParticleFx.owner(), to, 0.9, SkillFx.LIGHTNING);
            classEffectService.setChaining(player, true);
            try {
                AttackService.AttackDamage attack = attackService.calculateDamage(player, best, weapon);
                double damage = Math.round(attack.result().damage() * rate * Math.pow(decay, jump + 1) * 10.0) / 10.0;
                attackService.applyDirectDamage(player, best, damage, weapon, false);
            } finally {
                classEffectService.setChaining(player, false);
            }
            chainStep(player, weapon, best, touched, left - 1, range, decay, rate, big, jump + 1);
        }, 2L);
    }

    private LivingEntity nearestEnemy(Player player, double range) {
        LivingEntity best = null;
        double bestDistance = range * range;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), range, range, range)) {
            if (!isValidTarget(player, entity)) continue;
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = (LivingEntity) entity;
            }
        }
        return best;
    }

    private void strikeHit(Player player, ClassSkillDefinition skill, LivingEntity target, RpgItemDefinition weapon, double rate, boolean big, double lifesteal, double splashShare, boolean loud) {
        AttackService.AttackDamage attack = attackService.calculateDamage(player, target, weapon);
        double damage = Math.round(attack.result().damage() * rate * 10.0) / 10.0;
        boolean critical = attack.result().critical();

        playLightning(target.getEyeLocation(), target.getLocation(), big, loud);
        if (!attackService.applyDirectDamage(player, target, damage, weapon, critical)) return;

        if (lifesteal > 0.0) attackService.healPlayer(player, damage * lifesteal);
        // the thunder spreads: the enemies round the target take a share of the strike and are stunned for a moment (a strong slow)
        double splashRadius = skill.values().getOrDefault("splash-radius", 3.5) * (big ? 1.3 : 1.0);
        if (splashRadius > 0.0 && splashShare > 0.0) {
            for (org.bukkit.entity.Entity entity : target.getWorld().getNearbyEntities(target.getLocation(), splashRadius, splashRadius, splashRadius)) {
                if (!isValidTarget(player, entity) || entity.equals(target)) continue;
                LivingEntity near = (LivingEntity) entity;
                if (near.getLocation().distanceSquared(target.getLocation()) > splashRadius * splashRadius) continue;
                AttackService.AttackDamage around = attackService.calculateDamage(player, near, weapon);
                double splash = Math.round(around.result().damage() * rate * splashShare * 10.0) / 10.0;
                if (attackService.applyDirectDamage(player, near, splash, weapon, false) && statusEffectService != null && near.isValid() && !near.isDead()) {
                    statusEffectService.applyTimedSlow(near, 80.0, 1500L, player.getUniqueId());
                    SkillFx.trail(ParticleFx.owner(), target.getLocation().add(0.0, 1.0, 0.0), near.getLocation().add(0.0, 1.0, 0.0), SkillFx.LIGHTNING, 1.0);
                }
            }
        }
        attackService.sendDamageDebug(player, weapon.attackType() == AttackType.RANGE ? AttackType.RANGE : AttackType.MELEE, target, damage, critical, false);
    }

    /** A lightning bolt drawn with yellow dust particles, striking the head from above. */
    private void playLightning(Location head, Location feet, boolean big, boolean loud) {
        World world = head.getWorld();
        if (world == null) return;

        float volume = loud ? 1.0f : 0.45f;
        world.playSound(head, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, volume * (big ? 1.0f : 0.7f), big ? 1.2f : 1.6f);
        world.playSound(head, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, volume * (big ? 1.0f : 0.8f), big ? 0.9f : 1.2f);

        Player fxOwner = ParticleFx.owner();
        new SafeTask(plugin) {
            private int flash;

            @Override
            protected void tick() {
                ParticleFx.mark(fxOwner);
                if (flash >= BOLT_FLASHES) {
                    cancel();
                    return;
                }
                drawBolt(world, head, feet, flash == 0, big);
                flash++;
            }
        }.runTaskTimer(plugin, 0L, BOLT_FLASH_INTERVAL_TICKS);
    }

    private void drawBolt(World world, Location head, Location feet, boolean impact, boolean big) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double height = big ? BOLT_HEIGHT + 6.0 : BOLT_HEIGHT;
        Location top = head.clone().add(random.nextDouble(-1.2, 1.2), height, random.nextDouble(-1.2, 1.2));
        // a zigzag from the sky to the head (a hot core and a dimmer glow beside it, the color from amber to white)
        SkillFx.bolt(ParticleFx.owner(), top, head, (int) height, big ? 1.3 : 0.9, SkillFx.LIGHTNING, big);
        if (impact) {
            ParticleFx.to(world).spawnParticle(Particle.ELECTRIC_SPARK, head, big ? 60 : 24, 0.6, 0.6, 0.6, 0.3);
            SkillFx.groundRing(ParticleFx.owner(), feet, big ? 2.6 : 1.8, SkillFx.LIGHTNING, 0.6, 1.2);
            if (big) {
                SkillFx.groundRing(ParticleFx.owner(), feet, 1.3, SkillFx.LIGHTNING, 0.9, 1.2);
                ParticleFx.to(world).spawnParticle(Particle.FLASH, head, 1);
            }
        }
    }

    // ---- satsujin (assassin) --------------------------------------------------------

    /**
     * The assassin's frenzy: for some seconds (8; 12 when upgraded) the assassin's combo is stronger (see {@link AssassinMeleeCombo}): the blows hit harder,
     * the normal attacks come faster, an enemy out of reach is blinked to, a shadow cuts again after every normal attack, and the status
     * effects (confusion) land with every hit. A rush of shadow opens it, and the assassin is wrapped in it until it ends. The assassin cannot go farther than 12 blocks from
     * where it began ({@link net.tkgon.mc.iruuRPG.listener.FrenzyCageListener}), and heavy 0 is a dash instead of a clone.
     */
    private void satsujin(Player player, ClassSkillDefinition skill) {
        boolean upgraded = classService.hasSkill(profileManager.getOrCreate(player), SATSUJIN_UPGRADE);
        double baseSeconds = upgraded ? skill.values().getOrDefault("upgrade-duration-seconds", 12.0) : skill.values().getOrDefault("duration-seconds", 8.0);
        PlayerProfile snapshot = attackService.snapshotAttacker(player);
        double seconds = baseSeconds * Math.max(0.1, 1.0 + snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.DURATION) / 100.0);
        if (classEffectService == null) return;
        classEffectService.activateFrenzy(player, seconds, upgraded ? 2 : 1);
        int total = (int) Math.round(seconds * 20.0);
        attackService.statusUnlimited(player, total + 10);
        // every clone that stood is destroyed, and three are flung forward; each copies the next five attacks
        attackService.assassinSwapClones(player, attackService.weaponInMainHand(player), total);

        World world = player.getWorld();
        Location base = player.getLocation();
        world.playSound(base, Sound.ENTITY_ENDERMAN_TELEPORT, 0.9f, 0.7f);
        world.playSound(base, Sound.ENTITY_WITHER_SHOOT, 0.35f, 1.8f);
        world.playSound(base, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 0.7f);
        Player owner = ParticleFx.owner();
        SkillFx.column(owner, base, 3.2, 0.9, Clones.RED_BLACK);
        for (int ring = 1; ring <= 2; ring++) {
            double r = 1.2 + ring * 1.6;
            int which = ring;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> SkillFx.groundRing(owner, base, r, Clones.RED_BLACK, 1.0 - 0.2 * which, 1.2), ring - 1);
        }
        // afterimages of the assassin standing round
        net.tkgon.mc.iruuRPG.vfx.Fx fx = net.tkgon.mc.iruuRPG.vfx.Fx.of(owner, world, base);
        for (int image = 0; image < 3; image++) {
            double angle = Math.PI * 2.0 * image / 3.0;
            fx.sphereShell(base.clone().add(Math.cos(angle) * 1.8, 1.0, Math.sin(angle) * 1.8), 0.5, image * 0.6, 22, Clones.RED_BLACK, 1.0);
        }

        new SafeTask(plugin) {
            private int tick;
            private Location lastImage = base.clone();

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                if (tick >= total || !player.isOnline() || player.isDead() || classEffectService.frenzyLevel(player) == 0) {
                    // the time ran out (the echo was not pressed): it comes by itself
                    if (player.isOnline() && !player.isDead() && classEffectService.hasFrenzyRecord(player)) echo(player, skill);
                    else classEffectService.endFrenzy(player);
                    if (player.isOnline()) player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.6f);
                    cancel();
                    return;
                }
                Location feet = player.getLocation();
                // wisps of shadow climbing round the body, and the shape the assassin had a moment ago left behind
                if (tick % 6 == 0) SkillFx.helix(owner, feet, new Vector(0.0, 1.0, 0.0), 0.0, 2.0, 2.0, 0.7, Clones.RED_BLACK, -tick * 0.4);
                if (tick % 12 == 0) {
                    net.tkgon.mc.iruuRPG.vfx.Fx image = net.tkgon.mc.iruuRPG.vfx.Fx.of(owner, player.getWorld(), lastImage);
                    image.sphereShell(lastImage.clone().add(0.0, 1.0, 0.0), 0.45, 0.0, 18, Clones.RED_BLACK, 0.9);
                    lastImage = feet.clone();
                    SkillFx.groundRing(owner, feet, 1.5, Clones.RED_BLACK, 0.6, 1.0);
                }
                // the edge of the circle the assassin cannot leave shows when it is close
                double radius = plugin.getConfig().getDouble("class-effects.assassin.frenzy-radius", 12.0);
                double dx = feet.getX() - base.getX();
                double dz = feet.getZ() - base.getZ();
                if (tick % 4 == 0 && Math.hypot(dx, dz) > radius - 4.0) {
                    double angle = Math.atan2(dz, dx);
                    net.tkgon.mc.iruuRPG.vfx.Fx edge = net.tkgon.mc.iruuRPG.vfx.Fx.of(owner, player.getWorld(), feet);
                    for (int index = -6; index <= 6; index += 2) {
                        double a = angle + index * 0.05;
                        double fade = 1.0 - Math.abs(index) / 8.0;
                        for (double height : new double[]{0.4, 1.4}) {
                            edge.dust(base.getX() + Math.cos(a) * radius, feet.getY() + height, base.getZ() + Math.sin(a) * radius, Clones.RED_BLACK, 0.4 + 0.6 * fade, 1.2);
                        }
                    }
                }
                tick += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    /**
     * The item swap key pressed again during the frenzy (or the time of the frenzy running out: the same comes by itself): it ends, the clones go, and each enemy that a clone hurt at least once is struck in two parts, with the echo of what
     * the assassin dealt to it ({@code echo-share}, 10%): 30% of it in a run of six straight horizontal slashes at random angles, and then 70% in one wide, thin slash
     * across the enemy (broadside to the assassin). At most the 8 enemies hurt most. True when the press was used here (also when it is too early).
     */
    private boolean frenzyEcho(Player player, ClassSkillDefinition skill) {
        if (classEffectService == null || classEffectService.frenzyLevel(player) == 0) return false;
        double delay = skill.values().getOrDefault("recast-delay-seconds", 1.5);
        long elapsed = classEffectService.frenzyElapsedMillis(player);
        if (elapsed < delay * 1000.0) {
            player.sendMessage("[iruuRPG] SATUJINの再発動まで あと" + String.format(java.util.Locale.ROOT, "%.1f", delay - elapsed / 1000.0) + "秒");
            return true;
        }
        echo(player, skill);
        return true;
    }

    /** The echo itself (see {@link #frenzyEcho}): the press and the end of the time both come here. It ends the frenzy and takes away the clones. */
    private void echo(Player player, ClassSkillDefinition skill) {
        java.util.Set<UUID> hurtByClones = new java.util.HashSet<>(AssassinClones.hurtByClones(player));
        java.util.Map<UUID, Double> dealt = classEffectService.takeFrenzyDamage(player);
        classEffectService.endFrenzy(player);
        double share = skill.values().getOrDefault("echo-share", 0.10);
        List<java.util.Map.Entry<UUID, Double>> order = new ArrayList<>(dealt.entrySet());
        order.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        World world = player.getWorld();
        world.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.6f);
        world.playSound(player.getLocation(), Sound.ENTITY_WITHER_SHOOT, 0.4f, 1.9f);
        int victims = 0;
        double total = 0.0;
        final int slashes = 6;
        for (java.util.Map.Entry<UUID, Double> entry : order) {
            if (victims >= 8) break;
            if (!hurtByClones.contains(entry.getKey())) continue;
            Entity entity = org.bukkit.Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof LivingEntity victim) || victim.isDead() || !victim.isValid() || !victim.getWorld().equals(world)) continue;
            if (victim.getLocation().distanceSquared(player.getLocation()) > 48.0 * 48.0) continue;
            double budget = entry.getValue() * share;
            if (budget <= 0.0) continue;
            int slot = victims++;
            total += budget;
            double part = Math.round(budget * 0.3 / slashes * 10.0) / 10.0;
            double finish = Math.round(budget * 0.7 * 10.0) / 10.0;
            long start = slot * 2L;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;
                double angle = ThreadLocalRandom.current().nextDouble(Math.PI * 2.0);
                Clones.strike(plugin, player, Clones.Look.swap(true), () -> victim.isValid() && !victim.isDead() ? victim.getLocation() : null, angle, slashes, 2, blow -> {
                    if (!victim.isValid() || victim.isDead()) return;
                    echoDamage(player, victim, part);
                    world.playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 0.8f + 0.08f * blow);
                }, null);
            }, start);
            // after the run: one wide, thin slash broadside to the assassin
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline() || !victim.isValid() || victim.isDead()) return;
                wideSlash(player, victim);
                echoDamage(player, victim, finish);
            }, start + 22L);
        }
        player.sendMessage("[iruuRPG] SATUJINの残響: 分身が傷つけた" + victims + "体に、与えたダメージの" + Math.round(share * 100.0) + "%を返しました (合計 " + Math.round(total) + ")。");
    }

    private void echoDamage(Player player, LivingEntity victim, double amount) {
        if (amount <= 0.0) return;
        classEffectService.setEchoing(player, true);
        try {
            attackService.applyExtraDamage(player, victim, amount, net.tkgon.mc.iruuRPG.stat.Element.ORANGE);
        } finally {
            classEffectService.setEchoing(player, false);
        }
    }

    /** One wide, thin slash through the enemy, at right angles to the line from the assassin: fourteen blocks of red and black at the height of its chest. */
    private void wideSlash(Player player, LivingEntity victim) {
        Location chest = victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        Vector away = chest.toVector().subtract(player.getLocation().toVector()).setY(0.0);
        if (away.lengthSquared() < 1.0E-6) away = player.getLocation().getDirection().setY(0.0);
        away.normalize();
        Vector side = new Vector(-away.getZ(), 0.0, away.getX()).multiply(7.0);
        Player owner = ParticleFx.owner();
        net.tkgon.mc.iruuRPG.vfx.Fx fx = net.tkgon.mc.iruuRPG.vfx.Fx.of(owner, chest.getWorld(), chest);
        Location from = chest.clone().subtract(side);
        Location to = chest.clone().add(side);
        fx.line(from, to, Clones.RED_BLACK, 0.1, 1.0, 0.0, 1.4, 0.08, null, 1);
        fx.line(from.clone().add(0.0, 0.08, 0.0), to.clone().add(0.0, 0.08, 0.0), Clones.RED_BLACK, 0.6, 1.0, 0.0, 1.0, 0.12, null, 1);
        fx.line(from.clone().subtract(0.0, 0.08, 0.0), to.clone().subtract(0.0, 0.08, 0.0), Clones.RED_BLACK, 0.4, 0.8, 0.0, 1.0, 0.12, null, 1);
        fx.dust(chest.getX(), chest.getY(), chest.getZ(), Clones.RED_BLACK, 1.0, 2.4);
        SkillFx.burst(owner, chest, 1.8, Clones.RED_BLACK);
        chest.getWorld().playSound(chest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.6f);
        chest.getWorld().playSound(chest, Sound.ENTITY_WITHER_BREAK_BLOCK, 0.3f, 1.8f);
    }

    // ---- sanctuary (healer) --------------------------------------------------------

    private static final UUID NO_KNOCKBACK_ID = UUID.fromString("6c0b1c7e-5d54-4b7a-9d83-2f6a1d9e0a11");

    /**
     * A healing area (radius 7). Every second it heals allies in it by the damage the held weapon would deal
     * (no enemy armor; deploy weapons include the stability bonus). Upgrade: lasts longer and blocks knockback.
     */
    private void sanctuary(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        boolean upgraded = classService.hasSkill(profile, SANCTUARY_UPGRADE);
        double radius = skill.values().getOrDefault("radius", 7.0);
        double baseSeconds = upgraded
                ? skill.values().getOrDefault("upgrade-duration-seconds", 10.0)
                : skill.values().getOrDefault("duration-seconds", 5.0);

        PlayerProfile snapshot = attackService.snapshotAttacker(player);
        // the duration stat lengthens it (+100% = twice as long)
        double seconds = baseSeconds * Math.max(0.1, 1.0 + snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.DURATION) / 100.0);
        double heal = attackService.calculateNeutralDamage(snapshot, weapon).damage();
        if (heal <= 0.0) {
            heal = Math.max(snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.WEAPON_DAMAGE),
                    snapshot.finalStats().get(net.tkgon.mc.iruuRPG.stat.StatType.SPECIAL_DAMAGE));
        }
        final double healPerSecond = heal;
        final Location center = player.getLocation().clone();
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        // it opens with a column of light and a ring that rushes out to the edge
        SkillFx.column(player, center, 6.0, 1.8, SkillFx.HOLY);
        for (int ring = 1; ring <= 5; ring++) {
            double r = radius * Curves.expOut(ring / 5.0, 3.0);
            int which = ring;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> SkillFx.groundRing(player, center, r, SkillFx.HOLY, 1.0 - 0.15 * which, 1.2), ring - 1);
        }
        int totalTicks = (int) Math.round(seconds * 20.0);

        SanctuaryState state = new SanctuaryState(center, radius, healPerSecond);
        sanctuaries.put(player.getUniqueId(), state);

        new SafeTask(plugin) {
            private int tick;
            private final java.util.Set<UUID> shielded = new java.util.HashSet<>();

            @Override
            protected void tick() {
                ParticleFx.mark(player);
                state.age = tick;
                boolean finished = tick >= totalTicks || !player.isOnline() || state.ended;
                if (tick % 20 == 0 && !finished) {
                    double guardShare = skill.values().getOrDefault("ally-guard-percent", 12.0) / 100.0;
                    for (Player ally : alliesIn(center, radius)) {
                        attackService.healPlayerBy(player, ally, healPerSecond);
                        // the blessing: the allies inside take less damage for as long as they stay
                        if (guardShare > 0.0 && attackService.guard() != null) attackService.guard().grant(ally, guardShare, 30);
                        // a column of light rises round each one healed
                        SkillFx.column(player, ally.getLocation(), 2.4, 0.6, SkillFx.HOLY);
                    }
                    for (int wave = 1; wave <= 4; wave++) {
                        double r = radius * wave / 4.0;
                        int which = wave;
                        plugin.getServer().getScheduler().runTaskLater(plugin, () -> SkillFx.groundRing(player, center, r, SkillFx.HOLY, 0.5 + 0.1 * which, 1.1), wave);
                    }
                    world.playSound(center, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
                    world.playSound(center, Sound.ENTITY_ALLAY_ITEM_GIVEN, 0.5f, 1.4f);
                    // the enemies inside are weighed down (a slow of 25% for as long as they stay)
                    double enemySlow = skill.values().getOrDefault("enemy-slow-percent", 25.0);
                    if (enemySlow > 0.0 && statusEffectService != null) {
                        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
                            if (!isValidTarget(player, entity)) continue;
                            LivingEntity enemy = (LivingEntity) entity;
                            if (enemy.getLocation().distanceSquared(center) <= radius * radius) {
                                statusEffectService.applyTimedSlow(enemy, enemySlow, 1500L, player.getUniqueId());
                            }
                        }
                    }
                    SkillFx.groundRing(player, center, radius, SkillFx.HOLY, 0.8, 1.2);
                }
                if (upgraded) {
                    updateKnockbackShield(finished ? java.util.List.of() : alliesIn(center, radius), shielded);
                }
                if (finished) {
                    // when the time runs out (not when it was re-cast) the sanctuary blooms once: a last heal, the enemies pushed out and a little confused
                    if (tick >= totalTicks && !state.ended && player.isOnline()) sanctuaryBloom(player, skill, center, radius, healPerSecond);
                    state.ended = true;
                    sanctuaries.remove(player.getUniqueId(), state);
                    cancel();
                    return;
                }
                if (tick % 5 == 0) drawSanctuary(player, world, center, radius, tick);
                tick += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    /** The end of a sanctuary that ran its time: {@code end-heal-seconds} of its healing at once, the enemies inside pushed out and given {@code end-confusion}. */
    private void sanctuaryBloom(Player caster, ClassSkillDefinition skill, Location center, double radius, double healPerSecond) {
        World world = center.getWorld();
        if (world == null) return;
        double healSeconds = skill.values().getOrDefault("end-heal-seconds", 1.0);
        double confusion = skill.values().getOrDefault("end-confusion", 40.0);
        for (Player ally : alliesIn(center, radius)) {
            attackService.healPlayerBy(caster, ally, healPerSecond * healSeconds);
            SkillFx.column(caster, ally.getLocation(), 3.0, 0.8, SkillFx.HOLY);
        }
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(center, radius, radius, radius)) {
            if (!isValidTarget(caster, entity)) continue;
            LivingEntity enemy = (LivingEntity) entity;
            Vector away = enemy.getLocation().toVector().subtract(center.toVector()).setY(0.0);
            if (away.lengthSquared() < 1.0E-6) away = new Vector(0.0, 0.0, 1.0);
            if (enemy.getLocation().distanceSquared(center) > radius * radius) continue;
            enemy.setVelocity(enemy.getVelocity().add(away.normalize().multiply(0.7).setY(0.25)));
            if (confusion > 0.0 && statusEffectService != null) statusEffectService.addConfusion(enemy, confusion, caster.getUniqueId());
        }
        world.playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.8f);
        world.playSound(center, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.7f);
        for (int ring = 1; ring <= 4; ring++) {
            double r = radius * ring / 4.0;
            int which = ring;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> SkillFx.groundRing(caster, center, r, SkillFx.HOLY, 1.0 - 0.1 * which, 1.4), ring - 1);
        }
        SkillFx.column(caster, center, 7.0, 2.2, SkillFx.HOLY);
        ParticleFx.to(world).spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(0.0, 1.0, 0.0), 50, radius * 0.4, 0.8, radius * 0.4, 0.0);
    }

    /** The class of the player changed: the sanctuary they set ends without its bloom, and the re-casts of the other skills are gone. */
    public void clearAll(Player player) {
        SanctuaryState state = sanctuaries.remove(player.getUniqueId());
        if (state != null) state.ended = true;
        meteor.forget(player.getUniqueId());
    }

    /** A sanctuary that is up (for the re-cast of the upgrade). */
    private static final class SanctuaryState {
        private final Location center;
        private final double radius;
        private final double healPerSecond;
        private int age;
        private boolean ended;

        private SanctuaryState(Location center, double radius, double healPerSecond) {
            this.center = center;
            this.radius = radius;
            this.healPerSecond = healPerSecond;
        }
    }

    private SniperMode sniperMode;

    /** The ranger's sniper mode (the re-cast of the upgraded instinct release). */
    public void setSniperMode(SniperMode sniperMode) {
        this.sniperMode = sniperMode;
    }

    private final java.util.Map<UUID, SanctuaryState> sanctuaries = new java.util.HashMap<>();

    /**
     * Pressing the key of a skill again while it is up: the upgraded sanctuary can be cast once more, a few seconds
     * after the first cast and while it lasts. The re-cast heals the allies inside as much as 4 seconds of the
     * sanctuary at once, gives every enemy inside 100 confusion, and ends the sanctuary. True when the press was
     * handled here (also when it is too early), so no cooldown or MP is spent.
     */
    public boolean tryRecast(Player player, ClassSkillDefinition skill) {
        if (INSTINCT_RELEASE.equalsIgnoreCase(skill.id()) && sniperMode != null) return sniperMode.tryEnter(player, skill);
        if (SATSUJIN.equalsIgnoreCase(skill.id())) return frenzyEcho(player, skill);
        if (METEOR.equalsIgnoreCase(skill.id())) {
            RpgItemDefinition held = attackService.weaponInMainHand(player);
            if (held == null || held.attackType() != AttackType.RANGE) return false;
            return meteor.tryRecast(player, skill.values(), held, classService.hasSkill(profileManager.getOrCreate(player), METEOR_UPGRADE));
        }
        if (!SANCTUARY.equalsIgnoreCase(skill.id())) return false;
        SanctuaryState state = sanctuaries.get(player.getUniqueId());
        if (state == null || state.ended) return false;
        if (!classService.hasSkill(profileManager.getOrCreate(player), SANCTUARY_UPGRADE)) return false;

        double delay = skill.values().getOrDefault("recast-delay-seconds", 3.0);
        if (state.age < Math.round(delay * 20.0)) {
            player.sendMessage("[iruuRPG] 聖域の再発動まで あと" + String.format(java.util.Locale.ROOT, "%.1f", delay - state.age / 20.0) + "秒");
            return true;
        }
        double burstSeconds = skill.values().getOrDefault("recast-burst-seconds", 4.0);
        double confusion = skill.values().getOrDefault("recast-confusion", 100.0);
        World world = state.center.getWorld();
        if (world == null) return true;

        state.ended = true;
        for (Player ally : alliesIn(state.center, state.radius)) {
            attackService.healPlayerBy(player, ally, state.healPerSecond * burstSeconds);
        }
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(state.center, state.radius, state.radius, state.radius)) {
            if (!isValidTarget(player, entity)) continue;
            LivingEntity enemy = (LivingEntity) entity;
            if (enemy.getLocation().distanceSquared(state.center) > state.radius * state.radius) continue;
            if (statusEffectService != null) statusEffectService.addConfusion(enemy, confusion, player.getUniqueId());
        }
        world.playSound(state.center, Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.9f);
        world.playSound(state.center, Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.5f);
        for (int ring = 1; ring <= 3; ring++) drawSanctuary(player, world, state.center, state.radius * ring / 3.0, ring * 12);
        ParticleFx.to(world).spawnParticle(Particle.VILLAGER_HAPPY, state.center.clone().add(0.0, 1.0, 0.0), 40, state.radius * 0.4, 0.8, state.radius * 0.4, 0.0);
        return true;
    }

    private java.util.List<Player> alliesIn(Location center, double radius) {
        java.util.List<Player> allies = new ArrayList<>();
        for (Player other : center.getWorld().getPlayers()) {
            if (other.isDead() || !other.isValid()) continue;
            if (other.getLocation().distanceSquared(center) <= radius * radius) allies.add(other);
        }
        return allies;
    }

    /** Knockback immunity for the players inside; removed as soon as they leave or it ends. */
    private void updateKnockbackShield(java.util.List<Player> inside, java.util.Set<UUID> shielded) {
        java.util.Set<UUID> insideIds = new java.util.HashSet<>();
        for (Player player : inside) {
            insideIds.add(player.getUniqueId());
            org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (attribute != null && shielded.add(player.getUniqueId())) {
                attribute.removeModifier(NO_KNOCKBACK_ID);
                attribute.addTransientModifier(new org.bukkit.attribute.AttributeModifier(
                        NO_KNOCKBACK_ID, "iruuRPG sanctuary", 1.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER));
            }
        }
        shielded.removeIf(id -> {
            if (insideIds.contains(id)) return false;
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                org.bukkit.attribute.AttributeInstance attribute = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_KNOCKBACK_RESISTANCE);
                if (attribute != null) attribute.removeModifier(NO_KNOCKBACK_ID);
            }
            return true;
        });
    }

    private static final Vector GROUND_X = new Vector(1.0, 0.0, 0.0);
    private static final Vector GROUND_Z = new Vector(0.0, 0.0, 1.0);

    private void drawSanctuary(Player caster, World world, Location center, double radius, int tick) {
        Player owner = ParticleFx.owner();
        // a dome of light over the sanctuary (the upper half of a turning shell), a ring at its foot and motes rising inside
        SkillFx.dome(owner, center, radius, tick, SkillFx.HOLY);
        ParticleFx.to(world).spawnParticle(Particle.VILLAGER_HAPPY, center.clone().add(0, 0.6, 0), 2, radius / 3.0, 0.3, radius / 3.0, 0.0);
        // two magic circles on the ground turning against each other
        Location ground = center.clone().add(0.0, 0.12, 0.0);
        SkillFx.circle(owner, ground, GROUND_X, GROUND_Z, radius * 0.92, tick, SkillFx.HOLY);
        SkillFx.circle(owner, ground.clone().add(0.0, 0.02, 0.0), GROUND_X, GROUND_Z, radius * 0.55, -tick, SkillFx.HOLY);
        net.tkgon.mc.iruuRPG.vfx.Fx fx = net.tkgon.mc.iruuRPG.vfx.Fx.of(owner, world, center);
        if (!fx.active()) return;
        // six thin pillars of light at the rim, brighter toward the top
        for (int index = 0; index < 6; index++) {
            double angle = Math.PI * 2.0 * index / 6.0 + tick * 0.01;
            Location base = center.clone().add(Math.cos(angle) * radius * 0.96, 0.0, Math.sin(angle) * radius * 0.96);
            fx.line(base, base.clone().add(0.0, 4.5, 0.0), SkillFx.HOLY, 0.3, 1.0, 1.5, 0.9, 0.5, null, 1);
        }
        // motes of light drifting up from the ground inside
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int mote = 0; mote < 8; mote++) {
            double angle = random.nextDouble(Math.PI * 2.0);
            double distance = Math.sqrt(random.nextDouble()) * radius * 0.9;
            fx.dust(center.getX() + Math.cos(angle) * distance, center.getY() + random.nextDouble(0.2, 2.6), center.getZ() + Math.sin(angle) * distance, SkillFx.HOLY, random.nextDouble(0.5, 1.0), 1.2);
        }
        // a halo turning over the head of the one who cast it
        if (caster.isOnline() && caster.getWorld().equals(world)) {
            Location head = caster.getLocation().add(0.0, caster.getHeight() + 0.5, 0.0);
            net.tkgon.mc.iruuRPG.vfx.Fx haloFx = net.tkgon.mc.iruuRPG.vfx.Fx.of(owner, world, head);
            haloFx.flatRing(head, 0.45, 20, SkillFx.HOLY, 0.7 + 0.3 * Math.sin(tick * 0.2), 0.0, 1.0);
        }
    }

    // ---- ice lance (mage) ---------------------------------------------------

    private static final int LANCE_TICKS = 5;

    /** Pierces everything on a straight line, slows (or freezes, when upgraded) whatever it hits. */
    private void iceLance(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        boolean upgraded = classService.hasSkill(profile, ICE_LANCE_UPGRADE);
        double range = skill.values().getOrDefault("range", 18.0);
        double width = skill.values().getOrDefault("width", 0.9);
        double rate = skill.values().getOrDefault("damage-rate", 3.5);
        double slowPercent = upgraded
                ? skill.values().getOrDefault("upgrade-slow-percent", 999.0)
                : skill.values().getOrDefault("slow-percent", 50.0);
        long slowMillis = Math.round(skill.values().getOrDefault("slow-seconds", 7.0) * 1000.0);

        World world = player.getWorld();
        Location start = player.getEyeLocation().add(0.0, -0.25, 0.0);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        RayTraceResult wall = world.rayTraceBlocks(start, direction, range, FluidCollisionMode.NEVER, true);
        double length = wall == null ? range : Math.max(1.0, start.toVector().distance(wall.getHitPosition()));

        world.playSound(start, Sound.BLOCK_GLASS_BREAK, 0.8f, 1.5f);
        world.playSound(start, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.0f, upgraded ? 0.7f : 1.1f);
        playLanceTrail(world, start, direction, length, upgraded);

        UUID sourceId = player.getUniqueId();
        for (LivingEntity victim : entitiesOnLine(player, start, direction, length, width)) {
            AttackService.AttackDamage attack = attackService.calculateDamage(player, victim, weapon);
            double damage = Math.round(attack.result().damage() * rate * 10.0) / 10.0;
            boolean critical = attack.result().critical();
            if (!attackService.applyRangeDamage(player, victim, damage, weapon, critical)) continue;

            attackService.sendDamageDebug(player, AttackType.RANGE, victim, damage, critical, false);
            if (statusEffectService != null && victim.isValid() && !victim.isDead()) {
                statusEffectService.applyTimedSlow(victim, slowPercent, slowMillis, sourceId);
            }
            playLanceImpact(victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0), upgraded);
        }
        // where the lance ends it shatters: the enemies round that point (and not already hit) take a share of it and are slowed
        double shatterRadius = skill.values().getOrDefault("shatter-radius", 3.0);
        double shatterShare = skill.values().getOrDefault("shatter-share", 0.3);
        if (shatterRadius > 0.0 && shatterShare > 0.0) {
            Location end = start.clone().add(direction.clone().multiply(length));
            List<LivingEntity> already = entitiesOnLine(player, start, direction, length, width);
            SkillFx.burst(player, end, shatterRadius, SkillFx.ICE);
            SkillFx.groundRing(player, end, shatterRadius, SkillFx.ICE, 0.8, 1.2);
            world.playSound(end, Sound.BLOCK_GLASS_BREAK, 1.0f, 0.7f);
            for (Entity entity : world.getNearbyEntities(end, shatterRadius, shatterRadius, shatterRadius)) {
                if (!isValidTarget(player, entity)) continue;
                LivingEntity near = (LivingEntity) entity;
                if (already.contains(near) || near.getLocation().distanceSquared(end) > shatterRadius * shatterRadius) continue;
                AttackService.AttackDamage attack = attackService.calculateDamage(player, near, weapon);
                double damage = Math.round(attack.result().damage() * rate * shatterShare * 10.0) / 10.0;
                if (attackService.applyRangeDamage(player, near, damage, weapon, false) && statusEffectService != null && near.isValid() && !near.isDead()) {
                    statusEffectService.applyTimedSlow(near, slowPercent, slowMillis / 2, sourceId);
                }
            }
        }
    }

    private List<LivingEntity> entitiesOnLine(Player player, Location start, Vector direction, double length, double width) {
        Location mid = start.clone().add(direction.clone().multiply(length / 2.0));
        double half = length / 2.0 + width + 2.0;
        List<LivingEntity> hits = new ArrayList<>();
        for (Entity entity : start.getWorld().getNearbyEntities(mid, half, half, half)) {
            if (!isValidTarget(player, entity)) continue;

            BoundingBox box = entity.getBoundingBox().expand(width / 2.0);
            if (box.rayTrace(start.toVector(), direction, length) != null) {
                hits.add((LivingEntity) entity);
            }
        }
        hits.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(start)));
        return hits;
    }

    /** The spear flies out over a few ticks: two turning strands of ice round a bright axis (the color deep blue at the tail, white at the tip), frost in the air. */
    private void playLanceTrail(World world, Location start, Vector direction, double length, boolean upgraded) {
        Player fxOwner = ParticleFx.owner();
        new SafeTask(plugin) {
            private int tick;

            @Override
            protected void tick() {
                ParticleFx.mark(fxOwner);
                if (tick >= LANCE_TICKS) {
                    cancel();
                    return;
                }
                double from = length * tick / LANCE_TICKS;
                double to = length * (tick + 1) / LANCE_TICKS;
                SkillFx.helix(fxOwner, start, direction, from, to, length, upgraded ? 0.36 : 0.26, SkillFx.ICE);
                for (double distance = from; distance < to; distance += 0.8) {
                    if (ThreadLocalRandom.current().nextInt(2) == 0) {
                        Location point = start.clone().add(direction.clone().multiply(distance));
                        ParticleFx.to(world).spawnParticle(Particle.SNOWFLAKE, point, 1, 0.15, 0.15, 0.15, 0.01, null, true);
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void playLanceImpact(Location center, boolean upgraded) {
        World world = center.getWorld();
        if (world == null) return;

        world.playSound(center, Sound.BLOCK_GLASS_BREAK, 0.9f, 1.1f);
        ParticleFx.to(world).spawnParticle(Particle.SNOWFLAKE, center, upgraded ? 40 : 18, 0.4, 0.5, 0.4, 0.05, null, true);
        SkillFx.burst(ParticleFx.owner(), center, upgraded ? 2.2 : 1.6, SkillFx.ICE);
        if (upgraded) {
            ParticleFx.to(world).spawnParticle(Particle.BLOCK_CRACK, center, 30, 0.4, 0.5, 0.4, 0.0,
                    org.bukkit.Material.BLUE_ICE.createBlockData());
        }
    }

    public record Precheck(boolean hasEffect, String rejection, LivingEntity target, RpgItemDefinition weapon) {

        static Precheck noEffect() {
            return new Precheck(false, null, null, null);
        }

        static Precheck rejected(String message) {
            return new Precheck(true, message == null ? "" : message, null, null);
        }

        static Precheck ready(LivingEntity target, RpgItemDefinition weapon) {
            return new Precheck(true, null, target, weapon);
        }

        public boolean rejected() {
            return rejection != null;
        }
    }
}
