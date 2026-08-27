#version 330 core
// Full-screen quad blit of the rendered volume texture (compute image or FBO colour attachment).
// gl_FragColor is removed in core profiles: declare an explicit output instead.
uniform sampler2D compute;
// Fraction of the target that was actually rendered this frame — (1,1) at full resolution, less
// while the camera is being dragged and only a sub-rectangle was ray-cast (see FboRenderTexture).
uniform vec2 texScale;
in vec2 quadCoordinates;

layout (location = 0) out vec4 fragColor;

void main() {
    vec2 position = (quadCoordinates + 1.0) / 2.0 * texScale;
    // Stay half a texel inside the rendered sub-rectangle: GL_LINEAR would otherwise blend the
    // last row and column with the cleared texels beyond it.
    vec2 halfTexel = 0.5 / vec2(textureSize(compute, 0));
    fragColor = texture(compute, min(position, texScale - halfTexel));
}