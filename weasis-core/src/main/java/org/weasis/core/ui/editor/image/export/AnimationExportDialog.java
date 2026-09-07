/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor.image.export;

import static org.weasis.core.api.gui.Insertable.BLOCK_SEPARATOR;
import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR;
import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR_LARGE;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.formdev.flatlaf.util.SystemFileChooser.FileNameExtensionFilter;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.DisplayProfile;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.ColorLayerUI;
import org.weasis.core.ui.util.DisplayProfileSelector;
import org.weasis.core.ui.util.MaskingProfileSelector;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

/**
 * Common parameters of an animation export: what area to capture, at what size and speed, in what
 * format, and how much identity leaves with it. What advances a frame is contributed by the {@link
 * FrameSourceBuilder}, which owns the panel at the top.
 */
public class AnimationExportDialog extends JDialog {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnimationExportDialog.class);

  public static final String P_LAST_DIR = "animation.last.dir";

  private static final Integer[] SIZE_RATIOS = {100, 50, 25};

  private final transient FrameSourceBuilder builder;
  private final transient ViewCanvas<?> view;
  private final transient ImageViewerPlugin<?> container;

  private final JComboBox<CaptureScope> scopeCombo;
  private final JComboBox<Integer> sizeCombo = new JComboBox<>(SIZE_RATIOS);
  private final JCheckBox imageOnlyCheckBox =
      new JCheckBox(Messages.getString("capture.image.only"));
  private final JComboBox<FrameSinkFactory> formatCombo;
  private final JSpinner rateSpinner = new JSpinner();
  private final MaskingProfileSelector maskingProfile =
      MaskingProfileSelector.withDefault(MaskingProfile.DISPLAY_ID);
  private final DisplayProfileSelector displayProfile;
  private final JLabel frameSizeLabel = new JLabel();
  private final JLabel budgetLabel = new JLabel();
  private final JTextArea adviceArea = createAdviceArea();

  public AnimationExportDialog(
      Window parent,
      FrameSourceBuilder builder,
      ViewCanvas<?> view,
      ImageViewerPlugin<?> container,
      List<FrameSinkFactory> sinks) {
    super(parent, builder.title(), ModalityType.APPLICATION_MODAL);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    this.builder = Objects.requireNonNull(builder);
    this.view = view;
    this.container = container;
    this.scopeCombo = new JComboBox<>(availableScopes(view, container));
    this.formatCombo = new JComboBox<>(sinks.toArray(new FrameSinkFactory[0]));
    this.displayProfile = new DisplayProfileSelector(() -> view);
    initComponents();
    pack();
  }

  private static CaptureScope[] availableScopes(
      ViewCanvas<?> view, ImageViewerPlugin<?> container) {
    List<CaptureScope> scopes = new ArrayList<>();
    if (view != null) {
      scopes.add(CaptureScope.VIEW);
    }
    if (container != null) {
      scopes.add(CaptureScope.CONTAINER);
    }
    scopes.add(CaptureScope.APPLICATION_WINDOW);
    return scopes.toArray(new CaptureScope[0]);
  }

  private void initComponents() {
    scopeCombo.setSelectedItem(builder.defaultScope());
    if (scopeCombo.getSelectedItem() == null) {
      scopeCombo.setSelectedIndex(0);
    }
    scopeCombo.addActionListener(_ -> updateProjection());
    sizeCombo.setSelectedItem(builder.defaultSizeRatio());
    sizeCombo.addActionListener(_ -> updateProjection());
    imageOnlyCheckBox.setVisible(view instanceof DefaultView2d<?>);
    imageOnlyCheckBox.setToolTipText(Messages.getString("capture.image.only.tip"));
    imageOnlyCheckBox.addActionListener(_ -> updateProjection());

    rateSpinner.setModel(new SpinnerNumberModel(builder.defaultFrameRate(), 1.0, 60.0, 1.0));
    rateSpinner.getModel().addChangeListener(_ -> updateProjection());

    formatCombo.setRenderer(
        new DefaultListCellRenderer() {
          @Override
          public Component getListCellRendererComponent(
              JList<?> list, Object value, int index, boolean selected, boolean focus) {
            Object label = value instanceof FrameSinkFactory f ? f.title() : value;
            return super.getListCellRendererComponent(list, label, index, selected, focus);
          }
        });
    selectPreferredFormat();
    formatCombo.addActionListener(_ -> updateProjection());

    // The widest value the label can take, so the row keeps its width when the size changes
    frameSizeLabel.setText("(9999\u00d79999 px)"); // NON-NLS
    frameSizeLabel.setPreferredSize(frameSizeLabel.getPreferredSize());
    frameSizeLabel.setHorizontalAlignment(JLabel.LEADING);

    AnimationForm form = new AnimationForm();
    builder.addOptions(form, this::updateProjection);
    form.row(Messages.getString("capture.scope"), scopeCombo, imageOnlyCheckBox);
    if (DisplayProfile.supports(view)) {
      form.row(
          Messages.getString("capture.display"), displayProfile, displayProfile.getEditButton());
    }
    form.row(Messages.getString("size"), sizeCombo, new JLabel("%"), frameSizeLabel);
    form.row(
        Messages.getString("animation.rate"),
        rateSpinner,
        new JLabel(Messages.getString("animation.frames.per.second")));
    form.row(Messages.getString("masking.profile"), maskingProfile);
    form.row(Messages.getString("animation.format"), formatCombo);
    form.span(adviceArea);
    form.span(budgetLabel);
    form.span(
        MaskingProfileSelector.createReviewWarning(
            view == null ? null : view.getImage(), view == null ? null : view.getSeries()));

    JButton exportButton = new JButton(Messages.getString("animation.export"));
    exportButton.addActionListener(_ -> export());
    JButton closeButton = new JButton(Messages.getString("cancel"));
    closeButton.addActionListener(_ -> dispose());
    getRootPane().setDefaultButton(exportButton);

    JPanel buttons =
        GuiUtils.getFlowLayoutPanel(
            FlowLayout.TRAILING, BLOCK_SEPARATOR, ITEM_SEPARATOR_LARGE, exportButton, closeButton);
    form.getPanel().setAlignmentX(Component.LEFT_ALIGNMENT);
    buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(GuiUtils.getEmptyBorder(BLOCK_SEPARATOR));
    panel.add(form.getPanel());
    panel.add(buttons);
    panel.add(GuiUtils.boxYLastElement(ITEM_SEPARATOR));
    setContentPane(panel);
    updateProjection();
  }

  /** A fixed two-line area, so a longer advice never moves the rest of the dialog. */
  private static JTextArea createAdviceArea() {
    JTextArea area = new JTextArea(2, 36);
    area.setEditable(false);
    area.setFocusable(false);
    area.setOpaque(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setFont(UIManager.getFont("Label.font")); // NON-NLS
    area.setForeground(UIManager.getColor("Label.disabledForeground")); // NON-NLS
    area.setBorder(null);
    return area;
  }

  private void selectPreferredFormat() {
    String preferred = builder.preferredFormat().getExtension();
    for (int i = 0; i < formatCombo.getItemCount(); i++) {
      if (preferred.equals(formatCombo.getItemAt(i).extension())) {
        formatCombo.setSelectedIndex(i);
        return;
      }
    }
  }

  private CaptureScope getScope() {
    return (CaptureScope) scopeCombo.getSelectedItem();
  }

  private FrameSinkFactory getSinkFactory() {
    return (FrameSinkFactory) formatCombo.getSelectedItem();
  }

  private double getScale() {
    return ((Integer) sizeCombo.getSelectedItem()) / 100.0;
  }

  private int getFrameDurationMs() {
    double rate = ((Number) rateSpinner.getValue()).doubleValue();
    return (int) Math.round(1000.0 / rate);
  }

  private FrameGrabber createGrabber() {
    FrameGrabber grabber =
        new FrameGrabber(
            getScope(), view, container, maskingProfile.getSelectedProfile(), getScale());
    grabber.setImageOnly(imageOnlyCheckBox.isSelected());
    if (DisplayProfile.supports(view)) {
      grabber.setDisplayProfile(displayProfile.getSelection());
    }
    return grabber;
  }

  private void updateProjection() {
    FrameGrabber grabber = createGrabber();
    imageOnlyCheckBox.setEnabled(grabber.isRenderedOffScreen());
    displayProfile.setEnabled(grabber.supportsDisplayProfile());
    Dimension size = grabber.getFrameSize();
    frameSizeLabel.setText(String.format("(%d×%d px)", size.width, size.height)); // NON-NLS
    FrameSinkFactory factory = getSinkFactory();
    adviceArea.setText(factory == null ? StringUtil.EMPTY_STRING : factory.advice());
    if (factory == null || !factory.buffersRawFrames()) {
      budgetLabel.setText(Messages.getString("animation.streamed"));
      return;
    }
    long frameBytes = (long) size.width * size.height * 3L;
    int frames = Math.max(1, builder.frameCount(getFrameDurationMs()));
    long bytes = frameBytes * frames;
    if (bytes <= AnimatedImageSink.MAX_RESIDENT_BYTES) {
      budgetLabel.setText(
          String.format(
              Messages.getString("animation.memory.projection"),
              FileUtil.humanReadableByte(bytes, false)));
      return;
    }
    int capFrames =
        (int)
            Math.min(
                AnimatedImageSink.MAX_FRAMES, AnimatedImageSink.MAX_RESIDENT_BYTES / frameBytes);
    budgetLabel.setText(
        String.format(
            Messages.getString("animation.memory.cap"),
            FileUtil.humanReadableByte(AnimatedImageSink.MAX_RESIDENT_BYTES, false),
            capFrames,
            Math.round(capFrames * getFrameDurationMs() / 1000.0)));
  }

  private void export() {
    FrameSinkFactory factory = getSinkFactory();
    if (factory == null) {
      return;
    }
    Path file = chooseFile(factory);
    if (file == null) {
      return;
    }
    FrameSink sink;
    try {
      sink = factory.create(file, maskingProfile.getSelectedProfile());
    } catch (IOException e) {
      LOGGER.error("Cannot create the animation file", e);
      JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
      return;
    }
    FrameGrabber grabber = createGrabber();
    grabber.prepare();
    LOGGER.info(
        "Animation export: {} at {} scope, {}x{}, to {}",
        builder.title(),
        grabber.getScope(),
        grabber.getFrameSize().width,
        grabber.getFrameSize().height,
        file);
    Window parent = getOwner();
    dispose();
    builder.run(parent, grabber, getFrameDurationMs(), sink);
  }

  private Path chooseFile(FrameSinkFactory factory) {
    WProperties localPersistence = GuiUtils.getUICore().getLocalPersistence();
    SystemFileChooser fileChooser =
        new SystemFileChooser(localPersistence.getProperty(P_LAST_DIR, ""));
    fileChooser.setFileSelectionMode(SystemFileChooser.FILES_ONLY);
    fileChooser.setAcceptAllFileFilterUsed(false);
    String extension = factory.extension();
    fileChooser.addChoosableFileFilter(new FileNameExtensionFilter(factory.title(), extension));
    fileChooser.setSelectedFile(new File(builder.defaultFileName() + "." + extension));
    if (fileChooser.showSaveDialog(this) != SystemFileChooser.APPROVE_OPTION
        || fileChooser.getSelectedFile() == null) {
      return null;
    }
    File file = fileChooser.getSelectedFile();
    String suffix = "." + extension;
    Path path = Path.of(file.getName().endsWith(suffix) ? file.getPath() : file.getPath() + suffix);
    if (path.getParent() != null) {
      localPersistence.setProperty(P_LAST_DIR, path.getParent().toString());
    }
    return path;
  }

  public static void showDialog(
      FrameSourceBuilder builder,
      ViewCanvas<?> view,
      ImageViewerPlugin<?> container,
      List<FrameSinkFactory> sinks) {
    Component anchor = container != null ? container : view.getJComponent();
    ColorLayerUI layer = ColorLayerUI.createTransparentLayerUI(anchor);
    AnimationExportDialog dialog =
        new AnimationExportDialog(
            SwingUtilities.getWindowAncestor(anchor), builder, view, container, sinks);
    ColorLayerUI.showCenterScreen(dialog, layer);
  }
}
