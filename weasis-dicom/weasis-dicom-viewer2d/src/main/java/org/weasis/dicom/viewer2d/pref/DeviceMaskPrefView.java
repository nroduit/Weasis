/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.pref;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.AbstractTableModel;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.media.data.MaskRegion;
import org.weasis.core.api.media.data.MaskingModelRegistry;
import org.weasis.core.api.media.data.MaskingProfile;
import org.weasis.core.api.media.data.PixelMask;
import org.weasis.core.api.media.data.PixelMask.Reference;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.KarnakMasks;
import org.weasis.dicom.codec.PixelMaskMatcher;
import org.weasis.dicom.codec.PixelMaskMatcher.Candidate;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.Messages;

/**
 * Edits the device pixel masks: the regions burned over the images of one machine, proposed while a
 * masking profile is in force. Built-in and site entries are read-only; user entries can be
 * renamed, disabled, deleted, imported and exported, to Karnak among others.
 */
public class DeviceMaskPrefView extends AbstractItemDialogPage {
  private static final Logger LOGGER = LoggerFactory.getLogger(DeviceMaskPrefView.class);

  private final MaskingModelRegistry registry;
  private final MaskTableModel tableModel = new MaskTableModel();
  private final JTable table = new JTable(tableModel);
  private final RegionPreview preview = new RegionPreview();
  private final JLabel matchLabel = new JLabel();
  private final JLabel lockedLabel = new JLabel();
  private final JButton renameButton = new JButton(Messages.getString("DeviceMaskPrefView.rename"));
  private final JButton deleteButton = new JButton(Messages.getString("DeviceMaskPrefView.delete"));

  public DeviceMaskPrefView() {
    this(MaskingModelRegistry.getInstance());
  }

  DeviceMaskPrefView(MaskingModelRegistry registry) {
    super(Messages.getString("DeviceMaskPrefView.title"), 506);
    this.registry = registry;
    jbInit();
    fill();
  }

  private void jbInit() {
    setLayout(new MigLayout("ins 5lp, fillx, wrap 1, hidemode 3", "[0:pref,grow,fill]")); // NON-NLS
    lockedLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    add(lockedLabel);
    matchLabel.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
    add(matchLabel);

    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoCreateRowSorter(true);
    table.getSelectionModel().addListSelectionListener(e -> showSelected());
    add(new JScrollPane(table), "height 100lp:160lp:, grow, push"); // NON-NLS
    add(preview, "height 80lp:120lp:, grow"); // NON-NLS
    add(buildButtons());
  }

  private JPanel buildButtons() {
    JPanel panel = new JPanel(new MigLayout("ins 0, fillx", "[][][][]push[]")); // NON-NLS
    renameButton.addActionListener(e -> rename());
    deleteButton.addActionListener(e -> delete());
    JButton importButton =
        new JButton(Messages.getString("DeviceMaskPrefView.import") + StringUtil.Suffix.THREE_PTS);
    importButton.addActionListener(e -> importMasks());
    JButton exportButton =
        new JButton(Messages.getString("DeviceMaskPrefView.export") + StringUtil.Suffix.THREE_PTS);
    exportButton.addActionListener(e -> exportMasks());
    panel.add(renameButton);
    panel.add(deleteButton);
    panel.add(importButton);
    panel.add(exportButton);
    return panel;
  }

  private void fill() {
    tableModel.setMasks(registry.pixelMasks());
    boolean locked = registry.isLocked();
    lockedLabel.setVisible(locked);
    if (locked) {
      lockedLabel.setText(
          Messages.getString("DeviceMaskPrefView.locked").formatted(registry.siteLocation()));
    }
    updateMatch();
    showSelected();
  }

  /** Says which entry the series of the selected view uses, and why the others do not. */
  private void updateMatch() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    if (view == null || view.getImage() == null) {
      matchLabel.setVisible(false);
      return;
    }
    List<Candidate> candidates =
        PixelMaskMatcher.evaluate(
            view.getSeries(), view.getImage(), registry.sessionProfile().id());
    Optional<Candidate> applied = candidates.stream().filter(Candidate::applies).findFirst();
    matchLabel.setVisible(true);
    matchLabel.setText(
        applied
            .map(c -> Messages.getString("DeviceMaskPrefView.match").formatted(c.mask().name()))
            .orElseGet(() -> Messages.getString("DeviceMaskPrefView.match.none")));
    matchLabel.setToolTipText(
        candidates.stream()
            .filter(c -> !c.applies())
            .map(c -> c.mask().name() + ": " + c.reason().name().toLowerCase(Locale.ROOT))
            .reduce((a, b) -> a + "; " + b)
            .orElse(null));
  }

  private PixelMask selected() {
    int row = table.getSelectedRow();
    return row < 0 ? null : tableModel.mask(table.convertRowIndexToModel(row));
  }

  private boolean editable(PixelMask mask) {
    return mask != null
        && !registry.isLocked()
        && registry.maskOrigin(mask.id()) == MaskingModelRegistry.Origin.USER;
  }

  private void showSelected() {
    PixelMask mask = selected();
    preview.setMask(mask);
    renameButton.setEnabled(editable(mask));
    deleteButton.setEnabled(editable(mask));
  }

  private void rename() {
    PixelMask mask = selected();
    if (!editable(mask)) {
      return;
    }
    String name =
        JOptionPane.showInputDialog(
            this, Messages.getString("DeviceMaskPrefView.rename"), mask.name());
    if (StringUtil.hasText(name)) {
      save(
          new PixelMask(
              mask.id(),
              name.trim(),
              mask.match(),
              mask.reference(),
              mask.regions(),
              mask.profiles(),
              mask.enabled()));
    }
  }

  private void delete() {
    PixelMask mask = selected();
    if (!editable(mask)
        || JOptionPane.showConfirmDialog(
                this,
                Messages.getString("DeviceMaskPrefView.delete.q").formatted(mask.name()),
                getTitle(),
                JOptionPane.YES_NO_OPTION)
            != JOptionPane.YES_OPTION) {
      return;
    }
    try {
      registry.deleteUserMask(mask.id());
      fill();
    } catch (IOException | RuntimeException e) {
      error(e, "Cannot delete the device mask");
    }
  }

  private void save(PixelMask mask) {
    try {
      registry.saveUserMask(mask);
      fill();
    } catch (IOException | RuntimeException e) {
      error(e, "Cannot save the device mask");
    }
  }

  private void importMasks() {
    if (registry.isLocked()) {
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setFileFilter(
        new FileNameExtensionFilter(
            Messages.getString("DeviceMaskPrefView.files"), "json", "yml", "yaml")); // NON-NLS
    if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    try {
      List<PixelMask> masks =
          isYaml(file)
              ? KarnakMasks.read(file, askReference())
              : MaskingModelRegistry.readImport(file).masks();
      if (masks.isEmpty()) {
        throw new IOException("No device mask in " + file);
      }
      for (PixelMask mask : masks) {
        registry.saveUserMask(mask);
      }
      fill();
    } catch (IOException | RuntimeException e) {
      error(e, "Cannot import device masks");
    }
  }

  /**
   * A Karnak rectangle is absolute: without the image size of the entry, the frame it was drawn on
   * has to be named, and the open image is the best guess to offer.
   */
  private Reference askReference() {
    Reference current = currentSize();
    String answer =
        JOptionPane.showInputDialog(
            this,
            Messages.getString("DeviceMaskPrefView.reference.q"),
            current.columns() + "x" + current.rows()); // NON-NLS
    if (StringUtil.hasText(answer)) {
      String[] parts = answer.toLowerCase(Locale.ROOT).split("x");
      try {
        if (parts.length == 2) {
          return new Reference(
              Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()));
        }
      } catch (NumberFormatException e) {
        LOGGER.warn("Not an image size: {}", answer);
      }
    }
    return current;
  }

  private Reference currentSize() {
    ViewCanvas<DicomImageElement> view = EventManager.getInstance().getSelectedViewPane();
    Integer columns =
        view == null ? null : TagD.getTagValue(view.getImage(), Tag.Columns, Integer.class);
    Integer rows = view == null ? null : TagD.getTagValue(view.getImage(), Tag.Rows, Integer.class);
    return columns != null && rows != null && columns > 0 && rows > 0
        ? new Reference(columns, rows)
        : new Reference(1024, 768);
  }

  private void exportMasks() {
    int format =
        JOptionPane.showOptionDialog(
            this,
            Messages.getString("DeviceMaskPrefView.export.q"),
            getTitle(),
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            new Object[] {
              Messages.getString("DeviceMaskPrefView.export.weasis"),
              Messages.getString("DeviceMaskPrefView.export.karnak")
            },
            null);
    if (format < 0) {
      return;
    }
    boolean karnak = format == 1;
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File(karnak ? "karnak-masks.yml" : "deviceMasks.json")); // NON-NLS
    if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    Path file = chooser.getSelectedFile().toPath();
    try {
      if (karnak) {
        MaskingProfile profile = registry.sessionProfile();
        List<KarnakMasks.Loss> losses = new ArrayList<>();
        KarnakMasks.write(file, registry.pixelMasks(), profile, KarnakMasks.BLACK, true, losses);
        showLosses(profile, losses);
      } else {
        registry.userModel().write(file);
      }
    } catch (IOException | RuntimeException e) {
      error(e, "Cannot export device masks");
    }
  }

  private void showLosses(MaskingProfile profile, List<KarnakMasks.Loss> losses) {
    StringBuilder text =
        new StringBuilder(
            Messages.getString("DeviceMaskPrefView.export.done").formatted(profile.name()));
    for (KarnakMasks.Loss loss : losses) {
      text.append(System.lineSeparator())
          .append("- ")
          .append(loss.maskId() == null ? "" : loss.maskId() + ": ")
          .append(loss.message());
    }
    JOptionPane.showMessageDialog(
        this, text.toString(), getTitle(), JOptionPane.INFORMATION_MESSAGE);
  }

  private void error(Exception e, String message) {
    LOGGER.error(message, e);
    JOptionPane.showMessageDialog(this, e.getMessage(), getTitle(), JOptionPane.ERROR_MESSAGE);
  }

  private static boolean isYaml(Path file) {
    String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
    return name.endsWith(".yml") || name.endsWith(".yaml"); // NON-NLS
  }

  @Override
  public void closeAdditionalWindow() {
    // Every change is written when it is made
  }

  @Override
  public void resetToDefaultValues() {
    fill();
  }

  /** Draws the regions on the frame they were normalized against. */
  private static class RegionPreview extends JPanel {
    private transient PixelMask mask;

    RegionPreview() {
      setPreferredSize(new Dimension(200, 120));
    }

    void setMask(PixelMask mask) {
      this.mask = mask;
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (mask == null) {
        return;
      }
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      double ratio = mask.reference().ratio();
      int height = Math.min(getHeight() - 8, (int) ((getWidth() - 8) / ratio));
      int width = (int) (height * ratio);
      int x = (getWidth() - width) / 2;
      int y = (getHeight() - height) / 2;
      g2.setColor(UIManager.getColor("TextField.background")); // NON-NLS
      g2.fillRect(x, y, width, height);
      g2.setColor(UIManager.getColor("Component.borderColor")); // NON-NLS
      g2.drawRect(x, y, width, height);
      g2.translate(x, y);
      Color color = regionColor();
      for (MaskRegion region : mask.regions()) {
        Shape shape = region.toShape(width, height);
        g2.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 96));
        g2.fill(shape);
        g2.setColor(color);
        g2.draw(shape);
      }
      g2.dispose();
    }

    /**
     * The accent of the theme, not black: the preview shows where the regions are, while what is
     * burned over the pixels is the color of their surroundings. A region too thin to be filled
     * still shows its outline.
     */
    private static Color regionColor() {
      Color accent = UIManager.getColor("Component.accentColor"); // NON-NLS
      return accent == null ? new Color(0x389fd6) : accent;
    }
  }

  /** Rows of the merged library, user entries editable. */
  private class MaskTableModel extends AbstractTableModel {
    private transient List<PixelMask> masks = List.of();

    void setMasks(List<PixelMask> masks) {
      this.masks = masks;
      fireTableDataChanged();
    }

    PixelMask mask(int index) {
      return index >= 0 && index < masks.size() ? masks.get(index) : null;
    }

    @Override
    public int getRowCount() {
      return masks.size();
    }

    @Override
    public int getColumnCount() {
      return 5;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> Messages.getString("DeviceMaskPrefView.column.name");
        case 1 -> Messages.getString("DeviceMaskPrefView.column.device");
        case 2 -> Messages.getString("DeviceMaskPrefView.column.regions");
        case 3 -> Messages.getString("DeviceMaskPrefView.column.source");
        default -> Messages.getString("DeviceMaskPrefView.column.enabled");
      };
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return switch (column) {
        case 2 -> Integer.class;
        case 4 -> Boolean.class;
        default -> String.class;
      };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == 4 && editable(masks.get(row));
    }

    @Override
    public Object getValueAt(int row, int column) {
      PixelMask mask = masks.get(row);
      return switch (column) {
        case 0 -> mask.name();
        case 1 -> device(mask);
        case 2 -> mask.regions().size();
        case 3 -> registry.maskOrigin(mask.id()).name().toLowerCase(Locale.ROOT);
        default -> mask.enabled();
      };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (column == 4 && value instanceof Boolean enabled) {
        save(masks.get(row).withEnabled(enabled));
      }
    }

    private String device(PixelMask mask) {
      List<String> parts = mask.match().values();
      return parts.isEmpty()
          ? Messages.getString("DeviceMaskPrefView.any")
          : String.join(" / ", parts);
    }
  }
}
