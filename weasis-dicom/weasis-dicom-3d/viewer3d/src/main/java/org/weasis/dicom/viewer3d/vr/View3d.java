/*
 * Copyright (c) 2022 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d.vr;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.Paint;
import java.awt.Point;
import java.awt.Stroke;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.lang.foreign.Arena;
import java.lang.foreign.ValueLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.ToolTipManager;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4d;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.MouseActionAdapter;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.image.ZoomOp.Interpolation;
import org.weasis.core.api.image.util.ImageLayer;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.FontItem;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.*;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.export.AnimationExportDialog;
import org.weasis.core.ui.editor.image.export.RecorderSourceBuilder;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.graphic.imp.seg.SegRegion;
import org.weasis.core.ui.model.layer.AbstractInfoLayer;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.SpecialElementRegion;
import org.weasis.dicom.codec.export.DicomScSinkFactory;
import org.weasis.dicom.codec.geometry.PatientOrientation;
import org.weasis.dicom.codec.seg.SegBuildScheduler;
import org.weasis.dicom.codec.seg.SegSpecialElement;
import org.weasis.dicom.codec.seg.SegmentationVolume;
import org.weasis.dicom.explorer.DicomSeriesHandler;
import org.weasis.dicom.viewer2d.mpr.AxisDirection;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprFactory;
import org.weasis.dicom.viewer2d.mpr.OriginalStack;
import org.weasis.dicom.viewer2d.mpr.SegVolumeBuilder;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.InfoLayer3d;
import org.weasis.dicom.viewer3d.Messages;
import org.weasis.dicom.viewer3d.OpenGLInfo;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.dockable.DisplayTool;
import org.weasis.dicom.viewer3d.dockable.SegmentationTool.Type;
import org.weasis.dicom.viewer3d.export.RotationSourceBuilder;
import org.weasis.dicom.viewer3d.geometry.Axis;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.geometry.View;
import org.weasis.dicom.viewer3d.gl.Gl;
import org.weasis.dicom.viewer3d.gl.GlException;
import org.weasis.dicom.viewer3d.gl.GlRuntime;
import org.weasis.dicom.viewer3d.gl.RenderTarget;
import org.weasis.dicom.viewer3d.vr.TextureData.PixelFormat;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;
import org.weasis.opencv.seg.RegionAttributes;

public class View3d extends VolumeCanvas
    implements ViewCanvas<DicomImageElement>,
        RenderingLayerChangeListener<DicomImageElement>,
        ViewProgress,
        LiveCaptureView {

  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(View3d.class);

  /** What the view draws into and what Swing paints from. Owned by the GL thread. */
  private final RenderTarget renderTarget = new RenderTarget();

  /** Set by everything that used to force a {@code display()}; cleared once the frame is read. */
  private volatile boolean dirty = true;

  private boolean shadersReady;

  static final float[] vertexBufferData =
      new float[] {
        -1.0f, -1.0f,
        1.0f, -1.0f,
        -1.0f, 1.0f,
        -1.0f, 1.0f,
        1.0f, -1.0f,
        1.0f, 1.0f
      };

  protected final FocusHandler<DicomImageElement> focusHandler;
  protected final GraphicMouseHandler<DicomImageElement> graphicMouseHandler;

  private int pointerType = 0;
  private final LayerAnnotation infoLayer;
  protected final ContextMenuHandler<DicomImageElement> contextMenuHandler;

  private final TextureData texture;
  private final Program program;
  private final Program quadProgram;

  /**
   * The path tracer, built from the same includes as {@link #program} but linked on its own and
   * only when first needed: together, the two took the driver's linker several times as long.
   */
  private final Program tracerProgram;

  /** Whether the tracer program has been linked and given the uniform setters of the main one. */
  private boolean tracerReady;

  /**
   * Set while a driver with parallel shader compile is still linking the tracer program; path
   * tracing then shows its composite preview and polls the link on {@link #tracerLinkPoll}.
   */
  private boolean tracerLinking;

  private final Timer tracerLinkPoll =
      new Timer(
          TRACER_LINK_POLL_MS,
          e -> {
            if (isShowing()) {
              display();
            }
          });

  /** Post-pass of the path tracer: filters the running average into the display image. */
  private final Program denoiseProgram;

  private final boolean useComputeShader;
  protected final RenderingLayer<DicomImageElement> renderingLayer;

  private int vertexBuffer;
  protected Preset volumePreset;
  private Preset renderedPreset;
  private final ConcurrentLinkedQueue<Preset> retiredPresets = new ConcurrentLinkedQueue<>();
  private JProgressBar progressBar;

  private volatile Vector3d mprCrossHairPosition; // NOSONAR visibility reference
  private volatile Quaterniond mprCrossHairRotation; // NOSONAR visibility reference
  private volatile CrosshairCutMode mprCrossHairCutMode = CrosshairCutMode.NONE;

  private PropertyChangeListener mprCrossHairListener;
  private DicomVolTexture mprCrossHairListenerSource;

  /** Segmentation overlay 3D texture — null when no segmentation is active. */
  private volatile SegVolumeTexture segVolumeTexture; // NOSONAR visibility reference

  /** Frame-time breakdown, inert unless {@link RenderProfiler#P_PROFILE} is set. */
  private final RenderProfiler profiler = new RenderProfiler(this);

  /** Samples per pixel a path-traced scene accumulates before the view stops refining itself. */
  public static final int PATH_TRACING_FRAMES = 512;

  // Lighting options that path tracing switched on by itself, to give them back when leaving it.
  private boolean lightingForcedByPathTracing;
  private boolean shadingForcedByPathTracing;

  /** Interval at which a frame checks whether the driver has finished linking the tracer. */
  private static final int TRACER_LINK_POLL_MS = 50;

  /** Displayed frame time the sample count per frame is tuned to. */
  private static final long TARGET_FRAME_NANOS = 50_000_000L;

  /** Samples per pixel one displayed frame may trace at most, whatever the frame time. */
  private static final int MAX_SAMPLES_PER_FRAME = 64;

  /** Running average of the path tracer, allocated on first use. */
  private final AccumulationBuffers accumulation = new AccumulationBuffers();

  /** Set while an export captures the view, to keep the convergence message out of the frames. */
  private boolean capturing;

  /** Samples per pixel already averaged when the frame being rendered starts; 0 restarts. */
  private int frameIndex;

  /**
   * Samples per pixel a displayed frame traces. Each displayed frame costs a Swing repaint, the
   * denoise pass and the panel read-back on top of the tracing, so the count doubles while a frame
   * stays well under {@link #TARGET_FRAME_NANOS} and halves when it overshoots.
   */
  private int samplesPerFrame = 1;

  /** Samples the frame being rendered traces; 0 outside a path-traced frame. */
  private int tracedSamples;

  /** Whether the frame being rendered bounds its extinction per block of the majorant grid. */
  private boolean majorantGridReady;

  /** Per-block extinction bounds of the tracer, baked from the grid for the current window. */
  private final MajorantMap majorantMap = new MajorantMap();

  /** Fingerprint of the scene the current average belongs to. */
  private long sceneFingerprint;

  /** Bumped by every change that is not visible in the fingerprint's other inputs. */
  private int sceneVersion;

  /** Renders again while the average converges; coalesced so the EDT keeps serving other events. */
  private final Timer accumulationTimer =
      new Timer(
          0,
          e -> {
            if (isShowing()) {
              display();
            }
          });

  /** Environment textures of this context, uploaded on first use and kept until disposal. */
  private final Map<EnvironmentMap, EnvironmentTexture> environmentTextures =
      new EnumMap<>(EnvironmentMap.class);

  /** 1×1×1 placeholders keeping the segmentation sampler units complete, see bindSegTextures. */
  private int segPlaceholderTextureId;

  private int segPlaceholderColorId;

  /**
   * The SEG files contained in the current segmentation texture, each with the segment-number
   * offset applied when several files were merged into one volume — see {@link #mergeSegVolumes}.
   * Consumed by {@link #buildRegionOverrideMap()} so the colour LUT overrides target the shifted
   * IDs and so unchecked files are always forced invisible.
   */
  private volatile List<RenderedSeg> renderedSegs = List.of(); // NOSONAR visibility ref

  /** Per-view segmentation display mode — applied through the synchronization mechanism. */
  private volatile Type segType = Type.NONE;

  /** Generation stamp; bumping it invalidates any in-flight asynchronous seg texture build. */
  private final AtomicInteger segBuildGeneration = new AtomicInteger();

  /** {@code true} while an asynchronous seg texture build is running for this view. */
  private volatile boolean segBuildRunning;

  /**
   * Per-view auto-sync button rendered as a corner overlay. Lazily created the first time {@link
   * #updateSynchState()} is invoked, so it picks up the per-view {@link SynchData} attached by
   * {@link org.weasis.dicom.viewer3d.EventManager#updateAllListeners}.
   */
  private SynchViewButton synchButton;

  public View3d(
      ImageViewerEventManager<DicomImageElement> eventManager, DicomVolTexture volTexture) {
    super(eventManager, volTexture, null);
    // Detect whether compute shaders are available (OpenGL >= 4.3).
    OpenGLInfo glInfo = View3DFactory.getOpenGLInfo();
    this.useComputeShader =
        !View3DFactory.isFboForced() && (glInfo == null || glInfo.isComputeShaderCapable());

    if (useComputeShader) {
      this.texture = new ComputeTexture(this, ComputeTexture.COMPUTE_LOCAL_SIZE);
      this.program = new Program("compute", ShaderManager.COMPUTE_SHADER); // NON-NLS
      this.tracerProgram =
          new Program("pathTracing", ShaderManager.PATH_TRACING_COMPUTE_SHADER); // NON-NLS
      this.denoiseProgram = new Program("denoise", ShaderManager.DENOISE_COMPUTE_SHADER); // NON-NLS
      LOGGER.info("Volume rendering: using compute shader path (OpenGL >= 4.3)");
    } else {
      this.texture = new FboRenderTexture(this);
      this.program =
          new Program(
              "fbo", ShaderManager.FBO_VERTEX_SHADER, ShaderManager.FBO_FRAGMENT_SHADER); // NON-NLS
      this.tracerProgram =
          new Program(
              "pathTracingFbo", // NON-NLS
              ShaderManager.FBO_VERTEX_SHADER,
              ShaderManager.PATH_TRACING_FBO_FRAGMENT_SHADER);
      this.denoiseProgram =
          new Program(
              "denoiseFbo", // NON-NLS
              ShaderManager.FBO_VERTEX_SHADER,
              ShaderManager.DENOISE_FBO_FRAGMENT_SHADER);
      LOGGER.info(
          "Volume rendering: using FBO fragment-shader path (OpenGL 3.3 fallback{})",
          View3DFactory.isFboForced()
              ? ", forced via " + View3DFactory.P_FORCE_FBO
              : ""); // NON-NLS
    }
    this.quadProgram =
        new Program("basic", ShaderManager.VERTEX_SHADER, ShaderManager.FRAGMENT_SHADER); // NON-NLS
    setLayout(null);
    setOpaque(true);

    this.renderingLayer = new RenderingLayer<>();
    renderingLayer.getShadingOptions().loadCinematic(GuiUtils.getUICore().getLocalPersistence());
    accumulationTimer.setRepeats(false);
    tracerLinkPoll.setRepeats(false);
    this.volumePreset = Preset.getDefaultPreset(null);
    this.renderedPreset = volumePreset;
    renderedPreset.setRequiredBuilding(true);

    actionsInView.put(ActionVol.ORIENTATION_CUBE.cmd(), false);

    this.infoLayer = new InfoLayer3d(this);

    initActionWState();

    this.graphicMouseHandler = new GraphicMouseHandler<>(this);
    this.contextMenuHandler = new ContextMenuHandler<>(this);
    this.focusHandler = new FocusHandler<>(this);
    setFocusable(true);

    setBorder(viewBorder);
    setPreferredSize(new Dimension(4096, 4096));
    setMinimumSize(new Dimension(50, 50));
  }

  protected void initActionWState() {
    // set unit when texture load (middle image)
    actionsInView.put(ActionW.SPATIAL_UNIT.cmd(), Unit.PIXEL);
    actionsInView.put(ZOOM_TYPE_CMD, ZoomType.BEST_FIT);
    actionsInView.put(ActionW.ZOOM.cmd(), 0.0);
    actionsInView.put(ActionW.LENS.cmd(), false);
    actionsInView.put(ActionW.DRAWINGS.cmd(), true);
    actionsInView.put(LayerType.CROSSLINES.name(), true);
    actionsInView.put(ActionW.INVERSE_STACK.cmd(), false);
    actionsInView.put(ActionW.FILTERED_SERIES.cmd(), null);

    actionsInView.put(ActionW.ROTATION.cmd(), 0);
    actionsInView.put(ActionW.FLIP.cmd(), false);
  }

  @Override
  public void registerDefaultListeners() {
    addFocusListener(this);
    ToolTipManager.sharedInstance().registerComponent(this);
    renderingLayer.addLayerChangeListener(this);
    setTransferHandler(new DicomSeriesHandler(this));
    addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent e) {
            // The aspect ratio and the camera distance follow the size of the view
            camera.resetTransformation();
            Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            /*
             * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again
             * to default value to compute again the position. For instance, the image cannot be center aligned
             * until the view has been repaint once (because the size is null).
             */
            if (currentZoom <= 0.0) {
              zoom(0.0);
            }
            display();
          }
        });
  }

  /**
   * Subscribes to {@code "mpr.crosshair"} events on the given texture and unsubscribes from the
   * previous one. Pass {@code null} to only unsubscribe.
   */
  private void subscribeToCrossHairEvents(DicomVolTexture newTexture) {
    if (mprCrossHairListener != null && mprCrossHairListenerSource != null) {
      mprCrossHairListenerSource.removePropertyChangeListener(mprCrossHairListener);
      mprCrossHairListenerSource = null;
    }
    if (newTexture == null) {
      mprCrossHairPosition = null;
      mprCrossHairRotation = null;
      mprCrossHairListener = null;
      return;
    }
    mprCrossHairListener =
        evt -> {
          if (ActionVol.MPR_CROSSHAIR.cmd().equals(evt.getPropertyName())
              && evt.getNewValue() instanceof Object[] arr
              && arr.length >= 2
              && arr[0] instanceof Vector3d pos
              && arr[1] instanceof Quaterniond rot) {
            mprCrossHairPosition = new Vector3d(pos);
            mprCrossHairRotation = new Quaterniond(rot);
            display();
          }
        };
    mprCrossHairListenerSource = newTexture;
    newTexture.addPropertyChangeListener(mprCrossHairListener);
  }

  @Override
  public void disposeView() {
    disableMouseAndKeyListener();
    subscribeToCrossHairEvents(null);
    removeFocusListener(this);
    ToolTipManager.sharedInstance().unregisterComponent(this);
    renderingLayer.removeLayerChangeListener(this);
    if (volTexture != null) {
      GuiUtils.getUICore().closeSeries(volTexture.getSeries());
    }
    accumulationTimer.stop();
    tracerLinkPoll.stop();
    Preset retiredPreset =
        renderedPreset != null && renderedPreset.isPreview() ? renderedPreset : null;
    SegVolumeTexture svt = segVolumeTexture;
    segVolumeTexture = null;
    List<EnvironmentTexture> environments = List.copyOf(environmentTextures.values());
    environmentTextures.clear();
    tracerReady = false;
    GlRuntime.submit(
        gl -> {
          program.destroy(gl);
          tracerProgram.destroy(gl);
          quadProgram.destroy(gl);
          denoiseProgram.destroy(gl);
          texture.destroy(gl);
          accumulation.destroy(gl);
          majorantMap.dispose(gl);
          renderTarget.destroy(gl);
          environments.forEach(t -> t.destroy(gl));
          destroyRetiredPresets(gl);
          if (retiredPreset != null) {
            retiredPreset.destroy(gl);
          }
          if (svt != null) {
            // destroy() releases the SegVolumeTexture's retain on the SegmentationVolume. If no
            // other consumer (e.g. MprController) still holds the volume the CPU buffers are
            // freed inside release(); otherwise they stay alive for the MPR overlay.
            svt.destroy(gl);
          }
        });
    super.disposeView();
  }

  @Override
  public void handleLayerChanged(RenderingLayer<DicomImageElement> layer) {
    sceneVersion++;
    display();
  }

  public RenderingLayer<DicomImageElement> getRenderingLayer() {
    return renderingLayer;
  }

  public CrosshairCutMode getCrossHairCutMode() {
    CrosshairCutMode mode = mprCrossHairCutMode;
    return mode != null ? mode : CrosshairCutMode.NONE;
  }

  public Camera getCamera() {
    return camera;
  }

  public double getZoom() {
    return camera.getZoomFactor();
  }

  public void setVolTexture(DicomVolTexture volTexture) {
    if (this.volTexture != null) {
      this.volTexture.unregisterCrossHairRelay();
    }
    this.volTexture = volTexture;
    subscribeToCrossHairEvents(volTexture);
    if (volTexture != null && mprCrossHairCutMode != CrosshairCutMode.NONE) {
      volTexture.registerCrossHairRelay();
      syncMprPosition();
    }
    if (volTexture != null) {
      camera.setZoomFactor(-getBestFitViewScale());
      renderingLayer.setEnableRepaint(false);

      int quality = getDefaultQuality();
      renderingLayer.setQuality(quality);
      eventManager
          .getAction(ActionVol.VOL_QUALITY)
          .ifPresent(a -> a.setSliderValue(quality, false));
      renderingLayer.setEnableRepaint(true);
      // The anatomy volume always uses the modality's default preset.
      setVolumePreset(Preset.getDefaultPreset(volTexture.getModality()));
      // (Re)build the segmentation texture for this volume.
      updateSegmentation();
    } else {
      display();
    }
  }

  protected int getDefaultQuality() {
    if (volTexture == null) {
      return 0;
    }
    double val = Math.max(volTexture.getDepth(), volTexture.getMaxDimensionLength());
    return Math.min(RenderingLayer.MAX_QUALITY, (int) Math.round(val * camera.getFocalLength()));
  }

  @Override
  protected void paintComponent(Graphics graphs) {
    profiler.beginFrame();
    long panelStart = profiler.start();
    long frameStart = System.nanoTime();
    tracedSamples = 0;
    renderFrameIfNeeded();
    adaptSamplesPerFrame(System.nanoTime() - frameStart);
    long overlayStart = profiler.start();
    if (graphs instanceof Graphics2D graphics2D) {
      drawRenderedFrame(graphics2D);
      draw(graphics2D);
    }
    profiler.endFrame(
        panelStart,
        overlayStart,
        camera.isAdjusting(),
        getPassWidth(),
        getPassHeight(),
        tracedSamples,
        majorantGridReady);
  }

  /**
   * Tunes the samples per displayed frame to the time the frame just painted took, which covers the
   * tracing, the denoise pass and the read-back. A frame trimmed to the end of the budget says
   * nothing about the cost of a full one and is ignored.
   */
  private void adaptSamplesPerFrame(long frameNanos) {
    if (tracedSamples == 0 || tracedSamples != samplesPerFrame) {
      return;
    }
    if (frameNanos < TARGET_FRAME_NANOS / 2) {
      samplesPerFrame = Math.min(samplesPerFrame * 2, MAX_SAMPLES_PER_FRAME);
    } else if (frameNanos > TARGET_FRAME_NANOS * 3 / 2) {
      samplesPerFrame = Math.max(samplesPerFrame / 2, 1);
    }
  }

  /**
   * Renders on the GL thread and waits for it, as the panel used to wait for its own drawable. An
   * expose or an overlay-only repaint finds the frame still valid and paints it again for free.
   */
  private void renderFrameIfNeeded() {
    if (!dirty || getWidth() <= 0 || getHeight() <= 0) {
      return;
    }
    dirty = false;
    try {
      GlRuntime.thread()
          .invokeAndWait(
              gl -> {
                if (!shadersReady) {
                  initShaders(gl);
                  shadersReady = true;
                }
                renderTarget.ensureSize(gl, getSurfaceWidth(), getSurfaceHeight());
                renderTarget.bind(gl);
                render(gl, renderTarget);
                renderTarget.unbind(gl);
                renderTarget.read(gl);
                return null;
              });
    } catch (GlException e) {
      // The frame is skipped and the previous one stays on screen; 3D is not torn down for it.
      LOGGER.error("Rendering a 3D frame", e);
    }
  }

  /** Paints the last frame, stretched while a live resize outruns the renderer. */
  private void drawRenderedFrame(Graphics2D g2d) {
    java.awt.image.BufferedImage frame = renderTarget.image();
    if (frame != null) {
      g2d.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
    }
  }

  @Override
  protected void setDirty() {
    dirty = true;
  }

  protected void draw(Graphics2D g2d) {
    Stroke oldStroke = g2d.getStroke();
    Paint oldColor = g2d.getPaint();

    Font defaultFont = getFont();
    g2d.setFont(defaultFont);

    Point2D p = getClipViewCoordinatesOffset();
    g2d.translate(p.getX(), p.getY());
    drawLayers(g2d, affineTransform, inverseTransform);
    g2d.translate(-p.getX(), -p.getY());

    drawPointer(g2d, pointerType);
    if (infoLayer != null && volTexture != null) {
      g2d.setFont(getLayerFont());
      infoLayer.paint(g2d);
    }
    drawOnTop(g2d);

    g2d.setFont(defaultFont);
    g2d.setPaint(oldColor);
    g2d.setStroke(oldStroke);
  }

  protected void drawOnTop(Graphics2D g2d) {
    if (infoLayer != null
        && volTexture != null
        && LangUtil.nullToFalse(infoLayer.getVisible())
        && infoLayer.getDisplayPreferences(LayerItem.IMAGE_ORIENTATION)) {
      drawLpsOrientation(g2d);
    }
    drawProgressBar(g2d, progressBar);
    drawSegLoadingMessage(g2d);
    drawPathTracingProgress(g2d);
  }

  /**
   * Paints how far the path-traced average has converged, in the place and style of the
   * segmentation message, until the frame budget is spent. Hidden while the camera moves, since the
   * preview shown then is not accumulating.
   */
  private void drawPathTracingProgress(Graphics2D g2d) {
    if (capturing || !isPathTracingFrame() || frameIndex >= PATH_TRACING_FRAMES) {
      return;
    }
    String msg =
        Messages.format(
            "path.tracing.progress", Math.round(100.0 * frameIndex / PATH_TRACING_FRAMES));
    g2d.setFont(getLayerFont());
    FontMetrics fm = g2d.getFontMetrics();
    float x = (getWidth() - fm.stringWidth(msg)) / 2f;
    float y = getHeight() - fm.getHeight() * 2f;
    FontTools.paintColorFontOutline(g2d, msg, x, y, Color.ORANGE);
  }

  /**
   * Paints the pending segmentation build at the bottom of the view, as the 2D and MPR views do.
   */
  private void drawSegLoadingMessage(Graphics2D g2d) {
    if (!segBuildRunning) {
      return;
    }
    String msg = org.weasis.dicom.viewer2d.Messages.getString("seg.loading");
    g2d.setFont(getLayerFont());
    FontMetrics fm = g2d.getFontMetrics();
    float x = (getWidth() - fm.stringWidth(msg)) / 2f;
    float y = getHeight() - fm.getHeight() * 2f;
    FontTools.paintColorFontOutline(g2d, msg, x, y, Color.ORANGE);
  }

  private void drawLpsOrientation(Graphics2D g2d) {
    int axisLength = GuiUtils.getScaleLength(30);

    // LPS unit directions in volume-texture space:
    //   X+ = Left  (R/L, blue),  Y+ = Superior (S/I, green),  -Z = Posterior (A/P, red)
    Vector3d lrDir = new Vector3d(1, 0, 0);
    Vector3d apDir = new Vector3d(0, 0, -1);
    Vector3d siDir = new Vector3d(0, 1, 0);

    // Build the combined model × view matrix (direction-only — translation is irrelevant).
    // Camera.currentModelMatrix = rotateX(90°) × translate(-0.5,-0.5,-0.5)
    Matrix4d mv = camera.getViewMatrix().mul(Camera.currentModelMatrix, new Matrix4d());
    mv.transformDirection(lrDir);
    mv.transformDirection(apDir);
    mv.transformDirection(siDir);

    // Camera space uses y-up; screen uses y-down — flip y.
    lrDir.y = -lrDir.y;
    apDir.y = -apDir.y;
    siDir.y = -siDir.y;

    // Scale to the desired pixel length.
    lrDir.mul(axisLength);
    apDir.mul(axisLength);
    siDir.mul(axisLength);

    Point2D topLeft = infoLayer.getPosition(LayerAnnotation.Position.TopLeft);
    Point origin =
        AxisDirection.computeOrigin(axisLength, new Vector3d[] {lrDir, apDir, siDir}, topLeft);

    Stroke savedStroke = g2d.getStroke();
    g2d.setStroke(new BasicStroke(2));

    g2d.setColor(PatientOrientation.blue); // R/L
    AxisDirection.drawAxisLine(g2d, lrDir, origin);

    g2d.setColor(PatientOrientation.red); // A/P
    AxisDirection.drawAxisLine(g2d, apDir, origin);

    g2d.setColor(PatientOrientation.green); // S/I
    AxisDirection.drawAxisLine(g2d, siDir, origin);

    g2d.setStroke(savedStroke);
  }

  @Override
  public void setProgressBar(JProgressBar bar) {
    this.progressBar = bar;
  }

  @Override
  public JProgressBar getProgressBar() {
    return progressBar;
  }

  public void initShaders(Gl gl) {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    Color lightColor = preferences.getColorProperty(RenderingLayer.P_LIGHT_COLOR, Color.WHITE);
    Vector3f lColor =
        new Vector3f(
            lightColor.getRed() / 255f, lightColor.getGreen() / 255f, lightColor.getBlue() / 255f);
    Color bckColor = preferences.getColorProperty(RenderingLayer.P_BCK_COLOR, Color.GRAY);
    Vector3f bColor =
        new Vector3f(
            bckColor.getRed() / 255f, bckColor.getGreen() / 255f, bckColor.getBlue() / 255f);
    gl.glClearColor(bColor.x, bColor.y, bColor.z, 1);
    program.init(gl);
    program.allocateUniform(
        gl,
        "viewMatrix",
        (g, loc) ->
            g.glUniformMatrix4fv(loc, false, camera.getViewMatrix().invert().get(new float[16])));
    program.allocateUniform(
        gl,
        "projectionMatrix",
        (g, loc) ->
            g.glUniformMatrix4fv(
                loc, false, camera.getProjectionMatrix().invert().get(new float[16])));
    program.allocateUniform(
        gl,
        "depthSampleNumber",
        (g, loc) -> g.glUniform1i(loc, renderingLayer.getDepthSampleNumber()));
    program.allocateUniform(
        gl,
        "lutShape",
        (g, loc) -> g.glUniform1ui(loc, isSegMode() ? 0 : renderingLayer.getLutShapeId()));

    program.allocateUniform(
        gl,
        "backgroundColor",
        (g, loc) -> g.glUniform3fv(loc, new float[] {bColor.x, bColor.y, bColor.z}));

    for (int i = 0; i < 4; ++i) {
      int val = i;
      program.allocateUniform(
          gl,
          String.format("lights[%d].position", val), // NON-NLS
          (g, loc) -> {
            Vector4d origin = camera.getLightOrigin();
            g.glUniform4fv(
                loc,
                new float[] {
                  (float) origin.x, (float) origin.y, (float) origin.z, (float) origin.w
                });
          });
      program.allocateUniform(
          gl,
          String.format("lights[%d].specularPower", val), // NON-NLS
          (g, loc) -> g.glUniform1f(loc, renderingLayer.getShadingOptions().getSpecularPower()));
      program.allocateUniform(
          gl,
          String.format("lights[%d].enabled", val), // NON-NLS
          (g, loc) -> g.glUniform1i(loc, val < 1 ? 1 : 0));
    }
    program.allocateUniform(
        gl,
        "lightColor",
        (g, loc) -> g.glUniform3fv(loc, new float[] {lColor.x, lColor.y, lColor.z}));
    program.allocateUniform(
        gl,
        "shading",
        (g, loc) ->
            g.glUniform1i(loc, renderingLayer.isShading() || isPathTracingPreview() ? 1 : 0));
    allocateCinematicUniforms(gl);
    allocatePathTracingUniforms(gl);
    program.allocateUniform(
        gl,
        "texelSize",
        (g, loc) -> {
          DicomVolTexture tex = volTexture;
          Vector3f texel =
              tex != null
                  ? tex.getNormalizedTexelSize().get(new Vector3f())
                  : new Vector3f(1f, 1f, 1f);
          g.glUniform3fv(loc, new float[] {texel.x, texel.y, texel.z});
        });

    program.allocateUniform(
        gl, "renderingType", (g, loc) -> g.glUniform1ui(loc, effectiveRenderingType().getId()));
    program.allocateUniform(
        gl,
        "mipType",
        (g, loc) -> g.glUniform1ui(loc, renderingLayer.getRenderingType().getMipTypeId()));
    program.allocateUniform(gl, "volTexture", (g, loc) -> g.glUniform1i(loc, 0));
    program.allocateUniform(gl, "colorMap", (g, loc) -> g.glUniform1i(loc, 1));
    program.allocateUniform(
        gl,
        "textureDataType",
        (g, loc) -> g.glUniform1ui(loc, TextureData.getDataType(getPixelFormat())));

    program.allocateUniform(
        gl, "opacityFactor", (g, loc) -> g.glUniform1f(loc, (float) renderingLayer.getOpacity()));

    program.allocateUniform(
        gl,
        "inputLevelMin",
        (g, loc) -> {
          DicomVolTexture tex = volTexture;
          g.glUniform1f(loc, (tex == null || isSegMode()) ? 0 : (float) tex.getLevelMin());
        });
    program.allocateUniform(
        gl,
        "inputLevelMax",
        (g, loc) -> {
          DicomVolTexture tex = volTexture;
          g.glUniform1f(
              loc,
              isSegMode()
                  ? renderedPreset.getWidth()
                  : (tex != null ? (float) tex.getLevelMax() : 1f));
        });
    program.allocateUniform(gl, "outputLevelMin", (g, loc) -> g.glUniform1f(loc, 0));
    program.allocateUniform(
        gl, "outputLevelMax", (g, loc) -> g.glUniform1f(loc, renderedPreset.getWidth()));
    program.allocateUniform(
        gl, "visibleMin", (g, loc) -> g.glUniform1f(loc, renderedPreset.getVisibleRange()[0]));
    program.allocateUniform(
        gl, "visibleMax", (g, loc) -> g.glUniform1f(loc, renderedPreset.getVisibleRange()[1]));
    program.allocateUniform(
        gl,
        "gradientOpacityEnabled",
        (g, loc) -> g.glUniform1i(loc, renderedPreset.getGradientOpacityTable() != null ? 1 : 0));
    program.allocateUniform(
        gl,
        "gradientOpacity",
        (g, loc) -> {
          float[] table = renderedPreset.getGradientOpacityTable();
          if (table != null) {
            g.glUniform1fv(loc, table);
          }
        });
    program.allocateUniform(
        gl,
        "windowWidth",
        (g, loc) ->
            g.glUniform1f(
                loc,
                isSegMode()
                    ? renderedPreset.getColorMax() - renderedPreset.getColorMin()
                    : renderingLayer.getWindowWidth()));
    program.allocateUniform(
        gl,
        "windowCenter",
        (g, loc) ->
            g.glUniform1f(
                loc,
                isSegMode()
                    ? (renderedPreset.getColorMin() + renderedPreset.getColorMax()) / 2f
                    : renderingLayer.getWindowCenter()));

    if (!useComputeShader) {
      // The FBO shader uses voxelUniforms410.glsl which has no default for ditherRay
      // (default uniform initializers require GLSL 4.2+).
      program.allocateUniform(gl, "ditherRay", (g, loc) -> g.glUniform1i(loc, 1));
      // Explicitly bind all three samplers to their texture units.
      // layout(binding=N) on samplers requires GLSL 4.2 and is not available in 4.1,
      // so we must set the units from Java.
      program.allocateUniform(gl, "volTexture", (g, loc) -> g.glUniform1i(loc, 0));
      program.allocateUniform(gl, "colorMap", (g, loc) -> g.glUniform1i(loc, 1));
      program.allocateUniform(gl, "lightingMap", (g, loc) -> g.glUniform1i(loc, 2));
      // The crosshair overlay derives the viewport pixel resolution from screen-space
      // derivatives of quadCoordinates in volumeFbo.frag, which is robust to the macOS GLJPanel
      // quirk that lets gl_FragCoord range over physical pixels even when the FBO color
      // attachment is sized at logical resolution — so no viewportSize uniform is needed here.
      // Only the size of the pixel-metric overlays has to be corrected when the adaptive pass
      // runs below the screen resolution.
      program.allocateUniform(
          gl, "overlayScale", (g, loc) -> g.glUniform1f(loc, getRenderScaleX()));
    }

    texture.init(gl);
    if (volTexture != null) {
      volTexture.init(gl);
    }
    if (renderedPreset != null) {
      renderedPreset.init(gl, renderingLayer.isInvertLut());
    }

    // MPR crosshair uniforms — position in normalized [0,1]³ volume-texture space
    program.allocateUniform(
        gl,
        "crosshairPos", // NON-NLS
        (g, loc) -> {
          Vector3d pos = mprCrossHairPosition;
          if (pos != null) {
            g.glUniform3f(loc, (float) pos.x, (float) pos.y, (float) pos.z);
          } else {
            g.glUniform3f(loc, 0.5f, 0.5f, 0.5f);
          }
        });
    program.allocateUniform(
        gl,
        "crosshairRot", // NON-NLS
        (g, loc) -> {
          Quaterniond rot = mprCrossHairRotation;
          Matrix3d m = (rot != null) ? new Matrix3d().set(rot) : new Matrix3d().identity();
          float[] f = {
            (float) m.m00, (float) m.m10, (float) m.m20,
            (float) m.m01, (float) m.m11, (float) m.m21,
            (float) m.m02, (float) m.m12, (float) m.m22
          };
          g.glUniformMatrix3fv(loc, false, f);
        });
    program.allocateUniform(
        gl,
        "crosshairVisible", // NON-NLS
        (g, loc) -> g.glUniform1i(loc, mprCrossHairPosition != null ? 1 : 0));
    program.allocateUniform(
        gl,
        "crosshairCutMode", // NON-NLS
        (g, loc) -> {
          CrosshairCutMode mode = mprCrossHairCutMode;
          g.glUniform1i(loc, mode != null ? mode.getId() : 0);
        });

    // ---- Segmentation overlay uniforms ----
    program.allocateUniform(
        gl,
        "segOverlayEnabled", // NON-NLS
        (g, loc) -> g.glUniform1i(loc, isSegTextureActive() ? 1 : 0));
    program.allocateUniform(
        gl, "segOnly", (g, loc) -> g.glUniform1i(loc, isSegOnly() ? 1 : 0)); // NON-NLS
    program.allocateUniform(
        gl, "segMaskMode", (g, loc) -> g.glUniform1i(loc, getSegMaskMode())); // NON-NLS
    program.allocateUniform(
        gl,
        "segSegmentCount", // NON-NLS
        (g, loc) -> {
          SegVolumeTexture svt = segVolumeTexture;
          g.glUniform1i(
              loc, svt != null && svt.isReady() ? svt.getSegVolume().getSegmentCount() : 0);
        });
    program.allocateUniform(
        gl, "segTexture", (g, loc) -> g.glUniform1i(loc, SegVolumeTexture.SEG_TEXTURE_UNIT));
    program.allocateUniform(
        gl, "segColorMap", (g, loc) -> g.glUniform1i(loc, SegVolumeTexture.SEG_COLOR_UNIT));

    quadProgram.init(gl);
    // Blit uniforms: the compute path writes its image to unit 0 and always fills it entirely,
    // the FBO path blits from unit 3 and may have ray-cast only a sub-rectangle.
    quadProgram.allocateUniform(
        gl,
        "compute",
        (g, loc) ->
            g.glUniform1i(loc, useComputeShader ? 0 : FboRenderTexture.OUTPUT_TEXTURE_UNIT));
    quadProgram.allocateUniform(
        gl, "texScale", (g, loc) -> g.glUniform2f(loc, getRenderScaleX(), getRenderScaleY()));

    denoiseProgram.init(gl);
    denoiseProgram.shareUniforms(
        gl,
        program,
        "viewMatrix",
        "projectionMatrix",
        "texelSize",
        "overlayScale",
        "crosshairPos",
        "crosshairRot",
        "crosshairVisible",
        "crosshairCutMode",
        "exposure",
        "frameIndex",
        "historyMap",
        "featureMap",
        "momentMap");

    vertexBuffer = gl.glGenBuffers(1)[0];
    gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vertexBuffer);
    try (Arena arena = Arena.ofConfined()) {
      gl.glBufferData(
          Gl.GL_ARRAY_BUFFER,
          (long) vertexBufferData.length * Float.BYTES,
          arena.allocateFrom(ValueLayout.JAVA_FLOAT, vertexBufferData),
          Gl.GL_STATIC_DRAW);
    }
    // The driver links the tracer on its own threads while the view shows other renderings, so
    // choosing path tracing does not wait the seconds the first link of this shader takes.
    if (gl.isParallelShaderCompile()) {
      tracerProgram.startLink(gl);
    }
  }

  private boolean isSegMode() {
    return volumePreset != null && "Segmentation".equals(volumePreset.getName()); // NON-NLS
  }

  private PixelFormat getPixelFormat() {
    DicomVolTexture tex = volTexture;
    if (tex == null) {
      return PixelFormat.UNSIGNED_SHORT;
    }
    PixelFormat format = tex.getPixelFormat();
    if (isSegMode()) {
      if (format == PixelFormat.SIGNED_SHORT) {
        return PixelFormat.UNSIGNED_SHORT;
      }
    }
    return format;
  }

  private void allocateCinematicUniforms(Gl gl) {
    ShadingOptions options = renderingLayer.getShadingOptions();
    program.allocateUniform(
        gl,
        "cinematic",
        (g, loc) ->
            g.glUniform1i(loc, renderingLayer.isCinematic() || isPathTracingPreview() ? 1 : 0));
    program.allocateUniform(
        gl,
        "keyLightDir",
        (g, loc) -> {
          Vector3f dir = options.getKeyLightDirection();
          g.glUniform3fv(loc, new float[] {dir.x, dir.y, dir.z});
        });
    program.allocateUniform(
        gl, "shadowStrength", (g, loc) -> g.glUniform1f(loc, options.getShadowStrength()));
    program.allocateUniform(
        gl, "aoStrength", (g, loc) -> g.glUniform1f(loc, options.getAoStrength()));
    program.allocateUniform(gl, "exposure", (g, loc) -> g.glUniform1f(loc, options.getExposure()));
    program.allocateUniform(
        gl, "shadowSteps", (g, loc) -> g.glUniform1i(loc, getCinematicQuality().getShadowSteps()));
    program.allocateUniform(
        gl, "aoSteps", (g, loc) -> g.glUniform1i(loc, getCinematicQuality().getAoSteps()));
    program.allocateUniform(
        gl, "cinematicScale", (g, loc) -> g.glUniform1f(loc, (float) getInteractiveQualityRate()));
    program.allocateUniform(
        gl, "envMap", (g, loc) -> g.glUniform1i(loc, EnvironmentTexture.TEXTURE_UNIT));
    program.allocateUniform(
        gl, "envEnabled", (g, loc) -> g.glUniform1i(loc, environmentTexture() != null ? 1 : 0));
    program.allocateUniform(
        gl, "envStrength", (g, loc) -> g.glUniform1f(loc, options.getEnvironmentStrength()));
    program.allocateUniform(
        gl, "envMaxLod", (g, loc) -> g.glUniform1f(loc, EnvironmentTexture.maxLod()));
    program.allocateUniform(
        gl,
        "envSh",
        (g, loc) -> {
          EnvironmentTexture env = environmentTexture();
          if (env != null) {
            g.glUniform3fv(loc, env.getIrradianceSh());
          }
        });
  }

  private void allocatePathTracingUniforms(Gl gl) {
    program.allocateUniform(
        gl, "historyMap", (g, loc) -> g.glUniform1i(loc, AccumulationBuffers.HISTORY_UNIT));
    program.allocateUniform(
        gl, "featureMap", (g, loc) -> g.glUniform1i(loc, AccumulationBuffers.FEATURE_UNIT));
    program.allocateUniform(
        gl, "momentMap", (g, loc) -> g.glUniform1i(loc, AccumulationBuffers.MOMENT_UNIT));
    program.allocateUniform(gl, "frameIndex", (g, loc) -> g.glUniform1i(loc, frameIndex));
    program.allocateUniform(gl, "ptSamples", (g, loc) -> g.glUniform1i(loc, tracedSamples));
    program.allocateUniform(
        gl, "ptMaxBounces", (g, loc) -> g.glUniform1i(loc, getCinematicQuality().getBounces()));
    program.allocateUniform(
        gl, "majorantMap", (g, loc) -> g.glUniform1i(loc, MajorantMap.TEXTURE_UNIT));
    program.allocateUniform(
        gl, "ptGridEnabled", (g, loc) -> g.glUniform1i(loc, majorantGridReady ? 1 : 0));
    program.allocateUniform(
        gl,
        "ptGridSize",
        (g, loc) -> {
          MajorantGrid grid = majorantGridReady ? volTexture.getMajorantGrid() : null;
          if (grid != null) {
            g.glUniform3i(loc, grid.getWidth(), grid.getHeight(), grid.getDepth());
          } else {
            g.glUniform3i(loc, 1, 1, 1);
          }
        });
    program.allocateUniform(
        gl, "ptMaxAlpha", (g, loc) -> g.glUniform1f(loc, renderedPreset.getMaxAlpha()));
  }

  /** Path tracing is asked for, but the camera is moving: the cinematic composite stands in. */
  private boolean isPathTracingPreview() {
    return renderingLayer.getRenderingType() == RenderingType.PATH_TRACING
        && (camera.isAdjusting() || tracerLinking);
  }

  private boolean isPathTracingFrame() {
    return renderingLayer.getRenderingType() == RenderingType.PATH_TRACING
        && !camera.isAdjusting()
        && !tracerLinking;
  }

  // Polls the tracer link; until it completes, the frame is the composite preview.
  private void checkTracerLink(Gl gl) {
    if (renderingLayer.getRenderingType() != RenderingType.PATH_TRACING || camera.isAdjusting()) {
      return;
    }
    tracerLinking = !tracerProgram.isReady(gl);
    if (tracerLinking) {
      tracerLinkPoll.restart();
    }
  }

  /** Whether the volume and its segmentation overlay have finished loading. */
  public boolean isLoadFinished() {
    return volTexture != null && volTexture.isLoadFinished() && !segBuildRunning;
  }

  /** Whether the view renders a progressive path-traced image. */
  public boolean isPathTracing() {
    return renderingLayer.getRenderingType() == RenderingType.PATH_TRACING;
  }

  /** Samples averaged into the current path-traced image, or 0 when the scene changed since. */
  public int getAccumulatedSamples() {
    return isPathTracingFrame() && sceneFingerprint == computeSceneFingerprint() ? frameIndex : 0;
  }

  public void setCapturing(boolean capturing) {
    this.capturing = capturing;
  }

  @Override
  public List<LayerItem> getCaptureItems() {
    return DisplayTool.ANNOTATION_ITEMS;
  }

  @Override
  public Runnable beginCapture(DisplayProfile profile) {
    boolean wasCapturing = capturing;
    capturing = true;
    Runnable restoreLayer = () -> {};
    if (profile != null && infoLayer instanceof AbstractInfoLayer<?> layer) {
      Map<LayerItem, Boolean> items = new EnumMap<>(LayerItem.class);
      DisplayTool.ANNOTATION_ITEMS.forEach(
          item -> items.put(item, profile.annotationItems().contains(item)));
      restoreLayer = layer.overrideForCapture(profile.annotations(), items);
    }
    Runnable restore = restoreLayer;
    return () -> {
      restore.run();
      capturing = wasCapturing;
    };
  }

  private RenderingType effectiveRenderingType() {
    return isPathTracingPreview() ? RenderingType.COMPOSITE : renderingLayer.getRenderingType();
  }

  /**
   * Everything the average depends on. A frame whose fingerprint matches the previous one refines
   * the average; any other frame restarts it, so a repaint caused by a popup or an expose keeps the
   * progress while a camera move, a preset, a cut or a segmentation change starts over.
   */
  private long computeSceneFingerprint() {
    return Objects.hash(
        camera.getViewMatrix(),
        camera.getProjectionMatrix(),
        getSurfaceWidth(),
        getSurfaceHeight(),
        sceneVersion,
        mprCrossHairPosition,
        mprCrossHairRotation,
        mprCrossHairCutMode,
        getSegType(),
        System.identityHashCode(segVolumeTexture),
        System.identityHashCode(renderedPreset));
  }

  // Binds the history and the write target of the average, restarting it when the scene changed.
  private void beginPathTracingFrame(Gl gl) {
    long fingerprint = computeSceneFingerprint();
    if (fingerprint != sceneFingerprint) {
      sceneFingerprint = fingerprint;
      frameIndex = 0;
    }
    tracedSamples = Math.max(1, Math.min(samplesPerFrame, PATH_TRACING_FRAMES - frameIndex));
    MajorantGrid grid = volTexture.getMajorantGrid();
    majorantGridReady = grid.isComplete() && renderedPreset != null && isMajorantGridEnabled();
    if (!tracerReady) {
      tracerProgram.shareAllUniforms(gl, program);
      tracerReady = true;
    }
    accumulation.prepare(gl, getSurfaceWidth(), getSurfaceHeight());
    setAccumulationTargets(
        accumulation.getWriteId(),
        accumulation.getFeatureWriteId(),
        accumulation.getMomentWriteId());
  }

  /**
   * Binds the baked bounds of the tracer on their unit, baking them first when the preset or the
   * window changed since; a single placeholder block stands in while the volume is loading. The
   * bake runs its own program, so the active one is restored afterwards. The vertex array of the
   * quad must be set up.
   */
  private void bindMajorantMap(Gl gl, Program active) {
    if (!majorantGridReady) {
      majorantMap.bindPlaceholder(gl);
      return;
    }
    MajorantGrid grid = volTexture.getMajorantGrid();
    long fingerprint =
        Objects.hash(
            System.identityHashCode(renderedPreset),
            renderingLayer.getWindowWidth(),
            renderingLayer.getWindowCenter(),
            renderingLayer.getLutShapeId(),
            isSegMode());
    if (!majorantMap.isBaked(grid, fingerprint)) {
      grid.render(gl);
      majorantMap.bake(gl, program, grid, fingerprint, getSurfaceWidth(), getSurfaceHeight());
      active.use(gl);
    }
    majorantMap.bind(gl);
  }

  /**
   * Makes the frame just traced the history, filters it into the display image with the denoise
   * pass, then schedules the next refinement until the frame budget is spent. On the FBO path the
   * vertex array must still be set up.
   */
  private void endPathTracingFrame(Gl gl) {
    accumulation.swap();
    int before = frameIndex;
    frameIndex += tracedSamples;
    accumulation.bindHistory(gl);
    if (profiler.isEnabled() && ConvergenceReport.crosses(before, frameIndex)) {
      ConvergenceReport.log(
          gl,
          accumulation.getHistoryId(),
          accumulation.getMomentHistoryId(),
          getSurfaceWidth(),
          getSurfaceHeight(),
          frameIndex,
          profiler.getName());
    }
    denoiseProgram.use(gl);
    denoiseProgram.setUniforms(gl);
    if (texture instanceof ComputeTexture compute) {
      compute.dispatch(gl);
      gl.glMemoryBarrier(Gl.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
    } else if (texture instanceof FboRenderTexture fbo) {
      fbo.renderDenoisePass(gl);
    }
    if (frameIndex < PATH_TRACING_FRAMES) {
      accumulationTimer.restart();
    }
  }

  private void setAccumulationTargets(
      int colorTextureId, int featureTextureId, int momentTextureId) {
    if (texture instanceof ComputeTexture compute) {
      compute.setAccumulationTargets(colorTextureId, featureTextureId, momentTextureId);
    } else if (texture instanceof FboRenderTexture fbo) {
      fbo.setAccumulationTargets(colorTextureId, featureTextureId, momentTextureId);
    }
  }

  /** Texture of the selected environment, baked and cached on first request; null for NONE. */
  private EnvironmentTexture environmentTexture() {
    EnvironmentMap map = renderingLayer.getShadingOptions().getEnvironment();
    boolean lit = renderingLayer.isCinematic() && renderingLayer.isShading();
    if (!lit && renderingLayer.getRenderingType() != RenderingType.PATH_TRACING) {
      return null;
    }
    EnvironmentMap.Baked baked = map.bake();
    return baked == null
        ? null
        : environmentTextures.computeIfAbsent(map, m -> new EnvironmentTexture(baked));
  }

  // The sampler must always see a complete texture on its unit, so the environment is bound even
  // when unused: the first available one stands in for NONE.
  private void bindEnvironmentTexture(Gl gl) {
    EnvironmentTexture env = environmentTexture();
    if (env == null) {
      env =
          environmentTextures.computeIfAbsent(
              ShadingOptions.DEFAULT_ENVIRONMENT, m -> new EnvironmentTexture(m.bake()));
    }
    env.render(gl);
  }

  /** The grid is on unless the property says otherwise, in the JVM or the local persistence. */
  private static boolean isMajorantGridEnabled() {
    boolean fallback = !"false".equals(System.getProperty(RenderingLayer.P_MAJORANT_GRID));
    return GuiUtils.getUICore()
        .getLocalPersistence()
        .getBooleanProperty(RenderingLayer.P_MAJORANT_GRID, fallback);
  }

  private static CinematicQuality getCinematicQuality() {
    return CinematicQuality.fromOrdinal(
        GuiUtils.getUICore()
            .getLocalPersistence()
            .getIntProperty(
                RenderingLayer.P_CINEMATIC_QUALITY, CinematicQuality.DEFAULT.ordinal()));
  }

  /** Quality factor of the current frame: the dynamic-quality rate while adjusting, 1 otherwise. */
  private double getInteractiveQualityRate() {
    if (!camera.isAdjusting()) {
      return 1.0;
    }
    return GuiUtils.getUICore()
            .getLocalPersistence()
            .getIntProperty(
                RenderingLayer.P_DYNAMIC_QUALITY, RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE)
        / 100.0;
  }

  /**
   * Fraction of the render target that this frame is ray-cast into — below 1 only on the adaptive
   * FBO path while the camera is being dragged. The compute path always fills its image.
   */
  private float getRenderScaleX() {
    return texture instanceof FboRenderTexture fbo ? fbo.getRenderScaleX() : 1f;
  }

  private float getRenderScaleY() {
    return texture instanceof FboRenderTexture fbo ? fbo.getRenderScaleY() : 1f;
  }

  /** Resolution the volume is actually ray-cast at this frame, for the profiling report. */
  private int getPassWidth() {
    return Math.round(getRenderScaleX() * texture.getWidth());
  }

  private int getPassHeight() {
    return Math.round(getRenderScaleY() * texture.getHeight());
  }

  /**
   * Binds the segmentation textures on units 4 and 5, falling back to 1×1×1 placeholders when no
   * segmentation is loaded. Drivers validate every sampler of the active program, even those the
   * shader never reaches, so leaving the {@code usampler3D} unit empty makes strict implementations
   * (macOS) report an incomplete texture on each draw.
   */
  private void bindSegTextures(Gl gl) {
    SegVolumeTexture svt = segVolumeTexture;
    if (svt != null && svt.isReady()) {
      svt.bind(gl);
      return;
    }
    if (segPlaceholderTextureId <= 0) {
      int[] names = gl.glGenTextures(2);
      segPlaceholderTextureId = names[0];
      segPlaceholderColorId = names[1];

      try (Arena arena = Arena.ofConfined()) {
        gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_TEXTURE_UNIT);
        gl.glBindTexture(Gl.GL_TEXTURE_3D, segPlaceholderTextureId);
        gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
        gl.glTexParameteri(Gl.GL_TEXTURE_3D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
        gl.glTexImage3D(
            Gl.GL_TEXTURE_3D,
            0,
            Gl.GL_R8UI,
            1,
            1,
            1,
            0,
            Gl.GL_RED_INTEGER,
            Gl.GL_UNSIGNED_BYTE,
            arena.allocate(1));

        gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_COLOR_UNIT);
        gl.glBindTexture(Gl.GL_TEXTURE_2D, segPlaceholderColorId);
        gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MIN_FILTER, Gl.GL_NEAREST);
        gl.glTexParameteri(Gl.GL_TEXTURE_2D, Gl.GL_TEXTURE_MAG_FILTER, Gl.GL_NEAREST);
        gl.glTexImage2D(
            Gl.GL_TEXTURE_2D,
            0,
            Gl.GL_RGBA8,
            1,
            1,
            0,
            Gl.GL_RGBA,
            Gl.GL_UNSIGNED_BYTE,
            arena.allocate(4));
      }
    } else {
      gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_TEXTURE_UNIT);
      gl.glBindTexture(Gl.GL_TEXTURE_3D, segPlaceholderTextureId);
      gl.glActiveTexture(Gl.GL_TEXTURE0 + SegVolumeTexture.SEG_COLOR_UNIT);
      gl.glBindTexture(Gl.GL_TEXTURE_2D, segPlaceholderColorId);
    }
    gl.glActiveTexture(Gl.GL_TEXTURE0);
  }

  // A replaced preview preset belongs to this view only: its textures go at the next frame.
  private void retire(Preset old, Preset replacement) {
    if (old != null && old != replacement && old.isPreview()) {
      retiredPresets.add(old);
    }
  }

  private void destroyRetiredPresets(Gl gl) {
    Preset retired;
    while ((retired = retiredPresets.poll()) != null) {
      retired.destroy(gl);
    }
  }

  private void render(Gl gl) {
    render(gl, null);
  }

  /**
   * Draws one frame into {@code target}. The ray-cast pass of the FBO path binds a framebuffer of
   * its own and unbinds it when it is done, so the target is bound again before the blit.
   */
  private void render(Gl gl, RenderTarget target) {
    long start = profiler.start();
    destroyRetiredPresets(gl);
    gl.glClear(Gl.GL_COLOR_BUFFER_BIT | Gl.GL_DEPTH_BUFFER_BIT);
    if (volTexture != null && volTexture.isReadyForDisplay()) {
      int sampleCount = renderingLayer.getQuality();
      if (camera.isAdjusting()) {
        sampleCount = Math.max(64, (int) Math.round(sampleCount * getInteractiveQualityRate()));
      }
      renderingLayer.setDepthSampleNumber(sampleCount);

      checkTracerLink(gl);
      boolean pathTracing = isPathTracingFrame();
      if (pathTracing) {
        beginPathTracingFrame(gl);
      } else {
        frameIndex = 0;
        tracedSamples = 0;
        majorantGridReady = false;
        accumulation.bindHistory(gl);
        setAccumulationTargets(0, 0, 0);
      }

      Program active = pathTracing ? tracerProgram : program;
      active.use(gl);
      active.setUniforms(gl);
      volTexture.render(gl);
      if (volumePreset != null) {
        renderedPreset.render(gl, renderingLayer.isInvertLut());
      }
      // Bind segmentation overlay textures (units 4 and 5)
      bindSegTextures(gl);
      bindEnvironmentTexture(gl);

      if (useComputeShader) {
        // --- Compute shader path (OpenGL >= 4.3) ---
        if (pathTracing) {
          gl.glEnableVertexAttribArray(0);
          gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vertexBuffer);
          gl.glVertexAttribPointer(0, 2, Gl.GL_FLOAT, false, 0, 0);
          bindMajorantMap(gl, active);
          gl.glDisableVertexAttribArray(0);
        }
        texture.render(gl);
        if (pathTracing) {
          endPathTracingFrame(gl);
        }
        rebind(gl, target);
        blit(gl, Gl.GL_TEXTURE0);
      } else {
        // --- FBO fragment-shader fallback path (OpenGL 3.3+, e.g., macOS GL3) ---
        // Set up the vertex array so FboRenderTexture.render() can call glDrawArrays, then render
        // into the FBO (binds FBO, draws quad, unbinds FBO, restores viewport).
        gl.glEnableVertexAttribArray(0);
        gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vertexBuffer);
        gl.glVertexAttribPointer(0, 2, Gl.GL_FLOAT, false, 0, 0);
        if (pathTracing) {
          bindMajorantMap(gl, active);
        }
        texture.render(gl);
        if (pathTracing) {
          endPathTracingFrame(gl);
        }
        gl.glDisableVertexAttribArray(0);

        // The FBO output lives on unit 3 (FboRenderTexture.OUTPUT_TEXTURE_UNIT), so the quad
        // sampler never disturbs the 3D volume texture on unit 0, and texScale restricts the
        // sampling to the sub-rectangle that was ray-cast.
        rebind(gl, target);
        blit(gl, Gl.GL_TEXTURE0 + FboRenderTexture.OUTPUT_TEXTURE_UNIT);
      }
    }
    profiler.endGpu(gl, start);
  }

  private static void rebind(Gl gl, RenderTarget target) {
    if (target != null) {
      target.bind(gl);
    }
  }

  /** Draws the ray-cast output, read from {@code textureUnit}, over the background colour. */
  private void blit(Gl gl, int textureUnit) {
    quadProgram.use(gl);
    quadProgram.setUniforms(gl);

    gl.glEnable(Gl.GL_BLEND);
    gl.glBlendFunc(Gl.GL_SRC_ALPHA, Gl.GL_ONE_MINUS_SRC_ALPHA);

    gl.glEnableVertexAttribArray(0);
    gl.glBindBuffer(Gl.GL_ARRAY_BUFFER, vertexBuffer);
    gl.glVertexAttribPointer(0, 2, Gl.GL_FLOAT, false, 0, 0);
    gl.glActiveTexture(textureUnit);
    gl.glBindTexture(Gl.GL_TEXTURE_2D, texture.getId());
    gl.glDrawArrays(Gl.GL_TRIANGLES, 0, vertexBufferData.length / 2);
    gl.glDisableVertexAttribArray(0);
    gl.glDisable(Gl.GL_BLEND);
  }

  public void updateSegmentation() {
    sceneVersion++;
    Type currentType = getSegType();
    DicomVolTexture tex = volTexture;
    Volume<?, ?> volume = tex == null ? null : tex.getVolume();
    // Only the SEG files actually shown are resampled. Each one costs a full copy of the displayed
    // volume's grid (one byte per image voxel, hundreds of MB for a thin-slice CT), and a study may
    // carry dozens; building the hidden ones exhausts the heap for overlays nobody asked for.
    // Ticking one back on is caught by hasUnrenderedVisibleSeg() and rebuilds the texture.
    List<SpecialElementRegion> segList =
        currentType.requiresSegTexture() && volume != null
            ? tex.getSegmentations().stream().filter(SpecialElementRegion::isVisible).toList()
            : null;
    if (segList == null || segList.isEmpty()) {
      segBuildGeneration.incrementAndGet(); // invalidate any in-flight build
      setSegBuildRunning(false);
      destroySegTexture();
      display();
      return;
    }

    if (!segBuildRunning && isSegTextureUpToDate(segList)) {
      // The same SEG files are already uploaded: switching between the display modes only changes
      // shader uniforms, so render the existing texture instead of rebuilding it.
      display();
      return;
    }

    // The heavy work (mask decoding, splatting, resampling, GPU upload) runs asynchronously so
    // the EDT never freezes on large SEG objects; a message is shown in the view meanwhile.
    int generation = segBuildGeneration.incrementAndGet();
    setSegBuildRunning(true);
    final Volume<?, ?> imageVolume = volume;
    CompletableFuture.runAsync(() -> buildSegTexture(generation, segList, imageVolume))
        .exceptionally(
            e -> {
              LOGGER.error("Building segmentation texture", e);
              GuiExecutor.execute(
                  () -> {
                    if (generation == segBuildGeneration.get()) {
                      setSegBuildRunning(false);
                      destroySegTexture();
                      display();
                    }
                  });
              return null;
            });
  }

  /**
   * Background part of {@link #updateSegmentation()}: builds one image-aligned volume per SEG file,
   * combines them when needed, uploads the GPU texture through the shared GL context, then installs
   * the result on the EDT. Aborts silently when a newer build supersedes this one.
   */
  private void buildSegTexture(
      int generation, List<SpecialElementRegion> segList, Volume<?, ?> imageVolume) {
    List<RenderedSeg> rendered = new ArrayList<>();
    SegmentationVolume segVolume =
        buildCombinedSegVolume(generation, segList, imageVolume, rendered);

    // An interrupt cuts the build short, and a partial texture would be missing some SEG files.
    if (Thread.currentThread().isInterrupted() || generation != segBuildGeneration.get()) {
      return; // superseded by a newer request
    }
    if (segVolume == null) {
      GuiExecutor.execute(
          () -> {
            if (generation == segBuildGeneration.get()) {
              setSegBuildRunning(false);
              destroySegTexture();
              display();
            }
          });
      return;
    }

    SegVolumeTexture newSvt = new SegVolumeTexture(segVolume, new Vector3d(1.0, 1.0, 1.0));
    // Upload from this worker thread through the shared GL context
    newSvt.uploadVolumeDataAsync();

    GuiExecutor.execute(() -> installSegTexture(generation, newSvt, rendered));
  }

  /**
   * Resamples the SEG files onto the displayed volume's grid and combines them into one volume,
   * recording each file's segment-number offset in {@code rendered}. Returns {@code null} when no
   * file yielded anything usable.
   *
   * <p>Each resampled volume is a full copy of the image grid, so they are merged and freed as they
   * complete rather than collected first: the peak is the merge target plus the handful of builds
   * in flight, not one volume per SEG file. A lone file needs no merge and keeps its cached volume,
   * which the MPR overlays can then reuse.
   */
  private SegmentationVolume buildCombinedSegVolume(
      int generation,
      List<SpecialElementRegion> segList,
      Volume<?, ?> imageVolume,
      List<RenderedSeg> rendered) {

    if (segList.size() == 1) {
      AlignedSeg single = buildAlignedSeg(generation, segList.getFirst(), imageVolume);
      if (single == null) {
        return null;
      }
      rendered.add(new RenderedSeg(single.seg(), 0));
      return single.volume();
    }

    // Offsets follow segList order rather than completion order, so a study always maps a given
    // file to the same segment numbers however the concurrent builds interleave. Files that end up
    // yielding nothing simply leave their range unused.
    Map<Integer, RegionAttributes> combined = new HashMap<>();
    List<Integer> indexes = new ArrayList<>(segList.size());
    int[] offsets = new int[segList.size()];
    int offset = 0;
    for (int i = 0; i < segList.size(); i++) {
      indexes.add(i);
      offsets[i] = offset;
      int maxSegNum = 0;
      for (Map.Entry<Integer, ? extends RegionAttributes> e :
          segList.get(i).getSegAttributes().entrySet()) {
        combined.put(offset + e.getKey(), e.getValue());
        maxSegNum = Math.max(maxSegNum, e.getKey());
      }
      offset += maxSegNum;
    }

    SegVolumeMerger merger = new SegVolumeMerger(combined);
    long start = System.nanoTime();
    // mapBounded returns the non-null results in input order, so rendered stays in segList order.
    rendered.addAll(
        SegBuildScheduler.mapBounded(
            indexes,
            i -> {
              AlignedSeg as = buildAlignedSeg(generation, segList.get(i), imageVolume);
              if (as == null) {
                return null;
              }
              try {
                merger.merge(as.volume(), offsets[i]);
              } finally {
                // Freed as soon as it is merged. MPR keeps a retain() on the volumes it displays,
                // so this releases only the copies this build owns.
                as.seg().disposeAlignedVolume(imageVolume);
              }
              return new RenderedSeg(as.seg(), offsets[i]);
            }));
    LOGGER.debug(
        "Merged {} segmentation volumes ({} stamped voxels) in {} ms",
        rendered.size(),
        merger.stamped(),
        (System.nanoTime() - start) / 1_000_000);
    return merger.merged();
  }

  /**
   * Accumulates image-aligned SEG volumes into a single volume as they are built. Segment numbers
   * of each file are shifted by that file's offset so files sharing segment numbers do not
   * overwrite each other.
   */
  private static final class SegVolumeMerger {

    private final Map<Integer, RegionAttributes> combined;
    private SegmentationVolume merged;
    private long stamped;

    SegVolumeMerger(Map<Integer, RegionAttributes> combined) {
      this.combined = combined;
    }

    /** The merge target is created from the first volume, which fixes the grid for the rest. */
    synchronized void merge(SegmentationVolume volume, int offset) {
      if (merged == null) {
        merged = volume.createCompatible(combined);
      }
      stamped += volume.mergeInto(merged, segNum -> segNum + offset);
    }

    synchronized SegmentationVolume merged() {
      return merged;
    }

    synchronized long stamped() {
      return stamped;
    }
  }

  /**
   * Builds (or reuses) the image-aligned volume of a single SEG file. Returns {@code null} when the
   * file yields nothing usable or when a newer segmentation request has superseded this build.
   */
  private AlignedSeg buildAlignedSeg(
      int generation, SpecialElementRegion seg, Volume<?, ?> imageVolume) {
    if (generation != segBuildGeneration.get() || !(seg instanceof SegSpecialElement segElement)) {
      return null;
    }
    DicomSeries segSeries = segElement.getMediaReader().getMediaSeries();
    if (segSeries == null) {
      return null;
    }
    // Reuse the SEG's per-image-volume cached resample when available so we do not redo the
    // expensive frame splatting if MPR (or another 3D view) already built it for the same volume.
    SegmentationVolume segVol =
        segElement.getOrBuildAlignedVolume(
            imageVolume, s -> SegVolumeBuilder.build(s, segSeries, imageVolume));
    if (segVol == null) {
      return null;
    }
    if (segVol.isEmpty()) {
      // Empty resample is useless to keep cached: drop it from the SEG so a future request can
      // retry (e.g. once the image volume finishes loading).
      segElement.disposeAlignedVolume(imageVolume);
      return null;
    }
    return new AlignedSeg(segElement, segVol);
  }

  /** EDT part of the asynchronous build: swaps the new texture in and refreshes the view. */
  private void installSegTexture(
      int generation, SegVolumeTexture newSvt, List<RenderedSeg> rendered) {
    if (generation != segBuildGeneration.get()) {
      // Superseded while uploading — discard the freshly-built texture.
      GlRuntime.submit(newSvt::destroy);
      return;
    }
    this.renderedSegs = rendered;
    // Apply the tree's visibility/opacity state to the colour LUT. This runs on the EDT (no
    // current GL context), so stash the LUT — SegVolumeTexture.bind() uploads it on the GL
    // thread at the next render. buildSegmentColorLUT() is pure CPU work.
    newSvt.setPendingColorLut(newSvt.getSegVolume().buildSegmentColorLUT(buildRegionOverrideMap()));

    SegVolumeTexture old = this.segVolumeTexture;
    this.segVolumeTexture = newSvt;
    if (old != null) {
      GlRuntime.submit(old::destroy);
      // Do NOT free the underlying SegmentationVolume here: it is now owned by the
      // SegSpecialElement's per-image-volume cache (see SegSpecialElement.alignedVolumes) and
      // may still be in use by the MPR overlay or by a re-upload of this very texture.
    }
    setSegBuildRunning(false);
    display();
  }

  private void setSegBuildRunning(boolean running) {
    this.segBuildRunning = running;
    repaint();
  }

  /**
   * {@code true} when the current texture already holds exactly the SEG files of {@code segList}.
   */
  private boolean isSegTextureUpToDate(List<SpecialElementRegion> segList) {
    SegVolumeTexture svt = segVolumeTexture;
    if (svt == null) {
      return false;
    }
    List<RenderedSeg> rendered = renderedSegs;
    if (rendered.size() != segList.size()) {
      return false;
    }
    for (int i = 0; i < rendered.size(); i++) {
      if (rendered.get(i).seg() != segList.get(i)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Refreshes only the segment colour LUT on the GPU using the current visibility / opacity state
   * from the UI tree ({@link Preset#getRegionMap()}). This is much cheaper than rebuilding the
   * entire segmentation texture and should be used when only segment visibility or opacity has
   * changed.
   */
  public void refreshSegColorLUT() {
    if (segBuildRunning) {
      // A texture build is in flight; it reads the tree state when it installs the texture.
      return;
    }
    SegVolumeTexture svt = segVolumeTexture;
    if (svt == null || hasUnrenderedVisibleSeg()) {
      // No texture yet, or the user enabled a SEG file that is not part of the current texture
      // (e.g. its volume could not be built when the texture was created): rebuild it fully.
      // updateSegmentation() is a no-op when no segmentation display mode is active.
      updateSegmentation();
      return;
    }
    // Build the colour LUT from the Preset region map (reflects the tree's checkbox state)
    Map<Integer, RegionAttributes> overrideAttrs = buildRegionOverrideMap();
    svt.setPendingColorLut(svt.getSegVolume().buildSegmentColorLUT(overrideAttrs));
    sceneVersion++;
    display();
  }

  /** Returns {@code true} when a visible SEG file is missing from the rendered texture. */
  private boolean hasUnrenderedVisibleSeg() {
    DicomVolTexture tex = volTexture;
    if (tex == null) {
      return false;
    }
    List<RenderedSeg> rendered = renderedSegs;
    for (SpecialElementRegion seg : tex.getSegmentations()) {
      if (seg.isVisible()
          && seg instanceof SegSpecialElement sse
          && rendered.stream().noneMatch(rs -> rs.seg() == sse)) {
        return true;
      }
    }
    return false;
  }

  /** A SEG file together with its volume resampled onto the displayed image volume's grid. */
  private record AlignedSeg(SegSpecialElement seg, SegmentationVolume volume) {}

  /** A SEG file contained in the current texture and its segment-number offset. */
  private record RenderedSeg(SegSpecialElement seg, int offset) {}

  /**
   * Builds a flat segment-number → RegionAttributes map suitable for {@link
   * SegmentationVolume#buildSegmentColorLUT(Map)}. Region-level state comes from the Preset's
   * region map (the tool tree's copies, keyed by {@code "<regionUID>::<groupKey>"}), shifted by the
   * per-file offset used when the rendered volume was merged. On top of that, the file-level
   * checkbox is authoritative: every region of an unchecked SEG file is forced invisible, even when
   * the tree copies are unavailable or stale.
   */
  private Map<Integer, RegionAttributes> buildRegionOverrideMap() {
    List<RenderedSeg> rendered = renderedSegs;
    Map<Integer, RegionAttributes> result = new HashMap<>();

    Map<String, List<SegRegion<?>>> regionMap = Preset.getRegionMap();
    if (regionMap != null && !regionMap.isEmpty()) {
      for (Map.Entry<String, List<SegRegion<?>>> entry : regionMap.entrySet()) {
        Integer offset = resolveSegOffset(rendered, entry.getKey());
        if (offset == null) {
          continue; // this segmentation is not part of the rendered texture
        }
        for (SegRegion<?> region : entry.getValue()) {
          result.put(region.getId() + offset, region);
        }
      }
    }

    for (RenderedSeg rs : rendered) {
      if (!rs.seg().isVisible()) {
        for (Map.Entry<Integer, ? extends RegionAttributes> e :
            rs.seg().getSegAttributes().entrySet()) {
          if (e.getValue() instanceof SegRegion<?> region) {
            SegRegion<?> hidden = region.copy();
            hidden.setVisible(false);
            result.put(e.getKey() + rs.offset(), hidden);
          }
        }
      }
    }
    return result;
  }

  /**
   * Resolves the segment-number offset of the segmentation owning the given Preset region-map key
   * ({@code "<regionUID>::<groupKey>"}), or {@code null} when that segmentation is not rendered.
   */
  private static Integer resolveSegOffset(List<RenderedSeg> rendered, String key) {
    if (rendered.isEmpty()) {
      return 0;
    }
    for (RenderedSeg rs : rendered) {
      if (key.startsWith(rs.seg().getRegionUID() + "::")) { // NON-NLS
        return rs.offset();
      }
    }
    return null;
  }

  /** Returns this view's segmentation display mode (never {@code null}). */
  public Type getSegType() {
    return segType;
  }

  /** Applies a new segmentation display mode to this view and rebuilds/destroys its texture. */
  public void setSegType(Type type) {
    if (type != null && type != segType) {
      this.segType = type;
      updateSegmentation();
    }
  }

  private boolean isSegOnly() {
    return getSegType() == Type.SEG_ONLY;
  }

  /**
   * Returns true when a segmentation texture is ready and the current mode renders it — overlay,
   * segmentation-only, or one of the voxel mask modes.
   */
  private boolean isSegTextureActive() {
    SegVolumeTexture svt = segVolumeTexture;
    return svt != null && svt.isReady() && segType.requiresSegTexture();
  }

  /**
   * Returns the mask mode passed to the shader: {@code 0} = none, {@code 1} = include (only the
   * real voxels inside visible segments are rendered), {@code 2} = exclude (those voxels are
   * removed from the rendering).
   */
  private int getSegMaskMode() {
    if (segType == Type.SEG_MASK_INCLUDE) {
      return 1;
    }
    return segType == Type.SEG_MASK_EXCLUDE ? 2 : 0;
  }

  private void destroySegTexture() {
    renderedSegs = List.of();
    SegVolumeTexture svt = segVolumeTexture;
    if (svt != null) {
      segVolumeTexture = null;
      // The underlying SegmentationVolume is owned by the SegSpecialElement's per-image-volume
      // cache; only the GL texture is released here.
      GlRuntime.submit(svt::destroy);
    }
  }

  public void setVolumePreset(Preset preset) {
    this.volumePreset = Objects.requireNonNull(preset);
    sceneVersion++;
    Preset rendered = preset.forVolume(volTexture);
    retire(this.renderedPreset, rendered);
    this.renderedPreset = rendered;
    rendered.setRequiredBuilding(true);

    PresetWindowLevel defaultPreset = getVolTexture().getDefaultPreset(rendered);
    changePresetWindowLevel(defaultPreset);

    renderingLayer.applyVolumePreset(rendered, false);
    eventManager
        .getAction(ActionVol.VOL_SHADING)
        .ifPresent(a -> a.setSelectedWithoutTriggerAction(rendered.isShade()));
    display();
  }

  protected void changePresetWindowLevel(PresetWindowLevel p) {
    setPresetWindowLevel(p, false);
    eventManager.getAction(ActionW.PRESET).ifPresent(a -> a.setSelectedItemWithoutTriggerAction(p));
    eventManager
        .getAction(ActionW.LUT_SHAPE)
        .ifPresent(a -> a.setSelectedItemWithoutTriggerAction(p.getLutShape()));

    eventManager.applyDefaultWindowLevel(this);
  }

  /** The selected preset, as listed in the menus. */
  public Preset getVolumePreset() {
    return volumePreset;
  }

  /** The preset the view renders: the selected one compiled for the loaded volume. */
  public Preset getRenderedPreset() {
    return renderedPreset;
  }

  @Override
  public void copyActionWState(HashMap<String, Object> actionsInView) {
    actionsInView.putAll(this.actionsInView);
  }

  @Override
  public ImageViewerEventManager<DicomImageElement> getEventManager() {
    return eventManager;
  }

  @Override
  public void updateSynchState() {
    SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
    ImageViewerPlugin<DicomImageElement> container = eventManager.getSelectedView2dContainer();
    boolean multiView = container != null && container.getImagePanels().size() > 1;

    // Hide the per-view sync button when nothing can be synced — either no SynchData attached or
    // the layout has a single view (no other view to mirror).
    if (synchData == null || !multiView) {
      if (synchButton != null) {
        synchButton.setVisible(false);
      }
      repaint();
      return;
    }
    if (synchButton == null) {
      synchButton = new SynchViewButton(this::showSyncPopup);
      synchButton.setPosition(
          GridBagConstraints.SOUTHEAST); // absolute side: a button drawn in the image corner
      getViewButtons().add(synchButton);
    }
    synchButton.setState(
        synchData.isSynchActivated() ? SynchData.SyncState.ON : SynchData.SyncState.OFF);
    synchButton.setVisible(true);
    repaint();
  }

  /**
   * Invoked when the user clicks the auto-sync overlay button: shows the per-view sync popup at the
   * click location. Same content as the right-click "Volume sync" submenu, presented as a
   * standalone popup with an additional auto-sync on/off toggle at the top.
   */
  private void showSyncPopup(java.awt.Component invoker, int x, int y) {
    SynchData sd = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
    if (sd == null) {
      return;
    }
    ImageViewerPlugin<DicomImageElement> container = eventManager.getSelectedView2dContainer();
    if (container == null) {
      return;
    }

    SynchOptionsCheckBoxGroup group = new SynchOptionsCheckBoxGroup(container.getSyncOptions());
    JPopupMenu popup = group.buildPopupMenu(sd.getActions(), this::onSyncOptionToggled);

    // Top auto-sync on/off toggle.
    JCheckBoxMenuItem syncToggle =
        new JCheckBoxMenuItem(
            org.weasis.core.Messages.getString("ViewerToolBar.synch_this_view"),
            sd.isSynchActivated());
    syncToggle.setToolTipText(
        org.weasis.core.Messages.getString("ViewerToolBar.synch_this_view_tooltip"));
    syncToggle.addActionListener(e -> toggleAutoSync(sd, syncToggle.isSelected()));
    popup.insert(syncToggle, 0);
    popup.insert(new JPopupMenu.Separator(), 1);

    popup.addSeparator();
    popup.add(buildApplyToAllItem(sd, container));

    popup.show(invoker, x, y);
  }

  private void toggleAutoSync(SynchData sd, boolean enabled) {
    // Per-view toggle: only mutate this view's state and mark non-original so a subsequent
    // refresh (updateAllListeners → getOrCreateSynchData) preserves it. Do NOT touch SYNCH_MODE
    // — that would trigger the master toggle and propagate to every other view.
    sd.setOriginal(false);
    sd.setAutoSyncState(enabled ? SynchData.SyncState.ON : SynchData.SyncState.OFF);
    refreshAllListeners();
  }

  private void refreshAllListeners() {
    ComboItemListener<SynchView> synchAction = eventManager.getAction(ActionW.SYNCH).orElse(null);
    SynchView selected =
        synchAction != null && synchAction.getSelectedItem() instanceof SynchView sv
            ? sv
            : SynchView.DEFAULT_STACK;
    eventManager.updateAllListeners(eventManager.getSelectedView2dContainer(), selected);
  }

  private void onSyncOptionToggled(String cmd, boolean selected) {
    SynchData sd = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
    if (sd != null) {
      sd.getActions().put(cmd, selected);
      repaint();
    }
  }

  private JMenuItem buildApplyToAllItem(
      SynchData source, ImageViewerPlugin<DicomImageElement> container) {
    JMenuItem applyToAll = new JMenuItem(org.weasis.core.Messages.common("apply_to_all_views"));
    applyToAll.addActionListener(e -> applySyncOptionsToOtherViews(source, container));
    return applyToAll;
  }

  /**
   * Copy this view's sync actions map to every other view in the container. All 3D views show the
   * same volume so no FrameOfReferenceUID filter is applied; views with sync deactivated are still
   * updated so the user gets a consistent map when they re-enable.
   */
  private void applySyncOptionsToOtherViews(
      SynchData source, ImageViewerPlugin<DicomImageElement> container) {
    if (source == null || container == null) {
      return;
    }
    Map<String, Boolean> snapshot = new HashMap<>(source.getActions());
    for (ViewCanvas<DicomImageElement> v : container.getImagePanels()) {
      if (v == this) {
        continue;
      }
      SynchData target = (SynchData) v.getActionValue(ActionW.SYNCH_LINK.cmd());
      if (target == null) {
        continue;
      }
      target.getActions().clear();
      target.getActions().putAll(snapshot);
      v.getJComponent().repaint();
    }
  }

  @Override
  public PixelInfo getPixelInfo(Point p) {
    return null;
  }

  @Override
  public Panner<DicomImageElement> getPanner() {
    return null;
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> series) {
    // Do nothing, use addSeries instead
  }

  @Override
  public void setSeries(MediaSeries<DicomImageElement> newSeries, DicomImageElement selectedMedia) {
    // Do nothing, use addSeries instead
  }

  @Override
  public LayerAnnotation getInfoLayer() {
    return infoLayer;
  }

  @Override
  public int getTileOffset() {
    return 0;
  }

  @Override
  public void setTileOffset(int tileOffset) {}

  @Override
  public final void center() {
    setCenter(0.0, 0.0);
  }

  @Override
  public final void setCenter(Double modelOffsetX, Double modelOffsetY) {
    // Only apply when the panel size is not zero.
    if (getWidth() != 0 && getHeight() != 0) {
      getViewModel().setModelOffset(modelOffsetX, modelOffsetY);
      //  if (viewType == ViewType.VOLUME3D) {
      resetPan();
      //   }
    }
  }

  @Override
  public void moveOrigin(PanPoint point) {
    if (point == null) {
      return;
    }
    PanPoint.State state = point.getState();
    if (PanPoint.State.DRAGSTART.equals(state)) {
      // Anchor the pan reference point so the first DRAGGING delta is computed against the
      // press position rather than this view's stale prevMousePos (which would shift the
      // image visibly when sync is on).
      camera.anchorMouse(point);
    } else if (PanPoint.State.DRAGGING.equals(state)) {
      camera.translate(point);
    }
  }

  @Override
  public Comparator<DicomImageElement> getCurrentSortComparator() {
    return null;
  }

  @Override
  public void setSelected(Boolean selected) {
    setBorder(selected ? focusBorder : viewBorder);
    // Remove the selection of graphics
    graphicManager.setSelectedGraphic(null);
    // Throws to the tool listener the current graphic selection.
    graphicManager.fireGraphicsSelectionChanged(getImageLayer());

    if (selected && getSeries() != null) {
      AuditLog.LOGGER.info("select:series nb:{}", getSeries().getSeriesNumber());
    }
  }

  @Override
  public Font getLayerFont() {
    Font font = FontItem.DEFAULT_SEMIBOLD.getFont();
    return DefaultView2d.getLayerFont(getFontMetrics(font), getWidth());
  }

  @Override
  public void setDrawingsVisibility(Boolean visible) {}

  @Override
  public Object getLensActionValue(String action) {
    return null;
  }

  @Override
  public void changeZoomInterpolation(Interpolation interpolation) {}

  @Override
  public OpManager getDisplayOpManager() {
    return null;
  }

  @Override
  public void disableMouseAndKeyListener() {
    ViewCanvas.super.disableMouseAndKeyListener(this);
  }

  @Override
  public void iniDefaultMouseListener() {
    // focus listener is always on
    this.addMouseListener(focusHandler);
    this.addMouseMotionListener(focusHandler);
    this.addMouseWheelListener(focusHandler);
  }

  @Override
  public void iniDefaultKeyListener() {
    this.addKeyListener(this);
  }

  @Override
  public int getPointerType() {
    return pointerType;
  }

  @Override
  public void setPointerType(int pointerType) {
    this.pointerType = pointerType;
  }

  @Override
  public void addPointerType(int i) {
    this.pointerType |= i;
  }

  @Override
  public void resetPointerType(int i) {
    this.pointerType &= ~i;
  }

  @Override
  public Point2D getHighlightedPosition() {
    return highlightedPosition;
  }

  @Override
  public List<Action> getExportActions() {
    if (volTexture == null) {
      return null;
    }
    ImageViewerPlugin<DicomImageElement> container = eventManager.getSelectedView2dContainer();
    RecorderSourceBuilder recorder = new RecorderSourceBuilder(container != null);
    return List.of(
        CaptureAction.SCREENSHOT.action(
            ActionW.EXPORT_VIEW.getTitle(), _ -> ScreenshotDialog.showDialog(this)),
        CaptureAction.ANIMATION.action(
            Messages.getString("rotation.export"), _ -> showAnimationExport()),
        CaptureAction.RECORD.action(
            recorder.title(),
            _ ->
                AnimationExportDialog.showDialog(
                    recorder,
                    this,
                    container,
                    DicomScSinkFactory.defaultSinks(volTexture.getSeries(), recorder.title()))));
  }

  private void showAnimationExport() {
    if (volTexture == null) {
      return;
    }
    RotationSourceBuilder builder = new RotationSourceBuilder(this);
    AnimationExportDialog.showDialog(
        builder,
        this,
        eventManager.getSelectedView2dContainer(),
        DicomScSinkFactory.defaultSinks(volTexture.getSeries(), builder.title()));
  }

  public MouseActionAdapter getMouseAdapter(String command) {
    if (command.equals(ActionW.CONTEXTMENU.cmd())) {
      return contextMenuHandler;
    } else if (command.equals(ActionW.WINLEVEL.cmd())) {
      return getAction(ActionW.LEVEL);
    }

    Optional<Feature<? extends ActionState>> actionKey = eventManager.getActionKey(command);
    if (actionKey.isEmpty()) {
      return null;
    }

    if (actionKey.get().isDrawingAction()) {
      return graphicMouseHandler;
    }
    Optional<? extends ActionState> actionState = eventManager.getAction(actionKey.get());
    if (actionState.isPresent() && actionState.get() instanceof MouseActionAdapter listener) {
      return listener;
    }
    return null;
  }

  @Override
  public void resetMouseAdapter() {
    ViewCanvas.super.resetMouseAdapter();

    // reset context menu that is a field of this instance
    contextMenuHandler.setButtonMaskEx(0);
    graphicMouseHandler.setButtonMaskEx(0);
  }

  @Override
  public void resetZoom() {
    zoom(0.0);
  }

  @Override
  public void resetPan() {
    camera.resetPan();
  }

  @Override
  public void reset() {
    ImageViewerPlugin<DicomImageElement> pane = eventManager.getSelectedView2dContainer();
    if (pane != null) {
      pane.resetMaximizedSelectedImagePane(this);
    }

    initActionWState();

    View c = Camera.getDefaultOrientation();
    camera.set(c.position(), c.rotation(), -getBestFitViewScale(c.position(), c.rotation()), false);
    renderingLayer.setEnableRepaint(false);
    renderingLayer.setQuality(getDefaultQuality());
    renderingLayer.setOpacity(1.0);
    renderingLayer.setInvertLut(false);
    setVolumePreset(volumePreset);
    renderingLayer.setEnableRepaint(true);
    renderingLayer.fireLayerChanged();
    eventManager.updateComponentsListener(this);
  }

  private final List<ViewButton> viewButtons = new java.util.ArrayList<>();

  @Override
  public List<ViewButton> getViewButtons() {
    return viewButtons;
  }

  @Override
  public void closeLens() {}

  @Override
  public void updateCanvas(boolean triggerViewModelChangeListeners) {}

  @Override
  public void updateGraphicSelectionListener(ImageViewerPlugin<DicomImageElement> viewerPlugin) {}

  @Override
  public boolean requiredTextAntialiasing() {
    return false;
  }

  @Override
  public GraphicMouseHandler<DicomImageElement> getGraphicMouseHandler() {
    return graphicMouseHandler;
  }

  @Override
  public JPopupMenu buildGraphicContextMenu(MouseEvent evt, List<Graphic> selected) {
    return null;
  }

  @Override
  public JPopupMenu buildContextMenu(MouseEvent evt) {
    JPopupMenu popupMenu = buildLeftMouseActionMenu();

    int count = popupMenu.getComponentCount();

    if (eventManager instanceof EventManager manager) {
      GuiUtils.addItemToMenu(popupMenu, manager.getLutMenu(null));
      GuiUtils.addItemToMenu(popupMenu, manager.getLutShapeMenu(null));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getViewTypeMenu(null));
      GuiUtils.addItemToMenu(popupMenu, manager.getShadingMenu(null));
      GuiUtils.addItemToMenu(popupMenu, manager.getCinematicMenu(null));
      GuiUtils.addItemToMenu(popupMenu, manager.getSProjectionMenu(null));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getMprCutMenu(null));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getZoomMenu(null));
      GuiUtils.addItemToMenu(popupMenu, manager.getOrientationMenu(null));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, buildSyncOptionsMenu());
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getResetMenu(null));
    }
    return popupMenu;
  }

  /**
   * Per-view sync options submenu shown in the right-click context menu. Reads/writes this view's
   * own {@link SynchData} (attached by {@code EventManager.updateAllListeners}); the Apply-to-all
   * entry then copies this view's actions map to every other view in the container.
   */
  private JMenu buildSyncOptionsMenu() {
    ImageViewerPlugin<DicomImageElement> container = eventManager.getSelectedView2dContainer();
    SynchData sd = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
    if (container == null || sd == null) {
      return null;
    }
    SynchOptionsCheckBoxGroup group = new SynchOptionsCheckBoxGroup(container.getSyncOptions());
    JMenu menu = new JMenu(org.weasis.core.Messages.getString("ActionW.synch"));
    group.addStayOpenItemsTo(menu, sd.getActions(), this::onSyncOptionToggled);
    menu.addSeparator();
    menu.add(buildApplyToAllItem(sd, container));
    return menu;
  }

  @Override
  public boolean hasValidContent() {
    return volTexture != null && volTexture.isReadyForDisplay();
  }

  @Override
  public void focusGained(FocusEvent e) {}

  @Override
  public void focusLost(FocusEvent e) {}

  @Override
  public void keyTyped(KeyEvent e) {}

  @Override
  public void keyPressed(KeyEvent e) {
    defaultKeyPressed(eventManager, e);
  }

  @Override
  public void keyReleased(KeyEvent e) {}

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    String propertyName = evt.getPropertyName();
    if (propertyName != null) {
      if (propertyName.equals(ActionW.SYNCH.cmd())) {
        propertyChange((SynchEvent) evt.getNewValue());
      }
    }
  }

  private void propertyChange(final SynchEvent synch) {
    {
      SynchData synchData = (SynchData) actionsInView.get(ActionW.SYNCH_LINK.cmd());
      SynchData issuerSyncData =
          synch.getView() != null
              ? (SynchData) synch.getView().getActionsInView().get(ActionW.SYNCH_LINK.cmd())
              : null;
      boolean isSource = synch.getView() == this;
      if (!isSource) {
        if (synchData != null && !synchData.isSynchActivated()) {
          return;
        }
        if (issuerSyncData != null && !issuerSyncData.isSynchActivated()) {
          return;
        }
      }
      // Progressive mode for VR
      boolean forceRepaint = camera.isAdjusting() != synch.isValueIsAdjusting();
      camera.setAdjusting(synch.isValueIsAdjusting());

      for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
        String command = entry.getKey();
        final Object val = entry.getValue();
        // The originator always applies its own action (otherwise unchecking e.g. W/L in the
        // popup would also break the slider locally). For target views, both the issuer's and
        // the receiver's sync option must be enabled for this action.
        if (!isSource
            && ((synchData != null && !synchData.isActionEnable(command))
                || (issuerSyncData != null && !issuerSyncData.isActionEnable(command)))) {
          continue;
        }
        if (command.equals(ActionW.WINDOW.cmd())) {
          renderingLayer.setWindowWidth(((Double) val).intValue(), forceRepaint);
        } else if (command.equals(ActionW.LEVEL.cmd())) {
          renderingLayer.setWindowCenter(((Double) val).intValue(), forceRepaint);
        } else if (command.equals(ActionW.PRESET.cmd())) {
          if (val instanceof PresetWindowLevel preset) {
            setPresetWindowLevel(preset, true);
          } else if (val == null) {
            setActionsInView(ActionW.PRESET.cmd(), val, false);
          }
        } else if (command.equals(ActionW.LUT_SHAPE.cmd())) {
          if (val instanceof LutShape lutShape) {
            renderingLayer.setLutShape(lutShape);
          }
        } else if (command.equals(ActionW.ROTATION.cmd()) && val instanceof Integer rotation) {
          setRotation(rotation);
        } else if (command.equals(ActionW.ROTATION.cmd()) && val instanceof Quaterniond quat) {
          // Arcball path: the source view already applied the rotation via Camera.rotate(), so
          // re-applying here would only trigger a redundant repaint. Targets adopt the full 3D
          // rotation that the integer slider angle cannot represent.
          if (!isSource) {
            camera.setRotation(quat);
          }
        } else if (command.equals(ActionW.RESET.cmd())) {
          reset();
        } else if (command.equals(ActionW.ZOOM.cmd())) {
          double value = (Double) val;
          // Special Cases: -200.0 => best fit, -100.0 => real world size
          if (value != -200.0 && value != -100.0) {
            // Around the pointer when the mouse zooms and cursor zoom is on
            zoom(value, ZoomChangeListener.getAnchor(synch, this, issuerSyncData, synchData));
          } else {
            Object zoomType = actionsInView.get(ViewCanvas.ZOOM_TYPE_CMD);
            actionsInView.put(
                ViewCanvas.ZOOM_TYPE_CMD, value == -100.0 ? ZoomType.REAL : ZoomType.BEST_FIT);
            zoom(0.0);
            actionsInView.put(ViewCanvas.ZOOM_TYPE_CMD, zoomType);
          }
        } else if (command.equals(ActionW.PAN.cmd())) {
          if (val instanceof PanPoint panPoint) {
            moveOrigin(panPoint);
          }
        } else if (command.equals(ActionW.FLIP.cmd())) {
          actionsInView.put(ActionW.FLIP.cmd(), val);
          // LangUtil.getNULLtoFalse((Boolean) val);
          //   updateAffineTransform();
          repaint();
        } else if (command.equals(ActionVol.VOL_PRESET.cmd())) {
          setVolumePreset((Preset) val);
        } else if (command.equals(ActionW.INVERT_LUT.cmd())) {
          if (val instanceof Boolean invertLut) {
            if (renderedPreset != null && renderingLayer.isInvertLut() != invertLut) {
              renderedPreset.setRequiredBuilding(true);
            }
            renderingLayer.setInvertLut(invertLut);
          }
        } else if (command.equals(ActionW.SPATIAL_UNIT.cmd())) {
          actionsInView.put(command, val);

          // TODO update only measure and limit when selected view share graphics
          List<Graphic> list = this.getGraphicManager().getAllGraphics();
          for (Graphic graphic : list) {
            graphic.updateLabel(true, this);
          }
        } else if (command.equals(ActionVol.RENDERING_TYPE.cmd())) {
          if (val instanceof RenderingType type) {
            RenderingType oldType = renderingLayer.getRenderingType();
            if (type != oldType) {
              renderingLayer.setEnableRepaint(false);
              renderingLayer.setRenderingType(type);
              if (type == RenderingType.PATH_TRACING) {
                // Path tracing is the cinematic lighting taken to its end, so it turns it on.
                lightingForcedByPathTracing = !renderingLayer.isCinematic();
                shadingForcedByPathTracing = !renderingLayer.isShading();
                if (lightingForcedByPathTracing) {
                  setCinematic(true);
                  selectAction(ActionVol.VOL_CINEMATIC, true);
                }
              } else if (oldType == RenderingType.PATH_TRACING) {
                restoreLightingAfterPathTracing();
              }
              renderingLayer.setEnableRepaint(true);
              display();
            }
          }
        } else if (command.equals(ActionVol.VOL_AXIS.cmd())) {
          if (val instanceof Axis axis) {
            camera.setRotationAxis(axis);
          }
        } else if (command.equals(ActionVol.VOL_QUALITY.cmd())) {
          if (val instanceof Integer quality) {
            renderingLayer.setQuality(quality);
          }
        } else if (command.equals(ActionVol.VOL_OPACITY.cmd())) {
          if (val instanceof Double opacity) {
            renderingLayer.setOpacity(opacity, forceRepaint);
          }
        } else if (command.equals(ActionVol.VOL_SHADING.cmd())) {
          if (val instanceof Boolean shading) {
            shadingForcedByPathTracing = false;
            renderingLayer.setShading(shading);
          }
        } else if (command.equals(ActionVol.VOL_CINEMATIC.cmd())) {
          if (val instanceof Boolean cinematic) {
            lightingForcedByPathTracing = false;
            setCinematic(cinematic);
          }
        } else if (command.equals(ActionVol.SEG_TYPE.cmd())) {
          if (val instanceof Type type) {
            setSegType(type);
          }
        } else if (command.equals(ActionVol.CROSSHAIR_CUT_MODE.cmd())) {
          if (val instanceof CrosshairCutMode cutMode) {
            mprCrossHairCutMode = cutMode;
            if (cutMode == CrosshairCutMode.NONE) {
              if (volTexture != null) {
                volTexture.unregisterCrossHairRelay();
              }
              mprCrossHairPosition = null;
              mprCrossHairRotation = null;
            } else {
              if (volTexture != null) {
                volTexture.registerCrossHairRelay();
              }
              ensureMprIsOpen();
            }
            display();
          }
        } else if (command.equals(ActionVol.VOL_PROJECTION.cmd())) {
          if (val instanceof Boolean projection) {
            camera.setOrthographicProjection(projection);
          }
        }
      }
    }
  }

  // Cinematic lighting is a refinement of the shading, so switching it on switches shading on too.
  private void setCinematic(boolean cinematic) {
    renderingLayer.setEnableRepaint(false);
    renderingLayer.setCinematic(cinematic);
    if (cinematic && !renderingLayer.isShading()) {
      renderingLayer.setShading(true);
      selectAction(ActionVol.VOL_SHADING, true);
    }
    renderingLayer.setEnableRepaint(true);
    renderingLayer.fireLayerChanged();
  }

  /**
   * Leaving path tracing gives back the lighting it switched on, unless the user changed that
   * option in the meantime. The caller repaints.
   */
  private void restoreLightingAfterPathTracing() {
    if (lightingForcedByPathTracing) {
      renderingLayer.setCinematic(false);
      selectAction(ActionVol.VOL_CINEMATIC, false);
      if (shadingForcedByPathTracing) {
        renderingLayer.setShading(false);
        selectAction(ActionVol.VOL_SHADING, false);
      }
    }
    lightingForcedByPathTracing = false;
    shadingForcedByPathTracing = false;
  }

  private void selectAction(Feature.ToggleButtonListenerValue action, boolean selected) {
    eventManager.getAction(action).ifPresent(a -> a.setSelectedWithoutTriggerAction(selected));
  }

  private void ensureMprIsOpen() {
    DicomVolTexture tex = volTexture;
    if (tex == null) {
      return;
    }
    MediaSeries<DicomImageElement> series = tex.getSeries();
    if (series == null) {
      return;
    }
    if (!fireCrossHairIfMprOpen(tex)) {
      // No MPR viewer found – open one.
      MprFactory.getMprAction(series).actionPerformed(null);
    }
  }

  private void syncMprPosition() {
    DicomVolTexture tex = volTexture;
    if (tex == null) {
      return;
    }
    fireCrossHairIfMprOpen(tex);
  }

  private boolean fireCrossHairIfMprOpen(DicomVolTexture tex) {
    Volume<?, ?> texVolume = tex.getVolume();
    if (texVolume == null || texVolume.getStack() == null) {
      return false;
    }
    OriginalStack texStack = texVolume.getStack();
    boolean texIsBasic = texVolume.isBasic();
    List<?> plugins = GuiUtils.getUICore().getViewerPlugins();
    synchronized (plugins) {
      for (Object plugin : plugins) {
        if (plugin instanceof MprContainer mpr) {
          Volume<?, ?> mprVolume = mpr.getMprController().getVolume();
          if (mprVolume != null
              && texStack.equals(mprVolume.getStack())
              && texIsBasic == mprVolume.isBasic()) {
            mpr.getMprController().fireCrossHairChanged();
            return true;
          }
        }
      }
    }
    return false;
  }

  private void setPresetWindowLevel(PresetWindowLevel preset, boolean repaint) {
    if (preset != null) {
      renderingLayer.setEnableRepaint(false);
      renderingLayer.setWindowWidth((int) preset.getWindow());
      renderingLayer.setWindowCenter((int) preset.getLevel());
      renderingLayer.setLutShape(preset.getLutShape());
      renderingLayer.setEnableRepaint(true);
      if (repaint) {
        renderingLayer.fireLayerChanged();
      }
    }
    setActionsInView(ActionW.PRESET.cmd(), preset, false);
  }

  @Override
  public MediaSeries<DicomImageElement> getSeries() {
    DicomVolTexture vol = volTexture;
    if (vol != null) {
      return vol.getSeries();
    }
    return null;
  }

  @Override
  public int getFrameIndex() {
    return 0;
  }

  @Override
  public void drawLayers(
      Graphics2D g2d, AffineTransform transform, AffineTransform inverseTransform) {}

  @Override
  public ImageLayer<DicomImageElement> getImageLayer() {
    return null;
  }

  @Override
  public MeasurableLayer getMeasurableLayer() {
    return null;
  }

  @Override
  public DicomImageElement getImage() {
    return null;
  }

  @Override
  public PlanarImage getSourceImage() {
    return null;
  }

  @Override
  public void handleLayerChanged(ImageLayer<DicomImageElement> layer) {}
}
