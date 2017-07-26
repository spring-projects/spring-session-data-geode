package build

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction

class GemFireServerPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {

		project.tasks.create('gemfireServer', GemFireServerTask)

		project.tasks.integrationTest.doLast {
			println 'Stopping Apache Geode Server...'
			project.tasks.gemfireServer.process?.destroy()
//			project.tasks.gemfireServer.process?.destroyForcibly()
		}

		project.tasks.prepareAppServerForIntegrationTests {
			dependsOn project.tasks.gemfireServer
			doFirst {
				project.gretty {
					jvmArgs = [ "-Dspring.session.data.geode.cache.server.port=${project.tasks.gemfireServer.port}" ]
				}
			}
		}
	}

	static int availablePort() {
		new ServerSocket(0).withCloseable { socket ->
			socket.localPort
		}
	}

	static class GemFireServerTask extends DefaultTask {

		def mainClassName = "sample.ServerConfig"
		def port
		def process

		boolean debug

		@TaskAction
		def greet() {

			port = availablePort()
			println "Starting Apache Geode Server on port [$port]..."

			def out = debug ? System.err : new StringBuilder()
			def err = debug ? System.err : new StringBuilder()

			String classpath = project.sourceSets.main.runtimeClasspath.collect { it }.join(File.pathSeparator)
			String gemfireLogLevel = System.getProperty('spring.session.data.geode.log-level', 'warning')

			String[] commandLine = [
				'java', '-server', '-ea', '-classpath', classpath,
				//"-Dgemfire.log-file=gemfire-server.log",
				"-Dgemfire.log-level=" + gemfireLogLevel,
				"-Dspring.session.data.geode.cache.server.port=${port}",
				mainClassName
			]

			//println commandLine

			project.tasks.appRun.ext.process = process = commandLine.execute()

			process.consumeProcessOutput(out, err)
		}
	}
}
