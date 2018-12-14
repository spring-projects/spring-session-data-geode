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

package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalStateException;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import edu.umd.cs.mtc.TestFramework;

import org.apache.geode.DataSerializer;
import org.apache.geode.cache.DataPolicy;
import org.apache.geode.cache.GemFireCache;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientRegionShortcut;
import org.apache.geode.cache.client.Pool;
import org.apache.geode.cache.client.PoolManager;
import org.apache.geode.internal.InternalDataSerializer;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * The ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(
	classes = ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests.GemFireClientConfiguration.class
)
@SuppressWarnings("unused")
public class ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests
		extends AbstractConcurrentSessionOperationsIntegrationTests {

	private static final String GEMFIRE_LOG_LEVEL = "error";

	@Before
	public void setup() {

		GemFireCache cache = getGemFireCache();

		assertThat(cache).isNotNull();
		assertThat(cache.getCopyOnRead()).isFalse();
		assertThat(cache.getPdxSerializer()).isNull();
		assertThat(cache.getPdxReadSerialized()).isFalse();

		Pool defaultPool = PoolManager.find("DEFAULT");

		assertThat(defaultPool).isNotNull();
		assertThat(defaultPool.getSubscriptionEnabled()).isTrue();

		Region<Object, Session> sessions = cache.getRegion("Sessions");

		assertThat(sessions).isNotNull();
		assertThat(sessions.getName()).isEqualTo("Sessions");
		assertThat(sessions.getAttributes()).isNotNull();
		assertThat(sessions.getAttributes().getDataPolicy()).isEqualTo(DataPolicy.NORMAL);
	}

	@Test
	public void concurrentCachedSessionOperationsAreCorrect() throws Throwable {
		TestFramework.runOnce(new ConcurrentCachedSessionOperationsTestCase(this));
	}

	@Test
	public void regionPutWithNonDirtySessionResultsInInefficientIncorrectBehavior() throws Throwable {
		TestFramework.runOnce(new RegionPutWithNonDirtySessionTestCase(this));
	}

	// Tests that 2 Threads share the same Session object reference and therefore see's each other's changes.
	public static class ConcurrentCachedSessionOperationsTestCase extends AbstractConcurrentSessionOperationsTestCase {

		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		public ConcurrentCachedSessionOperationsTestCase(
				ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests testInstance) {

			super(testInstance);
		}

		@Override
		public void initialize() {

			Instant beforeCreationTime = Instant.now();

			Session session = newSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.getCreationTime()).isAfterOrEqualTo(beforeCreationTime);
			assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			save(session);

			this.sessionId.set(session.getId());
		}

		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Instant beforeLastAccessedTime = Instant.now();

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(beforeLastAccessedTime);
			assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			save(session);

			waitForTick(2);
			assertTick(2);

			// modify the Session without saving
			session.setAttribute("attributeOne", "one");
			session.setAttribute("attributeTwo", "two");
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Instant beforeLastAccessedTime = Instant.now();

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(beforeLastAccessedTime);
			assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			waitForTick(3);
			assertTick(3);

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("two");
		}
	}

	// Tests that DataSerializer.toData(..) is called twice; once for when the Session is new
	// and again when Region.put(..) is called with a Session having no delta/no changes.
	public static class RegionPutWithNonDirtySessionTestCase extends AbstractConcurrentSessionOperationsTestCase {

		private static final String DATA_SERIALIZER_NOT_FOUND_EXCEPTION_MESSAGE =
			"No DataSerializer was found capable of de/serializing Sessions";

		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		private final DataSerializer sessionSerializer;

		private final Region<Object, Session> sessions;

		public RegionPutWithNonDirtySessionTestCase(
				ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests testInstance) {

			super(testInstance);

			this.sessions = testInstance.getSessionRegion();
			this.sessionSerializer = reregisterDataSerializer(resolveDataSerializer());
		}

		private DataSerializer resolveDataSerializer() {

			return Arrays.stream(nullSafeArray(InternalDataSerializer.getSerializers(), DataSerializer.class))
				.filter(this.sessionSerializerFilter())
				.findFirst()
				.map(Mockito::spy)
				.orElseThrow(() -> newIllegalStateException(DATA_SERIALIZER_NOT_FOUND_EXCEPTION_MESSAGE));
		}

		private Predicate<? super DataSerializer> sessionSerializerFilter() {

			return dataSerializer -> {

				boolean isSessionSerializer = dataSerializer instanceof SessionSerializer;

				if (!isSessionSerializer) {
					isSessionSerializer =
						Arrays.stream(nullSafeArray(dataSerializer.getSupportedClasses(), Class.class))
							.filter(Objects::nonNull)
							.anyMatch(Session.class::isAssignableFrom);
				}

				return isSessionSerializer;
			};
		}

		private DataSerializer reregisterDataSerializer(DataSerializer dataSerializer) {

			InternalDataSerializer.unregister(dataSerializer.getId());
			InternalDataSerializer._register(dataSerializer, false);

			return dataSerializer;
		}

		private Session get(String id) {
			return this.sessions.get(id);
		}

		private void put(Session session) {

			this.sessions.put(session.getId(), session);

			if (session instanceof GemFireSession) {
				((GemFireSession) session).commit();
			}
		}

		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Session session = newSession();

			assertThat(session).isInstanceOf(GemFireSession.class);
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			session.setAttribute("attributeOne", "testOne");
			session.setAttribute("attributeTwo", "testTwo");

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(((GemFireSession) session).hasDelta()).isTrue();

			put(session);

			assertThat(((GemFireSession) session).hasDelta()).isFalse();

			// Reload to (fully) deserialize Session
			Session loadedSession = get(session.getId());

			assertThat(loadedSession).isInstanceOf(GemFireSession.class);
			assertThat(loadedSession.getId()).isEqualTo(session.getId());

			getSessionRepository().commit(loadedSession);

			this.sessionId.set(session.getId());
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Session session = get(this.sessionId.get());

			assertThat(session).isInstanceOf(GemFireSession.class);
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
			assertThat(((GemFireSession) session).hasDelta()).isFalse();

			put(session);
		}

		@Override
		public void finish() {

			Session session = get(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

			try {

				// The first Region.get(key) causes a deserialization (???)
				verify(this.sessionSerializer, times(1)).fromData(any(DataInput.class));

				verify(this.sessionSerializer, times(2))
					.toData(isA(GemFireSession.class), isA(DataOutput.class));
			}
			catch (ClassNotFoundException | IOException ignore) { }
		}
	}

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(GemFireServerConfiguration.class);
	}

	// Tests fail when 'copyOnRead' is set to 'true'!
	//@ClientCacheApplication(copyOnRead = true, logLevel = GEMFIRE_LOG_LEVEL, subscriptionEnabled = true)
	@ClientCacheApplication(logLevel = GEMFIRE_LOG_LEVEL, subscriptionEnabled = true)
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.CACHING_PROXY,
		poolName = "DEFAULT",
		regionName = "Sessions",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireClientConfiguration { }

	@CacheServerApplication(
		name = "ConcurrentSessionOperationsUsingClientCachingProxyRegionIntegrationTests",
		logLevel = GEMFIRE_LOG_LEVEL
	)
	@EnableGemFireHttpSession(
		regionName = "Sessions",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireServerConfiguration {

		public static void main(String[] args) {

			AnnotationConfigApplicationContext applicationContext =
				new AnnotationConfigApplicationContext(GemFireServerConfiguration.class);

			applicationContext.registerShutdownHook();
		}
	}
}
