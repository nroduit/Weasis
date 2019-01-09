package com.codeminders.demo.ui.dicomstore;

import com.codeminders.demo.GoogleAPIClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.concurrent.ExecutionException;

public abstract class AbstractDicomSelectorTask<T> extends SwingWorker<T, Void> {
    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDicomSelectorTask.class);

    protected final GoogleAPIClient api;
    protected final DicomStoreSelector view;

    public AbstractDicomSelectorTask(GoogleAPIClient api, DicomStoreSelector view) {
        this.api = api;
        this.view = view;
    }

    @Override
    protected final void done() {
        try {
            T result = get();
            onCompleted(result);
        } catch (ExecutionException ex) {
            LOGGER.error("Error on dicom task", ex.getCause());
            JOptionPane.showMessageDialog(null, "Unexpected error on fetching google api: " + ex.getCause().getMessage());
        } catch (InterruptedException ex) {
            LOGGER.error("Interrupted", ex);
        }
    }

    protected abstract void onCompleted(T result);
}
