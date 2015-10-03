/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Inform??tica Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import org.weasis.core.api.gui.util.ActionW;

/**
 * Animati Workstation Actions.
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 09 Jan
 */
public class ActionWA {

    /** smoothing: boolean. */
    public static final ActionW SMOOTHING = new ActionW("Smoothing", "interpolate", 0, 0, null);

    /** volumetricRendering: boolean. */
    public static final ActionW VOLUM_RENDERING =
        new ActionW("Volumetric Rendering", "volumetricRendering", 0, 0, null);

    /** volumetricQuality: int. */
    public static final ActionW VOLUM_QUALITY = new ActionW("Volumetric Quality", "volumetricQuality", 0, 0, null);

    /** volumetricCenterSlicing: boolean. */
    public static final ActionW VOLUM_CENTER_SLICING =
        new ActionW("Volumetric Center Slicing", "volumetricCenterSlicing", 0, 0, null);

    /** volumetricDithering: boolean. */
    public static final ActionW VOLUM_DITHERING =
        new ActionW("Volumetric Dithering", "volumetricDithering", 0, 0, null);

    /** volumetricLighting: boolean. */
    public static final ActionW VOLUM_LIGHT = new ActionW("Volumetric Lighting", "volumetricLighting", 0, 0, null);

    /** Mip option or state: enum TextureImageCanvas::MipOption. */
    public static final ActionW MIP_OPTION = new ActionW("Mip Type", "mip-opt", 0, 0, null);
    /** Mip depth: double. */
    public static final ActionW MIP_DEPTH = new ActionW("Mip Depth", "mip-dep", 0, 0, null);

    /** If present, informs actual Location of content (Double). */
    public static final ActionW LOCATION = new ActionW("Location", "location", 0, 0, null);

    public static final ActionW BEST_FIT = new ActionW("Best Fit", "bestFit", 0, 0, null);

    /** Private constructor. */
    private ActionWA() {
    }

}
