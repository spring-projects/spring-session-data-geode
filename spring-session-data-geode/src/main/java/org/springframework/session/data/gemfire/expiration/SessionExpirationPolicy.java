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

package org.springframework.session.data.gemfire.expiration;

import java.time.Duration;

import org.springframework.lang.NonNull;
import org.springframework.session.Session;

/**
 * The {@link SessionExpirationPolicy} interface is a Strategy interface defining a contract for users to implement
 * different {@link Session} expiration policies and rules.
 *
 * Examples of different {@link Session} expiration strategies might include, but are not limited to:
 * idle expiration timeout or fixed duration expiration timeouts, and so on.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.support.FixedTimeoutSessionExpirationPolicy
 * @see org.springframework.session.data.gemfire.expiration.support.IdleTimeoutSessionExpirationPolicy
 * @since 2.1.0
 */
@FunctionalInterface
@SuppressWarnings("unused")
public interface SessionExpirationPolicy {

	/**
	 * Defines the {@link Duration length of time} until the given {@link Session} will expire.
	 *
	 * @param session {@link Session} to evaluate.
	 * @return a {@link Duration} specifying the length of time until the {@link Session} will expire.
	 * @see org.springframework.session.Session
	 * @see java.time.Duration
	 */
	@NonNull
	Duration expireAfter(Session session);

	/**
	 * Defines the {@link ExpirationAction action} to take when the {@link Session} expires.
	 *
	 * Defaults to {@link ExpirationAction#INVALIDATE}.
	 *
	 * @return an {@link ExpirationAction} specifying the action to take when the {@link Session} expires.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
	 */
	default ExpirationAction getAction() {
		return ExpirationAction.INVALIDATE;
	}

	/**
	 * Enumeration of different actions to take when the {@link Session} expires.
	 */
	enum ExpirationAction {

		DESTROY,
		INVALIDATE;

		public static ExpirationAction defaultIfNull(ExpirationAction expirationAction) {
			return expirationAction != null ? expirationAction : INVALIDATE;
		}

	}
}
