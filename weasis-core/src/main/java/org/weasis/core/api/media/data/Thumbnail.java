/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.media.data;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.KeyListener;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelListener;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import org.opencv.core.MatOfInt;
import org.opencv.imgcodecs.Imgcodecs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.Messages;
import org.weasis.core.api.gui.util.AppProperties;
import org.weasis.core.api.gui.util.GuiUtils;
import org.weasis.core.api.image.OpManager;
import org.weasis.core.api.util.ResourceUtil;
import org.weasis.core.api.util.ResourceUtil.FileIcon;
import org.weasis.core.api.util.ThreadUtil;
import org.weasis.core.util.FileUtil;
import org.weasis.core.util.StringUtil;
import org.weasis.opencv.data.PlanarImage;
import org.weasis.opencv.op.ImageConversion;
import org.weasis.opencv.op.ImageProcessor;

public class Thumbnail extends JLabel implements Thumbnailable {
  private static final Logger LOGGER = LoggerFactory.getLogger(Thumbnail.class);

  public static final File THUMBNAIL_CACHE_DIR =
      AppProperties.buildAccessibleTempDirectory(
          AppProperties.FILE_CACHE_DIR.getName(), "thumb"); // NON-NLS
  public static final ExecutorService THUMB_LOADER =
      ThreadUtil.buildNewSingleThreadExecutor("Thumbnail Loader"); // NON-NLS

  public static final String KEY_SIZE = "explorer.thumbnail.size";
  public static final int MIN_SIZE = 48;
  public static final int DEFAULT_SIZE = 144;
  public static final int MAX_SIZE = 256;

  private static final NativeCache<Thumbnail, PlanarImage> mCache =
      new NativeCache<>(30_000_000) {

        @Override
        protected void afterEntryRemove(Thumbnail key, PlanarImage img) {
          if (img != null) {
            img.release();
          }
        }
      };

  protected volatile boolean readable = true;
  protected AtomicBoolean loading = new AtomicBoolean(false);
  protected File thumbnailPath = null;
  protected int thumbnailSize;

  public Thumbnail(int thumbnailSize) {
    super(null, null, SwingConstants.CENTER);
    this.thumbnailSize = GuiUtils.getScaleLength(thumbnailSize);
  }

  public Thumbnail(
      final MediaElement media, int thumbnailSize, boolean keepMediaCache, OpManager opManager) {
    super(null, null, SwingConstants.CENTER);
    if (media == null) {
      throw new IllegalArgumentException("image cannot be null");
    }
    this.thumbnailSize = GuiUtils.getScaleLength(thumbnailSize);
    init(media, keepMediaCache, opManager);
  }

  /**
   * @param media the {@link MediaElement} value
   * @param keepMediaCache if true will remove the media from cache after building the thumbnail.
   *     Only when media is an image.
   */
  protected void init(MediaElement media, boolean keepMediaCache, OpManager opManager) {
    buildThumbnail(media, keepMediaCache, opManager);
  }

  @Override
  public void registerListeners() {
    removeMouseAndKeyListener();
  }

  public static PlanarImage createThumbnail(PlanarImage source) {
    if (source == null) {
      return null;
    }
    return ImageProcessor.buildThumbnail(
        source, new Dimension(Thumbnail.MAX_SIZE, Thumbnail.MAX_SIZE), true);
  }

  protected synchronized void buildThumbnail(
      MediaElement media, boolean keepMediaCache, OpManager opManager) {
    FileIcon fileIcon = null;
    String type = "";
    if (media != null) {
      String mime = media.getMimeType();
      if (mime != null) {
        if (mime.startsWith("image")) {
          type = Messages.getString("Thumbnail.img");
          fileIcon = FileIcon.IMAGE;
        } else if (mime.startsWith("video")) { // NON-NLS
          type = Messages.getString("Thumbnail.video");
          fileIcon = FileIcon.VIDEO;
        } else if (mime.equals("sr/dicom")) { // NON-NLS
          type = Messages.getString("Thumbnail.dicom_sr");
          fileIcon = FileIcon.TEXT;
        } else if (mime.startsWith("txt")) {
          type = Messages.getString("Thumbnail.text");
          fileIcon = FileIcon.TEXT;
        } else if (mime.endsWith("html")) {
          type = Messages.getString("Thumbnail.html");
          fileIcon = FileIcon.XML;
        } else if (mime.equals("application/pdf")) {
          type = Messages.getString("Thumbnail.pdf");
          fileIcon = FileIcon.PDF;
        } else if (mime.equals("wf/dicom")) { // NON-NLS
          type = "ECG";
          fileIcon = FileIcon.ECG;
        } else if (mime.startsWith("audio") || mime.equals("au/dicom")) { // NON-NLS
          type = Messages.getString("Thumbnail.audio");
          fileIcon = FileIcon.AUDIO;
        } else {
          type = mime;
        }
      }
    }

    if (fileIcon == null) {
      fileIcon = FileIcon.UNKNOWN;
    }
    setIcon(media, ResourceUtil.getIcon(fileIcon, 64, 64), type, keepMediaCache, opManager);
  }

  private void setIcon(
      final MediaElement media,
      final FlatSVGIcon mimeIcon,
      final String description,
      final boolean keepMediaCache,
      OpManager opManager) {
    this.setSize(thumbnailSize, thumbnailSize);

    ImageIcon imageIcon =
        new ImageIcon() {

          @Override
          public synchronized void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2d = (Graphics2D) g;
            int width = thumbnailSize;
            int height = thumbnailSize;
            final PlanarImage thumbnail = Thumbnail.this.getImage(media, keepMediaCache, opManager);
            if (thumbnail == null) {
              FontMetrics fm = g2d.getFontMetrics();
              Icon icon = mimeIcon;
              int insetY = 5;
              int textLength = fm.stringWidth(description);
              int fontHeight = 0;
              boolean displayText = StringUtil.hasText(description) && textLength + insetY < width;
              if (displayText) {
                fontHeight = fm.getHeight();
              }
              int fy = y + (thumbnailSize - fontHeight - icon.getIconHeight()) / 2;
              icon.paintIcon(c, g2d, x + (thumbnailSize - icon.getIconWidth()) / 2, fy);

              if (displayText) {
                int startX = x + (thumbnailSize - textLength) / 2;
                int startY = fm.getAscent() - fm.getDescent() - fm.getLeading();
                g2d.drawString(description, startX, fy + icon.getIconHeight() + startY + insetY);
              }
            } else {
              width = thumbnail.width();
              height = thumbnail.height();
              x += (thumbnailSize - width) / 2;
              y += (thumbnailSize - height) / 2;
              if (g2d.getDeviceConfiguration().getDefaultTransform().getScaleX() > 1.0) {
                g2d.setRenderingHint(
                    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
              }
              g2d.drawImage(
                  ImageConversion.toBufferedImage(thumbnail),
                  AffineTransform.getTranslateInstance(x, y),
                  null);
            }
            drawOverIcon(g2d, x, y, width, height);
          }

          @Override
          public int getIconWidth() {
            return thumbnailSize;
          }

          @Override
          public int getIconHeight() {
            return thumbnailSize;
          }
        };
    setIcon(imageIcon);
  }

  protected void drawOverIcon(Graphics2D g2d, int x, int y, int width, int height) {
    // Do nothing
  }

  @Override
  public File getThumbnailPath() {
    return thumbnailPath;
  }

  protected synchronized PlanarImage getImage(
      final MediaElement media, final boolean keepMediaCache, final OpManager opManager) {
    PlanarImage cacheImage;
    if ((cacheImage = mCache.get(this)) == null && readable && loading.compareAndSet(false, true)) {
      try {
        SwingWorker<Boolean, String> thumbnailReader =
            new SwingWorker<>() {
              @Override
              protected void done() {
                repaint();
              }

              @Override
              protected Boolean doInBackground() {
                loadThumbnail(media, keepMediaCache, opManager);
                return Boolean.TRUE;
              }
            };
        THUMB_LOADER.execute(thumbnailReader);
      } catch (Exception e) {
        LOGGER.error("Cannot build thumbnail!", e);
        loading.set(false);
      }
    }
    return cacheImage;
  }

  private void loadThumbnail(
      final MediaElement media, final boolean keepMediaCache, final OpManager opManager) {
    try {
      File file = thumbnailPath;
      boolean noPath = file == null || !file.canRead();
      if (noPath && media != null) {
        String path = (String) media.getTagValue(TagW.ThumbnailPath);
        if (path != null) {
          file = new File(path);
          if (file.canRead()) {
            noPath = false;
            thumbnailPath = file;
          }
        }
      }
      if (noPath) {
        if (media instanceof final ImageElement image) {
          PlanarImage imgPl = image.getImage(opManager);
          if (imgPl != null) {
            PlanarImage img = image.getRenderedImage(imgPl);
            final PlanarImage thumb = createThumbnail(img);
            if (thumb != null) {
              try {
                file =
                    File.createTempFile("tumb_", ".jpg", Thumbnail.THUMBNAIL_CACHE_DIR); // NON-NLS
              } catch (IOException e) {
                LOGGER.error("Cannot create file for thumbnail!", e);
              }
            }
            try {
              if (thumb != null && file != null) {
                MatOfInt map = new MatOfInt(Imgcodecs.IMWRITE_JPEG_QUALITY, 80);
                if (ImageProcessor.writeImage(thumb.toMat(), file, map)) {
                  /*
                   * Write the thumbnail in temp folder, better than handling the thumbnail in memory.
                   *
                   * If writeImage returns false, it could be an out of memory exception.
                   */
                  image.setTag(TagW.ThumbnailPath, file.getPath());
                  thumbnailPath = file;
                  return;
                }
              }

              if (thumb == null || thumb.width() <= 0) {
                readable = false;
                ImageConversion.releasePlanarImage(thumb);
              } else {
                mCache.put(this, thumb);
              }
            } finally {
              if (!keepMediaCache) {
                // Prevent to many files open on Linux (Ubuntu => 1024) and close image stream
                image.removeImageFromCache();
              }
            }
          } else {
            readable = false;
          }
        }
      } else {
        Load ref = new Load(file);
        // loading images sequentially, only one thread pool
        Future<PlanarImage> future = ImageElement.IMAGE_LOADER.submit(ref);
        PlanarImage thumb = null;
        try {
          PlanarImage img = future.get();
          if (img != null) {
            int width = img.width();
            int height = img.height();
            if (width > thumbnailSize || height > thumbnailSize) {
              thumb =
                  ImageProcessor.buildThumbnail(
                      img, new Dimension(thumbnailSize, thumbnailSize), true);
            } else {
              thumb = img;
            }
          }

        } catch (InterruptedException e) {
          // Re-assert the thread's interrupted status
          Thread.currentThread().interrupt();
          // We don't need the result, so cancel the task too
          future.cancel(true);
        } catch (ExecutionException e) {
          LOGGER.error("Cannot read thumbnail pixel data!: {}", file, e);
        }
        if (thumb == null || thumb.width() <= 0) {
          readable = false;
        } else {
          mCache.put(this, thumb);
        }
      }
    } finally {
      loading.set(false);
    }
  }

  protected void removeImageFromCache() {
    // Unload image from memory
    mCache.remove(this);
  }

  @Override
  public void dispose() {
    removeImageFromCache();

    if (thumbnailPath != null
        && thumbnailPath.getPath().startsWith(AppProperties.FILE_CACHE_DIR.getPath())) {
      FileUtil.delete(thumbnailPath);
    }

    removeMouseAndKeyListener();
  }

  @Override
  public void removeMouseAndKeyListener() {
    MouseListener[] listener = this.getMouseListeners();
    MouseMotionListener[] motionListeners = this.getMouseMotionListeners();
    KeyListener[] keyListeners = this.getKeyListeners();
    MouseWheelListener[] wheelListeners = this.getMouseWheelListeners();
    for (MouseListener mouseListener : listener) {
      this.removeMouseListener(mouseListener);
    }
    for (MouseMotionListener motionListener : motionListeners) {
      this.removeMouseMotionListener(motionListener);
    }
    for (KeyListener keyListener : keyListeners) {
      this.removeKeyListener(keyListener);
    }
    for (MouseWheelListener wheelListener : wheelListeners) {
      this.removeMouseWheelListener(wheelListener);
    }
  }

  static class Load implements Callable<PlanarImage> {

    private final File path;

    public Load(File path) {
      this.path = path;
    }

    @Override
    public PlanarImage call() throws Exception {
      return ImageProcessor.readImageWithCvException(path, null);
    }
  }
}
