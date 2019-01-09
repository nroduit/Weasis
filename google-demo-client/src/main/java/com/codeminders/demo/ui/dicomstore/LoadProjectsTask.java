package com.codeminders.demo.ui.dicomstore;

import com.codeminders.demo.GoogleAPIClient;
import com.codeminders.demo.model.ProjectDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

public class LoadProjectsTask extends AbstractDicomSelectorTask<List<ProjectDescriptor>> {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadProjectsTask.class);

    public LoadProjectsTask(GoogleAPIClient api, DicomStoreSelector view) {
        super(api, view);
    }

    @Override
    protected List<ProjectDescriptor> doInBackground() throws Exception {
        List<ProjectDescriptor> projects = api.fetchProjects();
        projects.sort(Comparator.comparing(ProjectDescriptor::getName));
        return projects;
    }

    @Override
    protected void onCompleted(List<ProjectDescriptor> result) {
        LOGGER.debug("Loaded projects list " + result);
        view.updateProjects(result);
    }
}
