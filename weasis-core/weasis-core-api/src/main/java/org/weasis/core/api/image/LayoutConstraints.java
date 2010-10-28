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
package org.weasis.core.api.image;

import java.awt.GridBagConstraints;
import java.awt.Insets;

public class LayoutConstraints extends GridBagConstraints implements Comparable<LayoutConstraints> {

    private String type;
    private int layoutID;

    public LayoutConstraints(String type, int layoutID, int gridx, int gridy, int gridwidth, int gridheight,
        double weightx, double weighty, int anchor, int fill) {
        super(gridx, gridy, gridwidth, gridheight, weightx, weighty, anchor, fill, new Insets(0, 0, 0, 0), 0, 0);
        this.type = type;
        this.layoutID = layoutID;
    }

    public String getType() {
        return type;
    }

    public int getLayoutID() {
        return layoutID;
    }

    public int compareTo(LayoutConstraints o) {
        return (layoutID < o.layoutID ? -1 : (layoutID == o.layoutID ? 0 : 1));
    }

}
