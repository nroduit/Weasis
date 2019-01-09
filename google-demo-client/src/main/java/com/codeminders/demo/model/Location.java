package com.codeminders.demo.model;

import java.util.Objects;

public class Location {

    private final ProjectDescriptor parent;

	private final String name;
	private final String id;
	
	public Location(ProjectDescriptor parent, String name, String id) {
	    this.parent = parent;
		this.name = name;
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	public String getId() {
		return id;
	}

    public ProjectDescriptor getParent() {
        return parent;
    }

    @Override
	public String toString() {
		return id;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		Location location = (Location) o;
		return Objects.equals(parent, location.parent) &&
				Objects.equals(name, location.name) &&
				Objects.equals(id, location.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(parent, name, id);
	}
}
