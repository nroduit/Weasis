/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.annotate.comp;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

import org.weasis.base.viewer2d.EventManager;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.JToogleButtonGroup;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.MouseActions;
import org.weasis.core.ui.editor.image.ViewerToolBar;

@SuppressWarnings("serial")
public class AnnotationIconsPanel extends JPanel {
    private final Border spaceY = BorderFactory.createEmptyBorder(10, 3, 0, 3);

    public AnnotationIconsPanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        createButtons(ActionW.MEASURE, ActionW.DRAW_MEASURE);
        createButtons(ActionW.DRAW, ActionW.DRAW_GRAPHICS);
    }

    private void createButtons(ActionW action, ActionW graphicAction) {
        Optional<ComboItemListener> actionState =
            EventManager.getInstance().getAction(graphicAction, ComboItemListener.class);
        if (!actionState.isPresent()) {
            return;
        }

        final JPanel pIcons = new JPanel();
        pIcons.setBorder(BorderFactory.createCompoundBorder(spaceY, new TitledBorder(null, graphicAction.getTitle(),
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, FontTools.getFont12Bold(), Color.GRAY)));

        JToogleButtonGroup measures = actionState.get().createButtonGroup();
        JToggleButton[] items = measures.getJToggleButtonList();

        pIcons.setLayout(new GridBagLayout());
        for (int i = 0; i < items.length; i++) {
            items[i].addActionListener(e -> {
                ImageViewerEventManager<ImageElement> eventManager = EventManager.getInstance();
                ImageViewerPlugin<? extends ImageElement> view = eventManager.getSelectedView2dContainer();
                if (view != null) {
                    final ViewerToolBar toolBar = view.getViewerToolBar();
                    if (toolBar != null) {
                        String cmd = action.cmd();
                        if (!toolBar.isCommandActive(cmd)) {
                            MouseActions mouseActions = eventManager.getMouseActions();
                            mouseActions.setAction(MouseActions.LEFT, cmd);
                            view.setMouseActions(mouseActions);
                            toolBar.changeButtonState(MouseActions.LEFT, cmd);
                        }
                    }
                }

            });
            GridBagConstraints constraints = new GridBagConstraints();
            constraints.insets = new Insets(0, 0, 5, 5);
            constraints.gridx = i % 7;
            constraints.gridy = i / 7;
            Dimension size = items[i].getPreferredSize();
            if (size != null && size.width > size.height) {
                items[i].setPreferredSize(new Dimension(size.height + 2, size.height));
            }
            pIcons.add(items[i], constraints);
        }
        JPanel panelLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panelLeft.add(pIcons);
        add(panelLeft);
    }
}
