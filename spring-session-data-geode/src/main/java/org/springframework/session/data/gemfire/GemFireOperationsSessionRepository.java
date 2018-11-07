/*
 * Copyright 2014-2016 the original author or authors.
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

package org.springframework.session.data.gemfire;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.geode.cache.query.SelectResults;

import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;

/**
 * The {@link GemFireOperationsSessionRepository} class is a Spring {@link SessionRepository} implementation
 * that interfaces with and uses Pivotal GemFire to back and store Spring Sessions.
 *
 * @author John Blum
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @since 1.1.0
 */
public class GemFireOperationsSessionRepository extends AbstractGemFireOperationsSessionRepository {

	// Pivotal GemFire OQL query used to lookup Sessions by arbitrary attributes.
	protected static final String FIND_SESSIONS_BY_INDEX_NAME_INDEX_VALUE_QUERY =
		"SELECT s FROM %1$s s WHERE s.attributes['%2$s'] = $1";

	// Pivotal GemFire OQL query used to look up Sessions by principal name.
	protected static final String FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY =
		"SELECT s FROM %1$s s WHERE s.principalName = $1";

	/**
	 * Constructs an instance of GemFireOperationsSessionRepository initialized with the
	 * required GemfireOperations object used to perform data access operations to manage
	 * Session state.
	 *
	 * @param template the GemfireOperations object used to access and manage Session
	 * state in GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemFireOperationsSessionRepository(GemfireOperations template) {
		super(template);
	}

	/**
	 * Looks up all available Sessions with the particular attribute indexed by name
	 * having the given value.
	 *
	 * @param indexName name of the indexed Session attribute. (e.g.
	 * {@link org.springframework.session.FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}
	 * ).
	 * @param indexValue value of the indexed Session attribute to search on (e.g.
	 * username).
	 * @return a mapping of Session ID to Session instances.
	 * @see org.springframework.session.Session
	 * @see java.util.Map
	 * @see #prepareQuery(String)
	 */
	@Override
	public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {

		SelectResults<Session> results = getTemplate().find(prepareQuery(indexName), indexValue);

		Map<String, Session> sessions = new HashMap<>(results.size());

		results.asList().forEach(session -> sessions.put(session.getId(), session));

		return sessions;
	}

	/**
	 * Prepares the appropriate Pivotal GemFire OQL query based on the indexed Session attribute
	 * name.
	 *
	 * @param indexName a String indicating the name of the indexed Session attribute.
	 * @return an appropriate Pivotal GemFire OQL statement for querying on a particular indexed
	 * Session attribute.
	 */
	protected String prepareQuery(String indexName) {

		return (PRINCIPAL_NAME_INDEX_NAME.equals(indexName)
			? String.format(FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY, getFullyQualifiedRegionName())
			: String.format(FIND_SESSIONS_BY_INDEX_NAME_INDEX_VALUE_QUERY, getFullyQualifiedRegionName(), indexName));
	}

	/**
	 * Constructs a new {@link Session} instance backed by GemFire.
	 *
	 * @return an instance of {@link Session} backed by GemFire.
	 * @see AbstractGemFireOperationsSessionRepository.GemFireSession#create(Duration)
	 * @see org.springframework.session.Session
	 * @see #getMaxInactiveIntervalInSeconds()
	 */
	@NonNull
	public Session createSession() {
		return GemFireSession.create(getMaxInactiveInterval());
	}

	/**
	 * Gets a copy of an existing, non-expired {@link Session} by ID.
	 *
	 * If the {@link Session} is expired, then the {@link Session }is deleted.
	 *
	 * @param sessionId a String indicating the ID of the Session to get.
	 * @return an existing {@link Session} by ID or null if no {@link Session} exists.
	 * @see AbstractGemFireOperationsSessionRepository.GemFireSession#from(Session)
	 * @see org.springframework.session.Session
	 * @see #deleteById(String)
	 */
	@Nullable
	public Session findById(String sessionId) {

		Session storedSession = getTemplate().get(sessionId);

		if (storedSession != null) {
			storedSession = storedSession.isExpired()
				? delete(storedSession)
				: touch(GemFireSession.from(storedSession));
		}

		return storedSession;
	}

	/**
	 * Saves the specified {@link Session} to Apache Geode or Pivotal GemFire.
	 *
	 * Warning, the save method should never be called asynchronously and concurrently, from a separate Thread,
	 * while the caller continues to modify the given {@link Session} from the forking Thread
	 * or data loss can occur!  There is a reason why this method is blocking!
	 *
	 * @param session the {@link Session} to save.
	 * @see org.springframework.data.gemfire.GemfireOperations#put(Object, Object)
	 * @see org.springframework.session.Session
	 */
	public void save(@Nullable Session session) {

		Optional.ofNullable(session)
			.filter(this::isDirty)
			.ifPresent(this::doSave);
	}

	private boolean isDirty(@NonNull Session session) {
		return !(session instanceof GemFireSession) || ((GemFireSession) session).isDirty();
	}

	/*private*/ void doSave(@NonNull Session session) {

		// Save Session As GemFireSession
		getTemplate().put(session.getId(), GemFireSession.from(session));

		if (session instanceof GemFireSession) {
			((GemFireSession) session).commit();
		}
	}

	/**
	 * Deletes (removes) any existing {@link Session} from GemFire. This operation
	 * also results in a SessionDeletedEvent.
	 *
	 * @param sessionId a String indicating the ID of the Session to remove from GemFire.
	 * @see org.springframework.data.gemfire.GemfireOperations#remove(Object)
	 * @see #handleDeleted(String, Session)
	 */
	public void deleteById(String sessionId) {
		handleDeleted(sessionId, toSession(getTemplate().<Object, Session>remove(sessionId), sessionId));
	}
}
