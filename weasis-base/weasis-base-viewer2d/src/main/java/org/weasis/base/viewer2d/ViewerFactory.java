/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.base.viewer2d;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.util.DefaultAction;

@org.osgi.service.component.annotations.Component(service = SeriesViewerFactory.class, immediate = false)
public class ViewerFactory implements SeriesViewerFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewerFactory.class);

    public static final String NAME = Messages.getString("ViewerFactory.img_viewer"); //$NON-NLS-1$

    private static final DefaultAction preferencesAction = new DefaultAction(Messages.getString("OpenImageAction.img"), //$NON-NLS-1$
        new ImageIcon(SeriesViewerFactory.class.getResource("/icon/16x16/img-import.png")), //$NON-NLS-1$
        ViewerFactory::getOpenImageAction);

    public ViewerFactory() {
        super();
    }

    @Override
    public Icon getIcon() {
        return MimeInspector.imageIcon;
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
            if (obj instanceof GridBagLayoutModel) {
                model = (GridBagLayoutModel) obj;
            } else {
                obj = properties.get(ViewCanvas.class.getName());
                if (obj instanceof Integer) {
                    ActionState layout = EventManager.getInstance().getAction(ActionW.LAYOUT);
                    if (layout instanceof ComboItemListener) {
                        Object[] list = ((ComboItemListener) layout).getAllItem();
                        for (Object m : list) {
                            if (m instanceof GridBagLayoutModel) {
                                if (getViewTypeNumber((GridBagLayoutModel) m, ViewCanvas.class) >= (Integer) obj) {
                                    model = (GridBagLayoutModel) m;
                                    break;
                                }
                            }
                        }
                    }
                }
            }
            // Set UID
            Object val = properties.get(ViewerPluginBuilder.UID);
            if (val instanceof String) {
                uid = (String) val;
            }
        }
        View2dContainer instance = new View2dContainer(model, uid);
        if (properties != null) {
            Object obj = properties.get(DataExplorerModel.class.getName());
            if (obj instanceof DataExplorerModel) {
                // Register the PropertyChangeListener
                DataExplorerModel m = (DataExplorerModel) obj;
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
                    LOGGER.error("Checking view type", e); //$NON-NLS-1$
                }
            }
        }
        return val;
    }

    public static void closeSeriesViewer(View2dContainer view2dContainer) {
        // Unregister the PropertyChangeListener
        ViewerPluginBuilder.DefaultDataModel.removePropertyChangeListener(view2dContainer);
    }

    @Override
    public boolean canReadMimeType(String mimeType) {
        if (mimeType != null && mimeType.startsWith("image/")) { //$NON-NLS-1$
            return true;
        }
        return false;
    }

    @Override
    public boolean isViewerCreatedByThisFactory(SeriesViewer<? extends MediaElement> viewer) {
        if (viewer instanceof View2dContainer) {
            return true;
        }
        return false;
    }

    @Override
    public int getLevel() {
        return 5;
    }

    @Override
    public List<Action> getOpenActions() {
        if (!BundleTools.SYSTEM_PREFERENCES.getBooleanProperty("weasis.import.images", true)) { //$NON-NLS-1$
            return Collections.emptyList();
        }
        return Arrays.asList(preferencesAction);
    }

    @Override
    public boolean canAddSeries() {
        return true;
    }

    @Override
    public boolean canExternalizeSeries() {
        return true;
    }

    static void getOpenImageAction(ActionEvent e) {
        String directory = BundleTools.LOCAL_PERSISTENCE.getProperty("last.open.image.dir", "");//$NON-NLS-1$ //$NON-NLS-2$
        JFileChooser fileChooser = new JFileChooser(directory);

        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);

        FileFormatFilter.setImageDecodeFilters(fileChooser);
        File[] selectedFiles;
        if (fileChooser.showOpenDialog(UIManager.getApplicationWindow()) != JFileChooser.APPROVE_OPTION
            || (selectedFiles = fileChooser.getSelectedFiles()) == null) {
            return;
        } else {
            MediaSeries<MediaElement> series = null;
            for (File file : selectedFiles) {
                String mimeType = MimeInspector.getMimeType(file);
                if (mimeType != null && mimeType.startsWith("image")) { //$NON-NLS-1$
                    Codec codec = BundleTools.getCodec(mimeType, null);
                    if (codec != null) {
                        MediaReader reader = codec.getMediaIO(file.toURI(), mimeType, null);
                        if (reader != null) {
                            if (series == null) {
                                // TODO improve group model for image, uid for group ?
                                series = reader.getMediaSeries();
                            } else {
                                MediaElement[] elements = reader.getMediaElement();
                                if (elements != null) {
                                    for (MediaElement media : elements) {
                                        series.addMedia(media);
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (series != null && series.size(null) > 0) {
                ViewerPluginBuilder.openSequenceInDefaultPlugin(series, ViewerPluginBuilder.DefaultDataModel, true,
                    false);
            } else {
                Component c = e.getSource() instanceof Component ? (Component) e.getSource() : null;
                JOptionPane.showMessageDialog(c, Messages.getString("OpenImageAction.error_open_msg"), //$NON-NLS-1$
                    Messages.getString("OpenImageAction.open_img"), JOptionPane.WARNING_MESSAGE); //$NON-NLS-1$
            }
            BundleTools.LOCAL_PERSISTENCE.setProperty("last.open.image.dir", selectedFiles[0].getParent()); //$NON-NLS-1$
        }
    }
}
