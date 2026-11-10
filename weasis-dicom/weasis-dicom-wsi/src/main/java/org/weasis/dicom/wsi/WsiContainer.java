/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wsi;

import java.beans.PropertyChangeEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.swing.JComponent;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.layout.MigLayoutModel;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.View2dContainer;

/**
 * The slide viewer: a 2D viewer of the DICOM viewer whose single view paints a slide from its tiles
 * ({@link WsiView2d}), with the tools of the 2D viewer and the slide toolbar.
 */
public class WsiContainer extends View2dContainer {

  public static final MigLayoutModel DEFAULT_VIEW =
      new MigLayoutModel(
          "wsi1x1", // NON-NLS
          "1x1", // NON-NLS
          1,
          1,
          WsiView2d.class.getName());

  private static final SeriesViewerUI WSI_UI = new SeriesViewerUI(WsiContainer.class);

  /** The slide view settles the open once its slide is read, which is what --wait waits for. */
  @Override
  public CompletableFuture<Void> whenReady() {
    for (ViewCanvas<DicomImageElement> view : getImagePanels()) {
      if (view instanceof WsiView2d slideView) {
        return slideView.whenReady();
      }
    }
    return super.whenReady();
  }

  public WsiContainer() {
    this(DEFAULT_VIEW, null);
  }

  public WsiContainer(MigLayoutModel layoutModel, String uid) {
    super(layoutModel, uid, WsiFactory.NAME, ResourceUtil.getIcon(ActionIcon.ZOOM), null);
    // The tools of the 2D viewer (measurements, display) with the slide toolbar
    if (!WSI_UI.init.getAndSet(true)) {
      WSI_UI.toolBars.addAll(View2dContainer.UI.toolBars);
      WSI_UI.toolBars.add(new WsiToolBar(25));
      WSI_UI.tools.addAll(View2dContainer.UI.tools);
    }
  }

  @Override
  public SeriesViewerUI getSeriesViewerUI() {
    return WSI_UI;
  }

  @Override
  public DefaultView2d<DicomImageElement> createDefaultView(String classType) {
    return new WsiView2d(eventManager);
  }

  // The view class is in this plugin, out of reach of the class loader of the 2D viewer
  @Override
  public boolean isViewType(Class<?> defaultClass, String type) {
    if (WsiView2d.class.getName().equals(type)) {
      return defaultClass != null && defaultClass.isAssignableFrom(WsiView2d.class);
    }
    return super.isViewType(defaultClass, type);
  }

  @Override
  public int getViewTypeNumber(MigLayoutModel layout, Class<?> defaultClass) {
    if (layout == null || defaultClass == null) {
      return 0;
    }
    return (int) layout.getCells().stream().filter(c -> isViewType(defaultClass, c.type())).count();
  }

  @Override
  public JComponent createComponent(String clazz) {
    if (WsiView2d.class.getName().equals(clazz)) {
      return createDefaultView(clazz);
    }
    return super.createComponent(clazz);
  }

  @Override
  public MigLayoutModel getDefaultLayoutModel() {
    return DEFAULT_VIEW;
  }

  @Override
  public List<MigLayoutModel> getLayoutList() {
    return List.of(DEFAULT_VIEW);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    super.propertyChange(evt);
    if (evt instanceof ObservableEvent event) {
      switch (event.getActionCommand()) {
        // The viewer opens with the first instances of the series: the others join it later
        case LOADING_STOP -> refreshSlides(null);
        case UPDATE -> {
          if (event.getNewValue() instanceof MediaElement element) {
            refreshSlides(element);
          }
        }
        default -> {
          // Handled by the 2D viewer
        }
      }
    }
  }

  private void refreshSlides(MediaElement element) {
    GuiExecutor.execute(
        () -> {
          for (ViewCanvas<DicomImageElement> view : getImagePanels()) {
            if (view instanceof WsiView2d slideView
                && (element == null || slideView.isOfSlide(element))) {
              slideView.refreshSlide();
            }
          }
        });
  }
}
