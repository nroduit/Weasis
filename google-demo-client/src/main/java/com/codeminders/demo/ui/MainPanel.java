package com.codeminders.demo.ui;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.GoogleAPIClientFactory;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class MainPanel extends JPanel {

    public static final String VIEWER_PANEL = "Weasis";
    private static final String TABLE_PANEL = "StudiesTable";
    private static final String LOADING_PANEL = "LoadingPanel";

    private final CardLayout layout;
    private final GoogleExplorer explorer;

    private final GoogleAPIClient googleAPIClient = GoogleAPIClientFactory.getInstance().createGoogleClient();

    public MainPanel(JComponent viewer) {
        layout = new CardLayout();
        setLayout(layout);

        JPanel loaderIndicator = createLoaderIndicator();

        explorer = new GoogleExplorer(googleAPIClient);
        add(explorer, TABLE_PANEL);
        add(viewer, VIEWER_PANEL);
        add(loaderIndicator, LOADING_PANEL);
    }

    public void showViewer() {
        layout.show(this, VIEWER_PANEL);
    }

    public void showExplorer() {
        layout.show(this, TABLE_PANEL);
    }

    public void showLoadIndicator() {
        layout.show(this, LOADING_PANEL);
    }

    public void addViewSelectedListener(BiConsumer<String, String> consumer) {
        explorer.subscribeStudySelected(consumer);
    }

    private JPanel createLoaderIndicator() {
        JPanel loading = new JPanel();
        BorderLayout borderLayout = new BorderLayout();
        loading.setLayout(borderLayout);
        ImageIcon loadingIcon = new ImageIcon(MainPanel.class.getResource("/loader.gif"));
        JLabel label = new JLabel(loadingIcon);
        loading.add(label, BorderLayout.CENTER);
        return loading;
    }
}
