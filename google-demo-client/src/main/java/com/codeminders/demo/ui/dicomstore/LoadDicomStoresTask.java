package com.codeminders.demo.ui.dicomstore;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.model.Dataset;
import com.codeminders.demo.model.DicomStore;

import java.util.Comparator;
import java.util.List;

public class LoadDicomStoresTask extends AbstractDicomSelectorTask<List<DicomStore>> {

    private final Dataset dataset;

    public LoadDicomStoresTask(Dataset dataset,
                               GoogleAPIClient api,
                               DicomStoreSelector view) {
        super(api, view);
        this.dataset = dataset;
    }

    @Override
    protected List<DicomStore> doInBackground() throws Exception {
        List<DicomStore> locations = api.fetchDicomstores(dataset);
        locations.sort(Comparator.comparing(DicomStore::getName));
        return locations;
    }

    @Override
    protected void onCompleted(List<DicomStore> result) {
        view.updateDicomStores(result);
    }
}
