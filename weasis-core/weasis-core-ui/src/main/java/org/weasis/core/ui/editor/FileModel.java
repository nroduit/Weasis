package org.weasis.core.ui.editor;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.util.FileUtil;

public class FileModel extends AbstractFileModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileModel.class);

    public static final File IMAGE_CACHE_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "image"); //$NON-NLS-1$

    public static final String[] functions = { "get", "close" }; //$NON-NLS-1$ //$NON-NLS-2$

    public File getFile(String path) {
        File outFile = null;
        try {
            URL url = new URL(path);

            HttpURLConnection httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setRequestMethod("GET"); //$NON-NLS-1$
            // Connect to server.
            httpCon.connect();

            // Make sure response code is in the 200 range.
            if (httpCon.getResponseCode() / 100 != 2) {
                return null;
            }

            outFile = File.createTempFile("tumb_", FileUtil.getExtension(path), IMAGE_CACHE_DIR); // $NON-NLS-2$
                                                                                                  // //$NON-NLS-3$
            LOGGER.debug("Start to download image {} to {}.", url, outFile.getName()); //$NON-NLS-1$
            if (FileUtil.writeFile(httpCon, outFile) == 0) {
                return null;
            }
        } catch (IOException e) {
            LOGGER.error("Dowloading image", e);
        }
        return outFile;
    }

    public void get(String[] argv) throws IOException {
        final String[] usage = { "Load DICOM files remotely or locally", "Usage: image:get [Options] SOURCE", //$NON-NLS-1$ //$NON-NLS-2$
            "  -f --file     Open an image from a file", // $NON-NLS-1$
            "  -u --url      Open an image from an URL", "  -? --help        show help" }; // $NON-NLS-1$ //$NON-NLS-2$

        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || args.isEmpty()) { //$NON-NLS-1$
            opt.usage();
            return;
        }
        GuiExecutor.instance().execute(() -> {
            AbstractFileModel dataModel = ViewerPluginBuilder.DefaultDataModel;
            dataModel.firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.Select, dataModel, null, dataModel));
            if (opt.isSet("file")) { //$NON-NLS-1$
                args.stream().map(s -> new File(s)).filter(f -> f.isFile())
                    .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
            }
            if (opt.isSet("url")) { //$NON-NLS-1$
                args.stream().map(this::getFile)
                    .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
            }
        });

    }

    public void close(String[] argv) throws IOException {
        final String[] usage = { "Remove DICOM files in Dicom Explorer", //$NON-NLS-1$
            "Usage: dicom:close [series] [ARGS]", //$NON-NLS-1$
            "  -a --all Close all series", //$NON-NLS-1$
            "  -s --series <args>   Close series, [arg] is Series UID", "  -? --help        show help" }; //$NON-NLS-1$ //$NON-NLS-2$
        final Option opt = Options.compile(usage).parse(argv);
        final List<String> args = opt.args();

        if (opt.isSet("help") || (args.isEmpty() && !opt.isSet("all"))) { //$NON-NLS-1$ //$NON-NLS-2$
            opt.usage();
            return;
        }

        GuiExecutor.instance().execute(() -> {
            AbstractFileModel dataModel = ViewerPluginBuilder.DefaultDataModel;
            dataModel.firePropertyChange(
                new ObservableEvent(ObservableEvent.BasicAction.Select, dataModel, null, dataModel));
            if (opt.isSet("all")) { //$NON-NLS-1$
                for (MediaSeriesGroup g : dataModel.getChildren(MediaSeriesGroupNode.rootNode)) {
                    dataModel.removeTopGroup(g);
                }
            } else if (opt.isSet("series")) { //$NON-NLS-1$
                for (String seriesUID : args) {
                    for (MediaSeriesGroup topGroup : dataModel.getChildren(MediaSeriesGroupNode.rootNode)) {
                        MediaSeriesGroup s = dataModel.getHierarchyNode(topGroup, seriesUID);
                        if (s instanceof Series) {
                            dataModel.removeSeries(s);
                            break;
                        }
                    }
                }
            }
        });
    }

}
