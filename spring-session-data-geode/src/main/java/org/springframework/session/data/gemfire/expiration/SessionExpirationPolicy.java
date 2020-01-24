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
package org.springframework.session.data.gemfire.expiration;

import java.time.Duration;
import java.util.Optional;

import org.apache.geode.cache.Region;

import org.springframework.lang.NonNull;
import org.springframework.session.Session;

/**
 * The {@link SessionExpirationPolicy} interface is a Strategy Interface defining a contract for users to implement
 * custom application expiration policies and rules for {@link Session} state management.
 *
 * Examples of different {@link Session} expiration strategies might include, but are not limited to:
 * idle expiration timeouts, fixed duration expiration timeouts, Time-To-Live (TTL) expiration, and so on.
 *
 * @author John Blum
 * @see java.lang.FunctionalInterface
 * @see org.apache.geode.cache.Region
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.support.FixedTimeoutSessionExpirationPolicy
 * @see org.springframework.session.data.gemfire.expiration.support.IdleTimeoutSessionExpirationPolicy
 * @since 2.1.0
 */
@FunctionalInterface
public interface SessionExpirationPolicy {

	/**
	 * Determines an {@link Optional} {@link Duration length of time} until the given {@link Session} will expire.
	 * A {@link Duration#ZERO Zero} or {@link Duration#isNegative() Negative Duration} indicates that
	 * the {@link Session} has expired.
	 *
	 * May return {@link Optional#EMPTY} as a "suggestion" that the Session should not expire or that the expiration
	 * determination should be handled by the next expiration policy in a chain of policies.  Implementors are free
	 * to compose 2 or more expiration policies using Composite Software Design Pattern as necessary.
	 *
	 * In Apache Geode or Pivotal GemFire's case, an {@link Optional#EMPTY} return value will indicate that it
	 * should default to the configured Entry Idle Timeout (TTI) Expiration Policy of the {@link Region} managing
	 * {@link Session} state to determine exactly when the {@link Session} will expire.
	 *
	 * @param session {@link Session} to evaluate. {@link Session} is required.
	 * @return an {@link Optional} {@link Duration} specifying the length of time until the {@link Session} will expire.
	 * @see <a href="https://en.wikipedia.org/wiki/Composite_pattern">Composite Software Design Pattern</a>
	 * @see org.springframework.session.Session
	 * @see java.time.Duration
	 * @see java.util.Optional
	 */
	Optional<Duration> determineExpirationTimeout(@NonNull Session session);

	/**
	 * Specifies the {@link ExpirationAction action} to take when the {@link Session} expires.
	 *
	 * Defaults to {@link ExpirationAction#INVALIDATE}.
	 *
	 * @return an {@link ExpirationAction} specifying the action to take when the {@link Session} expires.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
	 */
	default ExpirationAction getExpirationAction() {
		return ExpirationAction.INVALIDATE;
	}

	/**
	 * Enumeration of different actions to take when a {@link Session} expires.
	 */
	enum ExpirationAction {

		DESTROY,
		INVALIDATE;

		/**
		 * Null-safe operation defaulting the {@link ExpirationAction} to {@link #INVALIDATE} if the given
		 * {@link ExpirationAction} is {@literal null}.
		 *
		 * @param expirationAction {@link ExpirationAction} to evaluate.
		 * @return the given {@link ExpirationAction} if not {@literal null}, otherwise return {@link #INVALIDATE}.
		 */
		public static ExpirationAction defaultIfNull(ExpirationAction expirationAction) {
			return expirationAction != null ? expirationAction : INVALIDATE;
		}
	}
}
