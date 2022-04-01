/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import org.dcm4che3.data.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.gui.util.ActionState;
import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.gui.util.ComboItemListener;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.image.GridBagLayoutModel;
import org.weasis.core.api.image.LayoutConstraints;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.OtherIcon;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.DefaultAction;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class)
public class View2dFactory implements SeriesViewerFactory {
  private static final Logger LOGGER = LoggerFactory.getLogger(View2dFactory.class);

  public static final String NAME = Messages.getString("View2dFactory.title");

  private static final DefaultAction preferencesAction =
      new DefaultAction(
          Messages.getString("OpenDicomAction.title"), View2dFactory::getOpenImageAction);

  public View2dFactory() {
    super();
  }

  @Override
  public Icon getIcon() {
    return ResourceUtil.getIcon(OtherIcon.XRAY);
  }

  @Override
  public String getUIName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return NAME;
  }

  @Override
  public SeriesViewer<?> createSeriesViewer(Map<String, Object> properties) {
    GridBagLayoutModel model = ImageViewerPlugin.VIEWS_1x1;
    String uid = null;
    if (properties != null) {
      Object obj = properties.get(org.weasis.core.api.image.GridBagLayoutModel.class.getName());
      if (obj instanceof GridBagLayoutModel gridBagLayoutModel) {
        model = gridBagLayoutModel;
      } else {
        obj = properties.get(ViewCanvas.class.getName());
        if (obj instanceof Integer intVal) {
          ActionState layout = EventManager.getInstance().getAction(ActionW.LAYOUT);
          if (layout instanceof ComboItemListener) {
            model = ImageViewerPlugin.getBestDefaultViewLayout(layout, intVal);
          }
        }
      }

      // Set UID
      Object val = properties.get(ViewerPluginBuilder.UID);
      if (val instanceof String s) {
        uid = s;
      }
    }
    View2dContainer instance = new View2dContainer(model, uid, getUIName(), getIcon(), null);
    if (properties != null) {
      Object obj = properties.get(DataExplorerModel.class.getName());
      if (obj instanceof DicomModel m) {
        // Register the PropertyChangeListener
        m.addPropertyChangeListener(instance);
      }
    }

    return instance;
  }

  public static int getViewTypeNumber(GridBagLayoutModel layout, Class<?> defaultClass) {
    int val = 0;
    if (layout != null && defaultClass != null) {
      Iterator<LayoutConstraints> enumVal = layout.getConstraints().keySet().iterator();
      while (enumVal.hasNext()) {
        try {
          Class<?> clazz = Class.forName(enumVal.next().getType());
          if (defaultClass.isAssignableFrom(clazz)) {
            val++;
          }
        } catch (Exception e) {
          LOGGER.error("Checking view", e);
        }
      }
    }
    return val;
  }

  public static void closeSeriesViewer(View2dContainer view2dContainer) {
    // Unregister the PropertyChangeListener
    DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
    if (dicomView != null) {
      dicomView.getDataExplorerModel().removePropertyChangeListener(view2dContainer);
    }
  }

  @Override
  public boolean canReadMimeType(String mimeType) {
    return DicomMediaIO.SERIES_MIMETYPE.equals(mimeType);
  }

  @Override
  public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
    return viewer instanceof View2dContainer;
  }

  @Override
  public int getLevel() {
    return 5;
  }

  @Override
  public List<Action> getOpenActions() {
    DataExplorerView dicomView = UIManager.getExplorerplugin(DicomExplorer.NAME);
    if (dicomView == null) {
      return Collections.singletonList(preferencesAction);
    }
    // In case DICOM explorer has been loaded get the first import action
    return dicomView.getOpenImportDialogAction().subList(0, 1);
  }

  @Override
  public boolean canAddSeries() {
    return true;
  }

  @Override
  public boolean canExternalizeSeries() {
    return true;
  }

  private static void getOpenImageAction(ActionEvent e) {
    String directory =
        BundleTools.LOCAL_UI_PERSISTENCE.getProperty("last.open.dicom.dir", ""); // NON-NLS
    JFileChooser fileChooser = new JFileChooser(directory);

    fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fileChooser.setMultiSelectionEnabled(true);
    FileFormatFilter filter =
        new FileFormatFilter(new String[] {"dcm", "dicm"}, "DICOM"); // NON-NLS
    fileChooser.addChoosableFileFilter(filter);
    fileChooser.setAcceptAllFileFilterUsed(true);
    fileChooser.setFileFilter(filter);

    File[] selectedFiles;
    if (fileChooser.showOpenDialog(UIManager.getApplicationWindow()) != JFileChooser.APPROVE_OPTION
        || (selectedFiles = fileChooser.getSelectedFiles()) == null) {
      return;
    } else {
      Codec codec = BundleTools.getCodec(DicomMediaIO.DICOM_MIMETYPE, DicomCodec.NAME);
      if (codec != null) {
        ArrayList<MediaSeries<? extends MediaElement>> list = new ArrayList<>();
        for (File file : selectedFiles) {
          if (MimeInspector.isMatchingMimeTypeFromMagicNumber(file, DicomMediaIO.DICOM_MIMETYPE)) {
            MediaReader reader = codec.getMediaIO(file.toURI(), DicomMediaIO.DICOM_MIMETYPE, null);
            if (reader != null) {
              if (reader.getMediaElement() == null) {
                // DICOM is not readable
                continue;
              }
              String sUID = TagD.getTagValue(reader, Tag.SeriesInstanceUID, String.class);
              String gUID = TagD.getTagValue(reader, Tag.PatientID, String.class);
              TagW tname = TagD.get(Tag.PatientName);
              String tvalue = (String) reader.getTagValue(tname);

              MediaSeries<MediaElement> s =
                  ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(
                      reader, gUID, tname, tvalue, sUID);

              if (s != null && !list.contains(s)) {
                list.add(s);
              }
            }
          }
        }
        if (!list.isEmpty()) {
          ViewerPluginBuilder.openSequenceInDefaultPlugin(
              list, ViewerPluginBuilder.DefaultDataModel, true, true);
        } else {
          JOptionPane.showMessageDialog(
              e.getSource() instanceof Component c ? c : null,
              Messages.getString("OpenDicomAction.open_err_msg"),
              Messages.getString("OpenDicomAction.desc"),
              JOptionPane.WARNING_MESSAGE);
        }
      }
      BundleTools.LOCAL_UI_PERSISTENCE.setProperty(
          "last.open.dicom.dir", selectedFiles[0].getParent());
    }
  }
}
