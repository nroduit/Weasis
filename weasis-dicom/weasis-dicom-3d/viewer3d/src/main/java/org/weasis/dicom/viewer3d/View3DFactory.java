/*
 * Copyright (c) 2012 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer3d;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLContext;
import com.jogamp.opengl.Threading;
import java.awt.Component;
import java.awt.Window;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Deactivate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ImageViewerPlugin.LayoutModel;
import org.weasis.core.ui.pref.PreferenceDialog;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer3d.vr.OpenglUtils;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class View3DFactory implements SeriesViewerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(View3DFactory.class);

  public static final String NAME = Messages.getString("3d.viewer");

  public static final String P_DEFAULT_LAYOUT = "volume.default.layout";
  public static final String P_OPENGL_ENABLE = "opengl.enable";
  public static final String P_OPENGL_PREV_INIT = "opengl.prev.init";

  private static final String JOGL_THREAD_CONFIG = "jogl.1thread";

  private static OpenGLInfo openGLInfo;

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(ActionIcon.VOLUME);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return NAME;
  }

  public static GridBagLayoutModel getDefaultGridBagLayoutModel() {
    String defLayout =
        GuiUtils.getUICore().getSystemPreferences().getProperty(View3DFactory.P_DEFAULT_LAYOUT);
    if (StringUtil.hasText(defLayout)) {
      return View3DContainer.LAYOUT_LIST.stream()
          .filter(g -> defLayout.equals(g.getId()))
          .findFirst()
          .orElse(View3DContainer.VIEWS_vr);
    }
    return View3DContainer.VIEWS_vr;
  }

  @Override
  public SeriesViewer createSeriesViewer(Map<String, Object> properties) {
    if (isOpenglEnable()) {
      ComboItemListener<GridBagLayoutModel> layoutAction =
          EventManager.getInstance().getAction(ActionW.LAYOUT).orElse(null);
      LayoutModel layout =
          ImageViewerPlugin.getLayoutModel(
              properties, getDefaultGridBagLayoutModel(), layoutAction);
      View3DContainer instance =
          new View3DContainer(layout.model(), layout.uid(), getUIName(), getIcon(), null);
      ImageViewerPlugin.registerInDataExplorerModel(properties, instance);
      return instance;
    }

    showOpenglErrorMessage(GuiUtils.getUICore().getBaseArea());
    return null;
  }

  public static int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    int val = 0;
    if (layout != null && defaultClass != null) {
      Iterator<LayoutConstraints> enumVal = layout.getConstraints().keySet().iterator();
      while (enumVal.hasNext()) {
        try {
          Class<?> clazz = Class.forName(enumVal.next().getType());
          if (defaultClass.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          LOGGER.error("Checking view", e);
        }
      }
    }
    return val;
  }

  public static void closeSeriesViewer(View3DContainer view3dContainer) {
    // Unregister the PropertyChangeListener
    DataExplorerView dicomView = GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(view3dContainer);
    }
    if (view3dContainer.volumeBuilder != null) {
      try {
        GL4 gl4 = OpenglUtils.getGL4();
        if (gl4 != null) {
          view3dContainer.volumeBuilder.getVolTexture().destroy(gl4);
        }
      } catch (Exception e) {
        LOGGER.error("Closing viewer", e);
      }
    }
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer viewer) {
    if (viewer instanceof View3DContainer) {
      return true;
    }
    return false;
  }

  @Override
  public int getLevel() {
    return 10;
  }

  @Override
  public boolean canAddSeries() {
    return false;
  }

  @Override
  public boolean canExternalizeSeries() {
    return true;
  }

  @Override
  public boolean canReadSeries(MediaSeries<?> series) {
    return series != null && series.isSuitableFor3d();
  }

  @Override
  public List<Action> getOpenActions() {
    return null;
  }

  public static OpenGLInfo getOpenGLInfo() {
    if (openGLInfo == null && isOpenglEnable()) {
      WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
      localPersistence.putBooleanProperty(P_OPENGL_PREV_INIT, false);
      try {
        Threading.invoke(true, View3DFactory::initOpenGLInfo, null);
      } catch (Throwable e) {
        localPersistence.putBooleanProperty(P_OPENGL_ENABLE, false);
        LOGGER.error("Cannot get basic OpenGL information");
      }
    }
    return openGLInfo;
  }

  public static int getMax3dTextureSize() {
    boolean previous =
        GuiUtils.getUICore().getLocalPersistence().getBooleanProperty(P_OPENGL_PREV_INIT, true);
    if (previous) {
      OpenGLInfo info = getOpenGLInfo();
      if (info != null) {
        return info.max3dTextureSize();
      }
    }
    return 512;
  }

  private static void initOpenGLInfo() {
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    try {
      long startTime = System.currentTimeMillis();
      LOGGER.info("Checking 3D capabilities");
      GLContext glContext = OpenglUtils.getDefaultGlContext();
      glContext.makeCurrent();
      GL var2 = glContext.getGL();
      int[] textMax = new int[1];
      var2.glGetIntegerv(GL2ES2.GL_MAX_3D_TEXTURE_SIZE, textMax, 0);
      String version = var2.glGetString(GL.GL_VERSION);
      openGLInfo =
          new OpenGLInfo(
              StringUtil.hasText(version) ? version : "0",
              glContext.getGLVersion(),
              var2.glGetString(GL.GL_VENDOR),
              var2.glGetString(GL.GL_RENDERER),
              textMax[0]);
      glContext.release();
      LOGGER.info(
          "{} 3D initialization time: {} ms",
          AuditLog.MARKER_PERF,
          (System.currentTimeMillis() - startTime));
      localPersistence.putBooleanProperty(P_OPENGL_PREV_INIT, true);
      LOGGER.info(
          "Video card for OpenGL: {}, {} {}",
          openGLInfo.vendor(),
          openGLInfo.renderer(),
          openGLInfo.version());
      if (!openGLInfo.isVersionCompliant()) {
        throw new IllegalStateException(
            "OpenGL %s is not compliant with compute shader".formatted(openGLInfo.shortVersion()));
      }
    } catch (Exception e) {
      localPersistence.putBooleanProperty(P_OPENGL_ENABLE, false);
      localPersistence.putBooleanProperty(P_OPENGL_PREV_INIT, false);
      LOGGER.error("Cannot init OpenGL", e);
    }
  }

  public static boolean isOpenglEnable() {
    return GuiUtils.getUICore().getLocalPersistence().getBooleanProperty(P_OPENGL_ENABLE, true);
  }

  public static void showOpenglErrorMessage(Component parent) {
    String msg = Messages.getString("opengl.error.msg");
    JButton prefButton = new JButton(Messages.getString("check.in.preferences"));
    prefButton.addActionListener(
        e -> {
          SwingUtilities.getWindowAncestor(prefButton).dispose();
          Window win = GuiUtils.getUICore().getApplicationWindow();
          ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(win);
          PreferenceDialog dialog = new PreferenceDialog(win);
          dialog.showPage(NAME);
          ColorLayerUI.showCenterScreen(dialog, layer);
        });
    JPanel panel =
        GuiUtils.getVerticalBoxLayoutPanel(GuiUtils.getScaleLength(7), new JLabel(msg), prefButton);
    JOptionPane.showMessageDialog(
        WinUtil.getValidComponent(parent),
        panel,
        Messages.getString("opengl.error"),
        JOptionPane.ERROR_MESSAGE);
  }

  // ================================================================================
  // OSGI service implementation
  // ================================================================================

  @Activate
  protected void activate(ComponentContext context) throws Exception {
    LOGGER.info("3D Viewer is activated");
    String joglThreadConfig =
        GuiUtils.getUICore().getSystemPreferences().getProperty(JOGL_THREAD_CONFIG);
    if (StringUtil.hasText(joglThreadConfig)) {
      System.setProperty(JOGL_THREAD_CONFIG, joglThreadConfig);
      LOGGER.debug("Custom " + JOGL_THREAD_CONFIG + " value: {}", joglThreadConfig);
    }

    WProperties prefs = GuiUtils.getUICore().getLocalPersistence();
    if (GuiUtils.getUICore().getSystemPreferences().getBooleanProperty("weasis.force.3d", false)) {
      prefs.putBooleanProperty(P_OPENGL_PREV_INIT, true);
      prefs.putBooleanProperty(P_OPENGL_ENABLE, true);
    }

    boolean openglPrevInit = prefs.getBooleanProperty(P_OPENGL_PREV_INIT, true);
    if (!openglPrevInit && isOpenglEnable()) {
      LOGGER.info("Do not enable OpenGL because of the last initialization has failed.");
      prefs.putBooleanProperty(P_OPENGL_ENABLE, false);
    }

    if (isOpenglEnable()) {
      getOpenGLInfo();
    }
  }

  @Deactivate
  protected void deactivate(ComponentContext context) {
    LOGGER.info("3D Viewer is deactivated");
  }
}
