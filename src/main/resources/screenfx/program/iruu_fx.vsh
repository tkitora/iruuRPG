#version 150

// The full-screen square of a post pass (the fragment shaders find their pixel by gl_FragCoord).

in vec4 Position;

uniform mat4 ProjMat;

void main() {
    vec4 outPos = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(outPos.xy, 0.2, 1.0);
}
