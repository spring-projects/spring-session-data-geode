/*
 * Copyright 2017 the original author or authors.
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
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.support.FixedTimeoutSessionExpirationPolicy
 * @since 2.1.0
 */
public class FixedTimeoutSessionExpirationPolicyUnitTests {

	@Test
	public void constructsFixedTimeoutSessionExpirationPolicy() {

		Duration fixedExpirationTimeout = Duration.ofSeconds(60);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(fixedExpirationTimeout);

		assertThat(sessionExpirationPolicy).isNotNull();
		assertThat(sessionExpirationPolicy.getFixedExpirationTimeout()).isEqualTo(fixedExpirationTimeout);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructsFixedTimeoutSessionExpirationPolicyWithNullDuration() {

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
	public void expireAfterReturnsFutureDuration() {

		Duration expirationTimeout = Duration.ofSeconds(30);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(expirationTimeout);

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(15).toMillis()));

		Duration expireAfter = sessionExpirationPolicy.expireAfter(mockSession);

		assertThat(expireAfter).isNotNull();
		assertThat(expireAfter.getSeconds()).isLessThanOrEqualTo(15);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, never()).getLastAccessedTime();
	}

	@Test
	public void expireAfterReturnsZero() {

		Duration expirationTimeout = Duration.ofSeconds(30);

		FixedTimeoutSessionExpirationPolicy sessionExpirationPolicy =
			new FixedTimeoutSessionExpirationPolicy(expirationTimeout);

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofSeconds(60).toMillis()));

		Duration expireAfter = sessionExpirationPolicy.expireAfter(mockSession);

		assertThat(expireAfter).isEqualTo(Duration.ZERO);

		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, never()).getLastAccessedTime();
	}
}
