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

import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalStateException;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.Delta;
import org.apache.geode.Instantiator;
import org.apache.geode.InvalidDeltaException;
import org.apache.geode.cache.AttributesMutator;
import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.Operation;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.util.CacheListenerAdapter;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.data.gemfire.support.SessionIdHolder;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * {@link AbstractGemFireOperationsSessionRepository} is an abstract base class encapsulating functionality
 * common to all implementations that support {@link SessionRepository} operations backed by Apache Geode.
 *
 * @author John Blum
 * @see org.apache.geode.DataSerializable
 * @see org.apache.geode.DataSerializer
 * @see org.apache.geode.Delta
 * @see org.apache.geode.Instantiator
 * @see org.apache.geode.cache.EntryEvent
 * @see org.apache.geode.cache.Operation
 * @see org.apache.geode.cache.Region
 * @see org.springframework.beans.factory.InitializingBean
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.ApplicationEventPublisher
 * @see org.springframework.context.ApplicationEventPublisherAware
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.expression.Expression
 * @see org.springframework.session.FindByIndexNameSessionRepository
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.events.SessionCreatedEvent
 * @see org.springframework.session.events.SessionDeletedEvent
 * @see org.springframework.session.events.SessionDestroyedEvent
 * @see org.springframework.session.events.SessionExpiredEvent
 * @since 1.1.0
 */
public abstract class AbstractGemFireOperationsSessionRepository extends CacheListenerAdapter<Object, Session>
		implements ApplicationEventPublisherAware, FindByIndexNameSessionRepository<Session>, InitializingBean {

	private static final AtomicBoolean usingDataSerialization = new AtomicBoolean(false);

	private ApplicationEventPublisher applicationEventPublisher = event -> {};

	private Duration maxInactiveInterval =
		Duration.ofSeconds(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

	private final GemfireOperations template;

	private final Log logger = newLogger();

	private final Set<Integer> cachedSessionIds = new ConcurrentSkipListSet<>();

	private String fullyQualifiedRegionName;

	/**
	 * Protected, default constructor used by extensions of {@link AbstractGemFireOperationsSessionRepository}
	 * in order to affect and assess {@link SessionRepository} configuration and state.
	 */
	protected AbstractGemFireOperationsSessionRepository() {
		this.template = null;
	}

	/**
	 * Constructs an instance of {@link AbstractGemFireOperationsSessionRepository}
	 * with a required {@link GemfireOperations} instance used to perform Pivotal GemFire data access operations
	 * and interactions supporting the SessionRepository operations.
	 *
	 * @param template {@link GemfireOperations} instance used to interact with GemFire; must not be {@literal null}.
	 * @throws IllegalArgumentException if {@link GemfireOperations} is {@literal null}.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public AbstractGemFireOperationsSessionRepository(GemfireOperations template) {

		Assert.notNull(template, "GemfireOperations is required");

		this.template = template;
	}

	/**
	 * Constructs a new instance of {@link Log} using Apache Commons {@link LogFactory}.
	 *
	 * @return a new instance of {@link Log} constructed from Apache commons-logging {@link LogFactory}.
	 * @see org.apache.commons.logging.LogFactory#getLog(Class)
	 * @see org.apache.commons.logging.Log
	 */
	private Log newLogger() {
		return LogFactory.getLog(getClass());
	}

	/**
	 * Sets the ApplicationEventPublisher used to publish Session events corresponding to
	 * Pivotal GemFire cache events.
	 *
	 * @param applicationEventPublisher the Spring ApplicationEventPublisher used to
	 * publish Session-based events; must not be {@literal null}.
	 * @throws IllegalArgumentException if {@link ApplicationEventPublisher} is {@literal null}.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {

		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher is required");

		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * Returns a reference to the {@link ApplicationEventPublisher} used to publish {@link Session} events
	 * corresponding to GemFire/Geode cache events.
	 *
	 * @return the Spring {@link ApplicationEventPublisher} used to publish {@link Session} events.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	protected ApplicationEventPublisher getApplicationEventPublisher() {
		return this.applicationEventPublisher;
	}

	/**
	 * Returns the fully-qualified name of the cache {@link Region} used to store and manage {@link Session} state.
	 *
	 * @return a {@link String} containing the fully qualified name of the cache {@link Region}
	 * used to store and manage {@link Session} data.
	 */
	protected String getFullyQualifiedRegionName() {
		return this.fullyQualifiedRegionName;
	}

	/**
	 * Return a reference to the {@link Log} used to log messages.
	 *
	 * @return a reference to the {@link Log} used to log messages.
	 * @see org.apache.commons.logging.Log
	 */
	protected Log getLogger() {
		return this.logger;
	}

	/**
	 * Sets the {@link Duration maximum interval} in which a {@link Session} can remain inactive
	 * before the {@link Session} is considered expired.
	 *
	 * @param maxInactiveInterval {@link Duration} specifying the maximum interval that a {@link Session}
	 * can remain inactive before the {@link Session} is considered expired.
	 * @see java.time.Duration
	 */
	public void setMaxInactiveInterval(Duration maxInactiveInterval) {
		this.maxInactiveInterval = maxInactiveInterval;
	}

	/**
	 * Returns the {@link Duration maximum interval} in which a {@link Session} can remain inactive
	 * before the {@link Session} is considered expired.
	 *
	 * @return a {@link Duration} specifying the maximum interval that a {@link Session} can remain inactive
	 * before the {@link Session} is considered expired.
	 * @see java.time.Duration
	 */
	public Duration getMaxInactiveInterval() {
		return this.maxInactiveInterval;
	}

	/**
	 * Sets the maximum interval in seconds in which a {@link Session} can remain inactive
	 * before the {@link Session} is considered expired.
	 *
	 * @param maxInactiveIntervalInSeconds an integer value specifying the maximum interval in seconds
	 * that a {@link Session} can remain inactive before the {@link Session }is considered expired.
	 * @see #setMaxInactiveInterval(Duration)
	 */
	public void setMaxInactiveIntervalInSeconds(int maxInactiveIntervalInSeconds) {
		setMaxInactiveInterval(Duration.ofSeconds(maxInactiveIntervalInSeconds));
	}

	/**
	 * Returns the maximum interval in seconds in which a {@link Session} can remain inactive
	 * before the {@link Session} is considered expired.
	 *
	 * @return an integer value specifying the maximum interval in seconds that a {@link Session} can remain inactive
	 * before the {@link Session} is considered expired.
	 * @see #getMaxInactiveInterval()
	 */
	public int getMaxInactiveIntervalInSeconds() {

		return Optional.ofNullable(getMaxInactiveInterval())
			.map(Duration::getSeconds)
			.map(Long::intValue)
			.orElse(0);
	}

	/**
	 * Gets a reference to the {@link GemfireOperations template} used to perform data access operations
	 * and other interactions on the cache {@link Region} backing this {@link SessionRepository}.
	 *
	 * @return a reference to the {@link GemfireOperations template} used to interact with GemFire/Geode.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public GemfireOperations getTemplate() {
		return this.template;
	}

	/**
	 * Sets a condition indicating whether the DataSerialization framework has been configured.
	 *
	 * @param useDataSerialization boolean indicating whether the DataSerialization framework has been configured.
	 */
	public void setUseDataSerialization(boolean useDataSerialization) {
		usingDataSerialization.set(useDataSerialization);
	}

	/**
	 * Determines whether the DataSerialization framework has been configured.
	 *
	 * @return a boolean indicating whether the DataSerialization framework has been configured.
	 */
	protected static boolean isUsingDataSerialization() {
		return usingDataSerialization.get();
	}

	/**
	 * Callback method during Spring bean initialization that will capture the fully-qualified name
	 * of the cache {@link Region} used to manage {@link Session} state and register this {@link SessionRepository}
	 * as a GemFire/Geode {@link org.apache.geode.cache.CacheListener}.
	 *
	 * Additionally, this method registers GemFire/Geode {@link Instantiator Instantiators}
	 * for the {@link GemFireSession} and {@link GemFireSessionAttributes} types to optimize GemFire/Geode's
	 * instantiation logic on deserialization using the data serialization framework when accessing the stored
	 * {@link Session} state.
	 *
	 * @throws Exception if an error occurs during the initialization process.
	 */
	public void afterPropertiesSet() throws Exception {

		GemfireOperations template = getTemplate();

		Assert.isInstanceOf(GemfireAccessor.class, template);

		Region<Object, Session> region = ((GemfireAccessor) template).getRegion();

		this.fullyQualifiedRegionName = region.getFullPath();

		AttributesMutator<Object, Session> attributesMutator = region.getAttributesMutator();

		attributesMutator.addCacheListener(this);
	}

	boolean isCreate(EntryEvent<?, ?> event) {
		return isCreate(event.getOperation()) && isNotUpdate(event) && isSession(event.getNewValue());
	}

	private boolean isCreate(Operation operation) {
		return operation.isCreate() && !Operation.LOCAL_LOAD_CREATE.equals(operation);
	}

	private boolean isNotUpdate(EntryEvent event) {
		return isNotProxyRegion() || !this.cachedSessionIds.contains(ObjectUtils.nullSafeHashCode(event.getKey()));
	}

	private boolean isNotProxyRegion() {
		return !isProxyRegion();
	}

	private boolean isProxyRegion() {
		return GemFireUtils.isProxy(((GemfireAccessor) getTemplate()).getRegion());
	}

	/**
	 * Used to determine whether the application developer is storing (HTTP) Sessions with other, arbitrary
	 * application domain objects in the same Pivotal GemFire cache {@link Region}; crazier things have happened!
	 *
	 * @param obj {@link Object} to evaluate.
	 * @return a boolean value indicating whether the old/new {@link Object} from the {@link Region}
	 * {@link EntryEvent} is indeed a {@link Session}.
	 * @see org.springframework.session.Session
	 */
	private boolean isSession(Object obj) {
		return obj instanceof Session;
	}

	/**
	 * Forgets the given {@link Object session ID}.
	 *
	 * @param sessionId {@link Object} containing the session ID to forget.
	 * @return a boolean value indicating whether the given session ID was even being remembered.
	 * @see #remember(Object)
	 */
	boolean forget(Object sessionId) {
		return this.cachedSessionIds.remove(ObjectUtils.nullSafeHashCode(sessionId));
	}

	/**
	 * Remembers the given {@link Object session ID}.
	 *
	 * @param sessionId {@link Object} containing the session ID to remember.
	 * @return a boolean value whether Spring Session is interested in and will remember
	 * this given session ID.
	 * @see #forget(Object)
	 */
	@SuppressWarnings("all")
	boolean remember(Object sessionId) {
		return isProxyRegion() && this.cachedSessionIds.add(ObjectUtils.nullSafeHashCode(sessionId));
	}

	/**
	 * Casts the given {@link Object} into a {@link Session} iff the {@link Object} is a {@link Session}.
	 *
	 * Otherwise, this method attempts to use the supplied {@link String session ID} to create a {@link Session}
	 * containing only the ID.
	 *
	 * @param obj {@link Object} to evaluate as a {@link Session}.
	 * @param sessionId {@link String} containing the session ID.
	 * @return a {@link Session} from the given {@link Object}
	 * or a {@link Session} containing only the supplied {@link String session ID}.
	 * @throws IllegalStateException if the given {@link Object} is not a {@link Session}
	 * and {@link String session ID} was not supplied.
	 */
	Session toSession(Object obj, String sessionId) {

		return obj instanceof Session
			? (Session) obj
			: Optional.ofNullable(sessionId)
				.filter(StringUtils::hasText)
				.map(SessionIdHolder::create)
				.orElseThrow(() -> newIllegalStateException(
					"Minimally, the session ID [%s] must be known to trigger a Session event", sessionId));
	}
	/**
	 * Callback method triggered when an entry is created in the Pivotal GemFire cache {@link Region}.
	 *
	 * @param event {@link EntryEvent} containing the details of the cache operation.
	 * @see org.apache.geode.cache.EntryEvent
	 * @see #handleCreated(String, Session)
	 */
	@Override
	public void afterCreate(EntryEvent<Object, Session> event) {

		Optional.ofNullable(event)
			.filter(this::isCreate)
			.ifPresent(it -> {

				String sessionId = it.getKey().toString();

				handleCreated(sessionId, toSession(it.getNewValue(), sessionId));
			});
	}

	/**
	 * Callback method triggered when an entry is destroyed in the Pivotal GemFire cache {@link Region}.
	 *
	 * @param event {@link EntryEvent} containing the details of the cache operation.
	 * @see org.apache.geode.cache.EntryEvent
	 * @see #handleDestroyed(String, Session)
	 */
	@Override
	public void afterDestroy(EntryEvent<Object, Session> event) {

		Optional.ofNullable(event)
			.ifPresent(it -> {

				String sessionId = event.getKey().toString();

				handleDestroyed(sessionId, toSession(event.getOldValue(), sessionId));
			});
	}

	/**
	 * Callback method triggered when an entry is invalidated in the Pivotal GemFire cache {@link Region}.
	 *
	 * @param event {@link EntryEvent} containing the details of the cache operation.
	 * @see org.apache.geode.cache.EntryEvent
	 * @see #handleExpired(String, Session)
	 */
	@Override
	public void afterInvalidate(EntryEvent<Object, Session> event) {

		Optional.ofNullable(event)
			.ifPresent(it -> {

				String sessionId = event.getKey().toString();

				handleExpired(sessionId, toSession(event.getOldValue(), sessionId));
			});
	}

	/**
	 * Commits the given {@link Session}.
	 *
	 * @param session {@link Session} to commit, if committable.
	 * @return the given {@link Session}
	 * @see GemFireSession#commit()
	 */
	protected @Nullable Session commit(@Nullable Session session) {

		return Optional.ofNullable(session)
			.filter(GemFireSession.class::isInstance)
			.map(GemFireSession.class::cast)
			.<Session>map(gemfireSession -> {
				gemfireSession.commit();
				return gemfireSession;
			})
			.orElse(session);
	}

	/**
	 * Deletes the given {@link Session} from GemFire.
	 *
	 * @param session {@link Session} to delete.
	 * @return {@literal null}.
	 * @see org.springframework.session.Session#getId()
	 * @see #deleteById(String)
	 */
	protected Session delete(Session session) {

		deleteById(session.getId());

		return null;
	}

	/**
	 * Causes Session created events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionCreatedEvent
	 * @see org.springframework.session.Session
	 * @see #newSessionCreatedEvent(Session)
	 * @see #publishEvent(ApplicationEvent)
	 */
	protected void handleCreated(String sessionId, Session session) {
		remember(sessionId);
		publishEvent(newSessionCreatedEvent(session));
	}

	/**
	 * Causes Session deleted events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionDeletedEvent
	 * @see org.springframework.session.Session
	 * @see #newSessionDeletedEvent(Session)
	 * @see #publishEvent(ApplicationEvent)
	 * @see #forget(Object)
	 */
	protected void handleDeleted(String sessionId, Session session) {
		forget(sessionId);
		publishEvent(newSessionDeletedEvent(session));
	}

	/**
	 * Causes Session destroyed events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionDestroyedEvent
	 * @see org.springframework.session.Session
	 * @see #newSessionDestroyedEvent(Session)
	 * @see #publishEvent(ApplicationEvent)
	 * @see #forget(Object)
	 */
	protected void handleDestroyed(String sessionId, Session session) {
		forget(sessionId);
		publishEvent(newSessionDestroyedEvent(session));
	}

	/**
	 * Causes Session expired events to be published to the Spring application context.
	 *
	 * @param sessionId a String indicating the ID of the Session.
	 * @param session a reference to the Session triggering the event.
	 * @see org.springframework.session.events.SessionExpiredEvent
	 * @see org.springframework.session.Session
	 * @see #newSessionExpiredEvent(Session)
	 * @see #publishEvent(ApplicationEvent)
	 * @see #forget(Object)
	 */
	protected void handleExpired(String sessionId, Session session) {
		forget(sessionId);
		publishEvent(newSessionExpiredEvent(session));
	}

	private SessionCreatedEvent newSessionCreatedEvent(Session session) {
		return new SessionCreatedEvent(this, session);
	}

	private SessionDeletedEvent newSessionDeletedEvent(Session session) {
		return new SessionDeletedEvent(this, session);
	}

	private SessionDestroyedEvent newSessionDestroyedEvent(Session session) {
		return new SessionDestroyedEvent(this, session);
	}

	private SessionExpiredEvent newSessionExpiredEvent(Session session) {
		return new SessionExpiredEvent(this, session);
	}

	/**
	 * Publishes the specified ApplicationEvent to the Spring application context.
	 *
	 * @param event the ApplicationEvent to publish.
	 * @see org.springframework.context.ApplicationEventPublisher#publishEvent(ApplicationEvent)
	 * @see org.springframework.context.ApplicationEvent
	 */
	protected void publishEvent(ApplicationEvent event) {

		try {
			getApplicationEventPublisher().publishEvent(event);
		}
		catch (Throwable cause) {
			getLogger().error(String.format("Error occurred while publishing event [%s]", event), cause);
		}
	}

	/**
	 * Updates the {@link Session#setLastAccessedTime(Instant)} property of the {@link Session}.
	 *
	 * @param session {@link Session} to touch.
	 * @return the {@link Session}.
	 * @see org.springframework.session.Session#setLastAccessedTime(Instant)
	 * @see java.time.Instant#now()
	 */
	protected Session touch(Session session) {

		session.setLastAccessedTime(Instant.now());

		return session;
	}

	@SuppressWarnings("unused")
	public static class DeltaCapableGemFireSession
			extends GemFireSession<DeltaCapableGemFireSessionAttributes> implements Delta {

		public DeltaCapableGemFireSession() { }

		public DeltaCapableGemFireSession(String id) {
			super(id);
		}

		public DeltaCapableGemFireSession(Session session) {
			super(session);
		}

		@Override
		protected DeltaCapableGemFireSessionAttributes newSessionAttributes(Object lock) {
			return new DeltaCapableGemFireSessionAttributes();
		}

		public synchronized void toDelta(DataOutput out) throws IOException {

			out.writeUTF(getId());
			out.writeLong(getLastAccessedTime().toEpochMilli());
			out.writeLong(getMaxInactiveInterval().getSeconds());
			getAttributes().toDelta(out);
		}

		public synchronized void fromDelta(DataInput in) throws IOException {

			setId(in.readUTF());
			setLastAccessedTime(Instant.ofEpochMilli(in.readLong()));
			setMaxInactiveInterval(Duration.ofSeconds(in.readLong()));
			getAttributes().fromDelta(in);
		}
	}

	/**
	 * {@link GemFireSession} is a Abstract Data Type (ADT) for a Spring {@link Session} that stores and manages
	 * {@link Session} state in Apache Geode or Pivotal GemFire.
	 *
	 * @see java.lang.Comparable
	 * @see org.springframework.session.Session
	 */
	@SuppressWarnings("serial")
	public static class GemFireSession<T extends GemFireSessionAttributes> implements Comparable<Session>, Session {

		protected static final Duration DEFAULT_MAX_INACTIVE_INTERVAL = Duration.ZERO;

		protected static final String GEMFIRE_SESSION_TO_STRING =
			"{ @type = %1$s, id = %2$s, creationTime = %3$s, lastAccessedTime = %4$s, maxInactiveInterval = %5$s, principalName = %6$s }";

		protected static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

		/**
		 * Factory method used to create a new instance of {@link GemFireSession} initialized with
		 * the {@link #DEFAULT_MAX_INACTIVE_INTERVAL}.
		 *
		 * @return new {@link GemFireSession}.
		 * @see #create(Duration)
		 */
		public static GemFireSession create() {
			return create(DEFAULT_MAX_INACTIVE_INTERVAL);
		}

		/**
		 * Factory method used to create a new instance of {@link GemFireSession} initialized with
		 * the given {@link Duration max inactive interval}.
		 *
		 * @param maxInactiveInterval {@link Duration} specifying the max inactive interval before
		 * this {@link Session} will expire.
		 * @return a new instance of {@link GemFireSession} initialized with
		 * the given {@link Duration max inactive interval}.
		 * @see #isUsingDataSerialization()
		 * @see java.time.Duration
		 */
		public static GemFireSession create(Duration maxInactiveInterval) {

			GemFireSession session = isUsingDataSerialization()
				? new DeltaCapableGemFireSession()
				: new GemFireSession();

			session.setMaxInactiveInterval(maxInactiveInterval);

			return session;
		}

		/**
		 * Copy (i.e. clone) the given {@link Session}.
		 *
		 * @param session {@link Session} to copy/clone.
		 * @return a new instance of {@link GemFireSession} copied from the given {@link Session}.
		 * @see org.springframework.session.Session
		 * @see #isUsingDataSerialization()
		 */
		public static GemFireSession copy(@NonNull Session session) {

			return isUsingDataSerialization()
				? new DeltaCapableGemFireSession(session)
				: new GemFireSession(session);
		}

		/**
		 * Returns the given {@link Session} if the {@link Session} is a {@link GemFireSession} or return a copy
		 * of the given {@link Session} as a {@link GemFireSession}.
		 *
		 * @param <T> {@link Class sub-type} of {@link GemFireSession}.
		 * @param session {@link Session} to evaluate and possibly copy.
		 * @return the given {@link Session} if the {@link Session} is a {@link GemFireSession} or return a copy
		 * of the given {@link Session} as a {@link GemFireSession}
		 * @see #copy(Session)
		 */
		@SuppressWarnings("unchecked")
		public static <T extends GemFireSession> T from(@NonNull Session session) {
			return (T) (session instanceof GemFireSession ? session : copy(session));
		}

		private transient boolean delta = true;

		private Duration maxInactiveInterval;

		private final Instant creationTime;
		private Instant lastAccessedTime;

		private transient final SpelExpressionParser parser = new SpelExpressionParser();

		private String id;

		private transient final T sessionAttributes = newSessionAttributes(this);

		/**
		 * Constructs a new, default instance of {@link GemFireSession} initialized with
		 * a generated {@link Session#getId() Session Identifier}.
		 *
		 * @see #GemFireSession(String)
		 * @see #generateSessionId()
		 */
		protected GemFireSession() {
			this(generateSessionId());
		}

		/**
		 * Constructs a new instance of {@link GemFireSession} initialized with
		 * the given {@link Session#getId() Session Identifier}.
		 *
		 * Additionally, the {@link #creationTime} is set to {@link Instant#now()}, {@link #lastAccessedTime}
		 * is set to {@link #creationTime} and the {@link #maxInactiveInterval} is set to {@link Duration#ZERO}.
		 *
		 * @param id {@link String} containing the unique identifier for this {@link Session}.
		 * @see #validateSessionId(String)
		 */
		protected GemFireSession(String id) {

			this.id = validateSessionId(id);
			this.creationTime = Instant.now();
			this.lastAccessedTime = this.creationTime;
			this.maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL;
		}

		/**
		 * Constructs a new instance of {@link GemFireSession} copied from the given {@link Session}.
		 *
		 * @param session {@link Session} to copy.
		 * @throws IllegalArgumentException if {@link Session} is {@literal null}.
		 * @see org.springframework.session.Session
		 */
		protected GemFireSession(Session session) {

			Assert.notNull(session, "The Session to copy must not be null");

			this.id = session.getId();
			this.creationTime = session.getCreationTime();
			this.lastAccessedTime = session.getLastAccessedTime();
			this.maxInactiveInterval = session.getMaxInactiveInterval();
			this.sessionAttributes.from(session);
		}

		/**
		 * Constructs a new {@link GemFireSessionAttributes} object to store and manage Session attributes.
		 *
		 * @param lock {@link Object} used as the mutex for concurrent access and Thread-safety.
		 * @return the new {@link GemFireSessionAttributes}.
		 * @see GemFireSessionAttributes
		 */
		@SuppressWarnings("unchecked")
		protected T newSessionAttributes(Object lock) {
			return (T) new GemFireSessionAttributes(lock);
		}

		/**
		 * Change the {@link String identifier} of this {@link Session}.
		 *
		 * @return the new {@link String identifier} of of this {@link Session}.
		 * @see #generateSessionId()
		 * @see #triggerDelta()
		 * @see #getId()
		 */
		@Override
		public synchronized String changeSessionId() {

			this.id = generateSessionId();

			triggerDelta();

			return getId();
		}

		/**
		 * Randomly generates a {@link String unique identifier} (ID) from {@link UUID} to be used as
		 * the {@link Session#getId() ID} for this {@link Session}.
		 *
		 * @return a new {@link String unique identifier (ID)}.
		 * @see java.util.UUID#randomUUID()
		 */
		private static String generateSessionId() {
			return UUID.randomUUID().toString();
		}

		/**
		 * Validates the given {@link Session} {@link String identifier} (ID) is set and valid.
		 *
		 * @param id {@link String} containing the {@link Session} identifier.
		 * @return the given {@link String ID}.
		 * @throws IllegalArgumentException if {@link String} contains no value.
		 */
		private static String validateSessionId(String id) {

			Assert.hasText(id, "ID is required");

			return id;
		}

		protected synchronized void commit() {
			this.delta = false;
			getAttributes().commit();
		}

		public synchronized boolean hasDelta() {
			return this.delta || getAttributes().hasDelta();
		}

		protected synchronized void triggerDelta() {
			triggerDelta(true);
		}

		protected synchronized void triggerDelta(boolean delta) {
			this.delta |= delta;
		}

		synchronized void setId(String id) {
			this.id = validateSessionId(id);
		}

		public synchronized String getId() {
			return this.id;
		}

		public void setAttribute(String attributeName, Object attributeValue) {
			getAttributes().setAttribute(attributeName, attributeValue);
		}

		public void removeAttribute(String attributeName) {
			getAttributes().removeAttribute(attributeName);
		}

		public <T> T getAttribute(String attributeName) {
			return getAttributes().getAttribute(attributeName);
		}

		public Set<String> getAttributeNames() {
			return getAttributes().getAttributeNames();
		}

		public T getAttributes() {
			return this.sessionAttributes;
		}

		public synchronized Instant getCreationTime() {
			return this.creationTime;
		}

		public synchronized boolean isExpired() {

			Instant lastAccessedTime = getLastAccessedTime();

			Duration maxInactiveInterval = getMaxInactiveInterval();

			return isExpirationEnabled(maxInactiveInterval)
				&& Instant.now().minus(maxInactiveInterval).isAfter(lastAccessedTime);
		}

		private boolean isExpirationDisabled(Duration duration) {
			return duration == null || duration.isNegative() || duration.isZero();
		}

		private boolean isExpirationEnabled(Duration duration) {
			return !isExpirationDisabled(duration);
		}

		private boolean isLastAccessedTimeValid(Instant lastAccessedTime) {
			return lastAccessedTime != null;
		}

		public synchronized void setLastAccessedTime(Instant lastAccessedTime) {

			if (isLastAccessedTimeValid(lastAccessedTime)) {

				triggerDelta(!ObjectUtils.nullSafeEquals(this.lastAccessedTime, lastAccessedTime));

				this.lastAccessedTime = lastAccessedTime;
			}
		}

		public synchronized Instant getLastAccessedTime() {
			return this.lastAccessedTime;
		}

		public synchronized void setMaxInactiveInterval(Duration maxInactiveInterval) {

			triggerDelta(!ObjectUtils.nullSafeEquals(this.maxInactiveInterval, maxInactiveInterval));

			this.maxInactiveInterval = maxInactiveInterval;
		}

		public synchronized Duration getMaxInactiveInterval() {

			return Optional.ofNullable(this.maxInactiveInterval)
				.orElse(DEFAULT_MAX_INACTIVE_INTERVAL);
		}

		public synchronized void setPrincipalName(String principalName) {
			setAttribute(PRINCIPAL_NAME_INDEX_NAME, principalName);
		}

		public synchronized String getPrincipalName() {

			String principalName = getAttribute(PRINCIPAL_NAME_INDEX_NAME);

			if (principalName == null) {

				Object authentication = getAttribute(SPRING_SECURITY_CONTEXT);

				if (authentication != null) {

					Expression expression = this.parser.parseExpression("authentication?.name");

					principalName = expression.getValue(authentication, String.class);
				}
			}

			return principalName;
		}

		@SuppressWarnings("all")
		@Override
		public int compareTo(Session session) {
			return getCreationTime().compareTo(session.getCreationTime());
		}

		@Override
		public boolean equals(final Object obj) {

			if (this == obj) {
				return true;
			}

			if (!(obj instanceof Session)) {
				return false;
			}

			Session that = (Session) obj;

			return this.getId().equals(that.getId());
		}

		@Override
		public int hashCode() {

			int hashValue = 17;

			hashValue = 37 * hashValue + getId().hashCode();

			return hashValue;
		}

		@Override
		public synchronized String toString() {

			return String.format(GEMFIRE_SESSION_TO_STRING, getClass().getName(), getId(), getCreationTime(),
				getLastAccessedTime(), getMaxInactiveInterval(), getPrincipalName());
		}
	}

	@SuppressWarnings("unused")
	public static class DeltaCapableGemFireSessionAttributes extends GemFireSessionAttributes implements Delta {

		private transient final Map<String, Object> sessionAttributeDeltas = new HashMap<>();

		public DeltaCapableGemFireSessionAttributes() { }

		public DeltaCapableGemFireSessionAttributes(Object lock) {
			super(lock);
		}

		protected Map<String, Object> getSessionAttributeDeltas() {

			synchronized (getLock()) {
				return this.sessionAttributeDeltas;
			}
		}

		@Override
		public Object setAttribute(String attributeName, Object attributeValue) {

			synchronized (getLock()) {

				if (attributeValue != null) {

					Object previousAttributeValue = super.setAttribute(attributeName, attributeValue);

					if (!attributeValue.equals(previousAttributeValue)) {
						getSessionAttributeDeltas().put(attributeName, attributeValue);
					}

					return previousAttributeValue;
				}
				else {
					return removeAttribute(attributeName);
				}
			}
		}

		@Override
		public Object removeAttribute(String attributeName) {

			synchronized (getLock()) {

				return Optional.ofNullable(super.removeAttribute(attributeName))
					.map(previousAttributeValue -> {
						getSessionAttributeDeltas().put(attributeName, null);
						return previousAttributeValue;
					})
					.orElse(null);
			}
		}

		public void toDelta(DataOutput out) throws IOException {

			synchronized (getLock()) {

				Map<String, Object> sessionAttributeDeltas = getSessionAttributeDeltas();

				out.writeInt(sessionAttributeDeltas.size());

				for (Entry<String, Object> entry : sessionAttributeDeltas.entrySet()) {
					out.writeUTF(entry.getKey());
					writeObject(entry.getValue(), out);
				}
			}
		}

		protected void writeObject(Object value, DataOutput out) throws IOException {
			DataSerializer.writeObject(value, out);
		}

		@Override
		public boolean hasDelta() {

			synchronized (getLock()) {
				return !getSessionAttributeDeltas().isEmpty();
			}
		}

		public void fromDelta(DataInput in) throws InvalidDeltaException, IOException {

			synchronized (getLock()) {
				try {

					int count = in.readInt();

					Map<String, Object> deltas = new HashMap<>(count);

					while (count-- > 0) {
						deltas.put(in.readUTF(), readObject(in));
					}

					Map<String, Object> sessionAttributeDeltas = getSessionAttributeDeltas();

					deltas.forEach((key, value) -> {
						setAttribute(key, value);
						sessionAttributeDeltas.remove(key);
					});
				}
				catch (ClassNotFoundException cause) {
					throw new InvalidDeltaException("Class type in data not found", cause);
				}
			}
		}

		protected <T> T readObject(DataInput in) throws ClassNotFoundException, IOException {
			return DataSerializer.readObject(in);
		}

		@Override
		protected void commit() {

			synchronized (getLock()) {
				getSessionAttributeDeltas().clear();
				super.commit();
			}
		}
	}

	/**
	 * The {@link GemFireSessionAttributes} class is a container for Session attributes implementing
	 * both the {@link DataSerializable} and {@link Delta} Pivotal GemFire interfaces for efficient
	 * storage and distribution (replication) in GemFire. Additionally, GemFireSessionAttributes
	 * extends {@link AbstractMap} providing {@link Map}-like behavior since attributes of a Session
	 * are effectively a name to value mapping.
	 *
	 * @see java.util.AbstractMap
	 * @see org.apache.geode.DataSerializable
	 * @see org.apache.geode.DataSerializer
	 * @see org.apache.geode.Delta
	 */
	@SuppressWarnings("serial")
	public static class GemFireSessionAttributes extends AbstractMap<String, Object> {

		public static GemFireSessionAttributes create() {
			return new GemFireSessionAttributes();
		}

		public static GemFireSessionAttributes create(Object lock) {
			return new GemFireSessionAttributes(lock);
		}

		private transient boolean delta = false;

		private transient final Map<String, Object> sessionAttributes = new HashMap<>();

		private transient final Object lock;

		/**
		 * Constructs a new instance of {@link GemFireSessionAttributes}.
		 */
		protected GemFireSessionAttributes() {
			this.lock = this;
		}

		/**
		 * Constructs a new instance of {@link GemFireSessionAttributes} initialized with the given {@link Object lock}
		 * to use to guard against concurrent access by multiple {@link Thread Threads}.
		 *
		 * @param lock {@link Object} used as the {@literal mutex} to guard the operations of this object
		 * from concurrent access by multiple {@link Thread Threads}.
		 */
		protected GemFireSessionAttributes(@Nullable Object lock) {
			this.lock = lock != null ? lock : this;
		}

		/**
		 * Returns the {@link Object} used as the {@literal lock} guarding the methods of this object
		 * from concurrent access by multiple {@link Thread Threads}.
		 *
		 * @return the {@link Object lock} guarding the methods of this object from concurrent access
		 * by multiple {@link Thread Threads}.
		 */
		public Object getLock() {
			return this.lock;
		}

		public Object setAttribute(String attributeName, Object attributeValue) {

			synchronized (getLock()) {
				return attributeValue != null
					? doSetAttribute(attributeName, attributeValue)
					: removeAttribute(attributeName);
			}
		}

		private Object doSetAttribute(String attributeName, Object attributeValue) {

			Object previousAttributeValue = this.sessionAttributes.put(attributeName, attributeValue);

			this.delta |= !attributeValue.equals(previousAttributeValue);

			return previousAttributeValue;
		}

		public Object removeAttribute(String attributeName) {

			synchronized (getLock()) {

				this.delta |= this.sessionAttributes.containsKey(attributeName);

				return this.sessionAttributes.remove(attributeName);
			}
		}

		@SuppressWarnings("unchecked")
		public <T> T getAttribute(String attributeName) {

			synchronized (getLock()) {
				return (T) this.sessionAttributes.get(attributeName);
			}
		}

		public Set<String> getAttributeNames() {

			synchronized (getLock()) {
				return Collections.unmodifiableSet(this.sessionAttributes.keySet());
			}
		}

		@Override
		@SuppressWarnings("all")
		public Set<Entry<String, Object>> entrySet() {

			synchronized (getLock()) {

				return new AbstractSet<Entry<String, Object>>() {

					@Override
					public Iterator<Entry<String, Object>> iterator() {
						return Collections.unmodifiableMap(GemFireSessionAttributes.this.sessionAttributes)
							.entrySet().iterator();
					}

					@Override
					public int size() {
						return GemFireSessionAttributes.this.sessionAttributes.size();
					}
				};
			}
		}

		protected void commit() {

			synchronized (getLock()) {
				this.delta = false;
			}
		}

		public void from(Session session) {

			synchronized (getLock()) {
				session.getAttributeNames().forEach(attributeName ->
					setAttribute(attributeName, session.getAttribute(attributeName)));
			}
		}

		public void from(Map<String, Object> map) {

			synchronized (getLock()) {
				map.forEach(this::setAttribute);
			}
		}

		public void from(GemFireSessionAttributes sessionAttributes) {

			synchronized (getLock()) {
				sessionAttributes.getAttributeNames().forEach(attributeName ->
					setAttribute(attributeName, sessionAttributes.getAttribute(attributeName)));
			}
		}

		public boolean hasDelta() {

			synchronized (getLock()) {
				return this.delta;
			}

		}
		@Override
		public String toString() {

			synchronized (getLock()) {
				return this.sessionAttributes.toString();
			}
		}
	}
}
