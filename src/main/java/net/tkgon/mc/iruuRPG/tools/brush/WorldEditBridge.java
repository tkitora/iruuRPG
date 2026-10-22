package net.tkgon.mc.iruuRPG.tools.brush;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The only class of the brush that touches the (Fast)AsyncWorldEdit API, so the rest of the tool loads without it. Blocks are put through an edit session of the
 * player, and the session is added to the player's history, which is what makes {@code //undo} and {@code //redo} take back a stroke of the brush.
 */
final class WorldEditBridge {

    private WorldEditBridge() {
    }

    /** Puts the changes in the world as one edit of the player (one //undo takes them back). Returns how many blocks were put. */
    static int apply(Player player, World world, List<BrushEngine.Change> changes) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        int count = 0;
        try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(world)).actor(actor).build()) {
            for (BrushEngine.Change change : changes) {
                if (edit.setBlock(BlockVector3.at(change.x(), change.y(), change.z()), BukkitAdapter.adapt(change.data()))) count++;
            }
            session.remember(edit);
        }
        return count;
    }
}
