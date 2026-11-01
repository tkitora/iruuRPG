package net.tkgon.mc.iruuRPG.listener;

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.AttackType;
import net.tkgon.mc.iruuRPG.combat.DeployService;
import net.tkgon.mc.iruuRPG.combat.ItemSkillService;
import net.tkgon.mc.iruuRPG.combat.SkillInput;
import net.tkgon.mc.iruuRPG.combat.SkillSlots;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.GameMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffectType;

import java.util.OptionalDouble;

public final class CombatListener implements Listener {

    private final AttackService attackService;
    private final DeployService deployService;
    private final ItemSkillService itemSkillService;
    private final RightClickGuard rightClickGuard;
    private final SkillSlots skillSlots;

    public CombatListener(AttackService attackService, DeployService deployService, ItemSkillService itemSkillService, RightClickGuard rightClickGuard,
                          SkillSlots skillSlots) {
        this.rightClickGuard = rightClickGuard;
        this.attackService = attackService;
        this.deployService = deployService;
        this.itemSkillService = itemSkillService;
        this.skillSlots = skillSlots;
        attackService.setUsualHeavy(this::usualHeavy);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeployDamage(EntityDamageEvent event) {
        if (!DeployService.isDeployEntity(event.getEntity())) return;

        event.setCancelled(true);
        event.setDamage(0.0);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPreCustomRpgAttack(PrePlayerAttackEntityEvent event) {
        if (!event.willAttack()) return;
        if (DeployService.isDeployEntity(event.getAttacked())) {
            event.setCancelled(true);
            return;
        }

        Player attacker = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (weapon == null || !weapon.isWeaponLike()) return;

        event.setCancelled(true);
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        if (!(event.getAttacked() instanceof LivingEntity victim)) return;

        // the left button of a ranged or deployed weapon is the other button (the weapon's skill, or 中 after Q)
        if (weapon.attackType() != AttackType.MELEE) {
            skillClick(attacker, weapon, false);
            return;
        }
        handleCustomMelee(attacker, victim, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onVanillaPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (isBlockedVanillaDebuff(event.getCause())) {
            event.setCancelled(true);
            event.setDamage(0.0);
            clearBlockedVanillaDebuffs(player);
            return;
        }
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            if (attackService.isPlayerVersusPlayer(byEntity.getDamager(), byEntity.getEntity())) {
                event.setCancelled(true);
                event.setDamage(0.0);
                return;
            }
            if (attackService.isCustomWeaponAttack(byEntity.getDamager())) return;
        }

        OptionalDouble rpgMobDamage = attackService.incomingRpgMobDamage(event, player);
        double damage = rpgMobDamage.orElse(Math.max(0.0, event.getFinalDamage()));
        if (damage <= 0.0) {
            if (rpgMobDamage.isPresent()) {
                event.setDamage(0.0);
            }
            return;
        }

        event.setDamage(0.0);
        attackService.applyPlayerDamage(player, attackService.profileForPlayer(player), damage);
        if (rpgMobDamage.isPresent()) {
            attackService.applyRpgMobStatusEffects(event, player, damage);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockedVanillaDebuff(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof LivingEntity entity)) return;
        if (event.getNewEffect() == null) return;
        if (!isBlockedVanillaDebuff(event.getNewEffect().getType())) return;

        event.setCancelled(true);
        clearBlockedVanillaDebuffs(entity);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onCustomRpgAttack(EntityDamageByEntityEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        Player attacker = attackService.resolvePlayerAttacker(event.getDamager());
        if (attacker == null) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (DeployService.isDeployEntity(victim)) {
            event.setCancelled(true);
            event.setDamage(0.0);
            return;
        }

        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (weapon == null || !weapon.isWeaponLike()) return;

        event.setCancelled(true);
        event.setDamage(0.0);
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        if (weapon.attackType() != AttackType.MELEE) {
            skillClick(attacker, weapon, false);
            return;
        }
        handleCustomMelee(attacker, victim, weapon);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAdventureRightClickBlock(PlayerInteractEvent event) {
        if (event.getPlayer().getGameMode() != GameMode.ADVENTURE) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        onEntityRightClick(event.getPlayer(), event.getHand(), event);
    }

    /** Armor stands and other "interact at" entities take the click through this event first. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onRightClickEntityAt(PlayerInteractAtEntityEvent event) {
        onEntityRightClick(event.getPlayer(), event.getHand(), event);
    }

    /**
     * A right click that lands on an entity: the weapon's right-click (manual skill, ranged attack, deploy) is used
     * here, because the entity can take the click before the use-item event ever fires. The click is marked, so the
     * use-item event that may follow does not use the weapon a second time.
     */
    private void onEntityRightClick(Player player, EquipmentSlot hand, Cancellable event) {
        if (hand != EquipmentSlot.HAND) return;

        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (rightClickGuard.weaponRecent(player)) {
            event.setCancelled(true);
            return;
        }
        if (useWeaponRightClick(player, weapon)) {
            rightClickGuard.markWeapon(player);
            event.setCancelled(true);
            return;
        }

        if (player.getGameMode() != GameMode.ADVENTURE) return;
        event.setCancelled(true);
    }

    /** Uses the right-click of the weapon in hand (a skill, the weapon's skill, or ranged / deploy attack). False when it has none. */
    private boolean useWeaponRightClick(Player player, RpgItemDefinition weapon) {
        // the other button of a melee weapon (its skill); after Q, a skill
        if (weapon != null && weapon.isWeaponLike() && skillClick(player, weapon, true)) return true;
        if (!isRangedRightClickWeapon(weapon)) return false;

        handleRpgRightClick(player, weapon);
        return true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDamage(PlayerItemDamageEvent event) {
        event.setDamage(0);
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onMeleeSwing(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.LEFT_CLICK_AIR && event.getAction() != Action.LEFT_CLICK_BLOCK) return;

        Player attacker = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (weapon == null || !weapon.isWeaponLike()) return;
        if (weapon.attackType() != AttackType.MELEE) {
            // the left button of a ranged or deployed weapon is its other button: the weapon's skill, or 中 after Q
            event.setCancelled(true);
            net.tkgon.mc.iruuRPG.combat.ClickSignals.left(attacker, () -> skillClick(attacker, weapon, false));
            return;
        }

        event.setCancelled(true);
        // only a real press of the left button counts (not the answer to an arm swing of the plugin, nor a held right button)
        net.tkgon.mc.iruuRPG.combat.ClickSignals.left(attacker, () -> meleeClick(attacker, weapon));
    }

    /**
     * A click of the weapon ({@link SkillInput}): true when it was used up (a skill, the weapon's skill, the same click again); false when it is
     * the weapon's normal attack.
     */
    private boolean skillClick(Player attacker, RpgItemDefinition weapon, boolean rightClick) {
        // (a deployed weapon has no 小・中: a Q of another weapon does not wait for its click)
        if (weapon.attackType() == AttackType.DEPLOY) SkillInput.forgetQ(attacker);
        SkillInput.Click click = SkillInput.click(attacker, AttackService.button(weapon, rightClick));
        switch (click.kind()) {
            case SKILL -> {
                skillSlots.use(attacker, click.slot(), weapon);
                return true;
            }
            case ITEM_SKILL -> {
                // (a weapon with no skill still keeps the click from being something else, as its heavy attacks' button did)
                return itemSkillService.useByKey(attacker, weapon) || attackService.hasHeavies(attacker, weapon);
            }
            case NOTHING -> {
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * F with a weapon in hand: util, or the ULT with shift; nothing changes hands. A deployed weapon has no util (only the ULT and its own skill:
     * the user's wish of 2026-11-02). The editors and the tutorial come first.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSwapKey(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike()) return;
        event.setCancelled(true);
        SkillSlots.Slot slot = SkillInput.pressF(player);
        if (weapon.attackType() == AttackType.DEPLOY && slot != SkillSlots.Slot.ULT) return;
        skillSlots.use(player, slot, weapon);
    }

    private void meleeClick(Player attacker, RpgItemDefinition weapon) {
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        // (Q and then this one: 小)
        if (skillClick(attacker, weapon, false)) return;
        meleeSwing(attacker, weapon, false);
    }

    /** The heavy attack of a melee weapon's usual chain (a weapon outside the class's combos): swung as a normal attack is. */
    private void usualHeavy(Player attacker, RpgItemDefinition weapon) {
        meleeSwing(attacker, weapon, true);
    }

    private void meleeSwing(Player attacker, RpgItemDefinition weapon, boolean heavy) {
        if (!attackService.tryStart(attacker, AttackType.MELEE)) return;

        // a class's combo with a melee weapon
        if (!heavy && attackService.comboNormal(attacker, weapon)) return;
        net.tkgon.mc.iruuRPG.combat.MeleeCombo.Step step = attackService.nextMeleeStep(attacker, heavy);
        if (step == null) return;
        AttackService.MeleeHand hand = attackService.nextMeleeHand(attacker, weapon);
        if (hand.offHand()) attackService.swingOffHand(attacker);
        attackService.playMeleeSlash(attacker, hand.weapon(), step);
        // a swing that hits nothing directly still splashes the enemies around the spot in front of the player
        // (never for the arm swing of a direct hit: that already did its own area damage)
        Integer direct = lastDirectMelee.get(attacker.getUniqueId());
        if (direct == null || direct != org.bukkit.Bukkit.getCurrentTick()) {
            attackService.applySwingAreaDamage(attacker, hand.weapon(), hand.profile(), step.damageMultiplier(), step.areaScale(), step.areaRadius());
            if (step.extraHits() > 0) attackService.scheduleRepeats(attacker, null, hand.weapon(), step);
        }
    }

    /** The tick of each player's last direct melee hit: the arm swing that follows it is not a whiff. */
    private final java.util.Map<java.util.UUID, Integer> lastDirectMelee = new java.util.HashMap<>();

    private void handleCustomMelee(Player attacker, LivingEntity victim, RpgItemDefinition weapon) {
        if (victim instanceof Player) return;
        if (DeployService.isDeployEntity(victim)) return;
        if (weapon.attackType() != AttackType.MELEE) return;
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }
        // (a heavy attack's recovery also holds off an attack on an enemy)
        if (!net.tkgon.mc.iruuRPG.combat.ClickSignals.leftPossible(attacker)) return;
        if (skillClick(attacker, weapon, false)) return;
        if (!attackService.tryStart(attacker, AttackType.MELEE)) return;

        if (attackService.comboNormal(attacker, weapon)) return;
        net.tkgon.mc.iruuRPG.combat.MeleeCombo.Step step = attackService.nextMeleeStep(attacker, false);
        if (step == null) return;
        // a dual-wielding assassin hits with one hand and then the other
        AttackService.MeleeHand hand = attackService.nextMeleeHand(attacker, weapon);
        RpgItemDefinition used = hand.weapon();
        AttackService.AttackDamage attack = attackService.calculateMeleeDamage(attacker, victim, hand);
        double scale = attackService.tripleScale(attacker);
        double damage = Math.round(attack.result().damage() * scale * step.damageMultiplier() * 10.0) / 10.0;
        if (!attackService.applyDirectDamage(attacker, victim, damage, used, attack.result().critical())) {
            return;
        }

        if (hand.offHand()) attackService.swingOffHand(attacker);
        attackService.playMeleeImpact(attacker, victim);
        attackService.playMeleeSlash(attacker, used, step);
        attackService.sendDamageDebug(attacker, AttackType.MELEE, victim, damage, attack.result().critical(), false);
        attackService.applyAreaDamage(attacker, victim, damage, AttackType.MELEE, used, step.areaScale(), step.areaRadius());
        if (step.extraHits() > 0) attackService.scheduleRepeats(attacker, victim, used, step);
        lastDirectMelee.put(attacker.getUniqueId(), org.bukkit.Bukkit.getCurrentTick());
        attackService.normalAttackHit(attacker);
        if (hand.doubleStrike()) attackService.doubleStrikeHit(attacker, victim, hand);
        if (scale < 1.0) attackService.scheduleTripleHits(attacker, victim, used, AttackType.MELEE);
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onRangeUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player attacker = event.getPlayer();
        RpgItemDefinition weapon = attackService.weaponInMainHand(attacker);
        if (rightClickGuard.weaponRecent(attacker)) {
            // the same click was already used through the entity-interact event
            event.setCancelled(true);
            return;
        }
        if (useWeaponRightClick(attacker, weapon)) {
            rightClickGuard.markWeapon(attacker);
            event.setCancelled(true);
        }
    }

    /** The tick of each player's last drop from an inventory screen (a drop then is not the Q key of the hotbar). */
    private final java.util.Map<java.util.UUID, Integer> screenDropTick = new java.util.HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR)
    public void onScreenDrop(InventoryClickEvent event) {
        InventoryAction action = event.getAction();
        if (action != InventoryAction.DROP_ONE_SLOT && action != InventoryAction.DROP_ALL_SLOT && action != InventoryAction.DROP_ONE_CURSOR
                && action != InventoryAction.DROP_ALL_CURSOR) return;
        if (event.getWhoClicked() instanceof Player player) screenDropTick.put(player.getUniqueId(), org.bukkit.Bukkit.getCurrentTick());
    }

    /**
     * The Q key (and Ctrl + Q) of the hotbar never drops anything (items are given by trading): with a weapon in hand it is the first key of 小・中
     * (Q and then a button) or either Q of 大 (Q → Q); a deployed weapon has none of them (as before the redesign of 2026-11-02, the user's wish):
     * shift + Q with it takes its placement away. A drop from an inventory screen is left as it is; the editors and the menu item (which cancel the
     * drop first) come before.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDropKey(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        Integer screen = screenDropTick.get(player.getUniqueId());
        if (screen != null && screen == org.bukkit.Bukkit.getCurrentTick()) return;
        RpgItemDefinition weapon = attackService.definitionOf(event.getItemDrop().getItemStack());
        boolean isWeapon = weapon != null && weapon.isWeaponLike();
        // combat.q-key.keep-items (default true): false lets Q drop what is not a weapon, as before
        if (!isWeapon && !attackService.plugin().getConfig().getBoolean("combat.q-key.keep-items", true)) return;
        event.setCancelled(true);
        if (!isWeapon) return;
        boolean sneaking = player.isSneaking();
        // the key is read now (its tick), what it does runs when the item is back in the hand
        boolean deployed = weapon.attackType() == AttackType.DEPLOY;
        if (deployed) SkillInput.forgetQ(player);
        SkillSlots.Slot slot = deployed ? null : SkillInput.pressQ(player);
        // (the item is back in the hand after the event)
        org.bukkit.Bukkit.getScheduler().runTask(attackService.plugin(), () -> {
            if (!player.isOnline() || player.isDead()) return;
            RpgItemDefinition held = attackService.weaponInMainHand(player);
            if (held == null || !held.id().equals(weapon.id())) return;
            if (sneaking && attackService.removePlacement(player, held)) {
                SkillInput.forgetQ(player);
                return;
            }
            if (slot != null) skillSlots.use(player, slot, held);
        });
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        screenDropTick.remove(event.getPlayer().getUniqueId());
    }

    private boolean isRangedRightClickWeapon(RpgItemDefinition weapon) {
        return weapon != null
                && weapon.isWeaponLike()
                && (weapon.attackType() == AttackType.RANGE || weapon.attackType() == AttackType.DEPLOY);
    }

    private boolean isBlockedVanillaDebuff(EntityDamageEvent.DamageCause cause) {
        return cause == EntityDamageEvent.DamageCause.POISON
                || cause == EntityDamageEvent.DamageCause.WITHER;
    }

    private boolean isBlockedVanillaDebuff(PotionEffectType type) {
        return type == PotionEffectType.POISON
                || type == PotionEffectType.WITHER;
    }

    private void clearBlockedVanillaDebuffs(LivingEntity entity) {
        entity.removePotionEffect(PotionEffectType.POISON);
        entity.removePotionEffect(PotionEffectType.WITHER);
    }

    private void handleRpgRightClick(Player attacker, RpgItemDefinition weapon) {
        if (!attackService.canUseItem(attacker, weapon)) {
            attackService.sendLevelRequirement(attacker, weapon);
            return;
        }

        if (weapon.attackType() == AttackType.DEPLOY) {
            // a class with a combo with a deployed weapon: the normal attacks of the combo (at the pace of the attack speed)
            if (attackService.hasClassCombo(attacker, weapon)) {
                if (attackService.heldRangedClick(attacker) || !attackService.tryStartDeployCombo(attacker)) return;
                attackService.deployClick(attacker, weapon);
                return;
            }
            if (!attackService.tryStart(attacker, AttackType.DEPLOY)) {
                return;
            }
            deployService.deploy(attacker, weapon);
            return;
        }

        // a held right button is one click, not a stream of normal attacks of the combo (the mage's and the ranger's)
        if (((attackService.isMage(attacker) && attackService.comboAllowed(attacker, weapon)) || attackService.hasClassCombo(attacker, weapon))
                && attackService.heldRangedClick(attacker)) {
            return;
        }
        if (!attackService.tryStart(attacker, AttackType.RANGE)) {
            return;
        }
        // the mage's normal attack is a step of its combo
        if (attackService.mageNormal(attacker, weapon)) return;
        // the other classes' combos with a ranged weapon
        if (attackService.comboNormal(attacker, weapon)) return;

        if (!attackService.rangedIsInstant(attacker)) {
            // the shot flies at balance.projectile-speed and hits when it arrives
            attackService.fireRangedProjectile(attacker, weapon);
            return;
        }

        AttackService.RangeTrace trace = attackService.traceRange(attacker);
        attackService.playRangeTrail(attacker, weapon, trace.hitLocation());

        LivingEntity victim = trace.victim();
        if (victim == null) {
            attackService.sendMissDebug(attacker, AttackType.RANGE);
            return;
        }
        attackService.hitWithShot(attacker, weapon, victim, trace.hitLocation());
    }
}
