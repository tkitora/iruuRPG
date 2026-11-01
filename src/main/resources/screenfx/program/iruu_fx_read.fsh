#version 150

// Reads the markers of the screen effects from the outline picture into a small picture of 8 x 2 (the state), before they are taken away:
//  - pixel (slot - 1, 0): the marker's glow color (177, slot, strength) with alpha 1 when the marker of that slot is there, else nothing;
//  - pixel (slot - 1, 1): where its display is on the screen (the color as rendertype_outline.fsh writes it) with alpha 1, else nothing.
// The marker of a slot is a 4 x 4 square at the bottom-left corner, x = 4 * (slot - 1) (see core/rendertype_outline.vsh): two pixels of each
// half are looked at, so an outline over one of them does not switch the effect off (nor move it: two places that differ are no place).

uniform sampler2D DiffuseSampler;   // the outlines' picture, with the markers

out vec4 fragColor;

bool isMarker(vec4 color, int slot) {
    ivec3 rgb = ivec3(round(color.rgb * 255.0));
    return rgb.r == 177 && rgb.g == slot;
}

void main() {
    int slot = int(gl_FragCoord.x) + 1;
    int x = 4 * (slot - 1);
    vec4 a = texelFetch(DiffuseSampler, ivec2(x + 1, 0), 0);
    vec4 b = texelFetch(DiffuseSampler, ivec2(x + 2, 1), 0);
    bool there = isMarker(a, slot) || isMarker(b, slot);
    if (!there) {
        fragColor = vec4(0.0);
    } else if (int(gl_FragCoord.y) == 0) {
        fragColor = vec4(isMarker(a, slot) ? a.rgb : b.rgb, 1.0);
    } else {
        vec4 c = texelFetch(DiffuseSampler, ivec2(x + 1, 2), 0);
        vec4 d = texelFetch(DiffuseSampler, ivec2(x + 2, 3), 0);
        fragColor = all(equal(ivec3(round(c.rgb * 255.0)), ivec3(round(d.rgb * 255.0)))) ? vec4(c.rgb, 1.0) : vec4(0.0);
    }
}
