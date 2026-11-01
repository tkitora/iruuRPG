package net.tkgon.mc.iruuRPG.markpack;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shaders of the screen effects ({@link net.tkgon.mc.iruuRPG.screenfx.ScreenEffects}), carried in the jar under {@code screenfx/} and put in the
 * marks' pack under {@code assets/minecraft/shaders/}. They replace two of the game's files: the core shader that draws the glowing outlines (a
 * glowing display of the color of a marker is drawn as a small square in the corner) and the post chain of the outlines (it reads the markers
 * into a small picture, draws the effects, and then the outlines as the game does). All the files or none: a part of them would break the outlines.
 */
public final class ScreenShaders {

    /** The files, as paths under {@code shaders/}. */
    public static final List<String> FILES = List.of(
            "core/rendertype_outline.json",
            "core/rendertype_outline.vsh",
            "core/rendertype_outline.fsh",
            "post/entity_outline.json",
            "program/iruu_fx.vsh",
            "program/iruu_fx_read.json",
            "program/iruu_fx_read.fsh",
            "program/iruu_fx_bloom_bright.json",
            "program/iruu_fx_bloom_bright.fsh",
            "program/iruu_fx_blur.json",
            "program/iruu_fx_blur.fsh",
            "program/iruu_fx_down.json",
            "program/iruu_fx_down.fsh",
            "program/iruu_fx_strip.json",
            "program/iruu_fx_strip.fsh",
            "program/iruu_fx_mix.json",
            "program/iruu_fx_mix.fsh");

    private ScreenShaders() {
    }

    /** The files' bytes by their place in a pack; empty when one of them is not in the jar. */
    public static Map<String, byte[]> files() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (String file : FILES) {
            try (InputStream in = ScreenShaders.class.getResourceAsStream("/screenfx/" + file)) {
                if (in == null) return Map.of();
                files.put("assets/minecraft/shaders/" + file, in.readAllBytes());
            }
        }
        return files;
    }

    public static void addTo(Map<String, byte[]> files) throws IOException {
        files.putAll(files());
    }
}
