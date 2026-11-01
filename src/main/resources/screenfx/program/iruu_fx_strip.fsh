#version 150

// The outlines' picture without the markers of the screen effects, so no outline is drawn around them: the 4 x 4 square of a marker that is
// there (only part of it is its glow color), and any pixel of a marker's glow color (177, slot 1 - 8 + 16 * level, any).

uniform sampler2D DiffuseSampler;

out vec4 fragColor;

bool isMarker(vec4 color, int slot) {
    ivec3 rgb = ivec3(round(color.rgb * 255.0));
    return rgb.r == 177 && (rgb.g & 15) == slot;
}

void main() {
    ivec2 here = ivec2(gl_FragCoord.xy);
    vec4 color = texelFetch(DiffuseSampler, here, 0);
    ivec3 rgb = ivec3(round(color.rgb * 255.0));
    if (rgb.r == 177 && (rgb.g & 15) >= 1 && (rgb.g & 15) <= 8 && color.a > 0.99) color = vec4(0.0);
    if (here.y < 4 && here.x < 32) {
        int slot = here.x / 4 + 1;
        int x = 4 * (slot - 1);
        if (isMarker(texelFetch(DiffuseSampler, ivec2(x, 0), 0), slot) || isMarker(texelFetch(DiffuseSampler, ivec2(x + 1, 1), 0), slot)) color = vec4(0.0);
    }
    fragColor = color;
}
