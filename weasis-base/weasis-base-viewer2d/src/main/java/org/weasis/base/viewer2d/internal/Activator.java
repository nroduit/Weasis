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

import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.base.viewer2d.EventManager;
import org.weasis.base.viewer2d.ImportToolBar;
import org.weasis.base.viewer2d.View2dContainer;
import org.weasis.core.api.gui.Insertable.Type;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator, ServiceListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  @Override
  public void start(final BundleContext bundleContext) {
    registerExistingComponents(bundleContext);

    // Instantiate UI components in EDT
    GuiExecutor.instance()
        .execute(() -> GuiUtils.getUICore().getExplorerPluginToolbars().add(new ImportToolBar(3)));

    // Add listener for getting new service events
    try {

      bundleContext.addServiceListener(
          Activator.this,
          String.format(
              "(%s=%s)", Constants.OBJECTCLASS, InsertableFactory.class.getName())); // NON-NLS
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
    GuiUtils.getUICore().getExplorerPluginToolbars().removeIf(ImportToolBar.class::isInstance);
    GuiUtils.getUICore().closeSeriesViewerType(View2dContainer.class);
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
            View2dContainer.unregisterToolBar(factory, context);
          } else if (Type.TOOL.equals(factory.getType())) {
            View2dContainer.unregisterTool(factory, context);
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
      View2dContainer.registerToolBar(factory.createInstance(null));
    } else if (Type.TOOL.equals(factory.getType())) {
      View2dContainer.registerTool(factory.createInstance(null));
    }
  }
}
