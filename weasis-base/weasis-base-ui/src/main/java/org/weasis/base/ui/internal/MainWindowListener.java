package org.weasis.base.ui.internal;

import java.beans.PropertyChangeListener;

import org.weasis.base.ui.gui.WeasisWin;

public interface MainWindowListener extends PropertyChangeListener {

    void setMainWindow(WeasisWin mainWindow);
}
