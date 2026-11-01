#version 150

// The last pass: the outline picture the game lays over the screen at the end of the frame (blend: source alpha), made of
//  - the bloom (two sizes of glow, made bigger again bilinearly), as a layer that adds it to the game's picture: its alpha is the least that
//    can do that,
//  - the glowing outlines over it, as the game draws them (its last blit gives (rgb * a, a * a)),
//  - black over all of them, when the marker of the darkening (slot 2) is there: alpha strength / 200 (100 gives 0.5).

uniform sampler2D DiffuseSampler;   // the outlines
uniform sampler2D BloomSampler;     // the blurred bright part (small)
uniform sampler2D WideBloomSampler; // the same, smaller and blurred again (a wider glow)
uniform sampler2D MainSampler;      // the game's picture
uniform sampler2D StateSampler;     // the markers read (iruu_fx_read): pixel (slot - 1, 0)
uniform vec2 OutSize;

out vec4 fragColor;

vec3 bilinear(sampler2D picture, vec2 uv) {
    ivec2 size = textureSize(picture, 0);
    vec2 at = uv * vec2(size) - 0.5;
    ivec2 low = ivec2(floor(at));
    vec2 t = at - vec2(low);
    ivec2 top = size - 1;
    vec3 a = texelFetch(picture, clamp(low, ivec2(0), top), 0).rgb;
    vec3 b = texelFetch(picture, clamp(low + ivec2(1, 0), ivec2(0), top), 0).rgb;
    vec3 c = texelFetch(picture, clamp(low + ivec2(0, 1), ivec2(0), top), 0).rgb;
    vec3 d = texelFetch(picture, clamp(low + ivec2(1, 1), ivec2(0), top), 0).rgb;
    return mix(mix(a, b, t.x), mix(c, d, t.x), t.y);
}

void main() {
    ivec2 here = ivec2(gl_FragCoord.xy);
    vec4 outline = texelFetch(DiffuseSampler, here, 0);
    vec3 lineColor = outline.rgb * outline.a;
    float lineAlpha = outline.a * outline.a;

    vec2 uv = gl_FragCoord.xy / OutSize;
    vec3 bloom = bilinear(BloomSampler, uv) + bilinear(WideBloomSampler, uv);
    vec3 under = texelFetch(MainSampler, here, 0).rgb;
    vec3 goal = min(under + bloom, vec3(1.0));
    // the least alpha a such that some color c (0 - 1) gives c * a + under * (1 - a) = goal
    vec3 need = (goal - under) / max(vec3(1.0) - under, vec3(0.00001));
    float bloomAlpha = clamp(max(need.r, max(need.g, need.b)), 0.0, 1.0);
    vec3 bloomColor = bloomAlpha > 0.0 ? clamp((goal - under * (1.0 - bloomAlpha)) / bloomAlpha, 0.0, 1.0) : vec3(0.0);

    // the outline over the bloom, as one layer
    float alpha = 1.0 - (1.0 - bloomAlpha) * (1.0 - lineAlpha);
    vec3 color = alpha > 0.0 ? (lineColor * lineAlpha + bloomColor * bloomAlpha * (1.0 - lineAlpha)) / alpha : vec3(0.0);

    // black over everything
    vec4 darken = texelFetch(StateSampler, ivec2(1, 0), 0);
    float dark = darken.a > 0.5 ? clamp(round(darken.b * 255.0) / 200.0, 0.0, 1.0) : 0.0;
    float total = 1.0 - (1.0 - alpha) * (1.0 - dark);
    color = total > 0.0 ? color * alpha * (1.0 - dark) / total : vec3(0.0);
    fragColor = vec4(color, total);
}
