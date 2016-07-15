package org.weasis.core.ui.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlList;

import org.weasis.core.ui.model.utils.imp.DefaultUUID;

public class ReferencedImage extends DefaultUUID {
    private static final long serialVersionUID = 634321872759432378L;

    private List<Integer> frames;

    public ReferencedImage() {
        this(null, null);
    }

    public ReferencedImage(String uuid) {
        this(uuid, null);
    }

    public ReferencedImage(String uuid, List<Integer> frames) {
        super(uuid);
        setFrames(frames);
    }

    @XmlList
    @XmlAttribute(name = "frames", required = false)
    public List<Integer> getFrames() {
        return frames;
    }

    public void setFrames(List<Integer> frames) {
        this.frames = Optional.ofNullable(frames).orElse(new ArrayList<>());
    }

}
