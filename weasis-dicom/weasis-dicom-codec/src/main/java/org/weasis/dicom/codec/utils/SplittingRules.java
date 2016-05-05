package org.weasis.dicom.codec.utils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.TagW;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.display.Modality;
import org.weasis.dicom.codec.utils.SplittingModalityRules.And;
import org.weasis.dicom.codec.utils.SplittingModalityRules.Condition;
import org.weasis.dicom.codec.utils.SplittingModalityRules.DefaultCondition;

public class SplittingRules {
    private final Map<Modality, SplittingModalityRules> rules;

    public SplittingRules() {
        rules = new HashMap<>();
        initDefault();

    }

    private void initDefault() {
        SplittingModalityRules defRules = new SplittingModalityRules(Modality.Default);

        defRules.addSingleFrameTags(Tag.ImageType, null);
        defRules.addSingleFrameTags(Tag.SOPClassUID, null);
        defRules.addSingleFrameTags(Tag.ContrastBolusAgent, null);
        defRules.addMultiFrameTags(Tag.ImageType, null);
        defRules.addMultiFrameTags(Tag.SOPInstanceUID, null);
        defRules.addMultiFrameTags(Tag.FrameType, null);
        defRules.addMultiFrameTags(Tag.FrameAcquisitionNumber, null);
        defRules.addMultiFrameTags(Tag.StackID, null);
        rules.put(defRules.getModality(), defRules);

        SplittingModalityRules ctRules = new SplittingModalityRules(Modality.CT, defRules);
        ctRules.addSingleFrameTags(Tag.ConvolutionKernel, null);
        ctRules.addSingleFrameTags(Tag.GantryDetectorTilt, null);
        ctRules.addSingleFrameTags(TagW.ImageOrientationPlane, null);
        rules.put(ctRules.getModality(), ctRules);

        SplittingModalityRules ptRules = new SplittingModalityRules(Modality.PT, defRules);
        ptRules.addSingleFrameTags(Tag.ConvolutionKernel, null);
        ptRules.addSingleFrameTags(Tag.GantryDetectorTilt, null);
        rules.put(ptRules.getModality(), ptRules);

        SplittingModalityRules mrRules = new SplittingModalityRules(Modality.MR, defRules);
        mrRules.addSingleFrameTags(Tag.ScanningSequence, null);
        mrRules.addSingleFrameTags(Tag.SequenceVariant, null);
        mrRules.addSingleFrameTags(Tag.ScanOptions, null);
        mrRules.addSingleFrameTags(Tag.RepetitionTime, null);
        mrRules.addSingleFrameTags(Tag.EchoTime, null);
        mrRules.addSingleFrameTags(Tag.InversionTime, null);
        mrRules.addSingleFrameTags(Tag.FlipAngle, null);

        And allOf = new And();
        allOf.addChild(new DefaultCondition(TagD.get(Tag.ImageType), Condition.Type.notContainsIgnoreCase, "PROJECTION"));
        mrRules.addSingleFrameTags(TagW.ImageOrientationPlane, allOf);
        rules.put(mrRules.getModality(), mrRules);
    }

    public SplittingModalityRules getSplittingModalityRules(Modality key, Modality defaultKey) {
        SplittingModalityRules val = rules.get(key);
        if (val == null) {
            val = rules.get(defaultKey);
        }
        return val;
    }

}
