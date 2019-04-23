package org.weasis.core.api.image.util;

import java.util.Map;
import java.util.Objects;

import org.weasis.core.api.gui.util.ActionW;
import org.weasis.core.api.image.LutShape;
import org.weasis.core.api.image.PseudoColorOp;
import org.weasis.core.api.image.WindowOp;
import org.weasis.core.api.media.data.ImageElement;
import org.weasis.core.api.media.data.TagReadable;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.LangUtil;
import org.weasis.opencv.data.LookupTableCV;

public class WindLevelParameters {
    private final double window;
    private final double level;
    private final double levelMin;
    private final double levelMax;
    private final boolean pixelPadding;
    private final boolean inverseLut;
    private final boolean fillOutsideLutRange;
    private final boolean allowWinLevelOnColorImage;
    private final LutShape lutShape;
    private final LookupTableCV presentationStateLut;
    private final TagReadable presentationStateTags;

    public WindLevelParameters(ImageElement img, Map<String, Object> params) {
        Objects.requireNonNull(img);

        Double win = null;
        Double lev = null;
        Double levMin = null;
        Double levMax = null;
        LutShape shape = null;
        Boolean padding = null;
        Boolean invLUT = null;
        Boolean fillLutOut = null;
        Boolean wlOnColor = null;
        TagReadable prTags = null;
        LookupTableCV prLutData = null;

        if (params != null) {
            win = (Double) params.get(ActionW.WINDOW.cmd());
            lev = (Double) params.get(ActionW.LEVEL.cmd());
            levMin = (Double) params.get(ActionW.LEVEL_MIN.cmd());
            levMax = (Double) params.get(ActionW.LEVEL_MAX.cmd());
            shape = (LutShape) params.get(ActionW.LUT_SHAPE.cmd());
            padding = (Boolean) params.get(ActionW.IMAGE_PIX_PADDING.cmd());
            invLUT = (Boolean) params.get(PseudoColorOp.P_LUT_INVERSE);
            fillLutOut = (Boolean) params.get(WindowOp.P_FILL_OUTSIDE_LUT);
            wlOnColor = (Boolean) params.get(WindowOp.P_APPLY_WL_COLOR);
            prTags = (TagReadable) params.get("pr.element");
            if (prTags != null) {
                prLutData = (LookupTableCV) prTags.getTagValue(TagW.PRLUTsData);
            }
        }

        this.presentationStateTags = prTags;
        this.presentationStateLut = prLutData;
        this.fillOutsideLutRange = LangUtil.getNULLtoFalse(fillLutOut);
        this.allowWinLevelOnColorImage = LangUtil.getNULLtoFalse(wlOnColor);
        this.pixelPadding = LangUtil.getNULLtoTrue(padding);
        this.inverseLut = LangUtil.getNULLtoFalse(invLUT);
        this.window = (win == null) ? img.getDefaultWindow(pixelPadding) : win;
        this.level = (lev == null) ? img.getDefaultLevel(pixelPadding) : lev;
        this.lutShape = (shape == null) ? img.getDefaultShape(pixelPadding) : shape;
        if (levMin == null || levMax == null) {
            this.levelMin = Math.min(level - window / 2.0, img.getMinValue(prTags, pixelPadding));
            this.levelMax = Math.max(level + window / 2.0, img.getMaxValue(prTags, pixelPadding));
        } else {
            this.levelMin = Math.min(levMin, img.getMinValue(prTags, pixelPadding));
            this.levelMax = Math.max(levMax, img.getMaxValue(prTags, pixelPadding));
        }
    }

    public double getWindow() {
        return window;
    }

    public double getLevel() {
        return level;
    }

    public double getLevelMin() {
        return levelMin;
    }

    public double getLevelMax() {
        return levelMax;
    }

    public boolean isPixelPadding() {
        return pixelPadding;
    }

    public boolean isInverseLut() {
        return inverseLut;
    }

    public boolean isFillOutsideLutRange() {
        return fillOutsideLutRange;
    }

    public boolean isAllowWinLevelOnColorImage() {
        return allowWinLevelOnColorImage;
    }

    public LutShape getLutShape() {
        return lutShape;
    }

    public LookupTableCV getPresentationStateLut() {
        return presentationStateLut;
    }

    public TagReadable getPresentationStateTags() {
        return presentationStateTags;
    }

}
