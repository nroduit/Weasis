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
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.dicom.codec.DicomMediaIO;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

    private final TagW[] PATIENT = { TagW.PatientName, TagW.PatientID, TagW.PatientSex, TagW.PatientBirthDate };
    private final TagW[] STATION = { TagW.Manufacturer, TagW.ManufacturerModelName, TagW.StationName, };
    private final TagW[] STUDY = { TagW.StudyInstanceUID, TagW.StudyDate, TagW.StudyID, TagW.AccessionNumber,
        TagW.ReferringPhysicianName, TagW.StudyDescription };
    private final TagW[] SERIES = { TagW.SeriesInstanceUID, TagW.SeriesDate, TagW.SeriesNumber, TagW.Modality,
        TagW.ReferringPhysicianName, TagW.InstitutionName, TagW.InstitutionalDepartmentName, TagW.BodyPartExamined };
    private final TagW[] IMAGE = { TagW.SOPInstanceUID, TagW.ImageType, TagW.TransferSyntaxUID, TagW.InstanceNumber,
        TagW.PhotometricInterpretation, TagW.SamplesPerPixel, TagW.PixelRepresentation, TagW.Columns, TagW.Rows,
        TagW.ImageComments, TagW.ImageWidth, TagW.ImageHeight, TagW.ImageDepth, TagW.BitsAllocated, TagW.BitsStored };
    private final TagW[] IMAGE_PLANE = { TagW.PixelSpacing, TagW.SliceLocation, TagW.SliceThickness };
    private final TagW[] IMAGE_ACQ = { TagW.KVP, TagW.ContrastBolusAgent };

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
        addTab(Messages.getString("DicomFieldsView.limited"), null, panel, null); //$NON-NLS-1$
        panel.add(limitedPane, BorderLayout.CENTER);
        jTextPane1.setBorder(new EmptyBorder(5, 5, 5, 5));
        jTextPane1.setContentType("text/html"); //$NON-NLS-1$
        jTextPane1.setEditable(false);
        StyledDocument doc = jTextPane1.getStyledDocument();
        addStylesToDocument(doc, UIManager.getColor("TextPane.foreground")); //$NON-NLS-1$

        JPanel dump = new JPanel();
        dump.setLayout(new BorderLayout());
        dump.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab(Messages.getString("DicomFieldsView.all"), null, dump, null); //$NON-NLS-1$
        dump.add(allPane, BorderLayout.CENTER);

        setPreferredSize(new Dimension(1024, 1024));

        ChangeListener changeListener = new ChangeListener() {
            @Override
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

    public StringBuffer toStringBuffer(DicomElement element, DicomObject dcmObj) {
        StringBuffer sb = new StringBuffer();
        TagUtils.toStringBuffer(element.tag(), sb);
        sb.append(' ');
        sb.append(element.vr());
        sb.append(" #"); //$NON-NLS-1$
        sb.append(element.length());
        sb.append(" ["); //$NON-NLS-1$
        // Other VR than sequence can have several items, but there are not displayed after.
        if (element.vr() == VR.SQ || element.hasFragments()) {
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

                    MediaReader loader = media.getMediaReader();
                    if (loader instanceof DicomMediaIO) {
                        writeItems(
                            Messages.getString("DicomFieldsView.pat"), PATIENT, model.getParent(series, DicomModel.patient), doc); //$NON-NLS-1$
                        writeItems(Messages.getString("DicomFieldsView.station"), STATION, series, doc); //$NON-NLS-1$
                        writeItems(
                            Messages.getString("DicomFieldsView.study"), STUDY, model.getParent(series, DicomModel.study), doc); //$NON-NLS-1$
                        writeItems(Messages.getString("DicomFieldsView.series"), SERIES, series, doc); //$NON-NLS-1$
                        writeItems(Messages.getString("DicomFieldsView.object"), IMAGE, null, doc); //$NON-NLS-1$
                        writeItems(Messages.getString("DicomFieldsView.plane"), IMAGE_PLANE, null, doc); //$NON-NLS-1$
                        writeItems(Messages.getString("DicomFieldsView.acqu"), IMAGE_ACQ, null, doc); //$NON-NLS-1$
                    }
                }
            }
        } catch (BadLocationException ble) {
            ble.getStackTrace();
        }
        limitedPane.setViewportView(jTextPane1);
    }

    private void writeItems(String title, TagW[] tags, MediaSeriesGroup group, StyledDocument doc) {
        Style regular = doc.getStyle("regular"); //$NON-NLS-1$
        Style italic = doc.getStyle("italic"); //$NON-NLS-1$
        int insertTitle = doc.getLength();
        boolean exist = false;
        for (TagW t : tags) {
            try {
                Object val = group == null ? currentMedia.getTagValue(t) : group.getTagValue(t);
                if (val != null) {
                    exist = true;
                    doc.insertString(doc.getLength(), t.toString(), italic); //$NON-NLS-1$
                    doc.insertString(doc.getLength(), ": " + t.getFormattedText(val, t.getType(), null) + "\n", regular); //$NON-NLS-1$ //$NON-NLS-2$
                }
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
        if (exist) {
            try {
                doc.insertString(insertTitle, title, doc.getStyle("title")); //$NON-NLS-1$
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }
}
