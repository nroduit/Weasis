package org.weasis.acquire.explorer.gui.model.actions;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;
import javax.swing.Action;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.AcquisitionView;
import org.weasis.acquire.explorer.util.ImageFileHelper;

public class ChangePathSelectionAction extends AbstractAction {
    private static final long serialVersionUID = -65145837841144613L;
    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private final AcquisitionView mainView;

    public ChangePathSelectionAction(AcquisitionView acquisitionView) {
        this.mainView = acquisitionView;

        putValue(Action.NAME, " ... ");
        putValue(Action.ACTION_COMMAND_KEY, "onChangeRootPath");
        putValue(Action.SHORT_DESCRIPTION, "Sélection répertoire ");
    }

    @Override
    public void actionPerformed(ActionEvent e) {

        String newRootPath;
        newRootPath = ImageFileHelper.openImageFileChooser(mainView.getSystemDrive().getID());
        if (newRootPath != null) {
            try {
                mainView.applyNewPath(newRootPath);
            } catch (Exception ex) {
                LOGGER.warn(ex.getMessage(), ex);
            }
        }
    }
}
