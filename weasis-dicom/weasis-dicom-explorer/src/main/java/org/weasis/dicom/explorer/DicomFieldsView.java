/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.explorer;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
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
import javax.swing.border.EmptyBorder;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Document;
import javax.swing.text.Highlighter;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyledDocument;
import javax.swing.text.html.HTMLEditorKit;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.VR;
import org.dcm4che3.imageio.plugins.dcm.DicomMetaData;
import org.dcm4che3.util.TagUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.gui.util.JMVUtils;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagView;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.AuditLog;
import org.weasis.core.api.util.StringUtil;
import org.weasis.core.ui.docking.UIManager;
import org.weasis.core.ui.editor.SeriesViewer;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.image.ImageViewerPlugin;
import org.weasis.core.ui.editor.image.ViewCanvas;
import org.weasis.core.ui.editor.image.ViewerPlugin;
import org.weasis.core.ui.model.layer.LayerAnnotation;
import org.weasis.core.ui.util.RotatedIcon;
import org.weasis.dicom.codec.DcmMediaReader;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.explorer.wado.DicomManager;

public class DicomFieldsView extends JTabbedPane implements SeriesViewerListener {

    private static final Logger LOGGER = LoggerFactory.getLogger(DicomFieldsView.class);

    private final JScrollPane allPane = new JScrollPane();
    private final JScrollPane limitedPane = new JScrollPane();
    private final JTextPane jTextPaneLimited = new JTextPane();
    private final JTextPane jTextPaneAll = new JTextPane();
    private MediaElement currentMedia;
    private MediaSeries<?> currentSeries;
    private boolean anonymize = false;
    private SeriesViewer<?> viewer;

    private static final Highlighter.HighlightPainter searchHighlightPainter =
        new SearchHighlightPainter(new Color(255, 125, 0));
    private static final Highlighter.HighlightPainter searchResultHighlightPainter =
        new SearchHighlightPainter(Color.YELLOW);

    public DicomFieldsView(SeriesViewer<?> viewer) {
        this.viewer = viewer;
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab(Messages.getString("DicomFieldsView.limited"), null, panel, null); //$NON-NLS-1$
        panel.add(new SearchPanel(jTextPaneLimited), BorderLayout.NORTH);
        panel.add(limitedPane, BorderLayout.CENTER);
        jTextPaneLimited.setBorder(new EmptyBorder(5, 5, 5, 5));
        // Keep this order to avoid build a default editor
        HTMLEditorKit kit = JMVUtils.buildHTMLEditorKit(jTextPaneLimited);
        jTextPaneLimited.setEditorKit(kit);
        jTextPaneLimited.setContentType("text/html"); //$NON-NLS-1$
        jTextPaneLimited.setEditable(false);
        JMVUtils.addStylesToHTML(jTextPaneLimited.getStyledDocument());

        JPanel dump = new JPanel();
        dump.setLayout(new BorderLayout());
        dump.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTab(Messages.getString("DicomFieldsView.all"), null, dump, null); //$NON-NLS-1$
        dump.add(new SearchPanel(jTextPaneAll), BorderLayout.NORTH);
        dump.add(allPane, BorderLayout.CENTER);
        jTextPaneAll.setBorder(new EmptyBorder(5, 5, 5, 5));
        jTextPaneAll.setEditorKit(kit);
        jTextPaneAll.setContentType("text/html"); //$NON-NLS-1$
        jTextPaneAll.setEditable(false);
        JMVUtils.addStylesToHTML(jTextPaneAll.getStyledDocument());

        setPreferredSize(new Dimension(400, 300));
        setMinimumSize(new Dimension(150, 50));

        this.addChangeListener(changeEvent -> changeDicomInfo(currentSeries, currentMedia));
    }

    @Override
    public void changingViewContentEvent(SeriesViewerEvent event) {
        EVENT type = event.getEventType();
        if (event.getSeriesViewer() == viewer
            && (EVENT.SELECT.equals(type) || EVENT.LAYOUT.equals(type) || EVENT.ANONYM.equals(type))) {
            currentMedia = event.getMediaElement();
            currentSeries = event.getSeries();
            if (event.getSeriesViewer() instanceof ImageViewerPlugin) {
                ViewCanvas<?> sel = ((ImageViewerPlugin<?>) event.getSeriesViewer()).getSelectedImagePane();
                if (sel != null) {
                    anonymize = sel.getInfoLayer().getDisplayPreferences(LayerAnnotation.ANONYM_ANNOTATIONS);
                }
            }
            changeDicomInfo(currentSeries, currentMedia);
        }
    }

    private void changeDicomInfo(MediaSeries<?> series, MediaElement media) {
        int index = getSelectedIndex();
        if (index == 0) {
            jTextPaneLimited.requestFocusInWindow();
            displayLimitedDicomInfo(series, media);
        } else {
            jTextPaneAll.requestFocusInWindow();
            displayAllDicomInfo(series, media);
        }
    }

    private void displayAllDicomInfo(MediaSeries<?> series, MediaElement media) {
        StyledDocument doc = jTextPaneAll.getStyledDocument();
        int oldCaretPosition = jTextPaneAll.getCaretPosition();
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
                        LOGGER.error("Get metadata", e); //$NON-NLS-1$
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
        } catch (BadLocationException e) {
            LOGGER.error("Clear document", e); //$NON-NLS-1$
        }
        oldCaretPosition = oldCaretPosition > doc.getLength() ? doc.getLength() : oldCaretPosition;
        jTextPaneAll.setCaretPosition(oldCaretPosition);
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
        if (!StringUtil.hasText(word)) {
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
            if (!seq.isEmpty()) {
                printSequence(seq, doc, buf);
            } else {
                buf.insert(0, "\n"); //$NON-NLS-1$
                printItem(doc, buf.toString(), null);
            }
        } else {
            buf.insert(0, "\n"); //$NON-NLS-1$
            if (vr.isInlineBinary()) {
                buf.append("binary data"); //$NON-NLS-1$
                printItem(doc, buf.toString(), null);
            } else {
                printItem(doc, buf.toString(), null);
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
            printItem(doc, buf.toString(), null);

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
                printItem(doc, buffer.toString(), null);
                int[] tags = attributes.tags();
                for (int tag : tags) {
                    printElement(attributes, tag, doc);
                }
            }
        } else {
            buf.insert(0, "\n"); //$NON-NLS-1$
            printItem(doc, buf.toString(), null);
        }
    }

    private void displayLimitedDicomInfo(MediaSeries<?> series, MediaElement media) {
        StyledDocument doc = jTextPaneLimited.getStyledDocument();
        int oldCaretPosition = jTextPaneLimited.getCaretPosition();
        try {
            // clear previous text
            doc.remove(0, doc.getLength());
        } catch (BadLocationException e) {
            LOGGER.error("Clear document", e); //$NON-NLS-1$
        }
        if (series != null && media != null) {
            Object tagValue = series.getTagValue(TagW.ExplorerModel);
            if (tagValue instanceof DicomModel) {
                DicomModel model = (DicomModel) tagValue;

                MediaReader loader = media.getMediaReader();
                if (loader instanceof DcmMediaReader) {
                    List<DicomData> list = DicomManager.getInstance().getLimitedDicomTags();
                    for (DicomData dicomData : list) {
                        writeItems(dicomData, getGroup(model, series, dicomData), doc);
                    }
                }
            }
        }
        oldCaretPosition = oldCaretPosition > doc.getLength() ? doc.getLength() : oldCaretPosition;
        jTextPaneLimited.setCaretPosition(oldCaretPosition);
        limitedPane.setViewportView(jTextPaneLimited);
    }

    private MediaSeriesGroup getGroup(DicomModel model, MediaSeries<?> series, DicomData dicomData) {
        Level level = dicomData.getLevel();

        if (Level.PATIENT.equals(level)) {
            return model.getParent(series, DicomModel.patient);
        } else if (Level.STUDY.equals(level)) {
            return model.getParent(series, DicomModel.study);
        } else if (Level.SERIES.equals(level)) {
            return model.getParent(series, DicomModel.series);
        }
        return null;
    }

    private void writeItems(DicomData dicomData, TagReadable group, StyledDocument doc) {
        int insertTitle = doc.getLength();
        boolean exist = false;
        for (TagView t : dicomData.getInfos()) {
            for (TagW tag : t.getTag()) {
                if (!anonymize || tag.getAnonymizationType() != 1) {
                    try {
                        Object val = TagUtil.getTagValue(tag, group, currentMedia);
                        if (val != null) {
                            exist = true;
                            doc.insertString(doc.getLength(), tag.getDisplayedName(), null);
                            doc.insertString(doc.getLength(),
                                StringUtil.COLON_AND_SPACE + tag.getFormattedTagValue(val, null) + "\n", //$NON-NLS-1$
                                doc.getStyle("bold")); //$NON-NLS-1$
                            break;
                        }
                    } catch (BadLocationException e) {
                        LOGGER.error("Writing textissue", e); //$NON-NLS-1$
                    }
                }
            }
        }
        if (exist) {
            try {
                String formatTitle = insertTitle < 3 ? dicomData.getTitle() + "\n" : "\n" + dicomData.getTitle() + "\n"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
                doc.insertString(insertTitle, formatTitle, doc.getStyle("title")); //$NON-NLS-1$
            } catch (BadLocationException e) {
                LOGGER.error("Writing text issue", e); //$NON-NLS-1$
            }
        }
    }

    public static void displayHeader(ImageViewerPlugin<?> container) {
        if (container != null) {
            ViewCanvas<?> selView = container.getSelectedImagePane();
            if (selView != null) {
                showHeaderDialog(container, selView.getSeries(), selView.getImage());
            }
        }
    }

    public static void displayHeaderForSpecialElement(ViewerPlugin<?> container, Series<?> series) {
        if (container != null && series != null) {
            DicomSpecialElement dcm = DicomModel.getFirstSpecialElement(series, DicomSpecialElement.class);
            showHeaderDialog(container, series, dcm);
        }
    }

    public static void showHeaderDialog(SeriesViewer<?> container, MediaSeries<? extends MediaElement> series,
        MediaElement dcm) {
        if (container != null && series != null && dcm != null) {
            JFrame frame = new JFrame(Messages.getString("DicomExplorer.dcmInfo")); //$NON-NLS-1$
            frame.setSize(500, 630);
            DicomFieldsView view = new DicomFieldsView(container);
            view.changingViewContentEvent(new SeriesViewerEvent(container, series, dcm, EVENT.SELECT));
            JPanel panel = new JPanel();
            panel.setLayout(new BorderLayout());
            panel.add(view);
            frame.getContentPane().add(panel);
            frame.setAlwaysOnTop(true);
            frame.setIconImage(
                new ImageIcon(ImageViewerPlugin.class.getResource("/icon/32x32/dcm-header.png")).getImage()); //$NON-NLS-1$
            Component c = container instanceof Component ? (Component) container : UIManager.MAIN_AREA.getComponent();
            JMVUtils.showCenterScreen(frame, c);
        }
    }

    static class SearchHighlightPainter extends DefaultHighlighter.DefaultHighlightPainter {
        public SearchHighlightPainter(Color color) {
            super(color);
        }
    }

    static class SearchPanel extends JPanel {
        private final List<Integer> searchPostions = new ArrayList<>();
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
            tf.addActionListener(evt -> {
                currentSearchPattern = tf.getText().trim();
                highlight(currentSearchPattern);
                if (!searchPostions.isEmpty()) {
                    try {
                        textComponent.scrollRectToVisible(textComponent.modelToView(searchPostions.get(0)));
                        textComponent.requestFocusInWindow();
                    } catch (BadLocationException e) {
                        LOGGER.error("Scroll to highight", e); //$NON-NLS-1$
                    }
                }
            });
            this.add(tf);
            JButton up = new JButton(new ImageIcon(SeriesViewerListener.class.getResource("/icon/up.png"))); //$NON-NLS-1$
            up.setToolTipText(Messages.getString("DicomFieldsView.previous")); //$NON-NLS-1$
            up.addActionListener(evt -> previous());
            this.add(up);
            JButton down =
                new JButton(new RotatedIcon(new ImageIcon(SeriesViewerListener.class.getResource("/icon/up.png")), //$NON-NLS-1$
                    RotatedIcon.Rotate.UPSIDE_DOWN));
            down.setToolTipText(Messages.getString("DicomFieldsView.next")); //$NON-NLS-1$
            down.addActionListener(evt -> next());
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
            if (!searchPostions.isEmpty()) {
                currentSearchIndex = currentSearchIndex <= 0 ? searchPostions.size() - 1 : currentSearchIndex - 1;
                showCurrentSearch(currentSearchPattern);
            }
        }

        private void next() {
            if (!searchPostions.isEmpty()) {
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
                    String patternUp = pattern.toUpperCase();
                    int pos = 0;

                    while ((pos = text.indexOf(patternUp, pos)) >= 0) {
                        if (searchPostions.isEmpty()) {
                            hilite.addHighlight(pos, pos + patternUp.length(), searchHighlightPainter);
                        } else {
                            hilite.addHighlight(pos, pos + patternUp.length(), searchResultHighlightPainter);
                        }
                        searchPostions.add(pos);
                        pos += patternUp.length();
                    }
                } catch (BadLocationException e) {
                    LOGGER.error("Highight result of search", e); //$NON-NLS-1$
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
            if (!searchPostions.isEmpty() && StringUtil.hasText(pattern)) {
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
                    LOGGER.error("Highight result of search", e); //$NON-NLS-1$
                }
            }
        }
    }

    public static class DicomData {

        private final String title;
        private final TagView[] infos;
        private final Level level;

        public DicomData(String title, TagView[] infos, Level level) {
            if (infos == null) {
                throw new IllegalArgumentException();
            }
            this.title = title;
            this.infos = infos;
            this.level = level;
            for (TagView tagView : infos) {
                for (TagW tag : tagView.getTag()) {
                    DicomMediaIO.tagManager.addTag(tag, level);
                }
            }
        }

        public TagView[] getInfos() {
            return infos;
        }

        public String getTitle() {
            return title;
        }

        public Level getLevel() {
            return level;
        }

    }
}
