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
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
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
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.explorer.DataExplorerView;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.AnnotationsLayer;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.util.RotatedIcon;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomFieldsView.class);

    private static final TagW[] PATIENT = { TagW.PatientName, TagW.PatientID, TagW.IssuerOfPatientID, TagW.PatientSex,
        TagW.PatientBirthDate, TagW.PatientAge };
    private static final TagW[] STATION = { TagW.Manufacturer, TagW.ManufacturerModelName, TagW.StationName, };
    private static final TagW[] STUDY = { TagW.StudyInstanceUID, TagW.StudyDate, TagW.StudyID, TagW.AccessionNumber,
        TagW.StudyDescription, TagW.StudyComments };
    private static final TagW[] SERIES =
        { TagW.SeriesInstanceUID, TagW.SeriesDate, TagW.SeriesNumber, TagW.Modality, TagW.ReferringPhysicianName,
            TagW.InstitutionName, TagW.InstitutionalDepartmentName, TagW.SeriesDescription, TagW.BodyPartExamined };
    private static final TagW[] IMAGE = { TagW.SOPInstanceUID, TagW.ImageType, TagW.FrameType, TagW.TransferSyntaxUID,
        TagW.InstanceNumber, TagW.ImageComments, TagW.ImageLaterality, TagW.PhotometricInterpretation,
        TagW.SamplesPerPixel, TagW.PixelRepresentation, TagW.Columns, TagW.Rows, TagW.ImageWidth, TagW.ImageHeight,
        TagW.ImageDepth, TagW.BitsAllocated, TagW.BitsStored };
    private static final TagW[] IMAGE_PLANE = { TagW.PixelSpacing, TagW.SliceLocation, TagW.SliceThickness,
        TagW.ImagePositionPatient, TagW.ImageOrientationPatient, };
    private static final TagW[] IMAGE_ACQ = { TagW.KVP, TagW.ContrastBolusAgent };

    private final JScrollPane allPane = new JScrollPane();
    private final JScrollPane limitedPane = new JScrollPane();
    private final JTextPane jTextPaneLimited = new JTextPane();
    private final JTextPane jTextPaneAll = new JTextPane();
    private MediaElement<?> currentMedia;
    private MediaSeries<?> currentSeries;
    private boolean anonymize = false;

    private final static Highlighter.HighlightPainter searchHighlightPainter =
        new SearchHighlightPainter(new Color(255, 125, 0));
    private final static Highlighter.HighlightPainter searchResultHighlightPainter =
        new SearchHighlightPainter(Color.YELLOW);

    public DicomFieldsView() {
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab(Messages.getString("DicomFieldsView.limited"), null, panel, null); //$NON-NLS-1$
        panel.add(new SearchPanel(jTextPaneLimited), BorderLayout.NORTH);
        panel.add(limitedPane, BorderLayout.CENTER);
        jTextPaneLimited.setBorder(new EmptyBorder(5, 5, 5, 5));
        jTextPaneLimited.setContentType("text/html"); //$NON-NLS-1$
        jTextPaneLimited.setEditable(false);
        StyledDocument doc = jTextPaneLimited.getStyledDocument();
        addStylesToDocument(doc, UIManager.getColor("TextPane.foreground")); //$NON-NLS-1$

        JPanel dump = new JPanel();
        dump.setLayout(new BorderLayout());
        dump.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab(Messages.getString("DicomFieldsView.all"), null, dump, null); //$NON-NLS-1$
        dump.add(new SearchPanel(jTextPaneAll), BorderLayout.NORTH);
        dump.add(allPane, BorderLayout.CENTER);
        jTextPaneAll.setBorder(new EmptyBorder(5, 5, 5, 5));
        jTextPaneAll.setContentType("text/html"); //$NON-NLS-1$
        jTextPaneAll.setEditable(false);
        StyledDocument doc2 = jTextPaneAll.getStyledDocument();
        addStylesToDocument(doc2, UIManager.getColor("TextPane.foreground")); //$NON-NLS-1$

        setPreferredSize(new Dimension(400, 300));
        setMinimumSize(new Dimension(150, 50));

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
                ViewCanvas<?> sel = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
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
            jTextPaneLimited.requestFocusInWindow();
            displayLimitedDicomInfo(series, media);
        } else {
            jTextPaneAll.requestFocusInWindow();
            displayAllDicomInfo(series, media);
        }
    }

    private void displayAllDicomInfo(MediaSeries<?> series, MediaElement<?> media) {
        StyledDocument doc = jTextPaneAll.getStyledDocument();
        try {
            // clear previous text
            doc.remove(0, doc.getLength());
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
                        printAttribute(metaData.getFileMetaInformation(), doc);
                        printAttribute(metaData.getAttributes(), doc);
                    }
                } else if (loader instanceof DcmMediaReader) {
                    printAttribute(((DcmMediaReader) loader).getDicomObject(), doc);
                }
                // Remove first return
                doc.remove(0, 1);
            }
        } catch (BadLocationException ble) {
            ble.getStackTrace();
        }
        allPane.setViewportView(jTextPaneAll);
    }

    private static void printAttribute(Attributes dcmObj, StyledDocument doc) {
        if (dcmObj != null) {
            int[] tags = dcmObj.tags();
            for (int tag : tags) {
                printElement(dcmObj, tag, doc);
            }
        }
    }

    private static void printElement(Attributes dcmObj, int tag, StyledDocument doc) {
        StringBuilder buf = new StringBuilder(TagUtils.toString(tag));
        buf.append(" ["); //$NON-NLS-1$
        VR vr = dcmObj.getVR(tag);
        buf.append(vr.toString());
        buf.append("] "); //$NON-NLS-1$

        String privateCreator = dcmObj.privateCreatorOf(tag);
        String word = ElementDictionary.keywordOf(tag, privateCreator);
        if (!JMVUtils.textHasContent(word)) {
            word = "PrivateTag"; //$NON-NLS-1$
        }

        buf.append(word);
        buf.append(StringUtil.COLON_AND_SPACE);

        int level = dcmObj.getLevel();
        if (level > 0) {
            buf.insert(0, "-->"); //$NON-NLS-1$
        }
        for (int i = 1; i < level; i++) {
            buf.insert(0, "--"); //$NON-NLS-1$
        }

        Sequence seq = dcmObj.getSequence(tag);
        if (seq != null) {
            if (seq.size() > 0) {
                printSequence(seq, doc, buf);
            } else {
                buf.insert(0, "\n"); //$NON-NLS-1$
                printItem(doc, buf.toString(), doc.getStyle("regular")); //$NON-NLS-1$
            }
        } else {
            buf.insert(0, "\n"); //$NON-NLS-1$
            if (vr.isInlineBinary()) {
                buf.append("binary data"); //$NON-NLS-1$
                printItem(doc, buf.toString(), doc.getStyle("regular")); //$NON-NLS-1$
            } else {
                printItem(doc, buf.toString(), doc.getStyle("regular")); //$NON-NLS-1$
                buf = new StringBuilder();
                String[] value = dcmObj.getStrings(privateCreator, tag);
                if (value != null && value.length > 0) {
                    buf.append(value[0]);
                    for (int i = 1; i < value.length; i++) {
                        buf.append("\\"); //$NON-NLS-1$
                        buf.append(value[i]);
                    }
                    if (buf.length() > 256) {
                        buf.setLength(253);
                        buf.append("..."); //$NON-NLS-1$
                    }
                }
                printItem(doc, buf.toString(), doc.getStyle("bold")); //$NON-NLS-1$
            }
        }
    }

    private static void printItem(StyledDocument doc, String val, AttributeSet attribute) {
        try {
            doc.insertString(doc.getLength(), val, attribute);
        } catch (BadLocationException e) {
            AuditLog.logError(LOGGER, e, "Error on writing dicom item!"); //$NON-NLS-1$
        }
    }

    private static void printSequence(Sequence seq, StyledDocument doc, StringBuilder buf) {
        if (seq != null) {
            buf.append(seq.size());
            if (seq.size() <= 1) {
                buf.append(" item"); //$NON-NLS-1$
            } else {
                buf.append(" items"); //$NON-NLS-1$
            }
            buf.insert(0, "\n"); //$NON-NLS-1$
            printItem(doc, buf.toString(), doc.getStyle("regular")); //$NON-NLS-1$

            for (int i = 0; i < seq.size(); i++) {
                Attributes attributes = seq.get(i);
                int level = attributes.getLevel();
                StringBuilder buffer = new StringBuilder();
                if (level > 0) {
                    buffer.insert(0, "-->"); //$NON-NLS-1$
                }
                for (int k = 1; k < level; k++) {
                    buffer.insert(0, "--"); //$NON-NLS-1$
                }
                buffer.append(" ITEM #"); //$NON-NLS-1$
                buffer.append(i + 1);
                buffer.insert(0, "\n"); //$NON-NLS-1$
                printItem(doc, buffer.toString(), doc.getStyle("regular")); //$NON-NLS-1$
                int[] tags = attributes.tags();
                for (int tag : tags) {
                    printElement(attributes, tag, doc);
                }
            }
        } else {
            buf.insert(0, "\n"); //$NON-NLS-1$
            printItem(doc, buf.toString(), doc.getStyle("regular")); //$NON-NLS-1$
        }
    }

    private void displayLimitedDicomInfo(MediaSeries<?> series, MediaElement<?> media) {

        StyledDocument doc = jTextPaneLimited.getStyledDocument();
        try {
            // clear previous text
            doc.remove(0, doc.getLength());
            if (media != null) {
                DataExplorerView dicomView = org.weasis.core.ui.docking.UIManager.getExplorerplugin(DicomExplorer.NAME);

                if (dicomView != null) {
                    DicomModel model = (DicomModel) dicomView.getDataExplorerModel();

                    MediaReader loader = media.getMediaReader();
                    if (loader instanceof DcmMediaReader) {
                        writeItems(Messages.getString("DicomFieldsView.pat"), PATIENT, //$NON-NLS-1$
                            model.getParent(series, DicomModel.patient), doc);
                        writeItems(Messages.getString("DicomFieldsView.station"), STATION, series, doc); //$NON-NLS-1$
                        writeItems(Messages.getString("DicomFieldsView.study"), STUDY, //$NON-NLS-1$
                            model.getParent(series, DicomModel.study), doc);
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
        limitedPane.setViewportView(jTextPaneLimited);
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
                            StringUtil.COLON_AND_SPACE + TagW.getFormattedText(val, t.getType(), null) + "\n", regular); //$NON-NLS-1$
                    }
                } catch (BadLocationException e) {
                    e.printStackTrace();
                }
            }
        }
        if (exist) {
            try {
                String formatTitle = PATIENT == tags ? title + "\n" : "\n" + title + "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                doc.insertString(insertTitle, formatTitle, doc.getStyle("title")); //$NON-NLS-1$
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        }
    }

    public static void displayHeader(ImageViewerPlugin<?> container) {
        if (container != null) {
            ViewCanvas<?> selView = container.getSelectedImagePane();
            if (selView != null) {
                ImageElement img = selView.getImage();
                if (img != null) {
                    JFrame frame = new JFrame(Messages.getString("DicomExplorer.dcmInfo")); //$NON-NLS-1$
                    frame.setSize(500, 630);
                    DicomFieldsView view = new DicomFieldsView();
                    view.changingViewContentEvent(
                        new SeriesViewerEvent(container, selView.getSeries(), img, EVENT.SELECT));
                    JPanel panel = new JPanel();
                    panel.setLayout(new BorderLayout());
                    panel.add(view);
                    frame.getContentPane().add(panel);
                    frame.setAlwaysOnTop(true);
                    JMVUtils.showCenterScreen(frame, container);
                }
            }
        }
    }

    public static void displayHeaderForSpecialElement(ViewerPlugin<?> container, Series<?> series) {
        if (container != null && series != null) {
            DicomSpecialElement dcm = DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
            if (dcm != null) {
                JFrame frame = new JFrame(Messages.getString("DicomExplorer.dcmInfo")); //$NON-NLS-1$
                frame.setSize(500, 630);
                DicomFieldsView view = new DicomFieldsView();
                view.changingViewContentEvent(new SeriesViewerEvent(container, series, dcm, EVENT.SELECT));
                JPanel panel = new JPanel();
                panel.setLayout(new BorderLayout());
                panel.add(view);
                frame.getContentPane().add(panel);
                frame.setAlwaysOnTop(true);
                JMVUtils.showCenterScreen(frame, container);
            }
        }
    }

    static class SearchHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {
        public SearchHighlightPainter(Color color) {
            super(color);
        }
    }

    static class SearchPanel extends JPanel {
        private final List<Integer> searchPostions = new ArrayList<Integer>();
        private final JTextComponent textComponent;
        private int currentSearchIndex = 0;
        private String currentSearchPattern;

        public SearchPanel(JTextComponent textComponent) {
            super();
            this.textComponent = textComponent;
            this.setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            init();
        }

        private void init() {
            this.add(new JLabel(Messages.getString("DicomFieldsView.search") + StringUtil.COLON_AND_SPACE)); //$NON-NLS-1$
            final JTextField tf = new JTextField();
            JMVUtils.setPreferredWidth(tf, 300, 100);
            tf.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    currentSearchPattern = tf.getText().trim();
                    highlight(currentSearchPattern);
                    if (searchPostions.size() > 0) {
                        try {
                            textComponent.scrollRectToVisible(textComponent.modelToView(searchPostions.get(0)));
                            textComponent.requestFocusInWindow();
                        } catch (BadLocationException e) {
                        }
                    }
                }
            });
            this.add(tf);
            JButton up = new JButton(new ImageIcon(SeriesViewerListener.class.getResource("/icon/up.png"))); //$NON-NLS-1$
            up.setToolTipText(Messages.getString("DicomFieldsView.previous")); //$NON-NLS-1$
            up.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    previous();
                }
            });
            this.add(up);
            JButton down =
                new JButton(new RotatedIcon(new ImageIcon(SeriesViewerListener.class.getResource("/icon/up.png")), //$NON-NLS-1$
                    RotatedIcon.Rotate.UPSIDE_DOWN));
            down.setToolTipText(Messages.getString("DicomFieldsView.next")); //$NON-NLS-1$
            down.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent evt) {
                    next();
                }
            });
            this.add(down);
            textComponent.addKeyListener(new KeyListener() {

                @Override
                public void keyTyped(KeyEvent e) {
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    if (e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_F3) {
                        previous();
                    } else if (e.getKeyCode() == KeyEvent.VK_F3) {
                        next();
                    }
                }

                @Override
                public void keyPressed(KeyEvent e) {
                }
            });

            textComponent.setFocusable(true);
        }

        private void previous() {
            if (searchPostions.size() > 0) {
                currentSearchIndex = currentSearchIndex <= 0 ? searchPostions.size() - 1 : currentSearchIndex - 1;
                showCurrentSearch(currentSearchPattern);
            }
        }

        private void next() {
            if (searchPostions.size() > 0) {
                currentSearchIndex = currentSearchIndex >= searchPostions.size() - 1 ? 0 : currentSearchIndex + 1;
                showCurrentSearch(currentSearchPattern);
            }
        }

        public void highlight(String pattern) {
            removeHighlights(textComponent);
            searchPostions.clear();
            if (StringUtil.hasText(pattern)) {
                try {
                    Highlighter hilite = textComponent.getHighlighter();
                    Document doc = textComponent.getDocument();
                    String text = doc.getText(0, doc.getLength()).toUpperCase();
                    pattern = pattern.toUpperCase();
                    int pos = 0;

                    while ((pos = text.indexOf(pattern, pos)) >= 0) {
                        if (searchPostions.size() == 0) {
                            hilite.addHighlight(pos, pos + pattern.length(), searchHighlightPainter);
                        } else {
                            hilite.addHighlight(pos, pos + pattern.length(), searchResultHighlightPainter);
                        }
                        searchPostions.add(pos);
                        pos += pattern.length();
                    }
                } catch (BadLocationException e) {
                }
            }
        }

        public void removeHighlights(JTextComponent textComonent) {
            Highlighter hilite = textComonent.getHighlighter();
            for (Highlighter.Highlight highlight : hilite.getHighlights()) {
                if (highlight.getPainter() instanceof SearchHighlightPainter) {
                    hilite.removeHighlight(highlight);
                }
            }
        }

        public void showCurrentSearch(String pattern) {
            if (searchPostions.size() > 0 && StringUtil.hasText(pattern)) {
                removeHighlights(textComponent);

                try {
                    if (currentSearchIndex < 0 || currentSearchIndex >= searchPostions.size()) {
                        currentSearchIndex = 0;
                    }
                    int curPos = searchPostions.get(currentSearchIndex);
                    Highlighter hilite = textComponent.getHighlighter();

                    for (Integer pos : searchPostions) {
                        if (pos == curPos) {
                            hilite.addHighlight(pos, pos + pattern.length(), searchHighlightPainter);
                        } else {
                            hilite.addHighlight(pos, pos + pattern.length(), searchResultHighlightPainter);
                        }
                    }
                    textComponent.scrollRectToVisible(textComponent.modelToView(curPos));
                } catch (BadLocationException e) {
                }
            }
        }
    }
}
