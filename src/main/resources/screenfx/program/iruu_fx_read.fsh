#version 150

// Reads the markers of the screen effects from the outline picture into a small picture of 8 x 3 (the state), before they are taken away;
// for each slot, pixel (slot - 1, row), alpha 1 when the marker of that slot is there, else nothing:
//  - row 0: the marker's glow color (177, slot + 16 * level, parameter);
//  - row 1: where its display is on the screen (the color as rendertype_outline.fsh writes it);
//  - row 2: the ticks since its parameter's tick (times 256, in red and green).
// The marker of a slot is a 4 x 4 square at the bottom-left corner, x = 4 * (slot - 1) (see core/rendertype_outline.vsh): two pixels of each
// part are looked at, so an outline over one of them does not switch the effect off (nor move it: two values that differ are no value).

uniform sampler2D DiffuseSampler;   // the outlines' picture, with the markers

out vec4 fragColor;

bool isMarker(vec4 color, int slot) {
    ivec3 rgb = ivec3(round(color.rgb * 255.0));
    return rgb.r == 177 && (rgb.g & 15) == slot;
}

vec4 same(vec4 c, vec4 d) {
    return all(equal(ivec3(round(c.rgb * 255.0)), ivec3(round(d.rgb * 255.0)))) ? vec4(c.rgb, 1.0) : vec4(0.0);
}

void main() {
    int slot = int(gl_FragCoord.x) + 1;
    int row = int(gl_FragCoord.y);
    int x = 4 * (slot - 1);
    vec4 a = texelFetch(DiffuseSampler, ivec2(x, 0), 0);
    vec4 b = texelFetch(DiffuseSampler, ivec2(x + 1, 1), 0);
    if (!isMarker(a, slot) && !isMarker(b, slot)) fragColor = vec4(0.0);
    else if (row == 0) fragColor = vec4(isMarker(a, slot) ? a.rgb : b.rgb, 1.0);
    else if (row == 1) fragColor = same(texelFetch(DiffuseSampler, ivec2(x + 1, 2), 0), texelFetch(DiffuseSampler, ivec2(x + 2, 3), 0));
    else fragColor = same(texelFetch(DiffuseSampler, ivec2(x + 2, 0), 0), texelFetch(DiffuseSampler, ivec2(x + 3, 1), 0));
}
