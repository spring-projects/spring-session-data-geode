/*
 * Copyright 2015-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package build

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class GemFireServerPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {

		GemFireServerTask gemFireServerTask = project.tasks.create('gemfireServer', GemFireServerTask)

		project.tasks.integrationTest.doLast {
			println 'Stopping Apache Geode Server...'
			gemFireServerTask.process?.destroy()
		}

		if (project.tasks.findByName("bootRun") != null) {
			project.tasks.integrationTest.dependsOn gemFireServerTask
			project.tasks.integrationTest.doFirst {
				systemProperties['spring.data.gemfire.cache.server.port'] = project.tasks.gemfireServer.port
				systemProperties['spring.data.gemfire.pool.servers'] = "localhost[${project.tasks.gemfireServer.port}]"
			}
		}

		project.tasks.findByName("prepareAppServerBeforeIntegrationTests")?.configure { task ->
			task.dependsOn gemFireServerTask
			task.doFirst {
				project.gretty {
					jvmArgs = [
						"-Dspring.data.gemfire.cache.server.port=${project.tasks.gemfireServer.port}",
						"-Dspring.data.gemfire.pool.servers=localhost[${project.tasks.gemfireServer.port}]"
					]
				}
			}
		}
	}

	static int availablePort() {
		new ServerSocket(0).withCloseable { it.localPort }
	}

	static class GemFireServerTask extends DefaultTask {

		@Internal
		def mainClassName = "sample.server.GemFireServer"

		@Internal
		def port

		@Internal
		def process

		@Input
		boolean debug

		static def isSet(value) {
			!(value == null || value.isEmpty())
		}

		static def init(value, defaultValue) {
			isSet(value) ? value : defaultValue
		}

		@TaskAction
		def run() {

			this.port = availablePort()
			println "Starting Apache Geode Server on port [$this.port]..."

			def out = this.debug ? System.err : new StringBuilder()
			def err = this.debug ? System.err : new StringBuilder()

			String classpath = project.sourceSets.main.runtimeClasspath.collect { it }.join(File.pathSeparator)
			String gemfireLogLevel = System.getProperty('spring.data.gemfire.cache.log-level', 'warning')
			String javaHome = init(System.getProperty("java.home"), System.getenv("JAVA_HOME"))

			javaHome = javaHome.endsWith(File.separator) ? javaHome : javaHome.concat(File.separator)

			String javaCommand = javaHome + "bin" + File.separator + "java"

			String[] commandLine = [
				javaCommand, '-server', '-ea', '-classpath', classpath,
				//"-Dgemfire.log-file=gemfire-server.log",
				"-Dgemfire.log-level=${gemfireLogLevel}",
				"-Dspring.data.gemfire.cache.server.port=${port}",
				this.mainClassName
			]

			//println commandLine

			this.process = commandLine.execute()

			project.tasks.findByName("appRun")?.configure { it.ext.process = this.process }

			this.process.consumeProcessOutput(out, err)
		}
	}
}
