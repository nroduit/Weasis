/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.gui.util;

public interface PageProps {

    void resetoDefaultValues();

    /**
     * Returns the key used to identify this page in a multiple page config dialog.
     *
     * @return the key, must not be <code>null</code> and unique within the page.
     */
    String getKey();

    /**
     * Returns the human readable tile of the page which is displayed in the config dialog's tree view and title bar.
     *
     * @return the title, must not be <code>null</code>
     */
    String getTitle();

    /**
     * Returns an array of sub-pages this page has. Subpages are displayed in the config dialog's tree view as children
     * of this page.
     *
     * @return an array of sub-pages or <code>null</code> if no sub-pages are used
     */
    PageProps[] getSubPages();

    void closeAdditionalWindow();
}
