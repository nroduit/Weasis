/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.tool;

import javax.swing.ImageIcon;
import javax.swing.JButton;

import org.weasis.core.api.explorer.model.DataExplorerModel;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.editor.ViewerPluginBuilder;
import org.weasis.core.ui.util.WtoolBar;
import org.weasis.dicom.explorer.DicomModel;
import org.weasis.dicom.viewer2d.EventManager;

import br.com.animati.texture.mpr3dview.View3DFactory;
import br.com.animati.texture.mpr3dview.internal.Messages;

/**
 *
 * @author Gabriela Carla Bauerman (gabriela@animati.com.br)
 * @version 2015, 14 May.
 */
public class CallTextureToolbar extends WtoolBar {

    public static final String NAME = Messages.getString("TextureToolbar.name");

    public CallTextureToolbar(int position) {
        super(NAME, position);

        JButton open = new JButton(new ImageIcon(CallTextureToolbar.class.getResource("/icon/32x32/3Dplugin.png")));
        open.setToolTipText(Messages.getString("CallTextureToolbar.open"));
        open.addActionListener(e -> {
            EventManager eventManager = EventManager.getInstance();
            MediaSeries<org.weasis.dicom.codec.DicomImageElement> s = eventManager.getSelectedSeries();
            if (s != null && s.size(null) >= 5) {
                DataExplorerModel model = (DataExplorerModel) s.getTagValue(TagW.ExplorerModel);
                if (model instanceof DicomModel) {
                    ViewerPluginBuilder.openSequenceInPlugin(new View3DFactory(), s, model, false, false);
                }
            }
        });

        add(open);
    }

}
