/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.data.gemfire;

import static java.util.Arrays.stream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.junit.Before;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.CacheClosedException;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.ClientCacheFactory;
import org.apache.geode.cache.query.Index;
import org.apache.geode.cache.server.CacheServer;
import org.apache.geode.internal.InternalDataSerializer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.data.gemfire.tests.integration.IntegrationTestsSupport;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.util.StringUtils;

/**
 * {@link AbstractGemFireIntegrationTests} is an abstract base class encapsulating common functionality
 * for writing Spring Session for Apache Geode & Pivotal GemFire integration tests.
 *
 * @author John Blum
 * @since 1.1.0
 * @see org.apache.geode.DataSerializer
 * @see org.apache.geode.cache.DataPolicy
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.GemFireCache
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.query.Index
 * @see org.apache.geode.cache.server.CacheServer
 * @see org.springframework.data.gemfire.tests.integration.IntegrationTestsSupport
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 */
@SuppressWarnings("unused")
public abstract class AbstractGemFireIntegrationTests extends IntegrationTestsSupport {

	protected static final boolean DEFAULT_ENABLE_QUERY_DEBUGGING = false;

	protected static final boolean GEMFIRE_QUERY_DEBUG =
		Boolean.getBoolean("spring.session.data.gemfire.query.debug");

	protected static final int DEFAULT_GEMFIRE_SERVER_PORT = CacheServer.DEFAULT_PORT;

	protected static final long DEFAULT_WAIT_DURATION = TimeUnit.SECONDS.toMillis(20);
	protected static final long DEFAULT_WAIT_INTERVAL = 500L;

	protected static final File WORKING_DIRECTORY = new File(System.getProperty("user.dir"));

	protected static final String DEFAULT_PROCESS_CONTROL_FILENAME = "process.ctl";

	protected static final String GEMFIRE_LOG_FILE_NAME =
		System.getProperty("spring.session.data.gemfire.log-file", "gemfire-server.log");

	protected static final String GEMFIRE_LOG_LEVEL =
		System.getProperty("spring.session.data.gemfire.log-level", "error");

	@Autowired(required = false)
	protected GemFireCache gemfireCache;

	@Autowired(required = false)
	protected GemFireOperationsSessionRepository gemfireSessionRepository;

	@Autowired(required = false)
	protected SessionRepository<Session> sessionRepository;

	@Before
	public void setup() {

		System.setProperty("gemfire.Query.VERBOSE", String.valueOf(isQueryDebuggingEnabled()));

		this.sessionRepository = this.sessionRepository != null
			? this.sessionRepository : this.gemfireSessionRepository;
	}

	protected static String buildClassPathContainingJarFiles(String... jarFilenames) {

		StringBuilder classpath = new StringBuilder();

		stream(nullSafeArray(jarFilenames, String.class))
			.map(AbstractGemFireIntegrationTests::findJarInClassPath)
			.forEach(classpathEntry -> classpathEntry
				.filter(StringUtils::hasText)
				.ifPresent(it -> {

					if (classpath.length() > 0) {
						classpath.append(File.pathSeparator);
					}

					classpath.append(it);
				}));

		return classpath.toString();
	}

	private static Optional<URL> findClassInFileSystem(Class<?> type) {

		return Optional.ofNullable(type)
			.map(AbstractGemFireIntegrationTests::toResourceName)
			.map(resourceName -> type.getClassLoader().getResource(resourceName));
	}

	private static Optional<String> findJarInClassPath(String jarFilename) {

		String[] javaClassPath = System.getProperty("java.class.path").split(File.pathSeparator);

		return stream(nullSafeArray(javaClassPath, String.class))
			.filter(element -> element.contains(jarFilename))
			.findFirst();
	}

	private static String toResourceName(Class<?> type) {
		return type.getName().replaceAll("\\.", "/").concat(".class");
	}

	protected static File createDirectory(String pathname) {

		File directory = new File(WORKING_DIRECTORY, pathname);

		assertThat(directory.isDirectory() || directory.mkdirs())
			.as(String.format("Failed to create directory [%s]", directory))
			.isTrue();

		directory.deleteOnExit();

		return directory;
	}

	protected static List<String> createJavaProcessCommandLine(Class<?> type, String... args) {
		return createJavaProcessCommandLine(System.getProperty("java.class.path"), type, args);
	}

	protected static List<String> createJavaProcessCommandLine(String classpath, Class<?> type, String... args) {

		List<String> commandLine = new ArrayList<>();

		String javaHome = System.getProperty("java.home");
		String javaExe = new File(new File(javaHome, "bin"), "java").getAbsolutePath();

		commandLine.add(javaExe);
		commandLine.add("-server");
		commandLine.add("-ea");
		commandLine.add(String.format("-Dgemfire.log-file=%1$s", GEMFIRE_LOG_FILE_NAME));
		commandLine.add(String.format("-Dgemfire.log-level=%1$s", GEMFIRE_LOG_LEVEL));
		commandLine.add(String.format("-Dgemfire.Query.VERBOSE=%1$s", GEMFIRE_QUERY_DEBUG));
		commandLine.addAll(extractJvmArguments(args));
		commandLine.add("-classpath");
		commandLine.add(classpath);
		commandLine.add(type.getName());
		commandLine.addAll(extractProgramArguments(args));

		 //System.err.printf("Java process command-line is [%s]%n", commandLine);

		return commandLine;
	}

	private static List<String> extractJvmArguments(String... args) {

		return stream(args)
			.filter(arg -> arg.startsWith("-"))
			.collect(Collectors.toList());
	}

	private static List<String> extractProgramArguments(String... args) {

		return stream(args)
			.filter(arg -> !arg.startsWith("-"))
			.collect(Collectors.toList());
	}

	// Run Java Class in Directory with Arguments
	protected static String resolveClasspath(String classpath, Class<?> type) {

		return Optional.ofNullable(classpath)
			.filter(StringUtils::hasText)
			.filter(it -> type != null)
			.flatMap(it -> findClassInFileSystem(type))
			.map(url -> {
				try {
					return new File(url.toURI());
				}
				catch (URISyntaxException ignore) {
					return null;
				}
			})
			.map(File::getAbsolutePath)
			.map(pathname -> {

				int indexOfTypeName = pathname.indexOf(toResourceName(type));

				pathname = indexOfTypeName > -1 ? pathname.substring(0, indexOfTypeName) : pathname;
				pathname = pathname.endsWith(File.separator) ? pathname.substring(0, pathname.length() - 1) : pathname;

				return pathname;
			})
			.map(location -> classpath.concat(File.pathSeparator).concat(location))
			.orElse(classpath);
	}

	// Run Java Class in Directory with Arguments
	protected static Process run(Class<?> type, File directory, String... args) throws IOException {
		return run(createJavaProcessCommandLine(type, args), directory);
	}

	// Run Java Class using Classpath in Directory with Arguments
	protected static Process run(String classpath, Class<?> type, File directory, String... args) throws IOException {
		return run(createJavaProcessCommandLine(resolveClasspath(classpath, type), type, args), directory);
	}

	private static Process run(List<String> command, File directory) throws IOException {

		return new ProcessBuilder()
			.command(command)
			.directory(directory)
			.inheritIO()
			.redirectErrorStream(true)
			.start();
	}

	protected static void unregisterAllDataSerializers() {

		stream(nullSafeArray(InternalDataSerializer.getSerializers(), DataSerializer.class))
			.map(DataSerializer::getId)
			.forEach(InternalDataSerializer::unregister);
	}

	protected static boolean waitForCacheServerToStart(CacheServer cacheServer) {
		return waitForCacheServerToStart(cacheServer, DEFAULT_WAIT_DURATION);
	}

	protected static boolean waitForCacheServerToStart(CacheServer cacheServer, long duration) {
		return waitForCacheServerToStart(cacheServer.getBindAddress(), cacheServer.getPort(), duration);
	}

	protected static boolean waitForCacheServerToStart(String host, int port) {
		return waitForCacheServerToStart(host, port, DEFAULT_WAIT_DURATION);
	}

	protected static boolean waitForCacheServerToStart(final String host, final int port, long duration) {

		return waitOnCondition(new Condition() {

			AtomicBoolean connected = new AtomicBoolean(false);

			public boolean evaluate() {

				Socket socket = null;

				try {
					if (!this.connected.get()) {
						socket = new Socket(host, port);
						this.connected.set(true);
					}
				}
				catch (IOException ignore) {
				}
				finally {
					GemFireUtils.close(socket);
				}

				return connected.get();
			}
		}, duration);
	}

	// NOTE this method would not be necessary except Spring Sessions' build does not fork
	// the test JVM
	// for every test class.
	protected static boolean waitForClientCacheToClose() {
		return waitForClientCacheToClose(DEFAULT_WAIT_DURATION);
	}

	protected static boolean waitForClientCacheToClose(long duration) {

		try {

			ClientCache clientCache = ClientCacheFactory.getAnyInstance();

			clientCache.close();
			waitOnCondition(clientCache::isClosed, duration);

			return clientCache.isClosed();
		}
		catch (CacheClosedException ignore) {
			return true;
		}

	}

	protected static boolean waitForProcessToStart(Process process, File directory) {
		return waitForProcessToStart(process, directory, DEFAULT_WAIT_DURATION);
	}

	@SuppressWarnings("all")
	protected static boolean waitForProcessToStart(Process process, File directory, long duration) {

		File processControl = new File(directory, DEFAULT_PROCESS_CONTROL_FILENAME);

		waitOnCondition(new Condition() {
			public boolean evaluate() {
				return processControl.isFile();
			}
		}, duration);

		return process.isAlive();
	}

	protected static int waitForProcessToStop(Process process, File directory) {
		return waitForProcessToStop(process, directory, DEFAULT_WAIT_DURATION);
	}

	protected static int waitForProcessToStop(Process process, File directory, long duration) {

		long timeout = (System.currentTimeMillis() + duration);

		try {
			while (process.isAlive() && System.currentTimeMillis() < timeout) {
				if (process.waitFor(DEFAULT_WAIT_INTERVAL, TimeUnit.MILLISECONDS)) {
					return process.exitValue();
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return (process.isAlive() ? -1 : process.exitValue());
	}

	protected static boolean waitOnCondition(Condition condition) {
		return waitOnCondition(condition, DEFAULT_WAIT_DURATION);
	}

	@SuppressWarnings("all")
	protected static boolean waitOnCondition(Condition condition, long duration) {

		long timeout = (System.currentTimeMillis() + duration);

		try {
			while (!condition.evaluate() && System.currentTimeMillis() < timeout) {
				synchronized (condition) {
					TimeUnit.MILLISECONDS.timedWait(condition, DEFAULT_WAIT_INTERVAL);
				}
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		return condition.evaluate();
	}

	@SuppressWarnings("all")
	protected static File writeProcessControlFile(File path) throws IOException {

		assertThat(path != null && path.isDirectory()).isTrue();

		File processControl = new File(path, DEFAULT_PROCESS_CONTROL_FILENAME);

		assertThat(processControl.createNewFile()).isTrue();

		processControl.deleteOnExit();

		return processControl;
	}

	protected void assertValidSession(Session session) {

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();
	}

	protected void assertRegion(Region<?, ?> actualRegion, String expectedName, DataPolicy expectedDataPolicy) {

		assertThat(actualRegion).isNotNull();
		assertThat(actualRegion.getName()).isEqualTo(expectedName);
		assertThat(actualRegion.getFullPath()).isEqualTo(GemFireUtils.toRegionPath(expectedName));
		assertThat(actualRegion.getAttributes()).isNotNull();
		assertThat(actualRegion.getAttributes().getDataPolicy()).isEqualTo(expectedDataPolicy);
	}

	protected void assertIndex(Index index, String expectedExpression, String expectedFromClause) {

		assertThat(index).isNotNull();
		assertThat(index.getIndexedExpression()).isEqualTo(expectedExpression);
		assertThat(index.getFromClause()).isEqualTo(expectedFromClause);
	}

	protected void assertEntryIdleTimeout(Region<?, ?> region, ExpirationAction expectedAction, int expectedTimeout) {
		assertEntryIdleTimeout(region.getAttributes().getEntryIdleTimeout(), expectedAction, expectedTimeout);
	}

	protected void assertEntryIdleTimeout(ExpirationAttributes actualExpirationAttributes,
			ExpirationAction expectedAction, int expectedTimeout) {

		assertThat(actualExpirationAttributes).isNotNull();
		assertThat(actualExpirationAttributes.getAction()).isEqualTo(expectedAction);
		assertThat(actualExpirationAttributes.getTimeout()).isEqualTo(expectedTimeout);
	}

	protected boolean enableQueryDebugging() {
		return DEFAULT_ENABLE_QUERY_DEBUGGING;
	}

	protected boolean isQueryDebuggingEnabled() {
		return GEMFIRE_QUERY_DEBUG || enableQueryDebugging();
	}

	protected List<String> listRegions(GemFireCache gemfireCache) {

		return gemfireCache.rootRegions().stream()
			.map(Region::getFullPath)
			.collect(Collectors.toList());
	}

	protected SessionRepository<Session> getSessionRepository() {
		return this.sessionRepository;
	}

	@SuppressWarnings("unchecked")
	protected <T extends Session> T createSession() {

		T session = (T) getSessionRepository().createSession();

		assertThat(session).isNotNull();

		return session;
	}

	@SuppressWarnings("unchecked")
	protected <T extends Session> T createSession(String principalName) {

		GemFireOperationsSessionRepository.GemFireSession session = createSession();

		session.setPrincipalName(principalName);

		return (T) session;
	}

	@SuppressWarnings("all")
	protected <T extends Session> T delete(T session) {
		getSessionRepository().deleteById(session.getId());
		return session;
	}

	protected <T extends Session> T expire(T session) {
		session.setLastAccessedTime(Instant.ofEpochMilli(0L));
		return session;
	}

	@SuppressWarnings("unchecked")
	protected <T extends Session> T get(String sessionId) {
		return (T) getSessionRepository().findById(sessionId);
	}

	protected <T extends Session> T save(T session) {
		getSessionRepository().save(session);
		return session;
	}

	protected <T extends Session> T touch(T session) {
		session.setLastAccessedTime(Instant.now());
		return session;
	}

	/**
	 * The SessionEventListener class is a Spring {@link ApplicationListener} listening
	 * for Spring HTTP Session application events.
	 *
	 * @see org.springframework.context.ApplicationListener
	 * @see org.springframework.session.events.AbstractSessionEvent
	 */
	public static class SessionEventListener implements ApplicationListener<AbstractSessionEvent> {

		private volatile AbstractSessionEvent sessionEvent;

		@SuppressWarnings("unchecked")
		public <T extends AbstractSessionEvent> T getSessionEvent() {

			T sessionEvent = (T) this.sessionEvent;

			this.sessionEvent = null;

			return sessionEvent;
		}

		public void onApplicationEvent(AbstractSessionEvent event) {
			this.sessionEvent = event;
		}

		public <T extends AbstractSessionEvent> T waitForSessionEvent(long duration) {

			waitOnCondition(() -> (SessionEventListener.this.sessionEvent != null), duration);

			return getSessionEvent();
		}
	}

	/**
	 * The Condition interface defines a logical condition that must be satisfied before
	 * it is safe to proceed.
	 */
	@FunctionalInterface
	protected interface Condition {
		boolean evaluate();
	}
}
