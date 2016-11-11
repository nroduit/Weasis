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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.swing.ButtonGroup;
import javax.swing.JPanel;

import org.dcm4che3.data.Tag;
import org.weasis.acquire.explorer.AcquireImageInfo;
import org.weasis.acquire.explorer.AcquireManager;
import org.weasis.acquire.explorer.core.bean.Serie;
import org.weasis.acquire.explorer.gui.control.AcquirePublishPanel;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.TagD;

@SuppressWarnings("serial")
public class AcquireTabPanel extends JPanel {

    private final Map<Serie, AcquireCentralImagePanel> btnMap = new HashMap<>();

    private final SerieButtonList serieList;
    private final ButtonGroup btnGrp;

    private AcquireCentralImagePanel imageList;
    private SerieButton selected;

    public AcquireTabPanel() {
        setLayout(new BorderLayout());
        btnGrp = new ButtonGroup();

        serieList = new SerieButtonList();
        imageList = new AcquireCentralImagePanel(this);
        JPanel seriesPanel = new JPanel(new BorderLayout());
        seriesPanel.add(serieList, BorderLayout.CENTER);
        seriesPanel.add(new AcquirePublishPanel(), BorderLayout.SOUTH);

        add(seriesPanel, BorderLayout.WEST);
        add(imageList, BorderLayout.CENTER);
    }

    public void setSelected(SerieButton btn) {
        remove(imageList);
        selected = btn;
        imageList = btnMap.get(selected.getSerie());
        add(imageList, BorderLayout.CENTER);
        imageList.refreshGUI();
    }

    public void updateSerie(Serie serie, List<AcquireImageInfo> images) {
        if (btnMap.containsKey(serie)) {
            // update series list
            btnMap.get(serie).updateList(images);
        } else {
            // Create series list
            AcquireCentralImagePanel tab = new AcquireCentralImagePanel(this, serie, images);
            btnMap.put(serie, tab);

            SerieButton btn = new SerieButton(serie, this);
            btnGrp.add(btn);
            serieList.addButton(btn);

            if (selected == null) {
                btnGrp.setSelected(btn.getModel(), true);
                setSelected(btn);
            }
        }
    }

    public Set<Serie> getSeries() {
        return new TreeSet<>(btnMap.keySet());
    }

    private void removeSerie(Serie serie) {

        Optional.ofNullable(btnMap.remove(serie)).ifPresent(imagePanel -> imagePanel.updateSerie(null));

        serieList.getButton(serie).ifPresent(btnGrp::remove);
        serieList.removeBySerie(serie);
        Optional<SerieButton> nextBtn = serieList.getFirstSerieButton();

        if (nextBtn.isPresent()) {
            btnGrp.setSelected(nextBtn.get().getModel(), true);
            setSelected(nextBtn.get());
        } else if (btnMap.isEmpty()) {
            selected = null;
        }
    }

    public SerieButton getSelected() {
        return selected;
    }

    public void removeImage(ImageElement image) {
        btnMap.entrySet().stream().filter(e -> e.getValue().containsImageElement(image)).findFirst()
            .ifPresent(e -> removeImage(e.getKey(), image));
    }

    public void removeImages(Collection<ImageElement> images) {
        Map<Serie, List<ImageElement>> imagesToRemove = new HashMap<>();

        for (Entry<Serie, AcquireCentralImagePanel> e : btnMap.entrySet()) {
            List<ImageElement> newList = null;
            Iterator<ImageElement> it = images.iterator();
            while (it.hasNext()) {
                ImageElement image = it.next();
                if (e.getValue().containsImageElement(image)) {
                    it.remove();
                    if (newList == null) {
                        newList = new ArrayList<>();
                    }
                    newList.add(image);
                }
            }
            if (newList != null) {
                imagesToRemove.put(e.getKey(), newList);
            }
        }

        imagesToRemove.forEach(this::removeImages);
    }

    private void removeImage(Serie serie, ImageElement image) {
        AcquireCentralImagePanel imagePane = btnMap.get(serie);
        if (Objects.nonNull(imagePane)) {
            imagePane.removeElement(image);
            imagePane.refreshGUI();

            if (imagePane.isEmpty()) {
                removeSerie(serie);
                serieList.refreshGUI();
            }
        }
    }

    private void removeImages(Serie serie, List<ImageElement> images) {
        AcquireCentralImagePanel imagePane = btnMap.get(serie);
        if (Objects.nonNull(imagePane)) {
            imagePane.removeElements(images);
            imagePane.refreshGUI();

            if (imagePane.isEmpty()) {
                removeSerie(serie);
                serieList.refreshGUI();
            }
        }
    }

    public void clearUnusedSeries(List<Serie> usedSeries) {
        List<Serie> seriesToRemove =
            btnMap.keySet().stream().filter(s -> !usedSeries.contains(s)).collect(Collectors.toList());
        seriesToRemove.stream().forEach(this::removeSerie);
        serieList.refreshGUI();
    }

    public void clearAll() {
        Iterator<Entry<Serie, AcquireCentralImagePanel>> iteratorBtnMap = btnMap.entrySet().iterator();
        while (iteratorBtnMap.hasNext()) {
            Entry<Serie, AcquireCentralImagePanel> btnMapEntry = iteratorBtnMap.next();

            Optional.ofNullable(btnMapEntry.getValue()).ifPresent(AcquireCentralImagePanel::clearAll);

            iteratorBtnMap.remove();
            Serie serie = btnMapEntry.getKey();
            serieList.getButton(serie).ifPresent(btnGrp::remove);
            serieList.removeBySerie(serie);

            Optional.ofNullable(btnMapEntry.getValue()).ifPresent(imagePane -> imagePane.updateSerie(null));
        }
        selected = null;
        imageList.refreshGUI();
        serieList.refreshGUI();
    }

    public void moveElements(Serie serie, List<ImageElement> medias) {
        removeImages(selected.getSerie(), medias);

        medias.forEach(m -> AcquireManager.findByImage(m).setSerie(serie));
        updateSerie(serie, AcquireManager.findbySerie(serie));
    }

    public void moveElementsByDate(List<ImageElement> medias) {
        removeImages(selected.getSerie(), medias);

        Set<Serie> series = new HashSet<>();
        medias.forEach(m -> {
            LocalDateTime date = TagD.dateTime(Tag.ContentDate, Tag.ContentTime, m);
            Serie serie = AcquireManager.getSerie(new Serie(date));
            series.add(serie);
            AcquireManager.findByImage(m).setSerie(serie);
        });

        AcquireManager.groupBySeries().forEach((k, v) -> {
            if (series.contains(k)) {
                updateSerie(k, v);
            }
        });
    }
}
