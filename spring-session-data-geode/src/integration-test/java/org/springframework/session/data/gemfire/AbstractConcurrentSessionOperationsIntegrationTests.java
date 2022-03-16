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
package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalArgumentException;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;
import org.mockito.Mockito;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.util.ObjectUtils;

/**
 * Abstract base class encapsulating functionality common to all concurrent {@link Session} data access operation
 * based Integration Tests.
 *
 * @author John Blum
 * @see java.time.Instant
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @since 2.1.0
 */
public abstract class AbstractConcurrentSessionOperationsIntegrationTests extends AbstractGemFireIntegrationTests {

	@Test
	public void concurrentSessionOperationsAreCorrect() throws Throwable {
		TestFramework.runOnce(new ConcurrentSessionOperationsTestCase(this));
	}

	protected static class AbstractConcurrentSessionOperationsTestCase extends MultithreadedTestCase {

		private final AbstractConcurrentSessionOperationsIntegrationTests testInstance;

		private final GemFireOperationsSessionRepository sessionRepository;

		protected AbstractConcurrentSessionOperationsTestCase(
				@NonNull AbstractConcurrentSessionOperationsIntegrationTests testInstance) {

			assertThat(testInstance).describedAs("Test class instance is required").isNotNull();

			this.testInstance = testInstance;

			SessionRepository<?> sessionRepository = this.testInstance.getSessionRepository();

			this.sessionRepository = Optional.ofNullable(sessionRepository)
				.filter(GemFireOperationsSessionRepository.class::isInstance)
				.map(GemFireOperationsSessionRepository.class::cast)
				.map(Mockito::spy)
				.orElseThrow(() -> newIllegalArgumentException("Expected SessionRepository of type [%1$s]; but was [%2$s]",
					GemFireOperationsSessionRepository.class.getName(), ObjectUtils.nullSafeClassName(sessionRepository)));
		}

		@NonNull @SuppressWarnings("unused")
		protected AbstractConcurrentSessionOperationsIntegrationTests getTestInstance() {
			return this.testInstance;
		}

		@NonNull
		protected GemFireOperationsSessionRepository getSessionRepository() {
			return this.sessionRepository;
		}

		@Nullable
		protected Session findById(String id) {
			return getSessionRepository().findById(id);
		}

		@NonNull
		protected Session newSession() {
			return getSessionRepository().createSession();
		}

		@Nullable
		protected <T extends Session> T save(@Nullable T session) {
			getSessionRepository().save(session);
			return session;
		}
	}

	@SuppressWarnings("unused")
	public static class ConcurrentSessionOperationsTestCase extends AbstractConcurrentSessionOperationsTestCase {

		private final AtomicReference<Instant> lastAccessedTime = new AtomicReference<>(null);
		private final AtomicReference<String> sessionId = new AtomicReference<>(null);

		public ConcurrentSessionOperationsTestCase(AbstractConcurrentSessionOperationsIntegrationTests testInstance) {
			super(testInstance);
		}

		// Creator Thread
		@SuppressWarnings("rawtypes")
		public void thread1() {

			Thread.currentThread().setName("User Session One");

			assertTick(0);

			Session session = newSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isNotEmpty();
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).isEmpty();

			session.setAttribute("attributeOne", "testOne");
			session.setAttribute("attributeTwo", "testTwo");

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");

			save(session);

			this.sessionId.set(session.getId());

			waitForTick(4);
			assertTick(4);

			// Save Session with no changes/no delta
			assertThat(session instanceof GemFireSession && ((GemFireSession) session).hasDelta()).isFalse();

			save(session);
		}

		// Modifier (Attribute) Thread
		public void thread2() {

			Thread.currentThread().setName("User Session Two");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

			waitForTick(2);
			assertTick(2);

			session.setAttribute("attributeThree", "testThree");

			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo", "attributeThree");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("testThree");

			save(session);
		}

		// Modifier (Timestamp) Thread
		public void thread3() {

			Thread.currentThread().setName("User Session Three");

			waitForTick(1);
			assertTick(1);

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

			waitForTick(3);
			assertTick(3);

			this.lastAccessedTime.set(getTestInstance().forcedTouch(session).getLastAccessedTime());

			save(session);
		}

		@Override
		public void finish() {

			super.finish();

			Session session = findById(this.sessionId.get());

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(this.sessionId.get());
			assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo", "attributeThree");
			assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
			assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
			assertThat(session.<String>getAttribute("attributeThree")).isEqualTo("testThree");
			assertThat(session.isExpired()).isFalse();
			assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(this.lastAccessedTime.get());

			verify(this.getSessionRepository(), times(3)).doSave(eq(session));
		}
	}
}
