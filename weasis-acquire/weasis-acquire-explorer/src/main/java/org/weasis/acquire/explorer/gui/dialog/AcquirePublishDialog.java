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
package org.weasis.acquire.explorer.gui.dialog;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.media.jai.PlanarImage;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.acquire.explorer.gui.model.publish.PublishTree;
import org.weasis.acquire.explorer.util.ImageInfoHelper;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.image.ZoomOp;
import org.weasis.core.api.util.FontTools;
import org.weasis.core.api.util.StringUtil;

public class AcquirePublishDialog extends JDialog {
    private static final long serialVersionUID = -8736946182228791444L;

    public static final Logger LOGGER = LoggerFactory.getLogger(AcquirePublishDialog.class);

    public static final Integer MAX_RESOLUTION_THRESHOLD = 3000; // in pixels

    private final AcquirePublishPanel publishPanel;
    private final JPanel content = new JPanel();

    private PublishTree tree;
    private JPanel resolutionPanel;
    private final JComboBox<EResolution> resolutionCombo;

    public AcquirePublishDialog(AcquirePublishPanel publishPanel) {
        super(WinUtil.getParentWindow(publishPanel), "", ModalityType.APPLICATION_MODAL);
        this.resolutionCombo = new JComboBox<>(EResolution.values());
        this.publishPanel = publishPanel;
        this.initContent();

        tree.getTree().addCheckingPath(new TreePath(tree.getModel().getRootNode().getPath()));

        setContentPane(content);

        setPreferredSize(new Dimension(700, 400));
        pack();
    }

    private JPanel initContent() {
        content.setBorder(new EmptyBorder(10, 15, 10, 15));
        content.setLayout(new BorderLayout());
        JLabel question = new JLabel("Select the images to be published");
        question.setFont(FontTools.getFont12Bold());
        JPanel responsesPanel = new JPanel(new BorderLayout());
        responsesPanel.setBorder(BorderFactory.createEmptyBorder(30, 10, 10, 10));

        tree = new PublishTree();

        tree.addTreeCheckingListener(evt -> {
            resolutionPanel.setVisible(!getOversizedSelected(tree).isEmpty());
            resolutionPanel.repaint();
        });
        responsesPanel.add(tree);

        content.add(question, BorderLayout.NORTH);
        content.add(responsesPanel, BorderLayout.CENTER);

        JPanel panel = new JPanel();
        content.add(panel, BorderLayout.SOUTH);
        panel.setLayout(new BorderLayout(0, 0));

        resolutionPanel = initResolutionPanel();
        resolutionPanel.setVisible(Boolean.FALSE);

        JPanel panel1 = new JPanel();
        FlowLayout flowLayout = (FlowLayout) panel1.getLayout();
        flowLayout.setHgap(25);
        flowLayout.setVgap(10);
        panel.add(panel1, BorderLayout.SOUTH);

        JButton buttonNewButton = new JButton("Publish");
        buttonNewButton.addActionListener(e -> publishAction());
        panel1.add(buttonNewButton);

        JButton buttonNewButton1 = new JButton("Cancel");
        buttonNewButton1.addActionListener(e -> clearAndHide());
        panel1.add(buttonNewButton1);
        panel.add(resolutionPanel, BorderLayout.NORTH);

        return content;
    }

    private JPanel initResolutionPanel() {
        JPanel panel = new JPanel();
        Border margin = BorderFactory.createEmptyBorder(5, 10, 20, 10);
        Border line = BorderFactory.createLineBorder(Color.BLACK);

        panel.setBorder(BorderFactory.createCompoundBorder(margin, line));

        JLabel resolutionLabel = new JLabel("Resolution" + StringUtil.COLON_AND_SPACE);

        panel.add(resolutionLabel);
        panel.add(resolutionCombo);

        return panel;
    }

    // Overridden so we can exit when window is closed
    @Override
    protected void processWindowEvent(WindowEvent e) {
        if (e.getID() == WindowEvent.WINDOW_CLOSING) {
            clearAndHide();
        }
        super.processWindowEvent(e);
    }

    private void publishAction() {
        List<AcquireImageInfo> toPublish = getSelectedImages(tree);

        if (toPublish.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please, select at least one image to publish",
                "The publish list cannot be empty", JOptionPane.ERROR_MESSAGE);
            return;
        }

        List<AcquireImageInfo> overSizedSelected = getOversizedSelected(tree);
        if (!overSizedSelected.isEmpty()) {
            for (AcquireImageInfo imgInfo : overSizedSelected) {
                // caculate zoom ration
                Double ratio = ImageInfoHelper.calculateRatio(imgInfo, (EResolution) resolutionCombo.getSelectedItem(),
                    MAX_RESOLUTION_THRESHOLD.doubleValue());

                imgInfo.getCurrentValues().setRatio(ratio);
                imgInfo.getPostProcessOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_X, ratio);
                imgInfo.getPostProcessOpManager().setParamValue(ZoomOp.OP_NAME, ZoomOp.P_RATIO_Y, ratio);
            }
        }
        publishPanel.publish(toPublish);
        // publishPanel.publishForTest(toPublish);
        clearAndHide();
    }

    private List<AcquireImageInfo> getSelectedImages(PublishTree tree) {
        return Arrays.stream(tree.getModel().getCheckingPaths())
            .map(o1 -> DefaultMutableTreeNode.class.cast(o1.getLastPathComponent()))
            .filter(o2 -> AcquireImageInfo.class.isInstance(o2.getUserObject()))
            .map(o3 -> AcquireImageInfo.class.cast(o3.getUserObject())).collect(Collectors.toList());
    }

    private List<AcquireImageInfo> getOversizedSelected(PublishTree tree) {
        return getSelectedImages(tree).stream().filter(oversizedImages()).collect(Collectors.toList());
    }

    private Predicate<AcquireImageInfo> oversizedImages() {
        return acqImg -> {
            PlanarImage img = acqImg.getImage().getImage(acqImg.getPostProcessOpManager());

            Integer width = img.getWidth();
            Integer height = img.getHeight();

            return width > AcquirePublishDialog.MAX_RESOLUTION_THRESHOLD
                || height > AcquirePublishDialog.MAX_RESOLUTION_THRESHOLD;

        };
    }

    public void clearAndHide() {
        dispose();
    }

    public enum EResolution {
        original("Original"), hd("High Resolution"), md("Medium Resolution");

        private String title;

        EResolution(String title) {
            this.title = title;
        }
        
        @Override
        public String toString() {
            return title;
        }
    }

}
