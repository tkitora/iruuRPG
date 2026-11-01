package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import net.tkgon.mc.iruuRPG.item.ClassUse;
import net.tkgon.mc.iruuRPG.item.ItemIdentifier;
import net.tkgon.mc.iruuRPG.item.ItemSkillType;
import net.tkgon.mc.iruuRPG.item.ItemVisualOptions;
import net.tkgon.mc.iruuRPG.item.Rarity;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemRegistry;
import net.tkgon.mc.iruuRPG.mob.DebugTargetService;
import net.tkgon.mc.iruuRPG.mob.RpgMobService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import net.tkgon.mc.iruuRPG.stat.Element;
import net.tkgon.mc.iruuRPG.stat.ElementStatSet;
import net.tkgon.mc.iruuRPG.stat.StatSet;
import net.tkgon.mc.iruuRPG.stat.StatType;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

public final class AttackService {

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final EquipmentService equipmentService;
    private final ItemIdentifier itemIdentifier;
    private final RpgItemRegistry itemRegistry;
    private final DamageCalculator damageCalculator;
    private final AttackCooldowns attackCooldowns;
    private final AttackEffects attackEffects;
    private static final String MELEE_AREA_SKILL = "melee_area";
    private static final String RANGE_AREA_EXPAND_SKILL = "range_area_expand";

    private final PlayerBars playerBars;
    private final StatusEffectService statusEffectService;
    private RpgMobService mobService;
    private ClassService classService;
    private TutorialDamageHook tutorialHook;
    private static final double TUTORIAL_DAMAGE_CAP = 99.0;
    private ClassEffectService classEffectService;
    private DebugTargetService debugTargetService;
    private java.util.function.BiConsumer<Player, String> stackHook;

    /** Called with (player, "hit" | "damaged") when something a weapon's stacks can grow from happens. */
    public void setStackHook(java.util.function.BiConsumer<Player, String> stackHook) {
        this.stackHook = stackHook;
    }

    private void stackTrigger(Player player, String trigger) {
        if (stackHook != null) stackHook.accept(player, trigger);
    }

    /** The players whose attack now going on has not given its weapon a "hit" stack yet (see {@link #beginAttack}). */
    private final java.util.Set<UUID> hitStackOpen = new java.util.HashSet<>();

    /**
     * A normal attack of the player landed on an enemy. A weapon's stacks grow once for each attack (a swing, a shot, a
     * step of a combo), however many enemies or how many times it hits.
     */
    public void normalAttackHit(Player attacker) {
        if (attacker == null || !hitStackOpen.remove(attacker.getUniqueId())) return;
        stackTrigger(attacker, "hit");
    }

    public AttackService(
            JavaPlugin plugin,
            PlayerProfileManager profileManager,
            EquipmentService equipmentService,
            ItemIdentifier itemIdentifier,
            RpgItemRegistry itemRegistry,
            DamageCalculator damageCalculator,
            AttackCooldowns attackCooldowns,
            AttackEffects attackEffects,
            PlayerBars playerBars,
            StatusEffectService statusEffectService
    ) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.equipmentService = equipmentService;
        this.itemIdentifier = itemIdentifier;
        this.itemRegistry = itemRegistry;
        this.damageCalculator = damageCalculator;
        this.attackCooldowns = attackCooldowns;
        this.attackEffects = attackEffects;
        this.playerBars = playerBars;
        this.statusEffectService = statusEffectService;
        this.meleeCombo = new MeleeCombo(plugin);
    }

    private final MeleeCombo meleeCombo;
    private final DamageMeter damageMeter = new DamageMeter();

    public DamageMeter damageMeter() {
        return damageMeter;
    }

    /**
     * How many normal attacks into the combo of {@code type} the player is (0 when the chain is not running): the
     * melee chain for MELEE, the mage's ranged chain for RANGE. The two are counted apart.
     */
    public int comboStage(Player attacker, AttackType type) {
        if (type == AttackType.RANGE && isMage(attacker)) return meleeCombo.currentStage(attacker, MeleeCombo.MAGE);
        // the generic combos (see ClassCombo): the chain of the class's combo with this kind of weapon
        ClassCombo combo = classComboFor(attacker, weaponInMainHand(attacker));
        if (combo != null && combo.type() == type) return meleeCombo.currentStage(attacker, combo.spec().plan());
        if (type == AttackType.MELEE) return meleeCombo.currentStage(attacker, MeleeCombo.USUAL);
        return 0;
    }

    /**
     * The next melee attack of the light / heavy chain ({@code heavy}: its heavy attack, chosen by the buttons). Call it once per attack that
     * got through the cooldown; a heavy attack or the finisher also lengthens the cooldown (its recovery).
     */
    public MeleeCombo.Step nextMeleeStep(Player attacker, boolean heavy) {
        // a weapon that is not yellow to the class is used with ordinary attacks (no combo)
        if (!comboAllowed(attacker, weaponInMainHand(attacker))) {
            beginAttack(attacker);
            return MeleeCombo.Step.PLAIN;
        }
        MeleeCombo.Step step = heavy ? meleeCombo.usualHeavy(attacker) : meleeCombo.next(attacker, MeleeCombo.USUAL);
        attackCooldowns.extend(attacker, AttackType.MELEE, step.recoveryTicks());
        beginAttack(attacker);
        return step;
    }

    private boolean hasClassId(Player attacker, String classId) {
        if (classService == null) return false;
        return classService.currentClass(profileManager.getOrCreate(attacker)).map(definition -> definition.id().equals(classId)).orElse(false);
    }

    private final Map<UUID, Integer> lastHeavyClickTick = new HashMap<>();
    /** The client repeats a held right click every 4 ticks. */
    private static final int HELD_CLICK_TICKS = 5;

    /**
     * Swings the off-hand arm. The client is already swinging its main arm for the click and ignores a swing until that
     * one is half over, so the off-hand swing is sent a few ticks after the click.
     */
    public void swingOffHand(Player player) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && !player.isDead()) {
                ClickSignals.offSwingSent(player);
                player.swingOffHand();
            }
        }, 3L);
    }
    private MageMoves mageMoves;

    public void setMageMoves(MageMoves mageMoves) {
        this.mageMoves = mageMoves;
        meleeCombo.register(MageMoves.SPEC);
    }

    public boolean isMage(Player attacker) {
        return hasClassId(attacker, "mage");
    }

    /** Which attack type's gauge shows the step of the combo: the ranged one for the mage, else the melee one. */
    public AttackType comboGaugeType(Player attacker) {
        return isMage(attacker) ? AttackType.RANGE : AttackType.MELEE;
    }

    /**
     * The normal attack (a right click) of a mage with a ranged weapon: the next step of the mage's combo. True when
     * it was handled (the cooldown of the normal attack was already spent).
     */
    /**
     * True when this right click is a button that is being held: the client sends the click again every 4 ticks, and a
     * normal attack of a combo is not a stream of them. A click within 5 ticks of the one before (also an ignored one)
     * is held; the first of a series, and a click that comes after a pause, are not.
     */
    public boolean heldRangedClick(Player attacker) {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastRangedClickTick.put(attacker.getUniqueId(), tick);
        // combat.hold-right-click (default true): a held right button keeps attacking, at the pace of the cooldowns
        if (holdRightClick()) return false;
        return last != null && tick - last <= HELD_CLICK_TICKS;
    }

    /**
     * Whether a held right button repeats the attacks the right click makes (the ranged / deployed normal attack, the
     * melee weapon's heavy attack): the client sends the click again every 4 ticks, and each one is a press as long as the
     * attack's own cooldown is over. Off: a held button is one click (the first of a series).
     */
    public boolean holdRightClick() {
        return plugin.getConfig().getBoolean("combat.hold-right-click", true);
    }

    private final Map<UUID, Integer> lastRangedClickTick = new HashMap<>();

    public boolean mageNormal(Player attacker, RpgItemDefinition weapon) {
        if (mageMoves == null || !isMage(attacker) || weapon.attackType() != AttackType.RANGE) return false;
        if (!plugin.getConfig().getBoolean("combat.combo.enabled", true) || !comboAllowed(attacker, weapon)) return false;
        MeleeCombo.Step step = meleeCombo.next(attacker, MeleeCombo.MAGE);
        beginAttack(attacker);
        // the delay a combo adds to a normal attack is small: the weapon's own cooldown and a few ticks (2 - 4)
        int base = (int) Math.round(attackCooldowns.totalMillis(attacker, AttackType.RANGE) / 50.0);
        attackCooldowns.ensureAtLeast(attacker, AttackType.RANGE, base + (int) plugin.getConfig().getLong("combat.combo.mage.normal-gap-extra-ticks", 3L));
        mageMoves.normal(attacker, weapon, step);
        return true;
    }

    /** True when the mage's ranged combo comes with this weapon (its heavy attacks are the mage's own, {@link MageMoves}). */
    private boolean mageRanged(Player attacker, RpgItemDefinition weapon) {
        return mageMoves != null && weapon != null && weapon.isWeaponLike() && weapon.attackType() == AttackType.RANGE && isMage(attacker)
                && plugin.getConfig().getBoolean("combat.combo.enabled", true) && comboAllowed(attacker, weapon);
    }

    /**
     * Heavy attack {@code index} (0 - 5) of a mage with a ranged weapon (強1 re-activates what the last one left); {@code free}: thrown as a class's
     * skill, without its own cooldown. False when it was not thrown (cooling down).
     */
    private boolean mageHeavy(Player attacker, RpgItemDefinition weapon, int index, boolean free) {
        MeleeCombo.Step step = meleeCombo.heavyByInput(attacker, MeleeCombo.MAGE, index, free || index == 0 && mageMoves.chainActive(attacker));
        if (step == null) {
            coolingClick(attacker);
            return false;
        }
        beginAttack(attacker);
        if (!free) showHeavyCooldown(attacker, weapon, MeleeCombo.MAGE, step);
        mageMoves.heavy(attacker, weapon, step);
        return true;
    }

    // ---- the combos of a class with a kind of weapon (the generic ones: see ClassCombo) ------------------------------------------

    private final Map<String, ClassCombo> classCombos = new HashMap<>();
    private MoveKit.Zones comboZones;

    /** Adds the combo of a class with a kind of weapon (its plan's numbers are registered with the chain). */
    public void registerCombo(ClassCombo combo) {
        if (comboZones == null) comboZones = new MoveKit.Zones(plugin);
        classCombos.put(combo.classId() + "|" + combo.type(), combo);
        meleeCombo.register(combo.spec());
    }

    public void shutdownCombos() {
        if (deployHold != null) deployHold.shutdown();
        AssassinClones.shutdown();
        if (comboZones != null) comboZones.shutdown();
    }

    /**
     * The class of the player changed: whatever the old class left standing ends at once: the zones and placements of the combos, the clones, the shields and guards,
     * the chain and the heavy cooldowns, what a heavy attack of the mage could trigger again, and the grey overlays of the weapons.
     */
    public void clearClassState(Player player) {
        UUID id = player.getUniqueId();
        forgetComboZones(id);
        if (deployHold != null) deployHold.forget(id);
        meleeCombo.forget(id);
        AssassinClones.clearAll(player);
        if (guard != null) guard.forget(id);
        if (mageMoves != null) mageMoves.forget(id);
        swapDashReady.remove(id);
        MageMeleeCombo.forget(id);
        RangerBuffs.forget(id);
        for (Material material : Material.values()) {
            if (material.isItem() && player.hasCooldown(material)) player.setCooldown(material, 0);
        }
    }

    public void forgetComboZones(UUID player) {
        if (comboZones != null) comboZones.clear(player);
    }

    /**
     * The combo this weapon gives the player: the class's own combo with this kind of weapon when the weapon is a favorite of the class, the basic combo for the kind of weapon
     * (sword, staff, turret: the same for every class) when it is only usable, nothing when it is not usable.
     */
    private ClassCombo classComboFor(Player attacker, RpgItemDefinition weapon) {
        if (weapon == null || !weapon.marked() || classCombos.isEmpty()) return null;
        String classId = equipmentService.classIdOf(attacker);
        ClassUse use = weapon.classUse(classId);
        if (use == ClassUse.FAVORITE) return classCombos.get(classId + "|" + weapon.attackType());
        if (use == ClassUse.USABLE) return classCombos.get("*|" + weapon.attackType());
        return null;
    }

    /** The internal id of the player's class (gifted, healer, mage, warrior, assassin; "" when none). */
    public String classIdOf(Player player) {
        return equipmentService.classIdOf(player);
    }

    /** True when the weapon in hand brings a (generic) combo of the player's class: a favorite weapon with such a combo. */
    public boolean hasClassCombo(Player attacker, RpgItemDefinition weapon) {
        return classComboFor(attacker, weapon) != null && comboOn();
    }

    /** Where each player's running combo began, and the way they faced (placed things are put round that place). */
    private final Map<UUID, Object[]> chainOrigins = new HashMap<>();

    // ---- the placement of a deployed weapon's combo (see DeployHold) -------------------------------------------------------------------

    private DeployHold deployHold;

    private DeployHold holds() {
        if (deployHold == null) deployHold = new DeployHold(plugin);
        return deployHold;
    }

    /** The cooldown after a placement, in ticks: 10 s ({@code combat.combo.deploy-hold.cooldown-seconds}), shortened by a cooldown reduction (there is no such stat yet: 0). */
    public int deployCooldownTicks(Player player) {
        double seconds = plugin.getConfig().getDouble("combat.combo.deploy-hold.cooldown-seconds", 10.0);
        return (int) Math.max(1, Math.round(seconds * 20.0 * (1.0 - Math.max(0.0, Math.min(0.8, cooldownReduction(player))))));
    }

    /** A share (0 - 0.8) the cooldowns are shorter by. Nothing gives one yet; this is where it will be read from. */
    private double cooldownReduction(Player player) {
        return 0.0;
    }

    /** How long a placement stays, in ticks: 10 s ({@code combat.combo.deploy-hold.life-seconds}) lengthened by the effect time stat (a share, like the skills'). */
    private int deployLifeTicks(Player player) {
        double seconds = plugin.getConfig().getDouble("combat.combo.deploy-hold.life-seconds", 10.0);
        double duration = equipmentService.recalculate(player).finalStats().get(StatType.DURATION);
        return (int) Math.max(20, Math.round(seconds * 20.0 * Math.max(0.1, 1.0 + duration / 100.0)));
    }

    /**
     * The right click with a deployed weapon of a class that has a combo with it: the first one sets the placement here (and starts the cooldown, with the item's grey overlay);
     * each one makes the next attack of the combo at that place, 1 to 5 and then 1 again. Nothing new can be set while the cooldown runs.
     */
    public void deployClick(Player attacker, RpgItemDefinition weapon) {
        if (!hasClassCombo(attacker, weapon)) return;
        ClassCombo combo = classComboFor(attacker, weapon);
        DeployHold.Hold hold = holds().active(attacker);
        if (hold == null) {
            long left = holds().cooldownMillis(attacker);
            if (left > 0L) {
                attacker.sendMessage("[iruuRPG] 設置のクールタイム中です (あと" + String.format(java.util.Locale.ROOT, "%.1f", left / 1000.0) + "秒)。");
                return;
            }
            org.bukkit.util.Vector facing = attacker.getLocation().getDirection().setY(0.0);
            if (facing.lengthSquared() < 1.0E-6) facing = new org.bukkit.util.Vector(0.0, 0.0, 1.0);
            int cooldown = deployCooldownTicks(attacker);
            Location spot = attacker.getLocation().clone();
            hold = holds().create(attacker, spot, facing.normalize(), deployLifeTicks(attacker), cooldown, kit(attacker, weapon).palette());
            showItemCooldown(attacker, AttackType.DEPLOY, cooldown);
            attacker.getWorld().playSound(spot, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.8f);
        }
        hold.stage = hold.stage >= 5 ? 1 : hold.stage + 1;
        MeleeCombo.Step step = meleeCombo.stageStep(combo.spec().plan(), hold.stage);
        beginAttack(attacker);
        combo.normal(kitAt(attacker, weapon, hold.spot, hold.facing), step);
    }

    /** Shift + Q with a deployed weapon of a class that has a combo with it: the placement is taken away (its cooldown stays). True when the weapon has one. */
    public boolean removePlacement(Player attacker, RpgItemDefinition weapon) {
        if (weapon == null || weapon.attackType() != AttackType.DEPLOY || !hasClassCombo(attacker, weapon)) return false;
        boolean removed = holds().remove(attacker);
        // (what was set down at the placement goes with it)
        if (removed && comboZones != null) comboZones.clear(attacker.getUniqueId());
        attacker.sendMessage(removed ? "[iruuRPG] 設置を削除しました (置いた罠も消え、クールタイムはそのままです)。" : "[iruuRPG] 削除する設置がありません。");
        return true;
    }

    /**
     * Heavy attack {@code index} (0 - 4) of a deployed weapon of a class that has a combo with it: made at the placement, once for each placement.
     */
    /** Heavy attack {@code index} of a deployed weapon's combo, once per placement: false when there is no placement, or its heavy attack was used. */
    private boolean deployHeavy(Player attacker, RpgItemDefinition weapon, int index) {
        DeployHold.Hold hold = holds().active(attacker);
        if (hold == null) {
            attacker.sendMessage("[iruuRPG] 設置がありません。右クリックで設置してください。");
            return false;
        }
        if (hold.heavyUsed) {
            attacker.sendMessage("[iruuRPG] この設置のスキルはもう使いました。");
            return false;
        }
        hold.heavyUsed = true;
        ClassCombo combo = classComboFor(attacker, weapon);
        MeleeCombo.Step step = meleeCombo.heavyStep(combo.spec().plan(), index);
        beginAttack(attacker);
        combo.heavy(kitAt(attacker, weapon, hold.spot, hold.facing), step);
        return true;
    }

    public void forgetDeployHold(UUID player) {
        holds().forget(player);
    }

    private MoveKit kitAt(Player attacker, RpgItemDefinition weapon, Location origin, org.bukkit.util.Vector facing) {
        MoveKit kit = new MoveKit(plugin, this, attackEffects, comboZones, attacker, weapon);
        kit.anchor(origin, facing);
        // what is set down at the placement stays as long as the placement does
        DeployHold.Hold hold = holds().active(attacker);
        if (hold != null) kit.holdTicks((int) Math.max(0L, (hold.endsAt - System.currentTimeMillis()) / 50L));
        return kit;
    }

    MoveKit kit(Player attacker, RpgItemDefinition weapon) {
        MoveKit kit = new MoveKit(plugin, this, attackEffects, comboZones, attacker, weapon);
        Object[] origin = chainOrigins.get(attacker.getUniqueId());
        if (origin != null) kit.anchor((Location) origin[0], (org.bukkit.util.Vector) origin[1]);
        return kit;
    }

    /** A chain that starts now (nothing thrown before it) begins where the player stands. */
    private void startChainIfFresh(Player attacker, String plan) {
        if (meleeCombo.done(attacker, plan) == 0) {
            org.bukkit.util.Vector facing = attacker.getLocation().getDirection().setY(0.0);
            if (facing.lengthSquared() < 1.0E-6) facing = new org.bukkit.util.Vector(0.0, 0.0, 1.0);
            chainOrigins.put(attacker.getUniqueId(), new Object[]{attacker.getLocation().clone(), facing.normalize()});
        }
    }

    /** Normal attack {@code n} of the class's combo with this weapon: the melee left click, or the right click with a ranged / deployed one. */
    public boolean comboNormal(Player attacker, RpgItemDefinition weapon) {
        if (!hasClassCombo(attacker, weapon)) return false;
        ClassCombo combo = classComboFor(attacker, weapon);
        startChainIfFresh(attacker, combo.spec().plan());
        MeleeCombo.Step step = meleeCombo.next(attacker, combo.spec().plan());
        beginAttack(attacker);
        attackCooldowns.ensureAtLeast(attacker, weapon.attackType(), (int) plugin.getConfig().getLong("combat.combo." + combo.spec().plan() + ".normal-gap-ticks", combo.normalGapTicks()));
        combo.normal(kit(attacker, weapon), step);
        return true;
    }

    /** Starts the cooldown of a normal attack of a deployed weapon's combo (the pace follows the attack speed, as a melee attack's does). */
    public boolean tryStartDeployCombo(Player attacker) {
        if (tutorialHook != null && tutorialHook.isAttackLocked(attacker)) return false;
        int base = (int) plugin.getConfig().getLong("combat.combo.deploy-normal-ticks", 14L);
        PlayerProfile profile = equipmentService.recalculate(attacker);
        double multiplier = Math.max(0.1, 1.0 + profile.finalStats().get(StatType.ATTACK_SPEED) / 100.0);
        int ticks = Math.max(Math.max(1, plugin.getConfig().getInt("combat.min-cooldown-ticks", 1)), (int) Math.round(base / multiplier));
        return attackCooldowns.tryStart(attacker, AttackType.DEPLOY, ticks);
    }

    /** The cooldown of the assassin's heavy 0 (a dash) during the item swap skill. */
    private static final int SWAP_DASH_COOLDOWN_TICKS = 2;
    private final Map<UUID, Integer> swapDashReady = new HashMap<>();

    /** The cooldown of heavy 0 shows as the grey overlay of the weapon (it blocks nothing: the normal attacks go on); a deployed weapon already shows the cooldown of its placement. */
    private void showHeavyCooldown(Player attacker, RpgItemDefinition weapon, String plan, MeleeCombo.Step step) {
        if (step.heavyIndex() != 0 || weapon.attackType() == AttackType.DEPLOY) return;
        int cooldown = meleeCombo.heavyCooldownTicks(plan, 0);
        if (cooldown > 0) attacker.setCooldown(weapon.material(), cooldown);
    }

    // ---- the heavy attacks (小・中・大 of SkillSlots until the classes' own moves are made) ----------------------------------------------------

    /**
     * Whether the weapon in hand has heavy attacks: a class's combo with it, the mage's ranged combo, or the usual chain of a melee weapon (小・中・大
     * throw its first three until the class's own moves are made, see {@link SkillSlots}).
     */
    public boolean hasHeavies(Player attacker, RpgItemDefinition weapon) {
        if (weapon == null || !weapon.isWeaponLike() || !comboOn()) return false;
        if (mageRanged(attacker, weapon) || hasClassCombo(attacker, weapon)) return true;
        return weapon.attackType() == AttackType.MELEE && comboAllowed(attacker, weapon);
    }

    /** How many heavy attacks a weapon has (skills 1 - 6; 強1 - 強5 were 1 - 5). */
    public static final int HEAVY_ATTACKS = 6;

    /** Which button a click is for this weapon: the left one is the normal attack of a melee weapon, the right one of a ranged or deployed one. */
    public static SkillInput.Button button(RpgItemDefinition weapon, boolean rightClick) {
        boolean melee = weapon.attackType() == AttackType.MELEE;
        return melee != rightClick ? SkillInput.Button.NORMAL : SkillInput.Button.OTHER;
    }

    /** The keys of a skill (小・中・大): heavy attack {@code index} of the weapon in hand. False when it has none. */
    public boolean heavyByKey(Player attacker, int index) {
        RpgItemDefinition weapon = weaponInMainHand(attacker);
        if (!hasHeavies(attacker, weapon)) return false;
        if (tutorialHook != null && tutorialHook.isAttackLocked(attacker)) return true;
        if (index >= HEAVY_ATTACKS) {
            // (skill 6 of t138: not made yet)
            attacker.sendMessage("[iruuRPG] スキル" + (index + 1) + " は、まだありません。");
            return true;
        }
        heavyAttack(attacker, weapon, index);
        return true;
    }

    /** Heavy attack {@code index} (0 - 5: skills 1 - 6, 強1 - 強5 were 0 - 4) of the weapon in hand. */
    public void heavyAttack(Player attacker, RpgItemDefinition weapon, int index) {
        if (!canUseItem(attacker, weapon)) {
            sendLevelRequirement(attacker, weapon);
            return;
        }
        if (mageRanged(attacker, weapon)) {
            mageHeavy(attacker, weapon, index, false);
            return;
        }
        if (hasClassCombo(attacker, weapon)) {
            if (weapon.attackType() == AttackType.DEPLOY) deployHeavy(attacker, weapon, index);
            else comboHeavy(attacker, weapon, index, false);
            return;
        }
        if (weapon.attackType() == AttackType.MELEE) usualHeavy.accept(attacker, weapon);
    }

    /**
     * Heavy attack {@code index} (0 - 5) of the weapon in hand thrown as a class's skill ({@link SlotMoves}): the skill cools down for its own
     * time ({@link SkillSlots}), so the heavy attack's own cooldown neither waits nor starts. False when nothing was thrown (the weapon has no
     * heavy attacks, the mage has nothing to re-activate, a deployed weapon's placement is missing or used, the attacks are locked).
     */
    public boolean slotHeavy(Player attacker, RpgItemDefinition weapon, int index) {
        if (!hasHeavies(attacker, weapon)) return false;
        if (tutorialHook != null && tutorialHook.isAttackLocked(attacker)) return false;
        if (!canUseItem(attacker, weapon)) {
            sendLevelRequirement(attacker, weapon);
            return false;
        }
        if (mageRanged(attacker, weapon)) {
            if (index == 0 && !mageMoves.chainActive(attacker)) {
                // (nothing to re-activate: the skill does not cool down)
                coolingClick(attacker);
                return false;
            }
            return mageHeavy(attacker, weapon, index, true);
        }
        if (hasClassCombo(attacker, weapon)) {
            if (weapon.attackType() == AttackType.DEPLOY) return deployHeavy(attacker, weapon, index);
            return comboHeavy(attacker, weapon, index, true);
        }
        if (weapon.attackType() != AttackType.MELEE) return false;
        usualHeavy.accept(attacker, weapon);
        return true;
    }

    /** The heavy attack of the usual chain of a melee weapon (set by the listener, which swings it as it swings a normal attack). */
    private java.util.function.BiConsumer<Player, RpgItemDefinition> usualHeavy = (player, weapon) -> {
    };

    public void setUsualHeavy(java.util.function.BiConsumer<Player, RpgItemDefinition> usualHeavy) {
        this.usualHeavy = usualHeavy;
    }

    /** Told of every critical hit that was applied: the attacker and the middle of the one hit (the screen effects' impact, t130). */
    private java.util.function.BiConsumer<Player, Location> criticalHit = (attacker, at) -> {
    };

    public void setCriticalHit(java.util.function.BiConsumer<Player, Location> criticalHit) {
        this.criticalHit = criticalHit == null ? (attacker, at) -> {
        } : criticalHit;
    }

    private void criticalHit(Player attacker, Location numberLocation, double height, boolean critical) {
        if (!critical || attacker == null || numberLocation == null) return;
        try {
            criticalHit.accept(attacker, numberLocation.clone().add(0.0, Math.max(0.5, height * 0.5), 0.0));
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("critical hit effect: " + exception);
        }
    }

    private final Map<UUID, Integer> lastCoolingClick = new HashMap<>();

    /** A heavy attack that is still cooling down: a dull click, nothing thrown (not again and again for a held button). */
    private void coolingClick(Player attacker) {
        int tick = org.bukkit.Bukkit.getCurrentTick();
        Integer last = lastCoolingClick.put(attacker.getUniqueId(), tick);
        if (last != null && tick - last <= 10) return;
        attacker.playSound(attacker.getLocation(), org.bukkit.Sound.BLOCK_DISPENSER_FAIL, org.bukkit.SoundCategory.PLAYERS, 0.5f, 1.4f);
    }

    /** {milliseconds left, the whole cooldown} of heavy attack {@code index} (0 - 4) of the weapon in hand ({0, 0}: ready, or no such heavy attack). */
    public long[] heavyCooldown(Player attacker, RpgItemDefinition weapon, int index) {
        String plan = heavyPlan(attacker, weapon);
        return plan == null ? new long[]{0L, 0L} : meleeCombo.heavyCooldown(attacker, plan, index);
    }

    /** The plan whose heavy attacks the weapon in hand throws (null when they have no cooldowns of their own: none, a deployed weapon's, the usual one). */
    private String heavyPlan(Player attacker, RpgItemDefinition weapon) {
        if (mageRanged(attacker, weapon)) return MeleeCombo.MAGE;
        if (!hasClassCombo(attacker, weapon) || weapon.attackType() == AttackType.DEPLOY) return null;
        return classComboFor(attacker, weapon).spec().plan();
    }

    /** Whether the heavy attacks of the weapon in hand show their icons on the action bar: they have cooldowns of their own. */
    public boolean heavyIconsShown(Player attacker, RpgItemDefinition weapon) {
        return heavyPlan(attacker, weapon) != null;
    }

    /** Whose icons the heavy attacks of the weapon in hand show: the class of its combo ("generic" for a weapon outside the class's own combos). */
    public String heavyIconClass(Player attacker, RpgItemDefinition weapon) {
        if (mageRanged(attacker, weapon)) return "mage";
        ClassCombo combo = classComboFor(attacker, weapon);
        return combo == null || "*".equals(combo.classId()) ? "generic" : combo.classId();
    }

    /**
     * Heavy attack {@code index} of the class's combo with a melee or ranged weapon. A heavy attack that can be gone on with (the scythe's) takes
     * any heavy input as its next part; 強1 is the assassin's re-activation of its clones while they stand, and its dash during the item swap skill.
     * {@code free}: thrown as a class's skill, without its own cooldown. False when nothing was thrown (cooling down).
     */
    private boolean comboHeavy(Player attacker, RpgItemDefinition weapon, int index, boolean free) {
        boolean melee = weapon.attackType() == AttackType.MELEE;
        int tick = org.bukkit.Bukkit.getCurrentTick();
        ClassCombo combo = classComboFor(attacker, weapon);
        startChainIfFresh(attacker, combo.spec().plan());
        if (combo.followUp(kit(attacker, weapon))) {
            beginAttack(attacker);
            if (melee) ClickSignals.lockLeft(attacker, (int) plugin.getConfig().getLong("combat.combo.heavy-recovery-ticks", 8L));
            return true;
        }
        boolean assassin = AssassinMeleeCombo.PLAN.equals(combo.spec().plan());
        // (not as a class's skill: 中 makes a new decoy each time, with no count to reach)
        if (assassin && index == 0 && !free && AssassinClones.canRecast(attacker)) {
            beginAttack(attacker);
            AssassinClones.recast(kit(attacker, weapon), assassinFrenzy(attacker) >= 2 ? 1.35 : assassinFrenzy(attacker) == 1 ? 1.2 : 1.0);
            return true;
        }
        // during the item swap skill the assassin's 強1 is a dash with a cooldown of 2 ticks of its own (the clone's cooldown of 8 s neither waits nor starts)
        boolean swapDash = assassin && index == 0 && assassinFrenzy(attacker) > 0;
        if (swapDash) {
            Integer ready = swapDashReady.get(attacker.getUniqueId());
            if (ready != null && tick < ready) return false;
            swapDashReady.put(attacker.getUniqueId(), tick + SWAP_DASH_COOLDOWN_TICKS);
        }
        MeleeCombo.Step step = meleeCombo.heavyByInput(attacker, combo.spec().plan(), index, swapDash || free);
        if (step == null) {
            coolingClick(attacker);
            return false;
        }
        beginAttack(attacker);
        if (!swapDash && !free) showHeavyCooldown(attacker, weapon, combo.spec().plan(), step);
        combo.heavy(kit(attacker, weapon), step);
        if (melee) ClickSignals.lockLeft(attacker, (int) plugin.getConfig().getLong("combat.combo.heavy-recovery-ticks", 8L));
        return true;
    }

    // ---- guard (invulnerability, damage cut, shields) -----------------------------------------------------------------------

    private Guard guard;

    public void setGuard(Guard guard) {
        this.guard = guard;
    }

    public Guard guard() {
        return guard;
    }

    // ---- the ranger (internal class id "gifted") ------------------------------------------------------------------------

    private RangerMoves rangerMoves;
    private final Map<UUID, Integer> lastRangerHeavyTick = new HashMap<>();

    public void setRangerMoves(RangerMoves rangerMoves) {
        this.rangerMoves = rangerMoves;
    }

    /** The ranger's loop: {damage multiplier, speed multiplier} for its next ranged attack (1, 1 when none is waiting); takes it. */
    public double[] takeRangeBoost(Player player) {
        return rangerMoves == null || !isRanger(player) ? new double[]{1.0, 1.0} : rangerMoves.takeRangeBoostPublic(player);
    }

    public RangerMoves rangerMoves() {
        return rangerMoves;
    }

    public boolean isRanger(Player attacker) {
        return rangerMoves != null && hasClassId(attacker, "gifted");
    }

    private boolean comboOn() {
        return plugin.getConfig().getBoolean("combat.combo.enabled", true);
    }

    /** Called after each hit of a player: a ranger's hits power up the next kind of attack (see {@link RangerMoves}). */
    public void rangerHit(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        if (isRanger(attacker)) rangerMoves.onHit(attacker, victim, weapon);
    }

    /** The damage multiplier configured for heavy attack {@code index} of {@code plan}. */
    public double heavyMultiplier(String plan, int index) {
        return meleeCombo.heavyMultiplier(plan, index);
    }

    /** Adds {@code amount} of confusion to an enemy (the assassin's). */
    public void confuse(LivingEntity target, double amount, UUID source) {
        if (statusEffectService != null && target.isValid() && !target.isDead()) statusEffectService.addConfusion(target, amount, source);
    }

    /** Slows an enemy by {@code percent} for {@code millis}. */
    public void slow(LivingEntity target, double percent, long millis, UUID source) {
        statusEffectService.applyTimedSlow(target, percent, millis, source);
    }

    /**
     * One slash of the assassin's combo: the hand it is struck with is the next one (固有0: the hands take turns, one
     * slash each, the combo's hits included), so the slash uses that hand's weapon and its stats. Returns the hand so
     * all the enemies the same slash hits can be struck with it ({@link #strike(Player, LivingEntity, MeleeHand, double)}).
     */
    public MeleeHand nextSlashHand(Player attacker, RpgItemDefinition mainWeapon) {
        MeleeHand hand = nextMeleeHand(attacker, mainWeapon);
        // (the main arm swings by itself for the click; only the off hand is played by the plugin)
        if (hand.offHand()) swingOffHand(attacker);
        return hand;
    }

    /** One ranged hit of {@code rate} times the weapon's damage on {@code target} (the mage's bursts and heavy attacks). */
    public boolean strikeRanged(Player attacker, LivingEntity target, RpgItemDefinition weapon, double rate) {
        if (rate <= 0.0 || target.isDead() || !target.isValid()) return false;
        AttackDamage attack = calculateDamage(attacker, target, weapon);
        double damage = round(attack.result().damage() * rate);
        boolean critical = attack.result().critical();
        if (!applyRangeDamage(attacker, target, damage, weapon, critical)) return false;
        sendDamageDebug(attacker, AttackType.RANGE, target, damage, critical, false);
        return true;
    }

    /** One hit of {@code multiplier} times the damage of {@code hand}'s weapon on {@code target}. */
    public boolean strike(Player attacker, LivingEntity target, MeleeHand hand, double multiplier) {
        if (multiplier <= 0.0 || target.isDead() || !target.isValid()) return false;
        AttackDamage attack = calculateMeleeDamage(attacker, target, hand);
        double damage = round(attack.result().damage() * multiplier);
        boolean critical = attack.result().critical();
        if (!applyDirectDamage(attacker, target, damage, hand.weapon(), critical)) return false;
        playMeleeImpact(attacker, target);
        sendDamageDebug(attacker, AttackType.MELEE, target, damage, critical, false);
        return true;
    }

    /** One hit of {@code multiplier} times the weapon's damage on {@code target}, with its number and impact. */
    public boolean strike(Player attacker, LivingEntity target, RpgItemDefinition weapon, double multiplier) {
        if (multiplier <= 0.0 || target.isDead() || !target.isValid()) return false;
        AttackDamage attack = calculateDamage(attacker, target, weapon);
        double damage = round(attack.result().damage() * multiplier);
        boolean critical = attack.result().critical();
        if (!applyDirectDamage(attacker, target, damage, weapon, critical)) return false;
        playMeleeImpact(attacker, target);
        sendDamageDebug(attacker, AttackType.MELEE, target, damage, critical, false);
        return true;
    }

    /**
     * The repeats of a multi-hit attack ({@code step.extraHits()} more cuts, each drawn again a little turned and, on
     * an enemy, hitting for {@code step.damageMultiplier()} of the weapon's damage), a few ticks apart. {@code victim}
     * is null for a swing that hit nothing (only the cuts are drawn).
     */
    public void scheduleRepeats(Player attacker, LivingEntity victim, RpgItemDefinition weapon, MeleeCombo.Step step) {
        long gapTicks = Math.max(1L, plugin.getConfig().getLong("combat.combo.assassin.multi-hit-gap-ticks", 4L));
        for (int index = 1; index <= step.extraHits(); index++) {
            int repeat = index;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!attacker.isOnline() || attacker.isDead()) return;
                // each cut is a slash of its own: the hands take turns (固有0), and the cut has the colour of that hand's weapon
                MeleeHand hand = nextSlashHand(attacker, weapon);
                playMeleeSlash(attacker, hand.weapon(), step, repeat);
                if (victim != null && !victim.isDead() && victim.isValid()) strike(attacker, victim, hand, step.damageMultiplier());
            }, gapTicks * index);
        }
    }

    private net.tkgon.mc.iruuRPG.skill.SkillRegistry comboStyleParser;

    /** The skill registry that reads `combo-style:` sections. */
    public void setComboStyleParser(net.tkgon.mc.iruuRPG.skill.SkillRegistry parser) {
        this.comboStyleParser = parser;
    }

    private ComboStyle comboStyleOf(String owner, java.util.Map<String, Object> raw) {
        return comboStyleParser == null || raw == null || raw.isEmpty() ? null : comboStyleParser.comboStyle(owner, raw);
    }

    public void setMobService(RpgMobService mobService) {
        this.mobService = mobService;
    }

    public void setDebugTargetService(DebugTargetService debugTargetService) {
        this.debugTargetService = debugTargetService;
    }

    public boolean isCustomWeaponAttack(Entity damager) {
        Player attacker = resolvePlayerAttacker(damager);
        if (attacker == null) return false;

        RpgItemDefinition weapon = weaponInMainHand(attacker);
        return weapon != null && weapon.isWeaponLike();
    }

    public boolean isPlayerVersusPlayer(Entity damager, Entity victim) {
        return victim instanceof Player && resolvePlayerAttacker(damager) != null;
    }

    public Player resolvePlayerAttacker(Entity damager) {
        Entity attacker = damager;

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        return attacker instanceof Player player ? player : null;
    }

    private LivingEntity resolveLivingAttacker(Entity damager) {
        Entity attacker = damager;

        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }

        return attacker instanceof LivingEntity living ? living : null;
    }

    /** The item of the plugin {@code item} is (null for anything else). */
    public RpgItemDefinition definitionOf(ItemStack item) {
        return itemIdentifier.itemId(item).flatMap(itemRegistry::find).orElse(null);
    }

    public JavaPlugin plugin() {
        return plugin;
    }

    public RpgItemDefinition weaponInMainHand(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return itemIdentifier.itemId(item)
                .flatMap(itemRegistry::find)
                .orElse(null);
    }

    public boolean canUseItem(Player player, RpgItemDefinition definition) {
        return equipmentService.canUse(player, definition);
    }

    /**
     * Whether the class's combo (the class's own chain of normal attacks and its heavy attacks) comes with this weapon:
     * only when the weapon is yellow (favorite) to the player's class. Anything that is not a melee / ranged / deployed
     * weapon has no restriction. A white weapon is used with ordinary attacks.
     */
    public boolean comboAllowed(Player attacker, RpgItemDefinition weapon) {
        if (weapon == null) return true;
        return weapon.comboByClass(equipmentService.classIdOf(attacker));
    }

    public void sendLevelRequirement(Player player, RpgItemDefinition definition) {
        if (definition == null) return;
        if (equipmentService.classUnusable(player, definition)) {
            player.sendMessage("[iruuRPG] このクラスでは、この武器は使えません。");
            return;
        }

        PlayerProfile profile = profileManager.getOrCreate(player);
        player.sendMessage("[iruuRPG] このアイテムはLv" + definition.requiredLevel()
                + "から使用できます。現在Lv" + profile.level() + "です。");
    }

    /** The tutorial's NPC and rat: only the player running that tutorial can interact with them. */
    private boolean isTutorialProtected(LivingEntity victim) {
        return tutorialHook != null && victim != null && tutorialHook.isProtected(victim);
    }

    public void setTutorialHook(TutorialDamageHook tutorialHook) {
        this.tutorialHook = tutorialHook;
    }

    // ---- status effects: once per attack ------------------------------------------------------------------------------------

    /** The enemies that already got the status effects of the attack (a combo step, a normal attack, a skill cast) now going on, per player. */
    private final Map<UUID, java.util.Set<UUID>> statusGiven = new HashMap<>();
    private final Map<UUID, Integer> statusUnlimitedUntil = new HashMap<>();

    /**
     * A new attack starts (a normal attack, a step of a combo, a skill cast): its hits give an enemy status effects
     * only once, however many times they hit, and the first hit of a normal attack gives the weapon's stacks one "hit".
     */
    public void beginAttack(Player attacker) {
        if (attacker == null) return;
        statusGiven.remove(attacker.getUniqueId());
        hitStackOpen.add(attacker.getUniqueId());
    }

    /** For the next {@code ticks} ticks every hit of the player gives status effects (the assassin's class skill). */
    public void statusUnlimited(Player attacker, int ticks) {
        statusUnlimitedUntil.put(attacker.getUniqueId(), org.bukkit.Bukkit.getCurrentTick() + ticks);
    }

    /** True when this hit may give {@code victim} status effects: the first hit of the attack on it (a deployed weapon's pulses always may). */
    private boolean statusAllowed(Player attacker, LivingEntity victim, RpgItemDefinition weapon, PlayerProfile snapshot) {
        if (attacker == null) return true;
        // a deployed weapon's own pulses (they come with the snapshot taken when it was put) are not an attack of the player
        if (snapshot != null) return true;
        Integer until = statusUnlimitedUntil.get(attacker.getUniqueId());
        if (until != null && org.bukkit.Bukkit.getCurrentTick() <= until) return true;
        return statusGiven.computeIfAbsent(attacker.getUniqueId(), ignored -> new java.util.HashSet<>()).add(victim.getUniqueId());
    }

    public boolean tryStart(Player attacker, AttackType attackType) {
        if (tutorialHook != null && tutorialHook.isAttackLocked(attacker)) {
            return false;
        }
        int cooldownTicks = effectiveCooldownTicks(attacker, attackType);
        if (!attackCooldowns.tryStart(attacker, attackType, cooldownTicks)) {
            return false;
        }
        beginAttack(attacker);

        // the normal attack's cooldown is shown by the action bar gauge; the item's own cooldown is the weapon skill's only
        return true;
    }

    public long remainingCooldownMillis(Player attacker, AttackType attackType) {
        return attackCooldowns.remainingMillis(attacker, attackType);
    }

    /** The normal attack's cooldown of {@code attackType}: {remaining, total} in milliseconds ({0, ...} when ready). */
    public long[] normalCooldown(Player attacker, AttackType attackType) {
        return new long[]{attackCooldowns.remainingMillis(attacker, attackType), attackCooldowns.totalMillis(attacker, attackType)};
    }

    /** Gives {@code amount} MP back (up to the most the player can have). */
    public void restoreMp(Player player, double amount) {
        PlayerProfile profile = profileForPlayer(player);
        profile.setCurrentMp(profile.currentMp() + Math.max(0.0, amount));
        playerBars.sync(player, profile);
    }

    public boolean consumeMp(Player player, double amount) {
        PlayerProfile profile = profileForPlayer(player);
        if (profile.currentMp() + 1.0E-9 < amount) {
            return false;
        }

        profile.setCurrentMp(profile.currentMp() - amount);
        playerBars.sync(player, profile);
        return true;
    }

    public AttackDamage calculateDamage(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        PlayerProfile attackerProfile = equipmentService.recalculate(attacker);
        return calculateDamage(attackerProfile, victim, weapon);
    }

    public AttackDamage calculateDamage(PlayerProfile attackerProfile, LivingEntity victim, RpgItemDefinition weapon) {
        PlayerProfile victimProfile = profileForVictim(victim);
        DamageResult result = damageCalculator.calculate(new DamageInput(attackerProfile, victimProfile, weapon, statusEffectService.corrosionOf(victim)));
        return new AttackDamage(result, victimProfile);
    }

    public DamageResult calculateNeutralDamage(PlayerProfile attackerProfile, RpgItemDefinition weapon) {
        PlayerProfile neutralVictim = new PlayerProfile(UUID.nameUUIDFromBytes("iruurpg:neutral-victim".getBytes()), 1.0, 1.0);
        DamageResult result = damageCalculator.calculate(new DamageInput(attackerProfile, neutralVictim, weapon));
        return result;
    }

    public OptionalDouble incomingRpgMobDamage(EntityDamageEvent event, Player victim) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return OptionalDouble.empty();
        if (mobService == null) return OptionalDouble.empty();

        LivingEntity attacker = resolveLivingAttacker(byEntity.getDamager());
        if (attacker == null || !mobService.isRpgMob(attacker)) return OptionalDouble.empty();

        PlayerProfile attackerProfile = mobService.profile(attacker).orElse(null);
        if (attackerProfile == null) return OptionalDouble.empty();

        PlayerProfile victimProfile = equipmentService.recalculate(victim);
        double corrosion = statusEffectService.corrosionOf(victim);
        RpgItemDefinition weapon = mobWeapon(attacker);
        DamageResult result = damageCalculator.calculate(new DamageInput(attackerProfile, victimProfile, weapon, corrosion));
        // a skill hit multiplies the mob's normal damage (see RpgMobService#hurt)
        double rate = mobService.pendingDamageRate(attacker);
        return OptionalDouble.of(scaleIncomingRpgMobDamage(Math.max(0.0, result.damage()) * rate, victimProfile));
    }

    public void applyRpgMobStatusEffects(EntityDamageEvent event, Player victim, double damage) {
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) return;
        if (mobService == null || damage <= 0.0 || victim == null || victim.isDead() || !victim.isValid()) return;

        LivingEntity attacker = resolveLivingAttacker(byEntity.getDamager());
        if (attacker == null || !mobService.isRpgMob(attacker)) return;

        PlayerProfile attackerProfile = mobService.profile(attacker).orElse(null);
        if (attackerProfile == null) return;

        RpgItemDefinition weapon = mobWeapon(attacker);
        statusEffectService.onMobDamageDealt(attacker, victim, weapon, attackerProfile, damage);
        stackTrigger(victim, "damaged");
    }

    public PlayerProfile snapshotAttacker(Player attacker) {
        PlayerProfile live = equipmentService.recalculate(attacker);
        PlayerProfile snapshot = new PlayerProfile(attacker.getUniqueId(), live.maxHp(), live.maxMp());
        snapshot.setLevel(live.level());
        snapshot.setXp(live.xp());
        snapshot.baseStats().replaceWith(live.finalStats());
        snapshot.baseElementStats().replaceWith(live.finalElementStats());
        snapshot.recalculate();
        snapshot.setCurrentHp(live.currentHp());
        snapshot.setCurrentMp(live.currentMp());
        return snapshot;
    }

    public PlayerProfile profileForPlayer(Player player) {
        return profileManager.getOrCreate(player);
    }

    public PlayerProfile profileForVictim(LivingEntity victim) {
        if (victim instanceof Player player) {
            return equipmentService.recalculate(player);
        }

        if (mobService != null) {
            PlayerProfile profile = mobService.profile(victim).orElse(null);
            if (profile != null) return profile;
        }

        double maxHealth = maxHealth(victim);
        PlayerProfile profile = new PlayerProfile(victim.getUniqueId(), maxHealth, 1.0);
        profile.setCurrentHp(victim.getHealth());
        return profile;
    }

    public LivingEntity findRangeVictim(Player attacker) {
        return traceRange(attacker).victim();
    }

    /**
     * How far a ranged attack of this player reaches: every class has balance.range-multiplier (0.5) of
     * combat.range-distance; the mage's innate "range_double" doubles it back.
     */
    public double rangeDistance(Player attacker) {
        return plugin.getConfig().getDouble("combat.range-distance", 16.0)
                * Math.max(0.1, plugin.getConfig().getDouble("balance.range-multiplier", 0.5))
                * (hasSkill(attacker, RANGE_DOUBLE_SKILL) ? 2.0 : 1.0)
                // the ranger's 固有1: a longer range for its ranged weapon
                * (hasSkill(attacker, RANGE_INSTANT_SKILL) && hasClassId(attacker, "gifted")
                        ? Math.max(1.0, plugin.getConfig().getDouble("combat.ranger-range-multiplier", 2.0)) : 1.0);
    }

    private static final String RANGE_DOUBLE_SKILL = "range_double";
    private static final String RANGE_INSTANT_SKILL = "range_instant";
    private static final String ATTACK_SPEED_PROJECTILE_SKILL = "attack_speed_cooldown";

    /**
     * Ranged shots always fly now (the ranger's 固有1 used to make them land at once; it lengthens the range instead,
     * see {@link #rangeDistance}).
     */
    public boolean rangedIsInstant(Player attacker) {
        return false;
    }

    /**
     * Blocks per second a ranged shot of this player flies: balance.projectile-speed (40); the mage's
     * attack-speed node also raises it by the attack speed %.
     */
    public double projectileSpeed(Player attacker) {
        double speed = Math.max(4.0, plugin.getConfig().getDouble("balance.projectile-speed", 40.0));
        if (hasSkill(attacker, ATTACK_SPEED_PROJECTILE_SKILL)) {
            double attackSpeed = equipmentService.recalculate(attacker).finalStats().get(StatType.ATTACK_SPEED);
            speed *= Math.max(1.0, 1.0 + attackSpeed / 100.0);
        }
        return Math.min(400.0, speed);
    }

    /**
     * A ranged shot with a speed: the shot flies from the eye along where the player looks and hits the first
     * enemy (or block) on the way; the damage is the one of an ordinary ranged hit at the moment it arrives.
     */
    public void fireRangedProjectile(Player attacker, RpgItemDefinition weapon) {
        Location eye = attacker.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        Location start = eye.clone().add(direction.clone().multiply(plugin.getConfig().getDouble("combat.effects.range-trail.start-offset", 0.65)));
        fireRangedProjectile(attacker, weapon, start, direction, 1.0, 1.0, null);
    }

    /**
     * A ranged shot from {@code start} along {@code direction}: it hits for {@code rate} times the usual damage,
     * flies {@code speedScale} times as fast, and is drawn by {@code segment} (null: the usual bolt of the weapon's element).
     */
    public void fireRangedProjectile(Player attacker, RpgItemDefinition weapon, Location start, Vector direction, double rate,
                                     double speedScale, java.util.function.BiConsumer<Location, Location> segment) {
        double perTick = projectileSpeed(attacker) * Math.max(0.1, speedScale) / 20.0;
        double maxDistance = rangeDistance(attacker);
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6);
        // (a ranger's shots are arrows: they sound like arrows)
        if (isRanger(attacker)) attackEffects.playArrowLaunch(attacker, start);
        else attackEffects.playRangeLaunch(attacker, weapon, start);
        org.bukkit.World world = attacker.getWorld();

        new org.bukkit.scheduler.BukkitRunnable() {
            private Location position = start.clone();
            private double travelled;

            @Override
            public void run() {
                ParticleFx.mark(attacker);
                if (!attacker.isOnline() || !position.getWorld().equals(attacker.getWorld())) {
                    cancel();
                    return;
                }
                double step = Math.min(perTick, maxDistance - travelled);
                if (step <= 1.0E-6) {
                    cancel();
                    return;
                }
                RayTraceResult result = world.rayTrace(position, direction, step, FluidCollisionMode.NEVER, true, raySize,
                        entity -> isAttackTarget(entity, attacker));
                Location next = result != null && result.getHitPosition() != null
                        ? result.getHitPosition().toLocation(world)
                        : position.clone().add(direction.clone().multiply(step));
                if (segment != null) segment.accept(position, next);
                else attackEffects.playBoltSegment(position, next, weapon.element());
                if (result != null) {
                    cancel();
                    if (result.getHitEntity() instanceof LivingEntity victim) {
                        hitWithShot(attacker, weapon, victim, next, rate);
                    } else {
                        attackEffects.playRangeImpact(attacker, next, weapon);
                        sendMissDebug(attacker, AttackType.RANGE);
                    }
                    return;
                }
                position = next;
                travelled += step;
                if (travelled >= maxDistance - 1.0E-6) {
                    cancel();
                    sendMissDebug(attacker, AttackType.RANGE);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /** The hit of a ranged shot that reached an enemy (also the one of an instant shot). */
    public void hitWithShot(Player attacker, RpgItemDefinition weapon, LivingEntity victim, Location hitLocation) {
        hitWithShot(attacker, weapon, victim, hitLocation, 1.0);
    }

    /** {@code rate}: the shot hits for this much of the usual ranged damage (the mage's scatter shots). */
    public void hitWithShot(Player attacker, RpgItemDefinition weapon, LivingEntity victim, Location hitLocation, double rate) {
        if (victim.isDead() || !victim.isValid()) return;
        AttackDamage attack = calculateDamage(attacker, victim, weapon);
        double scale = tripleScale(attacker);
        double damage = round(attack.result().damage() * scale * rate);
        if (!applyRangeDamage(attacker, victim, damage, weapon, attack.result().critical())) {
            return;
        }
        playRangeImpact(attacker, hitLocation, weapon);
        sendDamageDebug(attacker, AttackType.RANGE, victim, damage, attack.result().critical(), false);
        applyAreaDamage(attacker, victim, damage, AttackType.RANGE, weapon);
        if (scale < 1.0) scheduleTripleHits(attacker, victim, weapon, AttackType.RANGE);
        normalAttackHit(attacker);
    }

    public RangeTrace traceRange(Player attacker) {
        double range = rangeDistance(attacker);
        double raySize = plugin.getConfig().getDouble("combat.range-ray-size", 0.6);
        Location start = attacker.getEyeLocation();
        Vector direction = start.getDirection().normalize();
        Location end = start.clone().add(direction.clone().multiply(range));

        RayTraceResult result = attacker.getWorld().rayTrace(
                start,
                direction,
                range,
                FluidCollisionMode.NEVER,
                true,
                raySize,
                entity -> isAttackTarget(entity, attacker)
        );

        if (result == null || !(result.getHitEntity() instanceof LivingEntity victim)) {
            Location hitLocation = result != null && result.getHitPosition() != null
                    ? result.getHitPosition().toLocation(attacker.getWorld())
                    : end;
            return new RangeTrace(null, hitLocation);
        }

        Location hitLocation = result.getHitPosition() != null
                ? result.getHitPosition().toLocation(attacker.getWorld())
                : victim.getLocation().add(0.0, victim.getHeight() * 0.5, 0.0);
        return new RangeTrace(victim, hitLocation);
    }

    public void applyDirectDamage(LivingEntity victim, double damage) {
        applyDirectDamage(null, victim, damage);
    }

    public boolean applyDirectDamage(Player attacker, LivingEntity victim, double damage) {
        if (DeployService.isDeployEntity(victim) || isTutorialProtected(victim)) {
            return false;
        }

        if (attacker != null && victim instanceof Player) {
            return false;
        }

        if (mobService != null && mobService.applyDamage(victim, damage, attacker)) {
            return true;
        }

        if (victim instanceof Player player) {
            applyPlayerDamage(player, profileManager.getOrCreate(player), damage);
            return true;
        }

        victim.damage(damage);
        return true;
    }

    public boolean applyDirectDamage(Player attacker, LivingEntity victim, double damage, RpgItemDefinition weapon, boolean critical) {
        return applyDirectDamage(attacker, victim, damage, weapon, critical, null);
    }

    /**
     * {@code snapshot}: the attacker's stats taken when the attack was made (a deployed weapon's); the status effects it
     * gives come from these and not from what the player holds now. Null: the live stats.
     */
    public boolean applyDirectDamage(Player attacker, LivingEntity victim, double damage, RpgItemDefinition weapon, boolean critical, PlayerProfile snapshot) {
        if (isTutorialProtected(victim) && !tutorialHook.isOwnTarget(attacker, victim)) {
            return false;
        }
        if (tutorialHook != null && tutorialHook.isActive(attacker)) {
            if (tutorialHook.isNpc(victim)) {
                tutorialHook.onNpcHit(attacker);
                return false;
            }
            if (tutorialHook.isRat(victim)) {
                attackEffects.playDamageNumber(attacker, victim.getLocation().clone(), victim.getHeight(), weapon, TUTORIAL_DAMAGE_CAP, false);
                tutorialHook.onRatHit(attacker, victim);
                return true;
            }
            damage = Math.min(damage, TUTORIAL_DAMAGE_CAP);
        }
        Location numberLocation = victim.getLocation().clone();
        double numberHeight = victim.getHeight();
        double finalDamage = modifiedCustomDamage(attacker, victim, weapon, damage);
        boolean applied = applyDirectDamage(attacker, victim, finalDamage);
        if (applied && attacker != null) damageMeter.record(attacker.getUniqueId(), finalDamage);
        // the number shows also for the hit that kills (its place was taken before the damage); the status effects only on a victim that lives
        if (applied) attackEffects.playDamageNumber(attacker, numberLocation, numberHeight, weapon, finalDamage, critical);
        if (applied) criticalHit(attacker, numberLocation, numberHeight, critical);
        if (applied && hasRemainingHealth(victim)) {
            boolean statuses = statusAllowed(attacker, victim, weapon, snapshot);
            statusEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical, statuses, snapshot);
            if (classEffectService != null) {
                classEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical, statuses, snapshot);
            }
        }
        return applied;
    }

    /**
     * An extra hit on top of an attack (class effects): applies the damage and shows its number in the element's
     * color, also when it is the hit that kills (the position is taken before the damage).
     */
    public boolean applyExtraDamage(Player attacker, LivingEntity victim, double damage, net.tkgon.mc.iruuRPG.stat.Element element) {
        // an extra hit goes through defense and the kind resists, but not through the resist of its own element
        double resist = Math.max(0.0, profileForVictim(victim).finalElementStats().resist(element));
        damage = round(damage * (1.0 - resist / (1.0 + resist)));
        if (damage <= 0.0) return false;
        Location numberLocation = victim.getLocation().clone();
        double numberHeight = victim.getHeight();
        boolean applied = applyDirectDamage(attacker, victim, damage);
        if (applied && attacker != null) damageMeter.record(attacker.getUniqueId(), damage);
        if (applied) {
            attackEffects.playExtraDamageNumber(numberLocation, numberHeight, element, damage);
        }
        return applied;
    }

    /** A stack of the explosion status on {@code target} (ten of them detonate), from {@code source}. */
    public void addExplosionStack(LivingEntity target, Player source) {
        statusEffectService.addExplosion(target, 1.0, source.getUniqueId());
    }

    public boolean applyRangeDamage(Player attacker, LivingEntity victim, double damage) {
        if (DeployService.isDeployEntity(victim) || isTutorialProtected(victim)) {
            return false;
        }

        if (victim instanceof Player) {
            return false;
        }

        Vector beforeVelocity = victim.getVelocity().clone();
        boolean applied = applyNoKnockbackDamage(attacker, victim, damage);
        if (applied) {
            attackEffects.playRangeHurt(victim);
            victim.setVelocity(beforeVelocity);
        }
        return applied;
    }

    public boolean applyRangeDamage(Player attacker, LivingEntity victim, double damage, RpgItemDefinition weapon, boolean critical) {
        return applyRangeDamage(attacker, victim, damage, weapon, critical, null);
    }

    public boolean applyRangeDamage(Player attacker, LivingEntity victim, double damage, RpgItemDefinition weapon, boolean critical, PlayerProfile snapshot) {
        if (isTutorialProtected(victim) && !tutorialHook.isOwnTarget(attacker, victim)) {
            return false;
        }
        if (tutorialHook != null && tutorialHook.isActive(attacker)) {
            if (tutorialHook.isNpc(victim)) {
                tutorialHook.onNpcHit(attacker);
                return false;
            }
            if (tutorialHook.isRat(victim)) {
                attackEffects.playDamageNumber(attacker, victim.getLocation().clone(), victim.getHeight(), weapon, TUTORIAL_DAMAGE_CAP, false);
                tutorialHook.onRatHit(attacker, victim);
                return true;
            }
            damage = Math.min(damage, TUTORIAL_DAMAGE_CAP);
        }
        Location numberLocation = victim.getLocation().clone();
        double numberHeight = victim.getHeight();
        double finalDamage = modifiedCustomDamage(attacker, victim, weapon, damage);
        boolean applied = applyRangeDamage(attacker, victim, finalDamage);
        if (applied && attacker != null) damageMeter.record(attacker.getUniqueId(), finalDamage);
        // the number shows also for the hit that kills (its place was taken before the damage); the status effects only on a victim that lives
        if (applied) attackEffects.playDamageNumber(attacker, numberLocation, numberHeight, weapon, finalDamage, critical);
        if (applied) criticalHit(attacker, numberLocation, numberHeight, critical);
        if (applied && hasRemainingHealth(victim)) {
            boolean statuses = statusAllowed(attacker, victim, weapon, snapshot);
            statusEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical, statuses, snapshot);
            if (classEffectService != null) {
                classEffectService.onDamageDealt(attacker, victim, weapon, finalDamage, critical, statuses, snapshot);
            }
        }
        return applied;
    }

    /** The slash of an attack of the light / heavy chain: a heavy or the finisher is drawn big, the lights as usual. */
    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon, MeleeCombo.Step step) {
        playMeleeSlash(attacker, weapon, step, 0);
    }

    /** {@code repeat}: the n-th time (0 = the first) the cut of a multi-hit attack is drawn; each is turned a little further. */
    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon, MeleeCombo.Step step, int repeat) {
        // an assassin with a weapon in each hand: the crossing slashes of the two hands for the first two lights (the
        // third and fourth are cuts in front), two sweeps in opposite directions for the finisher
        boolean dualWield = dualWielding(attacker);
        boolean dualLight = !step.heavy() && !step.finisher() && dualWield;
        // a `combo-style` of the weapon, then of the class, decides first (each part falls back to the next level)
        ComboStyle itemStyle = comboStyleOf("item " + weapon.id(), weapon.comboStyle());
        // (the assassin's class style was for its old combo plan; its combo is drawn by the combo itself now)
        ComboStyle classStyle = classService == null ? null
                : classService.currentClass(profileManager.getOrCreate(attacker))
                        .filter(definition -> !definition.id().equals("assassin"))
                        .map(definition -> comboStyleOf("class " + definition.id(), definition.comboStyle())).orElse(null);
        for (ComboStyle style : new ComboStyle[]{itemStyle, classStyle}) {
            if (dualLight && style == classStyle) break;
            ComboStyle.Stage stage = style == null ? null : style.stageFor(step);
            if (stage == null) continue;
            ComboStyle.Stage chosen = stage;
            net.tkgon.mc.iruuRPG.skill.SkillLook look = style.look() != null ? style.look()
                    : itemStyle != null && itemStyle.look() != null ? itemStyle.look()
                    : classStyle != null ? classStyle.look() : null;
            attackEffects.playStyledSlash(attacker, weapon, chosen, look, repeat, false);
            return;
        }
        if (step.heavy()) {
            attackEffects.playHeavySlash(attacker, weapon);
            return;
        }
        if (step.finisher()) {
            attackEffects.playComboSlash(attacker, weapon, 3);
            return;
        }
        if (dualWielding(attacker)) {
            RpgItemDefinition[] pair = dualWeapons(attacker, weapon);
            attackEffects.playDualSlash(attacker, pair[0], pair[1]);
            return;
        }
        String classId = classService == null ? "" : classService.currentClass(profileManager.getOrCreate(attacker)).map(definition -> definition.id()).orElse("");
        if (classId.equals("warrior")) {
            attackEffects.playComboSlash(attacker, weapon, step.visual());
            return;
        }
        attackEffects.playMeleeSlash(attacker, weapon);
    }

    public void playMeleeSlash(Player attacker, RpgItemDefinition weapon) {
        // an assassin with a melee weapon in each hand: the crossing slashes of the two hands, on every swing
        if (dualWielding(attacker)) {
            RpgItemDefinition[] pair = dualWeapons(attacker, weapon);
            attackEffects.playDualSlash(attacker, pair[0], pair[1]);
            return;
        }
        // warrior and assassin: a chain of slashes whose direction changes from stage to stage
        String classId = classService == null ? "" : classService.currentClass(profileManager.getOrCreate(attacker)).map(definition -> definition.id()).orElse("");
        if (classId.equals("warrior")) {
            attackEffects.playComboSlash(attacker, weapon);
            return;
        }
        attackEffects.playMeleeSlash(attacker, weapon);
    }

    /** The usable melee weapon in the off hand of an assassin with 固有0, or null. */
    public RpgItemDefinition dualOffWeapon(Player attacker) {
        if (!hasSkill(attacker, ASSASSIN_DUAL_SKILL)) return null;
        RpgItemDefinition offWeapon = itemIdentifier.itemId(attacker.getInventory().getItemInOffHand()).flatMap(itemRegistry::find).orElse(null);
        boolean usable = offWeapon != null && offWeapon.isWeaponLike() && offWeapon.attackType() == AttackType.MELEE && canUseItem(attacker, offWeapon);
        return usable ? offWeapon : null;
    }

    /** The two weapons {main, off} of a dual-wielding assassin, for effects that show the colour of each hand's weapon. */
    private RpgItemDefinition[] dualWeapons(Player attacker, RpgItemDefinition fallbackMain) {
        RpgItemDefinition main = weaponInMainHand(attacker);
        return new RpgItemDefinition[]{main != null ? main : fallbackMain, dualOffWeapon(attacker)};
    }

    /** True for an assassin (固有0) who holds a usable melee weapon in the off hand. */
    public boolean dualWielding(Player attacker) {
        if (!hasSkill(attacker, ASSASSIN_DUAL_SKILL)) return false;
        RpgItemDefinition offWeapon = itemIdentifier.itemId(attacker.getInventory().getItemInOffHand()).flatMap(itemRegistry::find).orElse(null);
        return offWeapon != null && offWeapon.isWeaponLike() && offWeapon.attackType() == AttackType.MELEE && canUseItem(attacker, offWeapon);
    }

    public void playRangeTrail(Player attacker, RpgItemDefinition weapon, Location hitLocation) {
        attackEffects.playRangeTrail(attacker, weapon, hitLocation);
    }

    public void playRangeImpact(Player attacker, Location hitLocation, RpgItemDefinition weapon) {
        attackEffects.playRangeImpact(attacker, hitLocation, weapon);
    }

    /**
     * A melee swing that hit nothing directly still splashes: every enemy within the melee area, around a point just
     * in front of the player, takes the area share of the damage a direct hit on it would do (its own defense
     * counts). A direct hit never comes through here (it has its own area damage), so nothing is counted twice.
     */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon) {
        applySwingAreaDamage(attacker, weapon, null);
    }

    /** {@code profile} (null: the live one) is the attacker's profile with {@code weapon} in hand, for an off-hand swing. */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile) {
        applySwingAreaDamage(attacker, weapon, profile, 1.0);
    }

    /** {@code multiplier}: the damage multiplier of the attack in the light / heavy chain. */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile, double multiplier) {
        applySwingAreaDamage(attacker, weapon, profile, multiplier, 1.0);
    }

    /** {@code radiusScale}: how much wider the splash is (the assassin's finisher). */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile, double multiplier, double radiusScale) {
        applySwingAreaDamage(attacker, weapon, profile, multiplier, radiusScale, 0.0);
    }

    /** {@code fixedRadius}: when above 0, the splash has exactly this radius. */
    public void applySwingAreaDamage(Player attacker, RpgItemDefinition weapon, PlayerProfile profile, double multiplier, double radiusScale, double fixedRadius) {
        boolean baseMelee = !hasMeleeAreaSkill(attacker);
        double rate = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-damage-rate", 0.40)
                : areaDamageRate(AttackType.MELEE, false);
        double radius = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-radius", 2.0)
                : plugin.getConfig().getDouble("combat.area-radius", 3.0);
        if (rate <= 0.0 || radius <= 0.0) return;
        radius = fixedRadius > 0.0 ? fixedRadius : radius * Math.max(0.1, radiusScale);

        double reach = plugin.getConfig().getDouble("combat.melee-swing-area-reach", 1.8);
        Location center = attacker.getEyeLocation().add(attacker.getEyeLocation().getDirection().normalize().multiply(reach));
        PlayerProfile attackerProfile = profile != null ? profile : equipmentService.recalculate(attacker);
        for (Entity nearby : attacker.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (DeployService.isDeployEntity(target) || target instanceof Player || target.equals(attacker) || target.isInvulnerable()) continue;
            if (target.getLocation().add(0, target.getHeight() / 2.0, 0).distanceSquared(center) > radius * radius) continue;

            AttackDamage attack = calculateDamage(attackerProfile, target, weapon);
            double areaDamage = round(attack.result().damage() * rate * multiplier);
            if (areaDamage <= 0.0) continue;
            if (applyDirectDamage(attacker, target, areaDamage, weapon, false)) {
                playMeleeImpact(attacker, target);
                sendDamageDebug(attacker, AttackType.MELEE, target, areaDamage, false, true);
            }
        }
    }

    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType) {
        applyAreaDamage(attacker, mainVictim, sourceDamage, attackType, null);
    }

    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType, RpgItemDefinition weapon) {
        applyAreaDamage(attacker, mainVictim, sourceDamage, attackType, weapon, 1.0);
    }

    /** {@code radiusScale}: how much wider the splash is (the assassin's finisher). */
    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType, RpgItemDefinition weapon, double radiusScale) {
        applyAreaDamage(attacker, mainVictim, sourceDamage, attackType, weapon, radiusScale, 0.0);
    }

    /** {@code fixedRadius}: when above 0, the splash has exactly this radius. */
    public void applyAreaDamage(Player attacker, LivingEntity mainVictim, double sourceDamage, AttackType attackType, RpgItemDefinition weapon, double radiusScale, double fixedRadius) {
        if (sourceDamage <= 0.0) return;

        // melee: everyone has a small area (a weaker copy of the warrior's); the warrior's milestone makes it full size
        boolean baseMelee = attackType == AttackType.MELEE && !hasMeleeAreaSkill(attacker);
        boolean expanded = attackType == AttackType.RANGE && hasSkill(attacker, RANGE_AREA_EXPAND_SKILL);
        double damageRate = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-damage-rate", 0.40)
                : areaDamageRate(attackType, expanded);
        if (damageRate <= 0.0) return;

        double radius = baseMelee
                ? plugin.getConfig().getDouble("combat.melee-area-base-radius", 2.0)
                : expanded
                ? plugin.getConfig().getDouble("combat.range-area-expanded-radius", 5.0)
                : plugin.getConfig().getDouble("combat.area-radius", 3.0);
        radius = fixedRadius > 0.0 ? fixedRadius : radius * Math.max(0.1, radiusScale);
        double radiusSquared = radius * radius;
        double areaDamage = round(sourceDamage * damageRate);
        if (areaDamage <= 0.0) return;

        for (Entity nearby : mainVictim.getNearbyEntities(radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (DeployService.isDeployEntity(target)) continue;
            if (target instanceof Player) continue;
            if (target.equals(mainVictim) || target.equals(attacker)) continue;
            if (target.getLocation().distanceSquared(mainVictim.getLocation()) > radiusSquared) continue;
            if (!isInFront(attacker, target)) continue;

            boolean applied = attackType == AttackType.RANGE
                    ? applyRangeDamage(attacker, target, areaDamage, weapon, false)
                    : applyDirectDamage(attacker, target, areaDamage, weapon, false);
            if (applied) {
                if (attackType == AttackType.MELEE) {
                    playMeleeImpact(attacker, target);
                }
                sendDamageDebug(attacker, attackType, target, areaDamage, false, true);
            }
        }
    }

    /**
     * The hurt effect of a melee hit. A direct hit does not knock the target back; set
     * {@code combat.melee-area-knockback-horizontal / -vertical} above 0 to bring a push back.
     */
    public void playMeleeImpact(Player attacker, LivingEntity target) {
        if (target == null || target.isDead() || !target.isValid()) return;

        attackEffects.playRangeHurt(target);
        double horizontal = plugin.getConfig().getDouble("combat.melee-area-knockback-horizontal", 0.0);
        double vertical = plugin.getConfig().getDouble("combat.melee-area-knockback-vertical", 0.0);
        if (horizontal <= 0.0 && vertical <= 0.0) return;

        Vector direction = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        direction.setY(0.0);
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = attacker.getLocation().getDirection();
            direction.setY(0.0);
        }
        if (direction.lengthSquared() <= 1.0E-9) {
            direction = new Vector(0.0, 0.0, 1.0);
        } else {
            direction.normalize();
        }
        target.setVelocity(direction.multiply(horizontal).setY(vertical));
    }

    public void sendDamageDebug(Player attacker, AttackType attackType, LivingEntity victim, double damage, boolean critical, boolean area) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage-in-creative", true)) return;
        if (attacker.getGameMode() != GameMode.CREATIVE) return;

        String label = area ? "範囲" : "直撃";
        String criticalSuffix = critical ? " 会心" : "";
        attacker.sendMessage("[iruuRPG] " + attackType.displayName()
                + " " + label
                + " ダメージ: " + format(damage)
                + criticalSuffix
                + " -> " + victim.getName());
    }

    public void sendMissDebug(Player attacker, AttackType attackType) {
        if (!plugin.getConfig().getBoolean("combat.debug-damage-in-creative", true)) return;
        if (attacker.getGameMode() != GameMode.CREATIVE) return;

        attacker.sendMessage("[iruuRPG] " + attackType.displayName() + " は外れた。");
    }

    public void applyPlayerDamage(Player player, PlayerProfile profile, double damage) {
        // an invulnerable moment, a guard and the warrior's shields cut the damage (shields break)
        if (guard != null) damage = guard.absorb(player, damage);
        double finalDamage = statusEffectService.modifyIncomingDamage(player, damage);
        // during the item swap skill a blow of 40% of the assassin's life or more is taken by the clones of the skill (all destroyed) and nullified
        if (assassinFrenzy(player) > 0 && AssassinClones.sacrifice(player, finalDamage, profile.maxHp(), plugin.getConfig().getDouble("combat.combo.assassin_melee.swap-guard-share", 0.4))) return;
        profile.setCurrentHp(profile.currentHp() - finalDamage);

        if (profile.currentHp() <= 0.0 && !player.isDead()) {
            playerBars.kill(player, profile);
            return;
        }

        playerBars.sync(player, profile);
    }

    public boolean healPlayer(Player player, double amount) {
        if (amount <= 0.0 || player.isDead()) return false;

        PlayerProfile profile = equipmentService.recalculate(player);
        double before = profile.currentHp();
        profile.setCurrentHp(before + amount);
        double healed = profile.currentHp() - before;
        // Class effects that scale with healing (the healer's pulse) count the whole heal, overheal included.
        if (Math.abs(healed) <= 1.0E-9) {
            if (classEffectService != null) {
                classEffectService.onSelfHealed(player, amount);
            }
            return false;
        }

        playerBars.sync(player, profile);
        attackEffects.playHealNumber(player, healed);
        if (classEffectService != null) {
            classEffectService.onSelfHealed(player, amount);
        }
        return true;
    }

    /** A heal cast by {@code caster}: class bonuses to the caster's heals (e.g. the healer's protection) apply. */
    public boolean healPlayerBy(Player caster, Player target, double amount) {
        double multiplier = classEffectService == null ? 1.0 : classEffectService.healMultiplier(caster);
        return healPlayer(target, amount * multiplier);
    }

    /** The warrior's full melee area (milestone "melee_area"); everyone else has the smaller base area. */
    private boolean hasMeleeAreaSkill(Player attacker) {
        return hasSkill(attacker, MELEE_AREA_SKILL);
    }

    /** True when this player's class has the skill flag {@code skillName} (an innate or unlocked passive). */
    public boolean hasClassSkill(Player player, String skillName) {
        return hasSkill(player, skillName);
    }

    // ---- the assassin's two hands ----------------------------------------------------------------------------------

    private static final String ASSASSIN_DUAL_SKILL = "assassin_dual";
    private static final String ASSASSIN_DOUBLE_SKILL = "assassin_double";

    /**
     * The hand a melee attack is made with. {@code profile} is the attacker's profile as if that weapon were in the
     * main hand (null: the live profile). {@code other*} is the weapon of the other hand, for a double strike.
     */
    public record MeleeHand(RpgItemDefinition weapon, PlayerProfile profile, boolean offHand,
                            RpgItemDefinition otherWeapon, PlayerProfile otherProfile, boolean doubleStrike) {
    }

    private final java.util.Map<UUID, Boolean> nextOffHand = new java.util.HashMap<>();

    /**
     * Who hits this time. An assassin with 固有0 who holds a melee weapon in each hand hits with the main hand, then
     * the off hand, then the main hand again ... (the hotbar does not change); with 固有1 there is a 10% chance
     * (class-effects.assassin.double-chance) that both hands hit at once. Anybody else: the main-hand weapon.
     */
    public MeleeHand nextMeleeHand(Player attacker, RpgItemDefinition mainWeapon) {
        MeleeHand plain = new MeleeHand(mainWeapon, null, false, null, null, false);
        if (!hasSkill(attacker, ASSASSIN_DUAL_SKILL)) return plain;

        ItemStack offItem = attacker.getInventory().getItemInOffHand();
        RpgItemDefinition offWeapon = itemIdentifier.itemId(offItem).flatMap(itemRegistry::find).orElse(null);
        if (offWeapon == null || !offWeapon.isWeaponLike() || offWeapon.attackType() != AttackType.MELEE
                || !canUseItem(attacker, offWeapon)) {
            nextOffHand.remove(attacker.getUniqueId());
            return plain;
        }

        boolean useOff = nextOffHand.getOrDefault(attacker.getUniqueId(), false);
        nextOffHand.put(attacker.getUniqueId(), !useOff);
        boolean both = hasSkill(attacker, ASSASSIN_DOUBLE_SKILL)
                && java.util.concurrent.ThreadLocalRandom.current().nextDouble() < plugin.getConfig().getDouble("class-effects.assassin.double-chance", 0.10);
        PlayerProfile offProfile = equipmentService.profileHolding(attacker, offItem);
        return useOff
                ? new MeleeHand(offWeapon, offProfile, true, mainWeapon, null, both)
                : new MeleeHand(mainWeapon, null, false, offWeapon, offProfile, both);
    }

    /** Damage of a melee hit made with {@code hand}. */
    public AttackDamage calculateMeleeDamage(Player attacker, LivingEntity victim, MeleeHand hand) {
        PlayerProfile profile = hand.profile() != null ? hand.profile() : equipmentService.recalculate(attacker);
        return calculateDamage(profile, victim, hand.weapon());
    }

    /**
     * The other hand's hit of a double strike: lands at the same time as the first, shown as two slashes crossing.
     * (It is an ordinary melee hit with the other weapon; the area splash and the status effects work as usual.)
     */
    public void doubleStrikeHit(Player attacker, LivingEntity victim, MeleeHand hand) {
        if (hand.otherWeapon() == null || victim.isDead() || !victim.isValid()) return;
        PlayerProfile profile = hand.otherProfile() != null ? hand.otherProfile() : equipmentService.recalculate(attacker);
        AttackDamage attack = calculateDamage(profile, victim, hand.otherWeapon());
        double damage = round(attack.result().damage() * tripleScale(attacker));
        boolean critical = attack.result().critical();
        if (!applyDirectDamage(attacker, victim, damage, hand.otherWeapon(), critical)) return;
        sendDamageDebug(attacker, AttackType.MELEE, victim, damage, critical, false);
        applyAreaDamage(attacker, victim, damage, AttackType.MELEE, hand.otherWeapon());
    }

    // ---- the ranger's enhanced state: every normal attack is three hits of 70% ---------------------------------------

    private static final double TRIPLE_RATE = 0.7;
    private static final long TRIPLE_GAP_TICKS = 3L;

    /** True while the ranger's 本能開放 is on: normal attacks (and deployed weapons) hit three times for 70%. */
    public boolean tripleActive(Player player) {
        return classEffectService != null && classEffectService.instinctActive(player.getUniqueId());
    }

    /** What the first hit of a normal attack is multiplied by (0.7 in the enhanced state, else 1). */
    public double tripleScale(Player player) {
        return tripleActive(player) ? TRIPLE_RATE : 1.0;
    }

    /** The two further 70% hits of an enhanced normal attack, a few ticks apart (the first one is the normal hit). */
    public void scheduleTripleHits(Player attacker, LivingEntity victim, RpgItemDefinition weapon, AttackType type) {
        for (int index = 1; index <= 2; index++) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (!attacker.isOnline() || attacker.isDead() || victim.isDead() || !victim.isValid()) return;
                AttackDamage attack = calculateDamage(attacker, victim, weapon);
                double damage = round(attack.result().damage() * TRIPLE_RATE);
                boolean critical = attack.result().critical();
                boolean applied = type == AttackType.RANGE
                        ? applyRangeDamage(attacker, victim, damage, weapon, critical)
                        : applyDirectDamage(attacker, victim, damage, weapon, critical);
                if (!applied) return;
                if (type == AttackType.MELEE) {
                    playMeleeImpact(attacker, victim);
                    playMeleeSlash(attacker, weapon);
                }
                sendDamageDebug(attacker, type, victim, damage, critical, false);
            }, TRIPLE_GAP_TICKS * index);
        }
    }

    private boolean hasSkill(Player attacker, String skillName) {
        if (classService == null) return false;
        return classService.hasSkill(profileManager.getOrCreate(attacker), skillName);
    }

    public void setClassEffectService(ClassEffectService classEffectService) {
        this.classEffectService = classEffectService;
    }

    public void setClassService(ClassService classService) {
        this.classService = classService;
    }

    private double areaDamageRate(AttackType attackType, boolean expanded) {
        return switch (attackType) {
            case MELEE -> plugin.getConfig().getDouble("combat.melee-area-damage-rate", 0.65);
            case RANGE -> expanded
                    ? plugin.getConfig().getDouble("combat.range-area-expanded-damage-rate", 0.60)
                    : plugin.getConfig().getDouble("combat.range-area-damage-rate", 0.45);
            case DEPLOY, SPECIAL -> 0.0;
        };
    }

    /** What a mob hits with: its weapon (or a plain physical melee one), in the mob's own attack element if it has one. */
    private RpgItemDefinition mobWeapon(LivingEntity attacker) {
        RpgItemDefinition weapon = mobService.attackWeapon(attacker)
                .filter(RpgItemDefinition::isWeaponLike)
                .orElseGet(this::fallbackMobWeapon);
        return mobService.attackElement(attacker).map(weapon::withElement).orElse(weapon);
    }

    private RpgItemDefinition fallbackMobWeapon() {
        return new RpgItemDefinition(
                "__mob_attack__",
                Material.STONE_SWORD,
                ItemVisualOptions.NONE,
                "Mob Attack",
                0,
                Rarity.COMMON,
                Element.WHITE,
                DamageKind.PHYSICAL,
                AttackType.MELEE,
                ItemSkillType.NONE,
                "",
                java.util.Map.of(),
                null,
                new StatSet(),
                new ElementStatSet(),
                List.of()
        );
    }

    private double scaleIncomingRpgMobDamage(double damage, PlayerProfile victimProfile) {
        if (damage <= 0.0) return 0.0;

        double multiplier = Math.max(0.0, plugin.getConfig().getDouble("mob.player-damage-multiplier", 0.10));
        return round(damage * multiplier);
    }

    private static final String ATTACK_SPEED_COOLDOWN_SKILL = "attack_speed_cooldown";

    /**
     * What a skill's cooldown is multiplied by for this player: with the mage's "attack_speed_cooldown" node it is
     * 1 - attack speed % (never below 0.2); for everybody else 1 (attack speed does nothing to skill cooldowns).
     */
    public double skillCooldownMultiplier(Player player) {
        if (!hasSkill(player, ATTACK_SPEED_COOLDOWN_SKILL)) return 1.0;
        double attackSpeed = equipmentService.recalculate(player).finalStats().get(StatType.ATTACK_SPEED);
        return Math.max(0.2, Math.min(1.0, 1.0 - attackSpeed / 100.0));
    }

    /** The item swap skill of the assassin: its clones (see {@link AssassinClones#launchSwap}) for {@code lifeTicks} ticks. */
    public void assassinSwapClones(Player attacker, RpgItemDefinition weapon, int lifeTicks) {
        AssassinClones.launchSwap(kit(attacker, weapon), lifeTicks);
    }

    /** The place the assassin's item swap skill was used, while it lasts (null otherwise). */
    public Location frenzyCenter(Player player) {
        return classEffectService == null ? null : classEffectService.frenzyCenter(player);
    }

    /** 0 when the assassin is not in a frenzy (SATUJIN), else its level; see {@link ClassEffectService#activateFrenzy}. */
    public int assassinFrenzy(Player player) {
        return classEffectService == null ? 0 : classEffectService.frenzyLevel(player);
    }

    private int effectiveCooldownTicks(Player player, AttackType attackType) {
        int baseTicks = attackCooldowns.cooldownTicks(attackType);
        if (baseTicks <= 0 || attackType == AttackType.SPECIAL) return baseTicks;
        if (attackType == AttackType.DEPLOY) return baseTicks;

        PlayerProfile profile = equipmentService.recalculate(player);
        double attackSpeed = profile.finalStats().get(StatType.ATTACK_SPEED);
        // in a frenzy the assassin's normal attacks come faster (class-effects.assassin.frenzy-attack-speed, in % of attack speed)
        if (attackType == AttackType.MELEE && assassinFrenzy(player) > 0) {
            attackSpeed += plugin.getConfig().getDouble("class-effects.assassin.frenzy-attack-speed", 40.0);
        }
        double multiplier = Math.max(0.1, 1.0 + attackSpeed / 100.0);
        int minTicks = Math.max(1, plugin.getConfig().getInt("combat.min-cooldown-ticks", 1));
        return Math.max(minTicks, (int) Math.round(baseTicks / multiplier));
    }

    public void showItemCooldown(Player player, AttackType attackType, int ticks) {
        if (attackType == AttackType.SPECIAL) return;

        if (ticks <= 0) return;

        for (Material material : cooldownMaterials(attackType)) {
            player.setCooldown(material, ticks);
        }
    }

    private Set<Material> cooldownMaterials(AttackType attackType) {
        Set<Material> materials = new HashSet<>();
        for (RpgItemDefinition definition : itemRegistry.all().values()) {
            if (!definition.isWeaponLike()) continue;
            if (definition.attackType() != attackType) continue;
            materials.add(definition.material());
        }
        return materials;
    }

    private boolean isInFront(Player attacker, LivingEntity target) {
        if (!plugin.getConfig().getBoolean("combat.area-requires-facing", true)) {
            return true;
        }

        Vector toTarget = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
        if (toTarget.lengthSquared() <= 1.0E-9) return false;
        return attacker.getLocation().getDirection().dot(toTarget.normalize()) >= 0.0;
    }

    /**
     * What a ranged shot can hit. Players (a shot can never hurt one), armor stands and the tutorial's NPC / rat of
     * someone else are left out of the trace, so they do not block a shot at a mob behind them.
     */
    public boolean isAttackTarget(Entity entity, Player attacker) {
        return entity instanceof LivingEntity living
                && !entity.equals(attacker)
                && !entity.isDead()
                && !(entity instanceof Player)
                && !(entity instanceof ArmorStand)
                && !DeployService.isDeployEntity(entity)
                && !net.tkgon.mc.iruuRPG.npc.NpcService.isNpc(entity)
                && net.tkgon.mc.iruuRPG.questline.QuestMobs.mayHit(attacker, entity)
                && !(isTutorialProtected(living) && !tutorialHook.isOwnTarget(attacker, living));
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        return attribute != null ? attribute.getValue() : Math.max(1.0, entity.getHealth());
    }

    private boolean applyNoKnockbackDamage(Player attacker, LivingEntity victim, double damage) {
        if (DeployService.isDeployEntity(victim)) {
            return false;
        }

        if (mobService != null && mobService.applyDamage(victim, damage, attacker)) {
            return true;
        }

        if (victim.isDead() || !victim.isValid()) return false;

        double nextHealth = victim.getHealth() - Math.max(0.0, damage);
        if (nextHealth <= 0.0) {
            victim.setHealth(0.0);
            return true;
        }

        victim.setHealth(Math.min(nextHealth, maxHealth(victim)));
        updateDebugTargetName(victim);
        return true;
    }

    private boolean hasRemainingHealth(LivingEntity victim) {
        return victim.isValid() && !victim.isDead() && victim.getHealth() > 0.0;
    }

    private double modifiedCustomDamage(Player attacker, LivingEntity victim, RpgItemDefinition weapon, double damage) {
        double modified = statusEffectService.modifyIncomingDamage(victim, damage);
        double outgoing = statusEffectService.modifyOutgoingDamage(attacker, weapon, modified);
        // the ranger's loop: damage from a deployed weapon makes melee attacks stronger for a while
        if (rangerMoves != null && attacker != null && weapon != null && weapon.attackType() == AttackType.MELEE && isRanger(attacker)) {
            outgoing *= rangerMoves.meleeMultiplier(attacker);
        }
        // the ranger's arrows mark an enemy: the melee and deployed weapons hit it harder
        if (rangerMoves != null && attacker != null && weapon != null && weapon.attackType() != AttackType.RANGE && isRanger(attacker)) {
            outgoing *= rangerMoves.markMultiplier(victim);
        }
        return outgoing;
    }

    private void updateDebugTargetName(LivingEntity victim) {
        if (debugTargetService == null) return;
        if (!debugTargetService.isDebugTarget(victim)) return;
        if (!victim.isValid() || victim.isDead()) return;

        debugTargetService.updateName(victim);
    }

    private double round(double value) {
        return Math.floor(value * 10.0 + 0.5) / 10.0;
    }

    private String format(double value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setGroupingUsed(true);
        format.setMinimumFractionDigits(0);
        format.setMaximumFractionDigits(Math.abs(value - Math.rint(value)) < 1.0E-9 ? 0 : 1);
        return format.format(value);
    }

    public record AttackDamage(DamageResult result, PlayerProfile victimProfile) {
    }

    public record RangeTrace(LivingEntity victim, Location hitLocation) {
    }
}
