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

import java.awt.Dimension;
import java.util.Iterator;

import javax.media.jai.PlanarImage;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JScrollPane;
import javax.swing.border.EmptyBorder;

import org.dcm4che2.data.DicomElement;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.VR;
import org.dcm4che2.util.TagUtils;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.ui.editor.SeriesViewerEvent;
import org.weasis.core.ui.editor.SeriesViewerListener;
import org.weasis.core.ui.editor.SeriesViewerEvent.EVENT;
import org.weasis.dicom.codec.DicomImageElement;
import org.weasis.dicom.codec.DicomMediaIO;

public class DicomFieldsView extends JScrollPane implements SeriesViewerListener {

    private static final ThreadLocal<char[]> cbuf = new ThreadLocal<char[]>() {

        @Override
        protected char[] initialValue() {
            return new char[96];
        }
    };

    public DicomFieldsView() {
        setPreferredSize(new Dimension(1024, 1024));
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
            DefaultListModel listModel = new DefaultListModel();
            if (event.getMediaElement() instanceof DicomImageElement) {
                MediaReader<PlanarImage> loader = ((DicomImageElement) event.getMediaElement()).getMediaReader();
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
            setViewportView(jListElement);
        }
    }
}
