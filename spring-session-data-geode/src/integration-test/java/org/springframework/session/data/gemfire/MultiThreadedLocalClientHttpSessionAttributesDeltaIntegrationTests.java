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

import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.junit.runner.RunWith;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

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
 * @since 2.1.1
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
public class MultiThreadedLocalClientHttpSessionAttributesDeltaIntegrationTests
		extends AbstractGemFireIntegrationTests {

	@Test
	public void multiThreadedSessionOperationsAreCorrect() throws Throwable {
		TestFramework.runOnce(new MultiThreadedSessionOperationsTestCase(this));
	}

	@SuppressWarnings("unused")
	public static class MultiThreadedSessionOperationsTestCase extends MultithreadedTestCase {

		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		private final MultiThreadedLocalClientHttpSessionAttributesDeltaIntegrationTests testInstance;

		public MultiThreadedSessionOperationsTestCase(
				MultiThreadedLocalClientHttpSessionAttributesDeltaIntegrationTests testInstance) {

			this.testInstance = testInstance;
		}

		private Session findById(String id) {
			return this.testInstance.get(id);
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

			session.setAttribute("attributeOne", "foo");

			save(session);

			Session loadedSession = findById(session.getId());

			assertThat(loadedSession).isNotNull();
			assertThat(loadedSession).isNotSameAs(session);
			assertThat(loadedSession.getId()).isEqualTo(session.getId());
			assertThat(loadedSession.isExpired()).isFalse();
			assertThat(loadedSession.getAttributeNames()).containsExactly("attributeOne");
			assertThat(loadedSession.<String>getAttribute("attributeOne")).isEqualTo("foo");

			loadedSession.setAttribute("attributeTwo", "bar");

			save(loadedSession);

			this.sessionId.set(loadedSession.getId());

			waitForTick(2);
			assertTick(2);

			Session reloadedSession = findById(loadedSession.getId());

			assertThat(reloadedSession).isNotNull();
			assertThat(reloadedSession).isNotSameAs(loadedSession);
			assertThat(reloadedSession.getId()).isEqualTo(loadedSession.getId());
			assertThat(reloadedSession.isExpired()).isFalse();
			assertThat(reloadedSession.getAttributeNames())
				.containsOnly("attributeOne", "attributeTwo", "attributeThree");
			assertThat(reloadedSession.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(reloadedSession.<String>getAttribute("attributeTwo")).isEqualTo("bar");
			assertThat(reloadedSession.<String>getAttribute("attributeThree")).isEqualTo("baz");

			waitForTick(4);
			assertTick(4);

			Session endSession = findById(reloadedSession.getId());

			assertThat(endSession).isNotNull();
			assertThat(endSession.getId()).isEqualTo(reloadedSession.getId());
			assertThat(endSession.isExpired()).isFalse();
			assertThat(endSession.getAttributeNames()).containsOnly("attributeOne", "attributeThree");
			assertThat(endSession.getAttributeNames()).doesNotContain("attributeTwo");
			assertThat(endSession.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(endSession.<String>getAttribute("attributeTwo")).isNull();
			assertThat(endSession.<String>getAttribute("attributeThree")).isEqualTo("baz");
		}

		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("bar");

			session.setAttribute("attributeThree", "baz");

			save(session);

			waitForTick(4);
			assertTick(4);

			Session endSession = findById(session.getId());

			assertThat(endSession).isNotNull();
			assertThat(endSession.getId()).isEqualTo(session.getId());
			assertThat(endSession.isExpired()).isFalse();
			assertThat(endSession.getAttributeNames()).containsOnly("attributeOne", "attributeThree");
			assertThat(endSession.getAttributeNames()).doesNotContain("attributeTwo");
			assertThat(endSession.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(endSession.<String>getAttribute("attributeTwo")).isNull();
			assertThat(endSession.<String>getAttribute("attributeThree")).isEqualTo("baz");
		}

		public void thread3() {

			Thread.currentThread().setName("User Session Three");

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

			session.setAttribute("attributeTwo", null);

			save(session);
		}

		@Override
		public void finish() {

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeThree");
			assertThat(session.getAttributeNames()).doesNotContain("attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("foo");
			assertThat(session.<String>getAttribute("attributeTwo")).isNull();
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("baz");
		}
	}

	@ClientCacheApplication(logLevel = "error", copyOnRead = true)
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.LOCAL,
		poolName = "DEFAULT",
		sessionSerializerBeanName = GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME
	)
	static class TestConfiguration { }

}
