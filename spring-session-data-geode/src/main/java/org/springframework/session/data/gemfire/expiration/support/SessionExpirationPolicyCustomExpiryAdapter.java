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

package org.springframework.session.data.gemfire.expiration.support;

import java.time.Duration;
import java.util.Optional;

import org.apache.geode.cache.CustomExpiry;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.pdx.PdxInstance;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy;
import org.springframework.util.Assert;

/**
 * The {@link SessionExpirationPolicyCustomExpiryAdapter} class is an Apache Geode/Pivotal GemFire {@link CustomExpiry}
 * implementation wrapping and adapting an instance of the {@link SessionExpirationPolicy} strategy interface
 * to plugin to and affect Apache Geode/Pivotal GemFire's expiration behavior.
 *
 * @author John Blum
 * @see org.apache.geode.cache.CustomExpiry
 * @see org.apache.geode.cache.ExpirationAction
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.pdx.PdxInstance
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class SessionExpirationPolicyCustomExpiryAdapter implements CustomExpiry<String, Object> {

	protected static final SessionExpirationPolicy.ExpirationAction DEFAULT_EXPIRATION_ACTION =
		SessionExpirationPolicy.ExpirationAction.INVALIDATE;

	private final SessionExpirationPolicy sessionExpirationPolicy;

	/**
	 * Constructs a new {@link SessionExpirationPolicyCustomExpiryAdapter} initialized with
	 * the given, required {@link SessionExpirationPolicy}.
	 *
	 * @param sessionExpirationPolicy {@link SessionExpirationPolicy} used to determine the expiration policy
	 * for all {@link Session Sessions}.
	 * @throws IllegalArgumentException if {@link SessionExpirationPolicy} is {@literal null}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
	 */
	public SessionExpirationPolicyCustomExpiryAdapter(@NonNull SessionExpirationPolicy sessionExpirationPolicy) {

		Assert.notNull(sessionExpirationPolicy, "SessionExpirationPolicy is required");

		this.sessionExpirationPolicy = sessionExpirationPolicy;
	}

	/**
	 * Returns the configured {@link SessionExpirationPolicy} defining the expiration policies
	 * for all managed {@link Session Sessions}.
	 *
	 * @return the configured {@link SessionExpirationPolicy}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
	 */
	protected SessionExpirationPolicy getSessionExpirationPolicy() {
		return this.sessionExpirationPolicy;
	}

	@Nullable @Override
	public ExpirationAttributes getExpiry(@Nullable Region.Entry<String, Object> regionEntry) {

		return resolveSession(regionEntry)
			.flatMap(getSessionExpirationPolicy()::determineExpirationTimeout)
			.map(expirationTimeout ->
				newExpirationAttributes(expirationTimeout, getSessionExpirationPolicy().getExpirationAction()))
			.orElse(null);
	}

	/**
	 * Constructs a new {@link ExpirationAttributes} initialized with the given {@link Duration expiration timeut}
	 * and default {@link ExpirationAction#INVALIDATE expirtion action}.
	 *
	 * @param expirationTimeout {@link Duration} specifying the expiration timeout.
	 * @return the new {@link ExpirationAttributes}.
	 * @see #newExpirationAttributes(Duration, SessionExpirationPolicy.ExpirationAction)
	 * @see org.apache.geode.cache.ExpirationAttributes
	 * @see java.time.Duration
	 */
	@NonNull
	protected ExpirationAttributes newExpirationAttributes(@NonNull Duration expirationTimeout) {
		return newExpirationAttributes(expirationTimeout, DEFAULT_EXPIRATION_ACTION);
	}

	/**
	 * Constructs a new {@link ExpirationAttributes} initialized with the given {@link Duration expiration timeout}
	 * and action taken when the {@link Session} expires.
	 *
	 * @param expirationTimeout {@link Duration} specifying the expiration timeout.
	 * @param expirationAction action taken when the {@link Session} expires.
	 * @return the new {@link ExpirationAttributes}.
	 * @see #newExpirationAttributes(int, ExpirationAction)
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
	 * @see org.apache.geode.cache.ExpirationAttributes
	 * @see java.time.Duration
	 */
	@NonNull
	protected ExpirationAttributes newExpirationAttributes(@NonNull Duration expirationTimeout,
			@Nullable SessionExpirationPolicy.ExpirationAction expirationAction) {

		int expirationTimeoutInSeconds =
			(int) Math.min(Integer.MAX_VALUE, Math.max(expirationTimeout.getSeconds(), 1));

		return newExpirationAttributes(expirationTimeoutInSeconds, toGemFireExpirationAction(expirationAction));
	}

	/**
	 * Constructs a new {@link ExpirationAttributes} initialized with the given {@link Integer expiration timeout}
	 * in seconds and {@link ExpirationAction} taken when the {@link Session} expires.
	 *
	 * @param expirationTimeInSeconds {@link Integer length of time} in seconds until the {@link Session} expires.
	 * @param expirationAction {@link ExpirationAction} taken when the {@link Session} expires.
	 * @return the new {@link ExpirationAttributes}.
	 * @see org.apache.geode.cache.ExpirationAction
	 * @see org.apache.geode.cache.ExpirationAttributes
	 */
	@NonNull
	private ExpirationAttributes newExpirationAttributes(int expirationTimeInSeconds,
			ExpirationAction expirationAction) {

		return new ExpirationAttributes(expirationTimeInSeconds, expirationAction);
	}

	/**
	 * Resolves an {@link Optional} {@link Session} object from the {@link Region.Entry#getValue() Region Entry Value}.
	 *
	 * @param regionEntry {@link Region.Entry} from which to extract the {@link Session} value.
	 * @return an {@link Optional} {@link Session} object from the {@link Region.Entry#getValue() Region Entry Value}.
	 * @see org.apache.geode.cache.Region.Entry
	 * @see org.springframework.session.Session
	 * @see java.util.Optional
	 * @see #resolveSession(Object)
	 */
	private Optional<Session> resolveSession(@Nullable Region.Entry<String, Object> regionEntry) {

		return Optional.ofNullable(regionEntry)
			.map(Region.Entry::getValue)
			.flatMap(this::resolveSession);
	}

	/**
	 * Resolves an {@link Optional} {@link Session} object from the given {@link Object} value.
	 *
	 * The {@link Object} may already be a {@link Session} or may possibly be a {@link PdxInstance}
	 * if Apache Geode/Pivotal GemFire PDX serialization is enabled.
	 *
	 * @param value {@link Object} to evaluate as a {@link Session}.
	 * @return an {@link Optional} {@link Session} from the given {@link Object}.
	 * @see org.springframework.session.Session
	 * @see org.apache.geode.pdx.PdxInstance
	 * @see java.util.Optional
	 * @see java.lang.Object
	 */
	private Optional<Session> resolveSession(@Nullable Object value) {

		return Optional.ofNullable(value instanceof Session ? (Session) value
			: value instanceof PdxInstance ? (Session) ((PdxInstance) value).getObject()
			: null);
	}

	/**
	 * Converts the {@link org.apache.geode.cache.ExpirationAction} from the given
	 * {@link SessionExpirationPolicy.ExpirationAction}.
	 *
	 * Defaults to {@link ExpirationAction#INVALIDATE} if {@link SessionExpirationPolicy.ExpirationAction}
	 * is {@literal null}.
	 *
	 * @param expirationAction {@link SessionExpirationPolicy.ExpirationAction} to convert into an
	 * {@link org.apache.geode.cache.ExpirationAction}.
	 * @return an {@link org.apache.geode.cache.ExpirationAction} from the given
	 * {@link SessionExpirationPolicy.ExpirationAction}; defaults to {@link ExpirationAction#INVALIDATE}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
	 * @see org.apache.geode.cache.ExpirationAction
	 */
	private ExpirationAction toGemFireExpirationAction(SessionExpirationPolicy.ExpirationAction expirationAction) {

		switch (SessionExpirationPolicy.ExpirationAction.defaultIfNull(expirationAction)) {
			case DESTROY:
				return ExpirationAction.DESTROY;
			default:
				return ExpirationAction.INVALIDATE;
		}
	}
}
