package net.tkgon.mc.iruuRPG.listener;

import com.destroystokyo.paper.event.entity.EntityAddToWorldEvent;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Every living entity (players and mobs): a direct hit does not knock it back, and entities do not push each other.
 * Skills that move a target on purpose set its velocity themselves, so they are not affected.
 */
public final class EntityPhysicsListener implements Listener {

    private static final String TEAM_NAME = "iruurpg_nocollide";

    private final JavaPlugin plugin;

    public EntityPhysicsListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Applies the rules to everything that is already loaded (entities spawned later are handled by the events). */
    public void applyToLoaded() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                apply(entity);
            }
        }
    }

    @EventHandler
    public void onAddToWorld(EntityAddToWorldEvent event) {
        if (event.getEntity() instanceof LivingEntity living) apply(living);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        apply(event.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        // a respawned player is a new entity: set it up once the respawn is finished
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) apply(event.getPlayer());
        });
    }

    private void apply(LivingEntity entity) {
        if (plugin.getConfig().getBoolean("physics.no-knockback", true)) {
            AttributeInstance knockback = entity.getAttribute(Attribute.GENERIC_KNOCKBACK_RESISTANCE);
            if (knockback != null && knockback.getBaseValue() < 1.0) {
                knockback.setBaseValue(1.0);
            }
        }
        if (plugin.getConfig().getBoolean("physics.no-collision", true)) {
            entity.setCollidable(false);
            if (entity instanceof Player player) {
                noCollisionTeam().addEntry(player.getName());
            }
        }
    }

    /**
     * Players predict pushing each other on the client from the scoreboard team rules, so the players also join a team
     * that never collides (the server-side flag alone is not enough for that).
     */
    private Team noCollisionTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(TEAM_NAME);
        if (team == null) {
            team = scoreboard.registerNewTeam(TEAM_NAME);
            team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        }
        return team;
    }
}
