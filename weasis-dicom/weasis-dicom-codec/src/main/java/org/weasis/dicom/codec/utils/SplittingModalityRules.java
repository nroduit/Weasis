package org.weasis.dicom.codec.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.data.SpecificCharacterSet;
import org.dcm4che3.data.Tag;
import org.dcm4che3.util.StringUtils;
import org.easymock.internal.matchers.InstanceOf;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.media.data.TagW.TagType;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.util.StringUtil;
import org.weasis.dicom.codec.DicomMediaIO;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.TagD.Level;
import org.weasis.dicom.codec.display.Modality;

public class SplittingModalityRules {
    private final Modality modality;
    private final List<Rule> singleFrameTags;
    private final List<Rule> multiFrameTags;

    private final SplittingModalityRules extendRules;

    public SplittingModalityRules(Modality modality) {
        this(modality, null);
    }

    public SplittingModalityRules(Modality modality, SplittingModalityRules extendRules) {
        this.modality = modality;
        this.extendRules = extendRules;
        this.singleFrameTags =
            extendRules == null ? new ArrayList<Rule>() : new ArrayList<>(extendRules.getSingleFrameRules());
        this.multiFrameTags =
            extendRules == null ? new ArrayList<Rule>() : new ArrayList<>(extendRules.getMultiFrameRules());
    }

    public Modality getModality() {
        return modality;
    }

    public List<Rule> getSingleFrameRules() {
        return singleFrameTags;
    }

    public List<Rule> getMultiFrameRules() {
        return multiFrameTags;
    }

    public SplittingModalityRules getExtendRules() {
        return extendRules;
    }

    public void addSingleFrameTags(TagW tag, Condition condition) {
        if (tag != null) {
            singleFrameTags.add(new Rule(tag, condition));
            DicomMediaIO.tagManager.addTag(tag, Level.INSTANCE);
        }
    }

    public void addMultiFrameTags(TagW tag, Condition condition) {
        if (tag != null) {
            multiFrameTags.add(new Rule(tag, condition));
            DicomMediaIO.tagManager.addTag(tag, Level.FRAME);
        }
    }

    public void addSingleFrameTags(int tagID, Condition condition) {
        addSingleFrameTags(TagD.getNullable(tagID), condition);
    }

    public void addMultiFrameTags(int tagID, Condition condition) {
        addMultiFrameTags(TagD.getNullable(tagID), condition);
    }

    public static class Rule {
        protected final TagW tag;
        protected final Condition condition;

        public Rule(TagW tag, Condition condition) {
            this.tag = tag;
            this.condition = condition;
        }

        public TagW getTag() {
            return tag;
        }

        public Condition getCondition() {
            return condition;
        }

        public boolean isTagValueMatching(MediaElement<?> seriesMedia, MediaElement<?> newMedia) {
            Object val1 = seriesMedia.getTagValue(tag);
            Object val2 = newMedia.getTagValue(tag);

            if (TagUtil.isEquals(val1, val2)) {
                return true;
            }

            if (condition != null) {
                // When all conditions match then the tag values not matching any more (media goes into a new
                // sub-series)
                return !condition.match(newMedia);
            }
            return false;
        }

    }

    public abstract static class Condition {
        public enum Type {
            equals, notEquals, equalsIgnoreCase, notEqualsIgnoreCase, contains, notContains, containsIgnoreCase,
            notContainsIgnoreCase
        };

        protected boolean not;

        public final Condition not() {
            this.not = !not;
            return this;
        }

        public abstract boolean match(MediaElement<?> media);

        public void addChild(Condition child) {
            throw new UnsupportedOperationException();
        }

        public boolean isEmpty() {
            return false;
        }

    }

    abstract static class CompositeCondition extends Condition {
        protected final ArrayList<Condition> childs = new ArrayList<>();

        @Override
        public void addChild(Condition child) {
            childs.add(child);
        }

        @Override
        public boolean isEmpty() {
            return childs.isEmpty();
        }
    }

    public static class And extends CompositeCondition {
        @Override
        public boolean match(MediaElement<?> media) {
            for (Condition child : childs) {
                if (!child.match(media))
                    return not;
            }
            return !not;
        }
    }

    public static class Or extends CompositeCondition {
        @Override
        public boolean match(MediaElement<?> media) {
            for (Condition child : childs) {
                if (child.match(media))
                    return !not;
            }
            return not;
        }
    }

    public static class DefaultCondition extends Condition {
        final TagW tag;
        final Condition.Type type;
        final Object object;

        public DefaultCondition(TagW tag, Type type, String value) {
            this.tag = tag;
            this.type = type;
            this.object = readValue(value);
        }

        private Object readValue(String value) {
            if (!StringUtil.hasText(value)) {
                return null;
            }

            Object val;
            int vm = tag.getValueMultiplicity();
            TagType tt = tag.getType();
            if (TagType.STRING.equals(tt) || TagType.TEXT.equals(tt) || TagType.URI.equals(tt)
                || TagType.PERSON_NAME.equals(tt) || TagType.PERIOD.equals(tt)) {
                if (vm > 1) {
                    val = toStrings(value);
                    ;
                } else {
                    val = value;
                }
            } else if (TagType.DATE.equals(tt)) {
                if (vm > 1) {
                    String[] ss = toStrings(value);
                    Date[] is = new Date[ss.length];
                    for (int i = 0; i < is.length; i++) {
                        is[i] = TagUtil.getDicomDate(value);
                    }
                    val = is;
                } else {
                    val = TagUtil.getDicomDate(value);
                }
            } else if (TagType.TIME.equals(tt)) {
                if (vm > 1) {
                    String[] ss = toStrings(value);
                    Date[] is = new Date[ss.length];
                    for (int i = 0; i < is.length; i++) {
                        is[i] = TagUtil.getDicomTime(value);
                    }
                    val = is;
                } else {
                    val = TagUtil.getDicomTime(value);
                }
            } else if (TagType.DATETIME.equals(tt)) {
                if (vm > 1) {
                    String[] ss = toStrings(value);
                    Date[] is = new Date[ss.length];
                    for (int i = 0; i < is.length; i++) {
                        is[i] = TagUtil.dateTime(TagUtil.getDicomDate(value), TagUtil.getDicomTime(value));
                    }
                    val = is;
                } else {
                    val = TagUtil.dateTime(TagUtil.getDicomDate(value), TagUtil.getDicomTime(value));
                }
            } else if (TagType.INTEGER.equals(tt)) {
                if (vm > 1) {
                    String[] ss = toStrings(value);
                    int[] ds = new int[ss.length];
                    for (int i = 0; i < ds.length; i++) {
                        String s = ss[i];
                        ds[i] = (s != null && !s.isEmpty()) ? StringUtils.parseIS(s) : 0;
                    }
                    val = ds;
                } else {
                    val = StringUtils.parseIS(value);
                }
            } else if (TagType.FLOAT.equals(tt)) {
                if (vm > 1) {
                    String[] ss = toStrings(value);
                    float[] ds = new float[ss.length];
                    for (int i = 0; i < ds.length; i++) {
                        String s = ss[i];
                        ds[i] = (s != null && !s.isEmpty()) ? (float) StringUtils.parseDS(s) : Float.NaN;
                    }
                    val = ds;
                } else {
                    val = (float) StringUtils.parseDS(value);
                }
            } else if (TagType.DOUBLE.equals(tt)) {
                if (vm > 1) {
                    String[] ss = toStrings(value);
                    double[] ds = new double[ss.length];
                    for (int i = 0; i < ds.length; i++) {
                        String s = ss[i];
                        ds[i] = (s != null && !s.isEmpty()) ? StringUtils.parseDS(s) : Double.NaN;
                    }
                    val = ds;
                } else {
                    val = StringUtils.parseDS(value);
                }
            } else if (TagType.SEQUENCE.equals(tt)) {
                val = value;
            } else {
                val = value.getBytes();
            }

            return val;
        }

        private String[] toStrings(String val) {
            return StringUtils.split(val, '\\');
        }

        @Override
        public boolean match(MediaElement<?> media) {
            Object value = media.getTagValue(tag);

            if (Condition.Type.equals.equals(type)) {
                return TagUtil.isEquals(value, object);
            } else if (Condition.Type.notEquals.equals(type)) {
                return !TagUtil.isEquals(value, object);
            }

            String str = object == null ? null : object.toString();
            switch (type) {
                case equalsIgnoreCase:
                    return TagUtil.isEquals(value, object, true);
                case notEqualsIgnoreCase:
                    return !TagUtil.isEquals(value, object, true);
                case contains:
                    return TagUtil.isContaining(value, str, false);
                case notContains:
                    return !TagUtil.isContaining(value, str, false);
                case containsIgnoreCase:
                    return TagUtil.isContaining(value, str, true);
                case notContainsIgnoreCase:
                    return !TagUtil.isContaining(value, str, true);
                default:
                    break;
            }
            return false;
        }
    }
}