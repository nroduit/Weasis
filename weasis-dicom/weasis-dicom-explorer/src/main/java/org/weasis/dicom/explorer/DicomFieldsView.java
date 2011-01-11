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
package org.weasis.dicom.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.Iterator;

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
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagElement;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.dicom.codec.DicomMediaIO;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

    private final TagElement[] PATIENT = { TagElement.PatientName, TagElement.PatientID, TagElement.PatientSex,
        TagElement.PatientBirthDate };
    private final TagElement[] STATION = { TagElement.Manufacturer, TagElement.ManufacturerModelName,
        TagElement.StationName, };
    private final TagElement[] STUDY = { TagElement.StudyInstanceUID, TagElement.StudyDate, TagElement.StudyID,
        TagElement.AccessionNumber, TagElement.ReferringPhysicianName, TagElement.StudyDescription };
    private final TagElement[] SERIES = { TagElement.SeriesInstanceUID, TagElement.SeriesDate, TagElement.SeriesNumber,
        TagElement.Modality, TagElement.ReferringPhysicianName, TagElement.InstitutionName,
        TagElement.InstitutionalDepartmentName, TagElement.BodyPartExamined };
    private final TagElement[] IMAGE = { TagElement.SOPInstanceUID, TagElement.ImageType, TagElement.TransferSyntaxUID,
        TagElement.InstanceNumber, TagElement.PhotometricInterpretation, TagElement.SamplesPerPixel,
        TagElement.PixelRepresentation, TagElement.Columns, TagElement.Rows, TagElement.ImageComments,
        TagElement.ImageWidth, TagElement.ImageHeight, TagElement.ImageDepth, TagElement.BitsAllocated,
        TagElement.BitsStored };
    private final TagElement[] IMAGE_PLANE = { TagElement.PixelSpacing, TagElement.SliceLocation,
        TagElement.SliceThickness };
    private final TagElement[] IMAGE_ACQ = { TagElement.KVP, TagElement.ContrastBolusAgent };

    private static final ThreadLocal<char[]> cbuf = new ThreadLocal<char[]>() {

        @Override
        protected char[] initialValue() {
            return new char[96];
        }
    };
    private final JScrollPane allPane = new JScrollPane();
    private final JScrollPane limitedPane = new JScrollPane();
    private final JTextPane jTextPane1 = new JTextPane();
    private MediaElement currentMedia;
    private Series currentSeries;

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
                changeDicomInfo(currentSeries, currentMedia);
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
            currentSeries = event.getSeries();
            changeDicomInfo(currentSeries, currentMedia);
        }
    }

    private void changeDicomInfo(Series series, MediaElement media) {
        int index = getSelectedIndex();
        if (index == 0) {
            displayLimitedDicomInfo(series, media);
        } else {
            displayAllDicomInfo(series, media);
        }
    }

    private void displayAllDicomInfo(Series series, MediaElement media) {
        DefaultListModel listModel = new DefaultListModel();
        if (media != null) {
            MediaReader loader = media.getMediaReader();
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

    private void displayLimitedDicomInfo(Series series, MediaElement media) {

        StyledDocument doc = jTextPane1.getStyledDocument();
        try {
            // clear previous text
            doc.remove(0, doc.getLength());
            if (media != null) {
                DataExplorerView dicomView = org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);

                if (dicomView != null) {
                    DicomModel model = (DicomModel) dicomView.getDataExplorerModel();
                    MediaSeriesGroup study = model.getParent(series, DicomModel.study);

                    Style regular = doc.getStyle("regular"); //$NON-NLS-1$
                    Style title = doc.getStyle("title"); //$NON-NLS-1$
                    Style italic = doc.getStyle("italic"); //$NON-NLS-1$

                    MediaReader loader = media.getMediaReader();
                    if (loader instanceof DicomMediaIO) {
                        doc.insertString(doc.getLength(), "Patient\n", title);
                        writeItems(PATIENT, model.getParent(series, DicomModel.patient), doc);
                        doc.insertString(doc.getLength(), "\nStation\n", title);
                        writeItems(STATION, series, doc);
                        doc.insertString(doc.getLength(), "\nStudy\n", title);
                        writeItems(STUDY, model.getParent(series, DicomModel.study), doc);
                        doc.insertString(doc.getLength(), "\nSeries\n", title);
                        writeItems(SERIES, series, doc);
                        doc.insertString(doc.getLength(), "\nImage\n", title);
                        writeItems(IMAGE, null, doc);
                        doc.insertString(doc.getLength(), "\nImage Plane\n", title);
                        writeItems(IMAGE_PLANE, null, doc);
                        doc.insertString(doc.getLength(), "\nImage Acquisition\n", title);
                        writeItems(IMAGE_ACQ, null, doc);
                    }
                }
            }
        } catch (BadLocationException ble) {
            ble.getStackTrace();
        }
        limitedPane.setViewportView(jTextPane1);
    }

    private void writeItems(TagElement[] tags, MediaSeriesGroup group, StyledDocument doc) {
        Style regular = doc.getStyle("regular"); //$NON-NLS-1$
        Style italic = doc.getStyle("italic"); //$NON-NLS-1$

        for (TagElement t : tags) {
            try {
                Object val = group == null ? currentMedia.getTagValue(t) : group.getTagValue(t);
                if (val != null) {
                    doc.insertString(doc.getLength(), t.toString(), italic); //$NON-NLS-1$
                    doc.insertString(doc.getLength(), ": " + t.getFormattedText(val, t.getType(), null) + "\n", regular); //$NON-NLS-1$
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }
}
