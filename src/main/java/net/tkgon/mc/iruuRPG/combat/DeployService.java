package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.ItemSkillType;
import net.tkgon.mc.iruuRPG.item.ItemSkillRegistry;
import net.tkgon.mc.iruuRPG.item.ItemSkillVariant;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class DeployService {

    public static final String DEPLOY_ENTITY_TAG = "iruurpg_deploy";
    private static final double DEPLOY_SLASH_SPEED = 0.62;
    private static final double DEPLOY_SLASH_HIT_RADIUS = 0.85;
    private static final int DEPLOY_SLASH_MAX_TICKS = 45;

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final AttackEffects attackEffects;
    private final ItemSkillRegistry skillRegistry;

    public DeployService(JavaPlugin plugin, AttackService attackService, AttackEffects attackEffects, ItemSkillRegistry skillRegistry) {
        this.plugin = plugin;
        this.attackService = attackService;
        this.attackEffects = attackEffects;
        this.skillRegistry = skillRegistry;
    }

    public void deploy(Player owner, RpgItemDefinition weapon) {
        PlayerProfile snapshot = attackService.snapshotAttacker(owner);
        ItemSkillVariant deploySkill = skill(weapon);
        Location center = deployLocation(owner);
        ArmorStand stand = owner.getWorld().spawn(center, ArmorStand.class, spawned -> configureStand(spawned, weapon));

        int durationTicks = durationTicks(snapshot, deploySkill);
        int attackPeriodTicks = attackPeriodTicks(snapshot, deploySkill);
        double radius = deploySkill.value("radius", plugin.getConfig().getDouble("combat.deploy.radius", 6.0));
        new ActiveDeploy(owner, stand, weapon, deploySkill, snapshot, center, radius, durationTicks, attackPeriodTicks).start();
    }

    public static boolean isDeployEntity(Entity entity) {
        return entity != null && entity.getScoreboardTags().contains(DEPLOY_ENTITY_TAG);
    }

    private void configureStand(ArmorStand stand, RpgItemDefinition weapon) {
        stand.addScoreboardTag(DEPLOY_ENTITY_TAG);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setSilent(true);
        stand.setSmall(true);
        stand.setBasePlate(false);
        stand.setArms(false);
        stand.setMarker(true);
        stand.setInvulnerable(true);
        stand.setCollidable(false);
        stand.setPersistent(false);
        stand.setRemoveWhenFarAway(true);

        EntityEquipment equipment = stand.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(blockFor(weapon.element())));
        }
    }

    private Location deployLocation(Player owner) {
        Vector direction = owner.getLocation().getDirection();
        direction.setY(0.0);
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = new Vector(0.0, 0.0, 1.0);
        } else {
            direction.normalize();
        }

        double distance = plugin.getConfig().getDouble("combat.deploy.spawn-distance", 3.0);
        double height = plugin.getConfig().getDouble("combat.deploy.height", 1.4);
        Location location = owner.getLocation().add(direction.multiply(distance)).add(0.0, height, 0.0);
        location.setPitch(0.0f);
        return location;
    }

    private int durationTicks(PlayerProfile snapshot, ItemSkillVariant deploySkill) {
        double baseTicks = deploySkill.hasDuration()
                ? deploySkill.durationTicks(200)
                : plugin.getConfig().getDouble("combat.deploy.duration-ticks", 200.0);
        double durationStat = snapshot.finalStats().get(StatType.DURATION);
        double multiplier = Math.max(0.1, 1.0 + durationStat / 100.0);
        int minTicks = Math.max(1, plugin.getConfig().getInt("combat.deploy.min-duration-ticks", 20));
        return Math.max(minTicks, (int) Math.round(baseTicks * multiplier));
    }

    private int attackPeriodTicks(PlayerProfile snapshot, ItemSkillVariant deploySkill) {
        double baseTicks = deploySkill.value("attack-period-ticks", plugin.getConfig().getDouble("combat.deploy.attack-period-ticks", 40.0));
        double attackSpeed = snapshot.finalStats().get(StatType.ATTACK_SPEED);
        double multiplier = Math.max(0.1, 1.0 + attackSpeed / 100.0);
        int minTicks = Math.max(1, plugin.getConfig().getInt("combat.deploy.min-attack-period-ticks", 6));
        return Math.max(minTicks, (int) Math.round(baseTicks / multiplier));
    }

    private ItemSkillVariant skill(RpgItemDefinition weapon) {
        return skillRegistry.variant(weapon.skill(), AttackType.DEPLOY);
    }

    private Material blockFor(Element element) {
        return switch (element) {
            case RED -> Material.REDSTONE_BLOCK;
            case BLUE -> Material.LAPIS_BLOCK;
            case WHITE -> Material.SEA_LANTERN;
            case GREEN -> Material.EMERALD_BLOCK;
            case ORANGE -> Material.HONEYCOMB_BLOCK;
        };
    }

    private final class ActiveDeploy {

        private final Player owner;
        private final ArmorStand stand;
        private final RpgItemDefinition weapon;
        private final ItemSkillVariant deploySkill;
        private final PlayerProfile snapshot;
        private final Location center;
        private final double radius;
        private final double radiusSquared;
        private final int durationTicks;
        private final int attackPeriodTicks;
        private final int ringPeriodTicks;
        private BukkitTask task;
        private int ageTicks;
        private boolean strikeArmed;
        private int strikeTriggerTick = -1;

        private ActiveDeploy(
                Player owner,
                ArmorStand stand,
                RpgItemDefinition weapon,
                ItemSkillVariant deploySkill,
                PlayerProfile snapshot,
                Location center,
                double radius,
                int durationTicks,
                int attackPeriodTicks
        ) {
            this.owner = owner;
            this.stand = stand;
            this.weapon = weapon;
            this.deploySkill = deploySkill;
            this.snapshot = snapshot;
            this.center = center.clone();
            this.radius = radius;
            this.radiusSquared = radius * radius;
            this.durationTicks = durationTicks;
            this.attackPeriodTicks = attackPeriodTicks;
            this.ringPeriodTicks = Math.min(2, Math.max(1, plugin.getConfig().getInt("combat.effects.deploy-ring.period-ticks", 2)));
        }

        private void start() {
            this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 0L, 1L);
        }

        private void tick() {
            if (!owner.isOnline() || !stand.isValid()) {
                cleanup(false);
                return;
            }
            if (ageTicks >= durationTicks) {
                cleanup(true);
                return;
            }

            updateStand();
            if (ageTicks % ringPeriodTicks == 0) {
                attackEffects.playDeployRing(center, weapon, radius);
            }

            if (weapon.skill() == ItemSkillType.STRIKE) {
                handleStrikeTrap();
            } else if (ageTicks % attackPeriodTicks == 0) {
                pulseDamage();
            }

            ageTicks++;
        }

        private void updateStand() {
            double amplitude = plugin.getConfig().getDouble("combat.deploy.bob-amplitude", 0.25);
            double period = Math.max(1.0, plugin.getConfig().getDouble("combat.deploy.bob-period-ticks", 40.0));
            double y = Math.sin(Math.PI * 2.0 * ageTicks / period) * amplitude;
            float yaw = (float) (center.getYaw() + ageTicks * plugin.getConfig().getDouble("combat.deploy.rotation-degrees-per-tick", 4.0));

            Location next = center.clone().add(0.0, y, 0.0);
            next.setYaw(yaw);
            next.setPitch(0.0f);
            stand.teleport(next);
        }

        private void pulseDamage() {
            World world = center.getWorld();
            if (world == null) return;

            if (weapon.skill() == ItemSkillType.HEAL) {
                pulseHeal();
                return;
            }
            if (weapon.skill() == ItemSkillType.SLASH) {
                launchDeploySlash();
                return;
            }

            boolean sweep = weapon.skill() == ItemSkillType.SWEEP && ThreadLocalRandom.current().nextDouble() < deploySkill.value("chance", 0.33);
            if (sweep) {
                attackEffects.playDeploySweep(center, weapon, radius);
            } else {
                attackEffects.playDeployPulse(center, weapon, radius);
            }

            for (LivingEntity target : targetsInRange()) {
                AttackService.AttackDamage attack = attackService.calculateDamage(snapshot, target, weapon);
                double damage = sweep ? round(attack.result().damage() * deploySkill.value("damage-rate", 1.2)) : attack.result().damage();
                if (attackService.applyDirectDamage(owner, target, damage, weapon, attack.result().critical())) {
                    attackService.sendDamageDebug(owner, AttackType.DEPLOY, target, damage, attack.result().critical(), false);
                }
            }
        }

        private void pulseHeal() {
            World world = center.getWorld();
            if (world == null) return;

            attackEffects.playHealRing(center, weapon, radius);
            double amount = round(healAmount());
            if (amount <= 0.0) return;

            Set<UUID> healed = new HashSet<>();
            for (Player target : Bukkit.getOnlinePlayers()) {
                healDeployPlayer(target, amount, healed);
            }
        }

        private double healAmount() {
            double damage = attackService.calculateNeutralDamage(snapshot, weapon).damage();
            if (damage <= 0.0) {
                damage = Math.max(
                        snapshot.finalStats().get(StatType.WEAPON_DAMAGE),
                        snapshot.finalStats().get(StatType.SPECIAL_DAMAGE)
                );
            }
            return damage * deploySkill.value("heal-rate", 0.10);
        }

        private void healDeployPlayer(Player target, double amount, Set<UUID> healed) {
            if (!target.isOnline() || target.isDead() || !target.isValid()) return;
            if (!healed.add(target.getUniqueId())) return;
            if (!target.getWorld().equals(center.getWorld())) return;
            if (target.getLocation().distanceSquared(center) > radiusSquared) return;

            if (attackService.healPlayerBy(owner, target, amount)) {
                attackEffects.playHealBurst(target, weapon);
            }
        }

        private void launchDeploySlash() {
            Location start = center.clone().add(0.0, 0.75, 0.0);
            if (nearestTargetFrom(start) == null) return;
            double speed = deploySkill.value("slash-speed", DEPLOY_SLASH_SPEED);
            double hitRadius = deploySkill.value("hit-radius", DEPLOY_SLASH_HIT_RADIUS);
            int maxTicks = deploySkill.intValue("max-ticks", DEPLOY_SLASH_MAX_TICKS);

            new BukkitRunnable() {
                private final Location point = start.clone();
                private int ticks;

                @Override
                public void run() {
                    if (!owner.isOnline() || !stand.isValid() || ticks >= maxTicks) {
                        cancel();
                        return;
                    }

                    LivingEntity target = nearestTargetFrom(point);
                    if (target == null) {
                        cancel();
                        return;
                    }

                    Location targetCenter = target.getLocation().add(0.0, Math.max(0.65, target.getHeight() * 0.55), 0.0);
                    Vector direction = targetCenter.toVector().subtract(point.toVector());
                    double distance = direction.length();
                    if (distance <= hitRadius) {
                        attackEffects.playSlashImpact(target, weapon);
                        AttackService.AttackDamage attack = attackService.calculateDamage(snapshot, target, weapon);
                        double damage = attack.result().damage();
                        if (attackService.applyDirectDamage(owner, target, damage, weapon, attack.result().critical())) {
                            attackService.sendDamageDebug(owner, AttackType.DEPLOY, target, damage, attack.result().critical(), false);
                        }
                        cancel();
                        return;
                    }

                    if (distance > 1.0E-9) {
                        direction.normalize();
                        point.add(direction.clone().multiply(Math.min(speed, distance)));
                        attackEffects.playSlashProjectile(point, direction, weapon);
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);
        }

        private void handleStrikeTrap() {
            if (!strikeArmed) {
                if (targetsInRange().isEmpty()) return;

                strikeArmed = true;
                strikeTriggerTick = ageTicks + deploySkill.intValue("trigger-delay-ticks", 20);
                attackEffects.playDeployPulse(center, weapon, radius);
                return;
            }

            if (ageTicks < strikeTriggerTick) {
                if (ageTicks % 4 == 0) {
                    attackEffects.playDeployPulse(center, weapon, radius);
                }
                return;
            }

            triggerStrikeTrap();
            cleanup(false);
        }

        private void triggerStrikeTrap() {
            List<LivingEntity> targets = targetsInRange();
            attackEffects.playDeployStrikeCrush(center, weapon, radius);
            for (LivingEntity target : targets) {
                AttackService.AttackDamage attack = attackService.calculateDamage(snapshot, target, weapon);
                double damage = round(attack.result().damage() * deploySkill.value("damage-rate", 15.0));
                if (attackService.applyDirectDamage(owner, target, damage, weapon, attack.result().critical())) {
                    attackService.sendDamageDebug(owner, AttackType.DEPLOY, target, damage, attack.result().critical(), false);
                }
            }
        }

        private void finishDashDeploy() {
            attackEffects.playDeployDashFinish(center, weapon, radius);
            for (LivingEntity target : targetsInRange()) {
                Vector direction = center.toVector().subtract(target.getLocation().toVector());
                direction.setY(0.0);
                if (direction.lengthSquared() <= 1.0E-9) {
                    direction = new Vector(0.0, 0.0, 1.0);
                } else {
                    direction.normalize();
                }
                target.setVelocity(direction.multiply(deploySkill.value("pull-strength", 1.15)).setY(deploySkill.value("up-velocity", 0.35)));

                AttackService.AttackDamage attack = attackService.calculateDamage(snapshot, target, weapon);
                double damage = round(attack.result().damage() * deploySkill.value("damage-rate", 1.7));
                if (attackService.applyDirectDamage(owner, target, damage, weapon, attack.result().critical())) {
                    attackService.sendDamageDebug(owner, AttackType.DEPLOY, target, damage, attack.result().critical(), false);
                }
            }
        }

        private List<LivingEntity> targetsInRange() {
            List<LivingEntity> targets = new ArrayList<>();
            World world = center.getWorld();
            if (world == null) return targets;

            for (Entity nearby : world.getNearbyEntities(center, radius, radius, radius)) {
                if (!(nearby instanceof LivingEntity target)) continue;
                if (target.equals(stand) || target.equals(owner) || target instanceof ArmorStand || target instanceof Player) continue;
                if (DeployService.isDeployEntity(target)) continue;
                if (target.isDead() || !target.isValid()) continue;
                if (target.getLocation().distanceSquared(center) > radiusSquared) continue;

                targets.add(target);
            }
            return targets;
        }

        private LivingEntity nearestTargetFrom(Location point) {
            LivingEntity nearest = null;
            double nearestDistanceSquared = Double.MAX_VALUE;
            for (LivingEntity target : targetsInRange()) {
                double distanceSquared = target.getLocation().distanceSquared(point);
                if (distanceSquared >= nearestDistanceSquared) continue;

                nearest = target;
                nearestDistanceSquared = distanceSquared;
            }
            return nearest;
        }

        private double round(double value) {
            return Math.floor(value * 10.0 + 0.5) / 10.0;
        }

        private void cleanup(boolean expired) {
            if (expired && weapon.skill() == ItemSkillType.DASH && owner.isOnline()) {
                finishDashDeploy();
            }
            if (task != null) {
                task.cancel();
            }
            if (stand.isValid()) {
                stand.remove();
            }
        }
    }
}
