/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
			String gemfireLogLevel = System.getProperty('spring.data.gemfire.cache.log-level', 'warning')
			String javaHome = System.getProperty("java.home");

			javaHome = javaHome == null || javaHome.isEmpty() ? System.getenv("JAVA_HOME") : javaHome;
			javaHome = javaHome.endsWith(File.separator) ? javaHome : javaHome.concat(File.separator);

			String javaCommand = javaHome + "bin" + File.separator + "java";

			String[] commandLine = [
				javaCommand, '-server', '-ea', '-classpath', classpath,
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
