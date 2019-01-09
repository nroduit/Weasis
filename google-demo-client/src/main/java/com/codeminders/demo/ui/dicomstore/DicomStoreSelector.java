package com.codeminders.demo.ui.dicomstore;

import com.codeminders.demo.*;
import com.codeminders.demo.model.Dataset;
import com.codeminders.demo.model.DicomStore;
import com.codeminders.demo.model.Location;
import com.codeminders.demo.model.ProjectDescriptor;
import com.codeminders.demo.ui.StudiesTable;
import com.codeminders.demo.ui.StudyView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Collections.emptyList;

public class DicomStoreSelector extends JPanel {

    private static final Logger log = LoggerFactory.getLogger(DicomStoreSelector.class);

    private final GoogleAPIClient googleAPIClient;

    private final DefaultComboBoxModel<Optional<ProjectDescriptor>> modelProject = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<Optional<Location>> modelLocation = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<Optional<Dataset>> modelDataset = new DefaultComboBoxModel<>();
    private final DefaultComboBoxModel<Optional<DicomStore>> modelDicomstore = new DefaultComboBoxModel<>();

    private final StudiesTable table;

    public DicomStoreSelector(GoogleAPIClient googleAPIClient, StudiesTable table) {
        this.googleAPIClient = googleAPIClient;
        this.table = table;
        BoxLayout layout = new BoxLayout(this, BoxLayout.X_AXIS);
        setLayout(layout);

        JComboBox<Optional<ProjectDescriptor>> googleProjectCombobox = new JComboBox<>(modelProject);
        JComboBox<Optional<Location>> googleLocationCombobox = new JComboBox<>(modelLocation);
        JComboBox<Optional<Dataset>> googleDatasetCombobox = new JComboBox<>(modelDataset);
        JComboBox<Optional<DicomStore>> googleDicomstoreCombobox = new JComboBox<>(modelDicomstore);

        googleProjectCombobox.setPrototypeDisplayValue(Optional.empty());
        googleLocationCombobox.setPrototypeDisplayValue(Optional.empty());
        googleDatasetCombobox.setPrototypeDisplayValue(Optional.empty());
        googleDicomstoreCombobox.setPrototypeDisplayValue(Optional.empty());

        add(googleProjectCombobox);
        add(Box.createHorizontalStrut(10));
        add(googleLocationCombobox);
        add(Box.createHorizontalStrut(10));
        add(googleDatasetCombobox);
        add(Box.createHorizontalStrut(10));
        add(googleDicomstoreCombobox);
        add(Box.createHorizontalGlue());

        googleProjectCombobox.setRenderer(new ListRenderer<>(ProjectDescriptor::getName, "-- Choose project --"));
        googleLocationCombobox.setRenderer(new ListRenderer<>(Location::getId, "-- Choose location --"));
        googleDatasetCombobox.setRenderer(new ListRenderer<>(Dataset::getName, "-- Choose dataset --"));
        googleDicomstoreCombobox.setRenderer(new ListRenderer<>(DicomStore::getName, "-- Choose store --"));
        googleProjectCombobox.setLightWeightPopupEnabled(false);

        googleProjectCombobox.addItemListener(this.<ProjectDescriptor>selectedListener(
                project -> new LoadLocationsTask(project, googleAPIClient, this),
                nothing -> updateLocations(emptyList())
        ));

        AutoRefreshComboBoxExtension.wrap(googleProjectCombobox, () -> {
            log.info("Reloading projects");
            new LoadProjectsTask(googleAPIClient, DicomStoreSelector.this).execute();
            return true;
        });

        googleLocationCombobox.addItemListener(this.<Location>selectedListener(
                location -> new LoadDatasetsTask(location, googleAPIClient, this),
                nothing  -> updateDatasets(emptyList())
        ));

        AutoRefreshComboBoxExtension.wrap(googleLocationCombobox, () ->
            getSelectedItem(modelProject).map(
                    (project) -> {
                        log.info("Reloading locations");
                        new LoadLocationsTask(project, googleAPIClient, DicomStoreSelector.this).execute();
                        return true;
                    }
            ).orElse(false)
        );

        googleDatasetCombobox.addItemListener(this.<Dataset>selectedListener(
                dataset -> new LoadDicomStoresTask(dataset, googleAPIClient, this),
                nothing -> updateDicomStores(emptyList())
        ));

        AutoRefreshComboBoxExtension.wrap(googleDatasetCombobox, () ->
                getSelectedItem(modelLocation).map(
                        (location) -> {
                            log.info("Reloading Datasets");
                            new LoadDatasetsTask(location, googleAPIClient, DicomStoreSelector.this).execute();
                            return true;
                        }
                ).orElse(false)
        );

        googleDicomstoreCombobox.addItemListener(this.<DicomStore>selectedListener(
                store -> new LoadStudiesTask(store, googleAPIClient, this),
                nothing -> updateTable(emptyList())
        ));

        AutoRefreshComboBoxExtension.wrap(googleDicomstoreCombobox, () ->
                getSelectedItem(modelDataset).map(
                        (dataset) -> {
                            log.info("Reloading Dicom stores");
                            new LoadDicomStoresTask(dataset, googleAPIClient, DicomStoreSelector.this).execute();
                            return true;
                        }
                ).orElse(false)
        );

        new LoadProjectsTask(googleAPIClient, this).execute();
    }

    public void updateProjects(List<ProjectDescriptor> result) {
        if (updateModel(result, modelProject)) {
            updateLocations(emptyList());
        }
    }

    public void updateLocations(List<Location> result) {
        if (updateModel(result, modelLocation)) {
            updateDatasets(emptyList());
        }
    }

    public void updateDatasets(List<Dataset> result) {
        if (updateModel(result, modelDataset)) {
            updateDicomStores(emptyList());
        }
    }

    public void updateDicomStores(List<DicomStore> result) {
        if (updateModel(result, modelDicomstore)) {
            updateTable(emptyList());
        }
    }

    public void updateTable(List<StudyView> studies) {
        table.clearTable();
        studies.forEach(table::addStudy);
    }

    public Optional<DicomStore> getCurrentStore() {
        return (Optional<DicomStore>) modelDicomstore.getSelectedItem();
    }

    /**
     * @return true if selected item changed, false otherwise
     */
    private <T>boolean updateModel(List<T> list, DefaultComboBoxModel<Optional<T>> model) {
        Optional<T> selectedItem = (Optional<T>)model.getSelectedItem();
        return Optional.ofNullable(selectedItem)
                .flatMap(x -> x)
                .filter(list::contains)
                .map(item -> {
                    replaceAllExcludingItem(item, list, model);
                    return false;
                })
                .orElseGet(() -> {
                    model.removeAllElements();
                    if (!list.isEmpty()) {
                        model.addElement(Optional.empty());
                        list.stream().map(Optional::of).forEach(model::addElement);
                        model.setSelectedItem(Optional.empty());
                    }
                    return true;
                });
    }

    private <T>void replaceAllExcludingItem(T selectedItem, List<T> list, DefaultComboBoxModel<Optional<T>> model) {
        List<Optional<T>> toDelete = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            Optional<T> currentItem = (Optional<T>)model.getElementAt(i);
            if (!Objects.equals(currentItem, Optional.of(selectedItem))) {
                toDelete.add(currentItem);
            }
        }
        toDelete.forEach(model::removeElement);

        int selectedIndex = list.indexOf(selectedItem);
        model.insertElementAt(Optional.empty(), 0);
        for (int i = 0; i < list.size(); i++) {
            if (selectedIndex != i) {
                if (selectedIndex > i) {
                    model.insertElementAt(Optional.of(list.get(i)), model.getSize() - 1);
                } else {
                    model.insertElementAt(Optional.of(list.get(i)), model.getSize());
                }
            }
        }

    }

    private <T>ItemListener selectedListener(Function<T, SwingWorker<?, ?>> taskFactory, Consumer<Void> onEmpty) {
        return e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                Optional<T> item = (Optional<T>) e.getItem();

                if (!item.isPresent()) {
                    onEmpty.accept(null);
                }

                item.map(taskFactory).ifPresent(SwingWorker::execute);
            }
        };
    }

    private static <T>Optional<T> getSelectedItem(DefaultComboBoxModel<Optional<T>> model) {
        return Optional.ofNullable(model.getSelectedItem())
                .flatMap(x -> (Optional<T>) x);
    }

    private class ListRenderer<T> implements ListCellRenderer<Optional<T>> {

        private final DefaultListCellRenderer renderer = new DefaultListCellRenderer();
        private final String defaultLabel;
        private final Function<T, String> textExtractor;

        public ListRenderer(Function<T, String> textExtractor, String defaultLabel) {
            this.textExtractor = textExtractor;
            this.defaultLabel = defaultLabel;
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Optional<T>> list,
                                                      Optional<T> value,
                                                      int index,
                                                      boolean isSelected,
                                                      boolean cellHasFocus) {
            renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null) {
                String label = value.map(textExtractor).orElse(defaultLabel);
                renderer.setText(label);
            }
            return renderer;
        }
    }
}
