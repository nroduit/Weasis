/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import com.formdev.flatlaf.ui.FlatUIUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.img.DicomMetaData;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.util.RotatedIcon;
import org.weasis.core.ui.util.TableColumnAdjuster;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.wado.DicomManager;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(DicomFieldsView.class);

  private final JScrollPane allPane = new JScrollPane();
  private final JScrollPane limitedPane = new JScrollPane();
  private final JTextPane jTextPaneLimited = new JTextPane();
  private MediaElement currentMedia;
  private MediaSeries<?> currentSeries;
  private boolean anonymize = false;
  private final SeriesViewer<?> viewer;

  public static final String[] columns = {"Tag ID", "VR", "Tag Name", "Value"};
  private final DefaultTableModel model =
      new DefaultTableModel(columns, 0) {
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };

  private final JTable jtable = new JTable(model);

  private static final Highlighter.HighlightPainter searchHighlightPainter =
      new SearchHighlightPainter(new Color(255, 125, 0));
  private static final Highlighter.HighlightPainter searchResultHighlightPainter =
      new SearchHighlightPainter(Color.YELLOW);

  public DicomFieldsView(SeriesViewer<?> viewer) {
    this.viewer = viewer;
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    addTab(Messages.getString("DicomFieldsView.limited"), null, panel, null);
    panel.add(new SearchPanel(jTextPaneLimited), BorderLayout.NORTH);
    panel.add(limitedPane, BorderLayout.CENTER);
    jTextPaneLimited.setBorder(new EmptyBorder(5, 5, 5, 5));
    jTextPaneLimited.setContentType("text/html");
    jTextPaneLimited.setEditable(false);

    JPanel dump = new JPanel();
    dump.setLayout(new BorderLayout());
    dump.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    addTab(Messages.getString("DicomFieldsView.all"), null, dump, null);
    // dump.add(new SearchPanel(jTextPaneAll), BorderLayout.NORTH);
    jtable.getTableHeader().setReorderingAllowed(false);
    jtable.setShowHorizontalLines(true);
    jtable.setShowVerticalLines(true);
    dump.add(allPane, BorderLayout.CENTER);

    setPreferredSize(new Dimension(400, 300));
    setMinimumSize(new Dimension(150, 50));

    this.addChangeListener(changeEvent -> changeDicomInfo(currentSeries, currentMedia));
  }

  @Override
  public void changingViewContentEvent(SeriesViewerEvent event) {
    EVENT type = event.getEventType();
    if (event.getSeriesViewer() == viewer
        && (EVENT.SELECT.equals(type) || EVENT.LAYOUT.equals(type) || EVENT.ANONYM.equals(type))) {
      currentMedia = event.getMediaElement();
      currentSeries = event.getSeries();
      if (event.getSeriesViewer() instanceof ImageViewerPlugin) {
        ViewCanvas<?> sel = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
        if (sel != null) {
          anonymize = sel.getInfoLayer().getDisplayPreferences(LayerAnnotation.ANONYM_ANNOTATIONS);
        }
      }
      changeDicomInfo(currentSeries, currentMedia);
    }
  }

  private void changeDicomInfo(MediaSeries<?> series, MediaElement media) {
    int index = getSelectedIndex();
    if (index == 0) {
      jTextPaneLimited.requestFocusInWindow();
      displayLimitedDicomInfo(series, media);
    } else {
      allPane.requestFocusInWindow();
      displayAllDicomInfo(series, media);
    }
  }

  private static void applyFormatting(StyledDocument doc) {
    Color color = FlatUIUtils.getUIColor("TextArea.foreground", Color.DARK_GRAY);
    Style bold = doc.addStyle("bold", doc.getStyle(StyleContext.DEFAULT_STYLE));
    StyleConstants.setBold(bold, true);
    StyleConstants.setForeground(bold, color);

    bold = doc.addStyle("h3", bold);
    Font font = javax.swing.UIManager.getFont("h3.font");
    StyleConstants.setFontSize(bold, font == null ? 16 : font.getSize());
  }

  private void displayAllDicomInfo(MediaSeries<?> series, MediaElement media) {
    model.setRowCount(0);
    if (media != null) {
      MediaReader loader = media.getMediaReader();
      if (loader instanceof DicomMediaIO dicomMediaIO) {
        DicomMetaData metaData = dicomMediaIO.getDicomMetaData();
        if (metaData != null) {
          printAttribute(model, metaData.getFileMetaInformation());
          printAttribute(model, metaData.getDicomObject());
        }
      } else if (loader instanceof DcmMediaReader reader) {
        printAttribute(model, reader.getDicomObject());
      }
    }
    jtable.getColumnModel().getColumn(0).setPreferredWidth(100);
    jtable.getColumnModel().getColumn(1).setPreferredWidth(30);
    jtable.getColumnModel().getColumn(2).setPreferredWidth(250);
    jtable.getColumnModel().getColumn(3).setPreferredWidth(300);
    int height =
        (jtable.getRowHeight() + jtable.getRowMargin()) * jtable.getRowCount()
            + jtable.getTableHeader().getHeight()
            + 5;
    JPanel tableContainer = new JPanel();
    tableContainer.setLayout(new BorderLayout());
    tableContainer.setPreferredSize(
        new Dimension(jtable.getColumnModel().getTotalColumnWidth(), height));
    tableContainer.add(jtable.getTableHeader(), BorderLayout.PAGE_START);
    tableContainer.add(jtable, BorderLayout.CENTER);
    TableColumnAdjuster.pack(jtable);
    allPane.setViewportView(tableContainer);
    tableContainer.revalidate();
    tableContainer.repaint();
  }

  private static void printAttribute(DefaultTableModel model, Attributes dcmObj) {
    if (dcmObj != null) {
      int[] tags = dcmObj.tags();
      for (int tag : tags) {
        printElement(model, dcmObj, tag);
      }
    }
  }

  private static void printElement(DefaultTableModel model, Attributes dcmObj, int tag) {
    String privateCreator = dcmObj.privateCreatorOf(tag);
    int level = dcmObj.getLevel();
    VR.Holder holder = new VR.Holder();
    Object val = dcmObj.getValue(tag, holder);

    Object[] values = new Object[4];
    values[0] = getPrefixTag(level) + TagUtils.toString(tag);
    values[1] = holder.vr;

    String word = ElementDictionary.keywordOf(tag, privateCreator);
    if (!StringUtil.hasText(word)) {
      word = "PrivateTag";
    }
    values[2] = word;

    Sequence seq = dcmObj.getSequence(tag);
    if (seq != null) {
      if (!seq.isEmpty()) {
        printSequence(seq, model, values);
      } else {
        values[3] = "";
        model.addRow(values);
      }
    } else {
      if (holder.vr.isInlineBinary()) {
        values[3] = "binary data"; // NON-NLS
      } else {
        values[3] = printItem(dcmObj.getStrings(privateCreator, tag));
      }
      model.addRow(values);
    }
  }

  private static String getPrefixTag(int level) {
    StringBuilder buf = new StringBuilder();
    if (level > 0) {
      buf.insert(0, "-->");
    }
    for (int i = 1; i < level; i++) {
      buf.insert(0, "--");
    }
    return buf.toString();
  }

  private static String printItem(String[] values) {
    StringBuilder buf = new StringBuilder();
    if (values != null && values.length > 0) {
      buf.append(values[0]);
      for (int i = 1; i < values.length; i++) {
        buf.append("\\");
        buf.append(values[i]);
      }
      if (buf.length() > 256) {
        buf.setLength(253);
        buf.append("...");
      }
    }
    return buf.toString();
  }

  private static void printSequence(Sequence seq, DefaultTableModel model, Object[] values) {
    String items = seq.size() <= 1 ? " item" : " items"; // NON-NLS
    values[3] = printItem(new String[] {seq.size() + items});
    model.addRow(values);

    for (int i = 0; i < seq.size(); i++) {
      Attributes attributes = seq.get(i);
      int level = attributes.getLevel();
      Object[] v = new Object[4];
      v[0] = getPrefixTag(level) + " ITEM #" + (i + 1); // NON-NLS
      v[1] = "";
      v[2] = "";
      v[3] = "";
      model.addRow(v);

      int[] tags = attributes.tags();
      for (int tag : tags) {
        printElement(model, attributes, tag);
      }
    }
  }

  private void displayLimitedDicomInfo(MediaSeries<?> series, MediaElement media) {
    StyledDocument doc = jTextPaneLimited.getStyledDocument();
    applyFormatting(doc);
    int oldCaretPosition = jTextPaneLimited.getCaretPosition();
    try {
      // clear previous text
      doc.remove(0, doc.getLength());
    } catch (BadLocationException e) {
      LOGGER.error("Clear document", e);
    }
    if (series != null && media != null) {
      Object tagValue = series.getTagValue(TagW.ExplorerModel);
      if (tagValue instanceof DicomModel model) {
        MediaReader loader = media.getMediaReader();
        if (loader instanceof DcmMediaReader) {
          List<DicomData> list = DicomManager.getInstance().getLimitedDicomTags();
          for (DicomData dicomData : list) {
            writeItems(dicomData, getGroup(model, series, dicomData), doc);
          }
        }
      }
    }
    oldCaretPosition = Math.min(oldCaretPosition, doc.getLength());
    jTextPaneLimited.setCaretPosition(oldCaretPosition);
    limitedPane.setViewportView(jTextPaneLimited);
  }

  private MediaSeriesGroup getGroup(DicomModel model, MediaSeries<?> series, DicomData dicomData) {
    Level level = dicomData.getLevel();

    if (Level.PATIENT.equals(level)) {
      return model.getParent(series, DicomModel.patient);
    } else if (Level.STUDY.equals(level)) {
      return model.getParent(series, DicomModel.study);
    } else if (Level.SERIES.equals(level)) {
      return model.getParent(series, DicomModel.series);
    }
    return null;
  }

  private void writeItems(DicomData dicomData, TagReadable group, StyledDocument doc) {
    int insertTitle = doc.getLength();
    boolean exist = false;

    for (TagView t : dicomData.getInfos()) {
      for (TagW tag : t.getTag()) {
        if (!anonymize || tag.getAnonymizationType() != 1) {
          try {
            Object val = TagUtil.getTagValue(tag, group, currentMedia);
            if (val != null) {
              exist = true;
              doc.insertString(doc.getLength(), tag.getDisplayedName(), null);
              doc.insertString(
                  doc.getLength(),
                  StringUtil.COLON_AND_SPACE + tag.getFormattedTagValue(val, null) + "\n",
                  doc.getStyle("bold"));
              break;
            }
          } catch (BadLocationException e) {
            LOGGER.error("Writing text issue", e);
          }
        }
      }
    }
    if (exist) {
      try {
        String formatTitle =
            insertTitle < 3 ? dicomData.getTitle() + "\n" : "\n" + dicomData.getTitle() + "\n";
        doc.insertString(insertTitle, formatTitle, doc.getStyle("h3"));
      } catch (BadLocationException e) {
        LOGGER.error("Writing text issue", e);
      }
    }
  }

  public static void displayHeader(ImageViewerPlugin<?> container) {
    if (container != null) {
      ViewCanvas<?> selView = container.getSelectedImagePane();
      if (selView != null) {
        showHeaderDialog(container, selView.getSeries(), selView.getImage());
      }
    }
  }

  public static void displayHeaderForSpecialElement(ViewerPlugin<?> container, Series<?> series) {
    if (container != null && series != null) {
      DicomSpecialElement dcm =
          DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
      showHeaderDialog(container, series, dcm);
    }
  }

  public static void showHeaderDialog(
      SeriesViewer<?> container, MediaSeries<? extends MediaElement> series, MediaElement dcm) {
    if (container != null && series != null && dcm != null) {
      JFrame frame = new JFrame(Messages.getString("DicomExplorer.dcmInfo"));
      frame.setSize(500, 630);
      DicomFieldsView view = new DicomFieldsView(container);
      view.changingViewContentEvent(new SeriesViewerEvent(container, series, dcm, EVENT.SELECT));
      JPanel panel = new JPanel();
      panel.setLayout(new BorderLayout());
      panel.add(view);
      frame.getContentPane().add(panel);
      frame.setAlwaysOnTop(true);
      frame.setIconImage(ResourceUtil.getIcon(ActionIcon.METADATA).derive(64, 64).getImage());
      Component c =
          container instanceof Component
              ? (Component) container
              : UIManager.MAIN_AREA.getComponent();
      GuiUtils.showCenterScreen(frame, c);
    }
  }

  static class SearchHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {
    public SearchHighlightPainter(Color color) {
      super(color);
    }
  }

  static class SearchPanel extends JPanel {
    private final List<Integer> searchPostions = new ArrayList<>();
    private final JTextComponent textComponent;
    private int currentSearchIndex = 0;
    private String currentSearchPattern;

    public SearchPanel(JTextComponent textComponent) {
      super();
      this.textComponent = textComponent;
      this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
      init();
    }

    private void init() {
      this.add(
          new JLabel(Messages.getString("DicomFieldsView.search") + StringUtil.COLON_AND_SPACE));
      final JTextField tf = new JTextField();
      GuiUtils.setPreferredWidth(tf, 300, 100);
      tf.addActionListener(
          evt -> {
            currentSearchPattern = tf.getText().trim();
            highlight(currentSearchPattern);
            if (!searchPostions.isEmpty()) {
              try {
                textComponent.scrollRectToVisible(textComponent.modelToView(searchPostions.get(0)));
                textComponent.requestFocusInWindow();
              } catch (BadLocationException e) {
                LOGGER.error("Scroll to highight", e);
              }
            }
          });
      this.add(tf);
      JButton up =
          new JButton(new ImageIcon(SeriesViewerListener.class.getResource("/icon/up.png")));
      up.setToolTipText(Messages.getString("DicomFieldsView.previous"));
      up.addActionListener(evt -> previous());
      this.add(up);
      JButton down =
          new JButton(
              new RotatedIcon(
                  new ImageIcon(SeriesViewerListener.class.getResource("/icon/up.png")),
                  RotatedIcon.Rotate.UPSIDE_DOWN));
      down.setToolTipText(Messages.getString("DicomFieldsView.next"));
      down.addActionListener(evt -> next());
      this.add(down);
      textComponent.addKeyListener(
          new KeyListener() {

            @Override
            public void keyTyped(KeyEvent e) {}

            @Override
            public void keyReleased(KeyEvent e) {
              if (e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_F3) {
                previous();
              } else if (e.getKeyCode() == KeyEvent.VK_F3) {
                next();
              }
            }

            @Override
            public void keyPressed(KeyEvent e) {}
          });

      textComponent.setFocusable(true);
    }

    private void previous() {
      if (!searchPostions.isEmpty()) {
        currentSearchIndex =
            currentSearchIndex <= 0 ? searchPostions.size() - 1 : currentSearchIndex - 1;
        showCurrentSearch(currentSearchPattern);
      }
    }

    private void next() {
      if (!searchPostions.isEmpty()) {
        currentSearchIndex =
            currentSearchIndex >= searchPostions.size() - 1 ? 0 : currentSearchIndex + 1;
        showCurrentSearch(currentSearchPattern);
      }
    }

    public void highlight(String pattern) {
      removeHighlights(textComponent);
      searchPostions.clear();
      if (StringUtil.hasText(pattern)) {
        try {
          Highlighter hilite = textComponent.getHighlighter();
          Document doc = textComponent.getDocument();
          String text = doc.getText(0, doc.getLength()).toUpperCase();
          String patternUp = pattern.toUpperCase();
          int pos = 0;

          while ((pos = text.indexOf(patternUp, pos)) >= 0) {
            if (searchPostions.isEmpty()) {
              hilite.addHighlight(pos, pos + patternUp.length(), searchHighlightPainter);
            } else {
              hilite.addHighlight(pos, pos + patternUp.length(), searchResultHighlightPainter);
            }
            searchPostions.add(pos);
            pos += patternUp.length();
          }
        } catch (BadLocationException e) {
          LOGGER.error("Highight result of search", e);
        }
      }
    }

    public void removeHighlights(JTextComponent textComonent) {
      Highlighter hilite = textComonent.getHighlighter();
      for (Highlighter.Highlight highlight : hilite.getHighlights()) {
        if (highlight.getPainter() instanceof SearchHighlightPainter) {
          hilite.removeHighlight(highlight);
        }
      }
    }

    public void showCurrentSearch(String pattern) {
      if (!searchPostions.isEmpty() && StringUtil.hasText(pattern)) {
        removeHighlights(textComponent);

        try {
          if (currentSearchIndex < 0 || currentSearchIndex >= searchPostions.size()) {
            currentSearchIndex = 0;
          }
          int curPos = searchPostions.get(currentSearchIndex);
          Highlighter hilite = textComponent.getHighlighter();

          for (Integer pos : searchPostions) {
            if (pos == curPos) {
              hilite.addHighlight(pos, pos + pattern.length(), searchHighlightPainter);
            } else {
              hilite.addHighlight(pos, pos + pattern.length(), searchResultHighlightPainter);
            }
          }
          textComponent.scrollRectToVisible(textComponent.modelToView(curPos));
        } catch (BadLocationException e) {
          LOGGER.error("Highight result of search", e);
        }
      }
    }
  }

  public static class DicomData {

    private final String title;
    private final TagView[] infos;
    private final Level level;

    public DicomData(String title, TagView[] infos, Level level) {
      if (infos == null) {
        throw new IllegalArgumentException();
      }
      this.title = title;
      this.infos = infos;
      this.level = level;
      for (TagView tagView : infos) {
        for (TagW tag : tagView.getTag()) {
          DicomMediaIO.tagManager.addTag(tag, level);
        }
      }
    }

    public TagView[] getInfos() {
      return infos;
    }

    public String getTitle() {
      return title;
    }

    public Level getLevel() {
      return level;
    }
  }
}
