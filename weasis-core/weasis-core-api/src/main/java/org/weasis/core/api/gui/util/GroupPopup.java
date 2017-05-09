package org.weasis.core.api.gui.util;

import javax.swing.JMenu;
import javax.swing.JPopupMenu;

public interface GroupPopup {

    JPopupMenu createJPopupMenu();

    JMenu createMenu(String title);

}