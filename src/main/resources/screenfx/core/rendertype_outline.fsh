#version 150

// The game's outline shader; a marker of a screen effect (see rendertype_outline.vsh) is drawn whole, whatever its texture: its rows 0 - 1
// its glow color, its rows 2 - 3 its place on the screen, 12 bits for x and 12 for y (0 - 4094 for -2 - 2 across the screen; 4095 for both
// when it is behind the camera) in the 24 bits of the color.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
flat in int marker;
flat in vec3 markerAt;

out vec4 fragColor;

void main() {
    if (marker == 1) {
        if (gl_FragCoord.y < 2.0) {
            fragColor = vec4(vertexColor.rgb, 1.0);
        } else {
            ivec2 at = markerAt.z > 0.5 ? ivec2(round((clamp(markerAt.xy, -2.0, 2.0) + 2.0) / 4.0 * 4094.0)) : ivec2(4095);
            fragColor = vec4(float(at.x >> 4), float(((at.x & 15) << 4) | (at.y >> 8)), float(at.y & 255), 255.0) / 255.0;
        }
        return;
    }
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb * vertexColor.rgb, ColorModulator.a);
}
