/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.internal;

import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.felix.service.command.CommandProcessor;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.InsertableFactory;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.viewer2d.EventManager;
import org.weasis.dicom.viewer2d.View2dContainer;
import org.weasis.dicom.viewer2d.mpr.MprContainer;

@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}") // NON-NLS
public class Activator implements BundleActivator, ServiceListener {

  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  @Override
  public void start(final BundleContext bundleContext) {

    Dictionary<String, Object> dict = new Hashtable<>();
    dict.put(CommandProcessor.COMMAND_SCOPE, "dcmview2d"); // NON-NLS
    dict.put(CommandProcessor.COMMAND_FUNCTION, EventManager.functions.toArray(new String[0]));
    bundleContext.registerService(EventManager.class.getName(), EventManager.getInstance(), dict);

    BundleTools.registerExistingComponents(bundleContext, View2dContainer.UI);

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
    // Save preferences
    ImageViewerPlugin<DicomImageElement> container =
        EventManager.getInstance().getSelectedView2dContainer();
    if (container instanceof MprContainer) {
      // Remove crosshair tool
      container.setSelected(false);
    }
    EventManager.getInstance().savePreferences(bundleContext);

    GuiUtils.getUICore().closeSeriesViewerType(MprContainer.class);
    GuiUtils.getUICore().closeSeriesViewerType(View2dContainer.class);
  }

  @Override
  public synchronized void serviceChanged(final ServiceEvent event) {
    // Tools and Toolbars (with non-immediate instance) must be instantiated in the EDT
    GuiExecutor.execute(() -> BundleTools.dataExplorerChanged(event, View2dContainer.UI));
  }
}
