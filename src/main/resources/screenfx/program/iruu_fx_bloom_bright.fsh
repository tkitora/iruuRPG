#version 150

// Bloom, step 1: the bright part of the picture, made small (the size of this pass's target). Nothing (black) when the marker of the bloom
// (slot 1, glow color (177, 1, strength)) is not there.

uniform sampler2D DiffuseSampler;   // the game's picture
uniform sampler2D StateSampler;     // the markers read (iruu_fx_read): pixel (slot - 1, 0)
uniform vec2 OutSize;

out vec4 fragColor;

const float THRESHOLD = 0.65;       // brightness (luma) where the bloom starts
const float KNEE = 0.15;            // how softly it starts

// The strength of the bloom (strength / 100), or 0 when its marker is not there.
float strength() {
    vec4 marker = texelFetch(StateSampler, ivec2(0, 0), 0);
    return marker.a > 0.5 ? round(marker.b * 255.0) / 100.0 : 0.0;
}

void main() {
    float amount = strength();
    if (amount <= 0.0) {
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    ivec2 inSize = textureSize(DiffuseSampler, 0);
    vec2 scale = vec2(inSize) / OutSize;
    // the average of a 4 x 4 grid over the pixels this small pixel covers
    vec3 sum = vec3(0.0);
    vec2 from = floor(gl_FragCoord.xy) * scale;
    for (int y = 0; y < 4; y++) {
        for (int x = 0; x < 4; x++) {
            ivec2 at = ivec2(from + (vec2(x, y) + 0.5) * scale / 4.0);
            sum += texelFetch(DiffuseSampler, clamp(at, ivec2(0), inSize - 1), 0).rgb;
        }
    }
    vec3 color = sum / 16.0;
    float luma = dot(color, vec3(0.2126, 0.7152, 0.0722));
    // a soft knee: 0 below THRESHOLD - KNEE, a curve to THRESHOLD + KNEE, then straight
    float soft = clamp(luma - THRESHOLD + KNEE, 0.0, 2.0 * KNEE);
    soft = soft * soft / (4.0 * KNEE + 0.00001);
    float over = max(soft, luma - THRESHOLD) / max(luma, 0.00001);
    fragColor = vec4(color * over * amount, 1.0);
}
