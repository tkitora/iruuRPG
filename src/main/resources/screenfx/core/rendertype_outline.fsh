#version 150

// The game's outline shader; a marker of a screen effect (see rendertype_outline.vsh) is drawn whole, whatever its texture.

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;
flat in int marker;

out vec4 fragColor;

void main() {
    if (marker == 1) {
        fragColor = vec4(vertexColor.rgb, 1.0);
        return;
    }
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a == 0.0) {
        discard;
    }
    fragColor = vec4(ColorModulator.rgb * vertexColor.rgb, ColorModulator.a);
}
