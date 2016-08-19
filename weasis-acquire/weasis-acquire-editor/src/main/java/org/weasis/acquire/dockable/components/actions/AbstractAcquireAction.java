package org.weasis.acquire.dockable.components.actions;

import java.awt.Component;
import java.awt.event.ActionEvent;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.AcquireObject;
import org.weasis.acquire.dockable.components.AcquireActionButton;
import org.weasis.acquire.dockable.components.AcquireActionButtonsPanel;
import org.weasis.acquire.explorer.AcquireImageInfo;

/**
 * 
 * @author Yannick LARVOR
 * @version 2.5.0
 * @since 2.5.0 - 2016-04-08 - ylar - Creation
 * 
 */
public abstract class AbstractAcquireAction extends AcquireObject implements AcquireAction {
    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    protected final AcquireActionPanel centralPanel;

    protected final AcquireActionButtonsPanel panel;

    public AbstractAcquireAction(AcquireActionButtonsPanel panel) {
        this.panel = panel;
        this.centralPanel = newCentralPanel();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Cmd cmd = Cmd.valueOf(e.getActionCommand());
        switch (cmd) {
            case INIT:
                panel.setSelected((AcquireActionButton) e.getSource());
                init();
                break;
            case VALIDATE:
                validate();
                break;
            case CANCEL:
                cancel();
                break;
            case RESET:
                reset();
                break;
            default:
                LOGGER.warn("Unkown command : " + e.getActionCommand());
                break;
        }
    }

    @Override
    public void init() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.clearPreProcess();
        imageInfo.applyPostProcess(getView());
        imageInfo.applyPreProcess(getView());
    }

    @Override
    public boolean cancel() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        boolean dirty = imageInfo.isDirty();

        if (dirty) {
            imageInfo.clearPreProcess();
            centralPanel.initValues(imageInfo, imageInfo.getCurrentValues());
        }
        return dirty;
    }

    @Override
    public boolean reset() {
        AcquireImageInfo imageInfo = getImageInfo();
        imageInfo.removeLayer(getView());
        boolean dirty = imageInfo.isDirtyFromDefault();

        if (dirty) {
            int confirm = JOptionPane.showConfirmDialog((Component) centralPanel, "Are you sure you want to reset ?",
                "RESET", JOptionPane.YES_NO_OPTION);
            if (confirm == 0) {
                centralPanel.initValues(imageInfo, imageInfo.getDefaultValues());
                init();
            }
        }
        return dirty;
    }

    @Override
    public AcquireActionPanel getCentralPanel() {
        return centralPanel;
    }

    public abstract AcquireActionPanel newCentralPanel();
}
