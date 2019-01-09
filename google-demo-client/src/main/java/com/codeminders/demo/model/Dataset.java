package com.codeminders.demo.model;

import java.util.Objects;

public class Dataset {

    private Location parent;

    private final String name;

    public Dataset(Location parent, String name) {
        this.parent = parent;
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Location getParent() {
        return parent;
    }

    public ProjectDescriptor getProject() {
        return parent.getParent();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Dataset dataset = (Dataset) o;
        return Objects.equals(parent, dataset.parent) &&
                Objects.equals(name, dataset.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parent, name);
    }
}
