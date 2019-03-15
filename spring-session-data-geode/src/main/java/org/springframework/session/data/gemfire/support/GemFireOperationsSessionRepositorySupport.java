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

package org.springframework.session.data.gemfire.support;

import java.util.Map;

import org.apache.geode.cache.Region;

import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository;

/**
 * Framework supporting class for {@link AbstractGemFireOperationsSessionRepository} implementations.
 *
 * By default, all {@link SessionRepository} data access operations throw an {@link UnsupportedOperationException}.
 * Therefore, you are free to implement only the {@link SessionRepository} data access operations you need.
 *
 * For instance, if you only want to implement a {@literal read-only} {@link SessionRepository}, then you can simply
 * {@literal override} the {@link #findById(String)}, {@link #findByIndexNameAndIndexValue(String, String)}
 * and {@link #findByPrincipalName(String)} Repository methods.  In that way, the {@link Session} can never be updated.
 *
 * @author John Blum
 * @see org.apache.geode.cache.Region
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @since 2.1.1
 */
@SuppressWarnings("unused")
public abstract class GemFireOperationsSessionRepositorySupport
		extends AbstractGemFireOperationsSessionRepository {

	private static final String OPERATION_NOT_SUPPORTED = "Operation Not Supported";

	/**
	 * Construct an uninitialized instance of {@link GemFireOperationsSessionRepositorySupport}.
	 *
	 * @see #GemFireOperationsSessionRepositorySupport(GemfireOperations)
	 */
	protected GemFireOperationsSessionRepositorySupport() { }

	/**
	 * Constructs a new instance of {@link GemFireOperationsSessionRepositorySupport} initialized with
	 * the given {@link GemfireOperations} object used to perform {@link Region} data access operations
	 * managing the {@link Session} state.
	 *
	 * @param gemfireOperations {@link GemfireOperations} for performing data access operations
	 * on the {@link Region} used to manage {@link Session} state.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemFireOperationsSessionRepositorySupport(GemfireOperations gemfireOperations) {
		super(gemfireOperations);
	}

	@Override
	public Session createSession() {
		throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
	}

	@Override
	public void deleteById(String id) {
		throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
	}

	@Override
	public Session findById(String id) {
		throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
	}

	@Override
	public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {
		throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
	}

	@Override
	public void save(Session session) {
		throw new UnsupportedOperationException(OPERATION_NOT_SUPPORTED);
	}
}
