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
import java.net.URLClassLoader;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.data.gemfire.tests.integration.ForkingClientServerIntegrationTestsSupport;
import org.springframework.data.gemfire.tests.process.ProcessWrapper;
import org.springframework.data.gemfire.util.CollectionUtils;
import org.springframework.lang.NonNull;
import org.springframework.session.web.context.AbstractHttpSessionApplicationInitializer;
import org.springframework.util.StringUtils;

import io.github.classgraph.ClassGraph;
import sample.server.GemFireServer;

// tag::class[]
public class Initializer extends AbstractHttpSessionApplicationInitializer { // <1>

	public Initializer() {
		super(ApacheGeodeServerRunner.onStartup(ClientConfig.class)); // <2>
	}

	static class ApacheGeodeServerRunner extends ForkingClientServerIntegrationTestsSupport {

		static @NonNull Class<?> onStartup(@NonNull Class<?> clientConfigurationType) {

			try {

				//System.err.printf("JAVA CLASSPATH [%s]%n", javaSystemClasspath());
				//System.err.printf("THREAD CONTEXT CLASSLOADER CLASSPATH [%s]%n", contextClassLoaderClasspath());
				//System.err.printf("CLASS-GRAPH CLASSLOADER CLASSPATH [%s]%n", classGraphClassLoaderClasspath());

				registerGeodeServerRuntimeShutdownHook(
					startGeodeServer(classGraphClasspath(), GemFireServer.class));

				return clientConfigurationType;
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
// end::class[]

		static String javaSystemClasspath() {
			return System.getProperty("java.class.path");
		}

		@SuppressWarnings("all")
		static String threadContextClassLoaderClasspath() {

			ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();

			System.err.printf("THREAD CONTEXT CLASSLOADER TYPE [%s]%n", contextClassLoader != null
				? contextClassLoader.getClass().getName() : null);

			List<String> classpathElements = Optional.ofNullable(Thread.currentThread())
				.map(Thread::getContextClassLoader)
				.filter(URLClassLoader.class::isInstance)
				.map(URLClassLoader.class::cast)
				.map(URLClassLoader::getURLs)
				.map(Stream::of)
				.orElseGet(Stream::empty)
				.filter(Objects::nonNull)
				.map(URL::getFile)
				.filter(StringUtils::hasText)
				.collect(Collectors.toList());

			return StringUtils.collectionToDelimitedString(classpathElements, System.getProperty("path.separator"));
		}
	}
}
