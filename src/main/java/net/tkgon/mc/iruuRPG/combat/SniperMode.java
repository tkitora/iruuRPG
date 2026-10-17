package net.tkgon.mc.iruuRPG.combat;

import net.tkgon.mc.iruuRPG.classsystem.ClassSkillDefinition;
import net.tkgon.mc.iruuRPG.item.RpgItemDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The ranger's 本能開放・極: once during the enhanced state (a few seconds after it started) the key can be pressed
 * again. The ranger then floats up into the air and cannot move, is made to look through a spyglass (a spyglass is
 * put in the off hand and the client is told to use it), and the next click fires one high-damage shot; when the shot
 * is fired or the time is up they drop back to the ground and everything is put back.
 */
public final class SniperMode implements Listener {

    private static final Particle.DustOptions SHOT = new Particle.DustOptions(Color.fromRGB(255, 80, 60), 1.4f);
    private static final Particle.DustOptions SHOT_CORE = new Particle.DustOptions(Color.fromRGB(255, 240, 220), 0.9f);

    private final JavaPlugin plugin;
    private final AttackService attackService;
    private final ClassEffectService classEffectService;
    private final Map<UUID, State> states = new HashMap<>();
    /** The start time of the enhanced state that already had its sniper mode (one per cast). */
    private final Map<UUID, Long> usedFor = new HashMap<>();

    private final NamespacedKey scopeKey;
    private final NamespacedKey stashKey;

    private static final class State {
        private ItemStack savedHelmet;
        private double holdY;
        private int age;
        private int totalTicks;
        private boolean rising = true;
        private RpgItemDefinition weapon;
        private double damageRate;
        private double range;
        private BukkitRunnable task;
    }

    public SniperMode(JavaPlugin plugin, AttackService attackService, ClassEffectService classEffectService) {
        this.scopeKey = new NamespacedKey(plugin, "sniper_scope");
        this.stashKey = new NamespacedKey(plugin, "sniper_helmet");
        this.plugin = plugin;
        this.attackService = attackService;
        this.classEffectService = classEffectService;
    }

    public boolean active(Player player) {
        return states.containsKey(player.getUniqueId());
    }

    /**
     * The key pressed again: starts the sniper mode when the enhanced state is on, the delay has passed and this
     * cast has not used it yet. True when the press was handled (also a refusal with a message).
     */
    public boolean tryEnter(Player player, ClassSkillDefinition skill) {
        UUID id = player.getUniqueId();
        long elapsed = classEffectService.instinctElapsedMillis(id);
        if (elapsed < 0L) return false;
        if (states.containsKey(id)) return true;
        if (!attackService.hasClassSkill(player, ClassEffectService.INSTINCT_UPGRADE)) return false;

        long startedAt = System.currentTimeMillis() - elapsed;
        if (usedFor.containsKey(id) && Math.abs(usedFor.get(id) - startedAt) < 500L) {
            player.sendMessage("[iruuRPG] この本能開放では、もう再発動しました。");
            return true;
        }
        double delay = skill.values().getOrDefault("recast-delay-seconds", 3.0);
        if (elapsed < delay * 1000.0) {
            player.sendMessage("[iruuRPG] 再発動まで あと" + String.format(java.util.Locale.ROOT, "%.1f", delay - elapsed / 1000.0) + "秒");
            return true;
        }
        RpgItemDefinition weapon = attackService.weaponInMainHand(player);
        if (weapon == null || !weapon.isWeaponLike()) {
            player.sendMessage("[iruuRPG] 武器を持っていないと狙撃できません。");
            return true;
        }

        usedFor.put(id, startedAt);
        start(player, skill, weapon);
        return true;
    }

    private void start(Player player, ClassSkillDefinition skill, RpgItemDefinition weapon) {
        State state = new State();
        state.weapon = weapon;
        state.damageRate = skill.values().getOrDefault("sniper-damage-rate", 12.0);
        state.range = skill.values().getOrDefault("sniper-range", 60.0);
        state.totalTicks = (int) Math.round(skill.values().getOrDefault("sniper-seconds", 10.0) * 20.0);
        double rise = skill.values().getOrDefault("sniper-rise-blocks", 3.0);
        state.holdY = player.getLocation().getY() + rise;
        // the scope: a carved pumpkin on the head (its dark edge over the screen) and a very strong slowness, which
        // narrows the field of view (zoom). The real helmet is kept in memory and in the player's data until it is put back.
        ItemStack helmet = player.getInventory().getHelmet();
        state.savedHelmet = helmet == null ? new ItemStack(Material.AIR) : helmet.clone();
        player.getPersistentDataContainer().set(stashKey, PersistentDataType.BYTE_ARRAY, state.savedHelmet.serializeAsBytes());
        states.put(player.getUniqueId(), state);

        player.setFallDistance(0.0f);
        player.setGravity(false);
        player.getInventory().setHelmet(scopeItem());
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, state.totalTicks + 40, 9, false, false, false));
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_3, SoundCategory.PLAYERS, 0.8f, 1.4f);
        player.sendMessage("[iruuRPG] 狙撃モード: クリックで一撃を放つ (" + (state.totalTicks / 20) + "秒)");

        state.task = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    if (!player.isOnline() || player.isDead() || state.age++ >= state.totalTicks) {
                        end(player, "時間切れ");
                        return;
                    }
                    double y = player.getLocation().getY();
                    if (state.rising) {
                        if (y >= state.holdY - 0.1) {
                            state.rising = false;
                        } else {
                            player.setVelocity(new Vector(0.0, Math.min(0.45, (state.holdY - y) * 0.35 + 0.05), 0.0));
                        }
                    } else {
                        // hold the height (a jump or a push is pulled back)
                        player.setVelocity(new Vector(0.0, Math.max(-0.5, Math.min(0.5, (state.holdY - y) * 0.4)), 0.0));
                    }
                    player.setFallDistance(0.0f);
                    // a crosshair of light a little ahead of the eyes, for the player only
                    if (!state.rising && state.age % 4 == 0) {
                        org.bukkit.Location eye = player.getEyeLocation();
                        player.spawnParticle(Particle.END_ROD, eye.clone().add(eye.getDirection().multiply(2.0)), 1, 0.0, 0.0, 0.0, 0.0);
                    }
                } catch (RuntimeException exception) {
                    plugin.getLogger().warning("sniper mode stopped after an error: " + exception);
                    end(player, "エラー");
                }
            }
        };
        state.task.runTaskTimer(plugin, 1L, 1L);
    }

    private void end(Player player, String reason) {
        State state = states.remove(player.getUniqueId());
        if (state == null) return;
        if (state.task != null) state.task.cancel();
        if (player.isOnline()) {
            player.removePotionEffect(PotionEffectType.SLOW);
            player.setGravity(true);
            ItemStack worn = player.getInventory().getHelmet();
            if (isScope(worn)) player.getInventory().setHelmet(state.savedHelmet.getType().isAir() ? null : state.savedHelmet);
            player.getPersistentDataContainer().remove(stashKey);
            player.setFallDistance(0.0f);
        }
    }

    // ---- the shot ---------------------------------------------------------------------------------------------------

    private void fire(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.rising) return;
        end(player, "発射");

        World world = player.getWorld();
        org.bukkit.Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult hit = world.rayTrace(eye, direction, state.range, org.bukkit.FluidCollisionMode.NEVER, true, 0.5,
                entity -> attackService.isAttackTarget(entity, player));
        double length = hit == null || hit.getHitPosition() == null ? state.range : hit.getHitPosition().distance(eye.toVector());
        RangerVfx.spiralBeam(plugin, world, eye.clone().add(direction.clone().multiply(0.8)), direction, length);
        world.playSound(eye, Sound.ENTITY_GENERIC_EXPLODE, SoundCategory.PLAYERS, 0.7f, 1.9f);
        world.playSound(eye, Sound.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0f, 0.6f);

        if (hit == null || !(hit.getHitEntity() instanceof LivingEntity victim)) {
            attackService.sendMissDebug(player, AttackType.RANGE);
            return;
        }
        AttackService.AttackDamage attack = attackService.calculateDamage(player, victim, state.weapon);
        double damage = Math.round(attack.result().damage() * state.damageRate * 10.0) / 10.0;
        boolean critical = attack.result().critical();
        if (!attackService.applyRangeDamage(player, victim, damage, state.weapon, critical)) return;
        attackService.sendDamageDebug(player, AttackType.RANGE, victim, damage, critical, false);
        attackService.playRangeImpact(player, hit.getHitPosition().toLocation(world), state.weapon);
    }

    private ItemStack scopeItem() {
        ItemStack item = new ItemStack(Material.CARVED_PUMPKIN);
        org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(scopeKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private boolean isScope(ItemStack item) {
        if (item == null || item.getType() != Material.CARVED_PUMPKIN || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(scopeKey, PersistentDataType.BYTE);
    }

    // ---- events -----------------------------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(PlayerInteractEvent event) {
        if (!active(event.getPlayer())) return;
        if (event.getAction() == Action.PHYSICAL) return;
        event.setCancelled(true);
        fire(event.getPlayer());
    }

    /** An arm swing (the client sends one for a click even when the use of an item holds the interaction). */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSwing(PlayerAnimationEvent event) {
        if (!active(event.getPlayer())) return;
        fire(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        State state = states.get(event.getPlayer().getUniqueId());
        if (state == null || state.rising || event.getTo() == null) return;
        org.bukkit.Location from = event.getFrom();
        org.bukkit.Location to = event.getTo();
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return;
        // the head turns freely, the body stays where it hangs
        org.bukkit.Location held = from.clone();
        held.setYaw(to.getYaw());
        held.setPitch(to.getPitch());
        event.setTo(held);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventory(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && active(player)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        end(event.getPlayer(), "退出");
        usedFor.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        State state = states.get(player.getUniqueId());
        if (state != null && !event.getKeepInventory()) {
            // the scope must not drop, the helmet they really wore must
            event.getDrops().removeIf(this::isScope);
            if (!state.savedHelmet.getType().isAir()) event.getDrops().add(state.savedHelmet);
        }
        end(player, "死亡");
    }

    /** A helmet left in the player's data by a crash or a stop: put it back, and take the scope off. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        byte[] stash = player.getPersistentDataContainer().get(stashKey, PersistentDataType.BYTE_ARRAY);
        if (stash == null || active(player)) return;
        player.getPersistentDataContainer().remove(stashKey);
        player.removePotionEffect(PotionEffectType.SLOW);
        player.setGravity(true);
        if (isScope(player.getInventory().getHelmet())) {
            ItemStack saved = ItemStack.deserializeBytes(stash);
            player.getInventory().setHelmet(saved.getType().isAir() ? null : saved);
        }
    }

    /** On shutdown every sniper is put back down. */
    public void shutdown() {
        for (UUID id : new java.util.ArrayList<>(states.keySet())) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) end(player, "停止");
        }
    }
}
