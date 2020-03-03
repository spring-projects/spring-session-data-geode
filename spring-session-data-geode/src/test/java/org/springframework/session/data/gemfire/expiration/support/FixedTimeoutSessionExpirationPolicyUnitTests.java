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

package org.springframework.session.data.gemfire.expiration.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;

import org.springframework.session.Session;

/**
 * Unit tests for {@link FixedTimeoutSessionExpirationPolicy}.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see java.time.Instant
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.support.FixedTimeoutSessionExpirationPolicy
 * @since 2.1.0
 */
public class FixedTimeoutSessionExpirationPolicyUnitTests {

	@Test
	public void constructsFixedTimeoutSessionExpirationPolicy() {

		Duration fixedExpirationTimeout = Duration.ofSeconds(60L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedExpirationTimeout);

		assertThat(sessionExpirationPolicy).isNotNull();
		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedExpirationTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isNull();
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructsFixedTimeoutSessionExpirationPolicyWithNullFixedTimeout() {

		try {
			new FixedTimeoutSessionExpirationPolicy(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Fixed expiration timeout is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void determineExpirationTimeoutWithNoIdleTimeoutReturnsExpiredDuration() {

		Duration fixedTimeout = Duration.ofSeconds(30L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedTimeout);

		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isNull();

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(60L).toMillis()));

		Duration expirationTimeout = sessionExpirationPolicy.determineExpirationTimeout(mockSession).orElse(null);

		assertThat(expirationTimeout).isNotNull();
		assertThat(expirationTimeout).isLessThan(Duration.ZERO);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, never()).getLastAccessedTime();
	}

	@Test
	public void determineExpirationTimeoutWithNoIdleTimeoutReturnsNonExpiredDuration() {

		Duration fixedTimeout = Duration.ofSeconds(30L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedTimeout);

		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isNull();

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(15L).toMillis()));

		Duration expirationTimeout = sessionExpirationPolicy.determineExpirationTimeout(mockSession).orElse(null);

		assertThat(expirationTimeout).isNotNull();
		assertThat(expirationTimeout).isGreaterThan(Duration.ZERO);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, never()).getLastAccessedTime();
	}

	@Test
	public void determineExpirationTimeoutWithLongerIdleTimeoutReturnsExpiredDuration() {

		Duration fixedTimeout = Duration.ofSeconds(60L);
		Duration idleTimeout = Duration.ofSeconds(30L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedTimeout);

		sessionExpirationPolicy.setExpirationTimeout(idleTimeout);

		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isEqualTo(idleTimeout);

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(90L).toMillis()));

		when(mockSession.getLastAccessedTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(15L).toMillis()));

		Duration expirationTimeout = sessionExpirationPolicy.determineExpirationTimeout(mockSession).orElse(null);

		assertThat(expirationTimeout).isNotNull();
		assertThat(expirationTimeout).isLessThan(Duration.ZERO);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
	}

	@Test
	public void determineExpirationTimeoutWithLongerIdleTimeoutReturnsNonExpiredDuration() {

		Duration fixedTimeout = Duration.ofSeconds(60L);
		Duration idleTimeout = Duration.ofSeconds(30L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedTimeout);

		sessionExpirationPolicy.setExpirationTimeout(idleTimeout);

		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isEqualTo(idleTimeout);

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(45L).toMillis()));

		when(mockSession.getLastAccessedTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(10L).toMillis()));

		Duration expirationTimeout = sessionExpirationPolicy.determineExpirationTimeout(mockSession).orElse(null);

		assertThat(expirationTimeout).isNotNull();
		assertThat(expirationTimeout).isGreaterThan(Duration.ZERO);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
	}

	@Test
	public void determineExpirationTimeoutWithShorterTimeoutReturnsNoDuration() {

		Duration fixedTimeout = Duration.ofSeconds(60L);
		Duration idleTimeout = Duration.ofSeconds(30L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedTimeout);

		sessionExpirationPolicy.setExpirationTimeout(idleTimeout);

		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isEqualTo(idleTimeout);

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(30L).toMillis()));

		when(mockSession.getLastAccessedTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(15L).toMillis()));

		Duration expirationTimeout = sessionExpirationPolicy.determineExpirationTimeout(mockSession).orElse(null);

		assertThat(expirationTimeout).isNull();

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
	}

	@Test
	public void expirationTimeoutIsNotGreaterThanFixedTimeout() {

		Duration fixedTimeout = Duration.ofSeconds(30L);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedTimeout);

		assertThat(sessionExpirationPolicy.getFixedTimeout()).isEqualTo(fixedTimeout);
		assertThat(sessionExpirationPolicy.getIdleTimeout().orElse(null)).isNull();

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() + Duration.ofSeconds(30).toMillis()));

		Duration expirationTimeout = sessionExpirationPolicy.determineExpirationTimeout(mockSession).orElse(null);

		assertThat(expirationTimeout).isNotNull();
		assertThat(expirationTimeout).isEqualTo(fixedTimeout);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, never()).getLastAccessedTime();
	}
}
