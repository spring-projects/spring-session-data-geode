/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.tests.util.IdentityHashCodeComparator;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionAttributesSerializer;
import org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * Multi-Threaded, Highly-Concurrent, {@link Session} data access operations integration test.
 *
 * @author John Blum
 * @see java.util.concurrent.ExecutorService
 * @see org.junit.Test
 * @see org.springframework.data.gemfire.config.annotation.CacheServerApplication
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.1.2
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(
	classes = MultiThreadedHighlyConcurrentClientServerHttpSessionAccessIntegrationTests.GemFireClientConfiguration.class
)
@SuppressWarnings("unused")
public class MultiThreadedHighlyConcurrentClientServerHttpSessionAccessIntegrationTests
		extends AbstractGemFireIntegrationTests {

	private static final boolean SESSION_REFERENCE_CHECKING_ENABLED = false;

	private static final int THREAD_COUNT = 180;
	private static final int WORKLOAD_SIZE = 10000;

	private static final String GEMFIRE_LOG_LEVEL = "error";

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(GemFireServerConfiguration.class);
	}

	private final AtomicInteger sessionReferenceComparisonCounter = new AtomicInteger(0);
	private final AtomicInteger threadCounter = new AtomicInteger(0);

	private final AtomicReference<String> sessionId = new AtomicReference<>(null);

	private final List<String> sessionAttributeNames = Collections.synchronizedList(new ArrayList<>(WORKLOAD_SIZE));

	private final Random random = new Random(System.currentTimeMillis());

	private final Set<Session> sessionIdentityHashCodes =
		Collections.synchronizedSet(new TreeSet<>(IdentityHashCodeComparator.INSTANCE));

	private final Set<Session> sessionReferences =
		Collections.synchronizedSet(new TreeSet<>((sessionOne, sessionTwo) -> sessionOne == sessionTwo ? 0
			: this.sessionReferenceComparisonCounter.incrementAndGet() % 2 == 0 ? -1 : 1));

	@Before
	public void assertGemFireConfiguration() {

		assertThat(this.gemfireCache).isNotNull();

		assertThat(this.gemfireCache.getPdxSerializer())
			.describedAs("Expected the configured PdxSerializer to be null; but was [%s]",
				ObjectUtils.nullSafeClassName(this.gemfireCache.getPdxSerializer()))
			.isNull();

		assertThat(this.sessions).isNotNull();
		assertThat(this.sessions.getAttributes()).isNotNull();

		assertThat(this.sessions.getAttributes().getDataPolicy())
			.describedAs("Expected Region [%s] DataPolicy of EMPTY; but was %s",
				this.sessions.getName(), this.sessions.getAttributes().getDataPolicy())
			.isEqualTo(DataPolicy.EMPTY);
	}

	@Before
	public void setupSession() {

		Instant beforeCreationTime = Instant.now();

		Session session = createSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(beforeCreationTime);
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.isExpired()).isFalse();
		assertThat(session.getAttributeNames()).isEmpty();

		this.sessionId.set(save(touch(session)).getId());
	}

	private void assertUniqueSessionReference(Session session) {

		if (SESSION_REFERENCE_CHECKING_ENABLED) {
			assertThat(this.sessionReferences.add(session))
				.describedAs("Session reference was not unique; size [%d]", this.sessionReferences.size())
				.isTrue();
		}
	}

	private ExecutorService newSessionWorkloadExecutor() {

		return Executors.newFixedThreadPool(THREAD_COUNT, runnable -> {

			Thread sessionThread = new Thread(runnable);

			sessionThread.setDaemon(true);
			sessionThread.setName(String.format("Session Thread %d", this.threadCounter.incrementAndGet()));
			sessionThread.setPriority(Thread.NORM_PRIORITY);

			return sessionThread;
		});
	}

	private Collection<Callable<Integer>> newSessionWorkloadTasks() {

		Collection<Callable<Integer>> sessionWorkloadTasks = new ArrayList<>(WORKLOAD_SIZE);

		for (int count = 0, readCount = 0; count < WORKLOAD_SIZE; count++, readCount = 3 * count) {

			sessionWorkloadTasks.add(count % 79 != 0
				? newAddSessionAttributeTask()
				: readCount % 237 != 0
				? newRemoveSessionAttributeTask()
				: newSessionReaderTask());
		}

		return sessionWorkloadTasks;
	}

	private Callable<Integer> newAddSessionAttributeTask() {

		return () -> {

			Instant beforeLastAccessedTime = Instant.now();

			Session session = get(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(beforeLastAccessedTime);
			assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(session.isExpired()).isFalse();
			assertUniqueSessionReference(session);

			String attributeName = UUID.randomUUID().toString();
			Object attributeValue = System.currentTimeMillis();

			session.setAttribute(attributeName, attributeValue);

			save(touch(session));

			this.sessionAttributeNames.add(attributeName);

			return 1;
		};
	}

	@SuppressWarnings("all")
	private Callable<Integer> newRemoveSessionAttributeTask() {

		return () -> {

			int returnValue = 0;

			Instant beforeLastAccessedTime = Instant.now();

			Session session = get(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(beforeLastAccessedTime);
			assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(session.isExpired()).isFalse();
			assertUniqueSessionReference(session);

			String attributeName = null;

			synchronized (this.sessionAttributeNames) {

				int size = this.sessionAttributeNames.size();

				if (size > 0) {

					int index = this.random.nextInt(size);

					attributeName = this.sessionAttributeNames.remove(index);
				}
			}

			if (session.getAttributeNames().contains(attributeName)) {
				session.removeAttribute(attributeName);
				returnValue = -1;
			}
			else {
				Optional.ofNullable(attributeName)
					.filter(StringUtils::hasText)
					.ifPresent(this.sessionAttributeNames::add);
			}

			save(touch(session));

			return returnValue;
		};
	}

	private Callable<Integer> newSessionReaderTask() {

		return () -> {

			Instant beforeLastAccessedTime = Instant.now();

			Session session = get(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(beforeLastAccessedTime);
			assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(session.isExpired()).isFalse();
			assertUniqueSessionReference(session);

			save(session);

			return 0;
		};
	}

	private <T> T safeFutureGet(Future<T> future) {

		try {
			return future.get();
		}
		catch (Exception cause) {
			throw new RuntimeException("Session Access Task Failed", cause);
		}
	}

	private int runSessionWorkload() throws InterruptedException {

		ExecutorService sessionWorkloadExecutor = newSessionWorkloadExecutor();

		try {

			List<Future<Integer>> sessionWorkloadTasksFutures =
				sessionWorkloadExecutor.invokeAll(newSessionWorkloadTasks());

			return sessionWorkloadTasksFutures.stream()
				.mapToInt(this::safeFutureGet)
				.sum();
		}
		finally {
			Optional.of(sessionWorkloadExecutor)
				.ifPresent(ExecutorService::shutdownNow);
		}
	}

	@Test
	public void concurrentSessionAccessIsCorrect() throws InterruptedException {

		int sessionAttributeCount = runSessionWorkload();

		assertThat(sessionAttributeCount).isEqualTo(this.sessionAttributeNames.size());
		//assertThat(SpyingDataSerializableSessionSerializer.getSerializationCount()).isEqualTo(1);

		Session session = get(this.sessionId.get());

		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo(this.sessionId.get());
		assertThat(session.getAttributeNames()).hasSize(sessionAttributeCount);
	}

	@ClientCacheApplication(copyOnRead = true, logLevel = GEMFIRE_LOG_LEVEL, subscriptionEnabled = true)
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.PROXY,
		poolName = "DEFAULT",
		regionName = "Sessions",
		sessionSerializerBeanName = "spyingSessionSerializer"
	)
	static class GemFireClientConfiguration {

		@Bean
		SpyingDataSerializableSessionSerializer spyingSessionSerializer() {
			return new SpyingDataSerializableSessionSerializer();
		}
	}

	@CacheServerApplication(
		name = "MultiThreadedHighlyConcurrentClientServerHttpSessionAccessIntegrationTests",
		logLevel = GEMFIRE_LOG_LEVEL
	)
	//@EnableLogging(logFile = "gemfire.log", logLevel = "debug")
	@EnableGemFireHttpSession(
		regionName = "Sessions",
		sessionSerializerBeanName = "spyingSessionSerializer"
	)
	static class GemFireServerConfiguration {

		public static void main(String[] args) {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(GemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();
		}

		@Bean
		SpyingDataSerializableSessionSerializer spyingSessionSerializer() {
			return new SpyingDataSerializableSessionSerializer();
		}
	}

	static class SpyingDataSerializableSessionSerializer
			extends AbstractDataSerializableSessionSerializer<GemFireSession> {

		private static final AtomicInteger serializationCount = new AtomicInteger(0);

		private final DataSerializableSessionSerializer sessionSerializer;

		static int getSerializationCount() {
			return serializationCount.get();
		}

		public SpyingDataSerializableSessionSerializer() {
			this.sessionSerializer = new DataSerializableSessionSerializer();
			DataSerializableSessionAttributesSerializer.register();
		}

		@Override
		public Class<?>[] getSupportedClasses() {
			return this.sessionSerializer.getSupportedClasses();
		}

		@Override
		@SuppressWarnings("unchecked")
		public void serialize(GemFireSession session, DataOutput dataOutput) {

			assertThat(session).isInstanceOf(DeltaCapableGemFireSession.class);
			assertThat(session.hasDelta()).isTrue();

			this.sessionSerializer.serialize(session, dataOutput);

			serializationCount.incrementAndGet();
		}

		@Override
		public GemFireSession deserialize(DataInput dataInput) {
			return this.sessionSerializer.deserialize(dataInput);
		}
	}
}
