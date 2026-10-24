package net.tkgon.mc.iruuRPG.tools.procedural;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * The only class of the tree tool that touches the (Fast)AsyncWorldEdit API, so the rest of it loads without it. A tree is put through one edit session of the player
 * and the session is added to the player's history: {@code //undo} takes the whole tree back, {@code //redo} puts it again.
 */
final class TreeWorldEdit {

    /** One block to put. */
    record Change(int x, int y, int z, BlockData data) {
    }

    private TreeWorldEdit() {
    }

    /** Puts the changes in the world as one edit of the player; returns how many blocks were put. */
    static int apply(Player player, World world, List<Change> changes) {
        com.sk89q.worldedit.entity.Player actor = BukkitAdapter.adapt(player);
        LocalSession session = WorldEdit.getInstance().getSessionManager().get(actor);
        int count = 0;
        try (EditSession edit = WorldEdit.getInstance().newEditSessionBuilder().world(BukkitAdapter.adapt(world)).actor(actor).build()) {
            for (Change change : changes) {
                if (edit.setBlock(BlockVector3.at(change.x(), change.y(), change.z()), BukkitAdapter.adapt(change.data()))) count++;
            }
            session.remember(edit);
        }
        return count;
    }
}
