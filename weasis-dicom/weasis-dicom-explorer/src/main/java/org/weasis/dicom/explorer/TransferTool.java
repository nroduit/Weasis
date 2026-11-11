/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.swing.Icon;
import javax.swing.JButton;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;
import org.weasis.core.api.gui.util.AbstractWizardDialog;
import org.weasis.core.api.gui.util.EmbeddedWizardPanel;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.ui.docking.PluginTool;
import org.weasis.core.ui.docking.SidePanel;
import org.weasis.core.ui.docking.Slot;
import org.weasis.dicom.explorer.main.DicomExplorer;

/**
 * A tool of the bottom slot of the explorer side that moves DICOM objects in or out: import,
 * retrieve, export and send, one tool per direction and {@link TransferKind kind}, so each has its
 * own stripe button and its own height. The content is built when the tool is first shown: its
 * pages in an {@link EmbeddedWizardPanel} with the action button of the tool and a help button
 * under them. Several pages (a plugin contribution) show as a toggle bar, a single page directly.
 */
public abstract class TransferTool extends PluginTool {

  private final DicomModel model;
  private final double share;
  private final String actionLabel;
  private final String helpTopic;
  private EmbeddedWizardPanel wizard;

  /**
   * @param share the share of the side height the tool opens at in the bottom slot
   * @param actionLabel the label of the button that runs the selected page
   * @param helpTopic the topic of the help button
   */
  protected TransferTool(
      String id,
      String title,
      int position,
      Icon icon,
      double share,
      String actionLabel,
      String helpTopic,
      DicomModel model) {
    super(id, title, Insertable.Type.EXPLORER, position);
    this.model = Objects.requireNonNull(model);
    this.share = share;
    this.actionLabel = actionLabel;
    this.helpTopic = helpTopic;
    setIcon(icon);
    setLayout(new BorderLayout());
  }

  @Override
  public Slot getPreferredSlot() {
    return Slot.BOTTOM;
  }

  @Override
  public double getPreferredBottomShare() {
    return share;
  }

  /** The width that shows the forms of the tool whole, so the side widens for them. */
  @Override
  public int getWidthToFit() {
    ensureContent();
    if (wizard == null) {
      return 0;
    }
    Insets insets = getInsets();
    return wizard.getWidthToFit() + insets.left + insets.right;
  }

  /** Whether the site offers this tool: its feature switch and, for a host, a page to show. */
  public abstract boolean isOffered();

  /** The pages shown, sorted; built on each content build. */
  protected abstract List<AbstractItemDialogPage> createPages();

  /** Runs the action of the tool on the selected page. */
  protected abstract void run(AbstractItemDialogPage page);

  /** Whether the content is a snapshot of the model, rebuilt on every show. */
  protected boolean isRebuiltOnShow() {
    return false;
  }

  protected DicomModel getModel() {
    return model;
  }

  /** The tool of that id in the DICOM explorer, empty before the explorer exists. */
  public static Optional<TransferTool> find(String id) {
    return GuiUtils.getUICore().getExplorerPlugin(DicomExplorer.NAME)
            instanceof DicomExplorer explorer
        ? explorer.getTransferTool(id)
        : Optional.empty();
  }

  /**
   * Opens the tool of that id in the bottom slot of the explorer side, on the page of that title
   * when given.
   *
   * @return the wizard the tool shows, null when there is no explorer or no shell
   */
  public static EmbeddedWizardPanel open(String id, String pageTitle) {
    return find(id).map(tool -> tool.openOn(pageTitle)).orElse(null);
  }

  /** {@link #open(String, String)} on this tool. */
  public EmbeddedWizardPanel openOn(String pageTitle) {
    ensureContent();
    SidePanel host = GuiUtils.getUICore().getSidePanel(getRole());
    if (host != null) {
      if (!host.contains(this)) {
        host.show(this);
      }
      host.openByUser(getToolId(), Slot.BOTTOM);
      host.expand();
    }
    if (pageTitle != null && wizard != null) {
      wizard.selectPage(pageTitle);
    }
    return wizard;
  }

  /** The wizard the tool shows, null while the tool has no content. */
  public EmbeddedWizardPanel getWizard() {
    return wizard;
  }

  /** Whether the site enables the feature of that switch, on by default. */
  protected static boolean isAllowed(String featureSwitch) {
    return GuiUtils.getUICore().getSystemPreferences().getBooleanProperty(featureSwitch, true);
  }

  @Override
  public void addNotify() {
    super.addNotify();
    ensureContent();
  }

  @Override
  public void removeNotify() {
    if (isRebuiltOnShow()) {
      release();
    }
    super.removeNotify();
  }

  @Override
  public void closeDockable() {
    release();
    super.closeDockable();
  }

  private void ensureContent() {
    if (wizard != null) {
      return;
    }
    JButton action = new JButton(actionLabel);
    JButton help = GuiUtils.createHelpButton(helpTopic);
    EmbeddedWizardPanel panel =
        new EmbeddedWizardPanel(
            createPages(),
            GuiUtils.getFlowLayoutPanel(
                FlowLayout.TRAILING,
                AbstractWizardDialog.HORIZONTAL_GAP,
                AbstractWizardDialog.VERTICAL_GAP,
                help,
                action));
    action.addActionListener(
        _ -> {
          AbstractItemDialogPage page = panel.getSelectedPage();
          if (page != null) {
            run(page);
          }
        });
    panel.selectPage(LocalPersistence.getProperties().getProperty(lastPageKey()));
    wizard = panel;
    add(panel, BorderLayout.CENTER);
    revalidate();
    repaint();
  }

  private void release() {
    if (wizard == null) {
      return;
    }
    if (wizard.getSelectedPage() != null) {
      LocalPersistence.getProperties()
          .setProperty(lastPageKey(), wizard.getSelectedPage().getTitle());
    }
    wizard.closeAllPages();
    remove(wizard);
    wizard = null;
  }

  private String lastPageKey() {
    return "last.dicom." + getToolId() + ".page"; // NON-NLS
  }
}
