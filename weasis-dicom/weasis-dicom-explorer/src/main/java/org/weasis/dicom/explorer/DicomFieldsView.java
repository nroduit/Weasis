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
import java.io.IOException;

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

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.DefaultView2d;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.dicom.codec.DicomMediaIO;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomFieldsView.class);

    private static final TagW[] PATIENT = { TagW.PatientName, TagW.PatientID, TagW.IssuerOfPatientID, TagW.PatientSex,
        TagW.PatientBirthDate };
    private static final TagW[] STATION = { TagW.Manufacturer, TagW.ManufacturerModelName, TagW.StationName, };
    private static final TagW[] STUDY = { TagW.StudyInstanceUID, TagW.StudyDate, TagW.StudyID, TagW.AccessionNumber,
        TagW.StudyDescription, TagW.StudyComments };
    private static final TagW[] SERIES = { TagW.SeriesInstanceUID, TagW.SeriesDate, TagW.SeriesNumber, TagW.Modality,
        TagW.ReferringPhysicianName, TagW.InstitutionName, TagW.InstitutionalDepartmentName, TagW.BodyPartExamined };
    private static final TagW[] IMAGE = { TagW.SOPInstanceUID, TagW.ImageType, TagW.TransferSyntaxUID,
        TagW.InstanceNumber, TagW.ImageComments, TagW.ImageLaterality, TagW.PhotometricInterpretation,
        TagW.SamplesPerPixel, TagW.PixelRepresentation, TagW.Columns, TagW.Rows, TagW.ImageWidth, TagW.ImageHeight,
        TagW.ImageDepth, TagW.BitsAllocated, TagW.BitsStored };
    private static final TagW[] IMAGE_PLANE = { TagW.PixelSpacing, TagW.SliceLocation, TagW.SliceThickness };
    private static final TagW[] IMAGE_ACQ = { TagW.KVP, TagW.ContrastBolusAgent };

    private final JScrollPane allPane = new JScrollPane();
    private final JScrollPane limitedPane = new JScrollPane();
    private final JTextPane jTextPane1 = new JTextPane();
    private MediaElement<?> currentMedia;
    private MediaSeries<?> currentSeries;
    private boolean anonymize = false;

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

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (EVENT.SELECT.equals(type) || EVENT.LAYOUT.equals(type) || EVENT.ANONYM.equals(type)) {
            currentMedia = event.getMediaElement();
            currentSeries = event.getSeries();
            if (event.getSeriesViewer() instanceof ImageViewerPlugin) {
                DefaultView2d<?> sel = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
                if (sel != null) {
                    anonymize = sel.getInfoLayer().getDisplayPreferences(AnnotationsLayer.ANONYM_ANNOTATIONS);
                }
            }
            changeDicomInfo(currentSeries, currentMedia);
        }
    }

    private void changeDicomInfo(MediaSeries<?> series, MediaElement<?> media) {
        int index = getSelectedIndex();
        if (index == 0) {
            displayLimitedDicomInfo(series, media);
        } else {
            displayAllDicomInfo(series, media);
        }
    }

    private void displayAllDicomInfo(MediaSeries<?> series, MediaElement<?> media) {
        // DefaultListModel<String> listModel = new DefaultListModel<String>(); // only compliant with in JAVA7
        DefaultListModel listModel = new DefaultListModel();
        if (media != null) {
            MediaReader loader = media.getMediaReader();
            if (loader instanceof DicomMediaIO) {
                DicomMetaData metaData = null;
                try {
                    metaData = (DicomMetaData) ((DicomMediaIO) loader).getStreamMetadata();
                } catch (IOException e) {
                    LOGGER.error(e.getMessage());
                }
                if (metaData != null) {
                    printAttribute(metaData.getFileMetaInformation(), listModel);
                    printAttribute(metaData.getAttributes(), listModel);
                }
            }
        }

        JList jListElement = new JList(listModel);
        jListElement.setLayoutOrientation(JList.VERTICAL);
        jListElement.setBorder(new EmptyBorder(5, 5, 5, 5));
        allPane.setViewportView(jListElement);
    }

    private static void printAttribute(Attributes dcmObj, DefaultListModel listModel) {
        if (dcmObj != null) {
            int[] tags = dcmObj.tags();
            for (int tag : tags) {
                printElement(dcmObj, tag, listModel);
            }
        }
    }

    private static void printElement(Attributes dcmObj, int tag, DefaultListModel listModel) {
        StringBuilder buf = new StringBuilder(TagUtils.toString(tag));
        buf.append(" [");
        VR vr = dcmObj.getVR(tag);
        buf.append(vr.toString());
        buf.append("] ");

        String privateCreator = dcmObj.privateCreatorOf(tag);
        String word = ElementDictionary.keywordOf(tag, privateCreator);
        if (!JMVUtils.textHasContent(word)) {
            word = "PrivateTag";
        }

        buf.append(word);
        buf.append(": ");

        int level = dcmObj.getLevel();
        if (level > 0) {
            buf.insert(0, "-->");
        }
        for (int i = 1; i < level; i++) {
            buf.insert(0, "--");
        }

        Sequence seq = dcmObj.getSequence(tag);
        if (seq != null) {
            if (seq.size() > 0) {
                printSequence(seq, listModel, buf);
            } else {
                listModel.addElement(buf.toString());
            }
        } else {
            if (vr.isInlineBinary()) {
                buf.append("binary data");
            } else {
                String[] value = dcmObj.getStrings(privateCreator, tag);
                if (value != null && value.length > 0) {
                    buf.append(value[0]);
                    for (int i = 1; i < value.length; i++) {
                        buf.append("\\");
                        buf.append(value[i]);
                    }
                    if (buf.length() > 256) {
                        buf.setLength(253);
                        buf.append("...");
                    }
                }
            }
            listModel.addElement(buf.toString());
        }
    }

    private static void printSequence(Sequence seq, DefaultListModel listModel, StringBuilder buf) {
        if (seq != null) {
            buf.append(seq.size());
            if (seq.size() <= 1) {
                buf.append(" item");
            } else {
                buf.append(" items");
            }
            listModel.addElement(buf.toString());

            for (int i = 0; i < seq.size(); i++) {
                Attributes attributes = seq.get(i);
                int level = attributes.getLevel();
                StringBuilder buffer = new StringBuilder();
                if (level > 0) {
                    buffer.insert(0, "-->");
                }
                for (int k = 1; k < level; k++) {
                    buffer.insert(0, "--");
                }
                buffer.append(" ITEM #");
                buffer.append(i + 1);
                listModel.addElement(buffer.toString());
                int[] tags = attributes.tags();
                for (int tag : tags) {
                    printElement(attributes, tag, listModel);
                }
            }
        } else {
            listModel.addElement(buf.toString());
        }
    }

    private void displayLimitedDicomInfo(MediaSeries<?> series, MediaElement<?> media) {

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
            if (!anonymize || t.getAnonymizationType() != 1) {
                try {
                    Object val = group == null ? currentMedia.getTagValue(t) : group.getTagValue(t);
                    if (val != null) {
                        exist = true;
                        doc.insertString(doc.getLength(), t.toString(), italic);
                        doc.insertString(doc.getLength(),
                            ": " + TagW.getFormattedText(val, t.getType(), null) + "\n", regular); //$NON-NLS-1$ //$NON-NLS-2$
                    }
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
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
