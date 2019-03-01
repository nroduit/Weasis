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
package org.weasis.acquire.explorer.gui.central.tumbnail;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.event.ListSelectionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.AcquireTabPanel;
import org.weasis.acquire.explorer.gui.central.SerieButton;
import org.weasis.base.explorer.JIThumbnailCache;
import org.weasis.base.explorer.list.AThumbnailListPane;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.UriListFlavor;

public class AcquireCentralTumbnailPane<E extends MediaElement> extends AThumbnailListPane<E> {
    private static final long serialVersionUID = 5728507793866004078L;

    private static final Logger LOGGER = LoggerFactory.getLogger(AcquireCentralTumbnailPane.class);

    public AcquireCentralTumbnailPane(List<E> list, JIThumbnailCache thumbCache) {
        super(new AcquireCentralThumnailList<E>(thumbCache));
        setList(list);
        setTransferHandler(new SequenceHandler());
    }

    public void setAcquireTabPanel(AcquireTabPanel acquireTabPanel) {
        ((AcquireCentralThumnailList) this.thumbnailList).setAcquireTabPanel(acquireTabPanel);
    }

    public void addListSelectionListener(ListSelectionListener listener) {
        this.thumbnailList.addListSelectionListener(listener);
    }

    public void addElements(List<E> elements) {
        if (elements != null) {
            IThumbnailModel<E> model = this.thumbnailList.getThumbnailListModel();
            elements.forEach(model::addElement);
            repaintList();
        }
    }

    public void setList(List<E> elements) {
        IThumbnailModel<E> model = this.thumbnailList.getThumbnailListModel();
        model.clear();
        if (elements != null) {
            elements.forEach(model::addElement);
            repaintList();
        }
    }

    public void repaintList() {
        // Require to repaint the scroll pane correctly (otherwise not all the elements of JList are repainted)
        if (thumbnailList.asComponent() instanceof JComponent) {
            ((JComponent) thumbnailList.asComponent()).updateUI();
        }
    }

    private class SequenceHandler extends TransferHandler {
        private static final long serialVersionUID = -9199885304640621515L;

        public SequenceHandler() {
            super("series"); //$NON-NLS-1$
        }

        @Override
        public Transferable createTransferable(JComponent comp) {
            return null;
        }

        @Override
        public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) {
                return false;
            }
            if (AcquireManager.getInstance().getAcquireExplorer().getImportPanel().isLoading()) {
                return false;
            }
            if (support.isDataFlavorSupported(Series.sequenceDataFlavor)
                || support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                || support.isDataFlavorSupported(UriListFlavor.flavor)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean importData(TransferSupport support) {
            if (!canImport(support)) {
                return false;
            }

            Transferable transferable = support.getTransferable();

            List<File> files = null;
            // Not supported on Linux
            if (support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                try {
                    files = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                } catch (Exception e) {
                    LOGGER.error("Drop image file", e); //$NON-NLS-1$
                }
                return dropDFiles(files);
            }
            // When dragging a file or group of files from a Gnome or Kde environment
            // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4899516
            else if (support.isDataFlavorSupported(UriListFlavor.flavor)) {
                try {
                    // Files with spaces in the filename trigger an error
                    // http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6936006
                    String val = (String) transferable.getTransferData(UriListFlavor.flavor);
                    files = UriListFlavor.textURIListToFileList(val);
                } catch (Exception e) {
                    LOGGER.error("Drop image URI", e); //$NON-NLS-1$
                }
                return dropDFiles(files);
            }

            try {
                Object object = transferable.getTransferData(Series.sequenceDataFlavor);
                if (object instanceof Series) {

                    MediaElement media = ((Series) object).getMedia(0, null, null);
                    addToSerie(media);
                }
            } catch (UnsupportedFlavorException | IOException e) {
                LOGGER.error("Drop thumnail", e); //$NON-NLS-1$
            }

            return true;
        }

        private void addToSerie(MediaElement media) {
            if (media instanceof ImageElement) {
                AcquireCentralThumnailList tumbList =
                    (AcquireCentralThumnailList) AcquireCentralTumbnailPane.this.thumbnailList;
                AcquireImageInfo info = AcquireManager.findByImage((ImageElement) media);
                if (info != null) {
                    SeriesGroup seriesGroup =
                        Optional.ofNullable(tumbList.getSelectedSerie()).map(SerieButton::getSerie).orElse(null);
                    AcquireManager.importImage(info, seriesGroup, 0);
                }
            }
        }

        private boolean dropDFiles(List<File> files) {
            if (files != null) {
                for (File file : files) {
                    MediaReader reader = ViewerPluginBuilder.getMedia(file, false);
                    if (reader != null && !reader.getMediaFragmentMimeType().contains("dicom")) { //$NON-NLS-1$
                        MediaElement[] medias = reader.getMediaElement();
                        if (medias != null) {
                            for (MediaElement mediaElement : medias) {
                                addToSerie(mediaElement);
                            }
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }
}
