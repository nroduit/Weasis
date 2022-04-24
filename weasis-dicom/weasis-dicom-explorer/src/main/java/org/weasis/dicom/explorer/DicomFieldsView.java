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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
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

  public static final String[] columns = {
    Messages.getString("tag.id"), "VR", Messages.getString("tag.name"), Messages.getString("value")
  };
  private final DefaultTableModel tableModel =
      new DefaultTableModel(columns, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      };

  private final JTable jtable = new JTable(tableModel);
  private final TagSearchTablePanel tagSearchTablePanel;
  private final TagSearchDocumentPanel tagSearchDocumentPanel;

  public DicomFieldsView(SeriesViewer<?> viewer) {
    this.viewer = viewer;
    JPanel panel = new JPanel();
    panel.setLayout(new BorderLayout());
    panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    addTab(Messages.getString("DicomFieldsView.limited"), null, panel, null);
    this.tagSearchDocumentPanel = new TagSearchDocumentPanel(jTextPaneLimited);
    panel.add(tagSearchDocumentPanel, BorderLayout.NORTH);
    panel.add(limitedPane, BorderLayout.CENTER);
    jTextPaneLimited.setBorder(new EmptyBorder(5, 5, 5, 5));
    jTextPaneLimited.setContentType("text/html");
    jTextPaneLimited.setEditable(false);

    JPanel dump = new JPanel();
    dump.setLayout(new BorderLayout());
    dump.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
    addTab(Messages.getString("DicomFieldsView.all"), null, dump, null);
    TableRowSorter<TableModel> sorter =
        new TableRowSorter<>(tableModel) {
          @Override
          public boolean isSortable(int column) {
            return false;
          }
        };
    jtable.setRowSorter(sorter);
    this.tagSearchTablePanel = new TagSearchTablePanel(jtable);
    dump.add(tagSearchTablePanel, BorderLayout.NORTH);
    jtable.getTableHeader().setReorderingAllowed(false);
    jtable.setShowVerticalLines(true);
    dump.add(allPane, BorderLayout.CENTER);

    setPreferredSize(GuiUtils.getDimension(400, 300));
    setMinimumSize(GuiUtils.getDimension(150, 50));

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
      displayLimitedDicomInfo(series, media);
    } else {
      displayAllDicomInfo(series, media);
    }
  }

  private static void applyFormatting(StyledDocument doc) {
    Color color = FlatUIUtils.getUIColor("TextArea.foreground", Color.DARK_GRAY);
    Style bold = doc.addStyle("bold", doc.getStyle(StyleContext.DEFAULT_STYLE)); // NON-NLS
    StyleConstants.setBold(bold, true);
    StyleConstants.setForeground(bold, color);
    int fontSize = StyleConstants.getFontSize(bold.getResolveParent());
    StyleConstants.setFontSize(bold, fontSize);

    bold = doc.addStyle("h3", bold); // NON-NLS
    StyleConstants.setFontSize(bold, fontSize + 3);
  }

  private void displayAllDicomInfo(MediaSeries<?> series, MediaElement media) {
    tableModel.setRowCount(0);
    if (media != null) {
      MediaReader loader = media.getMediaReader();
      if (loader instanceof DicomMediaIO dicomMediaIO) {
        DicomMetaData metaData = dicomMediaIO.getDicomMetaData();
        if (metaData != null) {
          printAttribute(tableModel, metaData.getFileMetaInformation());
          printAttribute(tableModel, metaData.getDicomObject());
        }
      } else if (loader instanceof DcmMediaReader reader) {
        printAttribute(tableModel, reader.getDicomObject());
      }
    }
    jtable.getColumnModel().getColumn(0).setPreferredWidth(100);
    jtable.getColumnModel().getColumn(1).setPreferredWidth(30);
    jtable.getColumnModel().getColumn(2).setPreferredWidth(250);
    jtable.getColumnModel().getColumn(3).setPreferredWidth(300);
    jtable.getColumnModel().setColumnMargin(GuiUtils.getScaleLength(7));
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
    tagSearchTablePanel.filter();
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
    tagSearchDocumentPanel.filter();
    limitedPane.setViewportView(jTextPaneLimited);
  }

  private MediaSeriesGroup getGroup(DicomModel model, MediaSeries<?> series, DicomData dicomData) {
    Level level = dicomData.level;

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

    for (TagView t : dicomData.infos) {
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
            insertTitle < 3 ? dicomData.title + "\n" : "\n" + dicomData.title + "\n";
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
      frame.setSize(GuiUtils.getDimension(650, 600));
      DicomFieldsView view = new DicomFieldsView(container);
      view.changingViewContentEvent(new SeriesViewerEvent(container, series, dcm, EVENT.SELECT));
      JPanel panel = new JPanel();
      panel.setLayout(new BorderLayout());
      panel.add(view);
      frame.getContentPane().add(panel);
      frame.setIconImage(ResourceUtil.getIcon(ActionIcon.METADATA).derive(64, 64).getImage());
      Component c =
          container instanceof Component component ? component : UIManager.MAIN_AREA.getComponent();
      GuiUtils.showCenterScreen(frame, c);
    }
  }

  static class SearchHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {
    public SearchHighlightPainter(Color color) {
      super(color);
    }
  }

  public record DicomData(String title, TagView[] infos, Level level) {
    public DicomData(String title, TagView[] infos, Level level) {
      this.title = title;
      this.infos = Objects.requireNonNull(infos);
      this.level = level;
      for (TagView tagView : infos) {
        for (TagW tag : tagView.getTag()) {
          DicomMediaIO.tagManager.addTag(tag, level);
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      DicomData dicomData = (DicomData) o;
      return Objects.equals(title, dicomData.title)
          && Arrays.equals(infos, dicomData.infos)
          && level == dicomData.level;
    }

    @Override
    public int hashCode() {
      int result = Objects.hash(title, level);
      result = 31 * result + Arrays.hashCode(infos);
      return result;
    }

    @Override
    public String toString() {
      return title;
    }
  }
}
