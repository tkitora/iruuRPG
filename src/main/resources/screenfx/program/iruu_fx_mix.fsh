#version 150

// The last pass: the outline picture the game lays over the screen at the end of the frame (blend: source alpha), made of
//  - the screen effects: each pixel of the game's picture is taken from another place,
//      - the impact (slot 2, t130): from nearer to a place of the world (its marker's display), the more the nearer to it (a bulge that fades
//        away from it: a share strength / 100 * PULL of the distance at the place, less and less further out, over about RADIUS of the
//        screen's height),
//      - the chromatic aberration (slot 1, t129): red from further out from the middle of the screen, blue from further in (strength / 100
//        * SHIFT of the distance from the middle), green from the place itself.
//    The game's picture cannot be written here, so it is a layer that turns the game's picture into that: its alpha is the least that can do
//    that (0 where the colors do not change, so only the edges of things are touched),
//  - the glowing outlines over it, as the game draws them (its last blit gives (rgb * a, a * a)).

uniform sampler2D DiffuseSampler;   // the outlines
uniform sampler2D MainSampler;      // the game's picture
uniform sampler2D StateSampler;     // the markers read (iruu_fx_read): pixel (slot - 1, 0) the glow color, (slot - 1, 1) the place
uniform vec2 OutSize;

out vec4 fragColor;

const float SHIFT = 0.012;          // chromatic, at strength 100: the shift is 1.2 % of the distance from the middle (11 pixels at the side of 1920 x 1080)
const float PULL = 0.3;             // impact, at strength 100: at its place, a pixel is taken from 30 % nearer to it (1.4 times bigger)
const float RADIUS = 0.35;          // impact: how far it reaches (the share of the screen's height where the pull is down to 1 / e)

// The game's picture at a place between pixels (pixel units, centres at .5), bilinear (the game reads its pictures nearest).
vec3 mainAt(vec2 at) {
    ivec2 size = textureSize(MainSampler, 0);
    vec2 p = at - 0.5;
    ivec2 low = ivec2(floor(p));
    vec2 t = p - vec2(low);
    ivec2 top = size - 1;
    vec3 a = texelFetch(MainSampler, clamp(low, ivec2(0), top), 0).rgb;
    vec3 b = texelFetch(MainSampler, clamp(low + ivec2(1, 0), ivec2(0), top), 0).rgb;
    vec3 c = texelFetch(MainSampler, clamp(low + ivec2(0, 1), ivec2(0), top), 0).rgb;
    vec3 d = texelFetch(MainSampler, clamp(low + ivec2(1, 1), ivec2(0), top), 0).rgb;
    return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}

// The strength of the effect of a slot (strength / 100), 0 when its marker is not there.
float amountOf(int slot) {
    vec4 marker = texelFetch(StateSampler, ivec2(slot - 1, 0), 0);
    return marker.a > 0.5 ? round(marker.b * 255.0) / 100.0 : 0.0;
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
    float impact = amountOf(2);
    vec2 center;
    if (impact > 0.0 && !placeOf(2, center)) impact = 0.0;
    if (chromatic > 0.0 || impact > 0.0) {
        vec2 from = gl_FragCoord.xy;
        if (impact > 0.0) {
            vec2 away = from - center;
            float radius = RADIUS * OutSize.y;
            from -= away * PULL * impact * exp(-dot(away, away) / (radius * radius));
        }
        vec2 shift = (gl_FragCoord.xy - OutSize * 0.5) * SHIFT * chromatic;
        vec3 under = texelFetch(MainSampler, here, 0).rgb;
        vec3 goal = chromatic > 0.0 ? vec3(mainAt(from + shift).r, mainAt(from).g, mainAt(from - shift).b) : mainAt(from);
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
