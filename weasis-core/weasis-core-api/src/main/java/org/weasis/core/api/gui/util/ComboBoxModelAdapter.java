package org.weasis.core.api.gui.util;

import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataListener;

public interface ComboBoxModelAdapter extends ListDataListener {

    void setModel(ComboBoxModel dataModel);

    void setEnabled(boolean enabled);

}