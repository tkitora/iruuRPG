package net.tkgon.mc.iruuRPG.tools.procedural;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * (t295) Writes blocks as a Sponge schematic (version 2, the {@code .schem} WorldEdit and FastAsyncWorldEdit load with {@code //schem load}): the box round the
 * blocks, a palette of their states, every block of the box by its palette number (air where there is none). The origin is the block the object stands on, so
 * {@code //paste} puts it where the player stands as the tool would have. Pure: the same blocks give the same bytes.
 */
final class SchematicWriter {

    /** The game's data version of 1.20.4. */
    static final int DATA_VERSION = 3700;

    private SchematicWriter() {
    }

    /** The schematic of {@code blocks} (by {@link TreeVoxelizer#pack}ed place: the block's states as the game writes them, {@code minecraft:oak_stairs[facing=north,...]}). */
    static byte[] write(Map<Long, String> blocks) throws IOException {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long key : blocks.keySet()) {
            int x = TreeVoxelizer.unpackX(key);
            int y = TreeVoxelizer.unpackY(key);
            int z = TreeVoxelizer.unpackZ(key);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
        }
        if (blocks.isEmpty()) {
            minX = minY = minZ = 0;
            maxX = maxY = maxZ = 0;
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        int length = maxZ - minZ + 1;
        Map<String, Integer> palette = new LinkedHashMap<>();
        palette.put("minecraft:air", 0);
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    String state = blocks.get(TreeVoxelizer.pack(x + minX, y + minY, z + minZ));
                    int id = state == null ? 0 : palette.computeIfAbsent(state, unused -> palette.size());
                    varint(data, id);
                }
            }
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            name(out, "Schematic");
            tag(out, 3, "Version");
            out.writeInt(2);
            tag(out, 3, "DataVersion");
            out.writeInt(DATA_VERSION);
            tag(out, 2, "Width");
            out.writeShort(width);
            tag(out, 2, "Height");
            out.writeShort(height);
            tag(out, 2, "Length");
            out.writeShort(length);
            tag(out, 11, "Offset");
            out.writeInt(3);
            out.writeInt(minX);
            out.writeInt(minY);
            out.writeInt(minZ);
            // (WorldEdit's own: where the box is from the origin, the block the object stands on)
            tag(out, 10, "Metadata");
            tag(out, 3, "WEOffsetX");
            out.writeInt(minX);
            tag(out, 3, "WEOffsetY");
            out.writeInt(minY);
            tag(out, 3, "WEOffsetZ");
            out.writeInt(minZ);
            out.writeByte(0);
            tag(out, 3, "PaletteMax");
            out.writeInt(palette.size());
            tag(out, 10, "Palette");
            for (Map.Entry<String, Integer> entry : palette.entrySet()) {
                tag(out, 3, entry.getKey());
                out.writeInt(entry.getValue());
            }
            out.writeByte(0);
            tag(out, 7, "BlockData");
            byte[] array = data.toByteArray();
            out.writeInt(array.length);
            out.write(array);
            tag(out, 9, "BlockEntities");
            out.writeByte(10);
            out.writeInt(0);
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void tag(DataOutputStream out, int type, String name) throws IOException {
        out.writeByte(type);
        name(out, name);
    }

    private static void name(DataOutputStream out, String name) throws IOException {
        byte[] text = name.getBytes(StandardCharsets.UTF_8);
        out.writeShort(text.length);
        out.write(text);
    }

    private static void varint(ByteArrayOutputStream out, int value) {
        int v = value;
        while ((v & ~0x7F) != 0) {
            out.write((v & 0x7F) | 0x80);
            v >>>= 7;
        }
        out.write(v);
    }
}
