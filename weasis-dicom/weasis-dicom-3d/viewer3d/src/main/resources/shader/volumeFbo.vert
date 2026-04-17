#version 330 core
// Full-screen quad vertex shader for the FBO-based volume rendering fallback.
// Requires OpenGL 3.3 / GLSL 3.30. No 4.x features are used.

layout (location = 0) in vec2 position;
out vec2 quadCoordinates;

void main() {
    // z = 0.0 places fragments at mid clip-space; z = 1.0 (far plane) risks depth culling.
    gl_Position = vec4(position, 0.0, 1.0);
    quadCoordinates = position;
}



