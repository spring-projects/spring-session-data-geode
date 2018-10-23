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
 * An implementation of the {@link SessionExpirationPolicy} interface that specifies an expiration policy based on
 * a fixed period of time.  That is, the {@link Session} will timeout after a fixed {@link Duration} even if the
 * {@link Session} is still active.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @since 2.1.0
 */
@SuppressWarnings("unused")
public class FixedTimeoutSessionExpirationPolicy implements SessionExpirationPolicy {

	private final Duration fixedExpirationTimeout;

	/**
	 * Constructs a new {@link FixedTimeoutSessionExpirationPolicy} initialized with the given
	 * {@link Duration fixed expiration timeout}.
	 *
	 * @param fixedExpirationTimeout {@link Duration} specifying the fixed length of time until
	 * the {@link Session} expires.
	 * @throws IllegalArgumentException if {@link Duration} is {@literal null}.
	 * @see java.time.Duration
	 */
	public FixedTimeoutSessionExpirationPolicy(@NonNull Duration fixedExpirationTimeout) {

		Assert.notNull(fixedExpirationTimeout, "Fixed expiration timeout is required");

		this.fixedExpirationTimeout = fixedExpirationTimeout;
	}

	/**
	 * Return the configured {@link Duration fixed expiration timeout}.
	 *
	 * @return the configured {@link Duration fixed expiration timeout}.
	 * @see java.time.Duration
	 */
	protected Duration getFixedExpirationTimeout() {
		return this.fixedExpirationTimeout;
	}

	@NonNull @Override
	public Duration expireAfter(@NonNull Session session) {

		long currentTimeMinusCreationTime =
			Math.max(System.currentTimeMillis() - session.getCreationTime().toEpochMilli(), 0);

		Duration expirationDuration =
			getFixedExpirationTimeout().minus(Duration.ofMillis(currentTimeMinusCreationTime));

		return expirationDuration.isNegative() ? Duration.ZERO : expirationDuration;
	}
}
