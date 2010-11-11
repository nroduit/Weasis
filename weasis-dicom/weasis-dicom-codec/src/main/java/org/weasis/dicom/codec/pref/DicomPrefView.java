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
package org.weasis.dicom.codec.pref;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

import org.dcm4che2.imageio.ImageReaderFactory;
import org.weasis.core.api.gui.util.AbstractItemDialogPage;

public class DicomPrefView extends AbstractItemDialogPage {

    private final JPanel panel = new JPanel();
    private final JComboBox comboBox;

    public DicomPrefView() {
        setTitle("DICOM"); //$NON-NLS-1$
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        add(panel);
        GridBagLayout gbl_panel = new GridBagLayout();
        gbl_panel.columnWidths = new int[] { 0, 0, 0, 0 };
        gbl_panel.rowHeights = new int[] { 0, 0, 0, 0 };
        gbl_panel.columnWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        gbl_panel.rowWeights = new double[] { 0.0, 0.0, 0.0, Double.MIN_VALUE };
        panel.setLayout(gbl_panel);
        panel.setBorder(new TitledBorder(null, "Image Reader", TitledBorder.LEADING, TitledBorder.TOP, null, null)); //$NON-NLS-1$

        final JLabel j2kLabel = new JLabel("JPEG2000:"); //$NON-NLS-1$
        GridBagConstraints gbc_j2kLabel = new GridBagConstraints();
        gbc_j2kLabel.insets = new Insets(0, 0, 5, 5);
        gbc_j2kLabel.anchor = GridBagConstraints.WEST;
        gbc_j2kLabel.gridx = 0;
        gbc_j2kLabel.gridy = 0;
        panel.add(j2kLabel, gbc_j2kLabel);

        comboBox = new JComboBox(getJpeg2000ReaderClassName().toArray());
        GridBagConstraints gbc_comboBox = new GridBagConstraints();
        gbc_comboBox.anchor = GridBagConstraints.WEST;
        gbc_comboBox.insets = new Insets(0, 2, 5, 5);
        gbc_comboBox.gridx = 1;
        gbc_comboBox.gridy = 0;
        panel.add(comboBox, gbc_comboBox);

        GridBagConstraints gbc_lblstrut = new GridBagConstraints();
        gbc_lblstrut.insets = new Insets(0, 0, 5, 0);
        gbc_lblstrut.anchor = GridBagConstraints.WEST;
        gbc_lblstrut.fill = GridBagConstraints.HORIZONTAL;
        gbc_lblstrut.weightx = 1.0;
        gbc_lblstrut.gridx = 2;
        gbc_lblstrut.gridy = 0;
        panel.add(Box.createHorizontalStrut(2), gbc_lblstrut);

        String j2kReader = DicomPrefManager.getInstance().getJ2kReader();
        if (j2kReader != null) {
            for (int i = 0; i < comboBox.getItemCount(); i++) {
                Decoder dec = (Decoder) comboBox.getItemAt(i);
                if (j2kReader.equals(dec.getReader())) {
                    comboBox.setSelectedItem(dec);
                    break;
                }
            }
        }
    }

    @Override
    public void closeAdditionalWindow() {
        Decoder reader = (Decoder) comboBox.getSelectedItem();
        if (reader != null) {
            DicomPrefManager.getInstance().setJ2kReader(reader.getReader());
        }
    }

    @Override
    public void resetoDefaultValues() {
        // TODO Auto-generated method stub

    }

    public List<Decoder> getJpeg2000ReaderClassName() {
        ImageReaderFactory factory = ImageReaderFactory.getInstance();
        String decoders[] = factory.getProperty("jpeg2000").split(",");
        String decoderNames[] = factory.getProperty("jpeg2000.title").split(",");
        ArrayList<Decoder> list = new ArrayList<Decoder>();
        if (decoders.length == decoderNames.length) {
            for (int i = 0; i < decoders.length; i++) {
                for (Iterator it = ImageIO.getImageReadersByFormatName("jpeg2000"); it.hasNext();) {
                    ImageReader r = (ImageReader) it.next();
                    if (decoders[i].equals(r.getClass().getName())) {
                        list.add(new Decoder(decoderNames[i], decoders[i]));
                    }
                }
            }
        }
        return list;
    }

    static class Decoder {
        private final String name;
        private final String reader;

        public Decoder(String name, String reader) {
            super();
            this.name = name;
            this.reader = reader;
        }

        public String getName() {
            return name;
        }

        public String getReader() {
            return reader;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
