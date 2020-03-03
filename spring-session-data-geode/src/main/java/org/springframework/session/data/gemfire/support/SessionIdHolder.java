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

package org.springframework.session.data.gemfire.support;

import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalArgumentException;

import java.util.Optional;

import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.Operation;
import org.apache.geode.cache.Region;

import org.springframework.session.Session;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

/**
 * {@link SessionIdHolder} class is a Spring Session {@link Session} implementation that only holds
 * the {@link String ID} of the {@link Session}.
 *
 * This implementation is only used in case Apache Geode or Pivotal GemFire returns a {@literal null} (old) value
 * in a {@link Region} {@link EntryEvent} triggered by a {@link Operation#DESTROY} or {@link Operation#INVALIDATE}
 * operation.
 *
 * @author John Blum
 * @see org.apache.geode.cache.EntryEvent
 * @see org.apache.geode.cache.Operation
 * @see org.apache.geode.cache.Region
 * @see org.springframework.session.data.gemfire.support.SessionIdHolder
 * @since 2.0.0
 */
public final class SessionIdHolder extends AbstractSession {

	private final String sessionId;

	/***
	 * Factory method to create an instance of the {@link SessionIdHolder} initialized with
	 * the given {@link String session ID}.
	 *
	 * @param sessionId {@link String} containing the session ID used to initialize
	 * the new instance of {@link SessionIdHolder}.
	 * @return a new instance of {@link SessionIdHolder} initialized with
	 * the given {@link String session ID}.
	 * @throws IllegalArgumentException if session ID is {@literal null} or empty.
	 * @see #SessionIdHolder(String)
	 */
	public static SessionIdHolder create(String sessionId) {
		return new SessionIdHolder(sessionId);
	}

	/**
	 * Constructs a new instance of the {@link SessionIdHolder} initialized with
	 * the given {@link String session ID}.
	 *
	 * @param sessionId {@link String} containing the session ID used to initialize
	 * the new instance of {@link SessionIdHolder}.
	 * @throws IllegalArgumentException if session ID is {@literal null} or empty.
	 */
	public SessionIdHolder(String sessionId) {

		this.sessionId = Optional.ofNullable(sessionId)
			.filter(StringUtils::hasText)
			.orElseThrow(() -> newIllegalArgumentException("Session ID [%s] is required", sessionId));
	}

	/**
	 * Returns the {@link String ID} of this {@link Session}.
	 *
	 * @return the {@link String ID} of this {@link Session}.
	 */
	@Override
	public String getId() {
		return this.sessionId;
	}

	@Override
	public boolean equals(Object obj) {

		if (this == obj) {
			return true;
		}

		if (!(obj instanceof Session)) {
			return false;
		}

		Session that = (Session) obj;

		return ObjectUtils.nullSafeEquals(this.getId(), that.getId());
	}

	@Override
	public int hashCode() {

		int hashValue = 17;

		hashValue = 37 * hashValue + ObjectUtils.nullSafeHashCode(getId());

		return hashValue;
	}

	@Override
	public String toString() {
		return getId();
	}
}
