package org.weasis.acquire.dockable.components.actions.annotate;

import java.awt.BorderLayout;

import javax.swing.JPanel;

import org.weasis.acquire.dockable.components.actions.AbstractAcquireActionPanel;
import org.weasis.acquire.dockable.components.actions.annotate.comp.AnnotationIconsPanel;
import org.weasis.acquire.dockable.components.actions.annotate.comp.AnnotationOptionsPanel;

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


}
