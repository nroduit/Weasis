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
import org.weasis.core.api.util.FileUtil;

public class FileModel extends AbstractFileModel {
    private static final Logger LOGGER = LoggerFactory.getLogger(FileModel.class);

    public static final File IMAGE_CACHE_DIR =
        AppProperties.buildAccessibleTempDirectory(AppProperties.FILE_CACHE_DIR.getName(), "image"); //$NON-NLS-1$

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

    @Override
    public void get(String[] argv) throws IOException {
        final String[] usage = { "Load an image remotely or locally", "Usage: image:get [Options] SOURCE", //$NON-NLS-1$ //$NON-NLS-2$
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
                new ObservableEvent(ObservableEvent.BasicAction.SELECT, dataModel, null, dataModel));
            if (opt.isSet("file")) { //$NON-NLS-1$
                args.stream().map(File::new).filter(File::isFile)
                    .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
            }
            if (opt.isSet("url")) { //$NON-NLS-1$
                args.stream().map(this::getFile)
                    .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
            }
        });

    }

}
