package net.tkgon.mc.iruuRPG.listener;

import net.tkgon.mc.iruuRPG.classsystem.ClassHotKey;
import net.tkgon.mc.iruuRPG.classsystem.ClassService;
import net.tkgon.mc.iruuRPG.classsystem.ClassSkillDefinition;
import net.tkgon.mc.iruuRPG.classsystem.ClassSkillRegistry;
import net.tkgon.mc.iruuRPG.combat.AttackService;
import net.tkgon.mc.iruuRPG.combat.ClassSkillService;
import net.tkgon.mc.iruuRPG.gui.MainMenuItemService;
import net.tkgon.mc.iruuRPG.player.PlayerProfile;
import net.tkgon.mc.iruuRPG.player.PlayerProfileManager;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.Cancellable;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class ClassSkillListener implements Listener {

    private final ClassService classService;
    private final ClassSkillRegistry skillRegistry;
    private final PlayerProfileManager profileManager;
    private final AttackService attackService;
    private final MainMenuItemService mainMenuItemService;
    private final ClassSkillService classSkillService;
    private final RightClickGuard rightClickGuard;
    private final Map<UUID, Map<String, Long>> cooldownUntilMillis = new HashMap<>();
    /** How long each running cooldown is in all (the action bar's shade, t140). */
    private final Map<UUID, Map<String, Long>> cooldownTotalMillis = new HashMap<>();

    public ClassSkillListener(
            ClassService classService,
            ClassSkillRegistry skillRegistry,
            PlayerProfileManager profileManager,
            AttackService attackService,
            MainMenuItemService mainMenuItemService,
            ClassSkillService classSkillService,
            RightClickGuard rightClickGuard
    ) {
        this.rightClickGuard = rightClickGuard;
        this.classService = classService;
        this.skillRegistry = skillRegistry;
        this.profileManager = profileManager;
        this.attackService = attackService;
        this.mainMenuItemService = mainMenuItemService;
        this.classSkillService = classSkillService;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (mainMenuItemService.isMenuItem(event.getItem())) return;

        ClassHotKey hotKey = hotKey(event);
        if (hotKey == null) return;
        // the answer to an arm swing the plugin played itself is not a click
        if ((hotKey == ClassHotKey.L || hotKey == ClassHotKey.SL)
                && (!net.tkgon.mc.iruuRPG.combat.ClickSignals.leftPossible(event.getPlayer()) || net.tkgon.mc.iruuRPG.combat.ClickSignals.echoSuspected(event.getPlayer()))) return;
        boolean rightClick = hotKey == ClassHotKey.R || hotKey == ClassHotKey.SR;
        if (rightClick && rightClickGuard.classRecent(event.getPlayer())) {
            // the same click already fired the skill through the entity-interact event
            event.setCancelled(true);
            return;
        }
        if (!tryTrigger(event.getPlayer(), hotKey)) return;

        if (rightClick) rightClickGuard.markClass(event.getPlayer());
        event.setCancelled(true);
    }

    /**
     * A right click that lands on an entity may never reach the use-item event (the entity takes it), so the
     * right-click skill key is handled here too. The click is marked: the use-item event that may follow is skipped.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        onEntityRightClick(event.getPlayer(), event.getHand());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        onEntityRightClick(event.getPlayer(), event.getHand());
    }

    private void onEntityRightClick(Player player, EquipmentSlot hand) {
        if (hand != EquipmentSlot.HAND) return;
        if (mainMenuItemService.isMenuItem(player.getInventory().getItemInMainHand())) return;
        if (rightClickGuard.classRecent(player)) return;
        if (!tryTrigger(player, player.isSneaking() ? ClassHotKey.SR : ClassHotKey.R)) return;

        rightClickGuard.markClass(player);
    }

    /**
     * The click that fired a skill is cancelled late (not at LOWEST), so the weapon's own right-click handler,
     * which skips cancelled events, still sees the click, like it does for a click in the air.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntityCancel(PlayerInteractEntityEvent event) {
        cancelIfSkillFired(event.getPlayer(), event.getHand(), event);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntityCancel(PlayerInteractAtEntityEvent event) {
        cancelIfSkillFired(event.getPlayer(), event.getHand(), event);
    }

    private void cancelIfSkillFired(Player player, EquipmentSlot hand, Cancellable event) {
        if (hand == EquipmentSlot.HAND && rightClickGuard.classRecent(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        // (the style of the keys: Q is the first or second of two keys, read by the combat listener)
        if (net.tkgon.mc.iruuRPG.combat.HeavyInput.keyStyle()) return;
        if (mainMenuItemService.isMenuItem(event.getItemDrop().getItemStack())) return;
        if (!tryTrigger(event.getPlayer(), ClassHotKey.Q)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        // (the style of the keys: F is the first or second of two keys, and the class's skill of F is the special: Q → F by default)
        if (net.tkgon.mc.iruuRPG.combat.HeavyInput.keyStyle()) return;
        if (mainMenuItemService.isMenuItem(event.getMainHandItem()) || mainMenuItemService.isMenuItem(event.getOffHandItem())) return;
        if (!tryTrigger(event.getPlayer(), ClassHotKey.F)) return;

        event.setCancelled(true);
    }

    /** The class's special (t138): its active skill of F. True when it was cast or told why not; false when the class has none yet. */
    public boolean special(Player player) {
        return tryTrigger(player, ClassHotKey.F);
    }

    private boolean tryTrigger(Player player, ClassHotKey hotKey) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        String skillId = classService.activeSkill(profile, hotKey);
        if (skillId == null || skillId.isBlank()) return false;

        ClassSkillDefinition skill = skillRegistry.find(skillId).orElse(null);
        if (skill == null) {
            player.sendMessage("[iruuRPG] クラススキルが見つかりません: " + skillId);
            return true;
        }

        // pressing the key again while the skill is up (the upgraded sanctuary's re-cast) is not a new cast
        if (classSkillService.tryRecast(player, skill)) return true;

        long remaining = remainingCooldownMillis(player, skill.id());
        if (remaining > 0L) {
            player.sendMessage("[iruuRPG] " + skill.name() + "のクールダウン中: " + formatSeconds(remaining) + "秒");
            return true;
        }
        ClassSkillService.Precheck precheck = classSkillService.precheck(player, skill);
        if (precheck.rejected()) {
            if (!precheck.rejection().isBlank()) {
                player.sendMessage("[iruuRPG] " + precheck.rejection());
            }
            return true;
        }
        if (!attackService.consumeMp(player, skill.cost())) {
            player.sendMessage("[iruuRPG] MPが足りません。");
            return true;
        }

        startCooldown(player, skill);
        // the damage dealt while the skill is up is counted for the action bar
        double activeSeconds = skill.values().getOrDefault("active-seconds", skill.values().getOrDefault("duration-seconds", 6.0));
        attackService.damageMeter().startSkill(player.getUniqueId(), Math.round(activeSeconds * 1000.0));
        if (precheck.hasEffect()) {
            classSkillService.execute(player, skill, precheck);
            return true;
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.35f);
        player.sendMessage("[iruuRPG] クラススキル: " + skill.name());
        if (!skill.description().isEmpty()) {
            player.sendMessage("[iruuRPG] " + skill.description().get(0));
        }
        return true;
    }

    private ClassHotKey hotKey(PlayerInteractEvent event) {
        Action action = event.getAction();
        boolean sneaking = event.getPlayer().isSneaking();
        if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            return sneaking ? ClassHotKey.SL : ClassHotKey.L;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            return sneaking ? ClassHotKey.SR : ClassHotKey.R;
        }
        return null;
    }

    /** The class skill of the player as the action bar shows it: its name and the cooldown left (0 = ready). Null without one. */
    public HudStatus hudStatus(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        for (String skillId : profile.activeClassSkills().values()) {
            if (skillId == null || skillId.isBlank()) continue;
            ClassSkillDefinition skill = skillRegistry.find(skillId).orElse(null);
            if (skill == null) continue;
            return new HudStatus(skill.name(), remainingCooldownMillis(player, skill.id()));
        }
        return null;
    }

    public record HudStatus(String name, long remainingMillis) {
    }

    /**
     * The special (the class's skill of F, t138) as the action bar's icon shows it (t140): {milliseconds left, the whole cooldown}, or null when the class has
     * none yet (the icon is locked).
     */
    public long[] specialCooldown(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        String skillId = classService.activeSkill(profile, ClassHotKey.F);
        if (skillId == null || skillId.isBlank()) return null;
        ClassSkillDefinition skill = skillRegistry.find(skillId).orElse(null);
        if (skill == null) return null;
        long left = remainingCooldownMillis(player, skill.id());
        if (left <= 0L) return new long[]{0L, 0L};
        Map<String, Long> totals = cooldownTotalMillis.get(player.getUniqueId());
        long total = totals == null ? left : totals.getOrDefault(skill.id().toLowerCase(Locale.ROOT), left);
        return new long[]{left, Math.max(left, total)};
    }

    private void startCooldown(Player player, ClassSkillDefinition skill) {
        long millis = Math.round(skill.cooldownTicks() * attackService.skillCooldownMultiplier(player)) * 50L;
        cooldownUntilMillis
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(skill.id().toLowerCase(Locale.ROOT), System.currentTimeMillis() + millis);
        cooldownTotalMillis.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(skill.id().toLowerCase(Locale.ROOT), millis);
    }

    private long remainingCooldownMillis(Player player, String skillId) {
        Map<String, Long> playerCooldowns = cooldownUntilMillis.get(player.getUniqueId());
        if (playerCooldowns == null) return 0L;

        return Math.max(0L, playerCooldowns.getOrDefault(skillId.toLowerCase(Locale.ROOT), 0L) - System.currentTimeMillis());
    }

    private String formatSeconds(long millis) {
        double seconds = millis / 1000.0;
        if (Math.abs(seconds - Math.rint(seconds)) < 1.0E-9) {
            return String.valueOf((long) Math.rint(seconds));
        }
        return String.format(Locale.ROOT, "%.1f", seconds)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}
