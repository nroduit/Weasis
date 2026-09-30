/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.ui.editor;

import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObjectBuilder;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.imageio.ImageIO;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.AbstractVerb;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.InputPath;
import org.weasis.core.api.command.RemoteUrl;
import org.weasis.core.api.command.Renamed;
import org.weasis.core.api.command.Since;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.MediaSeriesGroupNode;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.api.util.JsonUtil;
import org.weasis.core.util.FileUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Extension
@Command(
    name = "image",
    description = "Loading and closing of plain images (non-DICOM) in the default viewer.",
    subcommands = {
      FileModel.Load.class,
      FileModel.Unload.class,
      FileModel.ListImages.class,
      FileModel.Compare.class
    })
public class FileModel extends AbstractFileModel implements CommandExtension {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileModel.class);

  public static final Path IMAGE_CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "image");

  @Override
  public String getNamespace() {
    return "image"; // NON-NLS
  }

  @Override
  public Object getCommandModel() {
    return this;
  }

  private static Path getFile(String url) {
    Path outFile;
    try (ClosableURLConnection http = NetworkUtil.getUrlConnection(url, URLParameters.DEFAULT);
        InputStream in = http.getInputStream()) {
      outFile = Files.createTempFile(IMAGE_CACHE_DIR, "img_", FileUtil.getExtension(url));
      LOGGER.debug("Start to download image {} to {}.", url, outFile);
      FileUtil.writeStreamWithIOException(in, outFile);
    } catch (IOException e) {
      LOGGER.error("Dowloading image", e);
      return null;
    }
    return outFile;
  }

  /**
   * The model every image opened by the application lands in. The namespace acts on it and not on
   * the extension instance the plugin framework creates, which holds nothing.
   */
  static AbstractFileModel dataModel() {
    return ViewerPluginBuilder.DefaultDataModel;
  }

  /** The handle of a group or of a series: the value its node is registered under. */
  static String handle(MediaSeriesGroup node) {
    return String.valueOf(node.getTagValue(node.getTagID()));
  }

  @Command(
      name = "load",
      aliases = "get",
      description = "Opens images from files or URLs and returns the series it opened.")
  static class Load extends AbstractVerb {
    @Option(names = "--file", paramLabel = "<input-path>", description = "Image file to open.")
    List<InputPath> files = new ArrayList<>();

    @Option(names = "-f", hidden = true, paramLabel = "<input-path>")
    @Renamed("--file")
    void setLegacyFiles(List<InputPath> values) {
      this.files = values;
    }

    @Option(
        names = "--url",
        paramLabel = "<remote-url>",
        description = "Image URL to download and open.")
    List<RemoteUrl> urls = new ArrayList<>();

    @Option(names = "-u", hidden = true, paramLabel = "<remote-url>")
    @Renamed("--url")
    void setLegacyUrls(List<RemoteUrl> values) {
      this.urls = values;
    }

    @Override
    protected void run() {
      if (files.isEmpty() && urls.isEmpty()) {
        throw invalid("Give at least one file or URL");
      }
      print(GuiExecutor.invokeAndRethrow(this::open));
    }

    private String open() {
      AbstractFileModel dataModel = dataModel();
      dataModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.SELECT, dataModel, null, dataModel));
      JsonArrayBuilder opened = Json.createArrayBuilder();
      JsonArrayBuilder failed = Json.createArrayBuilder();
      for (InputPath input : files) {
        Path file = input.value();
        if (Files.isRegularFile(file)) {
          openOne(file, file.toString(), opened, failed);
        } else {
          failed.add(file.toString());
        }
      }
      for (RemoteUrl url : urls) {
        Path file = getFile(url.toString());
        if (file == null) {
          failed.add(url.toString());
        } else {
          openOne(file, url.toString(), opened, failed);
        }
      }
      return Json.createObjectBuilder()
          .add("opened", opened) // NON-NLS
          .add("failed", failed) // NON-NLS
          .build()
          .toString();
    }

    private static void openOne(
        Path file, String source, JsonArrayBuilder opened, JsonArrayBuilder failed) {
      MediaSeries<MediaElement> series =
          ViewerPluginBuilder.openInDefaultViewer(file, ViewerOpenOptions.defaults());
      if (series == null) {
        failed.add(source);
        return;
      }
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("series", handle(series)) // NON-NLS
              .add("source", source) // NON-NLS
              .add("imageCount", series.size(null)); // NON-NLS
      MediaSeriesGroup group = dataModel().getParent(series, AbstractFileModel.group);
      if (group != null) {
        json.add("group", handle(group)); // NON-NLS
      }
      opened.add(json);
    }
  }

  @Command(
      name = "compare",
      description =
          "Compares two image files pixel by pixel and returns how far apart they are: the number "
              + "of pixels that differ, their share, and the largest and mean channel difference.")
  @Since("5.0")
  static class Compare extends AbstractVerb {

    @Option(
        names = "--a",
        required = true,
        paramLabel = "<input-path>",
        description = "First image.")
    @Since("5.0")
    InputPath first;

    @Option(
        names = "--b",
        required = true,
        paramLabel = "<input-path>",
        description = "Second image.")
    @Since("5.0")
    InputPath second;

    @Option(
        names = "--tolerance",
        paramLabel = "<n>",
        defaultValue = "0",
        description =
            "Channel difference below which two pixels count as equal, 0 to 255 (default: "
                + "${DEFAULT-VALUE}).")
    @Since("5.0")
    int tolerance;

    @Override
    protected void run() throws IOException {
      if (tolerance < 0 || tolerance > 255) {
        throw invalid("--tolerance is between 0 and 255");
      }
      BufferedImage a = read(first.value());
      BufferedImage b = read(second.value());
      JsonObjectBuilder json =
          Json.createObjectBuilder()
              .add("a", first.value().toAbsolutePath().toString()) // NON-NLS
              .add("b", second.value().toAbsolutePath().toString()); // NON-NLS
      if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
        print(
            json.add("equal", false) // NON-NLS
                .add("sameSize", false) // NON-NLS
                .add("width", a.getWidth()) // NON-NLS
                .add("height", a.getHeight()) // NON-NLS
                .add("otherWidth", b.getWidth()) // NON-NLS
                .add("otherHeight", b.getHeight()) // NON-NLS
                .build()
                .toString());
        return;
      }
      print(json.addAll(difference(a, b, tolerance)).build().toString());
    }

    /** The per-pixel difference of two images of the same size, over the three colour channels. */
    private static JsonObjectBuilder difference(BufferedImage a, BufferedImage b, int tolerance) {
      long different = 0;
      long sum = 0;
      int max = 0;
      int width = a.getWidth();
      int height = a.getHeight();
      for (int y = 0; y < height; y++) {
        for (int x = 0; x < width; x++) {
          int left = a.getRGB(x, y);
          int right = b.getRGB(x, y);
          int channels =
              Math.max(
                  Math.abs(((left >> 16) & 0xFF) - ((right >> 16) & 0xFF)),
                  Math.max(
                      Math.abs(((left >> 8) & 0xFF) - ((right >> 8) & 0xFF)),
                      Math.abs((left & 0xFF) - (right & 0xFF))));
          sum += channels;
          max = Math.max(max, channels);
          if (channels > tolerance) {
            different++;
          }
        }
      }
      long pixels = (long) width * height;
      return Json.createObjectBuilder()
          .add("equal", different == 0) // NON-NLS
          .add("sameSize", true) // NON-NLS
          .add("width", width) // NON-NLS
          .add("height", height) // NON-NLS
          .add("differentPixels", different) // NON-NLS
          .add(
              "differentPixelsPercent", // NON-NLS
              JsonUtil.decimal((float) (100.0 * different / pixels)))
          .add("maxChannelDifference", max) // NON-NLS
          .add(
              "meanChannelDifference", // NON-NLS
              JsonUtil.decimal((float) ((double) sum / pixels)));
    }

    private static BufferedImage read(Path file) throws IOException {
      BufferedImage image = ImageIO.read(file.toFile());
      if (image == null) {
        throw new IllegalArgumentException("Not an image this machine can read: " + file);
      }
      return image;
    }
  }

  @Command(
      name = "list",
      description = "The groups and series of images opened in the default viewer, as JSON.")
  @Since("5.0")
  static class ListImages extends AbstractVerb {
    @Override
    protected void run() {
      print(GuiExecutor.invokeAndRethrow(ListImages::list));
    }

    private static String list() {
      AbstractFileModel dataModel = dataModel();
      JsonArrayBuilder groups = Json.createArrayBuilder();
      for (MediaSeriesGroup group : dataModel.getChildren(MediaSeriesGroupNode.rootNode)) {
        JsonArrayBuilder series = Json.createArrayBuilder();
        for (MediaSeriesGroup node : dataModel.getChildren(group)) {
          JsonObjectBuilder json = Json.createObjectBuilder().add("series", handle(node));
          if (node instanceof MediaSeries<?> media) {
            json.add("imageCount", media.size(null)); // NON-NLS
          }
          series.add(json);
        }
        groups.add(
            Json.createObjectBuilder()
                .add("group", handle(group)) // NON-NLS
                .add("series", series)); // NON-NLS
      }
      return Json.createObjectBuilder().add("groups", groups).build().toString(); // NON-NLS
    }
  }

  @Command(
      name = "unload",
      aliases = "close",
      description =
          "Closes groups or images and returns what was closed; an unknown handle closes nothing.")
  static class Unload extends AbstractVerb {
    @Option(names = "--all", description = "Close every group.")
    boolean all;

    @Option(names = "-a", hidden = true)
    @Renamed("--all")
    void setLegacyAll(boolean value) {
      this.all = value;
    }

    @Option(
        names = "--group",
        paramLabel = "<group>",
        description = "Group to close, as 'image:load' returns it.")
    List<String> groups = new ArrayList<>();

    @Option(names = "-g", hidden = true, paramLabel = "<group>")
    @Renamed("--group")
    void setLegacyGroups(List<String> values) {
      this.groups = values;
    }

    @Option(
        names = "--series",
        paramLabel = "<series>",
        description = "Series or image to close, as 'image:load' returns it.")
    List<String> series = new ArrayList<>();

    @Option(names = "-s", hidden = true, paramLabel = "<series>")
    @Renamed("--series")
    void setLegacySeries(List<String> values) {
      this.series = values;
    }

    @Override
    protected void run() {
      if (!all && groups.isEmpty() && series.isEmpty()) {
        throw invalid("Give --all or at least one group or series");
      }
      print(GuiExecutor.invokeAndRethrow(this::close));
    }

    private String close() {
      AbstractFileModel dataModel = dataModel();
      List<MediaSeriesGroup> topGroups =
          all
              ? List.copyOf(dataModel.getChildren(MediaSeriesGroupNode.rootNode))
              : resolve(
                  groups, uid -> dataModel.getHierarchyNode(MediaSeriesGroupNode.rootNode, uid));
      List<MediaSeriesGroup> seriesNodes =
          all ? List.of() : resolve(series, uid -> seriesNode(dataModel, uid));

      dataModel.firePropertyChange(
          new ObservableEvent(ObservableEvent.BasicAction.SELECT, dataModel, null, dataModel));
      JsonArrayBuilder closedGroups = Json.createArrayBuilder();
      for (MediaSeriesGroup node : topGroups) {
        closedGroups.add(handle(node));
        dataModel.removeTopGroup(node);
      }
      JsonArrayBuilder closedSeries = Json.createArrayBuilder();
      for (MediaSeriesGroup node : seriesNodes) {
        closedSeries.add(handle(node));
        dataModel.removeSeries(node);
      }
      return Json.createObjectBuilder()
          .add("groups", closedGroups) // NON-NLS
          .add("series", closedSeries) // NON-NLS
          .build()
          .toString();
    }

    /** Resolves every handle before anything is closed: an unknown one is an error. */
    private static List<MediaSeriesGroup> resolve(
        List<String> handles, Function<String, MediaSeriesGroup> lookup) {
      List<MediaSeriesGroup> nodes = new ArrayList<>();
      List<String> unknown = new ArrayList<>();
      for (String handle : handles) {
        MediaSeriesGroup node = lookup.apply(handle);
        if (node == null) {
          unknown.add(handle);
        } else {
          nodes.add(node);
        }
      }
      if (!unknown.isEmpty()) {
        throw CommandFailure.unknownHandle(
            "Nothing closed, unknown handles: " + unknown, "image:list"); // NON-NLS
      }
      return nodes;
    }

    private static MediaSeriesGroup seriesNode(AbstractFileModel dataModel, String uid) {
      for (MediaSeriesGroup topGroup : dataModel.getChildren(MediaSeriesGroupNode.rootNode)) {
        MediaSeriesGroup node = dataModel.getHierarchyNode(topGroup, uid);
        if (node != null) {
          return node;
        }
      }
      return null;
    }
  }
}
