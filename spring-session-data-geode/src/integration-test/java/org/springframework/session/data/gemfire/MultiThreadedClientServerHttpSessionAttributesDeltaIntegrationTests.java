/*
 * Copyright 2020 the original author or authors.
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
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalStateException;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.data.gemfire.config.annotation.CacheServerApplication;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ObjectUtils;

/**
 * Integration tests containing test cases asserting the the proper behavior of concurrently accessing a {@link Session}
 * using Spring Session backed by Apache Geode or Pivotal GemFire in a Multi-Threaded context.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.1.2
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(
	classes = MultiThreadedClientServerHttpSessionAttributesDeltaIntegrationTests.GemFireClientConfiguration.class
)
public class MultiThreadedClientServerHttpSessionAttributesDeltaIntegrationTests
		extends AbstractGemFireIntegrationTests {

	@BeforeClass
	public static void startGemFireServer() throws IOException {
		startGemFireServer(GemFireServerConfiguration.class);
	}

	@Test
	public void multiThreadedConcurrentSessionOperationsAreCorrect() throws Throwable {
		TestFramework.runOnce(new MultiThreadedConcurrentSessionOperationsTestCase(this));
	}

	@SuppressWarnings("unused")
	public static final class MultiThreadedConcurrentSessionOperationsTestCase extends MultithreadedTestCase {

		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		private final MultiThreadedClientServerHttpSessionAttributesDeltaIntegrationTests testInstance;

		public MultiThreadedConcurrentSessionOperationsTestCase(
				MultiThreadedClientServerHttpSessionAttributesDeltaIntegrationTests testInstance) {

			this.testInstance = testInstance;
		}

		private Session commit(Session session) {

			return Optional.ofNullable(this.testInstance.getSessionRepository())
				.filter(AbstractGemFireOperationsSessionRepository.class::isInstance)
				.map(AbstractGemFireOperationsSessionRepository.class::cast)
				.map(it -> it.commit(session))
				.orElse(session);
		}

		private Session findById(String id) {
			return this.testInstance.get(id);
		}

		private boolean hasDelta(Session session) {

			return Optional.ofNullable(session)
				.filter(GemFireSession.class::isInstance)
				.map(GemFireSession.class::cast)
				.map(GemFireSession::hasDelta)
				.orElseThrow(() ->
					newIllegalStateException("Incompatible Session Type [%s]", ObjectUtils.nullSafeClassName(session)));
		}

		private Session newSession() {
			return this.testInstance.createSession();
		}

		private <T extends Session> T save(T session) {
			return this.testInstance.save(this.testInstance.touch(session));
		}

		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Session session = newSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			save(session);

			this.sessionId.set(session.getId());

			waitForTick(2);
			assertTick(2);

			assertThat(session.getAttributeNames()).isEmpty();

			session.setAttribute("attributeOne", "foo");
			session.setAttribute("attributeTwo", "bar");

			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("bar");
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");

			save(session);
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			waitForTick(2);
			assertTick(2);

			assertThat(session.getAttributeNames()).isEmpty();

			session.setAttribute("attributeThree", "baz");

			assertThat(session.getAttributeNames()).containsOnly("attributeThree");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("baz");

			save(session);

			waitForTick(4);
			assertTick(4);

			assertThat(session.getAttributeNames()).containsOnly("attributeThree");

			session.setAttribute("attributeFour", "qux");

			assertThat(session.getAttributeNames()).containsOnly("attributeThree", "attributeFour");
			assertThat(session.<String>getAttribute("attributeFour")).isEqualTo("qux");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("baz");

			save(session);
		}

		public void thread3() {

			Thread.currentThread().setName("User Session Four");

			waitForTick(3);
			assertTick(3);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo", "attributeThree");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("bar");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("baz");

			waitForTick(4);
			assertTick(4);

			session.setAttribute("attributeThree", "bazatch");
			session.removeAttribute("attributeTwo");

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeThree");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(session.<String>getAttribute("attributeTwo")).isNull();
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("bazatch");

			save(session);
		}

		public void thread4() {

			Thread.currentThread().setName("User Session Four");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			waitForTick(2);
			assertTick(2);

			session.setLastAccessedTime(session.getLastAccessedTime().plusSeconds(1));

			assertThat(hasDelta(session)).isTrue();

			save(session);

			waitForTick(3);
			assertTick(3);

			assertThat(hasDelta(session)).isFalse();

			save(session);
		}

		@Override
		public void finish() {

			super.finish();

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeThree", "attributeFour");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(session.<String>getAttribute("attributeTwo")).isNull();
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("bazatch");
			assertThat(session.<String>getAttribute("attributeFour")).isEqualTo("qux");
		}
	}

	@ClientCacheApplication(logLevel = "error", subscriptionEnabled = true)
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.PROXY,
		poolName = "DEFAULT",
		regionName = "Sessions",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class GemFireClientConfiguration { }

	@CacheServerApplication(
		name = "MultiThreadedClientServerHttpSessionAttributesDeltaIntegrationTests",
		logLevel = "error"
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
