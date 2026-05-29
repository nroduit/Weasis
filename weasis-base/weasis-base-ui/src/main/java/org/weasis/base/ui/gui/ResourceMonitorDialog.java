/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.ui.gui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import net.miginfocom.swing.MigLayout;
import org.weasis.base.ui.Messages;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.gui.util.GuiUtils.IconColor;
import org.weasis.core.api.util.GraphicsInfo;
import org.weasis.core.api.util.ResourceAdvisor;
import org.weasis.core.api.util.ResourceAdvisor.Assessment;
import org.weasis.core.api.util.ResourceAdvisor.Level;
import org.weasis.core.api.util.ResourceAdvisor.Reason;
import org.weasis.core.api.util.ResourceAdvisor.Recommendation;
import org.weasis.core.api.util.ResourceAdvisor.Report;
import org.weasis.core.api.util.ResourceMonitor;
import org.weasis.core.api.util.ResourceMonitor.Snapshot;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;

/**
 * Live dialog showing the pressure on the machine's resources, a verdict on whether the hardware
 * fits the user's practice, the limiting events observed, and an exportable upgrade recommendation.
 */
public class ResourceMonitorDialog extends JDialog {

  private static final int REFRESH_DELAY_MS = 2000;

  private final JProgressBar heapBar = new JProgressBar(0, 100);
  private final JProgressBar nativeBar = new JProgressBar(0, 100);
  private final JProgressBar cpuBar = new JProgressBar(0, 100);

  private final JLabel overallValue = new JLabel();
  private final JLabel memoryValue = new JLabel();
  private final JLabel cpuValue = new JLabel();
  private final JLabel recommendationValue = new JLabel();

  private final JLabel uptimeValue = new JLabel();
  private final JLabel evictionsValue = new JLabel();
  private final JLabel oomValue = new JLabel();
  private final JLabel diskFallbackValue = new JLabel();
  private final JLabel gcValue = new JLabel();

  private final JLabel largestImageValue = new JLabel();
  private final JLabel largestVolumeValue = new JLabel();

  private final transient Timer timer;

  public ResourceMonitorDialog(Frame owner) {
    super(owner, Messages.getString("ResourceMonitor.title"), true);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

    JPanel content = new JPanel(new MigLayout("insets 15, wrap 2", "[]15[grow,fill]", ""));
    Snapshot first = ResourceMonitor.getInstance().snapshot();

    addSection(content, "ResourceMonitor.hardware");
    addRow(content, "ResourceMonitor.os", new JLabel(systemDescription()));
    addRow(
        content,
        "ResourceMonitor.cpuCores",
        new JLabel(Integer.toString(Runtime.getRuntime().availableProcessors())));
    addRow(content, "ResourceMonitor.physicalRam", new JLabel(bytes(first.physicalTotalMemory())));
    addRow(content, "ResourceMonitor.heapMax", new JLabel(bytes(first.heapMax())));
    addRow(content, "ResourceMonitor.nativeBudget", new JLabel(bytes(first.nativeBudget())));
    addGpuRows(content);

    addSection(content, "ResourceMonitor.pressure");
    heapBar.setStringPainted(true);
    nativeBar.setStringPainted(true);
    cpuBar.setStringPainted(true);
    addRow(content, "ResourceMonitor.heap", heapBar);
    addRow(content, "ResourceMonitor.nativeMem", nativeBar);
    addRow(content, "ResourceMonitor.cpuLoad", cpuBar);

    addSection(content, "ResourceMonitor.assessment");
    addRow(content, "ResourceMonitor.overall", overallValue);
    addRow(content, "ResourceMonitor.memory", memoryValue);
    addRow(content, "ResourceMonitor.cpu", cpuValue);
    addRow(content, "ResourceMonitor.recommendation", recommendationValue);

    addSection(content, "ResourceMonitor.workload");
    addRow(content, "ResourceMonitor.largestImage", largestImageValue);
    addRow(content, "ResourceMonitor.largestVolume", largestVolumeValue);

    addSection(content, "ResourceMonitor.events");
    addRow(content, "ResourceMonitor.uptime", uptimeValue);
    addRow(content, "ResourceMonitor.evictions", evictionsValue);
    addRow(content, "ResourceMonitor.oomEvents", oomValue);
    addRow(content, "ResourceMonitor.diskFallbacks", diskFallbackValue);
    addRow(content, "ResourceMonitor.gcOverhead", gcValue);

    JButton helpButton = GuiUtils.createHelpButton("../basics/system-resources/"); // NON-NLS
    JButton copyButton = new JButton(Messages.getString("ResourceMonitor.copyReport"));
    copyButton.addActionListener(e -> copyReport());
    JButton closeButton = new JButton(Messages.getString("WeasisAboutBox.close"));
    closeButton.addActionListener(e -> dispose());
    content.add(helpButton, "span 2, split 3, gaptop 15");
    content.add(copyButton, "gapbefore push");
    content.add(closeButton);

    setContentPane(content);

    timer = new Timer(REFRESH_DELAY_MS, e -> refresh());
    addHierarchyListener(
        e -> {
          if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
            if (isShowing()) {
              timer.start();
              closeButton.requestFocusInWindow();
            } else {
              timer.stop();
            }
          }
        });
    refresh();
    pack();
    setResizable(false);
  }

  private void refresh() {
    Snapshot snapshot = ResourceMonitor.getInstance().snapshot();
    Report report = ResourceAdvisor.evaluate(snapshot);

    updateBar(heapBar, snapshot.heapUsed(), snapshot.heapMax(), snapshot.heapPeakUsed());
    updateBar(nativeBar, snapshot.nativeUsed(), snapshot.nativeBudget(), -1);
    updateCpuBar(snapshot);

    applyVerdict(overallValue, report.overall(), true);
    applyVerdict(memoryValue, report.memory(), false);
    applyVerdict(cpuValue, report.cpu(), false);
    recommendationValue.setText(recommendationText(report.recommendation(), snapshot));
    recommendationValue.setForeground(
        report.recommendation().isEmpty() ? defaultForeground() : levelColor(Level.SUBOPTIMAL));

    uptimeValue.setText(uptimeText(snapshot));
    evictionsValue.setText(Long.toString(snapshot.cacheEvictions()));
    // Color an event red only when it is the signal currently driving a suboptimal verdict;
    // a non-zero but rare count is informational, not a problem.
    Reason memoryReason = report.memory().reason();
    setEventValue(oomValue, snapshot.outOfMemoryEvents(), memoryReason == Reason.MEM_OUT_OF_MEMORY);
    setEventValue(
        diskFallbackValue,
        snapshot.volumeDiskFallbacks(),
        memoryReason == Reason.MEM_VOLUME_DISK_FALLBACK);
    gcValue.setText(percent(snapshot.peakGcOverhead()));
    gcValue.setForeground(
        snapshot.peakGcOverhead() > 0.10 ? levelColor(Level.SUBOPTIMAL) : defaultForeground());

    largestImageValue.setText(bytes(snapshot.largestImageBytes()));
    largestVolumeValue.setText(volumeText(snapshot.largestVolumeSlices()));
  }

  private static void applyVerdict(JLabel label, Assessment assessment, boolean bold) {
    label.setText(verdictText(assessment));
    label.setForeground(levelColor(assessment.level()));
    if (bold) {
      label.setFont(label.getFont().deriveFont(Font.BOLD));
    }
  }

  private static void updateBar(JProgressBar bar, long used, long limit, long peak) {
    int pct = limit <= 0 ? 0 : (int) (used * 100 / limit);
    bar.setValue(Math.min(100, pct));
    String text = bytes(used) + " / " + bytes(limit) + "  (" + pct + "%)";
    if (peak >= 0) {
      text += "  -  " + Messages.getString("ResourceMonitor.peak") + ' ' + bytes(peak);
    }
    bar.setString(text);
    bar.setForeground(pct >= 90 ? levelColor(Level.SUBOPTIMAL) : null);
  }

  private void updateCpuBar(Snapshot snapshot) {
    double load = snapshot.processCpuLoad();
    int pct = load < 0 ? 0 : (int) Math.round(load * 100);
    cpuBar.setValue(Math.min(100, pct));
    cpuBar.setString(
        (load < 0 ? "-" : pct + "%")
            + "  -  "
            + Messages.getString("ResourceMonitor.peak")
            + ' '
            + (int) Math.round(snapshot.peakProcessCpuLoad() * 100)
            + '%');
  }

  private static void setEventValue(JLabel label, long count, boolean problematic) {
    label.setText(Long.toString(count));
    label.setForeground(problematic ? levelColor(Level.SUBOPTIMAL) : defaultForeground());
  }

  private void copyReport() {
    Snapshot snapshot = ResourceMonitor.getInstance().snapshot();
    String report = buildTextReport(snapshot, ResourceAdvisor.evaluate(snapshot));
    Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(report), null);
    JOptionPane.showMessageDialog(
        this,
        Messages.getString("ResourceMonitor.reportCopied"),
        getTitle(),
        JOptionPane.INFORMATION_MESSAGE);
  }

  private String buildTextReport(Snapshot s, Report report) {
    StringBuilder sb = new StringBuilder();
    sb.append("Weasis - ").append(Messages.getString("ResourceMonitor.title")).append('\n');
    sb.append("=================================\n");
    sb.append(label("ResourceMonitor.os")).append(systemDescription()).append('\n');
    sb.append(label("ResourceMonitor.cpuCores")).append(s.cpuCores()).append('\n');
    sb.append(label("ResourceMonitor.physicalRam"))
        .append(bytes(s.physicalTotalMemory()))
        .append('\n');
    sb.append(label("ResourceMonitor.heapMax")).append(bytes(s.heapMax())).append('\n');
    sb.append(label("ResourceMonitor.nativeBudget")).append(bytes(s.nativeBudget())).append('\n');
    GraphicsInfo.get()
        .ifPresent(
            gpu ->
                sb.append(label("ResourceMonitor.gpu"))
                    .append(gpu.renderer())
                    .append(gpu.softwareRendered() ? " (software)" : "")
                    .append('\n'));
    sb.append(label("ResourceMonitor.uptime")).append(uptimeText(s)).append('\n');
    sb.append('\n');
    sb.append(label("ResourceMonitor.overall")).append(verdictText(report.overall())).append('\n');
    sb.append(label("ResourceMonitor.memory")).append(verdictText(report.memory())).append('\n');
    sb.append(label("ResourceMonitor.cpu")).append(verdictText(report.cpu())).append('\n');
    sb.append(label("ResourceMonitor.recommendation"))
        .append(recommendationText(report.recommendation(), s))
        .append('\n');
    sb.append('\n');
    sb.append(label("ResourceMonitor.largestImage"))
        .append(bytes(s.largestImageBytes()))
        .append('\n');
    sb.append(label("ResourceMonitor.largestVolume"))
        .append(volumeText(s.largestVolumeSlices()))
        .append('\n');
    sb.append(label("ResourceMonitor.evictions")).append(s.cacheEvictions()).append('\n');
    sb.append(label("ResourceMonitor.oomEvents")).append(s.outOfMemoryEvents()).append('\n');
    sb.append(label("ResourceMonitor.diskFallbacks")).append(s.volumeDiskFallbacks()).append('\n');
    sb.append(label("ResourceMonitor.gcOverhead")).append(percent(s.peakGcOverhead())).append('\n');
    return sb.toString();
  }

  private static String label(String key) {
    return Messages.getString(key) + StringUtil.COLON_AND_SPACE;
  }

  private static String verdictText(Assessment assessment) {
    return Messages.getString(levelKey(assessment.level()))
        + " - "
        + Messages.getString(reasonKey(assessment.reason()));
  }

  private static String recommendationText(Recommendation rec, Snapshot s) {
    if (rec.isEmpty()) {
      return Messages.getString("ResourceMonitor.none");
    }
    StringBuilder sb = new StringBuilder();
    String current = Messages.getString("ResourceMonitor.current");
    if (rec.hasHeapAdvice()) {
      sb.append(bytes(rec.recommendedHeapBytes()))
          .append(' ')
          .append(Messages.getString("ResourceMonitor.heap"))
          .append(" (-Xmx, ")
          .append(current)
          .append(' ')
          .append(bytes(s.heapMax()))
          .append(')');
    }
    if (rec.hasRamAdvice()) {
      if (!sb.isEmpty()) {
        sb.append(", ");
      }
      sb.append(bytes(rec.recommendedRamBytes()))
          .append(' ')
          .append(Messages.getString("ResourceMonitor.ram"))
          .append(" (")
          .append(current)
          .append(' ')
          .append(bytes(s.physicalTotalMemory()))
          .append(')');
    }
    if (rec.hasCpuAdvice()) {
      if (!sb.isEmpty()) {
        sb.append(", ");
      }
      sb.append(rec.recommendedCores())
          .append(' ')
          .append(Messages.getString("ResourceMonitor.cpu"))
          .append(" (")
          .append(current)
          .append(' ')
          .append(s.cpuCores())
          .append(')');
    }
    return sb.toString();
  }

  private static String uptimeText(Snapshot s) {
    return duration(s.uptimeMillis())
        + "  ("
        + duration(s.totalUptimeMillis())
        + " / "
        + s.sessionCount()
        + ' '
        + Messages.getString("ResourceMonitor.sessions")
        + ')';
  }

  private static String volumeText(int slices) {
    return slices <= 0
        ? Messages.getString("ResourceMonitor.none")
        : slices + " " + Messages.getString("ResourceMonitor.slices");
  }

  private static void addSection(JPanel panel, String key) {
    JLabel header = new JLabel(Messages.getString(key));
    header.setFont(header.getFont().deriveFont(Font.BOLD));
    panel.add(header, "span 2, gaptop 12");
  }

  private static void addRow(JPanel panel, String key, Component value) {
    panel.add(new JLabel(Messages.getString(key) + StringUtil.COLON), "gapleft 10");
    panel.add(value);
  }

  private static void addGpuRows(JPanel panel) {
    GraphicsInfo.get()
        .ifPresentOrElse(
            gpu -> {
              JLabel renderer = new JLabel(gpu.renderer());
              if (gpu.softwareRendered()) {
                renderer.setText(
                    gpu.renderer() + " - " + Messages.getString("ResourceMonitor.gpuSoftware"));
                renderer.setForeground(IconColor.ACTIONS_RED.getColor());
              }
              addRow(panel, "ResourceMonitor.gpu", renderer);
              addRow(panel, "ResourceMonitor.openglVersion", new JLabel(gpu.glVersion()));
            },
            () ->
                addRow(
                    panel,
                    "ResourceMonitor.gpu",
                    new JLabel(Messages.getString("ResourceMonitor.gpuNotAssessed"))));
  }

  private static String levelKey(Level level) {
    return switch (level) {
      case COLLECTING -> "ResourceMonitor.level.collecting";
      case SUBOPTIMAL -> "ResourceMonitor.level.suboptimal";
      case OPTIMAL -> "ResourceMonitor.level.optimal";
      case ABUNDANT -> "ResourceMonitor.level.abundant";
    };
  }

  private static String reasonKey(Reason reason) {
    return switch (reason) {
      case COLLECTING -> "ResourceMonitor.reason.collecting";
      case MEM_OUT_OF_MEMORY -> "ResourceMonitor.reason.oom";
      case MEM_VOLUME_DISK_FALLBACK -> "ResourceMonitor.reason.diskFallback";
      case MEM_HIGH_GC -> "ResourceMonitor.reason.highGc";
      case MEM_LIGHTLY_USED -> "ResourceMonitor.reason.lightlyUsed";
      case MEM_WITHIN_LIMITS -> "ResourceMonitor.reason.withinLimits";
      case CPU_FEW_CORES -> "ResourceMonitor.reason.fewCores";
      case CPU_MANY_IDLE_CORES -> "ResourceMonitor.reason.manyIdleCores";
      case CPU_ADEQUATE -> "ResourceMonitor.reason.adequate";
    };
  }

  private static Color levelColor(Level level) {
    return switch (level) {
      case SUBOPTIMAL -> IconColor.ACTIONS_RED.getColor();
      case OPTIMAL -> IconColor.ACTIONS_GREEN.getColor();
      case ABUNDANT -> IconColor.ACTIONS_BLUE.getColor();
      case COLLECTING -> defaultForeground();
    };
  }

  private static Color defaultForeground() {
    Color color = UIManager.getColor("Label.foreground");
    return color != null ? color : Color.GRAY;
  }

  private static String systemDescription() {
    return System.getProperty("os.name")
        + ' '
        + System.getProperty("os.version")
        + " ("
        + System.getProperty("os.arch")
        + ')';
  }

  private static String bytes(long value) {
    return value <= 0 ? "-" : FileUtil.humanReadableByte(value, false);
  }

  private static String percent(double fraction) {
    return String.format("%.1f%%", fraction * 100);
  }

  private static String duration(long millis) {
    long minutes = millis / 60_000;
    if (minutes < 60) {
      return minutes + " min";
    }
    return (minutes / 60) + " h " + (minutes % 60) + " min";
  }
}
