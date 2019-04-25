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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.JPanel;

import org.weasis.core.api.gui.Insertable;

@SuppressWarnings("serial")
public abstract class AbstractItemDialogPage extends JPanel implements PageProps, Insertable {
    protected static final AtomicInteger keyGenerator = new AtomicInteger(0);
    private final String key;
    private final String title;
    private List<PageProps> subPageList;
    private int pagePosition;

    public AbstractItemDialogPage(String title) {
        this.title = title == null ? "item" : title; //$NON-NLS-1$
        key = String.valueOf(keyGenerator.incrementAndGet());
        this.pagePosition = 1000;
    }

    public void deselectPageAction() {
    }

    public void selectPageAction() {
    }

    @Override
    public String getKey() {
        return key;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void addSubPage(PageProps subPage) {
        if (subPageList == null) {
            subPageList = new ArrayList<>();
        }
        subPageList.add(subPage);
    }

    public void removeSubPage(PageProps subPage) {
        if (subPageList == null) {
            return;
        }
        subPageList.remove(subPage);
    }

    @Override
    public PageProps[] getSubPages() {
        if (subPageList == null) {
            return new PageProps[0];
        }
        final PageProps[] subPages = new PageProps[subPageList.size()];
        subPageList.toArray(subPages);
        return subPages;
    }

    public void resetAllSubPagesToDefaultValues() {
        if (subPageList == null) {
            return;
        }
        for (PageProps subPage : subPageList) {
            subPage.resetoDefaultValues();
        }
    }

    @Override
    public String toString() {
        return title;
    }

    @Override
    public Type getType() {
        return Insertable.Type.PREFERENCES;
    }

    @Override
    public String getComponentName() {
        return title;
    }

    @Override
    public boolean isComponentEnabled() {
        return isEnabled();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
        if (enabled != isComponentEnabled()) {
            setEnabled(enabled);
        }
    }

    @Override
    public int getComponentPosition() {
        return pagePosition;
    }

    @Override
    public void setComponentPosition(int position) {
        this.pagePosition = position;
    }
}
