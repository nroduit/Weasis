/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.launcher;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import com.formdev.flatlaf.util.SystemInfo;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicComboPopup;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.ActionIcon;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.image.ImageViewerEventManager;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.core.ui.util.UriListFlavor;
import org.weasis.core.util.StringUtil;

public class Launcher {
  private static final Logger LOGGER = LoggerFactory.getLogger(Launcher.class);

  public enum Compatibility {
    LINUX("Linux"), // NON-NLS
    MAC("macOS"), // NON-NLS
    WINDOWS("Windows"); // NON-NLS

    final String title;

    Compatibility(String title) {
      this.title = title;
    }

    @Override
    public String toString() {
      return title;
    }
  }

  public enum Type {
    DICOM(Messages.getString("dicom.launcher"), "dicomLaunchers.json"),
    OTHER(Messages.getString("other.launcher"), "otherLaunchers.json");

    final String title;
    final String filename;

    Type(String title, String filename) {
      this.title = title;
      this.filename = filename;
    }

    @Override
    public String toString() {
      return title;
    }

    public String getFilename() {
      return filename;
    }
  }

  private String name;
  private String iconPath;
  private boolean enable;
  private boolean button;

  private boolean local = true;

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "launchType")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = URIConfiguration.class, name = "URI"),
    @JsonSubTypes.Type(value = ApplicationConfiguration.class, name = "Application")
  })
  private Configuration configuration;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getIconPath() {
    return iconPath;
  }

  public void setIconPath(String iconPath) {
    this.iconPath = iconPath;
  }

  @JsonIgnore
  public Icon getIcon() {
    String path = iconPath;
    if (!StringUtil.hasText(path)) {
      path = ActionIcon.LAUNCH.getPath();
    }

    Icon icon = null;
    if (path.endsWith(".svg")) {
      FlatSVGIcon svgIcon = ResourceUtil.getIcon(path);
      if (svgIcon.hasFound()) {
        icon = svgIcon;
      }
    } else {
      File file = ResourceUtil.getResource(path);
      if (file.canRead()) {
        icon = new ImageIcon(file.getAbsolutePath());
      }
    }

    if (icon == null) {
      icon = ResourceUtil.getIcon(ActionIcon.LAUNCH);
    }
    return icon;
  }

  @JsonIgnore
  public Icon getResizeIcon(int width, int height) {
    Icon icon = getIcon();
    if (icon instanceof FlatSVGIcon) {
      return ((FlatSVGIcon) icon).derive(width, height);
    } else if (icon instanceof ImageIcon imageIcon) {
      return new ImageIcon(
          imageIcon
              .getImage()
              .getScaledInstance(
                  GuiUtils.getScaleLength(width),
                  GuiUtils.getScaleLength(height),
                  java.awt.Image.SCALE_SMOOTH));
    }
    return icon;
  }

  public boolean isEnable() {
    return enable;
  }

  public void setEnable(boolean enable) {
    this.enable = enable;
  }

  public boolean isButton() {
    return button;
  }

  public void setButton(boolean button) {
    this.button = button;
  }

  public Configuration getConfiguration() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  public boolean isLocal() {
    return local;
  }

  public void setLocal(boolean local) {
    this.local = local;
  }

  @Override
  public String toString() {
    return name;
  }

  public abstract static class Configuration {
    protected static final String DICOM_SEL =
        "{dicom:" + Placeholder.DICOM_COPY_FOLDER + "}"; // NON-NLS
    private final String launchType;

    private Path source;

    public Configuration(String launchType) {
      this.launchType = launchType;
    }

    public String getLaunchType() {
      return launchType;
    }

    @JsonIgnore
    public boolean isDicomSelectionAction() {
      return source != null && source.toString().contains(DICOM_SEL);
    }

    @JsonIgnore
    public Path getSource() {
      return source;
    }

    @JsonIgnore
    public void setSource(Path source) {
      this.source = source;
    }

    @JsonIgnore
    public abstract void launch(ImageViewerEventManager<?> eventManager);

    @JsonIgnore
    protected String resolvePlaceholders(String text, ImageViewerEventManager<?> eventManager) {
      String val = Placeholder.PREFERENCES_PLACEHOLDER.resolvePlaceholders(text, eventManager);
      if (source != null && val != null) {
        val = val.replaceAll("\\" + DICOM_SEL, source.toAbsolutePath().toString());
      }
      if (eventManager != null) {
        return eventManager.resolvePlaceholders(val);
      }
      return val;
    }

    @JsonIgnore
    void showInvalidField(Window parent) {
      if (this instanceof URIConfiguration) {
        LauncherDialog.ShowRequiredValue(parent, Messages.getString("uri"));
      } else {
        LauncherDialog.ShowRequiredValue(parent, Messages.getString("application"));
      }
    }

    @JsonIgnore
    public abstract boolean isValid();
  }

  public static class URIConfiguration extends Configuration {
    private String uri;

    public URIConfiguration() {
      super("URI");
    }

    public String getUri() {
      return uri;
    }

    public void setUri(String uri) {
      this.uri = uri;
    }

    @JsonIgnore
    public boolean isDicomSelectionAction() {
      boolean sel = super.isDicomSelectionAction();
      if (sel) {
        return true;
      }
      return uri != null && uri.contains(DICOM_SEL);
    }

    @Override
    public void launch(ImageViewerEventManager<?> eventManager) {
      try {
        String val = resolvePlaceholders(uri, eventManager);
        URI ref = new URI(val);
        GuiUtils.openInDefaultBrowser(GuiUtils.getUICore().getBaseArea(), ref);
      } catch (Exception e1) {
        LOGGER.error("Opening launcher", e1);
      }
    }

    @Override
    public boolean isValid() {
      if (!StringUtil.hasText(uri)) {
        return false;
      }
      return UriListFlavor.isValidURI(uri.replaceAll("\\{.*?}", ""));
    }
  }

  public static class ApplicationConfiguration extends Configuration {

    private String binaryPath;
    private List<String> parameters;
    private String workingDirectory;
    private Map<String, String> environmentVariables;
    private Compatibility compatibility;

    public ApplicationConfiguration() {
      super(Messages.getString("application"));
      if (SystemInfo.isWindows) {
        compatibility = Compatibility.WINDOWS;
      } else if (SystemInfo.isMacOS) {
        compatibility = Compatibility.MAC;
      } else {
        compatibility = Compatibility.LINUX;
      }
    }

    public String getBinaryPath() {
      return binaryPath;
    }

    public void setBinaryPath(String binaryPath) {
      this.binaryPath = binaryPath;
    }

    public List<String> getParameters() {
      return parameters;
    }

    public void setParameters(List<String> parameters) {
      this.parameters = parameters;
    }

    public String getWorkingDirectory() {
      return workingDirectory;
    }

    public void setWorkingDirectory(String workingDirectory) {
      this.workingDirectory = workingDirectory;
    }

    public Map<String, String> getEnvironmentVariables() {
      return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
      this.environmentVariables = environmentVariables;
    }

    public Compatibility getCompatibility() {
      return compatibility;
    }

    public void setCompatibility(Compatibility compatibility) {
      this.compatibility = compatibility;
    }

    @JsonIgnore
    public boolean isCompatibleWithCurrentSystem() {
      if (compatibility == null) {
        return true;
      }
      if (SystemInfo.isWindows) {
        return compatibility == Compatibility.WINDOWS;
      } else if (SystemInfo.isMacOS) {
        return compatibility == Compatibility.MAC;
      } else {
        return compatibility == Compatibility.LINUX;
      }
    }

    @JsonIgnore
    public boolean isDicomSelectionAction() {
      boolean sel = super.isDicomSelectionAction();
      if (sel) {
        return true;
      }
      if (parameters != null && !parameters.isEmpty()) {
        for (String param : parameters) {
          if (StringUtil.hasText(param) && param.contains(DICOM_SEL)) {
            return true;
          }
        }
      }
      if (environmentVariables != null && !environmentVariables.isEmpty()) {
        for (String val : environmentVariables.values()) {
          if (StringUtil.hasText(val) && val.contains(DICOM_SEL)) {
            return true;
          }
        }
      }
      return false;
    }

    public void launch(ImageViewerEventManager<?> eventManager) {
      if (!StringUtil.hasText(binaryPath)) {
        return;
      }
      boolean isMac = compatibility == Compatibility.MAC;
      List<String> command =
          new ArrayList<>(Arrays.asList(binaryPath.trim().split("\\s+"))); // NON-NLS
      if (!isMac && parameters != null && !parameters.isEmpty()) {
        for (String param : parameters) {
          command.add(resolvePlaceholders(param, eventManager));
        }
      }

      ProcessBuilder processBuilder = new ProcessBuilder(command);
      if (StringUtil.hasText(workingDirectory)) {
        processBuilder.directory(new File(workingDirectory));
      }
      if (environmentVariables != null && !environmentVariables.isEmpty()) {
        Map<String, String> environment = processBuilder.environment();
        for (Entry<String, String> entry : environmentVariables.entrySet()) {
          environment.put(entry.getKey(), resolvePlaceholders(entry.getValue(), eventManager));
        }
      }

      Thread launcherThread =
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      Process p = processBuilder.start();
                      BufferedReader buffer =
                          new BufferedReader(new InputStreamReader(p.getInputStream()));

                      String data;
                      int lineCount = 0;
                      while (lineCount < 5 && (data = buffer.readLine()) != null) {
                        System.out.println(data);
                        lineCount++;
                      }

                      int val = getExitValue(p);

                      if (isMac && parameters != null && !parameters.isEmpty()) {
                        for (String param : parameters) {
                          command.add(resolvePlaceholders(param, eventManager));
                        }
                        Thread.sleep(500);
                        p = processBuilder.start();
                        val = getExitValue(p);
                      }

                      if (val != 0) {
                        JOptionPane.showMessageDialog(
                            GuiUtils.getUICore().getBaseArea(),
                            String.format(Messages.getString("error.launching.app"), binaryPath),
                            Messages.getString("launcher.error"),
                            JOptionPane.ERROR_MESSAGE);
                      }
                    } catch (IOException e1) {
                      LOGGER.error("Running cmd", e1);
                    } catch (InterruptedException e2) {
                      LOGGER.error("Cannot get the exit status of {}", binaryPath, e2);
                      Thread.currentThread().interrupt();
                    }
                  });
      launcherThread.start();
    }

    private int getExitValue(Process p) throws InterruptedException {
      if (!p.waitFor(15, TimeUnit.SECONDS)) {
        LOGGER.warn("Process did not exit within the timeout, detaching the process.");
        return 0;
      } else {
        return p.exitValue();
      }
    }

    @Override
    public boolean isValid() {
      return StringUtil.hasText(binaryPath) && isCompatibleWithCurrentSystem();
    }
  }

  private String getToolTips() {
    return Messages.getString("name")
        + StringUtil.COLON_AND_SPACE
        + name
        + "\n"
        + Messages.getString("icon.path")
        + StringUtil.COLON_AND_SPACE
        + iconPath
        + "\n"
        + Messages.getString("enable")
        + StringUtil.COLON_AND_SPACE
        + enable
        + "\n"
        + Messages.getString("button")
        + StringUtil.COLON_AND_SPACE
        + button
        + "\n"
        + Messages.getString("launcher.typ")
        + StringUtil.COLON_AND_SPACE
        + configuration.launchType;
  }

  public static void addNodeActionPerformed(JComboBox<Launcher> comboBox, Type type) {
    JDialog dialog =
        new LauncherDialog(SwingUtilities.getWindowAncestor(comboBox), type, null, comboBox);
    GuiUtils.showCenterScreen(dialog, comboBox);
  }

  public static void editNodeActionPerformed(JComboBox<Launcher> comboBox, Type type) {
    int index = comboBox.getSelectedIndex();
    if (index >= 0) {
      Launcher node = comboBox.getItemAt(index);
      if (node.isLocal()) {
        JDialog dialog =
            new LauncherDialog(SwingUtilities.getWindowAncestor(comboBox), type, node, comboBox);
        GuiUtils.showCenterScreen(dialog, comboBox);
      } else {
        JOptionPane.showMessageDialog(
            comboBox,
            Messages.getString("only.user.created.item.modified"),
            Messages.getString("error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  public static void deleteNodeActionPerformed(JComboBox<Launcher> comboBox, Type type) {
    int index = comboBox.getSelectedIndex();
    if (index >= 0) {
      Launcher node = comboBox.getItemAt(index);
      if (node.isLocal()) {
        int response =
            JOptionPane.showConfirmDialog(
                comboBox,
                String.format(Messages.getString("really.want.delete"), node),
                Type.DICOM.toString(),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (response == 0) {
          comboBox.removeItemAt(index);
          getLaunchers(type).remove(node);
          saveLaunchers(type);
        }
      } else {
        JOptionPane.showMessageDialog(
            comboBox,
            Messages.getString("only.user.created.item.modified"),
            Messages.getString("error"),
            JOptionPane.ERROR_MESSAGE);
      }
    }
  }

  static List<Launcher> getLaunchers(Type type) {
    if (type == Type.OTHER) {
      return GuiUtils.getUICore().getOtherLaunchers();
    } else if (type == Type.DICOM) {
      return GuiUtils.getUICore().getDicomLaunchers();
    }
    return Collections.emptyList();
  }

  static void saveLaunchers(Type type) {
    List<Launcher> list = getLaunchers(type);
    final BundleContext context = AppProperties.getBundleContext(Launcher.class);
    File file = new File(BundlePreferences.getDataFolder(context), type.getFilename());
    try {
      ObjectMapper mapper = new ObjectMapper();
      mapper.writeValue(file, list);
    } catch (IOException e) {
      LOGGER.error("Cannot save the launcher configuration", e);
    }

    List<ViewerPlugin<?>> viewerPlugins = GuiUtils.getUICore().getViewerPlugins();
    if (viewerPlugins != null && !viewerPlugins.isEmpty()) {
      Set<SeriesViewerUI> uiSet =
          viewerPlugins.stream().map(ViewerPlugin::getSeriesViewerUI).collect(Collectors.toSet());
      for (SeriesViewerUI ui : uiSet) {
        if (ui.clazz.getPackageName().contains("dicom")) { // NON-NLS
          BundleTools.notifyDicomModel(ObservableEvent.BasicAction.UPDATE_TOOLBARS, ui);
        } else {
          BundleTools.notifyDefaultDataModel(ObservableEvent.BasicAction.UPDATE_TOOLBARS, ui);
        }
      }
    }
  }

  public static void addTooltipToComboList(final JComboBox<? extends Launcher> combo) {
    Object comp = combo.getUI().getAccessibleChild(combo, 0);
    if (comp instanceof final BasicComboPopup popup) {
      popup
          .getList()
          .getSelectionModel()
          .addListSelectionListener(
              e -> {
                if (!e.getValueIsAdjusting()) {
                  ListSelectionModel model = (ListSelectionModel) e.getSource();
                  int first = model.getMinSelectionIndex();
                  if (first >= 0) {
                    Launcher item = combo.getItemAt(first);
                    ((JComponent) combo.getRenderer()).setToolTipText(item.getToolTips());
                  }
                }
              });
    }
  }

  public static List<Action> getLauncherActions(
      ImageViewerEventManager<?> eventManager, Type type) {
    ArrayList<Action> actions = new ArrayList<>();

    List<Launcher> launchers = new ArrayList<>();
    if (type == Type.OTHER) {
      launchers.addAll(GuiUtils.getUICore().getOtherLaunchers());
    } else if (type == Type.DICOM) {
      launchers.addAll(GuiUtils.getUICore().getDicomLaunchers());
    } else {
      launchers.addAll(GuiUtils.getUICore().getOtherLaunchers());
      launchers.addAll(GuiUtils.getUICore().getDicomLaunchers());
    }

    for (Launcher launcher : launchers) {
      if (launcher.isEnable() && launcher.getConfiguration().isValid()) {
        String name = launcher.getName();
        Icon icon = launcher.getResizeIcon(16, 16);
        actions.add(new DefaultAction(name, icon, _ -> launcher.execute(eventManager)));
      }
    }
    return actions;
  }

  public void execute(ImageViewerEventManager<?> eventManager) {
    if (isEnable() && configuration != null && configuration.isValid()) {
      if (eventManager != null) {
        eventManager.dicomExportAction(this);
      }
      configuration.launch(eventManager);
      configuration.setSource(null);
    }
  }

  public static void loadLaunchers(JComboBox<Launcher> comboBox, Type type) {
    if (comboBox == null) {
      return;
    }
    if (type == Type.DICOM) {
      for (Launcher node : GuiUtils.getUICore().getDicomLaunchers()) {
        comboBox.addItem(node);
      }
    } else if (type == Type.OTHER) {
      for (Launcher node : GuiUtils.getUICore().getOtherLaunchers()) {
        comboBox.addItem(node);
      }
    }
  }

  public static List<Launcher> loadLaunchers(Type type) {
    List<Launcher> list = new ArrayList<>();
    loadLaunchers(list, ResourceUtil.getResource(type.getFilename()), false);
    AppProperties.getBundleContext(Launcher.class);
    final BundleContext context = AppProperties.getBundleContext(Launcher.class);
    loadLaunchers(
        list, new File(BundlePreferences.getDataFolder(context), type.getFilename()), true);
    return list;
  }

  private static void loadLaunchers(List<Launcher> list, File resource, boolean local) {
    if (resource.canRead()) {
      try {
        ObjectMapper mapper = new ObjectMapper();
        List<Launcher> nodes =
            mapper.readValue(
                resource,
                mapper.getTypeFactory().constructCollectionType(List.class, Launcher.class));
        for (Launcher node : nodes) {
          node.setLocal(local);
          list.add(node);
        }
      } catch (IOException e) {
        LOGGER.error("Cannot load the launcher configuration", e);
      }
    }
  }
}
