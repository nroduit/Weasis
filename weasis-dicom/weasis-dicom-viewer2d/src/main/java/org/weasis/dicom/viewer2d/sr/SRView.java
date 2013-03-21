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
import java.awt.Color;
import java.awt.Dimension;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.explorer.DicomExplorer;
import org.weasis.dicom.explorer.DicomModel;

public class SRView extends JScrollPane implements SeriesViewerListener {

    private final JTextPane jTextPane1 = new JTextPane();
    private MediaElement currentMedia;
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
        StyledDocument doc = jTextPane1.getStyledDocument();
        addStylesToDocument(doc, UIManager.getColor("TextPane.foreground")); //$NON-NLS-1$
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
            currentMedia = event.getMediaElement();
            setSeries(event.getSeries());
        }
    }

    public static void addStylesToDocument(StyledDocument doc, Color textColor) {
        // Initialize some styles.
        final MutableAttributeSet def = new SimpleAttributeSet();
        Style style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style regular = doc.addStyle("regular", style); //$NON-NLS-1$
        StyleConstants.setFontFamily(def, "SansSerif"); //$NON-NLS-1$
        StyleConstants.setFontSize(def, 12);
        if (textColor == null) {
            textColor = UIManager.getColor("text"); //$NON-NLS-1$
        }
        StyleConstants.setForeground(def, textColor);
        Style s = doc.addStyle("title", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 16);
        StyleConstants.setBold(s, true);
        s = doc.addStyle("bold", regular); //$NON-NLS-1$
        StyleConstants.setBold(s, true);
        StyleConstants.setFontSize(s, 12);
        s = doc.addStyle("small", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 10);
        s = doc.addStyle("large", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 14);
        s = doc.addStyle("italic", regular); //$NON-NLS-1$
        StyleConstants.setFontSize(s, 12);
        StyleConstants.setItalic(s, true);
    }

    private void displayLimitedDicomInfo(DicomSpecialElement media) {
        StyledDocument doc = jTextPane1.getStyledDocument();
        try {
            // clear previous text
            doc.remove(0, doc.getLength());
            if (media != null) {
                DataExplorerView dicomView = org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);

                if (dicomView != null) {
                    DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
                    MediaSeriesGroup study = model.getParent(series, DicomModel.study);

                    MediaReader loader = media.getMediaReader();
                    if (loader instanceof DicomMediaIO) {

                        //               writeItems("type", IMAGE_ACQ, null, doc); //$NON-NLS-1$
                    }
                }
            }
        } catch (BadLocationException ble) {
            ble.getStackTrace();
        }
        this.setViewportView(jTextPane1);
    }

    private void writeItems(String title, TagW[] tags, MediaSeriesGroup group, StyledDocument doc) {
        Style regular = doc.getStyle("regular"); //$NON-NLS-1$
        Style italic = doc.getStyle("italic"); //$NON-NLS-1$

        for (TagW t : tags) {
            try {
                Object val = group == null ? currentMedia.getTagValue(t) : group.getTagValue(t);
                if (val != null) {
                    doc.insertString(doc.getLength(), t.toString(), italic); //$NON-NLS-1$
                    doc.insertString(doc.getLength(), ": " + t.getFormattedText(val, t.getType(), null) + "\n", regular); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    public void dispose() {

    }
}
