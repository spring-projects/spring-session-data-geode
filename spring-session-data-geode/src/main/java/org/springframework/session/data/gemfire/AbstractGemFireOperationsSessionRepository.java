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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import org.apache.geode.DataSerializable;
import org.apache.geode.DataSerializer;
import org.apache.geode.Delta;
import org.apache.geode.InvalidDeltaException;
import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.InterestResultPolicy;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.util.CacheListenerAdapter;

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
import org.springframework.session.data.gemfire.support.IsDirtyPredicate;
import org.springframework.session.data.gemfire.support.SessionIdHolder;
import org.springframework.session.data.gemfire.support.SessionUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link AbstractGemFireOperationsSessionRepository} is an abstract base class encapsulating functionality
 * common to all implementations that support {@link SessionRepository} operations backed by Apache Geode.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see java.time.Instant
 * @see org.apache.geode.DataSerializable
 * @see org.apache.geode.DataSerializer
 * @see org.apache.geode.Delta
 * @see org.apache.geode.cache.EntryEvent
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.util.CacheListenerAdapter
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.ApplicationEventPublisher
 * @see org.springframework.context.ApplicationEventPublisherAware
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.FindByIndexNameSessionRepository
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.support.DeltaAwareDirtyPredicate
 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
 * @see org.springframework.session.data.gemfire.support.SessionIdHolder
 * @see org.springframework.session.events.AbstractSessionEvent
 * @see org.springframework.session.events.SessionCreatedEvent
 * @see org.springframework.session.events.SessionDeletedEvent
 * @see org.springframework.session.events.SessionDestroyedEvent
 * @see org.springframework.session.events.SessionExpiredEvent
 * @since 1.1.0
 */
public abstract class AbstractGemFireOperationsSessionRepository
		implements ApplicationEventPublisherAware, FindByIndexNameSessionRepository<Session> {

	private static final boolean DEFAULT_REGISTER_INTEREST_DURABILITY = false;
	private static final boolean DEFAULT_REGISTER_INTEREST_ENABLED = false;
	private static final boolean DEFAULT_REGISTER_INTEREST_RECEIVE_VALUES = true;

	// TODO - use non-static variable
	private static final AtomicBoolean usingDataSerialization = new AtomicBoolean(false);

	private static final Duration DEFAULT_MAX_INACTIVE_INTERVAL =
		Duration.ofSeconds(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

	private static final InterestResultPolicy DEFAULT_REGISTER_INTEREST_RESULT_POLICY = InterestResultPolicy.NONE;

	private static final IsDirtyPredicate DEFAULT_IS_DIRTY_PREDICATE =
		GemFireHttpSessionConfiguration.DEFAULT_IS_DIRTY_PREDICATE;

	private boolean registerInterestEnabled = DEFAULT_REGISTER_INTEREST_ENABLED;

	private ApplicationEventPublisher applicationEventPublisher = event -> {};

	private Duration maxInactiveInterval = DEFAULT_MAX_INACTIVE_INTERVAL;

	private final GemfireOperations template;

	private IsDirtyPredicate dirtyPredicate = DEFAULT_IS_DIRTY_PREDICATE;

	private final Logger logger = newLogger();

	private final Region<Object, Session> sessions;

	private SessionEventHandlerCacheListenerAdapter sessionEventHandler;

	private final Set<Integer> interestingSessionIds = new ConcurrentSkipListSet<>();

	/**
	 * Protected, default constructor used by extensions of {@link AbstractGemFireOperationsSessionRepository}
	 * in order to affect and assess {@link SessionRepository} configuration and state.
	 */
	protected AbstractGemFireOperationsSessionRepository() {

		this.sessions = null;
		this.template = null;
	}

	/**
	 * Constructs a new instance of {@link AbstractGemFireOperationsSessionRepository} initialized with a required
	 * {@link GemfireOperations} object, which is used to perform Apache Geode or Pivotal GemFire data access operations
	 * on the cache {@link Region} storing and managing {@link Session} state to support this {@link SessionRepository}
	 * and its operations.
	 *
	 * @param template {@link GemfireOperations} object used to interact with the Apache Geode or Pivotal GemFire
	 * cache {@link Region} storing and managing {@link Session} state; must not be {@literal null}.
	 * @throws IllegalArgumentException if {@link GemfireOperations} is {@literal null}.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 * @see #resolveSessionsRegion(GemfireOperations)
	 * @see #initializeSessionsRegion(Region)
	 * @see #newLogger()
	 */
	public AbstractGemFireOperationsSessionRepository(GemfireOperations template) {

		Assert.notNull(template, "GemfireOperations is required");

		this.template = template;
		this.sessions = initializeSessionsRegion(resolveSessionsRegion(template));
	}

	/**
	 * Resolves the cache {@link Region} used to store and manage {@link Session} state
	 * from the given {@link GemfireOperations} object.
	 *
	 * @param gemfireOperations {@link GemfireOperations} object used to resolve the {@link Session} {@link Region}.
	 * @return the resolve cache {@link Region} used to store and manage {@link Session} state.
	 * @throws IllegalStateException if the {@link Session Sessions} {@link Region} could not be resolved.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 * @see org.springframework.session.Session
	 * @see org.apache.geode.cache.Region
	 */
	private Region<Object, Session> resolveSessionsRegion(@Nullable GemfireOperations gemfireOperations) {

		return Optional.ofNullable(gemfireOperations)
			.filter(GemfireAccessor.class::isInstance)
			.map(GemfireAccessor.class::cast)
			.<Region<Object, Session>>map(GemfireAccessor::getRegion)
			.orElseThrow(() -> newIllegalStateException("The ClusteredSpringSessions Region could not be resolved"));
	}

	/**
	 * Initializes the cache {@link Region} used to store and manage {@link Session} state and register this
	 * {@link SessionRepository} as an Apache Geode / Pivotal GemFire {@link org.apache.geode.cache.CacheListener}.
	 *
	 * @param sessionsRegion {@link Region} to initialize.
	 * @return the given {@link Region}.
	 * @see org.apache.geode.cache.Region
	 * @see #newSessionEventHandler()
	 * @see #newSessionIdInterestRegistrar()
	 */
	private Region<Object, Session> initializeSessionsRegion(@Nullable Region<Object, Session> sessionsRegion) {

		Optional.ofNullable(sessionsRegion)
			.map(Region::getAttributesMutator)
			.ifPresent(sessionsRegionAttributesMutator -> {

				this.sessionEventHandler = newSessionEventHandler();

				sessionsRegionAttributesMutator.addCacheListener(this.sessionEventHandler);

				if (GemFireUtils.isNonLocalClientRegion(sessionsRegion)) {
					this.registerInterestEnabled = true;
					sessionsRegionAttributesMutator.addCacheListener(newSessionIdInterestRegistrar());
				}
			});

		return sessionsRegion;
	}

	/**
	 * Constructs a new instance of {@link Logger} using Apache Commons {@link LoggerFactory}.
	 *
	 * @return a new instance of {@link Logger} constructed from Apache commons-logging {@link LoggerFactory}.
	 * @see org.apache.commons.logging.LogFactory#getLog(Class)
	 * @see org.apache.commons.logging.Log
	 */
	private Logger newLogger() {
		return LoggerFactory.getLogger(getClass());
	}

	/**
	 * Constructs a new instance of {@link SessionEventHandlerCacheListenerAdapter}.
	 *
	 * @return a new instance of {@link SessionEventHandlerCacheListenerAdapter}.
	 * @see SessionEventHandlerCacheListenerAdapter
	 */
	protected SessionEventHandlerCacheListenerAdapter newSessionEventHandler() {
		return new SessionEventHandlerCacheListenerAdapter(this);
	}

	/**
	 * Constructs a new instance of {@link SessionIdInterestRegisteringCacheListener}.
	 *
	 * @return a new instance of {@link SessionIdInterestRegisteringCacheListener}.
	 * @see SessionIdInterestRegisteringCacheListener
	 */
	protected SessionIdInterestRegisteringCacheListener newSessionIdInterestRegistrar() {
		return new SessionIdInterestRegisteringCacheListener(this);
	}

	/**
	 * Sets the configured {@link ApplicationEventPublisher} used to publish {@link Session}
	 * {@link AbstractSessionEvent events} corresponding to Apache Geode/Pivotal GemFire cache events.
	 *
	 * @param applicationEventPublisher {@link ApplicationEventPublisher} used to publish {@link Session}-based events;
	 * must not be {@literal null}.
	 * @throws IllegalArgumentException if {@link ApplicationEventPublisher} is {@literal null}.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	public void setApplicationEventPublisher(@NonNull ApplicationEventPublisher applicationEventPublisher) {

		Assert.notNull(applicationEventPublisher, "ApplicationEventPublisher is required");

		this.applicationEventPublisher = applicationEventPublisher;
	}

	/**
	 * Returns a reference to the configured {@link ApplicationEventPublisher} used to publish {@link Session}
	 * {@link AbstractSessionEvent events} corresponding to Apache Geode/Pivotal GemFire cache events.
	 *
	 * @return the configured {@link ApplicationEventPublisher} used to publish {@link Session}
	 * {@link AbstractSessionEvent events}.
	 * @see org.springframework.context.ApplicationEventPublisher
	 */
	protected @NonNull ApplicationEventPublisher getApplicationEventPublisher() {
		return this.applicationEventPublisher;
	}

	/**
	 * Configures the {@link IsDirtyPredicate} strategy interface used to determine whether the users' application
	 * domain objects are dirty or not.
	 *
	 * @param dirtyPredicate {@link IsDirtyPredicate} strategy interface implementation used to determine whether
	 * the users' application domain objects are dirty or not.
	 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
	 */
	public void setIsDirtyPredicate(IsDirtyPredicate dirtyPredicate) {
		this.dirtyPredicate = dirtyPredicate;
	}

	/**
	 * Returns the configured {@link IsDirtyPredicate} strategy interface implementation used to determine whether
	 * the users' application domain objects are dirty or not.
	 *
	 * Defaults to {@link GemFireHttpSessionConfiguration#DEFAULT_IS_DIRTY_PREDICATE}.
	 *
	 * @return the configured {@link IsDirtyPredicate} strategy interface used to determine whether
	 * the users' application domain objects are dirty or not.
	 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
	 */
	public IsDirtyPredicate getIsDirtyPredicate() {

		return this.dirtyPredicate != null
			? this.dirtyPredicate
			: DEFAULT_IS_DIRTY_PREDICATE;
	}

	/**
	 * Return a reference to the {@link Logger} used to log messages.
	 *
	 * @return a reference to the {@link Logger} used to log messages.
	 * @see org.apache.commons.logging.Log
	 */
	protected Logger getLogger() {
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
	 * Determines whether {@link Region} {@literal register interest} is enabled
	 * in the current Apache Geode / Pivotal GemFire configuration.
	 *
	 * @return a boolean value indicating whether interest registration is enabled.
	 */
	protected boolean isRegisterInterestEnabled() {
		return this.registerInterestEnabled;
	}

	protected Optional<SessionEventHandlerCacheListenerAdapter> getSessionEventHandler() {
		return Optional.ofNullable(this.sessionEventHandler);
	}

	/**
	 * Returns a reference to the configured Apache Geode / Pivotal GemFire cache {@link Region} used to
	 * store and manage (HTTP) {@link Session} data.
	 *
	 * @return a reference to the configured {@link Session Sessions} {@link Region}.
	 * @see org.springframework.session.Session
	 * @see org.apache.geode.cache.Region
	 */
	protected @NonNull Region<Object, Session> getSessionsRegion() {
		return this.sessions;
	}

	/**
	 * Returns the {@link String fully-qualified name} of the cache {@link Region} used to store
	 * and manage {@link Session} state.
	 *
	 * @return a {@link String} containing the fully qualified name of the cache {@link Region}
	 * used to store and manage {@link Session} data.
	 * @see #getSessionsRegion()
	 */
	protected String getSessionsRegionName() {
		return getSessionsRegion().getFullPath();
	}

	/**
	 * Returns a reference to the {@link GemfireOperations template} used to perform data access operations
	 * and other interactions on the cache {@link Region} storing and managing {@link Session} state
	 * and backing this {@link SessionRepository}.
	 *
	 * @return a reference to the {@link GemfireOperations template} used to interact the {@link Region}
	 * storing and managing {@link Session} state.
	 * @see org.springframework.data.gemfire.GemfireOperations
	 */
	public @NonNull GemfireOperations getSessionsTemplate() {
		return this.template;
	}

	/**
	 * @deprecated use {@link #getSessionsTemplate()}.
	 */
	@Deprecated
	public @NonNull GemfireOperations getTemplate() {
		return getSessionsTemplate();
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
	 * Commits the given {@link Session}.
	 *
	 * @param session {@link Session} to commit, iff the {@link Session} is {@literal committable}.
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

	protected @Nullable Session configure(@Nullable Session session) {

		return Optional.ofNullable(session)
			.filter(GemFireSession.class::isInstance)
			.map(GemFireSession.class::cast)
			.map(it -> it.configureWith(getMaxInactiveInterval()))
			.<Session>map(it -> it.configureWith(getIsDirtyPredicate()))
			.orElse(session);
	}

	/**
	 * Deletes the given {@link Session} from Apache Geode / Pivotal GemFire.
	 *
	 * @param session {@link Session} to delete.
	 * @return {@literal null}.
	 * @see org.springframework.session.Session#getId()
	 * @see org.springframework.session.Session
	 * @see #deleteById(String)
	 */
	protected @Nullable Session delete(@NonNull Session session) {

		deleteById(session.getId());

		return null;
	}

	/**
	 * Handles the deletion of the given {@link Session}.
	 *
	 * @param sessionId {@link String} containing the {@link Session#getId()} of the given {@link Session}.
	 * @param session deleted {@link Session}.
	 * @see SessionEventHandlerCacheListenerAdapter#afterDelete(String, Session)
	 * @see org.springframework.session.Session
	 * @see #unregisterInterest(Object)
	 */
	protected void handleDeleted(String sessionId, Session session) {

		getSessionEventHandler()
			.ifPresent(it -> it.afterDelete(sessionId, session));

		unregisterInterest(sessionId);
	}

	/**
	 * Publishes the specified {@link ApplicationEvent} to the Spring container thereby notifying other (potentially)
	 * interested application components/beans.
	 *
	 * @param event {@link ApplicationEvent} to publish.
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
	 * Registers interest in the given {@link Session} in order to receive notifications and updates.
	 *
	 * @param session {@link Session} of interest to this application that will be registered.
	 * @return the given {@link Session}.
	 * @see org.springframework.session.Session#getId()
	 * @see org.springframework.session.Session
	 * @see #registerInterest(Object)
	 */
	protected Session registerInterest(@Nullable Session session) {

		Optional.ofNullable(session)
			.map(Session::getId)
			.ifPresent(this::registerInterest);

		return session;
	}

	/**
	 * Registers interest on the {@link Session#getId()} ID} of a {@link Session}.
	 *
	 * And, only registers interest in the given Session ID iff we have not already registered interest
	 * in this Session ID before.
	 *
	 * @param sessionId {@link Session#getId() ID} of the {@link Session} of interest to this application.
	 * @see org.apache.geode.cache.Region#registerInterest(Object, InterestResultPolicy, boolean, boolean)
	 * @see #isRegisterInterestEnabled()
	 */
	protected void registerInterest(@Nullable Object sessionId) {

		Optional.ofNullable(sessionId)
			.filter(it -> this.isRegisterInterestEnabled())
			.filter(SessionUtils::isValidSessionId)
			.map(ObjectUtils::nullSafeHashCode)
			.filter(this.interestingSessionIds::add)
			.ifPresent(it ->
				getSessionsRegion().registerInterest(sessionId, DEFAULT_REGISTER_INTEREST_RESULT_POLICY,
					DEFAULT_REGISTER_INTEREST_DURABILITY, DEFAULT_REGISTER_INTEREST_RECEIVE_VALUES)
			);
	}

	/**
	 * Updates the {@link Session#setLastAccessedTime(Instant)} property of the {@link Session}
	 * to the {@link Instant#now() current time}.
	 *
	 * @param session {@link Session} to touch.
	 * @return the {@link Session}.
	 * @see org.springframework.session.Session#setLastAccessedTime(Instant)
	 * @see org.springframework.session.Session
	 * @see java.time.Instant#now()
	 */
	protected @NonNull Session touch(@NonNull Session session) {

		session.setLastAccessedTime(Instant.now());

		return session;
	}

	/**
	 * Unregisters interest in the given {@link Session} in order to stop notifications and updates.
	 *
	 * @param session {@link Session} no longer of any interest to this application that will be unregistered.
	 * @return the given {@link Session}.
	 * @see org.springframework.session.Session#getId()
	 * @see org.springframework.session.Session
	 * @see #unregisterInterest(Object)
	 */
	@SuppressWarnings("unused")
	protected Session unregisterInterest(@Nullable Session session) {

		Optional.ofNullable(session)
			.map(Session::getId)
			.ifPresent(this::unregisterInterest);

		return session;
	}

	/**
	 * Unregisters interest on the {@link Session#getId()} ID} of a {@link Session}.
	 *
	 * @param sessionId {@link Session#getId() ID} of the {@link Session} no longer of any interest
	 * to this application.
	 * @see org.apache.geode.cache.Region#unregisterInterest(Object)
	 * @see #isRegisterInterestEnabled()
	 */
	protected void unregisterInterest(@Nullable Object sessionId) {

		Optional.ofNullable(sessionId)
			.filter(it -> this.isRegisterInterestEnabled())
			.map(ObjectUtils::nullSafeHashCode)
			.filter(this.interestingSessionIds::remove)
			.ifPresent(it -> getSessionsRegion().unregisterInterest(sessionId));
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

			return new DeltaCapableGemFireSessionAttributes(lock)
				.configureWith(getIsDirtyPredicate());
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
	 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
	 */
	@SuppressWarnings("serial")
	public static class GemFireSession<T extends GemFireSessionAttributes> implements Comparable<Session>, Session {

		protected static final String GEMFIRE_SESSION_TO_STRING =
			"{ @type = %1$s, id = %2$s, creationTime = %3$s, lastAccessedTime = %4$s, maxInactiveInterval = %5$s, principalName = %6$s }";

		protected static final String SPRING_SECURITY_CONTEXT = "SPRING_SECURITY_CONTEXT";

		/**
		 * Factory method used to construct a new, default instance of {@link GemFireSession}.
		 *
		 * @param <T> {@link Class Sub-type} of {@link GemFireSessionAttributes}.
		 * @return a new {@link GemFireSession}.
		 * @see #isUsingDataSerialization()
		 */
		@SuppressWarnings("unchecked")
		public static <T extends GemFireSessionAttributes> GemFireSession<T> create() {

			return isUsingDataSerialization()
				? (GemFireSession<T>) new DeltaCapableGemFireSession()
				: new GemFireSession();
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
		 * Returns the given {@link Session} if the {@link Session} is a {@link GemFireSession}
		 * or return a copy of the given {@link Session} as a {@link GemFireSession}.
		 *
		 * @param session {@link Session} to evaluate and possibly copy.
		 * @return the given {@link Session} if the {@link Session} is a {@link GemFireSession}
		 * or return a copy of the given {@link Session} as a {@link GemFireSession}.
		 * @see #copy(Session)
		 */
		@SuppressWarnings("unchecked")
		public static GemFireSession from(@NonNull Session session) {
			return session instanceof GemFireSession ? (GemFireSession) session : copy(session);
		}

		private transient boolean delta = true;

		private Duration maxInactiveInterval;

		private final Instant creationTime;

		private Instant lastAccessedTime;

		private transient IsDirtyPredicate dirtyPredicate = DEFAULT_IS_DIRTY_PREDICATE;

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
			this.maxInactiveInterval = Duration.ZERO;
		}

		/**
		 * Constructs a new instance of {@link GemFireSession} copied from the given {@link Session}.
		 *
		 * @param session {@link Session} to copy.
		 * @throws IllegalArgumentException if {@link Session} is {@literal null}.
		 * @see org.springframework.session.Session
		 */
		protected GemFireSession(Session session) {

			Assert.notNull(session, "Session is required");

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
		 * @see #getIsDirtyPredicate()
		 */
		@SuppressWarnings("unchecked")
		protected T newSessionAttributes(Object lock) {

			return (T) new GemFireSessionAttributes(lock)
				.configureWith(getIsDirtyPredicate());
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

		/**
		 * Determines whether this {@link GemFireSession} has any changes (i.e. a delta).
		 *
		 * Changes exist if this {@link GemFireSession GemFireSession's} {@link #getId() ID},
		 * {@link #getLastAccessedTime() last accessed time}, {@link #getMaxInactiveInterval() max inactive interval}
		 * or any of these {@link #getAttributeNames() attributes} have changed.
		 *
		 * @return a boolean value indicating whether this {@link GemFireSession} has any changes.
		 * @see GemFireSessionAttributes#hasDelta()
		 * @see #getAttributes()
		 */
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

		protected synchronized void setIsDirtyPredicate(IsDirtyPredicate dirtyPredicate) {

			this.dirtyPredicate = dirtyPredicate;
			getAttributes().configureWith(dirtyPredicate);
		}

		protected synchronized IsDirtyPredicate getIsDirtyPredicate() {

			return this.dirtyPredicate != null
				? this.dirtyPredicate
				: DEFAULT_IS_DIRTY_PREDICATE;
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

			return this.maxInactiveInterval != null
				? this.maxInactiveInterval
				: Duration.ZERO;
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

		/**
		 * Builder method to configure the {@link Duration max inactive interval} before this {@link GemFireSession}
		 * will expire.
		 *
		 * @param maxInactiveInterval {@link Duration} specifying the maximum time this {@link GemFireSession}
		 * can remain inactive before expiration.
		 * @return this {@link GemFireSession}.
		 * @see #setMaxInactiveInterval(Duration)
		 * @see java.time.Duration
		 */
		public GemFireSession<T> configureWith(Duration maxInactiveInterval) {
			setMaxInactiveInterval(maxInactiveInterval);
			return this;
		}

		/**
		 * Builder method to configure the {@link IsDirtyPredicate} strategy interface implementation to determine
		 * whether users' {@link Object application domain objects} stored in this {@link GemFireSession} are dirty.
		 *
		 * @param dirtyPredicate {@link IsDirtyPredicate} strategy interface implementation that determines whether
		 * the users' {@link Object application domain objects} stored in this {@link GemFireSession} are dirty.
		 * @return this {@link GemFireSession}.
		 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
		 * @see #setIsDirtyPredicate(IsDirtyPredicate)
		 */
		public GemFireSession<T> configureWith(IsDirtyPredicate dirtyPredicate) {
			setIsDirtyPredicate(dirtyPredicate);
			return this;
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

	public static class DeltaCapableGemFireSessionAttributes extends GemFireSessionAttributes implements Delta {

		private transient final Set<String> sessionAttributeDeltas = new HashSet<>();

		public DeltaCapableGemFireSessionAttributes() { }

		public DeltaCapableGemFireSessionAttributes(Object lock) {
			super(lock);
		}

		Set<String> getSessionAttributeDeltas() {

			synchronized (getLock()) {
				return this.sessionAttributeDeltas;
			}
		}

		@Override
		protected BiFunction<String, Object, Boolean> sessionAttributesChangeInterceptor() {

			return (attributeName, attributeValue) -> {
				getSessionAttributeDeltas().add(attributeName);
				return true;
			};
		}

		public void toDelta(DataOutput out) throws IOException {

			synchronized (getLock()) {

				Set<String> sessionAttributeDeltas = getSessionAttributeDeltas();

				out.writeInt(sessionAttributeDeltas.size());

				for (String attributeName : sessionAttributeDeltas) {
					out.writeUTF(attributeName);
					writeObject(getAttribute(attributeName), out);
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

					Set<String> sessionAttributeDeltas = getSessionAttributeDeltas();

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

		private transient IsDirtyPredicate dirtyPredicate = DEFAULT_IS_DIRTY_PREDICATE;

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
		 * Returns a reference to the internal, {@link Session} attributes data structure.
		 *
		 * @return a reference to the internal, {@link Session} attributes data structure.
		 * @see java.util.Map
		 */
		Map<String, Object> getMap() {
			return this.sessionAttributes;
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

		protected void setIsDirtyPredicate(IsDirtyPredicate dirtyPredicate) {

			synchronized (getLock()) {
				this.dirtyPredicate = dirtyPredicate;
			}
		}

		protected IsDirtyPredicate getIsDirtyPredicate() {

			synchronized (getLock()) {
				return this.dirtyPredicate != null
					? this.dirtyPredicate
					: DEFAULT_IS_DIRTY_PREDICATE;
			}
		}

		public Object setAttribute(String attributeName, Object attributeValue) {

			synchronized (getLock()) {
				return attributeValue != null
					? doSetAttribute(attributeName, attributeValue)
					: removeAttribute(attributeName);
			}
		}

		private Object doSetAttribute(String attributeName, Object attributeValue) {

			Map<String, Object> sessionAttributes = getMap();

			Object previousAttributeValue = sessionAttributes.put(attributeName, attributeValue);

			this.delta |= getIsDirtyPredicate().isDirty(previousAttributeValue, attributeValue)
				&& sessionAttributesChangeInterceptor().apply(attributeName, attributeValue);

			return previousAttributeValue;
		}

		public Object removeAttribute(String attributeName) {

			synchronized (getLock()) {

				Map<String, Object> sessionAttributes = getMap();

				this.delta |= sessionAttributes.containsKey(attributeName)
					&& sessionAttributesChangeInterceptor().apply(attributeName, null);

				return sessionAttributes.remove(attributeName);
			}
		}

		@SuppressWarnings("unchecked")
		public <T> T getAttribute(String attributeName) {

			synchronized (getLock()) {
				return (T) getMap().get(attributeName);
			}
		}

		public Set<String> getAttributeNames() {

			synchronized (getLock()) {
				return Collections.unmodifiableSet(getMap().keySet());
			}
		}

		@Override
		@SuppressWarnings("all")
		public Set<Entry<String, Object>> entrySet() {

			synchronized (getLock()) {

				return new AbstractSet<Entry<String, Object>>() {

					@Override
					public Iterator<Entry<String, Object>> iterator() {
						return Collections.unmodifiableMap(GemFireSessionAttributes.this.getMap())
							.entrySet().iterator();
					}

					@Override
					public int size() {
						return GemFireSessionAttributes.this.getMap().size();
					}
				};
			}
		}

		protected BiFunction<String, Object, Boolean> sessionAttributesChangeInterceptor() {
			return (attributeName, attributeValue) -> true;
		}

		protected void commit() {

			synchronized (getLock()) {
				this.delta = false;
			}
		}

		@SuppressWarnings("unchecked")
		public <T extends GemFireSessionAttributes> T configureWith(IsDirtyPredicate dirtyPredicate) {
			setIsDirtyPredicate(dirtyPredicate);
			return (T) this;
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
				return getMap().toString();
			}
		}
	}

	protected static class SessionEventHandlerCacheListenerAdapter extends CacheListenerAdapter<Object, Session> {

		private final AbstractGemFireOperationsSessionRepository sessionRepository;

		private final Set<Integer> cachedSessionIds = new ConcurrentSkipListSet<>();

		/**
		 * Constructs a new instance of the {@link SessionEventHandlerCacheListenerAdapter} initialized with
		 * the given {@link AbstractGemFireOperationsSessionRepository}.
		 *
		 * @param sessionRepository {@link AbstractGemFireOperationsSessionRepository} used by this event handler
		 * to manage {@link AbstractSessionEvent Session Events}.
		 * @throws IllegalArgumentException if {@link AbstractGemFireOperationsSessionRepository} is {@literal null}.
		 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
		 */
		protected SessionEventHandlerCacheListenerAdapter(
				AbstractGemFireOperationsSessionRepository sessionRepository) {

			Assert.notNull(sessionRepository, "SessionRepository is required");

			this.sessionRepository = sessionRepository;
		}

		/**
		 * Returns a reference to the configured {@link SessionRepository}.
		 *
		 * @return a reference to the configured {@link SessionRepository}.
		 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
		 */
		protected @NonNull AbstractGemFireOperationsSessionRepository getSessionRepository() {
			return this.sessionRepository;
		}

		/**
		 * Callback method triggered when an entry is created (put) in the {@link Session} cache {@link Region}.
		 *
		 * @param event {@link EntryEvent} containing the details of the cache operation.
		 * @see org.springframework.session.events.SessionCreatedEvent
		 * @see org.springframework.session.Session
		 * @see org.apache.geode.cache.EntryEvent
		 * @see #newSessionCreatedEvent(Session)
		 * @see #publishEvent(ApplicationEvent)
		 * @see #toSession(Object, Object)
		 * @see #forget(Object)
		 */
		@Override
		public void afterCreate(EntryEvent<Object, Session> event) {

			Optional.ofNullable(event)
				.filter(this::remember)
				.ifPresent(it -> getSessionRepository()
					.publishEvent(newSessionCreatedEvent(toSession(it.getNewValue(), it.getKey()))));
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
		 * @see #toSession(Object, Object)
		 * @see #forget(Object)
		 */
		protected void afterDelete(String sessionId, Session session) {

			forget(sessionId);
			getSessionRepository().publishEvent(newSessionDeletedEvent(toSession(session, sessionId)));
		}

		/**
		 * Callback method triggered when an entry is destroyed (removed) in the {@link Session} cache {@link Region}.
		 *
		 * @param event {@link EntryEvent} containing the details of the cache operation.
		 * @see org.springframework.session.events.SessionDestroyedEvent
		 * @see org.springframework.session.Session
		 * @see org.apache.geode.cache.EntryEvent
		 * @see #newSessionDestroyedEvent(Session)
		 * @see #publishEvent(ApplicationEvent)
		 * @see #toSession(Object, Object)
		 * @see #forget(Object)
		 */
		@Override
		public void afterDestroy(EntryEvent<Object, Session> event) {

			Optional.ofNullable(event)
				.filter(this::forget)
				.ifPresent(it -> getSessionRepository()
					.publishEvent(newSessionDestroyedEvent(toSession(event.getOldValue(), it.getKey()))));
		}

		/**
		 * Callback method triggered when an entry is invalidated (expired) in the {@link Session} cache {@link Region}.
		 *
		 * @param event {@link EntryEvent} containing the details of the cache operation.
		 * @see org.springframework.session.events.SessionExpiredEvent
		 * @see org.springframework.session.Session
		 * @see org.apache.geode.cache.EntryEvent
		 * @see #newSessionExpiredEvent(Session)
		 * @see #publishEvent(ApplicationEvent)
		 * @see #toSession(Object, Object)
		 * @see #forget(Object)
		 */
		@Override
		public void afterInvalidate(EntryEvent<Object, Session> event) {

			Optional.ofNullable(event)
				.filter(this::forget)
				.ifPresent(it -> getSessionRepository()
					.publishEvent(newSessionExpiredEvent(toSession(event.getOldValue(), it.getKey()))));
		}

		/**
		 * Constructs a new {@link SessionCreatedEvent} initialized with the given {@link Session},
		 * using the {@link #getSessionRepository() SessionRepository} as the event source.
		 *
		 * @param session {@link Session} that is the subject of the {@link AbstractSessionEvent event}.
		 * @return a new {@link SessionCreatedEvent}.
		 * @see org.springframework.session.events.SessionCreatedEvent
		 * @see org.springframework.session.Session
		 * @see #getSessionRepository()
		 */
		protected SessionCreatedEvent newSessionCreatedEvent(Session session) {
			return new SessionCreatedEvent(getSessionRepository(), session);
		}

		/**
		 * Constructs a new {@link SessionDeletedEvent} initialized with the given {@link Session},
		 * using the {@link #getSessionRepository() SessionRepository} as the event source.
		 *
		 * @param session {@link Session} that is the subject of the {@link AbstractSessionEvent event}.
		 * @return a new {@link SessionDeletedEvent}.
		 * @see org.springframework.session.events.SessionDeletedEvent
		 * @see org.springframework.session.Session
		 * @see #getSessionRepository()
		 */
		protected SessionDeletedEvent newSessionDeletedEvent(Session session) {
			return new SessionDeletedEvent(getSessionRepository(), session);
		}

		/**
		 * Constructs a new {@link SessionDestroyedEvent} initialized with the given {@link Session},
		 * using the {@link #getSessionRepository() SessionRepository} as the event source.
		 *
		 * @param session {@link Session} that is the subject of the {@link AbstractSessionEvent event}.
		 * @return a new {@link SessionDestroyedEvent}.
		 * @see org.springframework.session.events.SessionDestroyedEvent
		 * @see org.springframework.session.Session
		 * @see #getSessionRepository()
		 */
		protected SessionDestroyedEvent newSessionDestroyedEvent(Session session) {
			return new SessionDestroyedEvent(getSessionRepository(), session);
		}

		/**
		 * Constructs a new {@link SessionExpiredEvent} initialized with the given {@link Session},
		 * using the {@link #getSessionRepository() SessionRepository} as the event source.
		 *
		 * @param session {@link Session} that is the subject of the {@link AbstractSessionEvent event}.
		 * @return a new {@link SessionExpiredEvent}.
		 * @see org.springframework.session.events.SessionExpiredEvent
		 * @see org.springframework.session.Session
		 * @see #getSessionRepository()
		 */
		protected SessionExpiredEvent newSessionExpiredEvent(Session session) {
			return new SessionExpiredEvent(getSessionRepository(), session);
		}

		Set<Integer> getCachedSessionIds() {
			return this.cachedSessionIds;
		}

		/**
		 * Determines whether the given {@link Session#getId() Session ID} has been remembered.
		 *
		 * @param sessionId {@link Object Session ID} to evaluate.
		 * @return return a boolean value determining whether the given {@link Session#getId() Session ID}
		 * has been remembered.
		 * @see #getCachedSessionIds()
		 */
		protected boolean isRemembered(Object sessionId) {
			return getCachedSessionIds().contains(ObjectUtils.nullSafeHashCode(sessionId));
		}

		/**
		 * Forgets the {@link EntryEvent#getKey() Key} contained in the given {@link EntryEvent}
		 * as a {@link Session#getId() Session ID}.
		 *
		 * @param entryEvent {@link EntryEvent} to evaluate.
		 * @return {@literal true} if the {@link EntryEvent#getKey() Key} contained in the given {@link EntryEvent}
		 * was forgotten as a {@link Session#getId() Session ID}.
		 * @see org.springframework.session.Session
		 * @see org.apache.geode.cache.EntryEvent
		 * @see #forget(Object)
		 */
		protected boolean forget(EntryEvent<Object, Session> entryEvent) {

			return Optional.ofNullable(entryEvent)
				.map(EntryEvent::getKey)
				.map(this::forget)
				.orElse(false);
		}

		/**
		 * Forgets the given {@link Object Session ID}.
		 *
		 * @param sessionId {@link Object} containing the {@link Session#getId() Session ID} to forget.
		 * @return a boolean value indicating whether the given {@link Session#getId() Session ID} was forgotten.
		 * @see #getCachedSessionIds()
		 * @see #remember(Object)
		 */
		protected boolean forget(Object sessionId) {
			return getCachedSessionIds().remove(ObjectUtils.nullSafeHashCode(sessionId));
		}

		/**
		 * Remembers the {@link EntryEvent#getKey() Key} contained by the given {@link EntryEvent}
		 * iff the {@link EntryEvent#getKey() Key} is a valid {@link Session#getId() Session ID}
		 * and the {@link EntryEvent#getNewValue() new value} is a {@link Session}.
		 *
		 * @param entryEvent {@link EntryEvent} to evaluate.
		 * @return {@literal true} if the {@link EntryEvent#getKey() Key} of the given {@link EntryEvent}
		 * is a valid {@link Session#getId() Session ID}.
		 * @see SessionUtils#isValidSessionId(Object)
		 * @see #isSession(EntryEvent)
		 * @see #remember(Object)
		 * @see org.springframework.session.Session
		 * @see org.apache.geode.cache.EntryEvent
		 */
		protected boolean remember(EntryEvent<Object, Session> entryEvent) {

			return Optional.ofNullable(entryEvent)
				.filter(this::isSession)
				.map(EntryEvent::getKey)
				.filter(SessionUtils::isValidSessionId)
				.map(this::remember)
				.orElse(false);
		}

		/**
		 * Remembers the given {@link Object Session ID}.
		 *
		 * @param sessionId {@link Object} containing the {@link Session#getId() Session ID} to remember.
		 * @return a boolean value indicating whether Spring Session is interested in and will remember
		 * the given {@link Session#getId() Session ID}.
		 * @see #getCachedSessionIds()
		 * @see #forget(Object)
		 */
		protected boolean remember(Object sessionId) {
			return getCachedSessionIds().add(ObjectUtils.nullSafeHashCode(sessionId));
		}

		/**
		 * Determines whether the {@link EntryEvent#getNewValue() new value} contained in the {@link EntryEvent}
		 * is a {@link Session}.
		 *
		 * @param entryEvent {@link EntryEvent} to evaluate.
		 * @return a boolean value indicating whether the {@link EntryEvent#getNewValue() new value}
		 * contained in the {@link EntryEvent} is a {@link Session}.
		 * @see org.springframework.session.Session
		 * @see org.apache.geode.cache.EntryEvent
		 */
		protected boolean isSession(EntryEvent<?, ?> entryEvent) {

			return Optional.ofNullable(entryEvent)
				.map(EntryEvent::getNewValue)
				.filter(Session.class::isInstance)
				.isPresent();
		}

		/**
		 * Determines whether the given {@link Object} is a {@link Session}.
		 *
		 * @param target {@link Object} to evaluate.
		 * @return a boolean value determining whether the given {@link Object} is a {@link Session}.
		 * @see org.springframework.session.Session
		 */
		protected boolean isSession(Object target) {
			return target instanceof Session;
		}

		/**
		 * Casts the given {@link Object} into a {@link Session} iff the {@link Object} is a {@link Session}.
		 *
		 * Otherwise, this method attempts to use the supplied {@link String Session ID} to create a {@link Session}
		 * representation containing only the ID.
		 *
		 * @param target {@link Object} to evaluate as a {@link Session}.
		 * @param sessionId {@link String} containing the {@link Session#getId() Session ID}.
		 * @return a {@link Session} from the given {@link Object} or a {@link Session} representation
		 * containing only the supplied {@link String Session ID}.
		 * @throws IllegalStateException if the given {@link Object} is not a {@link Session}
		 * and a {@link String Session ID} was not supplied.
		 * @see org.springframework.session.Session
		 * @see SessionUtils#isValidSessionId(Object)
		 * @see #isSession(Object)
		 */
		protected Session toSession(@Nullable Object target, Object sessionId) {

			return isSession(target) ? (Session) target
				: Optional.ofNullable(sessionId)
					.filter(SessionUtils::isValidSessionId)
					.map(Object::toString)
					.map(SessionIdHolder::create)
					.orElseThrow(() -> newIllegalStateException(
						"Session or the Session ID [%s] must be known to trigger a Session event", sessionId));
		}
	}

	protected static class SessionIdInterestRegisteringCacheListener extends CacheListenerAdapter<Object, Session> {

		private final AbstractGemFireOperationsSessionRepository sessionRepository;

		/**
		 * Constructs a new instance of the {@link SessionIdInterestRegisteringCacheListener} initialized with
		 * the {@link AbstractGemFireOperationsSessionRepository}.
		 *
		 * @param sessionRepository {@link AbstractGemFireOperationsSessionRepository} used by this listener
		 * to register and unregister interests in {@link Session Sessions}.
		 * @throws IllegalArgumentException if {@link AbstractGemFireOperationsSessionRepository} is {@literal null}.
		 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
		 */
		public SessionIdInterestRegisteringCacheListener(AbstractGemFireOperationsSessionRepository sessionRepository) {

			Assert.notNull(sessionRepository, "SessionRepository is required");

			this.sessionRepository = sessionRepository;
		}

		/**
		 * Returns a reference to the configured {@link SessionRepository}.
		 *
		 * @return a reference to the configured {@link SessionRepository}.
		 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
		 */
		protected AbstractGemFireOperationsSessionRepository getSessionRepository() {
			return this.sessionRepository;
		}

		@Override
		public void afterCreate(EntryEvent<Object, Session> event) {
			getSessionRepository().registerInterest(event.getKey());
		}

		@Override
		public void afterDestroy(EntryEvent<Object, Session> event) {
			getSessionRepository().unregisterInterest(event.getKey());
		}

		@Override
		public void afterInvalidate(EntryEvent<Object, Session> event) {
			getSessionRepository().unregisterInterest(event.getKey());
		}
	}
}
