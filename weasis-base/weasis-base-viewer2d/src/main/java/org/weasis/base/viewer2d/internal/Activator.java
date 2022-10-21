/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.base.viewer2d.internal;

import java.util.Collections;
import java.util.List;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.ImportToolBar;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.ObservableEvent.BasicAction;
import org.weasis.core.api.gui.Insertable;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.InsertableUtil;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.ui.docking.DockableTool;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.util.Toolbar;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator, ServiceListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  @Override
  public void start(final BundleContext bundleContext) {
    registerExistingComponents(bundleContext);

    // Instantiate UI components in EDT
    GuiExecutor.instance()
        .execute(() -> UIManager.EXPLORER_PLUGIN_TOOLBARS.add(new ImportToolBar(3)));

    // Add listener for getting new service events
    try {

      bundleContext.addServiceListener(
          Activator.this,
          String.format("(%s=%s)", Constants.OBJECTCLASS, InsertableFactory.class.getName()));
    } catch (InvalidSyntaxException e) {
      LOGGER.error("Add service listener", e);
    }
  }

  @Override
  public void stop(BundleContext bundleContext) {
    // Save preferences only if EventManager has been initialized
    if (EventManager.hasBeenInitialized()) {
      EventManager.getInstance().savePreferences(bundleContext);
    }
    UIManager.EXPLORER_PLUGIN_TOOLBARS.removeIf(ImportToolBar.class::isInstance);
    UIManager.closeSeriesViewerType(View2dContainer.class);
  }

  @Override
  public synchronized void serviceChanged(final ServiceEvent event) {
    // Tools and Toolbars (with non-immediate instance) must be instantiated in the EDT
    GuiExecutor.instance().execute(() -> dataExplorerChanged(event));
  }

  private void dataExplorerChanged(final ServiceEvent event) {

    final ServiceReference<?> mref = event.getServiceReference();
    // The View2dContainer name should be referenced as a property in the provided service
    if (Boolean.parseBoolean((String) mref.getProperty(View2dContainer.class.getName()))) {
      final BundleContext context =
          FrameworkUtil.getBundle(Activator.this.getClass()).getBundleContext();
      Object service = context.getService(mref);
      if (service instanceof InsertableFactory factory) {
        if (event.getType() == ServiceEvent.REGISTERED) {
          registerComponent(factory);
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
          if (Type.TOOLBAR.equals(factory.getType())) {
            unregisterToolBar(factory, context);
          } else if (Type.TOOL.equals(factory.getType())) {
            unregisterTool(factory, context);
          }
        }
      }
    }
  }

  private static void registerExistingComponents(BundleContext bundleContext) {
    try {
      for (ServiceReference<InsertableFactory> serviceReference :
          bundleContext.getServiceReferences(InsertableFactory.class, null)) {
        // The View2dContainer name should be referenced as a property in the provided service
        if (Boolean.parseBoolean(
            (String) serviceReference.getProperty(View2dContainer.class.getName()))) {
          // Instantiate UI components in EDT
          GuiExecutor.instance()
              .execute(() -> registerComponent(bundleContext.getService(serviceReference)));
        }
      }
    } catch (InvalidSyntaxException e1) {
      LOGGER.error("Register tool and toolbar", e1);
    }
  }

  private static void registerComponent(final InsertableFactory factory) {
    if (factory == null) {
      return;
    }

    if (Type.TOOLBAR.equals(factory.getType())) {
      registerToolBar(factory.createInstance(null));
    } else if (Type.TOOL.equals(factory.getType())) {
      registerTool(factory.createInstance(null));
    }
  }

  private static void registerToolBar(Insertable instance) {
    if (instance instanceof Toolbar bar && !View2dContainer.TOOLBARS.contains(instance)) {
      View2dContainer.TOOLBARS.add(bar);
      updateViewerUI(ObservableEvent.BasicAction.UPDATE_TOOLBARS);
      LOGGER.debug("Add Toolbar [{}] for {}", bar, View2dContainer.class.getName());
    }
  }

  private static void registerTool(Insertable instance) {
    if (instance instanceof DockableTool tool && !View2dContainer.TOOLS.contains(instance)) {
      View2dContainer.TOOLS.add(tool);
      ImageViewerPlugin<ImageElement> view =
          EventManager.getInstance().getSelectedView2dContainer();
      if (view instanceof View2dContainer) {
        tool.showDockable();
      }
      LOGGER.debug("Add Tool [{}] for {}", tool, View2dContainer.class.getName());
    }
  }

  private static void unregisterToolBar(InsertableFactory factory, final BundleContext context) {
    boolean updateGUI = false;
    synchronized (View2dContainer.TOOLBARS) {
      for (int i = View2dContainer.TOOLBARS.size() - 1; i >= 0; i--) {
        Insertable b = View2dContainer.TOOLBARS.get(i);
        if (factory.isComponentCreatedByThisFactory(b)) {
          Preferences prefs = BundlePreferences.getDefaultPreferences(context);
          if (prefs != null) {
            List<Insertable> list = Collections.singletonList(b);
            InsertableUtil.savePreferences(
                list,
                prefs.node(View2dContainer.class.getSimpleName().toLowerCase()),
                Type.TOOLBAR);
          }

          View2dContainer.TOOLBARS.remove(i);
          factory.dispose(b);
          updateGUI = true;
        }
      }
    }
    if (updateGUI) {
      updateViewerUI(ObservableEvent.BasicAction.UPDATE_TOOLBARS);
    }
  }

  private static void unregisterTool(InsertableFactory factory, final BundleContext context) {
    synchronized (View2dContainer.TOOLS) {
      for (int i = View2dContainer.TOOLS.size() - 1; i >= 0; i--) {
        DockableTool t = View2dContainer.TOOLS.get(i);
        if (factory.isComponentCreatedByThisFactory(t)) {
          Preferences prefs = BundlePreferences.getDefaultPreferences(context);
          if (prefs != null) {
            Preferences containerNode =
                prefs.node(View2dContainer.class.getSimpleName().toLowerCase());
            InsertableUtil.savePreferences(Collections.singletonList(t), containerNode, Type.TOOL);
          }

          View2dContainer.TOOLS.remove(i);
          factory.dispose(t);
          t.closeDockable();
        }
      }
    }
  }

  private static void updateViewerUI(BasicAction action) {
    ImageViewerPlugin<ImageElement> view = EventManager.getInstance().getSelectedView2dContainer();
    if (view instanceof View2dContainer) {
      ViewerPluginBuilder.DefaultDataModel.firePropertyChange(
          new ObservableEvent(action, view, null, view));
    }
  }
}
