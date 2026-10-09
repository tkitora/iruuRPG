package net.tkgon.mc.iruuRPG.player;

import net.tkgon.mc.iruuRPG.equipment.EquipmentService;
import net.tkgon.mc.iruuRPG.hud.PlayerBars;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.UUID;

public final class LevelService {

    private final JavaPlugin plugin;
    private final PlayerProfileManager profileManager;
    private final EquipmentService equipmentService;
    private final PlayerBars playerBars;
    private final NamespacedKey orbOwnerKey;
    private final NamespacedKey orbXpKey;

    public LevelService(
            JavaPlugin plugin,
            PlayerProfileManager profileManager,
            EquipmentService equipmentService,
            PlayerBars playerBars
    ) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.equipmentService = equipmentService;
        this.playerBars = playerBars;
        this.orbOwnerKey = new NamespacedKey(plugin, "rpg_xp_owner");
        this.orbXpKey = new NamespacedKey(plugin, "rpg_xp_amount");
    }

    public void startOrbTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickOrbs, 1L, 2L);
    }

    public long requiredXpForNextLevel(int level) {
        int currentLevel = Math.max(1, level);
        if (currentLevel >= levelCap()) return 0L;

        double levelValue = currentLevel;
        double raw = levelValue * levelValue * (1.0 + 0.5 * levelValue / 256.0)
                + 10.0 * levelValue
                + 10.0;
        if (!Double.isFinite(raw)) return Long.MAX_VALUE;

        return Math.max(1L, (long) Math.ceil(raw));
    }

    public int levelCap() {
        return Math.max(1, plugin.getConfig().getInt("player.level-cap", 256));
    }

    public float progress(PlayerProfile profile) {
        long required = requiredXpForNextLevel(profile.level());
        if (required <= 0L) return 1.0f;

        return (float) Math.max(0.0, Math.min(1.0, (double) profile.xp() / required));
    }

    public PlayerProfile profile(Player player) {
        return profileManager.getOrCreate(player);
    }

    public void syncBar(Player player, PlayerProfile profile) {
        player.setLevel(profile.level());
        player.setExp(progress(profile));
    }

    public void addXp(Player player, long amount) {
        if (amount <= 0L) return;

        PlayerProfile profile = profileManager.getOrCreate(player);
        if (profile.level() >= levelCap()) {
            profile.setXp(0L);
            syncAfterLevelChange(player, profile, false);
            return;
        }

        boolean wasReady = canLevelUp(profile);
        profile.setXp(safeAdd(profile.xp(), amount));

        syncAfterLevelChange(player, profile, false);
        if (!wasReady && canLevelUp(profile)) {
            player.sendMessage("[iruuRPG] レベルアップ可能です。メニューからレベルアップできます。");
        }
    }

    public void levelUp(Player player, int amount) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.setLevel(Math.min(levelCap(), profile.level() + Math.max(1, amount)));
        profile.setXp(0L);
        syncAfterLevelChange(player, profile, true);
    }

    public void setLevel(Player player, int level) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.setLevel(Math.max(1, Math.min(levelCap(), level)));
        profile.setXp(0L);
        syncAfterLevelChange(player, profile, true);
    }

    public void reset(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        profile.setLevel(1);
        profile.setXp(0L);
        syncAfterLevelChange(player, profile, true);
    }

    public boolean canLevelUp(PlayerProfile profile) {
        long required = requiredXpForNextLevel(profile.level());
        return required > 0L && profile.xp() >= required && profile.level() < levelCap();
    }

    public boolean tryLevelUp(Player player) {
        PlayerProfile profile = profileManager.getOrCreate(player);
        if (!canLevelUp(profile)) {
            return false;
        }

        long required = requiredXpForNextLevel(profile.level());
        profile.setXp(profile.xp() - required);
        profile.setLevel(profile.level() + 1);
        if (profile.level() >= levelCap()) {
            profile.setXp(0L);
        }

        syncAfterLevelChange(player, profile, true);
        playLevelUpEffects(player);
        player.sendMessage("[iruuRPG] Level Up! Lv " + profile.level());
        return true;
    }

    public void dropExperience(Location location, UUID ownerId, long amount) {
        if (location == null || location.getWorld() == null || amount <= 0L) return;

        if (!plugin.getConfig().getBoolean("player.leveling.drop-experience-orbs", true)) {
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner != null) {
                addXp(owner, amount);
            }
            return;
        }

        location.getWorld().spawn(location, ExperienceOrb.class, spawned -> {
            spawned.setExperience(1);
            spawned.setPersistent(true);
            spawned.getPersistentDataContainer().set(orbOwnerKey, PersistentDataType.STRING, ownerId.toString());
            spawned.getPersistentDataContainer().set(orbXpKey, PersistentDataType.LONG, amount);
        });
    }

    public boolean isRpgExperienceOrb(ExperienceOrb orb) {
        return orb.getPersistentDataContainer().has(orbOwnerKey, PersistentDataType.STRING);
    }

    public boolean isOrbForOtherPlayer(ExperienceOrb orb, Player player) {
        String owner = orb.getPersistentDataContainer().get(orbOwnerKey, PersistentDataType.STRING);
        return owner != null && !owner.equals(player.getUniqueId().toString());
    }

    public void collectOrb(Player player, ExperienceOrb orb) {
        if (isOrbForOtherPlayer(orb, player)) return;

        long amount = orb.getPersistentDataContainer().getOrDefault(orbXpKey, PersistentDataType.LONG, 0L);
        orb.remove();
        addXp(player, amount);
    }

    private void syncAfterLevelChange(Player player, PlayerProfile profile, boolean restoreResources) {
        equipmentService.recalculate(player);
        if (restoreResources) {
            profile.setCurrentHp(profile.maxHp());
            profile.setCurrentMp(profile.maxMp());
        }
        playerBars.sync(player, profile);
        syncBar(player, profile);
        profileManager.save(player);
    }

    private void tickOrbs() {
        for (World world : Bukkit.getWorlds()) {
            for (ExperienceOrb orb : world.getEntitiesByClass(ExperienceOrb.class)) {
                if (!isRpgExperienceOrb(orb)) continue;

                pullOrb(orb);
            }
        }
    }

    private void pullOrb(ExperienceOrb orb) {
        String ownerRaw = orb.getPersistentDataContainer().get(orbOwnerKey, PersistentDataType.STRING);
        if (ownerRaw == null) return;

        Player owner;
        try {
            owner = Bukkit.getPlayer(UUID.fromString(ownerRaw));
        } catch (IllegalArgumentException ignored) {
            return;
        }
        if (owner == null || !owner.isOnline() || owner.isDead() || !owner.getWorld().equals(orb.getWorld())) return;

        Vector direction = owner.getLocation().add(0.0, 0.75, 0.0).toVector().subtract(orb.getLocation().toVector());
        double distanceSquared = direction.lengthSquared();
        if (distanceSquared <= 1.1) {
            collectOrb(owner, orb);
            return;
        }

        if (distanceSquared <= 1.0E-9) return;

        double speed = plugin.getConfig().getDouble("player.leveling.experience-orb-speed", 0.32);
        orb.setVelocity(direction.normalize().multiply(Math.max(0.05, speed)));
    }

    private long safeAdd(long left, long right) {
        if (Long.MAX_VALUE - left < right) return Long.MAX_VALUE;
        return left + right;
    }

    private void playLevelUpEffects(Player player) {
        Location center = player.getLocation().add(0.0, 1.0, 0.0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.55f, 1.6f);
        player.getWorld().spawnParticle(Particle.TOTEM, center, 42, 0.65, 0.8, 0.65, 0.05);
        player.getWorld().spawnParticle(Particle.END_ROD, center, 28, 0.5, 0.7, 0.5, 0.02);
        player.getWorld().spawnParticle(Particle.VILLAGER_HAPPY, center, 18, 0.45, 0.55, 0.45, 0.02);
    }
}
