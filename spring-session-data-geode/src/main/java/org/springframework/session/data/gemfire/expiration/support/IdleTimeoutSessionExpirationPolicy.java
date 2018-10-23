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

import java.time.Duration;

import org.springframework.lang.NonNull;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;
import org.springframework.util.Assert;

/**
 * An implementation of the {@link SessionExpirationPolicy} interface that specifies an expiration policy
 * based on inactive, idle {@link Session Sessions} exceeding a predefined time period for expiration.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @since 2.1.0
 */
@SuppressWarnings("unused")
public class IdleTimeoutSessionExpirationPolicy implements SessionExpirationPolicy {

	private final Duration idleExpirationTimeout;

	/**
	 * Constructs a new instance of {@link IdleTimeoutSessionExpirationPolicy} initialized with
	 * the given {@link Duration expiration timeout}.
	 *
	 * @param idleExpirationTimeout {@link Duration} specifying the length of time until the {@link Session} expires.
	 * @throws IllegalArgumentException if {@link Duration} is {@literal null}.
	 * @see java.time.Duration
	 */
	public IdleTimeoutSessionExpirationPolicy(@NonNull Duration idleExpirationTimeout) {

		Assert.notNull(idleExpirationTimeout, "Idle expiration timeout is required");

		this.idleExpirationTimeout = idleExpirationTimeout;

	}

	/**
	 * Return the configured {@link Duration idle expiration timeout}.
	 *
	 * @return the configured {@link Duration idle expiration timeout}.
	 * @see java.time.Duration
	 */
	protected Duration getIdleExpirationTimeout() {
		return this.idleExpirationTimeout;
	}

	@NonNull @Override
	public Duration expireAfter(@NonNull Session session) {

		long currentTimeMinusLastAccessTime =
			Math.max(System.currentTimeMillis() - session.getLastAccessedTime().toEpochMilli(), 0);

		Duration expirationDuration =
			getIdleExpirationTimeout().minus(Duration.ofMillis(currentTimeMinusLastAccessTime));

		return expirationDuration.isNegative() ? Duration.ZERO : expirationDuration;
	}
}
