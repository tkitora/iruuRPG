#version 150

// What the next frame must know of this one (1 x 1): the ticks of the freeze (slot 3) times 256 in red and green, and blue 1 when its
// marker was there (iruu_fx_hold keeps the picture of the moment only while the frames before ran the same freeze).

uniform sampler2D DiffuseSampler;   // the state (iruu_fx_read)

out vec4 fragColor;

void main() {
    vec4 marker = texelFetch(DiffuseSampler, ivec2(2, 0), 0);
    vec4 ticks = texelFetch(DiffuseSampler, ivec2(2, 2), 0);
    fragColor = marker.a > 0.5 && ticks.a > 0.5 ? vec4(ticks.rg, 1.0, 1.0) : vec4(0.0, 0.0, 0.0, 1.0);
}
