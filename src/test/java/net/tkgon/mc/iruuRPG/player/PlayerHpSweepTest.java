package net.tkgon.mc.iruuRPG.player;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one-time base max HP migration of the saved players (no server needed). */
class PlayerHpSweepTest {

    @TempDir
    File folder;

    private File write(String name, String text) throws Exception {
        File file = new File(folder, name);
        Files.writeString(file.toPath(), text);
        return file;
    }

    @Test
    void oldFilesGetTheInitialHpAndNothingElseChanges() throws Exception {
        File old = write("a.yml", "base-hp-format: 2\nlevel: 200\ncurrent-hp: 2190.0\nclass:\n  id: assassin\nbase-stats:\n  max-hp: 200.0\n  crit-chance: 15.0\n");
        File first = write("b.yml", "level: 1\nbase-stats:\n  max-hp: 20.0\n");
        File done = write("c.yml", "base-hp-format: 3\nlevel: 5\nbase-stats:\n  max-hp: 50.0\n");
        List<String> warnings = new ArrayList<>();

        assertEquals(2, PlayerProfileStorage.sweepHp(folder, 20.0, warnings::add));

        YamlConfiguration a = YamlConfiguration.loadConfiguration(old);
        assertEquals(20.0, a.getDouble("base-stats.max-hp"));
        assertEquals(3, a.getInt("base-hp-format"));
        assertEquals(15.0, a.getDouble("base-stats.crit-chance"));
        assertEquals(2190.0, a.getDouble("current-hp"));
        assertEquals("assassin", a.getString("class.id"));
        assertEquals(200, a.getInt("level"));
        assertEquals(20.0, YamlConfiguration.loadConfiguration(first).getDouble("base-stats.max-hp"));
        // a file already in the new format is left alone, even with another base HP
        assertEquals(50.0, YamlConfiguration.loadConfiguration(done).getDouble("base-stats.max-hp"));
        assertTrue(warnings.isEmpty());
        // a second run changes nothing
        assertEquals(0, PlayerProfileStorage.sweepHp(folder, 20.0, warnings::add));
    }
}
