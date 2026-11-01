#version 150

// A plain copy (the held picture kept for the next frame).

uniform sampler2D DiffuseSampler;

out vec4 fragColor;

void main() {
    fragColor = texelFetch(DiffuseSampler, ivec2(gl_FragCoord.xy), 0);
}
