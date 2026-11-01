#version 150

// The last pass: the outline picture the game lays over the screen at the end of the frame (blend: source alpha), made of
//  - the chromatic aberration (slot 1), when its marker is there: red is taken from further out from the middle of the screen, blue from
//    further in (the shift grows with the distance from the middle: strength / 100 * SHIFT of it), green stays. The game's picture cannot be
//    written here, so it is a layer that turns the game's picture into that: its alpha is the least that can do that (0 where the colors do
//    not change, so only the edges of things are touched),
//  - the glowing outlines over it, as the game draws them (its last blit gives (rgb * a, a * a)).

uniform sampler2D DiffuseSampler;   // the outlines
uniform sampler2D MainSampler;      // the game's picture
uniform sampler2D StateSampler;     // the markers read (iruu_fx_read): pixel (slot - 1, 0)
uniform vec2 OutSize;

out vec4 fragColor;

const float SHIFT = 0.012;          // at strength 100: the shift is 1.2 % of the distance from the middle (11 pixels at the side of 1920 x 1080)

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

void main() {
    ivec2 here = ivec2(gl_FragCoord.xy);
    vec4 outline = texelFetch(DiffuseSampler, here, 0);
    vec3 lineColor = outline.rgb * outline.a;
    float lineAlpha = outline.a * outline.a;

    float layerAlpha = 0.0;
    vec3 layerColor = vec3(0.0);
    vec4 marker = texelFetch(StateSampler, ivec2(0, 0), 0);
    if (marker.a > 0.5) {
        float amount = round(marker.b * 255.0) / 100.0;
        vec2 shift = (gl_FragCoord.xy - OutSize * 0.5) * SHIFT * amount;
        vec3 under = texelFetch(MainSampler, here, 0).rgb;
        vec3 goal = vec3(mainAt(gl_FragCoord.xy + shift).r, under.g, mainAt(gl_FragCoord.xy - shift).b);
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
