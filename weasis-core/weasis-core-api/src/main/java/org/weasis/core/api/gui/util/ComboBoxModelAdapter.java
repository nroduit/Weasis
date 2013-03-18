package org.weasis.core.api.gui.util;

import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataListener;

public interface ComboBoxModelAdapter extends ListDataListener, State {

    void setModel(ComboBoxModel dataModel);
}