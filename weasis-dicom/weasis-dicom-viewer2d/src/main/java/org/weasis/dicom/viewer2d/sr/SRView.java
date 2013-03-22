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

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.border.EmptyBorder;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.dicom.codec.DicomSpecialElement;

public class SRView extends JScrollPane implements SeriesViewerListener {

    private final JTextPane jTextPane1 = new JTextPane();
    private Series series;

    public SRView() {
        this(null);
    }

    public SRView(Series series) {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        jTextPane1.setBorder(new EmptyBorder(5, 5, 5, 5));
        jTextPane1.setContentType("text/html"); //$NON-NLS-1$
        jTextPane1.setEditable(false);
        StyleSheet ss = ((HTMLEditorKit) jTextPane1.getEditorKit()).getStyleSheet();
        ss.addRule("body {font-family:sans-serif;font-size:12pt;color:#" //$NON-NLS-1$
            + Integer.toHexString((jTextPane1.getForeground().getRGB() & 0xffffff) | 0x1000000).substring(1)
            + ";margin-right:0;margin-left:0;font-weight:normal;}"); //$NON-NLS-1$
        setPreferredSize(new Dimension(1024, 1024));
        setSeries(series);
    }

    public Series getSeries() {
        return series;
    }

    public void setSeries(Series series) {
        this.series = series;
        if (series != null) {
            Object dicomObject = series.getTagValue(TagW.DicomSpecialElement);
            if (dicomObject instanceof DicomSpecialElement) {
                displayLimitedDicomInfo((DicomSpecialElement) dicomObject);
            }
        }
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.LAYOUT.equals(type)) {
            setSeries(event.getSeries());
        }
    }

    private void displayLimitedDicomInfo(DicomSpecialElement media) {

        StringBuffer html = new StringBuffer();
        if (media != null) {
            SRReader reader = new SRReader(media);
            reader.readDocumentGeneralModule(series, html);
        }
        jTextPane1.setText(html.toString());
        this.setViewportView(jTextPane1);
    }

    public void dispose() {

    }
}
