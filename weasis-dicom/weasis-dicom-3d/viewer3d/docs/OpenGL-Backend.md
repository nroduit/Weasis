# OpenGL back-end of the 3D viewer

How the volume renderer reaches the graphics card, and how a frame gets from the card into a Swing
panel. What the renderer draws — ray-casting, presets, cinematic lighting, path tracing — is not
described here; this is only the layer underneath it.

## One context, one thread

The application owns a single OpenGL context. It is created, used and destroyed on one daemon
thread, `GlThread`, and is never made current anywhere else. Nothing is shared between contexts, so
a texture name means the same thing in every view, and a view can be closed while another renders.

`GlRuntime` holds that thread for the application: it starts on first use and stops with the
plugin.

Two kinds of work reach the thread:

- **A frame** — `invokeAndWait`, which jumps ahead of the queue. The Swing thread asks for a frame
  and waits for it, exactly as it used to wait for its own drawable, so the render state is still
  only mutated and read while that thread is blocked and nothing needs to become thread-safe.
- **An upload or a deletion** — `submit`, which waits its turn. That is what keeps a volume loading
  in the background from delaying the display, and what finally gives a `destroy` a current
  context.

One rule holds it together: **a task must never block on another thread**, and in particular never
call the Swing thread synchronously. The Swing thread is waiting for frames; a task that waited for
it would deadlock the application.

## The facade

`Gl` is the only OpenGL surface the renderer sees: the entry points and constants it uses, as plain
Java methods. Pixel and buffer payloads are native `MemorySegment`s, generators return the names
they created, and queries return their value instead of writing through a pointer.

Its implementation, `FfmGl`, calls the driver directly through the Foreign Function & Memory API,
so the viewer ships no native library of its own. Entry points resolve on the first call, hence
after a context is current, which is what Windows requires. The compute entry points are held
apart, because a driver that stops at OpenGL 4.1 does not export them at all.

## The context

`GlContext` is the context itself, one implementation per platform binding:

| | |
|---|---|
| `EglContext` | Linux, through EGL. The session display first, kept only if its renderer is not a software rasteriser; otherwise the EGL devices, which is also what a machine with no display server has. Surfaceless when the driver offers it, on a 1 × 1 pbuffer otherwise |
| `WglContext` | Windows. WGL cannot be asked for a core profile directly, so a legacy context is created on an invisible window only to resolve `wglCreateContextAttribsARB`, and deleted once the real one exists |
| `CglContext` | macOS, through CGL, which — unlike the AppKit classes — has no main-thread constraint. No drawable is attached, which is all a framebuffer object needs. macOS stops at OpenGL 4.1 |

Versions are tried in descending order and the first a driver grants wins; OpenGL 3.3 is the
minimum the shaders compile against, and 4.3 is where the compute path becomes available. The
preference page and `view3d:info` report which context answered.

## How a frame reaches Swing

No OpenGL surface is ever on screen. `VolumeCanvas` is a plain `JPanel`, and each view owns a
`RenderTarget`: a colour texture with its framebuffer object at the surface size, a pixel pack
buffer, and a pair of images the frames alternate between.

A frame runs like this:

1. The Swing thread paints. If nothing changed since the last frame it draws the image again and
   stops here — an expose, a pointer move or an overlay change costs no OpenGL work.
2. Otherwise it asks `GlThread` for a frame and waits. On that thread the target is sized to the
   surface, the ray-casting passes run as they always have, and the quad pass blends their output
   over the background colour into the target.
3. The quad pass samples upside down, so the rows `glReadPixels` returns — which start at the
   bottom of a framebuffer — arrive in the order an image wants them. The frame is read into the
   pack buffer, mapped, copied into the back image, and the two images swap.
4. The Swing thread draws the front image and paints the overlays on top.

During a live resize the last image is drawn stretched until the next one lands. The surface size
is the panel size times the scale of the display it is on, so the ray-caster, the camera and the
target all work in physical pixels.

## Ray-casting paths

Which of the two ray-casters runs is a property of the driver, not of this layer: `ComputeTexture`
dispatches a compute shader on OpenGL 4.3 and above, `FboRenderTexture` draws a full-screen quad
into a framebuffer object on anything from 3.3. Both write the same output texture, which the quad
pass then blits into the render target, so everything above is identical. The FBO path ray-casts
only the logical-resolution sub-rectangle while the camera is being dragged, and the blit scales it
back up.

`Program` compiles and links a shader set and holds its uniform setters; `ShaderManager` reads the
sources and resolves their includes.

With one context on one thread, a link that takes the driver seconds would freeze every view. When
the driver offers `KHR_parallel_shader_compile` (or its ARB twin), the context enables it at
creation and a program can be linked without waiting: `Program.startLink` hands the sources over,
and `Program.isReady` polls the completion status on later frames. The path-tracing program is
linked this way as soon as a view has its shaders, and a view asked to path-trace shows its
composite preview until the link completes. A driver may still finish the program on the thread
that first uses it, a fraction of the full link; drivers without the extension link
synchronously.

## What the tests hold

The renderer is exercised without Swing. `SyntheticVolume` is a phantom computed from a formula and
`ReferenceScene` renders it through the viewer's own programs, textures and render target, driven
by fixed uniform values. On that basis:

- every shader compiles and links, and every rendering type produces a frame, on each ray-casting
  path the machine offers;
- a frame reaches the image the right way up;
- opening and closing a volume many times must leak no OpenGL object.

A frame is only reproducible on the driver that rendered it, so nothing is compared against a
stored image. Tests that need a driver skip themselves when there is none.
