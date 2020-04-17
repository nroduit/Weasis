/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec.utils;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.StringUtil;
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
            extendRules == null ? new ArrayList<>() : new ArrayList<>(extendRules.getSingleFrameRules());
        this.multiFrameTags =
            extendRules == null ? new ArrayList<>() : new ArrayList<>(extendRules.getMultiFrameRules());
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

        public boolean isTagValueMatching(MediaElement seriesMedia, MediaElement newMedia) {
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
        }

        protected boolean not;

        public final Condition not() {
            this.not = !not;
            return this;
        }

        public abstract boolean match(MediaElement media);

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
        public boolean match(MediaElement media) {
            for (Condition child : childs) {
                if (!child.match(media)) {
                    return not;
                }
            }
            return !not;
        }
    }

    public static class Or extends CompositeCondition {
        @Override
        public boolean match(MediaElement media) {
            for (Condition child : childs) {
                if (child.match(media)) {
                    return !not;
                }
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
            return tag.getValue(value);
        }

        @Override
        public boolean match(MediaElement media) {
            Object value = media.getTagValue(tag);

            if (Type.equals.equals(type)) {
                return TagUtil.isEquals(value, object);
            } else if (Type.notEquals.equals(type)) {
                return !TagUtil.isEquals(value, object);
            } else if (Type.equalsIgnoreCase.equals(type)) {
                return TagUtil.isEquals(value, object, true);
            } else if (Type.notEqualsIgnoreCase.equals(type)) {
                return !TagUtil.isEquals(value, object, true);
            }

            String str = null;
            if (object != null) {
                if (object.getClass().isArray() && Array.getLength(object) > 0) {
                    str = Array.get(object, 0).toString();
                } else {
                    str = object.toString();
                }
            }

            switch (type) {
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