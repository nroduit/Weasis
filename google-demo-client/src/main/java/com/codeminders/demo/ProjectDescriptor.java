package com.codeminders.demo;

public class ProjectDescriptor {

	private String name;
	private String id;
	
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
}
