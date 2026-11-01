#version 150

// The freeze (slot 3, t134): the picture of the moment it starts, kept from frame to frame while it runs (the mix shows it over the game's
// picture, less and less: iruu_fx_mix.fsh). While no freeze runs, it is the game's picture of the frame; in the first frame of a freeze
// (the last frame did not run this one: iruu_fx_memo) it takes the game's picture of that frame, and keeps it until the freeze ends.

uniform sampler2D DiffuseSampler;   // the game's picture
uniform sampler2D HoldSampler;      // the picture kept in the last frame
uniform sampler2D StateSampler;     // the markers read (iruu_fx_read)
uniform sampler2D MemoSampler;      // the last frame's ticks of the freeze (iruu_fx_memo)

out vec4 fragColor;

const float FREEZE_TICKS = 6.0;     // how long the freeze lasts at level 8 (strength 100): the length goes with the level (the same in the mix)

void main() {
    ivec2 here = ivec2(gl_FragCoord.xy);
    vec4 marker = texelFetch(StateSampler, ivec2(2, 0), 0);
    vec4 ticksColor = texelFetch(StateSampler, ivec2(2, 2), 0);
    float level = marker.a > 0.5 ? float(int(round(marker.g * 255.0)) >> 4) : 0.0;
    float ticks = ticksColor.a > 0.5 ? float((int(round(ticksColor.r * 255.0)) << 8) | int(round(ticksColor.g * 255.0))) / 256.0 : -1.0;
    float span = FREEZE_TICKS * level / 8.0;
    // (a client a little behind the server counts nearly 240: not started yet)
    bool running = level > 0.0 && ticks >= 0.0 && ticks <= 200.0 && ticks < span;
    vec4 memo = texelFetch(MemoSampler, ivec2(0, 0), 0);
    float last = float((int(round(memo.r * 255.0)) << 8) | int(round(memo.g * 255.0))) / 256.0;
    // the last frame ran this freeze: its ticks were a little before these (a gap of 2 ticks or more is another freeze, or frames missed)
    bool ranBefore = memo.b > 0.5 && last <= ticks && ticks - last < 2.0;
    fragColor = running && ranBefore ? texelFetch(HoldSampler, here, 0) : texelFetch(DiffuseSampler, here, 0);
}
