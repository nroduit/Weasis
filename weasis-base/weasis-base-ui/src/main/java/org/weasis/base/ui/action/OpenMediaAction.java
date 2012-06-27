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
package org.weasis.base.ui.action;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;

import javax.swing.Action;
import javax.swing.JFileChooser;
import javax.swing.KeyStroke;

import org.weasis.base.ui.gui.WeasisWin;
import org.weasis.base.ui.internal.Activator;
import org.weasis.core.api.gui.util.FileFormatFilter;
import org.weasis.core.api.media.MimeInspector;
import org.weasis.core.api.media.data.Codec;
import org.weasis.core.api.media.data.MediaReader;
import org.weasis.core.api.service.BundleTools;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.AbstractUIAction;

public class OpenMediaAction extends AbstractUIAction {

    /** The singleton instance of this singleton class. */
    private static OpenMediaAction openAction = null;

    /** Return the singleton instance */
    public static OpenMediaAction getInstance() {
        if (openAction == null) {
            openAction = new OpenMediaAction();
        }
        return openAction;
    }

    private OpenMediaAction() {
        super("Open Image...");
        setDescription("Open an supported image file");
        putValue(Action.ACCELERATOR_KEY, KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_MASK));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String directory = Activator.LOCAL_PERSISTENCE.getProperty("last.open.image.dir", "");//$NON-NLS-1$
        JFileChooser fileChooser = new JFileChooser(directory);

        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        fileChooser.setMultiSelectionEnabled(false);
        // TODO add format from plugins
        FileFormatFilter.setImageDecodeFilters(fileChooser);
        File selectedFile = null;
        if (fileChooser.showOpenDialog(WeasisWin.getInstance()) != JFileChooser.APPROVE_OPTION
            || (selectedFile = fileChooser.getSelectedFile()) == null) {
            return;
        } else {
            String lastDir = selectedFile.getPath();

            if (selectedFile != null && selectedFile.canRead()) {
                String mimeType = MimeInspector.getMimeType(selectedFile);
                // TODO add message when cannot open image
                if (mimeType != null && mimeType.startsWith("image")) {
                    Codec codec = BundleTools.getCodec(mimeType, null);
                    if (codec != null) {
                        MediaReader reader = codec.getMediaIO(selectedFile.toURI(), mimeType, null);
                        if (reader != null) {
                            ViewerPluginBuilder
                                .openSequenceInDefaultPlugin(reader.getMediaSeries(), null, false, false);
                        }
                    }
                }
            }
            if (lastDir != null) {
                Activator.LOCAL_PERSISTENCE.setProperty("last.open.image.dir", lastDir);
            }
        }
    }
}
