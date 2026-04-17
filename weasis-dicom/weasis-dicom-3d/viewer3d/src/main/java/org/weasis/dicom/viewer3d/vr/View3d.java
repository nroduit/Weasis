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

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import java.nio.IntBuffer;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Action;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.ToolTipManager;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.Feature;
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
import org.weasis.core.ui.editor.image.ContextMenuHandler;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.DefaultView2d.ZoomType;
import org.weasis.core.ui.editor.image.FocusHandler;
import org.weasis.core.ui.editor.image.GraphicMouseHandler;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.Panner;
import org.weasis.core.ui.editor.image.PixelInfo;
import org.weasis.core.ui.editor.image.SynchData;
import org.weasis.core.ui.editor.image.SynchData.Mode;
import org.weasis.core.ui.editor.image.SynchEvent;
import org.weasis.core.ui.editor.image.ViewButton;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewProgress;
import org.weasis.core.ui.model.graphic.Graphic;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.model.layer.LayerItem;
import org.weasis.core.ui.model.layer.LayerType;
import org.weasis.core.ui.model.utils.bean.PanPoint;
import org.weasis.core.util.LangUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.geometry.PatientOrientation;
import org.weasis.dicom.explorer.DicomSeriesHandler;
import org.weasis.dicom.viewer2d.mpr.AxisDirection;
import org.weasis.dicom.viewer2d.mpr.MprContainer;
import org.weasis.dicom.viewer2d.mpr.MprFactory;
import org.weasis.dicom.viewer2d.mpr.OriginalStack;
import org.weasis.dicom.viewer2d.mpr.Volume;
import org.weasis.dicom.viewer3d.ActionVol;
import org.weasis.dicom.viewer3d.EventManager;
import org.weasis.dicom.viewer3d.InfoLayer3d;
import org.weasis.dicom.viewer3d.OpenGLInfo;
import org.weasis.dicom.viewer3d.View3DFactory;
import org.weasis.dicom.viewer3d.dockable.SegmentationTool;
import org.weasis.dicom.viewer3d.dockable.SegmentationTool.Type;
import org.weasis.dicom.viewer3d.geometry.Axis;
import org.weasis.dicom.viewer3d.geometry.Camera;
import org.weasis.dicom.viewer3d.geometry.View;
import org.weasis.dicom.viewer3d.vr.TextureData.PixelFormat;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.lut.LutShape;

public class View3d extends VolumeCanvas
    implements ViewCanvas<DicomImageElement>,
        RenderingLayerChangeListener<DicomImageElement>,
        GLEventListener,
        ViewProgress {

  private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(View3d.class);

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
  private final boolean useComputeShader;
  protected final RenderingLayer<DicomImageElement> renderingLayer;

  private int vertexBuffer;
  protected Preset volumePreset;
  private JProgressBar progressBar;

  private volatile Vector3d mprCrossHairPosition; // NOSONAR visibility reference
  private volatile Quaterniond mprCrossHairRotation; // NOSONAR visibility reference
  private volatile CrosshairCutMode mprCrossHairCutMode = CrosshairCutMode.NONE;

  private PropertyChangeListener mprCrossHairListener;
  private DicomVolTexture mprCrossHairListenerSource;

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
      LOGGER.info("Volume rendering: using compute shader path (OpenGL >= 4.3)");
    } else {
      this.texture = new FboRenderTexture(this);
      this.program =
          new Program(
              "fbo", ShaderManager.FBO_VERTEX_SHADER, ShaderManager.FBO_FRAGMENT_SHADER); // NON-NLS
      LOGGER.info(
          "Volume rendering: using FBO fragment-shader path (OpenGL 3.3 fallback{})",
          View3DFactory.isFboForced()
              ? ", forced via " + View3DFactory.P_FORCE_FBO
              : ""); // NON-NLS
    }
    this.quadProgram =
        new Program("basic", ShaderManager.VERTEX_SHADER, ShaderManager.FRAGMENT_SHADER); // NON-NLS
    try {
      setSharedContext(OpenglUtils.getDefaultGlContext());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    setLayout(null);

    this.renderingLayer = new RenderingLayer<>();
    this.volumePreset = Preset.getDefaultPreset(null);
    volumePreset.setRequiredBuilding(true);

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

    addGLEventListener(this);
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
            Double currentZoom = (Double) actionsInView.get(ActionW.ZOOM.cmd());
            /*
             * Negative value means a default value according to the zoom type (pixel size, best fit...). Set again
             * to default value to compute again the position. For instance, the image cannot be center aligned
             * until the view has been repaint once (because the size is null).
             */
            if (currentZoom <= 0.0) {
              zoom(0.0);
            }
            repaint();
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
            repaint();
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
    GL2ES2 gl = OpenglUtils.getGL();
    if (gl != null) {
      program.destroy(gl);
      quadProgram.destroy(gl);
      texture.destroy(gl);
    }
    super.disposeView();
  }

  @Override
  public void handleLayerChanged(RenderingLayer<DicomImageElement> layer) {
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
      ComboItemListener<Type> segType = eventManager.getAction(ActionVol.SEG_TYPE).orElse(null);
      Preset preset;
      if (segType != null && segType.getSelectedItem() == SegmentationTool.Type.SEG_ONLY) {
        preset = Preset.getSegmentationLut();
      } else {
        preset = Preset.getDefaultPreset(volTexture.getModality());
      }

      renderingLayer.setEnableRepaint(true);
      setVolumePreset(preset);
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
    super.paintComponent(graphs);
    if (graphs instanceof Graphics2D graphics2D) {
      draw(graphics2D);
    }
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

  @Override
  public void init(GLAutoDrawable glAutoDrawable) {
    GL2ES2 gl = glAutoDrawable.getGL().getGL2ES2();
    initShaders(gl);
  }

  public void initShaders(GL2ES2 gl) {
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
            g.glUniformMatrix4fv(
                loc,
                1,
                false,
                camera.getViewMatrix().invert().get(Buffers.newDirectFloatBuffer(16))));
    program.allocateUniform(
        gl,
        "projectionMatrix",
        (g, loc) ->
            g.glUniformMatrix4fv(
                loc,
                1,
                false,
                camera.getProjectionMatrix().invert().get(Buffers.newDirectFloatBuffer(16))));
    program.allocateUniform(
        gl,
        "depthSampleNumber",
        (g, loc) -> g.glUniform1i(loc, renderingLayer.getDepthSampleNumber()));
    program.allocateUniform(
        gl,
        "lutShape",
        (g, loc) ->
            ((GL2ES3) g).glUniform1ui(loc, isSegMode() ? 0 : renderingLayer.getLutShapeId()));

    program.allocateUniform(
        gl,
        "backgroundColor",
        (g, loc) -> g.glUniform3fv(loc, 1, bColor.get(Buffers.newDirectFloatBuffer(3))));

    for (int i = 0; i < 4; ++i) {
      int val = i;
      program.allocateUniform(
          gl,
          String.format("lights[%d].position", val), // NON-NLS
          (g, loc) ->
              g.glUniform4fv(loc, 1, camera.getLightOrigin().get(Buffers.newDirectFloatBuffer(4))));
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
        (g, loc) -> g.glUniform3fv(loc, 1, lColor.get(Buffers.newDirectFloatBuffer(3))));
    program.allocateUniform(
        gl, "shading", (g, loc) -> g.glUniform1i(loc, renderingLayer.isShading() ? 1 : 0));
    program.allocateUniform(
        gl,
        "texelSize",
        (g, loc) -> {
          DicomVolTexture tex = volTexture;
          Vector3f texelSizeVal =
              tex != null
                  ? tex.getNormalizedTexelSize().get(new Vector3f())
                  : new Vector3f(1f, 1f, 1f);
          g.glUniform3fv(loc, 1, texelSizeVal.get(Buffers.newDirectFloatBuffer(3)));
        });

    program.allocateUniform(
        gl,
        "renderingType",
        (g, loc) -> ((GL2ES3) g).glUniform1ui(loc, renderingLayer.getRenderingType().getId()));
    program.allocateUniform(
        gl,
        "mipType",
        (g, loc) ->
            ((GL2ES3) g).glUniform1ui(loc, renderingLayer.getRenderingType().getMipTypeId()));
    program.allocateUniform(gl, "volTexture", (g, loc) -> g.glUniform1i(loc, 0));
    program.allocateUniform(gl, "colorMap", (g, loc) -> g.glUniform1i(loc, 1));
    program.allocateUniform(
        gl,
        "textureDataType",
        (g, loc) -> ((GL2ES3) g).glUniform1ui(loc, TextureData.getDataType(getPixelFormat())));

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
                  ? volumePreset.getWidth()
                  : (tex != null ? (float) tex.getLevelMax() : 1f));
        });
    program.allocateUniform(gl, "outputLevelMin", (g, loc) -> g.glUniform1f(loc, 0));
    program.allocateUniform(
        gl, "outputLevelMax", (g, loc) -> g.glUniform1f(loc, volumePreset.getWidth()));
    program.allocateUniform(
        gl,
        "windowWidth",
        (g, loc) ->
            g.glUniform1f(
                loc,
                isSegMode()
                    ? volumePreset.getColorMax() - volumePreset.getColorMin()
                    : renderingLayer.getWindowWidth()));
    program.allocateUniform(
        gl,
        "windowCenter",
        (g, loc) ->
            g.glUniform1f(
                loc,
                isSegMode()
                    ? (volumePreset.getColorMin() + volumePreset.getColorMax()) / 2f
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
      // FBO framebuffer dimensions for the crosshair overlay (imageSize() is unavailable in
      // fragment shaders; we pass the logical FBO size instead).
      program.allocateUniform(
          gl,
          "viewportSize", // NON-NLS
          (g, loc) -> g.glUniform2i(loc, texture.getWidth(), texture.getHeight()));
    }

    final IntBuffer intBuffer = IntBuffer.allocate(1);
    texture.init(gl);
    if (volTexture != null) {
      volTexture.init(gl);
    }
    if (volumePreset != null) {
      volumePreset.init(gl, renderingLayer.isInvertLut());
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
          g.glUniformMatrix3fv(loc, 1, false, f, 0);
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

    quadProgram.init(gl);
    gl.glGenBuffers(1, intBuffer);
    vertexBuffer = intBuffer.get(0);
    gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBuffer);
    gl.glBufferData(
        GL.GL_ARRAY_BUFFER,
        (long) vertexBufferData.length * Float.BYTES,
        Buffers.newDirectFloatBuffer(vertexBufferData),
        GL.GL_STATIC_DRAW);
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

  public void display(GLAutoDrawable drawable) {
    render(drawable.getGL().getGL2ES2());
  }

  private void render(GL2ES2 gl2) {
    gl2.glClear(GL.GL_COLOR_BUFFER_BIT | GL.GL_DEPTH_BUFFER_BIT);
    if (volTexture != null && volTexture.isReadyForDisplay()) {
      int sampleCount = renderingLayer.getQuality();
      if (camera.isAdjusting()) {
        double quality =
            GuiUtils.getUICore()
                    .getLocalPersistence()
                    .getIntProperty(
                        RenderingLayer.P_DYNAMIC_QUALITY,
                        RenderingLayer.DEFAULT_DYNAMIC_QUALITY_RATE)
                / 100.0;
        sampleCount = Math.max(64, (int) Math.round(sampleCount * quality));
      }
      renderingLayer.setDepthSampleNumber(sampleCount);

      if (useComputeShader) {
        // --- Compute shader path (OpenGL >= 4.3) ---
        // Cast to GL4 here: compute shaders (glDispatchCompute, glBindImageTexture) are GL4-only.
        GL4 gl4 = gl2.getGL4();
        program.use(gl4);
        program.setUniforms(gl4);
        volTexture.render(gl4);
        if (volumePreset != null) {
          volumePreset.render(gl4, renderingLayer.isInvertLut());
        }
        texture.render(gl4);
        quadProgram.use(gl4);

        gl4.glEnable(GL.GL_BLEND);
        gl4.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl4.glEnableVertexAttribArray(0);
        gl4.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBuffer);
        gl4.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 0, 0);
        gl4.glActiveTexture(GL.GL_TEXTURE0);
        gl4.glBindTexture(GL.GL_TEXTURE_2D, texture.getId());
        gl4.glDrawArrays(GL.GL_TRIANGLES, 0, vertexBufferData.length / 2);
        gl4.glDisableVertexAttribArray(0);
        gl4.glDisable(GL.GL_BLEND);
      } else {
        // --- FBO fragment-shader fallback path (OpenGL 3.3+, e.g., macOS GL3) ---
        program.use(gl2);
        program.setUniforms(gl2);

        // Bind volume and LUT textures on their expected texture units
        volTexture.render(gl2);
        if (volumePreset != null) {
          volumePreset.render(gl2, renderingLayer.isInvertLut());
        }

        // Set up the vertex array so FboRenderTexture.render() can call glDrawArrays
        gl2.glEnableVertexAttribArray(0);
        gl2.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBuffer);
        gl2.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 0, 0);

        // Render into FBO (binds FBO, draws quad, unbinds FBO, restores viewport)
        texture.render(gl2);
        gl2.glDisableVertexAttribArray(0);

        // Step 2: Blit the FBO colour-attachment texture to the screen using the quad program.
        // The FBO output lives on unit 3 (FboRenderTexture.OUTPUT_TEXTURE_UNIT); point the
        // quad sampler there so we never disturb the 3D volume texture on unit 0.
        quadProgram.use(gl2);
        gl2.glUniform1i(
            gl2.glGetUniformLocation(quadProgram.getProgramId(), "compute"), // NON-NLS
            FboRenderTexture.OUTPUT_TEXTURE_UNIT);

        gl2.glEnable(GL.GL_BLEND);
        gl2.glBlendFunc(GL.GL_SRC_ALPHA, GL.GL_ONE_MINUS_SRC_ALPHA);

        gl2.glEnableVertexAttribArray(0);
        gl2.glBindBuffer(GL.GL_ARRAY_BUFFER, vertexBuffer);
        gl2.glVertexAttribPointer(0, 2, GL.GL_FLOAT, false, 0, 0);
        gl2.glActiveTexture(GL.GL_TEXTURE0 + FboRenderTexture.OUTPUT_TEXTURE_UNIT);
        gl2.glBindTexture(GL.GL_TEXTURE_2D, texture.getId());
        gl2.glDrawArrays(GL.GL_TRIANGLES, 0, vertexBufferData.length / 2);
        gl2.glDisableVertexAttribArray(0);
        gl2.glDisable(GL.GL_BLEND);
      }
    }
  }

  public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
    GL2ES2 gl2 = drawable.getGL().getGL2ES2();
    gl2.glViewport(0, 0, width, height);
    camera.resetTransformation();
  }

  public void dispose(GLAutoDrawable drawable) {
    // GL4 gl2 = drawable.getGL().getGL4();
    // FIXME destroy when release of cache
    //    if (volTexture != null) {
    //      volTexture.destroy(gl2);
    //    }
  }

  public void updateSegmentation() {}

  public void setVolumePreset(Preset preset) {
    this.volumePreset = Objects.requireNonNull(preset);
    volumePreset.setRequiredBuilding(true);

    PresetWindowLevel defaultPreset = getVolTexture().getDefaultPreset(volumePreset);
    changePresetWindowLevel(defaultPreset);

    renderingLayer.applyVolumePreset(volumePreset, false);
    eventManager
        .getAction(ActionVol.VOL_SHADING)
        .ifPresent(a -> a.setSelectedWithoutTriggerAction(volumePreset.isShade()));
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

  public Preset getVolumePreset() {
    return volumePreset;
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
  public void updateSynchState() {}

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
    if (point != null) {
      if (PanPoint.State.DRAGGING.equals(point.getState())) {
        camera.translate(point);
      }
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
    return null;
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
    camera.set(c.position(), c.rotation(), -getBestFitViewScale(), false);
    renderingLayer.setEnableRepaint(false);
    renderingLayer.setQuality(getDefaultQuality());
    renderingLayer.setOpacity(1.0);
    renderingLayer.setInvertLut(false);
    setVolumePreset(volumePreset);
    renderingLayer.setEnableRepaint(true);
    renderingLayer.fireLayerChanged();
    eventManager.updateComponentsListener(this);
  }

  @Override
  public List<ViewButton> getViewButtons() {
    return Collections.emptyList();
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
      GuiUtils.addItemToMenu(popupMenu, manager.getSProjectionMenu(null));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getMprCutMenu(null));
      count = addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getZoomMenu(null));
      GuiUtils.addItemToMenu(popupMenu, manager.getOrientationMenu(null));
      addSeparatorToPopupMenu(popupMenu, count);

      GuiUtils.addItemToMenu(popupMenu, manager.getResetMenu(null));
    }
    return popupMenu;
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
      if (synchData != null && Mode.NONE.equals(synchData.getMode())) {
        return;
      }
      // Progressive mode for VR
      boolean forceRepaint = camera.isAdjusting() != synch.isValueIsAdjusting();
      camera.setAdjusting(synch.isValueIsAdjusting());

      for (Entry<String, Object> entry : synch.getEvents().entrySet()) {
        String command = entry.getKey();
        final Object val = entry.getValue();
        if (synchData != null && !synchData.isActionEnable(command)) {
          continue;
        } else if (command.equals(ActionW.WINDOW.cmd())) {
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
        } else if (command.equals(ActionW.RESET.cmd())) {
          reset();
        } else if (command.equals(ActionW.ZOOM.cmd())) {
          double value = (Double) val;
          // Special Cases: -200.0 => best fit, -100.0 => real world size
          if (value != -200.0 && value != -100.0) {
            zoom(value);
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
            if (volumePreset != null && renderingLayer.isInvertLut() != invertLut) {
              volumePreset.setRequiredBuilding(true);
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
            renderingLayer.setShading(shading);
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
