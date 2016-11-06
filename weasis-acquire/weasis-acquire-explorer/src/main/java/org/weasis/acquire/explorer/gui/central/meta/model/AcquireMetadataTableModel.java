/*******************************************************************************
 * Copyright (c) 2016 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.acquire.explorer.gui.central.meta.model;

import java.util.Arrays;
import java.util.Optional;

import javax.swing.table.AbstractTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.acquire.explorer.Messages;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.Tagable;

public abstract class AcquireMetadataTableModel extends AbstractTableModel {
    private static final long serialVersionUID = -2336248192936430413L;

    protected final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    private static final TagW[] NO_TAGS = {};
    protected String[] headers = { Messages.getString("AcquireMetadataTableModel.tag"), Messages.getString("AcquireMetadataTableModel.val") }; //$NON-NLS-1$ //$NON-NLS-2$
    protected Optional<Tagable> tagable;

    public AcquireMetadataTableModel(Tagable tagable) {
        this.tagable = Optional.ofNullable(tagable);
    }

    protected abstract Optional<TagW[]> tagsToDisplay();

    protected Optional<TagW[]> tagsEditable() {
        return Optional.of(NO_TAGS);
    }

    @Override
    public int getRowCount() {
        return tagsToDisplay().orElse(NO_TAGS).length;
    }

    @Override
    public int getColumnCount() {
        return headers.length;
    }

    @Override
    public String getColumnName(int column) {
        return headers[column];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        TagW tag = tagsToDisplay().orElse(NO_TAGS)[rowIndex];
        switch (columnIndex) {
            case 0:
                return tag;
            case 1:
                if (tagable.isPresent()) {
                    return tagable.get().getTagValue(tag);
                } else {
                    return null;
                }
        }

        return null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        TagW tag = tagsToDisplay().orElse(NO_TAGS)[rowIndex];
        switch (columnIndex) {
            case 1:
                boolean isValueEditable =
                    Arrays.stream(tagsEditable().orElse(NO_TAGS)).filter(t -> t.equals(tag)).findFirst().isPresent();
                return isValueEditable;
        }
        return false;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 1) {
            tagsToDisplay().ifPresent(list -> {
                TagW tag = list[rowIndex];
                tagable.ifPresent(t -> t.setTagNoNull(tag, aValue));
            });
        }
    }
}
