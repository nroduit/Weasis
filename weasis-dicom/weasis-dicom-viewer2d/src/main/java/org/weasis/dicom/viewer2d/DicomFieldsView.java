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
package org.weasis.dicom.viewer2d;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Iterator;

import javax.media.jai.PlanarImage;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.VR;
import org.dcm4che2.util.TagUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

    private final TagElement[] PATIENTS = { TagElement.PatientName, TagElement.PatientID, TagElement.PatientSex,
        TagElement.PatientBirthDate };
    private static final ThreadLocal<char[]> cbuf = new ThreadLocal<char[]>() {

        @Override
        protected char[] initialValue() {
            return new char[96];
        }
    };
    private final JScrollPane allPane = new JScrollPane();
    private final JScrollPane limitedPane = new JScrollPane();
    private final JTextPane jTextPane1 = new JTextPane();
    private MediaElement currentMedia = null;

    public DicomFieldsView() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab("Limited DICOM attributes", null, panel, null);
        panel.add(limitedPane, BorderLayout.CENTER);
        jTextPane1.setBorder(new EmptyBorder(5, 5, 5, 5));
        jTextPane1.setContentType("text/html"); //$NON-NLS-1$
        jTextPane1.setEditable(false);
        StyledDocument doc = jTextPane1.getStyledDocument();
        addStylesToDocument(doc, UIManager.getColor("TextPane.foreground"));

        JPanel dump = new JPanel();
        dump.setLayout(new BorderLayout());
        dump.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab("All DICOM attributes", null, dump, null);
        dump.add(allPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1024, 1024));

        ChangeListener changeListener = new ChangeListener() {
            public void stateChanged(ChangeEvent changeEvent) {
                changeDicomInfo(currentMedia);
            }
        };
        this.addChangeListener(changeListener);

    }

    public static void addStylesToDocument(StyledDocument doc, Color textColor) {
        // Initialize some styles.
        final MutableAttributeSet def = new SimpleAttributeSet();
        Style style = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE);
        Style regular = doc.addStyle("regular", style); //$NON-NLS-1$
        StyleConstants.setFontFamily(def, "SansSerif"); //$NON-NLS-1$
        StyleConstants.setFontSize(def, 12);
        if (textColor == null) {
            textColor = UIManager.getColor("text");
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

    public StringBuffer toStringBuffer(DicomElement element, DicomObject dcmObj) {
        StringBuffer sb = new StringBuffer();
        TagUtils.toStringBuffer(element.tag(), sb);
        sb.append(' ');
        sb.append(element.vr());
        sb.append(" #"); //$NON-NLS-1$
        sb.append(element.length());
        sb.append(" ["); //$NON-NLS-1$
        if (element.vr() == VR.SQ) {
            final int size = element.countItems();
            if (size != 0) {
                if (size == 1) {
                    sb.append("1 item"); //$NON-NLS-1$
                } else {
                    sb.append(size).append(" items"); //$NON-NLS-1$
                }
            }
        } else {
            element.vr().promptValue(element.getBytes(), element.bigEndian(), null, cbuf.get(), 96, sb);
        }
        sb.append("] "); //$NON-NLS-1$
        // String tag = ElementDictionary.getDictionary().nameOf(element.tag());
        // if (tag != null) {
        sb.append(dcmObj.nameOf(element.tag()));
        // }
        return sb;
    }

    public void addSequenceElement(DicomElement element, DefaultListModel listModel) {
        for (int i = 0, n = element.countItems(); i < n; ++i) {
            DicomObject dcmObj = element.getDicomObject(i);
            String[] val = dcmObj.toString().split("\n"); //$NON-NLS-1$
            for (int j = 0; j < val.length; j++) {
                listModel.addElement(" >" + val[j]); //$NON-NLS-1$
            }

        }
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        if (event.getEventType().equals(EVENT.SELECT) || event.getEventType().equals(EVENT.LAYOUT)) {
            currentMedia = event.getMediaElement();
            changeDicomInfo(currentMedia);
        }
    }

    private void changeDicomInfo(MediaElement media) {
        int index = getSelectedIndex();
        if (index == 0) {
            displayLimitedDicomInfo(media);
        } else {
            displayAllDicomInfo(media);
        }
    }

    private void displayAllDicomInfo(MediaElement media) {
        DefaultListModel listModel = new DefaultListModel();
        if (media instanceof DicomImageElement) {
            MediaReader<PlanarImage> loader = ((DicomImageElement) media).getMediaReader();
            if (loader instanceof DicomMediaIO) {
                DicomObject dcmObj = ((DicomMediaIO) loader).getDicomObject();
                Iterator it = dcmObj.fileMetaInfoIterator();
                while (it.hasNext()) {
                    DicomElement element = (DicomElement) it.next();
                    listModel.addElement(toStringBuffer(element, dcmObj).toString());
                    if (element.vr() == VR.SQ) {
                        addSequenceElement(element, listModel);
                    }
                }
                it = dcmObj.datasetIterator();
                while (it.hasNext()) {
                    DicomElement element = (DicomElement) it.next();
                    listModel.addElement(toStringBuffer(element, dcmObj).toString());
                    if (element.vr() == VR.SQ) {
                        addSequenceElement(element, listModel);
                    }
                }
            }
        }

        JList jListElement = new JList(listModel);
        jListElement.setLayoutOrientation(JList.VERTICAL);
        jListElement.setBorder(new EmptyBorder(5, 5, 5, 5));
        allPane.setViewportView(jListElement);
    }

    private void displayLimitedDicomInfo(MediaElement media) {

        StyledDocument doc = jTextPane1.getStyledDocument();
        try {
            // clear previous text
            doc.remove(0, doc.getLength());
            if (media != null) {
                Style regular = doc.getStyle("regular"); //$NON-NLS-1$
                Style title = doc.getStyle("title"); //$NON-NLS-1$
                Style italic = doc.getStyle("italic"); //$NON-NLS-1$

                MediaReader loader = media.getMediaReader();
                if (loader instanceof DicomMediaIO) {
                    DicomObject dcmObj = ((DicomMediaIO) loader).getDicomObject();
                    doc.insertString(doc.getLength(), "Patient\n", title); //$NON-NLS-1$ 
                    for (TagElement t : PATIENTS) {
                        String val = dcmObj.getString(t.getId());
                        doc.insertString(doc.getLength(), t.toString(), italic); //$NON-NLS-1$
                        doc.insertString(doc.getLength(), ": " + val + "\n", regular); //$NON-NLS-1$
                    }
                }

            }
        } catch (BadLocationException ble) {
            ble.getStackTrace();
        }
        limitedPane.setViewportView(jTextPane1);
    }
}
