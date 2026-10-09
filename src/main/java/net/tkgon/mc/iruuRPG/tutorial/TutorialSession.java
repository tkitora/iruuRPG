package net.tkgon.mc.iruuRPG.tutorial;

import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** State of one running tutorial (the debug tutorial only ever has one player). */
final class TutorialSession {

    final UUID playerId;
    final String id = UUID.randomUUID().toString();
    final Location origin;
    final float originYaw;
    final Snapshot snapshot;
    final List<TutorialStep> steps;
    final List<BukkitTask> tasks = new ArrayList<>();

    int index;
    boolean stopped;

    boolean lookLocked;
    boolean moveLocked;
    boolean attackLocked;
    float lockYaw;
    float lockPitch;

    Villager npc;
    TextDisplay speech;
    long speechUntilMillis;
    Location npcFrom;
    Location npcTo;
    int moveTick;
    int moveTotal;

    /** How far below the executor's position the view sits (he starts slouching); 0 once he stands up. */
    float yDrop;
    final List<MistAnchor> mistAnchors = new ArrayList<>();

    boolean waitingClassMenu;
    String chosenClass;

    Silverfish rat;
    int ratHits;
    boolean ratWaiting;
    boolean nudged;
    long ratWaitStartMillis;
    long lastRatHitMillis;

    boolean hitNpcReplied;

    TutorialSession(UUID playerId, Location origin, Snapshot snapshot, List<TutorialStep> steps) {
        this.playerId = playerId;
        this.origin = origin.clone();
        this.originYaw = origin.getYaw();
        this.snapshot = snapshot;
        this.steps = steps;
    }

    /** One color of the floating sparks: a fixed spot around the NPC that keeps emitting a few particles. */
    record MistAnchor(org.bukkit.util.Vector offset, Color color) {
    }

    /** What the player looked like before the tutorial, so a stop can put everything back. */
    record Snapshot(
            Location location,
            GameMode gameMode,
            float walkSpeed,
            float flySpeed,
            int heldSlot,
            ItemStack slotItem,
            Map<String, Integer> classLevels,
            String classId,
            boolean classChosen
    ) {
        Snapshot {
            classLevels = new HashMap<>(classLevels);
        }
    }
}
