/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.dicom.viewer2d.sr;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerFactory;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.macro.ImageSOPInstanceReference;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.explorer.MimeSystemAppFactory;

public class SRView extends JScrollPane implements SeriesViewerListener {

    private final JTextPane htmlPanel = new JTextPane();
    private final Map<String, SRImageReference> map = new HashMap<String, SRImageReference>();
    private Series<?> series;

    public SRView() {
        this(null);
    }

    public SRView(Series<?> series) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        htmlPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        htmlPanel.setContentType("text/html"); //$NON-NLS-1$
        htmlPanel.setEditable(false);
        htmlPanel.addHyperlinkListener(new HyperlinkListener() {
            @Override
            public void hyperlinkUpdate(HyperlinkEvent e) {
                JTextPane pane = (JTextPane) e.getSource();
                if (e.getEventType() == HyperlinkEvent.EventType.ENTERED) {
                    pane.setToolTipText(e.getDescription());
                } else if (e.getEventType() == HyperlinkEvent.EventType.EXITED) {
                    pane.setToolTipText(null);
                } else if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                    String desc = e.getDescription();
                    URL url = e.getURL();
                    if (url == null && desc != null && desc.startsWith("#")) {
                        htmlPanel.scrollToReference(desc.substring(1));
                    } else {
                        openRelatedSeries(e.getURL().getHost());
                    }
                }
            }
        });
        StyleSheet ss = ((HTMLEditorKit) htmlPanel.getEditorKit()).getStyleSheet();
        ss.addRule("body {font-family:sans-serif;font-size:12pt;color:#" //$NON-NLS-1$
            + Integer.toHexString((htmlPanel.getForeground().getRGB() & 0xffffff) | 0x1000000).substring(1)
            + ";margin-right:0;margin-left:0;font-weight:normal;}"); //$NON-NLS-1$
        setPreferredSize(new Dimension(1024, 1024));
        setSeries(series);
    }

    public JTextPane getHtmlPanel() {
        return htmlPanel;
    }

    public synchronized Series<?> getSeries() {
        return series;
    }

    public synchronized void setSeries(Series<?> series) {
        this.series = series;
        if (series != null) {
            List<MediaElement<?>> specialElements =
                (List<MediaElement<?>>) series.getTagValue(TagW.DicomSpecialElementList);
            if (specialElements != null && specialElements.size() > 0) {
                // Should have only one object by series (if more, they are split in several sub-series in dicomModel)
                displayLimitedDicomInfo((DicomSpecialElement) specialElements.get(0));
            }
        }
    }

    public void dispose() {

    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.LAYOUT.equals(type) && event.getSeries() instanceof Series) {
            setSeries((Series<?>) event.getSeries());
        }
    }

    private void displayLimitedDicomInfo(DicomSpecialElement media) {

        StringBuffer html = new StringBuffer();
        if (media != null) {
            SRReader reader = new SRReader(series, media);
            map.clear();
            reader.readDocumentGeneralModule(html, map);
        }
        htmlPanel.setText(html.toString());
        this.setViewportView(htmlPanel);
    }

    private void openRelatedSeries(String reference) {
        SRImageReference imgRef = map.get(reference);
        if (imgRef != null) {
            ImageSOPInstanceReference ref = imgRef.getImageSOPInstanceReference();
            if (ref != null) {
                DataExplorerView dicomView = org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);
                DicomModel model = null;
                if (dicomView != null) {
                    model = (DicomModel) dicomView.getDataExplorerModel();
                }
                if (model != null) {
                    MediaSeriesGroup study = model.getParent(series, DicomModel.study);
                    MediaSeriesGroup patient = model.getParent(series, DicomModel.patient);
                    Series<?> s = findSOPInstanceReference(model, patient, study, ref.getReferencedSOPInstanceUID());
                    if (s != null) {
                        SeriesViewerFactory plugin = UIManager.getViewerFactory(s.getMimeType());
                        if (plugin != null && !(plugin instanceof MimeSystemAppFactory)) {
                            Map<String, Object> props = Collections.synchronizedMap(new HashMap<String, Object>());
                            props.put(ViewerPluginBuilder.KOS, buildKO(ref));
                            props.put(ViewerPluginBuilder.PR, buildKO(ref));
                            ArrayList<MediaSeries<? extends MediaElement<?>>> list =
                                new ArrayList<MediaSeries<? extends MediaElement<?>>>(1);
                            list.add(s);
                            ViewerPluginBuilder builder =
                                new ViewerPluginBuilder(plugin, list, dicomView.getDataExplorerModel(), props);
                            ViewerPluginBuilder.openSequenceInPlugin(builder);

                        }
                    } else {
                        // TODO try to download through WADO
                        JOptionPane.showMessageDialog(this, "Cannot find the image!", "Open Image",
                            JOptionPane.WARNING_MESSAGE);
                    }
                }
            }
        }
    }

    private Series<?> findSOPInstanceReference(DicomModel model, MediaSeriesGroup patient, MediaSeriesGroup study,
        String sopUID) {
        if (model != null && patient != null && sopUID != null) {
            Series<?> series = null;
            if (study != null) {
                series = findSOPInstanceReference(model, study, sopUID);
                if (series != null) {
                    return series;
                }
            }

            if (series == null) {
                Collection<MediaSeriesGroup> studyList = model.getChildren(patient);
                synchronized (model) {
                    for (Iterator<MediaSeriesGroup> it = studyList.iterator(); it.hasNext();) {
                        MediaSeriesGroup st = it.next();
                        if (st != study) {
                            series = findSOPInstanceReference(model, st, sopUID);
                        }
                        if (series != null) {
                            return series;
                        }
                    }
                }
            }
        }
        return null;
    }

    private DicomSpecialElement buildKO(final ImageSOPInstanceReference ref) {
        if (ref == null) {
            return null;
        }
        int[] frames = ref.getReferencedFrameNumber();
        // TODO build KO
        return null;
    }

    private DicomSpecialElement buildPR(final SRImageReference ref) {
        if (ref != null) {
            // TODO build PR
            return null;
        }
        return null;
    }

    private Series<?> findSOPInstanceReference(DicomModel model, MediaSeriesGroup study, String sopUID) {
        if (model != null && study != null) {
            Collection<MediaSeriesGroup> seriesList = model.getChildren(study);
            synchronized (model) {
                for (Iterator<MediaSeriesGroup> it = seriesList.iterator(); it.hasNext();) {
                    MediaSeriesGroup seq = it.next();
                    if (seq instanceof Series) {
                        Series<?> s = (Series<?>) seq;
                        if (s.hasMediaContains(TagW.SOPInstanceUID, sopUID)) {
                            return s;
                        }
                    }
                }
            }
        }
        return null;
    }

}
