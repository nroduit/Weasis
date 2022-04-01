/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.acquire.dockable.components.actions.contrast;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.contrast.comp.BrightnessComponent;
import org.weasis.acquire.dockable.components.actions.contrast.comp.ContrastComponent;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.acquire.operations.impl.AutoLevelListener;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.BrightnessOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class ContrastPanel extends AbstractAcquireActionPanel
    implements ChangeListener, OpValueChanged {

  private final AbstractSliderComponent contrastSlider;
  private final AbstractSliderComponent brightnessSlider;
  private final AutoLevelListener autoLevelListener;

  private final JCheckBox autoLevelBtn = new JCheckBox(Messages.getString("ContrastPanel.auto"));

  public ContrastPanel() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(GuiUtils.getEmptyBorder(10, 5, 2, 5));

    this.autoLevelListener = new AutoLevelListener();
    autoLevelBtn.addActionListener(autoLevelListener);
    contrastSlider = new ContrastComponent(this);
    brightnessSlider = new BrightnessComponent(this);

    add(contrastSlider);
    add(GuiUtils.boxVerticalStrut(10));
    add(brightnessSlider);
    add(GuiUtils.getFlowLayoutPanel(autoLevelBtn));
    add(GuiUtils.boxYLastElement(5));
  }

  @Override
  public boolean needValidationPanel() {
    return true;
  }

  @Override
  public void initValues(AcquireImageInfo info, AcquireImageValues values) {
    ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();
    AcquireImageValues next = info.getNextValues();
    next.setContrast(values.getContrast());
    next.setBrightness(values.getBrightness());
    next.setAutoLevel(values.isAutoLevel());

    autoLevelBtn.removeActionListener(autoLevelListener);
    contrastSlider.removeChangeListener(this);
    brightnessSlider.removeChangeListener(this);
    contrastSlider.setValue(next.getContrast());
    brightnessSlider.setValue(next.getBrightness());
    autoLevelBtn.setSelected(next.isAutoLevel());
    autoLevelBtn.addActionListener(autoLevelListener);
    contrastSlider.addChangeListener(this);
    brightnessSlider.addChangeListener(this);
    repaint();

    applyNextValues();
    autoLevelListener.applyNextValues();

    info.applyCurrentProcessing(view);
  }

  @Override
  public void stateChanged(ChangeEvent e) {
    if (e.getSource() instanceof AbstractSliderComponent sliderComponent) {
      sliderComponent.updatePanelTitle();
    }

    AcquireImageInfo imageInfo = AcquireObject.getImageInfo();
    imageInfo.getNextValues().setBrightness(brightnessSlider.getSliderValue());
    imageInfo.getNextValues().setContrast(contrastSlider.getSliderValue());
    applyNextValues();
    imageInfo.applyCurrentProcessing(AcquireObject.getView());
  }

  @Override
  public void applyNextValues() {
    AcquireImageInfo imageInfo = AcquireObject.getImageInfo();
    ImageOpNode node = imageInfo.getPostProcessOpManager().getNode(BrightnessOp.OP_NAME);
    if (node != null) {
      node.clearIOCache();
      node.setParam(
          BrightnessOp.P_BRIGHTNESS_VALUE, (double) imageInfo.getNextValues().getBrightness());
      node.setParam(
          BrightnessOp.P_CONTRAST_VALUE, (double) imageInfo.getNextValues().getContrast());
    }
  }
}
