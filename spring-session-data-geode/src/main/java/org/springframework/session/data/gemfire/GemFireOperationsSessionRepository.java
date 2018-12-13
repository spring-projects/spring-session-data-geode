/*
 * Copyright 2018 the original author or authors.
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
	protected static final String FIND_SESSIONS_BY_INDEX_NAME_AND_INDEX_VALUE_QUERY =
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
	 * Finds an existing, non-expired {@link Session} by ID.
	 *
	 * If the {@link Session} is expired, then the {@link Session} is deleted and {@literal null} is returned.
	 *
	 * @param sessionId {@link String} containing the {@link Session#getId() ID}} of the {@link Session} to get.
	 * @return an existing {@link Session} by ID or {@literal null} if no {@link Session} exists
	 * or the {@link Session} expired.
	 * @see AbstractGemFireOperationsSessionRepository.GemFireSession#from(Session)
	 * @see org.springframework.session.Session
	 * @see #commit(Session)
	 * @see #deleteById(String)
	 * @see #registerInterest(Session)
	 * @see #touch(Session)
	 */
	@Nullable
	public Session findById(String sessionId) {

		Session storedSession = getSessionsTemplate().get(sessionId);

		if (storedSession != null) {
			storedSession = storedSession.isExpired()
				? delete(storedSession)
				: registerInterest(touch(commit(GemFireSession.from(storedSession))));
		}

		return storedSession;
	}

	/**
	 * Finds all available {@link Session Sessions} with the particular attribute indexed by {@link String name}
	 * having the given {@link Object value}.
	 *
	 * @param indexName {@link String name} of the indexed {@link Session} attribute.
	 * (e.g. {@link org.springframework.session.FindByIndexNameSessionRepository#PRINCIPAL_NAME_INDEX_NAME}).
	 * @param indexValue {@link Object value} of the indexed {@link Session} attribute to search on
	 * (e.g. {@literal username}).
	 * @return a mapping of {@link Session#getId()} Session IDs} to {@link Session} objects.
	 * @see org.springframework.session.Session
	 * @see #prepareQuery(String)
	 * @see java.util.Map
	 * @see #commit(Session)
	 * @see #registerInterest(Session)
	 * @see #touch(Session)
	 */
	@Override
	public Map<String, Session> findByIndexNameAndIndexValue(String indexName, String indexValue) {

		SelectResults<Session> results = getSessionsTemplate().find(prepareQuery(indexName), indexValue);

		Map<String, Session> sessions = new HashMap<>(results.size());

		results.asList().forEach(session -> sessions.put(session.getId(), registerInterest(touch(commit(session)))));

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

		String fullyQualifiedRegionName = getFullyQualifiedRegionName();

		return PRINCIPAL_NAME_INDEX_NAME.equals(indexName)
			? String.format(FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY, fullyQualifiedRegionName)
			: String.format(FIND_SESSIONS_BY_INDEX_NAME_AND_INDEX_VALUE_QUERY, fullyQualifiedRegionName, indexName);
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

		if (isNonNullAndDirty(session)) {
			doSave(session);
		}
	}

	private boolean isDirty(@NonNull Session session) {
		return !(session instanceof GemFireSession) || ((GemFireSession) session).hasDelta();
	}

	private boolean isNonNullAndDirty(@Nullable Session session) {
		return session != null && isDirty(session);
	}

	void doSave(@NonNull Session session) {

		// Save Session As GemFireSession
		getSessionsTemplate().put(session.getId(), GemFireSession.from(session));

		// Commit Session
		commit(session);
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
		handleDeleted(sessionId, getSessionsTemplate().<Object, Session>remove(sessionId));
	}
}
