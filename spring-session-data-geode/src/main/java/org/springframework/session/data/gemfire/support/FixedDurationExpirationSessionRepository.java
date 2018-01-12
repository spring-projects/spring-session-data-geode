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

package org.springframework.session.data.gemfire.support;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * The {@link FixedDurationExpirationSessionRepository} class...
 *
 * @author John Blum
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see <a href="https://github.com/spring-projects/spring-session/issues/922">Absolute Session Timeouts</a>
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class FixedDurationExpirationSessionRepository<S extends Session> implements SessionRepository<S> {

	private final SessionRepository<S> delegate;

	private final Duration expirationDuration;

	public FixedDurationExpirationSessionRepository(SessionRepository<S> sessionRepository, Duration expirationDuration) {

		this.delegate = Optional.ofNullable(sessionRepository)
			.orElseThrow(() -> new IllegalArgumentException("SessionRepository is required"));

		this.expirationDuration = expirationDuration;
	}

	protected SessionRepository<S> getDelegate() {
		return this.delegate;
	}

	public Optional<Duration> getExpirationDuration() {
		return Optional.ofNullable(this.expirationDuration);
	}

	@Override
	public S createSession() {
		return getDelegate().createSession();
	}

	@Override
	@SuppressWarnings("unchecked")
	public S findById(String id) {

		return Optional.ofNullable(getDelegate().findById(id))
			.map(session ->
				getExpirationDuration()
					.map(expirationDuration -> handleExpired(session, expirationDuration))
					.orElse(session)
			).orElse(null);
	}

	private S handleExpired(S session, Duration expirationDuration) {

		if (isExpired(session, expirationDuration)) {
			deleteById(session.getId());
			session = null;
		}

		return session;
	}

	private boolean isExpired(S session, Duration expirationDuration) {

		Instant sessionCreationTime = session.getCreationTime();
		Instant now = Instant.now();

		return now.minusMillis(expirationDuration.toMillis()).isAfter(sessionCreationTime);
	}

	@Override
	public void save(S session) {
		getDelegate().save(session);
	}

	@Override
	public void deleteById(String id) {
		getDelegate().deleteById(id);
	}
}
