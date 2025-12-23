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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandExtension;
import org.weasis.core.api.command.Option;
import org.weasis.core.api.command.Options;
import org.weasis.core.api.explorer.ObservableEvent;
import org.weasis.core.api.explorer.model.AbstractFileModel;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.net.ClosableURLConnection;
import org.weasis.core.api.net.NetworkUtil;
import org.weasis.core.api.net.URLParameters;
import org.weasis.core.util.FileUtil;

@Extension
public class FileModel extends AbstractFileModel implements CommandExtension {
  private static final Logger LOGGER = LoggerFactory.getLogger(FileModel.class);

  public static final Path IMAGE_CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(AppProperties.CACHE_NAME, "image");

  private static final String GET_USAGE =
      """
      Load images remotely or locally
      Usage: image:get ([-f file]... [-u url]...)
        -f --file=FILE     open an image from a file
        -u --url=URL       open an image from an URL
        -? --help          show help
      """;

  @Override
  public String getNamespace() {
    return "image";
  }

  @Override
  public void execute(String commandName, String[] args) throws Exception {
    switch (commandName) {
      case "get":
        get(args);
        break;
      case "close":
        close(args, commandName);
        break;
      default:
        throw new IllegalArgumentException("Unknown command: " + commandName);
    }
  }

  @Override
  public String getHelp() {
    return String.join("\n", GET_USAGE)
        + "\n\n"
        + String.join("\n", CLOSE_USAGE.formatted(getNamespace(), getNamespace()));
  }

  private File getFile(String url) {
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
    return outFile.toFile();
  }

  @Override
  public void get(String[] argv) throws IOException {
    final Option opt = Options.compile(GET_USAGE).parse(argv);
    final List<String> fargs = opt.getList("file"); // NON-NLS
    final List<String> uargs = opt.getList("url"); // NON-NLS

    if (opt.isSet("help") || (fargs.isEmpty() && uargs.isEmpty())) { // NON-NLS
      opt.usage();
      return;
    }
    GuiExecutor.execute(
        () -> {
          AbstractFileModel dataModel = ViewerPluginBuilder.DefaultDataModel;
          dataModel.firePropertyChange(
              new ObservableEvent(ObservableEvent.BasicAction.SELECT, dataModel, null, dataModel));
          if (opt.isSet("file")) { // NON-NLS
            fargs.stream()
                .map(File::new)
                .filter(File::isFile)
                .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
          }
          if (opt.isSet("url")) { // NON-NLS
            uargs.stream()
                .map(this::getFile)
                .forEach(f -> ViewerPluginBuilder.openSequenceInDefaultPlugin(f, true, true));
          }
        });
  }
}
