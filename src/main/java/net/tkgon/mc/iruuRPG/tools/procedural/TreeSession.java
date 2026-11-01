package net.tkgon.mc.iruuRPG.tools.procedural;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

/** What one player has in hand: the settings being edited, where the tree would stand, and the state of the preview. */
final class TreeSession {

    public TreeSettings settings = new TreeSettings();
    public Location base;
    /** The block that was clicked (the place of an object is next to it: above, or below for one that hangs). */
    Location clicked;
    /** The face of the block that was clicked, and the way from the place of the object to the player's eye then: a spike or a crystal grows from that face and points to the player. */
    org.bukkit.block.BlockFace face = org.bukkit.block.BlockFace.UP;
    org.bukkit.util.Vector aim;
    /** The page of the menu that was open last (by name): the menu opens on it next time. */
    String lastPage = "BASIC";
    /** The points of a web (block positions of air in {@link #pointsWorld}), in the order they were chosen: the first is the base of the web. */
    final java.util.List<int[]> points = new java.util.ArrayList<>();
    World pointsWorld;
    /** A tree is being put in the world. */
    boolean busy;

    // ---- the preview (see TreePreview) ----------------------------------------------------------------------------------
    /** The preview is on. */
    boolean preview;
    /** The seed in the settings was chosen by the preview (the settings had none): it goes back to "random" when the preview goes off. */
    boolean autoSeed;
    /** The wait before a changed setting is previewed; running while a change is fresh. */
    BukkitTask timer;
    /** The task that sends the blocks of the preview, a few thousand a tick. */
    BukkitTask show;
    /** A preview is being worked out off the main thread: another one is not started meanwhile (and none is queued). */
    volatile boolean computing;
    /** Number of the last preview started; a result of an older one (the preview was turned off meanwhile) is dropped. */
    int job;
    /** The blocks the player is shown now, by {@link TreePreview#key}, and the world they are in. */
    final Map<Long, org.bukkit.block.data.BlockData> shown = new HashMap<>();
    World shownWorld;
}
