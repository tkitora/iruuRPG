package net.tkgon.mc.iruuRPG.markpack;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shaders of the screen effects ({@link net.tkgon.mc.iruuRPG.screenfx.ScreenEffects}), carried in the jar under {@code screenfx/} and put in a
 * pack of their own ({@code iruufx.zip}, {@link #pack}) under {@code assets/minecraft/shaders/}: a client whose GPU cannot compile them drops that pack, not the
 * fonts of the marks' pack (they were in it before). They replace two of the game's files: the core shader that draws the glowing outlines (a
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

    /** The pack of the screen effects ({@code iruufx.zip}): pack.mcmeta and the shaders; null when a shader is not in the jar. */
    public static byte[] pack() throws IOException {
        Map<String, byte[]> shaders = files();
        if (shaders.isEmpty()) return null;
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("pack.mcmeta", MarkGlyphs.meta("iruuRPG: screen effects (shaders of the glowing outlines)"));
        files.putAll(shaders);
        return MarkGlyphs.zip(files);
    }
}
