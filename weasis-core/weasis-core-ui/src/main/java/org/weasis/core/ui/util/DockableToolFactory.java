package org.weasis.core.ui.util;

import java.util.Hashtable;

import org.weasis.core.ui.docking.DockableTool;

public interface DockableToolFactory {

    DockableTool createTool(Hashtable<String, Object> properties);

}
