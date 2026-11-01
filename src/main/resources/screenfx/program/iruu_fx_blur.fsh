#version 150

// Bloom, step 2: a Gaussian blur along BlurDir (pixels of the small picture; the pass runs once across and once down).

uniform sampler2D DiffuseSampler;
uniform vec2 BlurDir;

out vec4 fragColor;

const int RADIUS = 12;
const float SIGMA = 5.0;

void main() {
    ivec2 size = textureSize(DiffuseSampler, 0);
    ivec2 here = ivec2(gl_FragCoord.xy);
    ivec2 dir = ivec2(BlurDir);
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = -RADIUS; i <= RADIUS; i++) {
        float weight = exp(-float(i * i) / (2.0 * SIGMA * SIGMA));
        sum += texelFetch(DiffuseSampler, clamp(here + dir * i, ivec2(0), size - 1), 0).rgb * weight;
        total += weight;
    }
    fragColor = vec4(sum / total, 1.0);
}
