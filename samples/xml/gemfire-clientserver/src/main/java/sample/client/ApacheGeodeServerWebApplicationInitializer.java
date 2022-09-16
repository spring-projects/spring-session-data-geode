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
package sample.client;

import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newRuntimeException;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Objects;

import jakarta.servlet.ServletContext;

import org.springframework.data.gemfire.tests.integration.ForkingClientServerIntegrationTestsSupport;
import org.springframework.data.gemfire.tests.process.ProcessWrapper;
import org.springframework.data.gemfire.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.WebApplicationInitializer;

import io.github.classgraph.ClassGraph;
import sample.server.GemFireServer;

/**
 * Spring {@link WebApplicationInitializer} used to bootstrap an Apache Geode server process.
 *
 * @author John Blum
 * @see sample.server.GemFireServer
 * @see org.springframework.data.gemfire.tests.integration.ForkingClientServerIntegrationTestsSupport
 * @see org.springframework.data.gemfire.tests.process.ProcessWrapper
 * @see org.springframework.web.WebApplicationInitializer
 * @since 3.0.0
 */
public class ApacheGeodeServerWebApplicationInitializer extends ForkingClientServerIntegrationTestsSupport
		implements WebApplicationInitializer {

	@Override
	public void onStartup(ServletContext servletContext) {

		try {

			//System.err.printf("JAVA CLASSPATH [%s]%n", javaSystemClasspath());
			//System.err.printf("THREAD CONTEXT CLASSLOADER CLASSPATH [%s]%n", contextClassLoaderClasspath());
			//System.err.printf("CLASS-GRAPH CLASSLOADER CLASSPATH [%s]%n", classGraphClassLoaderClasspath());

			registerGeodeServerRuntimeShutdownHook(
				startGeodeServer(classGraphClasspath(), GemFireServer.class));
		}
		catch(IOException cause) {
			throw newRuntimeException(cause, "Failed to start the Apache Geode Server");
		}
	}

	static String classGraphClasspath() {

		List<String> classpathElements = CollectionUtils.nullSafeList(new ClassGraph().getClasspathURLs()).stream()
			.filter(Objects::nonNull)
			.map(URL::getFile)
			.filter(StringUtils::hasText)
			.toList();

		return StringUtils.collectionToDelimitedString(classpathElements, System.getProperty("path.separator"));
	}

	static ProcessWrapper registerGeodeServerRuntimeShutdownHook(ProcessWrapper geodeServer) {

		if (geodeServer != null) {
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				ForkingClientServerIntegrationTestsSupport.stop(geodeServer);
			}, "Apache Geode Server Runtime Shutdown Hook"));
		}

		return geodeServer;
	}
}
