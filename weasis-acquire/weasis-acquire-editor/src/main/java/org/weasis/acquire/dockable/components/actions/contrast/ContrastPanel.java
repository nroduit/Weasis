/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.contrast;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.Messages;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.contrast.comp.BrightnessComponent;
import org.weasis.acquire.dockable.components.actions.contrast.comp.ContrastComponent;
import org.weasis.acquire.dockable.components.util.AbstractComponent;
import org.weasis.acquire.dockable.components.util.AbstractSliderComponent;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;
import org.weasis.acquire.operations.OpValueChanged;
import org.weasis.acquire.operations.impl.AutoLevelListener;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.image.BrightnessOp;
import org.weasis.core.api.image.ImageOpNode;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

public class ContrastPanel extends AbstractAcquireActionPanel implements ChangeListener, OpValueChanged {
    private static final long serialVersionUID = -3978989511436089997L;

    private final AbstractSliderComponent contrastPanel;
    private final AbstractSliderComponent brightnessPanel;
    private final AutoLevelListener autoLevelListener;

    private JCheckBox autoLevelBtn = new JCheckBox(Messages.getString("ContrastPanel.auto")); //$NON-NLS-1$

    public ContrastPanel() {
        super();
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        autoLevelListener = new AutoLevelListener();
        autoLevelBtn.addActionListener(autoLevelListener);
        contrastPanel = new ContrastComponent(this);
        brightnessPanel = new BrightnessComponent(this);

        JPanel content = new JPanel(new GridLayout(3, 1, 0, 10));
        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.setAlignmentY(Component.TOP_ALIGNMENT);
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.add(contrastPanel);
        content.add(brightnessPanel);
        content.add(autoLevelBtn);

        add(content, BorderLayout.NORTH);
    }

    @Override
    public boolean needValidationPanel() {
        return true;
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        ViewCanvas<ImageElement> view = EventManager.getInstance().getSelectedViewPane();
        info.clearPreProcess();

        AcquireImageValues next = info.getNextValues();
        next.setContrast(values.getContrast());
        next.setBrightness(values.getBrightness());
        next.setAutoLevel(values.isAutoLevel());

        autoLevelBtn.removeActionListener(autoLevelListener);
        contrastPanel.removeChangeListener(this);
        brightnessPanel.removeChangeListener(this);
        contrastPanel.setSliderValue(next.getContrast());
        brightnessPanel.setSliderValue(next.getBrightness());
        autoLevelBtn.setSelected(next.isAutoLevel());
        autoLevelBtn.addActionListener(autoLevelListener);
        contrastPanel.addChangeListener(this);
        brightnessPanel.addChangeListener(this);
        repaint();

        applyNextValues();
        autoLevelListener.applyNextValues();

        info.applyPreProcess(view);
    }

    @Override
    public void stateChanged(ChangeEvent e) {
        JSlider slider = (JSlider) e.getSource();
        JPanel panel = (JPanel) slider.getParent();
        if (panel instanceof AbstractSliderComponent) {
            ((AbstractComponent) panel).updatePanelTitle();
        }

        AcquireImageInfo imageInfo = AcquireObject.getImageInfo();
        imageInfo.getNextValues().setBrightness(brightnessPanel.getSliderValue());
        imageInfo.getNextValues().setContrast(contrastPanel.getSliderValue());
        applyNextValues();
        imageInfo.applyPreProcess(AcquireObject.getView());
    }

    @Override
    public void applyNextValues() {
        AcquireImageInfo imageInfo = AcquireObject.getImageInfo();
        ImageOpNode node = imageInfo.getPreProcessOpManager().getNode(BrightnessOp.OP_NAME);
        if (node == null) {
            node = new BrightnessOp();
            imageInfo.addPreProcessImageOperationAction(node);
        } else {
            node.clearIOCache();
        }
        node.setParam(BrightnessOp.P_BRIGTNESS_VALUE, (double) imageInfo.getNextValues().getBrightness());
        node.setParam(BrightnessOp.P_CONTRAST_VALUE, (double) imageInfo.getNextValues().getContrast());
    }
}
