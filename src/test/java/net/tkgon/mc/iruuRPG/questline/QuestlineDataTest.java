package net.tkgon.mc.iruuRPG.questline;

import net.tkgon.mc.iruuRPG.questline.QuestlineData.LineState;
import net.tkgon.mc.iruuRPG.questline.QuestlineData.Status;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestlineDataTest {

    /** What a player has of the quest lines comes back the same from their file (and old files without it read as empty). */
    @Test
    void savesAndReadsBack() throws Exception {
        QuestlineData data = new QuestlineData();
        LineState state = new LineState(Status.PAUSED, "kill", 1234L);
        state.flags.put("boar_kills", 3L);
        state.flags.put("talked_wife", true);
        state.runs = 2;
        state.waitingCutscene = "farm_intro";
        data.lines().put("boar_hunt", state);
        data.flags().put("atlas_trusted", true);

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("level", 5);
        data.writeTo(yaml);
        YamlConfiguration again = new YamlConfiguration();
        again.loadFromString(yaml.saveToString());

        QuestlineData read = new QuestlineData();
        read.readFrom(again);
        LineState back = read.line("boar_hunt");
        assertEquals(Status.PAUSED, back.status);
        assertEquals("kill", back.step);
        assertEquals(1234L, back.stepSince);
        assertEquals(2, back.runs);
        assertEquals("farm_intro", back.waitingCutscene, "a player who left during a cutscene sees it again");
        assertEquals(3L, Questline.number(back.flags.get("boar_kills"), 0) == 3 ? 3L : -1L);
        assertEquals(true, back.flags.get("talked_wife"));
        assertEquals(true, read.flags().get("atlas_trusted"));

        QuestlineData empty = new QuestlineData();
        YamlConfiguration old = new YamlConfiguration();
        old.loadFromString("level: 256\nquest:\n  currency: 10\n");
        empty.readFrom(old);
        assertTrue(empty.lines().isEmpty());
        assertNull(empty.line("boar_hunt"));
    }
}
