package net.tkgon.mc.iruuRPG.combat.clone;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import net.minecraft.ChatFormatting;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundAnimatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_20_R3.inventory.CraftItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * A player-shaped entity that exists only on the clients that are sent its packets: a clone of {@code owner}, with the
 * owner's skin (the skin properties of their profile), the weapons and armor they wear, and no name tag. It is never
 * added to the world, so nothing in the world (mobs, damage, the save) knows of it. All the NMS code of the plugin is
 * here (and in this package): the rest of the plugin only talks to {@link net.tkgon.mc.iruuRPG.combat.clone.CloneBody}.
 *
 * <p>Built for Paper 1.20.4 (Mojang names in the source; the jar for the server is remapped by {@code reobfJar}).
 */
public final class CloneEntity {

    private static final AtomicInteger TEAMS = new AtomicInteger();
    /** Every clone shown now, so they can all be taken away when the plugin stops. */
    private static final java.util.Set<CloneEntity> ACTIVE = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final Player npc;
    private final PlayerTeam team;
    private final String teamName;
    private final String npcName;
    private final UUID uuid;
    private final List<org.bukkit.entity.Player> viewers = new ArrayList<>();
    private boolean removed;

    /**
     * Shows a clone of {@code owner} at {@code at} to the players for whom {@code visibleTo} is true (in the same world, within
     * {@code range} blocks).
     */
    public CloneEntity(org.bukkit.entity.Player owner, Location at, double range, Predicate<org.bukkit.entity.Player> visibleTo) {
        this(owner, at, range, visibleTo, CloneBody.Tint.NONE);
    }

    /** The same with an outline: a glow the colour of {@code tint} (grey for a ghost, red for the clones of the item swap skill). */
    public CloneEntity(org.bukkit.entity.Player owner, Location at, double range, Predicate<org.bukkit.entity.Player> visibleTo, CloneBody.Tint tint) {
        World world = at.getWorld();
        ServerLevel level = ((CraftWorld) world).getHandle();
        this.uuid = UUID.randomUUID();
        this.npcName = "c" + Integer.toHexString(Math.abs(uuid.hashCode()) & 0xFFFFFFF) + TEAMS.incrementAndGet() % 100;

        GameProfile profile = new GameProfile(uuid, npcName);
        for (com.destroystokyo.paper.profile.ProfileProperty property : owner.getPlayerProfile().getProperties()) {
            profile.getProperties().put(property.getName(), new Property(property.getName(), property.getValue(), property.getSignature()));
        }
        // (a plain Player, not a ServerPlayer: a ServerPlayer registers its stats and advancements with the server for good)
        this.npc = new Player(level, BlockPos.ZERO, at.getYaw(), profile) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }
        };
        npc.moveTo(at.getX(), at.getY(), at.getZ(), at.getYaw(), 0.0f);
        npc.setYHeadRot(at.getYaw());
        // every part of the skin (cape, jacket, sleeves, pants, hat)
        npc.getEntityData().set(Player.DATA_PLAYER_MODE_CUSTOMISATION, (byte) 0x7F);
        if (tint != CloneBody.Tint.NONE) npc.setGlowingTag(true);
        copyEquipment(owner);

        this.teamName = "iruurpg_clone_" + TEAMS.get();
        this.team = new PlayerTeam(new Scoreboard(), teamName);
        team.setNameTagVisibility(Team.Visibility.NEVER);
        team.setCollisionRule(Team.CollisionRule.NEVER);
        if (tint == CloneBody.Tint.GRAY) team.setColor(ChatFormatting.GRAY);
        else if (tint == CloneBody.Tint.RED) team.setColor(ChatFormatting.DARK_RED);

        double rangeSquared = range * range;
        for (org.bukkit.entity.Player viewer : world.getPlayers()) {
            if (viewer.getLocation().distanceSquared(at) > rangeSquared) continue;
            if (!visibleTo.test(viewer)) continue;
            viewers.add(viewer);
        }
        spawnFor();
        ACTIVE.add(this);
    }

    /** Takes every clone away (the plugin stops). */
    public static void removeAll() {
        for (CloneEntity clone : new ArrayList<>(ACTIVE)) clone.remove();
    }

    private void copyEquipment(org.bukkit.entity.Player owner) {
        PlayerInventory inventory = owner.getInventory();
        npc.setItemSlot(EquipmentSlot.MAINHAND, CraftItemStack.asNMSCopy(inventory.getItemInMainHand()));
        npc.setItemSlot(EquipmentSlot.OFFHAND, CraftItemStack.asNMSCopy(inventory.getItemInOffHand()));
        npc.setItemSlot(EquipmentSlot.HEAD, CraftItemStack.asNMSCopy(inventory.getHelmet()));
        npc.setItemSlot(EquipmentSlot.CHEST, CraftItemStack.asNMSCopy(inventory.getChestplate()));
        npc.setItemSlot(EquipmentSlot.LEGS, CraftItemStack.asNMSCopy(inventory.getLeggings()));
        npc.setItemSlot(EquipmentSlot.FEET, CraftItemStack.asNMSCopy(inventory.getBoots()));
    }

    private void spawnFor() {
        // listed = false: the player info is needed for the skin, but the clone is not in the tab list
        ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(
                uuid, npc.getGameProfile(), false, 0, GameType.SURVIVAL, null, null);
        Packet<?> info = new ClientboundPlayerInfoUpdatePacket(
                EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), entry);
        Packet<?> teamAdd = ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true);
        Packet<?> teamMember = ClientboundSetPlayerTeamPacket.createPlayerPacket(team, npcName, ClientboundSetPlayerTeamPacket.Action.ADD);
        Packet<?> add = new ClientboundAddEntityPacket(npc);
        Packet<?> data = new ClientboundSetEntityDataPacket(npc.getId(), npc.getEntityData().getNonDefaultValues());
        List<Pair<EquipmentSlot, net.minecraft.world.item.ItemStack>> slots = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            slots.add(Pair.of(slot, npc.getItemBySlot(slot)));
        }
        Packet<?> equipment = new ClientboundSetEquipmentPacket(npc.getId(), slots);
        Packet<?> head = new ClientboundRotateHeadPacket(npc, angle(npc.getYHeadRot()));
        for (org.bukkit.entity.Player viewer : viewers) {
            send(viewer, info);
            send(viewer, teamAdd);
            send(viewer, teamMember);
            send(viewer, add);
            send(viewer, data);
            send(viewer, equipment);
            send(viewer, head);
        }
    }

    /** Moves the clone to {@code x, y, z} facing {@code yaw} degrees. */
    public void moveTo(double x, double y, double z, float yaw) {
        if (removed) return;
        npc.moveTo(x, y, z, yaw, 0.0f);
        npc.setYHeadRot(yaw);
        Packet<?> teleport = new ClientboundTeleportEntityPacket(npc);
        Packet<?> head = new ClientboundRotateHeadPacket(npc, angle(yaw));
        for (org.bukkit.entity.Player viewer : viewers) {
            send(viewer, teleport);
            send(viewer, head);
        }
    }

    /** The clone swings its main arm. */
    public void swing() {
        if (removed) return;
        Packet<?> swing = new ClientboundAnimatePacket(npc, 0);
        for (org.bukkit.entity.Player viewer : viewers) send(viewer, swing);
    }

    /** Takes the clone away from everyone who saw it. */
    public void remove() {
        if (removed) return;
        removed = true;
        ACTIVE.remove(this);
        Packet<?> gone = new ClientboundRemoveEntitiesPacket(npc.getId());
        Packet<?> info = new ClientboundPlayerInfoRemovePacket(List.of(uuid));
        Packet<?> teamRemove = ClientboundSetPlayerTeamPacket.createRemovePacket(team);
        for (org.bukkit.entity.Player viewer : viewers) {
            send(viewer, gone);
            send(viewer, info);
            send(viewer, teamRemove);
        }
        viewers.clear();
    }

    private static byte angle(float degrees) {
        return (byte) (int) (degrees * 256.0f / 360.0f);
    }

    private static void send(org.bukkit.entity.Player viewer, Packet<?> packet) {
        if (!viewer.isOnline()) return;
        ((CraftPlayer) viewer).getHandle().connection.send(packet);
    }
}
