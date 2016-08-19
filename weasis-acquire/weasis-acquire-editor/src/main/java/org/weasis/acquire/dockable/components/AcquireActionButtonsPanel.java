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
package org.weasis.acquire.dockable.components;

import java.awt.GridLayout;
import java.util.Objects;
import java.util.Optional;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JPanel;

import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.EditionTool;
import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.AcquireAction;
import org.weasis.acquire.dockable.components.actions.AcquireAction.Cmd;
import org.weasis.acquire.dockable.components.actions.annotate.AnnotateAction;
import org.weasis.acquire.dockable.components.actions.calibrate.CalibrationAction;
import org.weasis.acquire.dockable.components.actions.contrast.ContrastAction;
import org.weasis.acquire.dockable.components.actions.meta.MetadataAction;
import org.weasis.acquire.dockable.components.actions.rectify.RectifyAction;

public class AcquireActionButtonsPanel extends JPanel {
    private static final long serialVersionUID = -8768455915804608493L;

    private static final int PADDING = 10;

    private final ButtonGroup btnGroup = new ButtonGroup();

    private AcquireActionButton selected;
    private final EditionTool editionTool;

    public AcquireActionButtonsPanel(EditionTool editionTool) {
        super(new GridLayout(2, 3, PADDING, PADDING));
        setBorder(BorderFactory.createEmptyBorder(PADDING, PADDING, PADDING, PADDING));

        this.editionTool = editionTool;

        setSelected(addNewButton("Metadata", new MetadataAction(this)));
        addNewButton("Rectify", new RectifyAction(this));
        addNewButton("Contrasts", new ContrastAction(this));
        addNewButton("Calibration", new CalibrationAction(this));
        addNewButton("Annotation", new AnnotateAction(this));
    }

    private AcquireActionButton addNewButton(String title, AcquireAction action) {
        AcquireActionButton btn = new AcquireActionButton(title, action);
        add(btn);
        btnGroup.add(btn);
        return btn;
    }

    public void setSelected(AcquireActionButton selected) {
        Objects.requireNonNull(selected);
        AcquireActionButton old = this.selected;

        if (Objects.nonNull(old) && Objects.equals(selected.getActionCommand(), Cmd.INIT.name())) {
            old.getAcquireAction().validate();
        }
        this.selected = selected;
        btnGroup.clearSelection();
        btnGroup.setSelected(this.selected.getModel(), true);

        Optional.ofNullable(AcquireObject.getImageInfo())
            .ifPresent(i -> this.selected.getCentralPanel().initValues(i, i.getNextValues()));

        editionTool.setCentralPanel((AbstractAcquireActionPanel) selected.getCentralPanel());
        editionTool.setBottomPanelActions(selected.getAcquireAction());
    }

    public AcquireActionButton getSelected() {
        return this.selected;
    }
}
