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
import org.weasis.acquire.explorer.core.bean.SeriesGroup;
import org.weasis.acquire.explorer.gui.central.tumbnail.AcquireCentralTumbnailPane;
import org.weasis.base.explorer.list.IThumbnailModel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.TagD;

@SuppressWarnings("serial")
public class AcquireCentralImagePanel extends JPanel implements ListSelectionListener {

    private final AcquireCentralTumbnailPane<ImageElement> imageListPane;
    private AcquireCentralInfoPanel imageInfo;

    public AcquireCentralImagePanel(AcquireTabPanel acquireTabPanel) {
        this(acquireTabPanel, null, new ArrayList<AcquireImageInfo>());
    }

    public AcquireCentralImagePanel(AcquireTabPanel acquireTabPanel, SeriesGroup seriesGroup,
        List<AcquireImageInfo> imageInfos) {
        setLayout(new BorderLayout());

        imageInfo = new AcquireCentralInfoPanel(seriesGroup);

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

    public void addImagesInfo(List<AcquireImageInfo> imageInfos) {
        imageListPane.addElements(toImageElement(imageInfos));
    }

    public void updateList(List<AcquireImageInfo> imageInfos) {
        imageListPane.setList(toImageElement(imageInfos));
    }

    public void updateSerie(SeriesGroup newSerie) {
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
        imageListPane.revalidate();
        imageListPane.repaint();
        imageInfo.refreshGUI();
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
