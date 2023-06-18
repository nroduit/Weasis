#version 330 core
// Texture space
uniform sampler2D compute;
in vec2 quadCoordinates;
void main() {
    vec2 position = (quadCoordinates +1.0)/2.0;
    // Get color from texture space with coordinates
    gl_FragColor = texture(compute,position);
}
