/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.acquire.dockable.components.actions.annotate;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.annotate.comp.AnnotationIconsPanel;
import org.weasis.acquire.dockable.components.actions.annotate.comp.AnnotationOptionsPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireImageValues;

public class AnnotatePanel extends AbstractAcquireActionPanel {
    private static final long serialVersionUID = -3096519473431772537L;

    private final JPanel buttonsPanel = new AnnotationIconsPanel();
    private final JPanel optionsPanel = new AnnotationOptionsPanel();

    private JPanel content;

    public AnnotatePanel() {
        setLayout(new BorderLayout());

        content = createContent();
        add(content, BorderLayout.NORTH);

    }

    private JPanel createContent() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        panel.add(buttonsPanel, BorderLayout.NORTH);
        panel.add(optionsPanel, BorderLayout.CENTER);

        return panel;
    }

    @Override
    public void initValues(AcquireImageInfo info, AcquireImageValues values) {
        // Nothing to do
    }

}
