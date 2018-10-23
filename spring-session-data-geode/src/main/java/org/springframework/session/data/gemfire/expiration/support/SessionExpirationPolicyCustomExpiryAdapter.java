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
 * to plugin to GemFire/Geode's expiration logistics.
 *
 * @author John Blum
 * @see org.apache.geode.cache.CustomExpiry
 * @see org.apache.geode.cache.ExpirationAction
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.Region
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class SessionExpirationPolicyCustomExpiryAdapter implements CustomExpiry<String, Object> {

	private final SessionExpirationPolicy sessionExpirationPolicy;

	/**
	 * Constructs a new instance of {@link SessionExpirationPolicyCustomExpiryAdapter} initialized with
	 * the given, required {@link SessionExpirationPolicy}.
	 *
	 * @param sessionExpirationPolicy {@link SessionExpirationPolicy} used to enforce the expiration policy
	 * on all {@link Session Sessions}.
	 * @throws IllegalArgumentException if the {@link SessionExpirationPolicy} is {@literal null}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
	 */
	public SessionExpirationPolicyCustomExpiryAdapter(@NonNull SessionExpirationPolicy sessionExpirationPolicy) {

		Assert.notNull(sessionExpirationPolicy, "SessionExpirationPolicy is required");

		this.sessionExpirationPolicy = sessionExpirationPolicy;
	}

	/**
	 * Returns a reference to the {@link SessionExpirationPolicy} defining the expiration policies
	 * for all managed {@link Session Sessions}.
	 *
	 * @return a reference to the {@link SessionExpirationPolicy}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy
	 */
	protected SessionExpirationPolicy getSessionExpirationPolicy() {
		return this.sessionExpirationPolicy;
	}

	@Nullable @Override
	public ExpirationAttributes getExpiry(@Nullable Region.Entry<String, Object> regionEntry) {

		return Optional.ofNullable(resolveSession(regionEntry))
			.map(this::newExpirationAttributes)
			.orElse(null);
	}

	/**
	 * Constructs {@link ExpirationAttributes} from the given {@link Session}.
	 *
	 * @param session {@link Session} used to construct the {@link ExpirationAttributes}.
	 * @return a new {@link ExpirationAttributes} constructed from the given {@link Session}.
	 * @see #newExpirationAttributes(Duration, SessionExpirationPolicy.ExpirationAction)
	 * @see org.apache.geode.cache.ExpirationAttributes
	 * @see org.springframework.session.Session
	 */
	private ExpirationAttributes newExpirationAttributes(Session session) {

		SessionExpirationPolicy sessionExpirationPolicy = getSessionExpirationPolicy();
		Duration sessionExpirationDuration = sessionExpirationPolicy.expireAfter(session);
		SessionExpirationPolicy.ExpirationAction sessionExpirationAction = sessionExpirationPolicy.getAction();

		return newExpirationAttributes(sessionExpirationDuration, sessionExpirationAction);
	}

	/**
	 * Constructs a new {@link ExpirationAttributes} initialized with the given {@link Duration expiration timeout}
	 * and {@link SessionExpirationPolicy.ExpirationAction} to take when the {@link Session} expires.
	 *
	 * @param duration {@link Duration} specifying the expiration timeout.
	 * @param expirationAction {@link SessionExpirationPolicy.ExpirationAction} to take when
	 * the {@link Session} expires.
	 * @return the new {@link ExpirationAttributes}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
	 * @see #newExpirationAttributes(int, ExpirationAction)
	 * @see org.apache.geode.cache.ExpirationAttributes
	 * @see java.time.Duration
	 */
	private ExpirationAttributes newExpirationAttributes(Duration duration,
			SessionExpirationPolicy.ExpirationAction expirationAction) {

		int expirationTimeout = (int) Math.min(Integer.MAX_VALUE, Math.max(duration.getSeconds(), 1));

		return newExpirationAttributes(expirationTimeout, resolveExpirationAction(expirationAction));
	}

	/**
	 * Constructs new {@link ExpirationAttributes} with the given {@link Integer expiration timeout}
	 * and {@link ExpirationAction} to take when the {@link Session} expires.
	 *
	 * @param expirationTimeInSeconds length of time in seconds until the {@link Session} expires.
	 * @param expirationAction {@link ExpirationAction} to take when the {@link Session} expires.
	 * @return the new {@link ExpirationAttributes}.
	 * @see org.apache.geode.cache.ExpirationAttributes
	 */
	private ExpirationAttributes newExpirationAttributes(int expirationTimeInSeconds,
			ExpirationAction expirationAction) {

		return new ExpirationAttributes(expirationTimeInSeconds, expirationAction);
	}

	/**
	 * Resolves the {@link org.apache.geode.cache.ExpirationAction} from the given
	 * {@link SessionExpirationPolicy.ExpirationAction}.
	 *
	 * Defaults to {@link ExpirationAction#INVALIDATE} if {@link SessionExpirationPolicy.ExpirationAction}
	 * is {@literal null}.
	 *
	 * @param expirationAction {@link SessionExpirationPolicy.ExpirationAction} to convert into a
	 * {@link org.apache.geode.cache.ExpirationAction}.
	 * @return an {@link org.apache.geode.cache.ExpirationAction} from the given
	 * {@link SessionExpirationPolicy.ExpirationAction}.
	 * @see org.springframework.session.data.gemfire.expiration.SessionExpirationPolicy.ExpirationAction
	 * @see org.apache.geode.cache.ExpirationAction
	 */
	private ExpirationAction resolveExpirationAction(SessionExpirationPolicy.ExpirationAction expirationAction) {

		switch (SessionExpirationPolicy.ExpirationAction.defaultIfNull(expirationAction)) {
			case DESTROY:
				return ExpirationAction.DESTROY;
			default:
				return ExpirationAction.INVALIDATE;
		}
	}

	/**
	 * Resolves a {@link Session} object from the given {@link Region.Entry#getValue() Region Entry Value}.
	 *
	 * @param regionEntry {@link Region.Entry} from which to extract the {@link Session} value.
	 * @return a {@link Session} object from the given {@link Region.Entry#getValue()}.
	 * @see org.springframework.session.Session
	 * @see org.apache.geode.cache.Region.Entry
	 * @see #resolveSession(Object)
	 */
	@Nullable
	private Session resolveSession(Region.Entry<String, Object> regionEntry) {
		return resolveSession(Optional.ofNullable(regionEntry).map(Region.Entry::getValue).orElse(null));
	}

	/**
	 * Resolves a {@link Session} object from the given {@link Object} value.
	 *
	 * The {@link Object} may already be a {@link Session} or may possibly be a {@link PdxInstance} if GemFire/Geode
	 * PDX serialization is enabled.
	 *
	 * @param regionEntryValue {@link Object} to evaluate as a {@link Session}.
	 * @return a {@link Session} from the given {@link Object}.
	 * @see org.springframework.session.Session
	 * @see org.apache.geode.pdx.PdxInstance
	 * @see java.lang.Object
	 */
	@Nullable
	private Session resolveSession(Object regionEntryValue) {

		return regionEntryValue instanceof Session ? (Session) regionEntryValue
			: regionEntryValue instanceof PdxInstance ? (Session) ((PdxInstance) regionEntryValue).getObject()
			: null;
	}
}
