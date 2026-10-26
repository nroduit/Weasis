/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.docking.ws.WorkspaceListener;
import org.weasis.core.ui.docking.ws.WorkspaceManager;
import org.weasis.core.ui.docking.ws.WorkspaceView;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.hp.HangingEngine.ProtocolScore;
import org.weasis.dicom.explorer.hp.ActiveHanging;
import org.weasis.dicom.explorer.hp.HangingPlanApplier;
import org.weasis.dicom.explorer.hp.HangingPlanApplier.Applied;
import org.weasis.dicom.explorer.hp.SessionActions;

/**
 * The protocol bar: the protocol that hung the selected viewer with the other candidates of the
 * study, the reading step shown with previous and next, and a button that hangs the step again.
 * Everything goes through {@link ActiveHanging}, as the {@code hp:group} verb does. Disabled when
 * the selected viewer was not hung by a protocol.
 */
public class HangingProtocolBar extends WtoolBar {

  private static final Logger LOGGER = LoggerFactory.getLogger(HangingProtocolBar.class);

  /** A candidate in the combo box: shown by name, identified by id. */
  private record Choice(String id, String label) {
    @Override
    public String toString() {
      return label;
    }
  }

  /** The id prefix of the choice that restores the kept session of the study. */
  private static final String SESSION = "session:"; // NON-NLS

  private final JComboBox<Choice> protocols = new JComboBox<>();
  private final JLabel step = new JLabel();
  private final JButton previous = new JButton();
  private final JButton next = new JButton();
  private final JButton again = new JButton();
  private final JButton save = new JButton();
  private boolean updating;

  public HangingProtocolBar(int index) {
    super(Messages.getString("HangingProtocolBar.title"), index);
    protocols.setToolTipText(Messages.getString("HangingProtocolBar.protocol"));
    protocols.setMaximumRowCount(12);
    protocols.setPrototypeDisplayValue(new Choice(null, "Mammography with priors")); // NON-NLS
    protocols.setMaximumSize(protocols.getPreferredSize());
    protocols.addActionListener(
        e -> {
          if (!updating && protocols.getSelectedItem() instanceof Choice choice) {
            if (choice.id() != null && choice.id().startsWith(SESSION)) {
              SessionActions.restore(choice.id().substring(SESSION.length()));
              refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
            } else {
              act(current -> ActiveHanging.protocol(current, choice.id()));
            }
          }
        });
    add(protocols);

    previous.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.PREVIOUS));
    previous.setToolTipText(Messages.getString("HangingProtocolBar.previous"));
    previous.addActionListener(e -> act(ActiveHanging::previous));
    add(previous);

    add(step);

    next.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.NEXT));
    next.setToolTipText(Messages.getString("HangingProtocolBar.next"));
    next.addActionListener(e -> act(ActiveHanging::next));
    add(next);

    again.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.RESET));
    again.setToolTipText(Messages.getString("HangingProtocolBar.again"));
    again.addActionListener(e -> act(current -> ActiveHanging.step(current, current.step())));
    add(again);

    save.setIcon(ResourceUtil.getToolBarIcon(ActionIcon.REDACT_SAVE));
    save.setToolTipText(Messages.getString("HangingProtocolBar.save"));
    save.addActionListener(
        e -> {
          ImageViewerPlugin<DicomImageElement> container =
              EventManager.getInstance().getSelectedView2dContainer();
          if (container != null) {
            SaveProtocolDialog.open(container);
          }
        });
    add(save);

    WorkspaceManager manager = GuiUtils.getUICore().getWorkspaceManager();
    if (manager != null) {
      manager.addListener(
          new WorkspaceListener() {
            @Override
            public void viewSelected(WorkspaceView view, Window window) {
              refresh(view instanceof ViewerPlugin<?> plugin ? plugin : null);
            }
          });
    }
    HangingPlanApplier.addListener(
        applied -> {
          ViewerPlugin<?> selected = GuiUtils.getUICore().getSelectedPlugin().orElse(null);
          if (selected != null && applied.viewers().contains(selected)) {
            refresh(selected);
          }
        });
    refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
  }

  private void act(java.util.function.UnaryOperator<Applied> action) {
    Optional<Applied> current = ActiveHanging.of(null);
    if (current.isEmpty()) {
      refresh(null);
      return;
    }
    try {
      Applied applied = action.apply(current.get());
      applied.container().ifPresent(ViewerPlugin::setSelectedAndGetFocus);
      refresh(applied.container().orElse(null));
    } catch (RuntimeException e) {
      LOGGER.warn("Cannot change the hanging", e);
      refresh(GuiUtils.getUICore().getSelectedPlugin().orElse(null));
    }
  }

  /** Shows the hanging of a viewer, or disables the bar when it has none. */
  public void refresh(ViewerPlugin<?> viewer) {
    Optional<Applied> active = viewer == null ? Optional.empty() : ActiveHanging.of(viewer);
    updating = true;
    try {
      if (active.isEmpty()) {
        protocols.setModel(
            new DefaultComboBoxModel<>(
                new Choice[] {new Choice(null, Messages.getString("HangingProtocolBar.none"))}));
        protocols.setEnabled(false);
        step.setText("");
        previous.setEnabled(false);
        next.setEnabled(false);
        again.setEnabled(false);
        save.setEnabled(
            viewer instanceof ImageViewerPlugin<?> image && image.supportsViewportPanes());
        return;
      }
      Applied applied = active.get();
      List<Choice> choices = new ArrayList<>();
      Choice selected = null;
      for (ProtocolScore score : ActiveHanging.candidates(applied)) {
        String name = score.document().protocol().getHangingProtocolName();
        Choice choice =
            new Choice(score.document().id(), name == null ? score.document().id() : name);
        choices.add(choice);
        if (choice.id().equals(applied.plan().protocolId())) {
          selected = choice;
        }
      }
      if (selected == null) {
        String name = applied.plan().name();
        selected =
            new Choice(
                applied.plan().protocolId(), name == null ? applied.plan().protocolId() : name);
        choices.addFirst(selected);
      }
      SessionActions.offerFor(applied.context().study())
          .ifPresent(
              session ->
                  choices.add(
                      new Choice(
                          SESSION + session.id(),
                          String.format(
                              Messages.getString("HangingProtocolBar.session"),
                              SessionActions.label(session)))));
      protocols.setModel(new DefaultComboBoxModel<>(choices.toArray(Choice[]::new)));
      protocols.setSelectedItem(selected);
      protocols.setEnabled(choices.size() > 1);
      int steps = applied.plan().steps().size();
      step.setText(
          String.format(Messages.getString("HangingProtocolBar.step"), applied.step() + 1, steps));
      previous.setEnabled(steps > 1);
      next.setEnabled(steps > 1);
      again.setEnabled(true);
      save.setEnabled(true);
    } finally {
      updating = false;
    }
  }
}
