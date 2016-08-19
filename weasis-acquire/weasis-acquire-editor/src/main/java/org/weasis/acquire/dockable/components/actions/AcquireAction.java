package org.weasis.acquire.dockable.components.actions;

import java.awt.event.ActionListener;

import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.ui.editor.image.ViewCanvas;

public interface AcquireAction extends ActionListener {
    public enum Cmd {
        INIT, VALIDATE, CANCEL, RESET
    }

    AcquireActionPanel getCentralPanel();

    void init();

    void validate(AcquireImageInfo imageInfo, ViewCanvas<ImageElement> view);

    void validate();

    boolean cancel();

    boolean reset();
}
