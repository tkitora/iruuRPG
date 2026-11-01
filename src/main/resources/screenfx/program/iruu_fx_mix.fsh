#version 150

// The last pass: the outline picture the game lays over the screen at the end of the frame (blend: source alpha), made of
//  - the screen effects: the picture shown, each pixel of it taken from another place,
//      - the freeze (slot 3, t134): the picture shown is the picture of the moment the freeze started (kept by iruu_fx_hold) over the game's
//        picture, less and less: its share is exp(-FREEZE_SHARE * S(t)) (S the integral of smoothstep over the freeze, 0 - 1), brought to
//        0 at the end; it depends only on the client's time since the start (from GameTime), so it is the same at any frame rate,
//      - the impact (slot 2, t130, a ripple since t133): a ring that spreads from a place of the world (its marker's display) and fades;
//        a pixel on the ring is taken from nearer to the place, most on the middle of the ring, nothing at its edges (0 .. 1 .. 0, smooth).
//        Its time is the client's (the ticks since the marker's first tick, from GameTime: smooth at any frame rate), and runs slow at first
//        (SLOW_START), smoothly back to its usual speed over SLOW_TICKS,
//      - the chromatic aberration (slot 1, t129): red from further out from the middle of the screen, blue from further in (strength / 100
//        * SHIFT of the distance from the middle), green from the place itself.
//    The game's picture cannot be written here, so it is a layer that turns the game's picture into that: its alpha is the least that can do
//    that (0 where the colors do not change, so only the edges of things are touched),
//  - the glowing outlines over it, as the game draws them (its last blit gives (rgb * a, a * a)).

uniform sampler2D DiffuseSampler;   // the outlines
uniform sampler2D MainSampler;      // the game's picture
uniform sampler2D StateSampler;     // the markers read (iruu_fx_read): pixel (slot - 1, 0) the glow color, (slot - 1, 1) the place, (slot - 1, 2) the ticks
uniform sampler2D HoldSampler;      // the picture of the moment the freeze started (the game's while none runs: iruu_fx_hold)
uniform vec2 OutSize;

out vec4 fragColor;

const float SHIFT = 0.012;          // chromatic, at strength 100: the shift is 1.2 % of the distance from the middle (11 pixels at the side of 1920 x 1080)
// impact: lengths are shares of the screen's height, times are ticks
const float RIPPLE_TICKS = 10.0;    // how long the ripple lasts, in its own (slowed) time (about 12 real ticks with the slow start below)
const float RIPPLE_REACH = 0.6;     // how far the middle of the ring goes
const float RIPPLE_WIDTH = 0.08;    // half the width of the ring (from its middle to where it is 0)
const float RIPPLE_PUSH = 0.03;     // at level 8 (strength 100), at the start: how far a pixel on the middle of the ring is taken from
const float SLOW_START = 0.25;      // the ripple's time runs at this speed at first,
const float SLOW_TICKS = 6.0;       // and smoothly (smoothstep) back to 1 over these real ticks
// freeze
const float FREEZE_TICKS = 6.0;     // how long the freeze lasts at level 8 (strength 100): the length goes with the level (the same in iruu_fx_hold)
const float FREEZE_SHARE = 4.0;     // how fast the moment gives way: exp(-4) of it would be left at the end (taken to 0 there)

// A picture at a place between pixels (pixel units, centres at .5), bilinear (the game reads its pictures nearest).
vec3 bilinear(sampler2D picture, vec2 at) {
    ivec2 size = textureSize(picture, 0);
    vec2 p = at - 0.5;
    ivec2 low = ivec2(floor(p));
    vec2 t = p - vec2(low);
    ivec2 top = size - 1;
    vec3 a = texelFetch(picture, clamp(low, ivec2(0), top), 0).rgb;
    vec3 b = texelFetch(picture, clamp(low + ivec2(1, 0), ivec2(0), top), 0).rgb;
    vec3 c = texelFetch(picture, clamp(low + ivec2(0, 1), ivec2(0), top), 0).rgb;
    vec3 d = texelFetch(picture, clamp(low + ivec2(1, 1), ivec2(0), top), 0).rgb;
    return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}

// The share of the freeze's picture in the picture shown (0 when no freeze runs).
float frozen = 0.0;

// The picture shown at a place between pixels: the game's, with the freeze's picture over it.
vec3 shownAt(vec2 at) {
    vec3 game = bilinear(MainSampler, at);
    return frozen > 0.0 ? mix(game, bilinear(HoldSampler, at), frozen) : game;
}

// The strength of the effect of a slot from the parameter of its marker (strength / 100), 0 when its marker is not there.
float amountOf(int slot) {
    vec4 marker = texelFetch(StateSampler, ivec2(slot - 1, 0), 0);
    return marker.a > 0.5 ? round(marker.b * 255.0) / 100.0 : 0.0;
}

// The level of the effect of a slot (the high 4 bits of its marker's green; 8 is the usual), 0 when its marker is not there.
float levelOf(int slot) {
    vec4 marker = texelFetch(StateSampler, ivec2(slot - 1, 0), 0);
    return marker.a > 0.5 ? float(int(round(marker.g * 255.0)) >> 4) : 0.0;
}

// The ticks since the first tick of a timed effect (its parameter), -1 when its marker is not there or the time is not known.
float ticksOf(int slot) {
    vec4 color = texelFetch(StateSampler, ivec2(slot - 1, 2), 0);
    if (color.a < 0.5) return -1.0;
    ivec2 c = ivec2(round(color.rg * 255.0));
    return float((c.r << 8) | c.g) / 256.0;
}

// The ripple's own time after t real ticks: the integral of its speed, SLOW_START rising to 1 by smoothstep over SLOW_TICKS.
float rippleTime(float t) {
    float u = clamp(t / SLOW_TICKS, 0.0, 1.0);
    // (the integral of smoothstep from 0 to u is u^3 - u^4 / 2: 0.5 at u = 1)
    return SLOW_START * t + (1.0 - SLOW_START) * (SLOW_TICKS * (u * u * u - 0.5 * u * u * u * u) + max(t - SLOW_TICKS, 0.0));
}

// The place on the screen (pixels) of the display of a slot's marker; false when it is not known or behind the camera.
bool placeOf(int slot, out vec2 place) {
    vec4 color = texelFetch(StateSampler, ivec2(slot - 1, 1), 0);
    ivec3 c = ivec3(round(color.rgb * 255.0));
    ivec2 at = ivec2((c.r << 4) | (c.g >> 4), ((c.g & 15) << 8) | c.b);
    place = ((vec2(at) / 4094.0 * 4.0 - 2.0) * 0.5 + 0.5) * OutSize;
    return color.a > 0.5 && at.x != 4095;
}

void main() {
    ivec2 here = ivec2(gl_FragCoord.xy);
    vec4 outline = texelFetch(DiffuseSampler, here, 0);
    vec3 lineColor = outline.rgb * outline.a;
    float lineAlpha = outline.a * outline.a;

    float layerAlpha = 0.0;
    vec3 layerColor = vec3(0.0);
    float chromatic = amountOf(1);
    // the impact: how far along its ripple is (0 - 1), from its own time; nothing before its first tick (a client a little behind the
    // server counts nearly 240) and after its end
    float impact = levelOf(2) / 8.0;
    float ticks = ticksOf(2);
    float along = rippleTime(ticks) / RIPPLE_TICKS;
    vec2 center;
    if (impact > 0.0 && (ticks < 0.0 || ticks > 200.0 || along >= 1.0 || !placeOf(2, center))) impact = 0.0;
    // the freeze: the share of the picture of its moment, from its own time
    float freezeTicks = ticksOf(3);
    float freezeSpan = FREEZE_TICKS * levelOf(3) / 8.0;
    if (freezeSpan > 0.0 && freezeTicks >= 0.0 && freezeTicks <= 200.0 && freezeTicks < freezeSpan) {
        float u = freezeTicks / freezeSpan;
        float spent = 2.0 * (u * u * u - 0.5 * u * u * u * u);
        frozen = (exp(-FREEZE_SHARE * spent) - exp(-FREEZE_SHARE)) / (1.0 - exp(-FREEZE_SHARE));
    }
    vec3 under = texelFetch(MainSampler, here, 0).rgb;
    vec3 goal = frozen > 0.0 ? mix(under, texelFetch(HoldSampler, here, 0).rgb, frozen) : under;
    if (chromatic > 0.0 || impact > 0.0) {
        vec2 from = gl_FragCoord.xy;
        if (impact > 0.0) {
            vec2 away = from - center;
            float dist = length(away);
            float height = OutSize.y;
            float ring = RIPPLE_REACH * height * (1.0 - (1.0 - along) * (1.0 - along));
            float across = smoothstep(0.0, 1.0, clamp(1.0 - abs(dist - ring) / (RIPPLE_WIDTH * height), 0.0, 1.0));
            float push = RIPPLE_PUSH * height * impact * (1.0 - along) * (1.0 - along);
            from -= away / max(dist, 1.0) * push * across;
        }
        vec2 shift = (gl_FragCoord.xy - OutSize * 0.5) * SHIFT * chromatic;
        goal = chromatic > 0.0 ? vec3(shownAt(from + shift).r, shownAt(from).g, shownAt(from - shift).b) : shownAt(from);
    }
    if (goal != under) {
        // the least alpha a such that some color c (0 - 1) gives c * a + under * (1 - a) = goal, channel by channel
        vec3 up = (goal - under) / max(vec3(1.0) - under, vec3(0.00001));
        vec3 down = (under - goal) / max(under, vec3(0.00001));
        vec3 need = max(up, down);
        layerAlpha = clamp(max(need.r, max(need.g, need.b)), 0.0, 1.0);
        if (layerAlpha > 0.0) layerColor = clamp((goal - under * (1.0 - layerAlpha)) / layerAlpha, 0.0, 1.0);
    }

    // the outline over the layer, as one layer
    float alpha = 1.0 - (1.0 - layerAlpha) * (1.0 - lineAlpha);
    vec3 color = alpha > 0.0 ? (lineColor * lineAlpha + layerColor * layerAlpha * (1.0 - lineAlpha)) / alpha : vec3(0.0);
    fragColor = vec4(color, alpha);
}
