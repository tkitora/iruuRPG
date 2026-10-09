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
    private final Map<UUID, Map<String, Long>> cooldownUntilMillis = new HashMap<>();

    public ClassSkillListener(
            ClassService classService,
            ClassSkillRegistry skillRegistry,
            PlayerProfileManager profileManager,
            AttackService attackService,
            MainMenuItemService mainMenuItemService,
            ClassSkillService classSkillService
    ) {
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
        if (!tryTrigger(event.getPlayer(), hotKey)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrop(PlayerDropItemEvent event) {
        if (mainMenuItemService.isMenuItem(event.getItemDrop().getItemStack())) return;
        if (!tryTrigger(event.getPlayer(), ClassHotKey.Q)) return;

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (mainMenuItemService.isMenuItem(event.getMainHandItem()) || mainMenuItemService.isMenuItem(event.getOffHandItem())) return;
        if (!tryTrigger(event.getPlayer(), ClassHotKey.F)) return;

        event.setCancelled(true);
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

    private void startCooldown(Player player, ClassSkillDefinition skill) {
        cooldownUntilMillis
                .computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
                .put(skill.id().toLowerCase(Locale.ROOT), System.currentTimeMillis() + skill.cooldownTicks() * 50L);
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
