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

        final JLabel lblTranscodingTo = new JLabel("JPEG2000 decoder:"); //$NON-NLS-1$
        GridBagConstraints gbc_lblTranscodingTo = new GridBagConstraints();
        gbc_lblTranscodingTo.insets = new Insets(0, 0, 5, 5);
        gbc_lblTranscodingTo.anchor = GridBagConstraints.WEST;
        gbc_lblTranscodingTo.gridx = 0;
        gbc_lblTranscodingTo.gridy = 0;
        panel.add(lblTranscodingTo, gbc_lblTranscodingTo);

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

    }

    @Override
    public void closeAdditionalWindow() {
        String reader = (String) comboBox.getSelectedItem();
        if (reader != null) {
            // DicomManager.getInstance().setWadoTSUID(tsuid);
        }
    }

    @Override
    public void resetoDefaultValues() {
        // TODO Auto-generated method stub

    }

    public List<String> getJpeg2000ReaderClassName() {
        ArrayList<String> list = new ArrayList<String>();
        for (Iterator it = ImageIO.getImageReadersByFormatName("jpeg2000"); it.hasNext();) {
            ImageReader r = (ImageReader) it.next();
            list.add(r.getClass().getName());
        }
        return list;
    }
}
