#version 150

// The outlines' picture without the markers of the screen effects (glow color (177, slot 1 - 8, any)), so no outline is drawn around them.

uniform sampler2D DiffuseSampler;

out vec4 fragColor;

void main() {
    vec4 color = texelFetch(DiffuseSampler, ivec2(gl_FragCoord.xy), 0);
    ivec3 rgb = ivec3(round(color.rgb * 255.0));
    if (rgb.r == 177 && rgb.g >= 1 && rgb.g <= 8 && color.a > 0.99) color = vec4(0.0);
    fragColor = color;
}
