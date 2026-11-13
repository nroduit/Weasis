/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.main;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.beans.PropertyChangeListener;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import net.miginfocom.swing.MigLayout;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.Slot;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSeries;
import org.weasis.dicom.codec.ObjectDescriptor;
import org.weasis.dicom.codec.ObjectKind;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.items.DownloadState;
import org.weasis.dicom.explorer.items.DownloadSummary;
import org.weasis.dicom.explorer.items.ExplorerItem;
import org.weasis.dicom.explorer.items.Interest;
import org.weasis.dicom.explorer.items.InterestSource;
import org.weasis.dicom.explorer.items.ItemActions;
import org.weasis.dicom.explorer.items.ObjectItem;
import org.weasis.dicom.explorer.items.SeriesItem;
import org.weasis.dicom.explorer.items.StudyContents;

/**
 * The study content panel of the bottom slot of the explorer side: the selected item in full. For a
 * series, its attributes, the objects that reference it and the actions on it; for an object (key
 * object selection, presentation state, segmentation, RT set), its attributes, origin and the
 * series it references. The study line says how many of its series are downloaded and offers to
 * download the rest. It follows the selection of the explorer while it is shown.
 */
public class StudyContentTool extends PluginTool {

  public static final String ID = "contents"; // NON-NLS

  private static final DateTimeFormatter TIME =
      DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT);

  private final DicomExplorer explorer;
  private final DicomModel model;
  private final JLabel title = new JLabel();
  private final JLabel empty = new JLabel(Messages.getString("StudyContentTool.nothing"));
  private final JPanel attributes =
      new JPanel(new MigLayout("insets 0, wrap 2, gapx 10, gapy 2", "[trailing]10[grow,fill]"));
  private final JPanel references = new JPanel(new MigLayout("insets 0, wrap 1, gapy 1", "[grow]"));
  private final JLabel referencesTitle = new JLabel();
  private final JPanel actions =
      GuiUtils.getFlowLayoutPanel(FlowLayout.TRAILING, Insertable.ITEM_SEPARATOR_SMALL, 2);
  private final JLabel studyLine = new JLabel();
  private final JButton downloadAll =
      new JButton(Messages.getString("StudyContentTool.download_all"));
  private final Timer refresh = new Timer(200, _ -> rebuild());

  private final SeriesSelectionModel.SelectionChangeListener selectionListener =
      this::onSelectionChanged;
  private final Consumer<ExplorerItem> focusListener = this::show;
  private final PropertyChangeListener modelListener =
      evt -> {
        if (evt instanceof ObservableEvent event && refreshesOn(event.getActionCommand())) {
          refresh.restart();
        }
      };

  private ExplorerItem item;

  public StudyContentTool(DicomExplorer explorer) {
    super(ID, Messages.getString("StudyContentTool.title"), Insertable.Type.EXPLORER, 25);
    this.explorer = Objects.requireNonNull(explorer);
    this.model = explorer.getDataExplorerModel();
    setIcon(ResourceUtil.getIcon(ActionIcon.STUDY_CONTENT));
    setLayout(new BorderLayout());
    refresh.setRepeats(false);
    buildLayout();
  }

  @Override
  public Slot getPreferredSlot() {
    return Slot.BOTTOM;
  }

  @Override
  public double getPreferredBottomShare() {
    return 0.4;
  }

  private void buildLayout() {
    title.setFont(title.getFont().deriveFont(Font.BOLD));
    title.setBorder(BorderFactory.createEmptyBorder(5, 8, 2, 8));
    empty.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
    referencesTitle.setFont(referencesTitle.getFont().deriveFont(Font.BOLD));
    downloadAll.addActionListener(_ -> downloadAll());

    JPanel body = new JPanel(new MigLayout("insets 5 8 5 8, wrap 1, gapy 6", "[grow,fill]"));
    body.add(empty);
    body.add(attributes);
    body.add(referencesTitle);
    body.add(references);
    JScrollPane scroll = new JScrollPane(body);
    scroll.setBorder(BorderFactory.createEmptyBorder());

    JPanel footer = new JPanel(new BorderLayout());
    JPanel studyRow =
        GuiUtils.getFlowLayoutPanel(FlowLayout.LEADING, Insertable.ITEM_SEPARATOR_SMALL, 2);
    studyRow.add(studyLine);
    studyRow.add(downloadAll);
    footer.add(studyRow, BorderLayout.LINE_START);
    footer.add(actions, BorderLayout.LINE_END);

    add(title, BorderLayout.NORTH);
    add(scroll, BorderLayout.CENTER);
    add(footer, BorderLayout.SOUTH);
    rebuild();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    explorer.getSelectionList().addSelectionChangeListener(selectionListener);
    explorer.addItemFocusListener(focusListener);
    model.addPropertyChangeListener(modelListener);
    DicomSeries lead = leadOf(explorer.getSelectionList());
    if (item == null && lead != null) {
      item = SeriesItem.of(lead, model.getParent(lead, DicomModel.study));
    }
    rebuild();
  }

  @Override
  public void removeNotify() {
    explorer.getSelectionList().removeSelectionChangeListener(selectionListener);
    explorer.removeItemFocusListener(focusListener);
    model.removePropertyChangeListener(modelListener);
    refresh.stop();
    super.removeNotify();
  }

  private void onSelectionChanged(SeriesSelectionModel selection) {
    DicomSeries lead = leadOf(selection);
    if (lead != null) {
      show(SeriesItem.of(lead, model.getParent(lead, DicomModel.study)));
    }
  }

  /** The lead of a mouse selection, else the last series selected (a command sets no lead). */
  private static DicomSeries leadOf(SeriesSelectionModel selection) {
    DicomSeries lead = selection.getLeadSelection();
    if (lead == null && !selection.isEmpty()) {
      lead = selection.getLast();
    }
    return lead;
  }

  /** Shows an item: the lead series of the selection, or the object the list focused. */
  public void show(ExplorerItem item) {
    this.item = item;
    rebuild();
  }

  /** The item shown, null when the panel is empty. */
  public ExplorerItem getItem() {
    return item;
  }

  private static boolean refreshesOn(ObservableEvent.BasicAction action) {
    return action == ObservableEvent.BasicAction.UPDATE
        || action == ObservableEvent.BasicAction.ADD
        || action == ObservableEvent.BasicAction.REMOVE
        || action == ObservableEvent.BasicAction.REPLACE
        || action == ObservableEvent.BasicAction.LOADING_START
        || action == ObservableEvent.BasicAction.LOADING_STOP
        || action == ObservableEvent.BasicAction.LOADING_CANCEL;
  }

  private void rebuild() {
    attributes.removeAll();
    references.removeAll();
    actions.removeAll();
    ExplorerItem current = stillLoaded(item);
    item = current;
    boolean hasItem = current != null;
    empty.setVisible(!hasItem);
    referencesTitle.setVisible(hasItem);
    title.setVisible(hasItem);
    if (current == null) {
      title.setText("");
      title.setIcon(null);
      studyLine.setText("");
      downloadAll.setVisible(false);
    } else {
      StudyContents contents = StudyContents.of(model, current.study());
      switch (current) {
        case SeriesItem series ->
            showSeries(SeriesItem.of(series.series(), series.study()), contents);
        case ObjectItem object -> showObject(object, contents);
      }
      DownloadSummary summary = DownloadSummary.of(contents);
      studyLine.setText(
          Messages.format("StudyContentTool.downloaded", summary.complete(), summary.total()));
      downloadAll.setVisible(summary.hasPending());
    }
    revalidate();
    repaint();
  }

  /** The item when its series or object is still in the model, else null. */
  private ExplorerItem stillLoaded(ExplorerItem candidate) {
    if (candidate == null || candidate.study() == null) {
      return null;
    }
    if (model.getParent(candidate.study(), DicomModel.patient) == null) {
      return null;
    }
    return switch (candidate) {
      case SeriesItem s -> model.getSeriesNode(s.seriesUID()) == null ? null : s;
      case ObjectItem o ->
          StudyContents.of(model, o.study()).objects().stream()
                  .anyMatch(other -> other.element() == o.element())
              ? o
              : null;
    };
  }

  private void showSeries(SeriesItem series, StudyContents contents) {
    DicomSeries dicom = series.series();
    title.setIcon(null);
    title.setText(seriesTitle(series));
    attribute(Messages.common("modality"), series.modality());
    attribute(
        Messages.getString("StudyContentTool.number"),
        series.seriesNumber() == null
            ? null
            : series.isSplit()
                ? series.seriesNumber() + "." + series.splitNumber()
                : String.valueOf(series.seriesNumber()));
    attribute(Messages.common("description"), formatted(dicom, Tag.SeriesDescription));
    String images = series.imageCount() > 0 ? String.valueOf(series.imageCount()) : null;
    if (series.downloadState() != DownloadState.COMPLETE
        && series.expectedImageCount() > series.imageCount()) {
      images =
          Messages.format(
              "StudyContentTool.images_of", series.imageCount(), series.expectedImageCount());
    }
    attribute(Messages.getString("StudyContentTool.images"), images);
    attribute(Messages.common("date"), dateTime(dicom, Tag.SeriesDate, series.date()));
    attribute(Messages.getString("DicomExplorer.facet_state"), stateLabel(series.downloadState()));
    if (explorer.rejectedVisibility() != RejectedVisibility.HIDDEN) {
      int rejected = series.rejectedImageCount();
      attribute(
          Messages.getString("DicomExplorer.facet_rejected"),
          rejected > 0 ? String.valueOf(rejected) : null);
    }
    Interest interest = Interest.of(dicom);
    if (!interest.isEmpty()) {
      StringBuilder text = new StringBuilder(String.valueOf(interest.count()));
      text.append(" (");
      boolean first = true;
      for (InterestSource source : interest.sources()) {
        if (!first) {
          text.append(", ");
        }
        first = false;
        text.append(source.getId()).append(' ').append(interest.count(source));
      }
      text.append(')');
      attribute(Messages.getString("StudyContentTool.interest"), text.toString());
    }

    referencesTitle.setText(Messages.getString("StudyContentTool.referencing"));
    boolean listed = explorer.isRejectionListed();
    List<ObjectItem> referencing =
        contents.objectsReferencing(series.seriesUID()).stream()
            .filter(o -> listed || o.kind() != ObjectKind.REJECTION_NOTE)
            .toList();
    if (referencing.isEmpty()) {
      references.add(new JLabel(Messages.common("none")));
    }
    for (ObjectItem object : referencing) {
      references.add(link(objectTitle(object), icon(object), () -> show(object)));
    }

    JButton open = new JButton(Messages.common("open"));
    open.addActionListener(
        _ -> ThumbnailMouseAndKeyAdapter.openSeriesInDefaultPlugin(dicom, model));
    actions.add(open);
    if (!interest.isEmpty()) {
      JButton openInterest =
          new JButton(Messages.format("DicomExplorer.open_interest", interest.count()));
      openInterest.addActionListener(_ -> InterestOpener.open(dicom, model));
      actions.add(openInterest);
    }
    if (series.downloadState() == DownloadState.PENDING) {
      JButton download = new JButton(Messages.getString("StudyContentTool.download"));
      download.addActionListener(_ -> ItemActions.download(List.of(series)));
      actions.add(download);
    } else if (series.downloadState() == DownloadState.DOWNLOADING) {
      JButton stop = new JButton(Messages.common("stop"));
      stop.addActionListener(_ -> ItemActions.stop(series));
      actions.add(stop);
    }
  }

  private void showObject(ObjectItem object, StudyContents contents) {
    ObjectDescriptor d = object.descriptor();
    title.setIcon(icon(object));
    title.setText(objectTitle(object));
    attribute(Messages.common("type"), d.kind().getId().toUpperCase());
    attribute(Messages.common("description"), d.label());
    attribute(Messages.common("modality"), d.modality());
    attribute(Messages.common("date"), dateTime(object.element(), Tag.ContentDate, d.date()));
    attribute(Messages.getString("StudyContentTool.creator"), d.creator());
    attribute(
        Messages.getString("StudyContentTool.images"),
        d.referencedImages() > 0 ? String.valueOf(d.referencedImages()) : null);
    if (d.parts() > 0) {
      String parts =
          d.kind() == ObjectKind.SEGMENTATION
              ? Messages.format("StudyContentTool.segments", d.parts())
              : Messages.format("StudyContentTool.structures", d.parts());
      attribute(null, parts);
    }
    if (d.provenance() != null) {
      attribute(Messages.getString("StudyContentTool.origin"), d.provenance().describe());
    }

    referencesTitle.setText(Messages.getString("StudyContentTool.references"));
    if (d.referencedSeriesUIDs().isEmpty()) {
      references.add(new JLabel(Messages.common("none")));
    }
    for (String uid : d.referencedSeriesUIDs()) {
      if (model.getSeriesNode(uid) instanceof DicomSeries series) {
        SeriesItem referenced = SeriesItem.of(series, model.getParent(series, DicomModel.study));
        references.add(link(seriesTitle(referenced), null, () -> explorer.reveal(series)));
      } else {
        JLabel missing = new JLabel(Messages.getString("StudyContentTool.not_loaded"));
        missing.setEnabled(false);
        references.add(missing);
      }
    }
    boolean openable = !ItemActions.referencedLoadedSeries(object, model).isEmpty();
    JButton open = new JButton(openLabel(d.kind()));
    open.setEnabled(openable);
    open.addActionListener(_ -> ItemActions.open(object, model));
    actions.add(open);
    if (!contents.series().isEmpty()) {
      // the series the object describes are of this study: the list and the thumbnails show them
      actions.add(GuiUtils.boxHorizontalStrut(0));
    }
  }

  private static String openLabel(ObjectKind kind) {
    return switch (kind) {
      case KEY_OBJECT, REJECTION_NOTE -> Messages.getString("DicomExplorer.open_ko");
      case PRESENTATION_STATE -> Messages.getString("StudyContentTool.apply_pr");
      default -> Messages.common("open");
    };
  }

  private void downloadAll() {
    if (item != null) {
      ItemActions.downloadAll(StudyContents.of(model, item.study()));
      refresh.restart();
    }
  }

  private void attribute(String label, String value) {
    if (!StringUtil.hasText(value)) {
      return;
    }
    JLabel name = new JLabel(label == null ? "" : label + StringUtil.COLON);
    name.setEnabled(false);
    attributes.add(name);
    JLabel text = new JLabel(value);
    attributes.add(text);
  }

  private static JComponent link(String text, javax.swing.Icon icon, Runnable action) {
    JButton button = new JButton(text, icon);
    button.putClientProperty(
        FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    button.setHorizontalAlignment(SwingConstants.LEADING);
    button.setFocusable(false);
    button.addActionListener(_ -> action.run());
    return button;
  }

  private static javax.swing.Icon icon(ObjectItem object) {
    return object.icon() == null ? null : ResourceUtil.getIcon(object.icon());
  }

  private String seriesTitle(SeriesItem series) {
    StringBuilder text = new StringBuilder();
    if (StringUtil.hasText(series.modality())) {
      text.append(series.modality()).append(' ');
    }
    if (series.seriesNumber() != null) {
      text.append('[').append(series.seriesNumber());
      if (series.isSplit()) {
        text.append('.').append(series.splitNumber());
      }
      text.append("] ");
    }
    String description = formatted(series.series(), Tag.SeriesDescription);
    text.append(description.isEmpty() ? Messages.common("series") : description);
    return text.toString().trim();
  }

  private static String objectTitle(ObjectItem object) {
    String label = object.label();
    return object.kind().getId().toUpperCase() + (StringUtil.hasText(label) ? " • " + label : "");
  }

  private static String stateLabel(DownloadState state) {
    return switch (state) {
      case COMPLETE -> Messages.getString("DicomExplorer.state_complete");
      case PENDING -> Messages.getString("DicomExplorer.state_pending");
      case DOWNLOADING -> Messages.getString("DicomExplorer.state_downloading");
    };
  }

  /** Masked and formatted value of a tag, empty when absent. */
  private static String formatted(TagReadable taggable, int tagId) {
    TagW tag = TagD.get(tagId);
    Object masked = IdentityMask.maskValue(tag, taggable.getTagValue(tag));
    String text = masked == null ? null : tag.getFormattedTagValue(masked, null);
    return StringUtil.hasText(text) ? text : "";
  }

  /** The masked date of the tag with the time of the item, when known. */
  private static String dateTime(TagReadable taggable, int dateTag, LocalDateTime date) {
    String day = formatted(taggable, dateTag);
    if (date == null) {
      return day;
    }
    String time = TIME.format(date);
    return day.isEmpty() ? time : day + " " + time;
  }
}
