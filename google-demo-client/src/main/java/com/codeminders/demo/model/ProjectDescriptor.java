package com.codeminders.demo.model;

import java.util.Objects;

public class ProjectDescriptor {

	private final String name;
	private final String id;
	
	public ProjectDescriptor(String name, String id) {
		this.name = name;
		this.id = id;
	}
	
	public String getName() {
		return name;
	}
	public String getId() {
		return id;
	}
	
	@Override
	public String toString() {
		return name;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		ProjectDescriptor that = (ProjectDescriptor) o;
		return Objects.equals(name, that.name) &&
				Objects.equals(id, that.id);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, id);
	}
}
