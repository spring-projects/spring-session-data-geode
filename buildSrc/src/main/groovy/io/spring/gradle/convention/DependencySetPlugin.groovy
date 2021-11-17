/*
 * Copyright 2002-2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.spring.gradle.convention;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin

/**
 * Adds sets of dependencies to make it easy to add a grouping of dependencies. The
 * dependencies added are:
 *
 * <ul>
 * <li>jstlDependencies</li>
 * <li>seleniumDependencies</li>
 * <li>slf4jDependencies</li>
 * </ul>
 *
 * @author Rob Winch
 */
class DependencySetPlugin implements Plugin<Project> {

	@Override
	void apply(Project project) {

		project.ext.jstlDependencies = [
			"javax.servlet.jsp.jstl:javax.servlet.jsp.jstl-api",
			"org.apache.taglibs:taglibs-standard-jstlel"
		]

		project.ext.seleniumDependencies = [
			"org.seleniumhq.selenium:htmlunit-driver",
			"org.seleniumhq.selenium:selenium-support"
		]

		project.ext.slf4jDependencies = [
			"org.slf4j:slf4j-api",
			"org.slf4j:jcl-over-slf4j",
			"org.slf4j:log4j-over-slf4j",
			"ch.qos.logback:logback-classic"
		]

		project.ext.testDependencies = [
			"junit:junit",
			"org.assertj:assertj-core",
			"org.mockito:mockito-core",
			"org.projectlombok:lombok",
			"org.springframework:spring-test",
			"edu.umd.cs.mtc:multithreadedtc"
		]

		project.plugins.withType(JavaPlugin) {
			project.dependencies {
				testImplementation project.testDependencies
			}
		}
	}
}
