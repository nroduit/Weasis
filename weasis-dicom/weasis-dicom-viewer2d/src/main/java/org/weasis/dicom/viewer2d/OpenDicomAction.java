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

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.io.File;
import java.util.ArrayList;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.AbstractUIAction;
import org.weasis.dicom.codec.DicomCodec;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;

public class OpenDicomAction extends AbstractUIAction {

    /** The singleton instance of this singleton class. */
    private static OpenDicomAction openAction = null;

    /** Return the singleton instance */
    public static OpenDicomAction getInstance() {
        if (openAction == null) {
            openAction = new OpenDicomAction();
        }
        return openAction;
    }

    private OpenDicomAction() {
        super(Messages.getString("OpenDicomAction.title")); //$NON-NLS-1$
        setDescription(Messages.getString("OpenDicomAction.desc")); //$NON-NLS-1$
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String directory = BundleTools.LOCAL_PERSISTENCE.getProperty("last.open.dicom.dir", "");//$NON-NLS-1$ //$NON-NLS-2$
        JFileChooser fileChooser = new JFileChooser(directory);

        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(true);
        FileFormatFilter filter = new FileFormatFilter(new String[] { "dcm", "dicm" }, "DICOM"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
        fileChooser.addChoosableFileFilter(filter);
        fileChooser.setAcceptAllFileFilterUsed(true);
        fileChooser.setFileFilter(filter);

        File[] selectedFiles;
        if (fileChooser
            .showOpenDialog(EventManager.getInstance().getSelectedView2dContainer()) != JFileChooser.APPROVE_OPTION
            || (selectedFiles = fileChooser.getSelectedFiles()) == null) {
            return;
        } else {
            Codec codec = BundleTools.getCodec(DicomMediaIO.MIMETYPE, DicomCodec.NAME);
            if (codec != null) {
                ArrayList<MediaSeries<? extends MediaElement<?>>> list = new ArrayList<>();
                for (File file : selectedFiles) {
                    if (MimeInspector.isMatchingMimeTypeFromMagicNumber(file, DicomMediaIO.MIMETYPE)) {
                        MediaReader reader = codec.getMediaIO(file.toURI(), DicomMediaIO.MIMETYPE, null);
                        if (reader != null) {
                            if (reader.getMediaElement() == null) {
                                // DICOM is not readable
                                continue;
                            }
                            String sUID = TagD.getTagValue(reader, Tag.SeriesInstanceUID, String.class);
                            String gUID = TagD.getTagValue(reader, Tag.PatientID, String.class);
                            TagW tname = TagD.get(Tag.PatientName);
                            String tvalue = (String) reader.getTagValue(tname);

                            MediaSeries s =
                                ViewerPluginBuilder.buildMediaSeriesWithDefaultModel(reader, gUID, tname, tvalue, sUID);

                            if (s != null && !list.contains(s)) {
                                list.add(s);
                            }
                        }
                    }
                }
                if (!list.isEmpty()) {
                    ViewerPluginBuilder.openSequenceInDefaultPlugin(list, ViewerPluginBuilder.DefaultDataModel, true,
                        true);
                } else {
                    Component c = e.getSource() instanceof Component ? (Component) e.getSource() : null;
                    JOptionPane.showMessageDialog(c, Messages.getString("OpenDicomAction.open_err_msg"), //$NON-NLS-1$
                        getDescription(), JOptionPane.WARNING_MESSAGE);
                }
            }
            BundleTools.LOCAL_PERSISTENCE.setProperty("last.open.dicom.dir", selectedFiles[0].getParent()); //$NON-NLS-1$
        }
    }
}
