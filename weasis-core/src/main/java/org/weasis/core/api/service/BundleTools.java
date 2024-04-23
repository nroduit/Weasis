/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.service;

import java.util.Collections;
import java.util.List;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceReference;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.editor.SeriesViewerUI;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.Toolbar;

public class BundleTools {
  private static final Logger LOGGER = LoggerFactory.getLogger(BundleTools.class);

  private BundleTools() {}

  public static Codec<MediaElement> getCodec(String mimeType, String preferredCodec) {
    Codec<MediaElement> codec = null;
    List<Codec<MediaElement>> codecs = GuiUtils.getUICore().getCodecPlugins();
    synchronized (codecs) {
      for (Codec<MediaElement> c : codecs) {
        if (c.isMimeTypeSupported(mimeType)) {
          if (c.getCodecName().equals(preferredCodec)) {
            codec = c;
            break;
          }
          // If the preferred codec cannot be found, the first-found codec is retained
          if (codec == null) {
            codec = c;
          }
        }
      }
      return codec;
    }
  }

  public static String createServiceFilter(Class<?>... interfaces) {
    StringBuilder builder = new StringBuilder();

    builder.append("( |");
    for (Class<?> clazz : interfaces) {
      builder.append(String.format("(%s=%s) ", Constants.OBJECTCLASS, clazz.getName())); // NON-NLS
    }

    builder.append(" ) ");
    return builder.toString();
  }

  public static void dataExplorerChanged(final ServiceEvent event, SeriesViewerUI ui) {

    final ServiceReference<?> mref = event.getServiceReference();
    // The View2dContainer name should be referenced as a property in the provided service
    if (Boolean.parseBoolean((String) mref.getProperty(ui.clazz.getName()))) {
      final BundleContext context = FrameworkUtil.getBundle(ui.clazz).getBundleContext();
      if (context == null) {
        return;
      }
      GuiExecutor.execute(
          () -> {
            Object service = context.getService(mref);
            if (service instanceof InsertableFactory factory) {
              if (event.getType() == ServiceEvent.REGISTERED) {
                registerComponent(factory, ui);
              } else if (event.getType() == ServiceEvent.UNREGISTERING) {
                if (Type.TOOLBAR.equals(factory.getType())) {
                  unregisterToolBar(factory, context, ui);
                } else if (Type.TOOL.equals(factory.getType())) {
                  unregisterTool(factory, context, ui);
                } else if (Type.TOOL_EXT.equals(factory.getType())) {
                  unregisterDynamicTool(factory, ui);
                }
              }
            }
          });
    }
  }

  public static void registerExistingComponents(BundleContext bundleContext, SeriesViewerUI ui) {
    try {
      for (ServiceReference<InsertableFactory> serviceReference :
          bundleContext.getServiceReferences(InsertableFactory.class, null)) {
        // The View2dContainer name should be referenced as a property in the provided service
        if (Boolean.parseBoolean((String) serviceReference.getProperty(ui.clazz.getName()))) {
          // Instantiate UI components in EDT
          GuiExecutor.execute(
              () -> registerComponent(bundleContext.getService(serviceReference), ui));
        }
      }
    } catch (InvalidSyntaxException e1) {
      LOGGER.error("Register tool and toolbar", e1);
    }
  }

  private static void registerComponent(InsertableFactory factory, SeriesViewerUI ui) {
    if (factory == null) {
      return;
    }

    if (Type.TOOLBAR.equals(factory.getType())) {
      registerToolBar(factory.createInstance(null), ui);
    } else if (Type.TOOL.equals(factory.getType())) {
      registerTool(factory.createInstance(null), ui);
    } else if (Type.TOOL_EXT.equals(factory.getType())) {
      registerToolExt(factory, ui);
    }
  }

  private static void registerToolBar(Insertable instance, SeriesViewerUI ui) {
    List<Toolbar> toolBars = ui.getToolBars();
    if (instance instanceof Toolbar bar && !toolBars.contains(instance)) {
      toolBars.add(bar);
      if (ui.clazz.getPackageName().contains("dicom")) {
        notifyDicomModel(ObservableEvent.BasicAction.UPDATE_TOOLBARS, ui);
      } else {
        notifyDefaultDataModel(ObservableEvent.BasicAction.UPDATE_TOOLBARS, ui);
      }
      LOGGER.debug("Add Toolbar [{}] for {}", bar, ui.clazz.getName());
    }
  }

  private static void registerTool(Insertable instance, SeriesViewerUI ui) {
    List<DockableTool> tools = ui.getTools();
    if (instance instanceof DockableTool tool && !tools.contains(tool)) {
      tools.add(tool);
      LOGGER.debug("Add Tool [{}] for {}", tool, ui.clazz.getName());
    }
  }

  private static void registerToolExt(InsertableFactory factory, SeriesViewerUI ui) {
    List<InsertableFactory> tools = ui.getDynamicTools();
    if (!tools.contains(factory)) {
      tools.add(factory);
      LOGGER.debug("Add Tool Extension [{}] for {}", factory, ui.clazz.getName());
    }
  }

  private static void unregisterToolBar(
      InsertableFactory factory, final BundleContext context, SeriesViewerUI ui) {
    boolean updateGUI = false;
    List<Toolbar> toolBars = ui.getToolBars();
    synchronized (toolBars) {
      for (int i = toolBars.size() - 1; i >= 0; i--) {
        Insertable b = toolBars.get(i);
        if (factory.isComponentCreatedByThisFactory(b)) {
          Preferences prefs = BundlePreferences.getDefaultPreferences(context);
          if (prefs != null) {
            List<Insertable> list = Collections.singletonList(b);
            InsertableUtil.savePreferences(
                list, prefs.node(ui.clazz.getSimpleName().toLowerCase()), Type.TOOLBAR);
          }

          toolBars.remove(i);
          factory.dispose(b);
          updateGUI = true;
        }
      }
    }
    if (updateGUI) {
      notifyDicomModel(ObservableEvent.BasicAction.UPDATE_TOOLBARS, ui);
    }
  }

  private static void unregisterTool(
      InsertableFactory factory, final BundleContext context, SeriesViewerUI ui) {
    List<DockableTool> tools = ui.getTools();
    synchronized (tools) {
      for (int i = tools.size() - 1; i >= 0; i--) {
        DockableTool t = tools.get(i);
        if (factory.isComponentCreatedByThisFactory(t)) {
          Preferences prefs = BundlePreferences.getDefaultPreferences(context);
          if (prefs != null) {
            Preferences containerNode = prefs.node(ui.clazz.getSimpleName().toLowerCase());
            InsertableUtil.savePreferences(Collections.singletonList(t), containerNode, Type.TOOL);
          }

          tools.remove(i);
          factory.dispose(t);
          t.closeDockable();
        }
      }
    }
  }

  private static void unregisterDynamicTool(InsertableFactory factory, SeriesViewerUI ui) {
    List<InsertableFactory> tools = ui.getDynamicTools();
    synchronized (tools) {
      for (int i = tools.size() - 1; i >= 0; i--) {
        InsertableFactory t = tools.get(i);
        if (factory.equals(t)) {
          tools.remove(i);
        }
      }
    }
  }

  private static void notifyDicomModel(BasicAction action, SeriesViewerUI ui) {
    if (ui != null) {
      List<DataExplorerView> explorerPlugins = GuiUtils.getUICore().getExplorerPlugins();
      explorerPlugins.stream()
          .map(DataExplorerView::getDataExplorerModel)
          .filter(m -> "DICOM".equals(m.toString()))
          .findFirst()
          .ifPresent(model -> model.firePropertyChange(new ObservableEvent(action, ui, null, ui)));
    }
  }

  private static void notifyDefaultDataModel(BasicAction action, SeriesViewerUI ui) {
    ViewerPluginBuilder.DefaultDataModel.firePropertyChange(
        new ObservableEvent(action, ui, null, ui));
  }
}
