#version 150

// The full-screen square of a post pass (the fragment shaders find their pixel by gl_FragCoord). Made from OutSize, not ProjMat: the game
// gives every pass of a chain the screen's ProjMat, which would cover only the bottom-left corner of a target smaller than the screen (t127).

in vec4 Position;

uniform vec2 OutSize;

void main() {
    gl_Position = vec4(Position.xy / OutSize * 2.0 - 1.0, 0.2, 1.0);
}
