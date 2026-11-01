#version 150

// The game's outline shader, plus the markers of iruuRPG's screen effects (see post/entity_outline.json): a glowing display whose glow
// color is (177, slot, strength) is drawn as a 4 x 4 pixel square in the bottom-left corner of the outline picture, at x = 4 * (slot - 1),
// wherever the display really is; the post shaders read it there and take it away before the outlines are drawn. Its rows 0 - 1 are the
// glow color, its rows 2 - 3 where the display is on the screen (rendertype_outline.fsh), for the effects centred on a place of the world.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;

out vec4 vertexColor;
out vec2 texCoord0;
flat out int marker;
flat out vec3 markerAt;     // the display's place: x, y on the screen (-1 - 1 across it), z 1 when it is in front of the camera

void main() {
    vertexColor = Color;
    texCoord0 = UV0;
    vec4 clip = ProjMat * ModelViewMat * vec4(Position, 1.0);
    ivec3 rgb = ivec3(round(Color.rgb * 255.0));
    if (rgb.r == 177 && rgb.g >= 1 && rgb.g <= 8 && ScreenSize.x > 32.0) {
        // (the display has no size: every corner of it is at its place)
        markerAt = clip.w > 0.0 ? vec3(clip.xy / clip.w, 1.0) : vec3(0.0);
        // every quad of the display becomes the same square: its 4 corners by the vertex's place in the quad
        int corner = gl_VertexID % 4;
        vec2 pixel = vec2(float(4 * (rgb.g - 1)) + (corner >= 2 ? 4.0 : 0.0), (corner == 0 || corner == 3) ? 4.0 : 0.0);
        gl_Position = vec4(pixel / ScreenSize * 2.0 - 1.0, 0.0, 1.0);
        marker = 1;
    } else {
        gl_Position = clip;
        markerAt = vec3(0.0);
        marker = 0;
    }
}
