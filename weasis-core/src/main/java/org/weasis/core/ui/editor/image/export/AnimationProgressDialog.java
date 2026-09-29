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
import static org.weasis.core.api.gui.Insertable.ITEM_SEPARATOR_LARGE;

import java.awt.FlowLayout;
import java.awt.Window;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.concurrent.CancellationException;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingWorker;
import javax.swing.SwingWorker.StateValue;
import javax.swing.WindowConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.util.StringUtil;

/** Progress and cancellation for a running {@link AnimationExportTask}. */
public class AnimationProgressDialog extends JDialog implements PropertyChangeListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(AnimationProgressDialog.class);

  private final JProgressBar progressBar = new JProgressBar(0, 100);
  private final transient SwingWorker<?, ?> worker;

  private AnimationProgressDialog(
      Window parent, String title, SwingWorker<?, ?> worker, Runnable onCancel) {
    super(parent, title, ModalityType.APPLICATION_MODAL);
    this.worker = worker;
    setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    progressBar.setStringPainted(true);
    progressBar.setIndeterminate(onCancel == null);

    JPanel panel = GuiUtils.getVerticalBoxLayoutPanel();
    panel.setBorder(GuiUtils.getEmptyBorder(BLOCK_SEPARATOR));
    panel.add(
        GuiUtils.getFlowLayoutPanel(
            new JLabel(Messages.getString("animation.encoding") + StringUtil.COLON)));
    panel.add(GuiUtils.getFlowLayoutPanel(progressBar));
    if (onCancel != null) {
      JButton cancelButton = new JButton(Messages.getString("cancel"));
      cancelButton.addActionListener(_ -> onCancel.run());
      panel.add(
          GuiUtils.getFlowLayoutPanel(
              FlowLayout.TRAILING, BLOCK_SEPARATOR, ITEM_SEPARATOR_LARGE, cancelButton));
    }
    setContentPane(panel);
    pack();
    setLocationRelativeTo(parent);
  }

  /** Runs {@code task} and blocks until it completes or is cancelled. */
  public static void run(Window parent, String title, AnimationExportTask task) {
    run(parent, title, task, task::stop);
  }

  /** Runs {@code worker} and blocks until it completes; {@code onCancel} may be {@code null}. */
  public static void run(Window parent, String title, SwingWorker<?, ?> worker, Runnable onCancel) {
    AnimationProgressDialog dialog = new AnimationProgressDialog(parent, title, worker, onCancel);
    worker.addPropertyChangeListener(dialog);
    worker.execute();
    dialog.setVisible(true);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if ("progress".equals(evt.getPropertyName())) { // NON-NLS
      progressBar.setValue((Integer) evt.getNewValue());
    } else if (StateValue.DONE.equals(evt.getNewValue())) {
      dispose();
      reportFailure();
    }
  }

  private void reportFailure() {
    try {
      worker.get();
    } catch (CancellationException e) {
      LOGGER.info("Animation export cancelled");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      LOGGER.error("Cannot export the animation", e);
      JOptionPane.showMessageDialog(
          getOwner(),
          e.getCause() == null ? e.getMessage() : e.getCause().getMessage(),
          getTitle(),
          JOptionPane.ERROR_MESSAGE);
    }
  }
}
