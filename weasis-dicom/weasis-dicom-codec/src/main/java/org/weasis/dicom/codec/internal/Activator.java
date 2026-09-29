/*
 * Copyright (c) 2025 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.internal;

import java.nio.file.Path;
import java.util.List;
import javax.imageio.spi.IIOServiceProvider;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.img.DicomImageReaderSpi;
import org.dcm4che3.img.lut.PresetWindowLevel;
import org.dcm4che3.util.UIDUtils;
import org.pf4j.PluginWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.media.data.PixelReviewAdvisor;
import org.weasis.core.api.service.WProperties;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElementFactory;
import org.weasis.dicom.codec.Redaction;
import org.weasis.dicom.codec.display.WindowPresetJson;
import org.weasis.dicom.codec.display.WindowPresetRegistry;
import org.weasis.imageio.codec.ImageioUtil;
import org.weasis.plugin.WeasisPlugin;

public class Activator extends WeasisPlugin {
  private static final Logger LOGGER = LoggerFactory.getLogger(Activator.class);

  private final IIOServiceProvider[] dcm4cheCodecs = {new DicomImageReaderSpi()};

  public Activator(org.weasis.plugin.PluginContext context) {
    super(context);
  }

  @Override
  public void start() {
    LOGGER.info("Initialize DicomCodec");
    /*
     * Set value for dicom root UID which should be registered at the
     * https://www.iana.org/assignments/enterprise-numbers <br>
     * Default value is 2.25, this enables users to generate OIDs without any registration procedure
     *
     * @see http://www.dclunie.com/medical-image-faq/html/part2.html#UUID <br>
     *     http://www.oid-info.com/get/2.25 <br>
     *     http://www.itu.int/ITU-T/asn1/uuid.html<br>
     *     http://healthcaresecprivacy.blogspot.ch/2011/02/creating-and-using-unique-id-uuid-oid.html
     */
    String weasisRootUID =
        GuiUtils.getUICore()
            .getSystemPreferences()
            .getProperty("weasis.dicom.root.uid", UIDUtils.getRoot());
    UIDUtils.setRoot(weasisRootUID);

    // Set the default encoding (must contain ASCII)
    SpecificCharacterSet.setDefaultCharacterSet("ISO_IR 192"); // NON-NLS

    for (IIOServiceProvider p : dcm4cheCodecs) {
      ImageioUtil.registerServiceProvider(p);
    }

    configureWindowPresets();
    // Only the codec can tell whether pixels may carry identity: the dialogs of the core ask it
    PixelReviewAdvisor.install(Redaction::requiresReview);

    // Register DicomSpecialElementFactory extensions
    List<DicomSpecialElementFactory> factories =
        GuiUtils.getUICore().getPluginManager().getExtensions(DicomSpecialElementFactory.class);
    addDicomSpecialElementFactory(factories);

    GuiUtils.getUICore()
        .getPluginManager()
        .addPluginStateListener(
            event -> {
              if (event.getPluginState() == org.pf4j.PluginState.STARTED) {
                onExtensionsAdded(event.getPlugin());
              } else if (event.getPluginState() == org.pf4j.PluginState.STOPPED) {
                onExtensionsRemoved(event.getPlugin());
              }
            });
  }

  /** Loads the site and user preset documents and supplies them to the renderer. */
  private static void configureWindowPresets() {
    WProperties preferences = GuiUtils.getUICore().getSystemPreferences();
    String prefDir = preferences.getProperty("weasis.pref.dir"); // NON-NLS
    WindowPresetRegistry registry = WindowPresetRegistry.getInstance();
    registry.configure(
        preferences.getProperty(WindowPresetRegistry.CONFIG_PROPERTY),
        StringUtil.hasText(prefDir)
            ? Path.of(prefDir).resolve(WindowPresetRegistry.USER_FILE)
            : null,
        path -> GuiUtils.getUICore().storeRemotePref(path, WindowPresetJson.CONTENT_TYPE));
    PresetWindowLevel.setModalityPresetProvider(registry);
  }

  @Override
  public void stop() {
    LOGGER.info("Deactivate DicomCodec");
    PresetWindowLevel.setModalityPresetProvider(null);
    for (IIOServiceProvider p : dcm4cheCodecs) {
      ImageioUtil.deregisterServiceProvider(p);
    }
  }

  private void onExtensionsAdded(PluginWrapper plugin) {
    List<DicomSpecialElementFactory> factories =
        plugin
            .getPluginManager()
            .getExtensions(DicomSpecialElementFactory.class, plugin.getPluginId());
    addDicomSpecialElementFactory(factories);
  }

  private void onExtensionsRemoved(PluginWrapper plugin) {
    List<DicomSpecialElementFactory> factories =
        plugin
            .getPluginManager()
            .getExtensions(DicomSpecialElementFactory.class, plugin.getPluginId());
    removeDicomSpecialElementFactory(factories);
  }

  private void addDicomSpecialElementFactory(List<DicomSpecialElementFactory> factories) {
    for (DicomSpecialElementFactory factory : factories) {
      String name = factory.getClass().getName();
      for (String modality : factory.getModalities()) {
        DicomSpecialElementFactory prev = DicomMediaIO.DCM_ELEMENT_FACTORIES.put(modality, factory);
        if (prev != null) {
          LOGGER.warn("{} factory has been replaced by {}", prev.getClass().getName(), name);
        }
        LOGGER.info("Register DicomSpecialElementFactory: {} => {}", modality, name);
      }
    }
  }

  private void removeDicomSpecialElementFactory(List<DicomSpecialElementFactory> factories) {
    for (DicomSpecialElementFactory factory : factories) {
      String name = factory.getClass().getName();
      for (String modality : factory.getModalities()) {
        DicomSpecialElementFactory f = DicomMediaIO.DCM_ELEMENT_FACTORIES.get(modality);
        if (factory.equals(f)) {
          DicomMediaIO.DCM_ELEMENT_FACTORIES.remove(modality);
          LOGGER.info("Unregister DicomSpecialElementFactory: {} => {}", modality, name);
        } else {
          LOGGER.warn(
              "{}: Unregistering {} has no effect, {} is registered instead",
              modality,
              name,
              f.getClass().getName());
        }
      }
    }
  }
}
