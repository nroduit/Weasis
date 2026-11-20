/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.SystemFileChooser;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionListener;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.TooManyListenersException;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.WinUtil;
import org.weasis.core.api.gui.util.WrapLayout;
import org.weasis.core.api.net.URIUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.api.util.ResourceUtil.FileIcon;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.editor.image.SequenceHandler;
import org.weasis.core.util.LangUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.LocalPersistence;
import org.weasis.dicom.explorer.Messages;

/**
 * The import from the local device in one page. Files and folders of any kind (DICOM, ZIP,
 * DICOMDIR), mixed, are dropped on the page or picked in the native Files and Folder dialogs, and
 * are imported at once. The path field keeps the recent sources: a typed or recalled path is
 * imported with Enter or the Import button of the tool.
 *
 * <p>The native dialogs of Windows and Linux cannot pick files and folders together (FlatLaf maps
 * {@code FILES_AND_DIRECTORIES} to {@code FILES_ONLY} there), so a mixed set goes through the drop.
 */
public class LocalImport extends AbstractItemDialogPage implements ImportDicom {
  private static final Logger LOGGER = LoggerFactory.getLogger(LocalImport.class);

  public static final String LAST_RECURSIVE_MODE = "last.recursive.mode";
  public static final String LAST_IMPORT_PATH = "last.import.path";

  /** The recent sources, most recent first, one per line. */
  public static final String RECENT_IMPORT_PATHS = "last.import.paths";

  /** The folder of the last import, also the folder the export placeholders point to. */
  public static final String LAST_OPEN_DIR = "lastOpenDir";

  static final int MAX_RECENT = 8;

  private final DicomModel model;
  private final JComboBox<String> pathCombo = new JComboBox<>();
  private final JCheckBox recursive = new JCheckBox(Messages.getString("LocalImport.recursive"));

  public LocalImport(DicomModel model) {
    super(Messages.getString("LocalImport.local_dev"), 0);
    this.model = model;
    initGUI();
  }

  private void initGUI() {
    Properties props = LocalPersistence.getProperties();
    recursive.setSelected(LangUtil.emptyToTrue(props.getProperty(LAST_RECURSIVE_MODE)));
    recursive.addActionListener(
        _ ->
            LocalPersistence.getProperties()
                .setProperty(LAST_RECURSIVE_MODE, String.valueOf(recursive.isSelected())));

    pathCombo.setEditable(true);
    recentPaths(props).forEach(pathCombo::addItem);
    pathCombo.setSelectedItem(pathCombo.getItemCount() > 0 ? pathCombo.getItemAt(0) : "");
    pathCombo.putClientProperty(
        FlatClientProperties.PLACEHOLDER_TEXT, Messages.getString("LocalImport.path_hint"));
    pathCombo.setToolTipText(Messages.getString("LocalImport.path_tip"));
    pathCombo.addActionListener(
        e -> {
          if ("comboBoxEdited".equals(e.getActionCommand())) { // NON-NLS
            importDICOM(model, null);
          }
        });
    // the field fills its row: the page is not as wide as the path it holds
    GuiUtils.setPreferredWidth(pathCombo, 150, GuiUtils.MIN_SHRUNK_COMBO_WIDTH);

    JLabel label = new JLabel(Messages.getString("LocalImport.path") + StringUtil.COLON);
    label.setLabelFor(pathCombo);
    JPanel options =
        GuiUtils.getVerticalBoxLayoutPanel(
            GuiUtils.boxVerticalStrut(ITEM_SEPARATOR),
            GuiUtils.getHorizontalBoxLayoutPanel(
                label, GuiUtils.boxHorizontalStrut(ITEM_SEPARATOR_SMALL), pathCombo),
            GuiUtils.boxVerticalStrut(ITEM_SEPARATOR_SMALL),
            GuiUtils.getFlowLayoutPanel(0, 0, GuiUtils.shrinkable(recursive)));

    // the zone takes the free height, so the path stays next to the Import button of the tool
    DropZone dropZone = new DropZone();
    setLayout(new BorderLayout());
    add(dropZone, BorderLayout.CENTER);
    add(options, BorderLayout.SOUTH);

    // The whole page takes drops, the zone shows them
    setTransferHandler(new FileDropHandler());
    try {
      getDropTarget().addDropTargetListener(dropZone);
    } catch (TooManyListenersException e) {
      LOGGER.debug("No drop highlight", e);
    }
  }

  /**
   * The dashed zone of the page: drop target, Files, Folder and disc buttons. Its width follows the
   * title alone: the text under it and the row of buttons wrap, and the whole is centered in the
   * height the page leaves.
   */
  private final class DropZone extends JPanel implements DropTargetListener {
    private final JLabel title;
    private final JLabel kinds;
    private final JPanel buttons;
    private boolean dragOver;

    DropZone() {
      super(null);
      setOpaque(false);
      setBorder(GuiUtils.getEmptyBorder(ITEM_SEPARATOR, ITEM_SEPARATOR_SMALL));

      title =
          new JLabel(
              Messages.getString("LocalImport.drop"),
              ResourceUtil.getIcon(ActionIcon.IMPORT_DICOM),
              JLabel.CENTER);
      title.setIconTextGap(GuiUtils.getScaleLength(ITEM_SEPARATOR_SMALL));
      title.putClientProperty(FlatClientProperties.STYLE_CLASS, "h4"); // NON-NLS
      kinds =
          new JLabel(
              "<html><div style='text-align:center'>" // NON-NLS
                  + Messages.getString("LocalImport.drop_kinds")
                  + "</div></html>", // NON-NLS
              JLabel.CENTER);
      kinds.putClientProperty(FlatClientProperties.STYLE_CLASS, "small"); // NON-NLS
      kinds.setForeground(UIManager.getColor("Label.disabledForeground"));

      JButton disc =
          button(
              Messages.getString("LocalImport.media"),
              ResourceUtil.getIcon(OtherIcon.CDROM),
              _ -> importDisc());
      disc.setToolTipText(Messages.getString("LocalImport.media_tip"));
      buttons =
          new JPanel(new WrapLayout(FlowLayout.CENTER, ITEM_SEPARATOR_SMALL, ITEM_SEPARATOR_SMALL));
      buttons.setOpaque(false);
      buttons.add(
          button(
              Messages.getString("LocalImport.browse_files"),
              ResourceUtil.getIcon(FileIcon.DICOM),
              _ -> browse(SystemFileChooser.FILES_ONLY)));
      buttons.add(
          button(
              Messages.getString("LocalImport.browse_folder"),
              ResourceUtil.getIcon(OtherIcon.FOLDER),
              _ -> browse(SystemFileChooser.DIRECTORIES_ONLY)));
      buttons.add(disc);

      add(title);
      add(kinds);
      add(buttons);
    }

    private static JButton button(String text, Icon icon, ActionListener action) {
      JButton button = new JButton(text, icon);
      button.addActionListener(action);
      return button;
    }

    /** The height of the wrapped text for that width. */
    private int kindsHeight(int width) {
      if (kinds.getClientProperty(BasicHTML.propertyKey) instanceof View view) {
        view.setSize(width, 0);
        return (int) Math.ceil(view.getPreferredSpan(View.Y_AXIS));
      }
      return kinds.getPreferredSize().height;
    }

    /** The height of the title, the text and the rows of buttons, the last two wrapped. */
    private int contentHeight(int width) {
      return title.getPreferredSize().height
          + kindsHeight(width)
          + buttons.getLayout().preferredLayoutSize(buttons).height;
    }

    @Override
    public Dimension getPreferredSize() {
      Insets insets = getInsets();
      int sides = insets.left + insets.right;
      int width = title.getPreferredSize().width + sides;
      int inner = Math.max(getWidth(), width) - sides;
      return new Dimension(width, contentHeight(inner) + insets.top + insets.bottom);
    }

    /** Narrow enough to follow the side: the texts are cut, the buttons wrap. */
    @Override
    public Dimension getMinimumSize() {
      return new Dimension(GuiUtils.getScaleLength(120), getPreferredSize().height);
    }

    @Override
    public void doLayout() {
      Insets insets = getInsets();
      int width = getWidth() - insets.left - insets.right;
      // the rows of buttons depend on the width they get
      buttons.setSize(width, buttons.getHeight());
      int content = contentHeight(width);
      int y = insets.top + Math.max(0, (getHeight() - insets.top - insets.bottom - content) / 2);
      int[] heights = {
        title.getPreferredSize().height,
        kindsHeight(width),
        buttons.getLayout().preferredLayoutSize(buttons).height
      };
      for (int i = 0; i < heights.length; i++) {
        getComponent(i).setBounds(insets.left, y, width, heights[i]);
        y += heights[i];
      }
      if (content + insets.top + insets.bottom > getHeight()) {
        // wrapped on more rows than the page planned: the page has to grow, and scroll if needed
        SwingUtilities.invokeLater(this::revalidate);
      }
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        float line = GuiUtils.getScaleLength(1.5f);
        float arc = GuiUtils.getScaleLength(Math.max(10f, UIManager.getInt("Component.arc")));
        RoundRectangle2D shape =
            new RoundRectangle2D.Float(
                line / 2, line / 2, getWidth() - line, getHeight() - line, arc, arc);
        Color accent = UIManager.getColor("Component.focusColor");
        if (dragOver && accent != null) {
          g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 48));
          g2.fill(shape);
        }
        Color border = dragOver ? accent : UIManager.getColor("Component.borderColor");
        g2.setColor(border != null ? border : getForeground());
        float dash = GuiUtils.getScaleLength(5f);
        g2.setStroke(
            new BasicStroke(
                line, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND, 10f, new float[] {dash}, 0f));
        g2.draw(shape);
      } finally {
        g2.dispose();
      }
    }

    private void setDragOver(boolean over) {
      if (dragOver != over) {
        dragOver = over;
        repaint();
      }
    }

    @Override
    public void dragEnter(DropTargetDragEvent e) {
      setDragOver(true);
    }

    @Override
    public void dragOver(DropTargetDragEvent e) {
      // the state of the enter holds
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent e) {
      // nothing to change
    }

    @Override
    public void dragExit(DropTargetEvent e) {
      setDragOver(false);
    }

    @Override
    public void drop(DropTargetDropEvent e) {
      setDragOver(false);
    }
  }

  /** Takes dropped files and folders, of any kind and mixed. */
  private final class FileDropHandler extends SequenceHandler {
    FileDropHandler() {
      super(false, true);
    }

    @Override
    protected boolean dropFiles(List<Path> paths) {
      if (paths == null || paths.isEmpty()) {
        return false;
      }
      importSources(paths.stream().map(Path::toFile).toList());
      return true;
    }
  }

  private void browse(int selectionMode) {
    SystemFileChooser chooser = new SystemFileChooser(initialDirectory());
    chooser.setFileSelectionMode(selectionMode);
    chooser.setMultiSelectionEnabled(true);
    if (chooser.showOpenDialog(WinUtil.getParentWindow(this)) == SystemFileChooser.APPROVE_OPTION) {
      File[] selected = chooser.getSelectedFiles();
      if (selected != null && selected.length > 0) {
        importSources(Arrays.asList(selected));
      }
    }
  }

  private void importDisc() {
    File dicomdir = DicomDirImport.getDcmDirFromMedia();
    if (dicomdir == null) {
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          Messages.getString("LocalImport.no_media"),
          getTitle(),
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    importSources(List.of(dicomdir));
  }

  /** Imports the sources at once and remembers them. */
  private void importSources(List<File> files) {
    if (files.isEmpty() || model == null) {
      return;
    }
    remember(files);
    DicomDirImport.importFiles(files, model, recursive.isSelected(), this::forget);
  }

  private String initialDirectory() {
    File typed = typedFile();
    if (typed != null && typed.exists()) {
      return directoryOf(typed);
    }
    return LocalPersistence.getProperties().getProperty(LAST_OPEN_DIR, "");
  }

  private static String directoryOf(File file) {
    if (file.isFile()) {
      File parent = file.getParentFile();
      return parent != null ? parent.getPath() : file.getPath();
    }
    return file.getPath();
  }

  /**
   * Keeps the sources worth recalling in the path history: folders, DICOMDIR and a single file. The
   * files of a multiple selection are not recalled one by one.
   */
  private void remember(List<File> files) {
    List<String> recalled = new ArrayList<>();
    for (File file : files) {
      if (files.size() == 1 || !file.isFile() || DicomDirImport.isDicomDir(file.toPath())) {
        recalled.add(file.getPath());
      }
    }
    Properties props = LocalPersistence.getProperties();
    props.setProperty(LAST_OPEN_DIR, directoryOf(files.getFirst()));
    if (recalled.isEmpty()) {
      return;
    }
    List<String> recent = mergeRecent(recalled, recentPaths(props));
    props.setProperty(RECENT_IMPORT_PATHS, String.join("\n", recent));
    props.setProperty(LAST_IMPORT_PATH, recent.getFirst());
    pathCombo.removeAllItems();
    recent.forEach(pathCombo::addItem);
    pathCombo.setSelectedItem(recent.getFirst());
  }

  /**
   * Takes out of the path history the sources in which nothing to import was found. The path stays
   * in the field, so it can be corrected.
   */
  private void forget(List<File> files) {
    Properties props = LocalPersistence.getProperties();
    List<String> known = recentPaths(props);
    List<String> recent = withoutPaths(known, files.stream().map(File::getPath).toList());
    if (recent.size() == known.size()) {
      return;
    }
    props.setProperty(RECENT_IMPORT_PATHS, String.join("\n", recent));
    props.setProperty(LAST_IMPORT_PATH, recent.isEmpty() ? "" : recent.getFirst());
    String typed = typedPath();
    pathCombo.removeAllItems();
    recent.forEach(pathCombo::addItem);
    pathCombo.setSelectedItem(typed);
  }

  /** The recent sources without the removed ones, in the same order. */
  static List<String> withoutPaths(List<String> recent, List<String> removed) {
    return recent.stream().filter(path -> !removed.contains(path)).toList();
  }

  /**
   * The new sources first, then the older ones, without duplicates or blanks, at most {@value
   * #MAX_RECENT}.
   */
  static List<String> mergeRecent(List<String> added, List<String> older) {
    List<String> merged = new ArrayList<>(MAX_RECENT);
    for (List<String> list : List.of(added, older)) {
      for (String path : list) {
        if (StringUtil.hasText(path) && !merged.contains(path) && merged.size() < MAX_RECENT) {
          merged.add(path);
        }
      }
    }
    return merged;
  }

  /** The recent sources, seeded with the single last path of the earlier versions. */
  static List<String> recentPaths(Properties props) {
    String recent = props.getProperty(RECENT_IMPORT_PATHS);
    if (recent == null) {
      recent = props.getProperty(LAST_IMPORT_PATH, "");
    }
    return mergeRecent(recent.lines().map(String::trim).toList(), List.of());
  }

  /** Fills the field with a path to import, as the CD import does with the folder of a medium. */
  public void setImportPath(String path) {
    pathCombo.setSelectedItem(path);
  }

  private String typedPath() {
    Object item =
        pathCombo.getEditor().getEditorComponent() instanceof JTextComponent text
            ? text.getText()
            : pathCombo.getSelectedItem();
    return item == null ? "" : item.toString().trim();
  }

  /** The typed path as a file, also when given as a file URI; null when the field is empty. */
  private File typedFile() {
    String path = typedPath();
    if (!StringUtil.hasText(path)) {
      return null;
    }
    File file = new File(path);
    if (file.exists() || !path.contains(":/")) {
      return file;
    }
    try {
      return URIUtils.toFile(new URI(path));
    } catch (Exception e) {
      LOGGER.debug("Not a file path nor a file URI: {}", path);
      return file;
    }
  }

  /** Imports the path of the field (Enter, or the Import button of the tool). */
  @Override
  public void importDICOM(DicomModel dicomModel, JProgressBar info) {
    File file = typedFile();
    if (file == null || !file.canRead()) {
      LOGGER.warn("Cannot import DICOM from {}", typedPath());
      JOptionPane.showMessageDialog(
          WinUtil.getValidComponent(this),
          file == null
              ? Messages.getString("LocalImport.no_path")
              : Messages.format("LocalImport.unreadable", file.getPath()),
          getTitle(),
          JOptionPane.WARNING_MESSAGE);
      return;
    }
    remember(List.of(file));
    DicomDirImport.importFiles(
        List.of(file),
        dicomModel != null ? dicomModel : model,
        recursive.isSelected(),
        this::forget);
  }

  @Override
  public void closeAdditionalWindow() {
    LocalPersistence.getProperties()
        .setProperty(LAST_RECURSIVE_MODE, String.valueOf(recursive.isSelected()));
  }

  @Override
  public void resetToDefaultValues() {
    // nothing to reset
  }
}
