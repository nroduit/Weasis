package com.codeminders.demo.ui.dicomstore;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.model.Dataset;
import com.codeminders.demo.model.Location;

import java.util.Comparator;
import java.util.List;

public class LoadDatasetsTask extends AbstractDicomSelectorTask<List<Dataset>> {

    private final Location location;

    public LoadDatasetsTask(Location location,
                            GoogleAPIClient api,
                            DicomStoreSelector view) {
        super(api, view);
        this.location = location;
    }

    @Override
    protected List<Dataset> doInBackground() throws Exception {
        List<Dataset> locations = api.fetchDatasets(location);
        locations.sort(Comparator.comparing(Dataset::getName));
        return locations;
    }

    @Override
    protected void onCompleted(List<Dataset> result) {
        view.updateDatasets(result);
    }
}
