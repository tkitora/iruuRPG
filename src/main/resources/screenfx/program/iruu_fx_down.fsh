#version 150

// Bloom, step 3: the glow made half as big (the average of the pixels each new pixel covers), to be blurred again for a wider glow.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;

out vec4 fragColor;

void main() {
    ivec2 inSize = textureSize(DiffuseSampler, 0);
    vec2 scale = vec2(inSize) / OutSize;
    vec2 from = floor(gl_FragCoord.xy) * scale;
    vec3 sum = vec3(0.0);
    for (int y = 0; y < 2; y++) {
        for (int x = 0; x < 2; x++) {
            ivec2 at = ivec2(from + (vec2(x, y) + 0.5) * scale / 2.0);
            sum += texelFetch(DiffuseSampler, clamp(at, ivec2(0), inSize - 1), 0).rgb;
        }
    }
    fragColor = vec4(sum / 4.0, 1.0);
}
