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
package org.weasis.core.api.gui.util;

import java.util.ArrayList;

import javax.swing.JPanel;

/**
 * <p>
 * Title: PetroSpector
 * </p>
 * <p>
 * Description: Thin sections analysis
 * </p>
 * <p>
 * Copyright: Copyright (c) 2002
 * </p>
 * <p>
 * Company:
 * </p>
 * 
 * @author non attribuable
 * @version 1.0
 */

public abstract class AbstractItemDialogPage extends JPanel implements PageProps {

    private static int _lastKey;
    private final String _key;
    private String _title;
    private java.util.List<PageProps> _subPageList;

    public AbstractItemDialogPage() {
        _key = getClass().getName().concat(String.valueOf(_lastKey++));
    }

    public abstract void resetoDefaultValues();

    public abstract void closeAdditionalWindow();

    public void deselectPageAction() {
    }

    public void selectPageAction() {
    }

    public String getKey() {
        return _key;
    }

    public String getTitle() {
        return _title;
    }

    public void setTitle(String title) {
        _title = title;
    }

    public void addSubPage(PageProps subPage) {
        if (_subPageList == null) {
            _subPageList = new ArrayList<PageProps>();
        }
        _subPageList.add(subPage);
    }

    public void removeSubPage(PageProps subPage) {
        if (_subPageList == null) {
            return;
        }
        _subPageList.remove(subPage);
    }

    public PageProps[] getSubPages() {
        if (_subPageList == null) {
            return null;
        }
        final PageProps[] subPages = new PageProps[_subPageList.size()];
        _subPageList.toArray(subPages);
        return subPages;
    }

    public void resetAllSubPagesToDefaultValues() {
        if (_subPageList == null) {
            return;
        }
        for (PageProps subPage : _subPageList) {
            subPage.resetoDefaultValues();
        }
    }

    @Override
    public String toString() {
        return _title;
    }

}
