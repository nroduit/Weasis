/*******************************************************************************
 * Copyright (c) 2010 Nicolas Roduit.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 ******************************************************************************/
package org.weasis.core.ui.graphic.model;

import java.util.ArrayList;

import org.weasis.core.ui.Messages;

/**
 * The Enum Tools.
 * 
 * @author Nicolas Roduit
 */
public enum Tools {
    CROSSLINES(0, Messages.getString("Tools.cross"), true, ""), //$NON-NLS-1$ //$NON-NLS-2$

    NOTE(1, Messages.getString("Tools.Anno"), true, "note_pinned.png"), //$NON-NLS-1$ //$NON-NLS-2$

    MEASURE(2, Messages.getString("Tools.meas"), true, "measure1D.png"), //$NON-NLS-1$ //$NON-NLS-2$

    OBJECTEXTRACT(4, Messages.getString("Tools.seg"), true, "objectExtract.png"), //$NON-NLS-1$ //$NON-NLS-2$

    CALIBRATION(8, Messages.getString("Tools.calib"), false, "calibration.png"), //$NON-NLS-1$ //$NON-NLS-2$

    INFOLAYER(12, "", true, ""), //$NON-NLS-1$ //$NON-NLS-2$

    TEMPCLASSIFLAYER(10, "", true, ""), //$NON-NLS-1$ //$NON-NLS-2$

    TEMPDRAGLAYER(11, Messages.getString("Tools.deco"), true, ""); //$NON-NLS-1$ //$NON-NLS-2$

    // keep TempLayer in last position
    private final int id;
    private final String title;
    private final boolean layer;
    private final String imgName;

    Tools(int id, String title, boolean islayer, String imgName) {
        this.id = id;
        this.title = title;
        this.layer = islayer;
        this.imgName = imgName;
    }

    public int getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String toString() {
        return title;
    }

    public boolean isLayer() {
        return layer;
    }

    public String getImgName() {
        return imgName;
    }

    public static Tools getCurrentTools(int toolPosition) {
        for (Tools t : Tools.values()) {
            if (toolPosition == t.ordinal()) {
                return t;
            }
        }
        return Tools.TEMPDRAGLAYER;
    }

    public static void setLevelToLayers(ArrayList<AbstractLayer> layers) {
        if (layers != null) {
            for (int j = layers.size() - 1; j >= 0; j--) {
                AbstractLayer layer = layers.get(j);
                switch (layer.getDrawType()) {
                    case 0:
                        layer.setLevel(0);
                        break;
                    case 1:
                        layer.setLevel(1);
                        break;
                    case 2:
                        layer.setLevel(2);
                        break;
                    case 3:
                        layer.setLevel(3);
                        break;
                    case 4:
                        layer.setLevel(4);
                        break;
                    case 10:
                        layer.setLevel(10);
                        break;
                    case 11:
                        layer.setLevel(11);
                        break;
                    default:
                        layer.setLevel(12);
                        break;
                }
            }
        }
    }

    public static String getToolName(int id) {
        for (Tools t : Tools.values()) {
            if (id == t.id) {
                return t.title;
            }
        }
        return ""; //$NON-NLS-1$
    }

    public static final ArrayList<String> getToolsName() {
        ArrayList<String> list = new ArrayList<String>();
        // ne pas utiliser la Classe EnumSet et un range, problème de casting après l'obfuscation
        // EnumSet<Tools> toolsEnum = EnumSet.range(G_DESCRIPTION, VPROFILE);
        for (Tools t : Tools.values()) {
            if (!t.equals(TEMPDRAGLAYER) && !t.equals(TEMPCLASSIFLAYER)) {
                list.add(t.getTitle());
            }
        }
        return list;
    }

    public static final ArrayList<Tools> getToolsToDisplay() {
        ArrayList<Tools> list = new ArrayList<Tools>();
        for (Tools t : Tools.values()) {
            if (!t.equals(TEMPDRAGLAYER) && !t.equals(TEMPCLASSIFLAYER)) {
                list.add(t);
            }
        }
        return list;
    }
}
