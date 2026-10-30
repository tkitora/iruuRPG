package net.tkgon.mc.iruuRPG.item;

import net.tkgon.mc.iruuRPG.skill.SkillRegistry;
import net.tkgon.mc.iruuRPG.skill.SkillSounds;
import net.tkgon.mc.iruuRPG.skill.SkillSpec;
import net.tkgon.mc.iruuRPG.skill.SoundStyle;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sound styles of skills, and the sounds a skill sets for itself (look.sounds). */
class SkillSoundsTest {

    private static SkillRegistry skills;
    private static final List<String> warnings = new ArrayList<>();

    @BeforeAll
    static void load() throws IOException {
        TestServer.start();
        File data = Files.createTempDirectory("skillsounds").toFile();
        TestServer.TestPlugin plugin = new TestServer.TestPlugin(data);
        plugin.getLogger().addHandler(new java.util.logging.Handler() {
            @Override public void publish(java.util.logging.LogRecord record) { warnings.add(record.getMessage()); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        File folder = new File(data, "skills");
        folder.mkdirs();
        Files.writeString(new File(folder, "test.yml").toPath(), String.join("\n",
                "skills:",
                "  fire_cut:",
                "    template: sweep",
                "    look: {sound: fire}",
                "  own_sounds:",
                "    template: strike",
                "    look:",
                "      sound: thunder",
                "      sounds:",
                "        windup: []",
                "        cast:",
                "          - {sound: ENTITY_BLAZE_SHOOT, volume: 0.6, pitch: 1.2}",
                "          - {sound: entity.generic.explode, volume: 3, pitch: 0.1}",
                "          - {sound: \"iruu:skill.boom\"}",
                "          - {sound: NOT_A_SOUND}",
                "          - {sound: \"minecraft:not.a.sound\"}",
                "        hit: [BLOCK_GLASS_BREAK]",
                "        swing: [BLOCK_GLASS_BREAK]",
                "  too_many:",
                "    template: nova",
                "    look: {sounds: {cast: [ENTITY_BLAZE_SHOOT, ENTITY_BLAZE_SHOOT, ENTITY_BLAZE_SHOOT, ENTITY_BLAZE_SHOOT, ENTITY_BLAZE_SHOOT]}}",
                "  bad_style:",
                "    template: nova",
                "    look: {sound: kazoo}",
                ""), StandardCharsets.UTF_8);
        skills = new SkillRegistry(plugin);
        skills.reload();
    }

    private static SkillSpec skill(String id) {
        return skills.forItem("test", id, Map.of());
    }

    @Test
    void theNewStylesAreRead() {
        assertEquals(SoundStyle.FIRE, skill("fire_cut").look().sound());
        assertNull(skill("fire_cut").look().sounds());
        assertTrue(SoundStyle.values().length >= 16);
        // an unknown style keeps the template's own, with a warning that lists the styles
        assertEquals(SoundStyle.MAGIC, skill("bad_style").look().sound());
        assertTrue(warnings.stream().anyMatch(line -> line.contains("kazoo") && line.contains("thunder")), warnings.toString());
    }

    @Test
    void aSkillsOwnSoundsAreReadAndChecked() {
        SkillSounds sounds = skill("own_sounds").look().sounds();
        assertEquals(SoundStyle.THUNDER, skill("own_sounds").look().sound());
        // an empty list: silent at that moment
        assertEquals(List.of(), sounds.windup());
        // names and keys of the game's sounds, and a resource pack's own key; volume and pitch kept in range
        assertEquals(List.of(
                new SkillSounds.Cue("minecraft:entity.blaze.shoot", 0.6f, 1.2f),
                new SkillSounds.Cue("minecraft:entity.generic.explode", 1.0f, 0.5f),
                new SkillSounds.Cue("iruu:skill.boom", 1.0f, 1.0f)), sounds.cast());
        assertEquals(List.of(new SkillSounds.Cue("minecraft:block.glass.break", 1.0f, 1.0f)), sounds.hit());
        assertTrue(warnings.stream().anyMatch(line -> line.contains("NOT_A_SOUND")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(line -> line.contains("minecraft:not.a.sound")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(line -> line.contains("sounds.swing")), warnings.toString());
    }

    @Test
    void atMostFourSoundsAtOneMomentAndALeftOutMomentKeepsTheStyle() {
        SkillSounds sounds = skill("too_many").look().sounds();
        assertEquals(SkillSounds.MAX_CUES, sounds.cast().size());
        assertNull(sounds.windup());
        assertNull(sounds.hit());
    }

    @Test
    void anOverrideChangesOnlyTheMomentsItSets() {
        Map<String, Object> over = new LinkedHashMap<>();
        over.put("sounds", Map.of("hit", List.of(Map.of("sound", "ENTITY_ARROW_HIT", "pitch", 1.5))));
        SkillSounds sounds = skills.forItem("test", "own_sounds", over).look().sounds();
        assertEquals(List.of(new SkillSounds.Cue("minecraft:entity.arrow.hit", 1.0f, 1.5f)), sounds.hit());
        assertEquals(3, sounds.cast().size());
        assertEquals(List.of(), sounds.windup());
    }
}
