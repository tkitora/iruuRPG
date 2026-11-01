package net.tkgon.mc.iruuRPG.markpack;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.tkgon.mc.iruuRPG.screenfx.ScreenEffects;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** t125: the shaders of the screen effects, and the post chain of the outlines that uses them. */
class ScreenShadersTest {

    /** The game's own programs the chain still uses (they are not in the pack). */
    private static final Set<String> GAME_PROGRAMS = Set.of("entity_outline", "blur");

    private static Map<String, byte[]> unzip(byte[] pack) throws IOException {
        Map<String, byte[]> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(pack))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) files.put(entry.getName(), zip.readAllBytes());
        }
        return files;
    }

    private static String text(Map<String, byte[]> files, String path) {
        byte[] bytes = files.get("assets/minecraft/shaders/" + path);
        assertNotNull(bytes, path);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static Set<String> names(JsonObject program, String key) {
        Set<String> names = new HashSet<>();
        if (program.has(key)) for (JsonElement element : program.getAsJsonArray(key)) names.add(element.getAsJsonObject().get("name").getAsString());
        return names;
    }

    @Test
    void thePackCarriesTheShadersOnlyWhenTheyAreOn() throws IOException {
        Map<String, byte[]> files = ScreenShaders.files();
        assertEquals(ScreenShaders.FILES.size(), files.size(), "every file is in the jar");
        Map<String, byte[]> on = unzip(MarkGlyphs.pack(true));
        Map<String, byte[]> off = unzip(MarkGlyphs.pack(false));
        for (String path : files.keySet()) {
            assertTrue(on.containsKey(path), path);
            assertFalse(off.containsKey(path), path);
        }
        assertTrue(off.containsKey("assets/iruurpg/font/marks.json") && off.containsKey("assets/iruurpg/font/hud.json"));
    }

    @Test
    void theChainNamesOnlyWhatItsProgramsHave() throws IOException {
        Map<String, byte[]> files = ScreenShaders.files();
        JsonObject chain = JsonParser.parseString(text(files, "post/entity_outline.json")).getAsJsonObject();
        Set<String> targets = new HashSet<>(Set.of("minecraft:main"));
        for (JsonElement target : chain.getAsJsonArray("targets")) {
            targets.add(target.isJsonPrimitive() ? target.getAsString() : target.getAsJsonObject().get("name").getAsString());
        }
        assertTrue(targets.contains("final") && targets.contains("swap"), "the game reads the outline picture from final");
        for (JsonElement element : chain.getAsJsonArray("passes")) {
            JsonObject pass = element.getAsJsonObject();
            String name = pass.get("name").getAsString();
            assertTrue(targets.contains(pass.get("intarget").getAsString()), name);
            assertTrue(targets.contains(pass.get("outtarget").getAsString()), name);
            if (GAME_PROGRAMS.contains(name)) continue;
            JsonObject program = JsonParser.parseString(text(files, "program/" + name + ".json")).getAsJsonObject();
            text(files, "program/" + program.get("vertex").getAsString() + ".vsh");
            String fragment = text(files, "program/" + program.get("fragment").getAsString() + ".fsh");
            Set<String> samplers = names(program, "samplers");
            assertTrue(samplers.contains("DiffuseSampler"), name);
            for (String sampler : samplers) assertTrue(fragment.contains("uniform sampler2D " + sampler + ";"), name + " " + sampler);
            if (pass.has("auxtargets")) {
                for (JsonElement aux : pass.getAsJsonArray("auxtargets")) {
                    // an aux target the program does not name as a sampler is never bound
                    assertTrue(samplers.contains(aux.getAsJsonObject().get("name").getAsString()), name);
                    assertTrue(targets.contains(aux.getAsJsonObject().get("id").getAsString()), name);
                }
            }
            if (pass.has("uniforms")) {
                // the game throws when the chain sets a uniform the program does not have
                for (JsonElement uniform : pass.getAsJsonArray("uniforms")) assertTrue(names(program, "uniforms").contains(uniform.getAsJsonObject().get("name").getAsString()), name);
            }
        }
    }

    @Test
    void everyPassCoversItsWholeTarget() throws IOException {
        // t127: the game gives every pass the screen's ProjMat, so a pass into a smaller target (the bloom's, the 8 x 1 state) drew only its
        // bottom-left corner; the square is made from OutSize, the size of the pass's own target
        Map<String, byte[]> files = ScreenShaders.files();
        String vertex = text(files, "program/iruu_fx.vsh");
        assertFalse(vertex.contains("uniform mat4 ProjMat"), vertex);
        assertTrue(vertex.contains("Position.xy / OutSize"));
        for (String file : ScreenShaders.FILES) {
            if (!file.startsWith("program/") || !file.endsWith(".json")) continue;
            JsonObject program = JsonParser.parseString(text(files, file)).getAsJsonObject();
            if (!program.get("vertex").getAsString().equals("iruu_fx")) continue;
            assertTrue(names(program, "uniforms").contains("OutSize"), file);
            assertFalse(names(program, "uniforms").contains("ProjMat"), file + ": a uniform the program does not use (the game warns)");
        }
    }

    @Test
    void theShadersReadTheMarkersTheServerShows() throws IOException {
        Map<String, byte[]> files = ScreenShaders.files();
        String red = "== " + ScreenEffects.MARKER_RED;
        assertTrue(text(files, "core/rendertype_outline.vsh").contains("rgb.r " + red));
        assertTrue(text(files, "program/iruu_fx_strip.fsh").contains("rgb.r " + red));
        assertTrue(text(files, "program/iruu_fx_read.fsh").contains("rgb.r " + red));
        // each effect reads its slot's pixel of the state (slot - 1, 0)
        assertTrue(text(files, "program/iruu_fx_bloom_bright.fsh").contains("ivec2(" + (ScreenEffects.Effect.BLOOM.slot() - 1) + ", 0)"));
        assertTrue(text(files, "program/iruu_fx_mix.fsh").contains("ivec2(" + (ScreenEffects.Effect.DARKEN.slot() - 1) + ", 0)"));
        for (ScreenEffects.Effect effect : ScreenEffects.Effect.values()) assertTrue(effect.slot() >= 1 && effect.slot() <= 8, effect.id());
        JsonObject core = JsonParser.parseString(text(files, "core/rendertype_outline.json")).getAsJsonObject();
        assertTrue(names(core, "uniforms").contains("ScreenSize"), "the core shader needs the screen size to find the corner");
    }
}
