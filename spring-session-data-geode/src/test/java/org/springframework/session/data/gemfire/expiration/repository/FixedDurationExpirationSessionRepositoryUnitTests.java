/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.session.data.gemfire.expiration.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * Unit tests {@link FixedDurationExpirationSessionRepository}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.expiration.repository.FixedDurationExpirationSessionRepository
 * @since 2.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class FixedDurationExpirationSessionRepositoryUnitTests {

	@Mock
	private SessionRepository<Session> mockSessionRepository;

	@Test
	public void constructsFixedDurationExpirationSessionRepository() {

		Duration expirationTimeout = Duration.ofMinutes(30L);

		FixedDurationExpirationSessionRepository<?> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, expirationTimeout);

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getDelegate()).isEqualTo(this.mockSessionRepository);
		assertThat(sessionRepository.getExpirationTimeout().orElse(null)).isEqualTo(expirationTimeout);
	}

	@Test
	public void constructsFixedDurationExpirationSessionRepositoryWithNullExpirationTimeout() {

		FixedDurationExpirationSessionRepository<?> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, null);

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.getDelegate()).isEqualTo(this.mockSessionRepository);
		assertThat(sessionRepository.getExpirationTimeout().orElse(null)).isNull();
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructFixedDurationExpirationSessionRepositoryWithNullSessionRepository() {

		try {
			new FixedDurationExpirationSessionRepository<>(null, Duration.ofMinutes(30L));
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("SessionRepository is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void createSessionCallsDelegate() {

		Session mockSession = mock(Session.class);

		when(this.mockSessionRepository.createSession()).thenReturn(mockSession);

		FixedDurationExpirationSessionRepository<?> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, Duration.ofMinutes(30L));

		assertThat(sessionRepository.createSession()).isEqualTo(mockSession);

		verify(this.mockSessionRepository, times(1)).createSession();
	}

	@Test
	public void deleteByIdCallsDelegate() {

		FixedDurationExpirationSessionRepository<?> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, Duration.ofMinutes(30L));

		sessionRepository.deleteById("1");

		verify(this.mockSessionRepository, times(1)).deleteById(eq("1"));
	}

	@Test
	public void saveCallsDelegate() {

		Session mockSession = mock(Session.class);

		FixedDurationExpirationSessionRepository<Session> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, Duration.ofMinutes(30L));

		sessionRepository.save(mockSession);

		verify(this.mockSessionRepository, times(1)).save(eq(mockSession));
	}

	@Test
	public void findByIdHandlesNullSessionCorrectly() {

		when(this.mockSessionRepository.findById(anyString())).thenReturn(null);

		FixedDurationExpirationSessionRepository<Session> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, Duration.ofMinutes(30L));

		assertThat(sessionRepository.findById("1")).isNull();

		verify(this.mockSessionRepository, times(1)).findById(eq("1"));
		verifyNoMoreInteractions(this.mockSessionRepository);
	}

	@Test
	public void findByIdHandlesNullExpirationTimeoutCorrectly() {

		Session mockSession = mock(Session.class);

		when(this.mockSessionRepository.findById(anyString())).thenReturn(mockSession);

		FixedDurationExpirationSessionRepository<Session> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, null);

		assertThat(sessionRepository.findById("1")).isEqualTo(mockSession);

		verify(this.mockSessionRepository, times(1)).findById(eq("1"));
		verifyNoMoreInteractions(this.mockSessionRepository);
		verifyZeroInteractions(mockSession);
	}

	@Test
	public void findByIdHandlesExpiredSessionCorrectly() {

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofMinutes(31L).toMillis()));

		when(mockSession.getId()).thenReturn("1");

		when(this.mockSessionRepository.findById(anyString())).thenReturn(mockSession);

		FixedDurationExpirationSessionRepository<Session> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, Duration.ofMinutes(30L));

		assertThat(sessionRepository.findById("1")).isNull();

		verify(mockSession, never()).getLastAccessedTime();
		verify(this.mockSessionRepository, times(1)).findById(eq("1"));
		verify(this.mockSessionRepository, times(1)).deleteById(eq("1"));
	}

	@Test
	public void findByIdHandlesActiveSessionCorrectly() {

		Session mockSession = mock(Session.class);

		when(mockSession.getCreationTime())
			.thenReturn(Instant.ofEpochMilli(System.currentTimeMillis() - Duration.ofMinutes(15L).toMillis()));

		when(this.mockSessionRepository.findById(anyString())).thenReturn(mockSession);

		FixedDurationExpirationSessionRepository<Session> sessionRepository =
			new FixedDurationExpirationSessionRepository<>(this.mockSessionRepository, Duration.ofMinutes(30L));

		assertThat(sessionRepository.findById("1")).isEqualTo(mockSession);

		verify(mockSession, never()).getLastAccessedTime();
		verify(this.mockSessionRepository, times(1)).findById(eq("1"));
		verify(this.mockSessionRepository, never()).deleteById(anyString());
	}
}
