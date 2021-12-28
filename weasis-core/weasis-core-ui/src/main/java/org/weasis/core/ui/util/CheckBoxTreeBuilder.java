package org.weasis.core.ui.util;

import it.cnr.imaa.essi.lablib.gui.checkboxtree.DefaultCheckboxTreeCellRenderer;

public class CheckBoxTreeBuilder {

  public static DefaultCheckboxTreeCellRenderer buildNoIconCheckboxTreeCellRenderer() {
    DefaultCheckboxTreeCellRenderer renderer = new DefaultCheckboxTreeCellRenderer();
    renderer.setOpenIcon(null);
    renderer.setClosedIcon(null);
    renderer.setLeafIcon(null);
    return renderer;
  }
}
