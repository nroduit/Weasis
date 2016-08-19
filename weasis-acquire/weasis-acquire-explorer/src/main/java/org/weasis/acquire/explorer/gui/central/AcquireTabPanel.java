package org.weasis.acquire.explorer.gui.central;

import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import org.weasis.acquire.explorer.gui.central.component.SerieButton;
import org.weasis.acquire.explorer.gui.central.component.SerieButtonList;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.dicom.codec.TagD;

@SuppressWarnings("serial")
public class AcquireTabPanel extends JPanel {

    private final Map<Serie, AcquireCentralImagePanel> btnMap = new HashMap<>();

    private final SerieButtonList serieList;
    private final ButtonGroup btnGrp = new ButtonGroup();

    private AcquireCentralImagePanel imageList;
    private SerieButton selected;

    public AcquireTabPanel() {
        setLayout(new BorderLayout());
        serieList = new SerieButtonList();
        imageList = new AcquireCentralImagePanel(this);

        add(serieList, BorderLayout.WEST);
        add(imageList, BorderLayout.CENTER);
    }

    public void setSelected(SerieButton btn) {
        remove(imageList);
        selected = btn;
        imageList = btnMap.get(selected.getSerie());
        add(imageList, BorderLayout.CENTER);
        imageList.refreshSerieMeta();
        imageList.revalidate();
        imageList.repaint();
    }

    public void updateSerie(Serie serie, List<AcquireImageInfo> images) {
        if (btnMap.containsKey(serie)) {
            // update serie list
            btnMap.get(serie).updateList(images);
        } else {
            // Create serie list
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

    private void remove(Serie s) {
        btnMap.remove(s);
        Optional<SerieButton> nextBtn = serieList.removeBySerie(s);
        if(nextBtn.isPresent()){
            btnGrp.setSelected(nextBtn.get().getModel(), true);
            setSelected(nextBtn.get());          
        }
        else if(btnMap.isEmpty()) {
            selected = null;
        }
    }

    public SerieButton getSelected() {
        return selected;
    }

    public void clearUnusedSeries(List<Serie> usedSeries) {
        List<Serie> seriesToRemove =
            btnMap.keySet().stream().filter(s -> !usedSeries.contains(s)).collect(Collectors.toList());
        seriesToRemove.stream().forEach(this::remove);
        serieList.revalidate();
        serieList.repaint();
    }

    public void removeElements(List<ImageElement> medias) {
        AcquireCentralImagePanel currentPane = btnMap.get(selected.getSerie());
        removeElements(currentPane, medias);
    }

    public void removeElements(AcquireCentralImagePanel currentPane, List<ImageElement> medias) {
        currentPane.removeElements(medias);
        currentPane.revalidate();
        currentPane.repaint();

        if (currentPane.isEmpty()) {
            remove(selected.getSerie());
            serieList.revalidate();
            serieList.repaint();
        }
    }

    public void moveElements(Serie serie, List<ImageElement> medias) {
        AcquireCentralImagePanel currentPane = btnMap.get(selected.getSerie());
        removeElements(currentPane, medias);

        medias.forEach(m -> AcquireManager.findByImage(m).setSerie(serie));
        updateSerie(serie, AcquireManager.findbySerie(serie));
    }

    public void moveElementsByDate(List<ImageElement> medias) {
        AcquireCentralImagePanel currentPane = btnMap.get(selected.getSerie());
        removeElements(currentPane, medias);

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
