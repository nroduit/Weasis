/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import java.awt.Color;
import java.awt.print.PrinterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.ui.print.PageSource;
import org.weasis.core.ui.print.PageSpec;
import org.weasis.core.ui.print.PageSpec.Orientation;
import org.weasis.core.ui.print.Paper;
import org.weasis.core.ui.print.PrintDestination;
import org.weasis.core.ui.util.PrintOptions;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.Type;
import org.weasis.dicom.explorer.pref.node.AbstractDicomNode.UsageType;
import org.weasis.dicom.explorer.pref.node.DefaultDicomNode;
import org.weasis.dicom.explorer.pref.node.DicomNodeDialog;
import org.weasis.dicom.explorer.pref.node.DicomPrintNode;

/**
 * A DICOM print node as a destination of the print dialog: the film sizes, the film resolution and
 * the defaults configured on the node, adjusted for the job in its options panel, and the calling
 * AE of the association.
 */
public final class DicomPrintDestination implements PrintDestination {

  public static final String ID_PREFIX = "dicom:"; // NON-NLS

  /** The unprintable edge of a film. */
  public static final double FILM_MARGIN_MM = 2.0;

  private static final String LAST_CALLING_NODE = "lastCallingNode"; // NON-NLS

  private final DicomPrintNode node;
  private final DicomPrintOptions options;
  private DefaultDicomNode callingNode;

  public DicomPrintDestination(DicomPrintNode node) {
    this.node = node;
    this.options = node.getPrintOptions().copy();
  }

  /** The print nodes the user sees. */
  public static List<DicomPrintDestination> lookup() {
    List<DicomPrintDestination> list = new ArrayList<>();
    for (AbstractDicomNode n : AbstractDicomNode.loadDicomNodes(Type.PRINTER, UsageType.BOTH)) {
      if (n instanceof DicomPrintNode printNode) {
        list.add(new DicomPrintDestination(printNode));
      }
    }
    return list;
  }

  public DicomPrintNode getNode() {
    return node;
  }

  /** The options of this job: the defaults of the node, as adjusted in the panel. */
  public DicomPrintOptions getOptions() {
    return options;
  }

  @Override
  public String id() {
    return ID_PREFIX + node.getAeTitle() + "@" + node.getHostname() + ":" + node.getPort();
  }

  @Override
  public String title() {
    return StringUtil.hasText(node.getDescription()) ? node.getDescription() : node.getAeTitle();
  }

  @Override
  public List<Paper> papers() {
    List<Paper> papers = new ArrayList<>(DicomPrintOptions.filmSizes());
    Paper first = defaultPaper();
    papers.remove(first);
    papers.addFirst(first);
    return papers;
  }

  @Override
  public Paper defaultPaper() {
    return options.getFilmSizeId();
  }

  @Override
  public Color defaultBackground() {
    return "WHITE".equals(options.getEmptyDensity()) ? Color.WHITE : Color.BLACK; // NON-NLS
  }

  @Override
  public boolean isColor() {
    return options.isColorPrint();
  }

  @Override
  public Optional<Orientation> preferredOrientation() {
    return Optional.of(
        "LANDSCAPE".equals(options.getFilmOrientation()) // NON-NLS
            ? Orientation.LANDSCAPE
            : Orientation.PORTRAIT);
  }

  @Override
  public Optional<JComponent> optionsComponent(Runnable onChange) {
    DicomPrintOptionPane pane = new DicomPrintOptionPane(DicomPrintOptionPane.Scope.JOB);
    pane.applyOptions(options);
    pane.addChangeListener(
        () -> {
          pane.saveOptions(options);
          onChange.run();
        });

    JComboBox<AbstractDicomNode> callingCombo = new JComboBox<>();
    AbstractDicomNode.loadDicomNodes(callingCombo, Type.DICOM_CALLING, UsageType.STORAGE);
    GuiUtils.setPreferredWidth(callingCombo, 185, 150);
    AbstractDicomNode.addTooltipToComboList(callingCombo);
    AbstractDicomNode.restoreNodeSelection(
        persistence(), callingCombo.getModel(), LAST_CALLING_NODE);
    callingNode = callingNodeOf(callingCombo);
    callingCombo.addActionListener(
        e -> {
          callingNode = callingNodeOf(callingCombo);
          AbstractDicomNode.nodeSelectionPersistence(persistence(), callingNode, LAST_CALLING_NODE);
        });

    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[trailing]rel[leading]")); // NON-NLS
    panel.add(pane, "span 2, growx"); // NON-NLS
    panel.add(new JLabel(Messages.common("calling_node") + StringUtil.COLON));
    panel.add(callingCombo);
    return Optional.of(panel);
  }

  private static DefaultDicomNode callingNodeOf(JComboBox<AbstractDicomNode> combo) {
    return combo.getSelectedItem() instanceof DefaultDicomNode n ? n : null;
  }

  private static WProperties persistence() {
    return GuiUtils.getUICore().getPluginPersistence(DicomNodeDialog.class.getPackageName());
  }

  @Override
  public PageSpec pageSpec(Paper paper, Orientation orientation, Color background, Color border) {
    return new PageSpec(
        paper,
        orientation,
        PageSpec.Margins.uniform(FILM_MARGIN_MM),
        PageSpec.DEFAULT_GUTTER_MM,
        background,
        border);
  }

  @Override
  public OptionalInt resolutionDpi() {
    return OptionalInt.of(options.getFilmResolution());
  }

  @Override
  public void print(PageSource pages, PageSpec spec, PrintOptions page, int copies)
      throws PrinterException {
    try {
      new DicomPrint(node, options).print(pages, spec, page, copies, callingNode);
    } catch (Exception e) {
      PrinterException failure = new PrinterException(e.getMessage());
      failure.initCause(e);
      throw failure;
    }
  }

  @Override
  public String toString() {
    return title();
  }
}
