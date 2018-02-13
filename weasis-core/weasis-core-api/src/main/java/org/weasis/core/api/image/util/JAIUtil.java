/*******************************************************************************
 * Copyright (c) 2009-2018 Weasis Team and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v20.html
 *
 * Contributors:
 *     Nicolas Roduit - initial API and implementation
 *******************************************************************************/
package org.weasis.core.api.image.util;

import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.Raster;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderedImageFactory;

import javax.media.jai.ImageLayout;
import javax.media.jai.JAI;
import javax.media.jai.OperationDescriptorImpl;
import javax.media.jai.OperationRegistry;
import javax.media.jai.PlanarImage;
import javax.media.jai.RegistryElementDescriptor;
import javax.media.jai.TileCache;
import javax.media.jai.registry.RIFRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JAIUtil {
    private static final Logger LOGGER = LoggerFactory.getLogger(JAIUtil.class);

    private JAIUtil() {
    }

    public static void registerOp(OperationRegistry or, OperationDescriptorImpl descriptor) {
        String name = descriptor.getName();
        String[] mode = descriptor.getSupportedModes();
        RegistryElementDescriptor val = or.getDescriptor(mode[0], name);
        if (val == null) {
            or.registerDescriptor(descriptor);
            RIFRegistry.register(null, name, "org.weasis.core.api.image.op", (RenderedImageFactory) descriptor); //$NON-NLS-1$
        }
    }

    public static OperationRegistry getOperationRegistry() {
        return getJAI().getOperationRegistry();
    }

    public static JAI getJAI() {
        // Necessary to load JAI with the right classloader when JAI already exist in JRE
        // Change to the bundle classloader for loading the services providers (spi) correctly.
        ClassLoader bundleClassLoader = JAI.class.getClassLoader();
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(bundleClassLoader);
        JAI jai = JAI.getDefaultInstance();
        Thread.currentThread().setContextClassLoader(originalClassLoader);
        return jai;
    }

    /**
     * Set memory in JAI used by the Tile Cache.
     */
    public static void setJaiCacheMemoryCapacity(long tileCacheMB) {
        getJAI().getTileCache().setMemoryCapacity(tileCacheMB * 1024L * 1024L);
    }

    public static void removeCacheTiles(PlanarImage img) {
        if (img != null) {
            getJAI().getTileCache().removeTiles(img);
        }
    }

    public static void addCacheTiles(RenderedImage img, Rectangle tileBounds) {
        if (img != null && tileBounds != null) {
            TileCache tileCache = getJAI().getTileCache();

            // Loop over tiles within the clipping region
            for (int tj = tileBounds.y; tj <= tileBounds.height; tj++) {
                for (int ti = tileBounds.x; ti <= tileBounds.width; ti++) {
                    try {
                        Raster tile = tileCache.getTile(img, ti, tj);
                        if (tile == null) {
                            tile = img.getTile(ti, tj);
                            tileCache.add(img, ti, tj, tile);
                        }
                    } catch (Exception ex) {
                        LOGGER.error("Cannot add tile to cache", ex); //$NON-NLS-1$
                    }
                }
            }
        }
    }

    public static ImageLayout getImageLayoutHint(RenderingHints renderHints) {
        if (renderHints == null) {
            return null;
        } else {
            return (ImageLayout) renderHints.get(JAI.KEY_IMAGE_LAYOUT);
        }
    }
}
