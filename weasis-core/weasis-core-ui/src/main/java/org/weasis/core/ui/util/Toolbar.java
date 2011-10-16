package org.weasis.core.ui.util;

import java.awt.Dimension;

import org.weasis.core.ui.util.WtoolBar.TYPE;

public interface Toolbar {

    public static final Dimension SEPARATOR_2x24 = new Dimension(2, 24);

    TYPE getType();

    String getBarName();

    WtoolBar getComponent();
}