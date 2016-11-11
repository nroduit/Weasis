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
package org.weasis.acquire.explorer.gui.central;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.central.tumbnail.AcquireCentralTumbnailPane;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.TagD;

public class AcquireCentralImagePanel extends JPanel implements ListSelectionListener {
    private static final long serialVersionUID = 1270219114006046523L;

    private final AcquireCentralTumbnailPane<ImageElement> imageListPane;
    private AcquireCentralInfoPanel imageInfo;

    public AcquireCentralImagePanel(AcquireTabPanel acquireTabPanel) {
        this(acquireTabPanel, null, new ArrayList<AcquireImageInfo>());
    }

    public AcquireCentralImagePanel(AcquireTabPanel acquireTabPanel, Serie serie, List<AcquireImageInfo> imageInfos) {
        setLayout(new BorderLayout());

        imageInfo = new AcquireCentralInfoPanel(serie);

        imageListPane = new AcquireCentralTumbnailPane<>(toImageElement(imageInfos));
        imageListPane.setAcquireTabPanel(acquireTabPanel);
        imageListPane.addListSelectionListener(this);

        add(imageListPane, BorderLayout.CENTER);
        add(imageInfo, BorderLayout.SOUTH);
    }

    private List<ImageElement> toImageElement(List<AcquireImageInfo> list) {
        return list.stream().map(e -> e.getImage())
            .sorted(Comparator.comparing(i -> TagD.dateTime(Tag.ContentDate, Tag.ContentTime, i)))
            .collect(Collectors.toList());

    }

    public void updateList(List<AcquireImageInfo> imageInfos) {
        imageListPane.setList(toImageElement(imageInfos));
    }

    public void updateSerie(Serie newSerie) {
        imageInfo.setSerie(newSerie);
    }

    public IThumbnailModel<ImageElement> getFileListModel() {
        return imageListPane.getFileListModel();
    }

    public boolean containsImageElement(ImageElement image) {
        return getFileListModel().contains(image);
    }

    public void removeElement(ImageElement image) {
        getFileListModel().removeElement(image);
    }

    public void removeElements(List<ImageElement> medias) {
        IThumbnailModel<ImageElement> model = getFileListModel();
        medias.forEach(model::removeElement);
    }

    public void clearAll() {
        getFileListModel().clear();
    }

    public boolean isEmpty() {
        return getFileListModel().isEmpty();
    }

    protected void refreshGUI() {
        imageInfo.refreshGUI();
        revalidate();
        repaint();
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        List<ImageElement> images = imageListPane.getSelectedValuesList();
        if (images.size() == 1) {
            imageInfo.setImage(images.get(0));
        } else {
            imageInfo.setImage(null);
        }
    }
}
