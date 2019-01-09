package com.codeminders.demo.ui;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.ui.dicomstore.DicomStoreSelector;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import static javax.swing.BoxLayout.PAGE_AXIS;

public class GoogleExplorer extends JPanel {

    private final StudiesTable table;

    private final GoogleAPIClient googleAPIClient;
    private final List<BiConsumer<String, String>> studySelectedListener = new ArrayList<>();
    private final DicomStoreSelector storeSelector;

    private final SearchPanel searchPanel;

    public GoogleExplorer(GoogleAPIClient googleAPIClient) {
        this.googleAPIClient = googleAPIClient;

        BorderLayout layout = new BorderLayout();

        layout.setHgap(15);
        setLayout(layout);

        table = new StudiesTable(this);
        storeSelector = new DicomStoreSelector(googleAPIClient, table);
        searchPanel = new SearchPanel(googleAPIClient, storeSelector);

        add(centralComponent(), BorderLayout.CENTER);
        add(searchPanel, BorderLayout.WEST);
    }

    public Component centralComponent() {
        JPanel panel = new JPanel();
        BoxLayout layout = new BoxLayout(panel, PAGE_AXIS);
        panel.setLayout(layout);

        panel.add(storeSelector);
        panel.add(Box.createVerticalStrut(10));
        panel.add(table);

        return panel;
    }

    public void fireStudySelected(String studyId) {
        storeSelector.getCurrentStore()
                .map(store -> GoogleAPIClient.getImageUrl(store, studyId))
                .ifPresent(image -> studySelectedListener.forEach(listener -> listener.accept(image, googleAPIClient.getAccessToken())));
    }

    public void subscribeStudySelected(BiConsumer<String, String> consumer) {
        studySelectedListener.add(consumer);
    }
}
