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

import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import net.miginfocom.swing.MigLayout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.event.Event;
import org.weasis.core.api.event.EventBus;
import org.weasis.core.api.event.EventFilter;
import org.weasis.core.api.event.Subscription;
import org.weasis.core.api.event.WeasisEvents;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.setting.Settings;
import org.weasis.core.api.util.FontItem;
import org.weasis.dicom.explorer.Messages;
import org.weasis.dicom.explorer.hp.Checkpoint;
import org.weasis.dicom.explorer.hp.CheckpointService;
import org.weasis.dicom.explorer.hp.HpSettings;

/**
 * The discreet notice at the bottom of the explorer after a remote batch changed the display: "An
 * agent changed the display. Restore". It appears once the batch has settled (no remote command for
 * a moment) and only when the display differs from the checkpoint taken at the boundary; it goes
 * away on the next user action, or restores the checkpoint when asked. The setting {@link
 * HpSettings#REMOTE_NOTICE} turns it off.
 */
public final class RemoteChangeNotice extends JPanel {

  private static final Logger LOGGER = LoggerFactory.getLogger(RemoteChangeNotice.class);

  /** How long after the last remote command the batch is considered settled. */
  static final int SETTLE_MS = 1_500;

  private final Supplier<CheckpointService> service;
  private final JLabel text = new JLabel(Messages.getString("RemoteChangeNotice.changed"));
  private final JButton restore = new JButton(Messages.getString("RemoteChangeNotice.restore"));
  private final JButton dismiss = new JButton(Messages.getString("RemoteChangeNotice.dismiss"));
  private final Timer settle = new Timer(SETTLE_MS, _ -> evaluate());
  private final AWTEventListener dismisser = this::userActed;
  private volatile Checkpoint pending;
  private boolean listening;

  public RemoteChangeNotice() {
    this(CheckpointService::global, EventBus.global());
  }

  RemoteChangeNotice(Supplier<CheckpointService> service, EventBus bus) {
    super(new MigLayout("insets 2 5 2 5, wrap 1, gapy 2", "[grow,fill]")); // NON-NLS
    this.service = service;
    setBorder(
        BorderFactory.createMatteBorder(
            1, 0, 0, 0, UIManager.getColor("Component.borderColor"))); // NON-NLS
    text.setFont(FontItem.SMALL_SEMIBOLD.getFont());
    for (JButton button : List.of(restore, dismiss)) {
      button.setFont(FontItem.SMALL.getFont());
      button.setMargin(new java.awt.Insets(1, 6, 1, 6));
      button.setFocusable(false);
    }
    restore.addActionListener(_ -> restore());
    dismiss.addActionListener(_ -> dismissNotice());
    add(text);
    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 5, 0));
    buttons.setOpaque(false);
    buttons.add(restore);
    buttons.add(dismiss);
    add(buttons);
    settle.setRepeats(false);
    setVisible(false);
    service.get().onRemoteBoundary(this::remoteBoundary);
    bus.subscribe(
        EventFilter.parse(WeasisEvents.COMMAND_FINISHED),
        Subscription.Delivery.ASYNC,
        "explorer.notice", // NON-NLS
        this::commandFinished);
  }

  /** The checkpoint of the latest remote boundary the notice waits on, for a test. */
  Checkpoint pending() {
    return pending;
  }

  private void remoteBoundary(Checkpoint checkpoint) {
    pending = checkpoint;
    GuiExecutor.execute(settle::restart);
  }

  private void commandFinished(Event event) {
    if (pending != null && !Event.APP_ORIGIN.equals(event.origin())) {
      GuiExecutor.execute(settle::restart);
    }
  }

  /** The batch settled: the notice shows when the display differs from the boundary. */
  void evaluate() {
    Checkpoint checkpoint = pending;
    if (checkpoint == null || !Settings.get(HpSettings.REMOTE_NOTICE)) {
      return;
    }
    try {
      if (service.get().diffFromNow(checkpoint).isEmpty()) {
        return;
      }
    } catch (RuntimeException e) {
      LOGGER.debug("No notice: the display cannot be compared", e);
      return;
    }
    showNotice();
  }

  private void showNotice() {
    text.setText(Messages.getString("RemoteChangeNotice.changed"));
    restore.setEnabled(true);
    setVisible(true);
    revalidate();
    repaint();
    if (!listening) {
      Toolkit.getDefaultToolkit()
          .addAWTEventListener(dismisser, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
      listening = true;
    }
  }

  void dismissNotice() {
    pending = null;
    if (listening) {
      Toolkit.getDefaultToolkit().removeAWTEventListener(dismisser);
      listening = false;
    }
    if (isVisible()) {
      setVisible(false);
      revalidate();
      repaint();
    }
  }

  /** The next user action anywhere but on the notice dismisses it. */
  private void userActed(AWTEvent event) {
    if (!(event instanceof InputEvent input)) {
      return;
    }
    boolean action =
        input.getID() == MouseEvent.MOUSE_PRESSED || input.getID() == KeyEvent.KEY_PRESSED;
    if (!action
        || (input.getSource() instanceof Component c && SwingUtilities.isDescendingFrom(c, this))) {
      return;
    }
    dismissNotice();
  }

  private void restore() {
    Checkpoint checkpoint = pending;
    if (checkpoint == null) {
      dismissNotice();
      return;
    }
    text.setText(Messages.getString("RemoteChangeNotice.restoring"));
    restore.setEnabled(false);
    CompletableFuture.supplyAsync(() -> service.get().rollback(checkpoint, false))
        .whenComplete(
            (result, error) ->
                GuiExecutor.execute(
                    () -> {
                      dismissNotice();
                      if (error != null) {
                        LOGGER.warn("Cannot restore the display", error);
                        JOptionPane.showMessageDialog(
                            SwingUtilities.getWindowAncestor(this),
                            error.getCause() == null
                                ? error.getMessage()
                                : error.getCause().getMessage(),
                            Messages.getString("RemoteChangeNotice.restore"),
                            JOptionPane.WARNING_MESSAGE);
                      } else if (!result.notRestored().isEmpty()) {
                        showPartial(this, result.notRestored());
                      }
                    }));
  }

  /** The dialog of a rollback that could not restore everything: it lists what stays. */
  public static void showPartial(Component parent, List<String> notRestored) {
    StringBuilder message = new StringBuilder(Messages.getString("RemoteChangeNotice.partial"));
    notRestored.forEach(line -> message.append("\n- ").append(line));
    JOptionPane.showMessageDialog(
        parent == null ? null : SwingUtilities.getWindowAncestor(parent),
        message.toString(),
        Messages.getString("RemoteChangeNotice.restore"),
        JOptionPane.INFORMATION_MESSAGE);
  }
}
