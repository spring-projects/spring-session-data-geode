package io.spring.gradle.convention;

import org.gradle.api.Project;

class Utils {

	private Utils() {}

	static String getProjectName(Project project) {

		String projectName = project.getRootProject().getName();

		if(projectName.endsWith("-build")) {
			projectName = projectName.substring(0, projectName.length() - "-build".length());
		}

		return projectName;
	}

	static boolean isMilestone(Project project) {
		return projectVersion(project).matches('^.*[.-]M\\d+$')
			|| projectVersion(project).matches('^.*[.-]RC\\d+$')
	}

	static boolean isRelease(Project project) {
		return !(isSnapshot(project) || isMilestone(project))
	}

	static boolean isSnapshot(Project project) {
		return projectVersion(project).matches('^.*([.-]BUILD)?-SNAPSHOT$')
	}

	private static String projectVersion(Project project) {
		return String.valueOf(project.getVersion());
	}
}
