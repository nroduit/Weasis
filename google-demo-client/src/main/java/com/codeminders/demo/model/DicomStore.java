package com.codeminders.demo.model;

import java.util.Objects;

public class DicomStore {

    private final Dataset parent;

    private final String name;

    public DicomStore(Dataset parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Dataset getParent() {
        return parent;
    }

    public ProjectDescriptor getProject() {
        return parent.getProject();
    }

    public Location getLocation() {
        return parent.getParent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DicomStore that = (DicomStore) o;
        return Objects.equals(parent, that.parent) &&
                Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, name);
    }
}
