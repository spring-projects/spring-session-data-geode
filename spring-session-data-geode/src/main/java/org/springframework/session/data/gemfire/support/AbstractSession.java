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
import java.util.Set;

import org.springframework.session.Session;

/**
 * The AbstractSession class...
 *
 * @author John Blum
 * @since 1.0.0
 */
public class AbstractSession implements Session {

	@Override
	public String getId() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public void setAttribute(String attributeName, Object attributeValue) {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public <T> T getAttribute(String attributeName) {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public <T> T getAttributeOrDefault(String name, T defaultValue) {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public <T> T getRequiredAttribute(String name) {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public Set<String> getAttributeNames() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public boolean isExpired() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public Instant getCreationTime() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public void setLastAccessedTime(Instant lastAccessedTime) {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public Instant getLastAccessedTime() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public void setMaxInactiveInterval(Duration interval) {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public Duration getMaxInactiveInterval() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public String changeSessionId() {
		throw new UnsupportedOperationException("Not Implemented");
	}

	@Override
	public void removeAttribute(String attributeName) {
		throw new UnsupportedOperationException("Not Implemented");
	}
}
