/*
 * @copyright Copyright (c) 2012 Animati Sistemas de Informática Ltda.
 * (http://www.animati.com.br)
 */
package br.com.animati.texture.mpr3dview.api;

import br.com.animati.texture.mpr3dview.ViewTexture;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.image.RenderedImage;
import java.util.List;
import org.weasis.core.api.gui.util.GuiExecutor;
import org.weasis.core.api.image.measure.MeasurementsAdapter;
import org.weasis.core.api.image.util.MeasurableLayer;
import org.weasis.core.api.image.util.Unit;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.ui.graphic.Graphic;

/**
 *
 * @author Gabriela Bauermann (gabriela@animati.com.br)
 * @version 2015, 06 May.
 */
public class TextureMeasurableLayer implements MeasurableLayer {
    
    private ViewTexture owner;
    
    public TextureMeasurableLayer(ViewTexture parent) {
        owner = parent;
    }
    
    @Override
    public boolean hasContent() {
        return owner.hasContent();
    }

    @Override
    public MeasurementsAdapter getMeasurementAdapter(Unit displayUnit) {
        //TODO: support other displayUnits
        if (hasContent()) {
            String abr = owner.getSeriesObject().getPixelSpacingUnit().getAbbreviation();
            double[] pixelSpacing = owner.getSeriesObject().getAcquisitionPixelSpacing();
            double pixelSize = 1;
            if (pixelSpacing != null) {
                pixelSize = pixelSpacing[0];
            }
            return new MeasurementsAdapter(pixelSize, 0, 0, false, 0, abr);
        }
        return null;
    }
    
    @Override
    public AffineTransform getShapeTransform() {
        //TODO Rename??
        //_________Affine para posicionar e escalar shape
            //Necessario porque a imagem retornada pelo renderizador terah o
            //tamanho do canvas, nao da imagem original.
        if (hasContent()) {
            double scale = owner.getActualDisplayZoom();
            final AffineTransform affineTransform = AffineTransform.getScaleInstance(scale, scale);
            Rectangle imageRect = owner.getUnrotatedImageRect();
            affineTransform.translate(imageRect.getX() / scale, imageRect.getY() / scale);
            return affineTransform;
        }
        return null;
    }
    
    @Override
    public Object getSourceTagValue(TagW tagW) {
        if (hasContent()) {
            Object tagValue = owner.getSeriesObject().getTagValue(tagW);
            if (tagValue == null) {
                tagValue = owner.getSeriesObject().getTagValue(tagW, 0);
            }
            return tagValue;
        }
        return null;
    }
    
    @Override
    public String getPixelValueUnit() {
        if (hasContent()) {
            return owner.getSeriesObject().getPixelValueUnit();
        }
        return null;
    }


    @Override
    public RenderedImage getSourceRenderedImage() {
        RenderSupport rs= owner.getRenderSupport();
        RenderedImage rendered = rs.getRenderedAsSource();
        if (rendered == null) {
            rs.startRendering(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    //TODO: for now the only use of this method is for graphics
                    //statistic. If is has more uses, maybe this listener will
                    //have to be variable.
                    GuiExecutor.instance().execute(new Runnable() {
                        @Override
                        public void run() {
                            List<Graphic> list = owner.getLayerModel().getAllGraphics();
                            for (Graphic graphic : list) {
                                graphic.updateLabel(true, owner);
                            }
                        }
                    });
                }
            });
        }
        return rendered;
    }
    
}
