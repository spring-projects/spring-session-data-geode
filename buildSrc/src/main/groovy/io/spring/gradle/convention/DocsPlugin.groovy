package io.spring.gradle.convention

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.PluginManager
import org.gradle.api.tasks.bundling.Zip

/**
 * Aggregates asciidoc, javadoc, and deploying of the docs into a single plugin
 */
class DocsPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {

		PluginManager pluginManager = project.getPluginManager();

		pluginManager.apply("org.asciidoctor.jvm.convert");
		pluginManager.apply("org.asciidoctor.jvm.pdf");
		pluginManager.apply(AsciidoctorConventionPlugin);
		pluginManager.apply(DeployDocsPlugin);
		pluginManager.apply(JavadocApiPlugin);

		Task docsZip = project.tasks.create('docsZip', Zip) {

			archiveBaseName = project.rootProject.name
			archiveClassifier = 'docs'
			group = 'Distribution'
			description = "Builds -${archiveClassifier} archive containing all Docs for deployment at docs.spring.io."
			dependsOn 'api', 'asciidoctor'

			from(project.tasks.api.outputs) {
				into 'api'
			}

			into 'docs'
			duplicatesStrategy 'exclude'
		}

		Task docs = project.tasks.create("docs") {
			group = 'Documentation'
			description 'Aggregator Task to generate all documentation.'
			dependsOn docsZip
		}

		project.tasks.assemble.dependsOn docs
	}
}
