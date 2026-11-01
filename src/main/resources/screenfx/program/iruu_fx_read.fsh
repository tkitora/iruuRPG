#version 150

// Reads the markers of the screen effects from the outline picture into a small picture of 8 x 1 (the state), before they are taken away:
// pixel (slot - 1, 0) is the marker's color (177, slot, strength) with alpha 1 when the marker of that slot is there, else nothing.
// The marker of a slot is a 4 x 4 square at the bottom-left corner, x = 4 * (slot - 1) (see core/rendertype_outline.vsh); two of its pixels
// are looked at, so an outline over one of them does not switch the effect off.

uniform sampler2D DiffuseSampler;   // the outlines' picture, with the markers

out vec4 fragColor;

bool isMarker(vec4 color, int slot) {
    ivec3 rgb = ivec3(round(color.rgb * 255.0));
    return rgb.r == 177 && rgb.g == slot;
}

void main() {
    int slot = int(gl_FragCoord.x) + 1;
    vec4 a = texelFetch(DiffuseSampler, ivec2(4 * (slot - 1) + 1, 1), 0);
    vec4 b = texelFetch(DiffuseSampler, ivec2(4 * (slot - 1) + 2, 2), 0);
    if (isMarker(a, slot)) fragColor = vec4(a.rgb, 1.0);
    else if (isMarker(b, slot)) fragColor = vec4(b.rgb, 1.0);
    else fragColor = vec4(0.0);
}
