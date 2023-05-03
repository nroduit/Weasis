#version 330 core

layout (location=0) in vec2 position;
out vec2 quadCoordinates;

void main() {
    gl_Position = vec4(position, 1.0, 1.0);
    quadCoordinates = position;
}
