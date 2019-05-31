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
package org.springframework.session.data.gemfire;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.data.gemfire.util.CollectionUtils.asSet;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.SessionEventHandlerCacheListenerAdapter;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.SessionIdInterestRegisteringCacheListener;

import java.io.DataInput;
import java.io.DataOutput;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import edu.umd.cs.mtc.MultithreadedTestCase;
import edu.umd.cs.mtc.TestFramework;

import org.apache.geode.Delta;
import org.apache.geode.cache.AttributesMutator;
import org.apache.geode.cache.EntryEvent;
import org.apache.geode.cache.InterestResultPolicy;
import org.apache.geode.cache.Operation;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;
import org.apache.geode.cache.client.ClientCache;
import org.apache.geode.cache.client.Pool;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.data.gemfire.GemfireTemplate;
import org.springframework.data.gemfire.util.RegionUtils;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.support.DeltaAwareDirtyPredicate;
import org.springframework.session.data.gemfire.support.EqualsDirtyPredicate;
import org.springframework.session.data.gemfire.support.GemFireOperationsSessionRepositorySupport;
import org.springframework.session.data.gemfire.support.IdentityEqualsDirtyPredicate;
import org.springframework.session.data.gemfire.support.IsDirtyPredicate;
import org.springframework.session.data.gemfire.support.SessionIdHolder;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionCreatedEvent;
import org.springframework.session.events.SessionDeletedEvent;
import org.springframework.session.events.SessionDestroyedEvent;
import org.springframework.session.events.SessionExpiredEvent;
import org.springframework.util.ObjectUtils;

import org.slf4j.Logger;

/**
 * Unit tests for {@link AbstractGemFireOperationsSessionRepository}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.mockito.Spy
 * @see org.apache.geode.cache.Region
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.data.gemfire.GemfireTemplate
 * @see org.springframework.session.FindByIndexNameSessionRepository
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.events.AbstractSessionEvent
 * @see edu.umd.cs.mtc.MultithreadedTestCase
 * @see edu.umd.cs.mtc.TestFramework
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractGemFireOperationsSessionRepositoryTests {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 300;

	// Subject Under Test (SUT)
	private AbstractGemFireOperationsSessionRepository sessionRepository;

	@Mock
	private Logger mockLog;

	@Mock
	private Pool mockPool;

	@Mock
	private Region<Object, Session> mockRegion;

	@Mock
	private Session mockSession;

	@Before
	@SuppressWarnings("all")
	public void setup() {

		this.sessionRepository = new TestGemFireOperationsSessionRepository(new GemfireTemplate(this.mockRegion));
		this.sessionRepository.setUseDataSerialization(false);
		this.sessionRepository = spy(this.sessionRepository);

		doReturn(this.mockLog).when(this.sessionRepository).getLogger();
		doReturn(this.mockRegion).when(this.sessionRepository).getSessionsRegion();
	}

	@SuppressWarnings("unchecked")
	private <K, V> EntryEvent<K, V> mockEntryEvent(Operation operation, K key, V oldValue, V newValue) {

		EntryEvent<K, V> mockEntryEvent = mock(EntryEvent.class, withSettings().lenient());

		when(mockEntryEvent.getOperation()).thenReturn(operation);
		when(mockEntryEvent.getKey()).thenReturn(key);
		when(mockEntryEvent.getOldValue()).thenReturn(oldValue);
		when(mockEntryEvent.getNewValue()).thenReturn(newValue);

		return mockEntryEvent;
	}

	private Session mockSession() {

		String sessionId = UUID.randomUUID().toString();

		Instant now = Instant.now();

		Duration maxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mock(Session.class, withSettings().name(sessionId).lenient());

		when(mockSession.getId()).thenReturn(sessionId);
		when(mockSession.getAttributeNames()).thenReturn(Collections.emptySet());
		when(mockSession.getCreationTime()).thenReturn(now);
		when(mockSession.getLastAccessedTime()).thenReturn(now);
		when(mockSession.getMaxInactiveInterval()).thenReturn(maxInactiveInterval);

		return mockSession;
	}

	@SuppressWarnings("unused")
	private Session mockSession(String sessionId) {
		return mockSession(sessionId, Instant.now().toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);
	}

	private Session mockSession(String sessionId, long creationAndLastAccessedTime, long maxInactiveInterval) {
		return mockSession(sessionId, creationAndLastAccessedTime, creationAndLastAccessedTime, maxInactiveInterval);
	}

	private Session mockSession(String sessionId, long creationTime, long lastAccessedTime, long maxInactiveInterval) {

		Session mockSession = mock(Session.class, withSettings().lenient().name(sessionId));

		when(mockSession.getId()).thenReturn(sessionId);
		when(mockSession.getCreationTime()).thenReturn(Instant.ofEpochMilli(creationTime));
		when(mockSession.getLastAccessedTime()).thenReturn(Instant.ofEpochMilli(lastAccessedTime));
		when(mockSession.getMaxInactiveInterval()).thenReturn(Duration.ofSeconds(maxInactiveInterval));

		return mockSession;
	}

	private GemFireSession newNonDirtyGemFireSession() {

		GemFireSession session = GemFireSession.create();

		session.commit();

		return session;
	}

	@Test
	@SuppressWarnings("unchecked")
	public void constructGemFireOperationsSessionRepository() {

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		AttributesMutator<Object, Session> mockAttributesMutator = mock(AttributesMutator.class);

		ClientCache mockClientCache = mock(ClientCache.class);

		Region<Object, Session> mockRegion = mock(Region.class);

		RegionAttributes<Object, Session> mockRegionAttributes = mock(RegionAttributes.class);

		when(this.mockPool.getSubscriptionEnabled()).thenReturn(true);
		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);
		when(mockRegion.getFullPath()).thenReturn(RegionUtils.toRegionPath("Example"));
		when(mockRegion.getRegionService()).thenReturn(mockClientCache);
		when(mockRegionAttributes.getPoolName()).thenReturn("Dead");

		GemfireTemplate template = new GemfireTemplate(mockRegion);

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(template);

		assertThat(sessionRepository.getApplicationEventPublisher()).isInstanceOf(ApplicationEventPublisher.class);
		assertThat(sessionRepository.getApplicationEventPublisher()).isNotEqualTo(mockApplicationEventPublisher);
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);
		assertThat(sessionRepository.isRegisterInterestEnabled()).isTrue();
		assertThat(sessionRepository.getSessionEventHandler().orElse(null))
			.isInstanceOf(SessionEventHandlerCacheListenerAdapter.class);
		assertThat(sessionRepository.getSessionsRegion()).isSameAs(mockRegion);
		assertThat(sessionRepository.getSessionsRegionName()).isEqualTo(RegionUtils.toRegionPath("Example"));
		assertThat(sessionRepository.getSessionsTemplate()).isSameAs(template);
		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);
		sessionRepository.setMaxInactiveIntervalInSeconds(300);

		assertThat(sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);
		assertThat(sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(300);

		verify(this.mockPool, times(1)).getSubscriptionEnabled();
		verify(mockRegion, times(2)).getAttributes();
		verify(mockRegion, times(1)).getAttributesMutator();
		verify(mockRegion, times(1)).getFullPath();
		verify(mockRegion, times(1)).getRegionService();
		verify(mockRegionAttributes, times(2)).getPoolName();
		verify(mockAttributesMutator, times(1))
			.addCacheListener(isA(SessionEventHandlerCacheListenerAdapter.class));
		verify(mockAttributesMutator, times(1))
			.addCacheListener(isA(SessionIdInterestRegisteringCacheListener.class));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireOperationsSessionRepositoryWithNullTemplate() {

		try {
			new TestGemFireOperationsSessionRepository(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("GemfireOperations is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalStateException.class)
	public void constructGemFireOperationSessionRepositoryWithUnresolvableRegion() {

		GemfireOperations mockGemfireOperations = mock(GemfireOperations.class);

		try {
			new TestGemFireOperationsSessionRepository(mockGemfireOperations);
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("The ClusteredSpringSessions Region could not be resolved");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void setAndGetApplicationEventPublisher() {

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isNotNull();

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		this.sessionRepository.setApplicationEventPublisher(mockApplicationEventPublisher);

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(mockApplicationEventPublisher);
	}

	@SuppressWarnings("all")
	@Test(expected = IllegalArgumentException.class)
	public void setApplicationEventPublisherToNull() {

		try {
			this.sessionRepository.setApplicationEventPublisher(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("ApplicationEventPublisher is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void getFullyQualifiedRegionNameUsesRegionFullPath() {

		when(this.mockRegion.getFullPath()).thenReturn("/Sessions/Region/Full/Path");

		assertThat(this.sessionRepository.getSessionsRegionName()).isEqualTo("/Sessions/Region/Full/Path");

		verify(this.sessionRepository, times(1)).getSessionsRegion();
		verify(this.mockRegion, times(1)).getFullPath();
	}

	@Test
	public void setAndGetIsDirtyPredicate() {

		assertThat(this.sessionRepository.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		IsDirtyPredicate mockDirtyPredicate = mock(IsDirtyPredicate.class);

		this.sessionRepository.setIsDirtyPredicate(mockDirtyPredicate);

		assertThat(this.sessionRepository.getIsDirtyPredicate()).isEqualTo(mockDirtyPredicate);

		this.sessionRepository.setIsDirtyPredicate(null);

		assertThat(this.sessionRepository.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		this.sessionRepository.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		assertThat(this.sessionRepository.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
	}

	@Test
	public void setAndGetMaxInactiveInterval() {

		assertThat(this.sessionRepository.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS));

		Duration tenMinutes = Duration.ofMinutes(10);

		this.sessionRepository.setMaxInactiveInterval(tenMinutes);

		assertThat(this.sessionRepository.getMaxInactiveInterval()).isEqualTo(tenMinutes);
		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(600);
	}

	@Test
	public void setMaxInactiveIntervalToNull() {

		assertThat(this.sessionRepository.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS));

		this.sessionRepository.setMaxInactiveInterval(null);

		assertThat(this.sessionRepository.getMaxInactiveInterval()).isNull();
	}

	@Test
	public void setMaxInactiveIntervalUsingSeconds() {

		assertThat(this.sessionRepository.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS));

		this.sessionRepository.setMaxInactiveIntervalInSeconds(300);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(300);
		assertThat(this.sessionRepository.getMaxInactiveInterval()).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	public void setMaxInactiveIntervalInSecondsAllowsExtremelyLargeAndNegativeValues() {

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_MAX_INACTIVE_INTERVAL_IN_SECONDS);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(-1);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(-1);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MIN_VALUE);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MIN_VALUE);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(1024000);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(1024000);

		this.sessionRepository.setMaxInactiveIntervalInSeconds(Integer.MAX_VALUE);

		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void isRegisterInterestEnabledReturnsTrue() {

		AttributesMutator mockAttributesMutator = mock(AttributesMutator.class);

		ClientCache mockClientCache = mock(ClientCache.class);

		Region mockRegion = mock(Region.class);

		RegionAttributes mockRegionAttributes = mock(RegionAttributes.class);

		when(this.mockPool.getSubscriptionEnabled()).thenReturn(true);
		when(mockRegion.getAttributes()).thenReturn(mockRegionAttributes);
		when(mockRegion.getAttributesMutator()).thenReturn(mockAttributesMutator);
		when(mockRegion.getRegionService()).thenReturn(mockClientCache);
		when(mockRegionAttributes.getPoolName()).thenReturn("Dead");

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(new GemfireTemplate(mockRegion));

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.isRegisterInterestEnabled()).isTrue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void isRegisterInterestEnabledReturnsFalse() {

		Region mockRegion = mock(Region.class);

		AbstractGemFireOperationsSessionRepository sessionRepository =
			new TestGemFireOperationsSessionRepository(new GemfireTemplate(mockRegion));

		assertThat(sessionRepository).isNotNull();
		assertThat(sessionRepository.isRegisterInterestEnabled()).isFalse();
	}

	@Test
	public void setAndIsUsingDataSerialization() {

		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		this.sessionRepository.setUseDataSerialization(true);

		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isTrue();

		this.sessionRepository.setUseDataSerialization(false);

		assertThat(GemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();
	}

	@Test
	public void commitGemFireSessionIsCorrect() {

		GemFireSession<?> session = spy(GemFireSession.create());

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();

		this.sessionRepository.commit(session);

		assertThat(session.hasDelta()).isFalse();

		verify(session, times(1)).commit();
	}

	@Test
	public void commitNonGemFireSessionIsSafe() {
		this.sessionRepository.commit(this.mockSession);
	}

	@Test
	public void commitNullIsSafe() {
		this.sessionRepository.commit(null);
	}

	@Test
	public void configureWithGemFireSession() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);

		this.sessionRepository.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);
		this.sessionRepository.setMaxInactiveIntervalInSeconds(300);
		this.sessionRepository.configure(session);

		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(300));
		assertThat(session.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);

		verify(this.sessionRepository, times(1)).getIsDirtyPredicate();
		verify(this.sessionRepository, times(1)).getMaxInactiveInterval();
	}

	@Test
	public void configureWithNull() {

		this.sessionRepository.configure(null);

		verify(this.sessionRepository, never()).getIsDirtyPredicate();
		verify(this.sessionRepository, never()).getMaxInactiveInterval();
	}

	@Test
	public void configureWithSession() {

		this.sessionRepository.configure(this.mockSession);

		verify(this.sessionRepository, never()).getIsDirtyPredicate();
		verify(this.sessionRepository, never()).getMaxInactiveInterval();
	}

	@Test
	public void deleteSessionCallsDeleteSessionById() {

		doNothing().when(this.sessionRepository).deleteById(anyString());

		when(this.mockSession.getId()).thenReturn("2");

		assertThat(this.sessionRepository.delete(this.mockSession)).isNull();

		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).deleteById(eq("2"));
	}

	@Test
	public void handleDeletedSessionForgetsSessionIdPublishesSessionDeletedEventAndUnregistersInterest() {

		when(this.mockSession.getId()).thenReturn("12345");

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isEqualTo(this.mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo("12345");
			assertThat(sessionEvent.getSource()).isEqualTo(this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		SessionEventHandlerCacheListenerAdapter mockSessionEventHandler =
			mock(SessionEventHandlerCacheListenerAdapter.class);

		doReturn(mockApplicationEventPublisher).when(this.sessionRepository).getApplicationEventPublisher();
		doReturn(Optional.of(mockSessionEventHandler)).when(this.sessionRepository).getSessionEventHandler();
		doReturn(this.sessionRepository).when(mockSessionEventHandler).getSessionRepository();
		doCallRealMethod().when(mockSessionEventHandler).afterDelete(anyString(), any(Session.class));
		doCallRealMethod().when(mockSessionEventHandler).newSessionDeletedEvent(any(Session.class));
		doCallRealMethod().when(mockSessionEventHandler).toSession(any(), anyString());

		this.sessionRepository.handleDeleted("12345", this.mockSession);

		verify(mockSessionEventHandler, times(1))
			.afterDelete(eq("12345"), eq(this.mockSession));
		verify(mockSessionEventHandler, times(1)).forget(eq("12345"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionDeletedEvent.class));
		verify(this.sessionRepository, times(1)).unregisterInterest(eq("12345"));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
		verify(this.mockSession, times(1)).getId();
		verify(this.mockLog, never()).error(anyString(), any(Throwable.class));
	}

	@Test
	public void handleDeletedSessionWhenNoSessionEventHandlerIsPresentDoesNotPublishEventButStillUnregistersInterest() {

		doReturn(Optional.empty()).when(this.sessionRepository).getSessionEventHandler();

		this.sessionRepository.handleDeleted("1", this.mockSession);

		verify(this.sessionRepository, times(1)).getSessionEventHandler();
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
		verify(this.sessionRepository, times(1)).unregisterInterest(eq("1"));
		verifyZeroInteractions(this.mockSession);
	}

	@Test
	public void publishEventPublishesApplicationEvent() {

		ApplicationEvent mockApplicationEvent = mock(ApplicationEvent.class);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doReturn(mockApplicationEventPublisher).when(this.sessionRepository).getApplicationEventPublisher();

		this.sessionRepository.publishEvent(mockApplicationEvent);

		verify(mockApplicationEventPublisher, times(1)).publishEvent(eq(mockApplicationEvent));
	}

	@Test
	public void publishEventHandlesThrowable() {

		ApplicationEvent mockApplicationEvent = mock(ApplicationEvent.class);

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		doThrow(new IllegalStateException("test")).when(mockApplicationEventPublisher)
			.publishEvent(any(ApplicationEvent.class));

		doReturn(mockApplicationEventPublisher).when(this.sessionRepository).getApplicationEventPublisher();

		this.sessionRepository.publishEvent(mockApplicationEvent);

		verify(mockApplicationEventPublisher, times(1)).publishEvent(eq(mockApplicationEvent));

		verify(this.mockLog, times(1))
			.error(eq(String.format("Error occurred while publishing event [%s]", mockApplicationEvent)),
				isA(IllegalStateException.class));
	}

	private Session testRegisterInterestWithInvalidSession(Session session) {

		doReturn(true).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);

		Session returnedSession = this.sessionRepository.registerInterest(session);

		verifyNoRegionRegisterInterestCalls(this.mockRegion);

		return returnedSession;
	}

	private void verifyNoRegionRegisterInterestCalls(Region<?, ?> region) {

		verify(region, never()).registerInterest(any());
		verify(region, never()).registerInterest(any(), anyBoolean());
		verify(region, never()).registerInterest(any(), anyBoolean(), anyBoolean());
		verify(region, never()).registerInterest(any(), any(InterestResultPolicy.class));
		verify(region, never()).registerInterest(any(), any(InterestResultPolicy.class), anyBoolean());
		verify(region, never()).registerInterest(any(), any(InterestResultPolicy.class), anyBoolean(), anyBoolean());
	}

	@Test
	public void registerInterestIsNullSafe() {
		assertThat(testRegisterInterestWithInvalidSession(null)).isNull();
	}

	@Test
	public void registerInterestWhenRegisteringInterestIsNotEnabled() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(false).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.registerInterest(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).registerInterest(eq("1"));
		verify(this.sessionRepository, times(1)).isRegisterInterestEnabled();
		verifyNoRegionRegisterInterestCalls(this.mockRegion);
	}

	@Test
	public void registerInterestWithSession() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(true).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.registerInterest(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).registerInterest(eq("1"));
		verify(this.sessionRepository, times(1)).isRegisterInterestEnabled();
		verify(this.mockRegion, times(1))
			.registerInterest(eq("1"), eq(InterestResultPolicy.NONE), eq(false), eq(true));
	}

	@Test
	public void registerInterestWithSessionHavingEmptyId() {

		when(this.mockSession.getId()).thenReturn("");

		assertThat(testRegisterInterestWithInvalidSession(this.mockSession)).isEqualTo(this.mockSession);
	}

	@Test
	public void registerInterestWithSessionHavingNullId() {

		when(this.mockSession.getId()).thenReturn(null);

		assertThat(testRegisterInterestWithInvalidSession(this.mockSession)).isEqualTo(this.mockSession);
	}

	@Test
	public void registerInterestWithSessionHavingUnspecifiedId() {

		when(this.mockSession.getId()).thenReturn("  ");

		assertThat(testRegisterInterestWithInvalidSession(this.mockSession)).isEqualTo(this.mockSession);
	}

	@Test
	public void registerInterestWithTheSameSessionTwice() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(true).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.registerInterest(this.mockSession)).isEqualTo(this.mockSession);
		assertThat(this.sessionRepository.registerInterest(this.mockSession)).isEqualTo(this.mockSession);

		verify(this.mockSession, times(2)).getId();
		verify(this.sessionRepository, times(2)).registerInterest(eq("1"));
		verify(this.sessionRepository, times(2)).isRegisterInterestEnabled();
		verify(this.mockRegion, times(1))
			.registerInterest(eq("1"), eq(InterestResultPolicy.NONE), eq(false), eq(true));
	}

	@Test
	public void touchSetsLastAccessedTime() {

		assertThat(this.sessionRepository.touch(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(1)).setLastAccessedTime(any(Instant.class));
	}

	@Test
	public void unregisterInterestIsNullSafe() {
		assertThat(this.sessionRepository.unregisterInterest(null)).isNull();
	}

	@Test
	public void unregisterInterestWhenRegisteringInterestIsDisabled() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(false).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.unregisterInterest(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).unregisterInterest(eq("1"));
		verify(this.sessionRepository, times(1)).isRegisterInterestEnabled();
		verify(this.mockRegion, never()).unregisterInterest(any());
	}

	@Test
	public void unregisterInterestWithRegisteredSession() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(true).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.registerInterest(this.mockSession)).isSameAs(this.mockSession);
		assertThat(this.sessionRepository.unregisterInterest(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(2)).getId();
		verify(this.sessionRepository, times(1)).unregisterInterest(eq("1"));
		verify(this.sessionRepository, times(2)).isRegisterInterestEnabled();
		verify(this.mockRegion, times(1)).unregisterInterest(eq("1"));
	}

	@Test
	public void unregisterInterestWithTheSameSessionTwice() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(true).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.registerInterest(this.mockSession)).isSameAs(this.mockSession);
		assertThat(this.sessionRepository.unregisterInterest(this.mockSession)).isSameAs(this.mockSession);
		assertThat(this.sessionRepository.unregisterInterest(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(3)).getId();
		verify(this.sessionRepository, times(2)).unregisterInterest(eq("1"));
		verify(this.sessionRepository, times(3)).isRegisterInterestEnabled();
		verify(this.mockRegion, times(1)).unregisterInterest(eq("1"));
	}

	@Test
	public void unregisterInterestWithUnknownSession() {

		when(this.mockSession.getId()).thenReturn("1");
		doReturn(true).when(this.sessionRepository).isRegisterInterestEnabled();

		assertThat(this.sessionRepository.getSessionsRegion()).isEqualTo(this.mockRegion);
		assertThat(this.sessionRepository.unregisterInterest(this.mockSession)).isSameAs(this.mockSession);

		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).unregisterInterest(eq("1"));
		verify(this.sessionRepository, times(1)).isRegisterInterestEnabled();
		verify(this.mockRegion, never()).unregisterInterest(any());
	}

	@Test
	public void constructSessionEventHandlerCacheListenerAdapter() {

		AbstractGemFireOperationsSessionRepository mockSessionRepository =
			mock(AbstractGemFireOperationsSessionRepository.class);

		SessionEventHandlerCacheListenerAdapter sessionEventHanlder =
			new SessionEventHandlerCacheListenerAdapter(mockSessionRepository);

		assertThat(sessionEventHanlder).isNotNull();
		assertThat(sessionEventHanlder.getSessionRepository()).isSameAs(mockSessionRepository);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructSessionEventHandlerCacheListenerAdapterWithNull() {

		try {
			new SessionEventHandlerCacheListenerAdapter(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("SessionRepository is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void newSessionEventHandlerCacheListenerAdapterUsingSessionRepository() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.getSessionRepository()).isSameAs(this.sessionRepository);
	}

	@Test
	public void afterCreateIsNullSafe() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		sessionEventHandler.afterCreate(null);

		verify(sessionEventHandler, never()).remember(any());
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionCreatedEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateHandlesNewSessionPublishesSessionCreatedEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getNewValue()).thenReturn(this.mockSession);
		when(this.mockSession.getId()).thenReturn("1");
		doNothing().when(this.sessionRepository).publishEvent(any(ApplicationEvent.class));

		sessionEventHandler.afterCreate(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(2)).getNewValue();
		verify(sessionEventHandler, times(1)).remember(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionCreatedEvent(eq(this.mockSession));
		verify(sessionEventHandler, times(1)).toSession(eq(this.mockSession), eq("1"));
		verify(this.mockSession, times(1)).getId();
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionCreatedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateHandlesKnownSessionWillNotPublishSessionCreatedEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getNewValue()).thenReturn(this.mockSession);

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterCreate(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(sessionEventHandler, times(1)).remember(eq("1"));
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionCreatedEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verifyZeroInteractions(this.mockSession);
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateHandlesNullSessionWillNotPublishSessionCreatedEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(null);

		sessionEventHandler.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(sessionEventHandler, never()).remember(eq("1"));
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionCreatedEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterCreateHandlesTombstoneWillNotPublishSessionCreatedEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(new Tombstone());

		sessionEventHandler.afterCreate(mockEntryEvent);

		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
		verify(sessionEventHandler, never()).remember(eq("1"));
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionCreatedEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void afterDeleteForgetsSessionIdPublishesSessionDeletedEventForSession() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		when(this.mockSession.getId()).thenReturn("1");

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterDelete("1", this.mockSession);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionDeletedEvent(eq(this.mockSession));
		verify(sessionEventHandler, times(1)).toSession(eq(this.mockSession), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void afterDeleteForgetsSessionIdPublishesSessionDeletedEventForSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterDelete("1", null);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionDeletedEvent(isA(SessionIdHolder.class));
		verify(sessionEventHandler, times(1)).toSession(isNull(), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test(expected = IllegalStateException.class)
	public void afterDeleteHandlesNullSessionAndNullSessionIdThrowsIllegalStateException() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		try {
			sessionEventHandler.afterDelete(null, null);
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("Session or the Session ID [null] must be known to trigger a Session event");
			assertThat(expected).hasNoCause();

			throw expected;
		}
		finally {
			//verify(sessionEventHandler, times(1)).forget(isNull());
			verify(sessionEventHandler, times(1)).getSessionRepository();
			verify(sessionEventHandler, never()).newSessionDeletedEvent(any(Session.class));
			verify(sessionEventHandler, times(1)).toSession(isNull(), isNull());
			verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
		}
	}

	@Test
	public void afterDestroyIsNullSafe() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		sessionEventHandler.afterDestroy(null);

		verify(sessionEventHandler, never()).forget(any());
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionDestroyedEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyHandlesKnownSessionPublishesSessionDestroyedEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getOldValue()).thenReturn(this.mockSession);
		when(this.mockSession.getId()).thenReturn("1");

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		when(this.mockSession.getId()).thenReturn("1");

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterDestroy(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockSession, times(1)).getId();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionDestroyedEvent(eq(this.mockSession));
		verify(sessionEventHandler, times(1)).toSession(eq(this.mockSession), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyHandlesNullSessionPublishesSessionDestroyedEventWithSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterDestroy(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionDestroyedEvent(isA(SessionIdHolder.class));
		verify(sessionEventHandler, times(1)).toSession(isNull(), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyHandlesTombstonePublishesSessionDestroyedEventWithSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getOldValue()).thenReturn(new Tombstone());

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterDestroy(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionDestroyedEvent(isA(SessionIdHolder.class));
		verify(sessionEventHandler, times(1)).toSession(isA(Tombstone.class), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionDestroyedEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterDestroyHandlesUnknownSessionWillNotPublishSessionDestroyedEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");

		sessionEventHandler.afterDestroy(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getOldValue();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionDestroyedEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void afterInvalidateIsNullSafe() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		sessionEventHandler.afterInvalidate(null);

		verify(sessionEventHandler, never()).forget(any());
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionExpiredEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateHandlesKnownSessionPublishesSessionExpiredEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getOldValue()).thenReturn(this.mockSession);
		when(this.mockSession.getId()).thenReturn("1");

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		when(this.mockSession.getId()).thenReturn("1");

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterInvalidate(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(this.mockSession, times(1)).getId();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionExpiredEvent(eq(this.mockSession));
		verify(sessionEventHandler, times(1)).toSession(eq(this.mockSession), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateHandlesNullSessionPublishesSessionExpiredEventUsingSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getOldValue()).thenReturn(null);

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterInvalidate(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionExpiredEvent(isA(SessionIdHolder.class));
		verify(sessionEventHandler, times(1)).toSession(isNull(), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateHandlesTombstonePublishesSessionExpiredEventUsingSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");
		when(mockEntryEvent.getOldValue()).thenReturn(new Tombstone());

		assertThat(sessionEventHandler.remember("1")).isTrue();
		assertThat(sessionEventHandler.isRemembered("1")).isTrue();

		sessionEventHandler = spy(sessionEventHandler);
		sessionEventHandler.afterInvalidate(mockEntryEvent);

		assertThat(sessionEventHandler.isRemembered("1")).isFalse();

		verify(mockEntryEvent, times(2)).getKey();
		verify(mockEntryEvent, times(1)).getOldValue();
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, times(2)).getSessionRepository();
		verify(sessionEventHandler, times(1)).newSessionExpiredEvent(isA(SessionIdHolder.class));
		verify(sessionEventHandler, times(1)).toSession(isA(Tombstone.class), eq("1"));
		verify(this.sessionRepository, times(1)).publishEvent(isA(SessionExpiredEvent.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void afterInvalidateHandlesUnknownSessionWillNotPublishSessionExpiredEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");

		sessionEventHandler.afterInvalidate(mockEntryEvent);

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, never()).getOldValue();
		verifyZeroInteractions(this.mockSession);
		verify(sessionEventHandler, times(1)).forget(eq("1"));
		verify(sessionEventHandler, never()).getSessionRepository();
		verify(sessionEventHandler, never()).newSessionExpiredEvent(any(Session.class));
		verify(sessionEventHandler, never()).toSession(any(), any());
		verify(this.sessionRepository, never()).publishEvent(any(ApplicationEvent.class));
	}

	@Test
	public void sessionCreateCreateExpireRecreatePublishesSessionEventsCreateExpireCreate() {

		ApplicationEventPublisher mockApplicationEventPublisher = mock(ApplicationEventPublisher.class);

		AtomicInteger index = new AtomicInteger(0);

		Class[] expectedSessionEventTypes = {
			SessionCreatedEvent.class, SessionExpiredEvent.class, SessionCreatedEvent.class
		};

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(expectedSessionEventTypes[index.getAndIncrement()]);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isEqualTo(this.mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo("123456789");
			assertThat(sessionEvent.getSource()).isEqualTo(this.sessionRepository);

			return null;

		}).when(mockApplicationEventPublisher).publishEvent(isA(ApplicationEvent.class));

		EntryEvent<Object, Session> mockCreateEvent =
			mockEntryEvent(Operation.CREATE, "123456789", null, this.mockSession);

		EntryEvent<Object, Session> mockExpireEvent =
			mockEntryEvent(Operation.INVALIDATE, "123456789", this.mockSession, null);

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		doReturn(mockApplicationEventPublisher).when(this.sessionRepository).getApplicationEventPublisher();
		when(this.mockSession.getId()).thenReturn("123456789");

		sessionEventHandler.afterCreate(mockCreateEvent);
		sessionEventHandler.afterCreate(mockCreateEvent);
		sessionEventHandler.afterInvalidate(mockExpireEvent);
		sessionEventHandler.afterCreate(mockCreateEvent);

		verify(mockCreateEvent, times(5)).getKey();
		verify(mockCreateEvent, times(5)).getNewValue();
		verify(mockCreateEvent, never()).getOldValue();
		verify(mockExpireEvent, times(2)).getKey();
		verify(mockExpireEvent, never()).getNewValue();
		verify(mockExpireEvent, times(1)).getOldValue();
		verify(this.mockSession, times(3)).getId();
		verify(mockApplicationEventPublisher, times(2))
			.publishEvent(isA(SessionCreatedEvent.class));
		verify(mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionExpiredEvent.class));
	}

	private <T extends AbstractSessionEvent> void testNewSessionEventIsCorrect(
			Function<Session, T> sessionEventFactory, Class<T> sessionEventType) {

		when(this.mockSession.getId()).thenReturn("1");

		AbstractSessionEvent event = sessionEventFactory.apply(this.mockSession);

		assertThat(event).isInstanceOf(sessionEventType);
		assertThat(event.getSource()).isEqualTo(this.sessionRepository);
		assertThat(event.<Session>getSession()).isEqualTo(this.mockSession);
		assertThat(event.getSessionId()).isEqualTo("1");

		verify(this.mockSession, times(1)).getId();
	}

	@Test
	public void newSessionCreatedEventIsCorrect() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		assertThat(sessionEventHandler).isNotNull();

		testNewSessionEventIsCorrect(sessionEventHandler::newSessionCreatedEvent, SessionCreatedEvent.class);

		verify(sessionEventHandler, times(1)).getSessionRepository();
	}

	@Test
	public void newSessionDeletedEventIsCorrect() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		assertThat(sessionEventHandler).isNotNull();

		testNewSessionEventIsCorrect(sessionEventHandler::newSessionDeletedEvent, SessionDeletedEvent.class);

		verify(sessionEventHandler, times(1)).getSessionRepository();
	}

	@Test
	public void newSessionDestroyedEventIsCorrect() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		assertThat(sessionEventHandler).isNotNull();

		testNewSessionEventIsCorrect(sessionEventHandler::newSessionDestroyedEvent, SessionDestroyedEvent.class);

		verify(sessionEventHandler, times(1)).getSessionRepository();
	}

	@Test
	public void newSessionExpiredEventIsCorrect() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler =
			spy(this.sessionRepository.newSessionEventHandler());

		assertThat(sessionEventHandler).isNotNull();

		testNewSessionEventIsCorrect(sessionEventHandler::newSessionExpiredEvent, SessionExpiredEvent.class);

		verify(sessionEventHandler, times(1)).getSessionRepository();
	}

	@Test
	public void isRememberedWithKnownSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.getCachedSessionIds().add(ObjectUtils.nullSafeHashCode(1))).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isTrue();
	}

	@Test
	public void isRememberedWithUnknownSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();
		assertThat(sessionEventHandler.isRemembered(1)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void forgetsEntryEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(1);

		assertThat(sessionEventHandler.getCachedSessionIds().add(ObjectUtils.nullSafeHashCode(1))).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isTrue();
		assertThat(sessionEventHandler.forget(mockEntryEvent)).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();

		verify(mockEntryEvent, times(1)).getKey();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void forgetEntryEventWithNullKey() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(null);

		assertThat(sessionEventHandler.forget(mockEntryEvent)).isFalse();

		verify(mockEntryEvent, times(1)).getKey();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void forgetNullEntryEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.forget(null)).isFalse();
	}

	@Test
	public void forgetKnownSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		assertThat(sessionEventHandler.getCachedSessionIds().add(ObjectUtils.nullSafeHashCode(1))).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isTrue();
		assertThat(sessionEventHandler.forget(1)).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();
	}

	@Test
	public void forgetNullSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.forget((Object) null)).isFalse();
	}

	@Test
	public void forgetUnknownSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.forget(1)).isFalse();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void remembersEntryEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(1);
		when(mockEntryEvent.getNewValue()).thenReturn(this.mockSession);

		assertThat(sessionEventHandler.isRemembered(1)).isFalse();
		assertThat(sessionEventHandler.remember(mockEntryEvent)).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isTrue();

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void rememberEntryEventWithInvalidSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn(null);
		when(mockEntryEvent.getNewValue()).thenReturn(this.mockSession);

		assertThat(sessionEventHandler.remember(mockEntryEvent)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();

		verify(mockEntryEvent, times(1)).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void rememberEntryEventWithInvalidValue() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(new Tombstone());

		assertThat(sessionEventHandler.remember(mockEntryEvent)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();

		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void rememberEntryEventWithNullValue() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(null);

		assertThat(sessionEventHandler.remember(mockEntryEvent)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();

		verify(mockEntryEvent, never()).getKey();
		verify(mockEntryEvent, times(1)).getNewValue();
	}

	@Test
	public void rememberNullEntryEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		assertThat(sessionEventHandler.remember(null)).isFalse();
		assertThat(sessionEventHandler.getCachedSessionIds()).isEmpty();
	}

	@Test
	public void rememberNullSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isRemembered(null)).isFalse();
		assertThat(sessionEventHandler.remember((Object) null)).isTrue();
		assertThat(sessionEventHandler.isRemembered(null)).isTrue();
		assertThat(sessionEventHandler.getCachedSessionIds()).containsExactly(0);
	}

	@Test
	public void remembersSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isRemembered(1)).isFalse();
		assertThat(sessionEventHandler.remember(1)).isTrue();
		assertThat(sessionEventHandler.isRemembered(1)).isTrue();
	}

	@Test
	public void isSessionWithEntryEventContainingNull() {

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(null);

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession(mockEntryEvent)).isFalse();
	}

	@Test
	public void isSessionWithEntryEventContainingSession() {

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(this.mockSession);

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession(mockEntryEvent)).isTrue();
	}

	@Test
	public void isSessionWithEntryEventContainingTombstone() {

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getNewValue()).thenReturn(new Tombstone());

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession(mockEntryEvent)).isFalse();
	}

	@Test
	public void isSessionWithNullEntryEvent() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession(null)).isFalse();
	}

	@Test
	public void isSessionWithNull() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession((Object) null)).isFalse();
	}

	@Test
	public void isSessionWithSession() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession(this.mockSession)).isTrue();
	}

	@Test
	public void isSessionWithTombstone() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.isSession(new Tombstone())).isFalse();
	}

	@Test
	public void toSessionWithSessionAndSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();
		assertThat(sessionEventHandler.toSession(this.mockSession, "12345")).isSameAs(this.mockSession);
	}

	@Test
	public void toSessionWithTombstoneAndSessionId() {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		Tombstone tombstone = new Tombstone();

		Session session = sessionEventHandler.toSession(tombstone, "12345");

		assertThat(session).isNotNull();
		assertThat(session).isNotSameAs(tombstone);
		assertThat(session.getId()).isEqualTo("12345");
	}

	private void testToSessionWithNullSessionAndInvalidSessionId(String sessionId) {

		SessionEventHandlerCacheListenerAdapter sessionEventHandler = this.sessionRepository.newSessionEventHandler();

		assertThat(sessionEventHandler).isNotNull();

		try {
			sessionEventHandler.toSession(null, sessionId);
		}
		catch (IllegalStateException expected) {

			assertThat(expected)
				.hasMessage("Session or the Session ID [%s] must be known to trigger a Session event",
					sessionId);

			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalStateException.class)
	public void toSessionWithNullSessionAndEmptySessionId() {
		testToSessionWithNullSessionAndInvalidSessionId("");
	}

	@Test(expected = IllegalStateException.class)
	public void toSessionWithNullSessionAndNullSessionId() {
		testToSessionWithNullSessionAndInvalidSessionId(null);
	}

	@Test(expected = IllegalStateException.class)
	public void toSessionWithNullSessionAndUnspecifiedSessionId() {
		testToSessionWithNullSessionAndInvalidSessionId(null);
	}

	@Test
	public void constructSessionIdInterestRegisteringCacheListener() {

		SessionIdInterestRegisteringCacheListener listener =
			new SessionIdInterestRegisteringCacheListener(this.sessionRepository);

		assertThat(listener).isNotNull();
		assertThat(listener.getSessionRepository()).isSameAs(this.sessionRepository);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructSessionIdInterestRegisteringCacheListenerWithNull() {

		try {
			new SessionIdInterestRegisteringCacheListener(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("SessionRepository is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void constructSessionIdInterestRegisteringCacheListenerUsingSessionRepository() {

		SessionIdInterestRegisteringCacheListener listener = this.sessionRepository.newSessionIdInterestRegistrar();

		assertThat(listener).isNotNull();
		assertThat(listener.getSessionRepository()).isSameAs(this.sessionRepository);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void sessionIdInterestRegisteringCacheListerAfterCreateCallsRegisterInterest() {

		SessionIdInterestRegisteringCacheListener listener =
			spy(this.sessionRepository.newSessionIdInterestRegistrar());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");

		listener.afterCreate(mockEntryEvent);

		verify(listener, times(1)).getSessionRepository();
		verify(mockEntryEvent, times(1)).getKey();
		verify(this.sessionRepository).registerInterest(eq("1"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void sessionIdInterestRegisteringCacheListerAfterDestroyCallsUnregisterInterest() {

		SessionIdInterestRegisteringCacheListener listener =
			spy(this.sessionRepository.newSessionIdInterestRegistrar());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");

		listener.afterDestroy(mockEntryEvent);

		verify(listener, times(1)).getSessionRepository();
		verify(mockEntryEvent, times(1)).getKey();
		verify(this.sessionRepository).unregisterInterest(eq("1"));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void sessionIdInterestRegisteringCacheListerAfterInvalidateCallsUnregisterInterest() {

		SessionIdInterestRegisteringCacheListener listener =
			spy(this.sessionRepository.newSessionIdInterestRegistrar());

		EntryEvent mockEntryEvent = mock(EntryEvent.class);

		when(mockEntryEvent.getKey()).thenReturn("1");

		listener.afterInvalidate(mockEntryEvent);

		verify(listener, times(1)).getSessionRepository();
		verify(mockEntryEvent, times(1)).getKey();
		verify(this.sessionRepository).unregisterInterest(eq("1"));
	}

	@Test
	public void constructDefaultGemFireSession() {

		Instant testCreationTime = Instant.now();

		GemFireSession session = new GemFireSession();

		assertThat(session.getId()).isNotNull();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test
	public void constructGemFireSessionWithId() {

		Instant testCreationTime = Instant.now();

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	private void testConstructGemFireSessionWithInvalidId(String id) {

		try {
			new GemFireSession(id);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("ID is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithEmptyId() {
		testConstructGemFireSessionWithInvalidId("");
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithNullId() {
		testConstructGemFireSessionWithInvalidId(null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithUnspecifiedId() {
		testConstructGemFireSessionWithInvalidId("  ");
	}

	@Test
	public void constructGemFireSessionWithSession() {

		Instant expectedCreationTime = Instant.ofEpochMilli(1L);
		Instant expectedLastAccessTime = Instant.ofEpochMilli(2L);

		Duration expectedMaxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mockSession("2", expectedCreationTime.toEpochMilli(),
			expectedLastAccessTime.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Set<String> expectedAttributedNames = asSet("attributeOne", "attributeTwo");

		when(mockSession.getAttributeNames()).thenReturn(expectedAttributedNames);
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attributeTwo"))).thenReturn("testTwo");

		GemFireSession gemfireSession = new GemFireSession(mockSession);

		assertThat(gemfireSession.getId()).isEqualTo("2");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.hasDelta()).isTrue();
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
		assertThat(gemfireSession.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
		assertThat(gemfireSession.getAttributeNames()).isEqualTo(expectedAttributedNames);
		assertThat(gemfireSession.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(gemfireSession.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
		verify(mockSession, times(1)).getAttribute(eq("attributeTwo"));
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructGemFireSessionWithNull() {

		try {
			new GemFireSession((Session) null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Session is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void createNewGemFireSession() {

		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		Instant testCreationTime = Instant.now();

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session).isNotInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test
	public void createNewDeltaCapableGemFireSession() {

		this.sessionRepository.setUseDataSerialization(true);

		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isTrue();

		Instant testCreationTime = Instant.now();

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.getCreationTime()).isAfterOrEqualTo(testCreationTime);
		assertThat(session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(session.getLastAccessedTime()).isEqualTo(session.getCreationTime());
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.getAttributes()).isNotNull();
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test(expected = IllegalArgumentException.class)
	public void copyNullThrowsIllegalArgumentException() {

		try {
			GemFireSession.copy(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Session is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void copySessionWhenNotUsingDataSerialization() {

		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isFalse();

		Session mockSession = mockSession();

		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attributeOne"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("test");

		GemFireSession<?> sessionCopy = GemFireSession.copy(mockSession);

		assertThat(sessionCopy).isNotNull();
		assertThat(sessionCopy).isNotInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(sessionCopy.getId()).isEqualTo(mockSession.getId());
		assertThat(sessionCopy.getCreationTime()).isEqualTo(mockSession.getCreationTime());
		assertThat(sessionCopy.hasDelta()).isTrue();
		assertThat(sessionCopy.getLastAccessedTime()).isEqualTo(mockSession.getLastAccessedTime());
		assertThat(sessionCopy.getMaxInactiveInterval()).isEqualTo(mockSession.getMaxInactiveInterval());
		assertThat(sessionCopy.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionCopy.<String>getAttribute("attributeOne")).isEqualTo("test");

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(2)).getCreationTime();
		verify(mockSession, times(2)).getLastAccessedTime();
		verify(mockSession, times(2)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
	}

	@Test
	public void copySessionWhenUsingDataSerialization() {

		this.sessionRepository.setUseDataSerialization(true);

		assertThat(AbstractGemFireOperationsSessionRepository.isUsingDataSerialization()).isTrue();

		Session mockSession = mockSession();

		when(mockSession.getAttributeNames()).thenReturn(Collections.singleton("attributeOne"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("test");

		GemFireSession<?> sessionCopy = GemFireSession.copy(mockSession);

		assertThat(sessionCopy).isInstanceOf(DeltaCapableGemFireSession.class);
		assertThat(sessionCopy.getId()).isEqualTo(mockSession.getId());
		assertThat(sessionCopy.getCreationTime()).isEqualTo(mockSession.getCreationTime());
		assertThat(sessionCopy.hasDelta()).isTrue();
		assertThat(sessionCopy.getLastAccessedTime()).isEqualTo(mockSession.getLastAccessedTime());
		assertThat(sessionCopy.getMaxInactiveInterval()).isEqualTo(mockSession.getMaxInactiveInterval());
		assertThat(sessionCopy.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionCopy.<String>getAttribute("attributeOne")).isEqualTo("test");

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(2)).getCreationTime();
		verify(mockSession, times(2)).getLastAccessedTime();
		verify(mockSession, times(2)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
	}

	@Test
	public void fromExistingGemFireSessionIsGemFireSession() {

		GemFireSession<?> gemfireSession = GemFireSession.create();

		GemFireSession<?> fromGemFireSession = GemFireSession.from(gemfireSession);

		assertThat(fromGemFireSession).isSameAs(gemfireSession);
	}

	@Test
	public void fromExistingSessionCopiesSession() {

		Instant expectedCreationTime = Instant.ofEpochMilli(1L);
		Instant expectedLastAccessedTime = Instant.ofEpochMilli(2L);

		Duration expectedMaxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mockSession("4", expectedCreationTime.toEpochMilli(),
			expectedLastAccessedTime.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);

		when(mockSession.getAttributeNames()).thenReturn(Collections.emptySet());

		GemFireSession<?> gemfireSession = GemFireSession.from(mockSession);

		assertThat(gemfireSession).isNotNull();
		assertThat(gemfireSession.getId()).isEqualTo("4");
		assertThat(gemfireSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(gemfireSession.hasDelta()).isTrue();
		assertThat(gemfireSession.getLastAccessedTime()).isEqualTo(expectedLastAccessedTime);
		assertThat(gemfireSession.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
		assertThat(gemfireSession.getAttributes()).isNotNull();
		assertThat(gemfireSession.getAttributes()).isEmpty();

		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, never()).getAttribute(anyString());
	}

	@Test(expected = IllegalArgumentException.class)
	public void fromNullSessionThrowsIllegalArgumentException() {

		try {
			GemFireSession.from(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Session is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void newSessionAttributesIsConfiguredCorrectly() {

		GemFireSession<?> session = new GemFireSession<>();

		session.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		assertThat(session.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);

		GemFireSessionAttributes sessionAttributes = session.newSessionAttributes(session);

		assertThat(sessionAttributes).isNotNull();
		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.getLock()).isSameAs(session);
	}

	@Test
	public void newDeltaCapableSessionAttributesIsConfiguredCorrectly() {

		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		session.setIsDirtyPredicate(IdentityEqualsDirtyPredicate.INSTANCE);

		assertThat(session.getIsDirtyPredicate()).isEqualTo(IdentityEqualsDirtyPredicate.INSTANCE);

		DeltaCapableGemFireSessionAttributes sessionAttributes = session.newSessionAttributes(session);

		assertThat(sessionAttributes).isNotNull();
		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(IdentityEqualsDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.getLock()).isEqualTo(session);
	}

	@Test
	public void changeSessionIdIsCorrect() {

		GemFireSession<?> session = new GemFireSession<>();

		String sessionId = session.getId();

		assertThat(sessionId).isNotEmpty();
		assertThat(session.changeSessionId()).isNotEmpty();
		assertThat(session.getId()).isNotEqualTo(sessionId);
	}

	@Test
	public void setGetAndRemoveAttribute() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();

		session.setAttribute("attributeOne", "testOne");

		assertThat(session.getAttributeNames()).containsOnly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attributeTwo")).isNull();

		session.setAttribute("attributeTwo", "testTwo");

		assertThat(session.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

		session.setAttribute("attributeTwo", null);

		assertThat(session.getAttributeNames()).containsOnly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attributeTwo")).isNull();

		session.removeAttribute("attributeOne");

		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.<String>getAttribute("attributeOne")).isNull();
		assertThat(session.<String>getAttribute("attributeTwo")).isNull();
	}

	@Test
	public void isExpiredWhenMaxInactiveIntervalIsNegativeReturnsFalse() {

		Duration expectedMaxInactiveIntervalInSeconds = Duration.ofSeconds(-1);

		GemFireSession<?> session = GemFireSession.create()
			.configureWith(expectedMaxInactiveIntervalInSeconds);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveIntervalInSeconds);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenMaxInactiveIntervalIsNullReturnsFalse() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		session.setMaxInactiveInterval(null);

		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenMaxInactiveIntervalIsZeroReturnsFalse() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenSessionIsActiveReturnsFalse() {

		long expectedMaxInactiveIntervalInSeconds = TimeUnit.HOURS.toSeconds(2);

		GemFireSession<?> session = GemFireSession.create()
			.configureWith(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval())
			.isEqualTo(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));

		Instant now = Instant.now();

		session.setLastAccessedTime(now);

		assertThat(session.getLastAccessedTime()).isEqualTo(now);
		assertThat(session.isExpired()).isFalse();
	}

	@Test
	public void isExpiredWhenSessionIsInactiveReturnsTrue() {

		Duration maxInactiveInterval = Duration.ofMillis(1);

		GemFireSession<?> session = GemFireSession.create()
			.configureWith(maxInactiveInterval);

		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(maxInactiveInterval);

		long diff;

		do {
			diff = System.currentTimeMillis() - session.getLastAccessedTime().toEpochMilli();
		}
		while (diff < maxInactiveInterval.toMillis() + 1);

		assertThat(session.isExpired()).isTrue();
	}

	@Test
	public void setAndGetGemFireSessionIsDirtyPredicate() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		IsDirtyPredicate mockDirtyPredicate = mock(IsDirtyPredicate.class);

		session.setIsDirtyPredicate(mockDirtyPredicate);

		assertThat(session.getIsDirtyPredicate()).isEqualTo(mockDirtyPredicate);

		session.setIsDirtyPredicate(null);

		assertThat(session.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		session.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		assertThat(session.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
	}

	@Test
	public void setAndGetLastAccessedTime() {

		Instant inTheBeginning = Instant.now();

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(inTheBeginning);
		assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());

		Instant lastAccessedTime = session.getLastAccessedTime();

		session.setLastAccessedTime(Instant.now());

		assertThat(session.getLastAccessedTime()).isAfterOrEqualTo(lastAccessedTime);
		assertThat(session.getLastAccessedTime()).isBeforeOrEqualTo(Instant.now());

		lastAccessedTime = session.getLastAccessedTime();

		session.setLastAccessedTime(lastAccessedTime.plusSeconds(5));

		assertThat(session.getLastAccessedTime()).isAfter(lastAccessedTime);
		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessedTime.plusSeconds(5));
	}

	@Test
	public void setLastAccessedTimeInThePast() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		Instant lastAccessedTime = session.getLastAccessedTime();

		assertThat(lastAccessedTime).isNotNull();
		assertThat(lastAccessedTime).isBeforeOrEqualTo(Instant.now());

		session.setLastAccessedTime(lastAccessedTime.minusMillis(1));

		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessedTime.minusMillis(1));
		assertThat(session.getLastAccessedTime()).isBefore(Instant.now());

		session.setLastAccessedTime(lastAccessedTime.minusSeconds(300));

		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessedTime.minusSeconds(300));
		assertThat(session.getLastAccessedTime()).isBefore(Instant.now());
	}

	@Test
	public void setLastAccessedTimeCannotBeSetToNull() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		Instant lastAccessedTime = session.getLastAccessedTime();

		assertThat(lastAccessedTime).isNotNull();
		assertThat(lastAccessedTime).isBeforeOrEqualTo(Instant.now());

		session.setLastAccessedTime(null);

		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessedTime);
		assertThat(lastAccessedTime).isBeforeOrEqualTo(Instant.now());
	}

	@Test
	public void setAndGetPrincipalName() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.getPrincipalName()).isNull();

		session.setPrincipalName("jxblum");

		assertThat(session.getPrincipalName()).isEqualTo("jxblum");
		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("jxblum");

		session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, "rwinch");

		assertThat(session.getAttributeNames()).isEqualTo(asSet(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME));
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME)).isEqualTo("rwinch");
		assertThat(session.getPrincipalName()).isEqualTo("rwinch");

		session.removeAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);

		assertThat(session.getPrincipalName()).isNull();
	}

	@Test
	public void configuresIsDirtyPredicateReturnsGemFireSession() {

		GemFireSession<?> session = new GemFireSession<>();

		assertThat(session.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(session.configureWith(EqualsDirtyPredicate.INSTANCE)).isSameAs(session);
		assertThat(session.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
	}

	@Test
	public void configuresMaxInactiveIntervalReturnsGemFireSession() {

		GemFireSession<?> session = new GemFireSession<>();

		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(session.configureWith(Duration.ofSeconds(1))).isSameAs(session);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(1));
	}

	@Test
	public void sessionToDelta() throws Exception {

		DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		Instant lastAccessedTime = session.getLastAccessedTime().plusSeconds(1);

		session.setLastAccessedTime(lastAccessedTime);
		session.setMaxInactiveInterval(Duration.ofSeconds(300L));
		session.setAttribute("attributeOne", "test");

		assertThat(session.hasDelta()).isTrue();

		session.toDelta(mockDataOutput);

		assertThat(session.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeUTF(eq(session.getId()));
		verify(mockDataOutput, times(1)).writeLong(eq(lastAccessedTime.toEpochMilli()));
		verify(mockDataOutput, times(1)).writeLong(eq(300L));
		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attributeOne"));
	}

	@Test
	public void sessionFromDelta() throws Exception {

		DataInput mockDataInput = mock(DataInput.class);

		Instant lastAccessedTime = Instant.now().plusSeconds(5);

		when(mockDataInput.readUTF()).thenReturn("1");
		when(mockDataInput.readLong()).thenReturn(lastAccessedTime.toEpochMilli()).thenReturn(300L);
		when(mockDataInput.readInt()).thenReturn(0);

		DeltaCapableGemFireSession session = new DeltaCapableGemFireSession();

		Instant creationTime = session.getCreationTime();

		session.fromDelta(mockDataInput);

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.getCreationTime()).isEqualTo(creationTime);
		assertThat(session.getLastAccessedTime()).isEqualTo(lastAccessedTime);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(300L));
		assertThat(session.getAttributeNames().isEmpty()).isTrue();

		verify(mockDataInput, times(1)).readUTF();
		verify(mockDataInput, times(2)).readLong();
		verify(mockDataInput, times(1)).readInt();
	}

	@Test
	public void sessionHasDeltaWithExistingSessionReturnsTrue() {

		GemFireSession<?> session = GemFireSession.from(mockSession());

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionHasDeltaWithNewSessionReturnsTrue() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionHasDeltaWhenSessionIsNotDirtyReturnsFalse() {
		assertThat(newNonDirtyGemFireSession().hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionAttributeIsAddedReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("attributeOne", "one");

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionAttributeIsRemovedReturnsTrue() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		session.setAttribute("attributeOne", "one");
		session.commit();

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(session.hasDelta()).isFalse();

		session.removeAttribute("attributeOne");

		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.<String>getAttribute("attributeOne")).isNull();
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionAttributeIsUpdatedReturnsTrue() {

		GemFireSession<?> session = GemFireSession.create();

		assertThat(session).isNotNull();

		session.setAttribute("attributeOne", "one");
		session.commit();

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(session.hasDelta()).isFalse();

		session.setAttribute("attributeOne", "two");

		assertThat(session.getAttributeNames()).containsExactly("attributeOne");
		assertThat(session.<String>getAttribute("attributeOne")).isEqualTo("two");
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionIdChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.hasDelta()).isFalse();

		String currentSessionId = session.getId();

		assertThat(currentSessionId).isNotEmpty();
		assertThat(session.changeSessionId()).isNotEqualTo(currentSessionId);
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionLastAccessedTimeChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		Instant lastAccessedTime = session.getLastAccessedTime();

		assertThat(lastAccessedTime).isNotNull();

		session.setLastAccessedTime(lastAccessedTime.plus(Duration.ofSeconds(5)));

		assertThat(session.getLastAccessedTime()).isAfter(lastAccessedTime);
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionMaxInactiveIntervalChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		Duration maxInactiveInterval = session.getMaxInactiveInterval();

		assertThat(maxInactiveInterval).isNotNull();

		session.setMaxInactiveInterval(maxInactiveInterval.plus(Duration.ofSeconds(5)));

		assertThat(session.getMaxInactiveInterval()).isGreaterThan(maxInactiveInterval);
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionPrincipalNameChangesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.getPrincipalName()).isNull();
		assertThat(session.hasDelta()).isFalse();

		session.setPrincipalName("jxblum");

		assertThat(session.getPrincipalName()).isEqualTo("jxblum");
		assertThat(session.hasDelta()).isTrue();

		session.commit();

		assertThat(session.hasDelta()).isFalse();
	}

	@Test
	public void sessionHasDeltaWhenSessionIsDirtyAndAttributesAreNotModifiedOnSubsequentUpdatesReturnsTrue() {

		GemFireSession<?> session = newNonDirtyGemFireSession();

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();

		String previousSessionId = session.getId();

		Instant newLastAccessedTime = session.getLastAccessedTime().plusSeconds(5L);

		Duration newMaxInactiveInterval = session.getMaxInactiveInterval().plusSeconds(5L);

		assertThat(session.changeSessionId()).isNotEqualTo(previousSessionId);

		session.setAttribute("attributeOne", "testOne");
		session.setLastAccessedTime(newLastAccessedTime);
		session.setMaxInactiveInterval(newMaxInactiveInterval);
		session.setPrincipalName("TestPrincipal");

		assertThat(session.hasDelta()).isTrue();

		session.setAttribute("attributeOne", "testOne");
		session.setLastAccessedTime(newLastAccessedTime);
		session.setMaxInactiveInterval(newMaxInactiveInterval);
		session.setPrincipalName("TestPrincipal");

		assertThat(session.hasDelta()).isTrue();
	}

	@Test
	public void sessionCompareTo() {

		Instant twoHoursAgo = Instant.now().minusMillis(TimeUnit.HOURS.toMillis(2));

		Session mockSession = mockSession("1", twoHoursAgo.toEpochMilli(), MAX_INACTIVE_INTERVAL_IN_SECONDS);

		GemFireSession<?> sessionOne = new GemFireSession<>(mockSession);

		GemFireSession<?> sessionTwo = new GemFireSession<>("2");

		assertThat(sessionOne.getCreationTime()).isEqualTo(twoHoursAgo);
		assertThat(sessionTwo.getCreationTime().isAfter(twoHoursAgo)).isTrue();
		assertThat(sessionOne.compareTo(sessionTwo)).isLessThan(0);
		assertThat(sessionOne.compareTo(sessionOne)).isEqualTo(0);
		assertThat(sessionTwo.compareTo(sessionOne)).isGreaterThan(0);
	}

	@Test
	@SuppressWarnings("all")
	public void sessionEqualsDifferentLogicalSessionBasedOnId() {

		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(Instant.ofEpochSecond(12345L));
		sessionOne.setMaxInactiveInterval(Duration.ofSeconds(120L));
		sessionOne.setPrincipalName("jxblum");

		long timestamp = System.currentTimeMillis();

		while (System.currentTimeMillis() == timestamp);

		GemFireSession sessionTwo = new GemFireSession("1");

		sessionTwo.setLastAccessedTime(Instant.ofEpochSecond(67890L));
		sessionTwo.setMaxInactiveInterval(Duration.ofSeconds(300L));
		sessionTwo.setPrincipalName("rwinch");

		assertThat(sessionTwo.getId().equals(sessionOne.getId())).isTrue();
		assertThat(sessionTwo.getCreationTime()).isNotEqualTo(sessionOne.getCreationTime());
		assertThat(sessionTwo.getLastAccessedTime()).isNotEqualTo(sessionOne.getLastAccessedTime());
		assertThat(sessionTwo.getMaxInactiveInterval()).isNotEqualTo(sessionOne.getMaxInactiveInterval());
		assertThat(sessionTwo.getPrincipalName()).isNotEqualTo(sessionOne.getPrincipalName());
		assertThat(sessionOne.equals(sessionTwo)).isTrue();
		assertThat(sessionTwo.equals(sessionOne)).isTrue();
	}

	@Test
	public void sessionNotEqualToDifferentSessionBasedOnId() {

		GemFireSession sessionOne = new GemFireSession("1");

		sessionOne.setLastAccessedTime(Instant.ofEpochSecond(12345L));
		sessionOne.setMaxInactiveInterval(Duration.ofSeconds(120L));
		sessionOne.setPrincipalName("jxblum");

		GemFireSession sessionTwo = new GemFireSession(sessionOne);

		sessionTwo.changeSessionId();

		assertThat(sessionTwo.getId().equals(sessionOne.getId())).isFalse();
		assertThat(sessionTwo.getCreationTime()).isEqualTo(sessionOne.getCreationTime());
		assertThat(sessionTwo.getLastAccessedTime()).isEqualTo(sessionOne.getLastAccessedTime());
		assertThat(sessionTwo.getMaxInactiveInterval()).isEqualTo(sessionOne.getMaxInactiveInterval());
		assertThat(sessionTwo.getPrincipalName()).isEqualTo(sessionOne.getPrincipalName());
		assertThat(sessionOne.equals(sessionTwo)).isFalse();
		assertThat(sessionTwo.equals(sessionOne)).isFalse();
	}

	@Test
	public void sessionHashCodeIsNotEqualToStringHashCode() {

		GemFireSession session = new GemFireSession("1");

		assertThat(session.getId()).isEqualTo("1");
		assertThat(session.hashCode()).isNotEqualTo("1".hashCode());
	}

	@Test
	public void sessionHashCodeWithEqualSessionsHaveSameHashCode() {

		GemFireSession<?> sessionOne = new GemFireSession("1");
		GemFireSession<?> sessionTwo = new GemFireSession(sessionOne);

		assertThat(sessionOne).isNotSameAs(sessionTwo);
		assertThat(sessionOne).isEqualTo(sessionTwo);
		assertThat(sessionOne.hashCode()).isEqualTo(sessionTwo.hashCode());
	}

	@Test
	public void sessionHashCodeWithUnequalSessionsHaveDifferentHashCodes() {

		GemFireSession<?> sessionOne = new GemFireSession("1");
		GemFireSession<?> sessionTwo = new GemFireSession(sessionOne);

		sessionTwo.changeSessionId();

		assertThat(sessionOne).isNotSameAs(sessionTwo);
		assertThat(sessionOne).isNotEqualTo(sessionTwo);
		assertThat(sessionOne.hashCode()).isNotEqualTo(sessionTwo.hashCode());
	}

	@Test @SuppressWarnings("unchecked")
	public void sessionToStringContainsId() {

		Session mockSession = mockSession();

		GemFireSession session = GemFireSession.from(mockSession);

		assertThat(session).isNotNull();
		assertThat(session.getId()).isEqualTo(mockSession.getId());
		assertThat(session.toString()).startsWith(String.format("{ @type = %1$s, id = %2$s",
			session.getClass().getName(), session.getId()));
	}

	@Test
	public void sessionAttributesFromMap() {

		Map<String, Object> source = new HashMap<>();

		source.put("attributeOne", "testOne");
		source.put("attributeTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target).isEmpty();

		target.from(source);

		assertThat(target).hasSize(2);
		assertThat(target.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(target.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(target.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
	}

	@Test
	public void sessionAttributesFromSession() {

		Session mockSession = mock(Session.class);

		when(mockSession.getAttributeNames()).thenReturn(asSet("attributeOne", "attributeTwo"));
		when(mockSession.getAttribute(eq("attributeOne"))).thenReturn("testOne");
		when(mockSession.getAttribute(eq("attributeTwo"))).thenReturn("testTwo");

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes).isEmpty();

		sessionAttributes.from(mockSession);

		assertThat(sessionAttributes).hasSize(2);
		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");

		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attributeOne"));
		verify(mockSession, times(1)).getAttribute(eq("attributeTwo"));
	}

	@Test
	public void sessionAttributesFromSessionAttributes() {

		GemFireSessionAttributes source = new GemFireSessionAttributes();

		source.setAttribute("attributeOne", "testOne");
		source.setAttribute("attributeTwo", "testTwo");

		GemFireSessionAttributes target = new GemFireSessionAttributes();

		assertThat(target).isEmpty();

		target.from(source);

		assertThat(target).hasSize(2);
		assertThat(target.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(target.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(target.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
	}

	@Test
	public void setAndGetGemFireSessionAttributesIsDirtyPredicate() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		IsDirtyPredicate mockDirtyPredicate = mock(IsDirtyPredicate.class);

		sessionAttributes.setIsDirtyPredicate(mockDirtyPredicate);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(mockDirtyPredicate);

		sessionAttributes.setIsDirtyPredicate(null);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);

		sessionAttributes.setIsDirtyPredicate(EqualsDirtyPredicate.INSTANCE);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
	}

	@Test
	public void sessionAttributesToDelta() throws Exception {

		AtomicInteger count = new AtomicInteger(0);

		DataOutput mockDataOutput = mock(DataOutput.class);

		@SuppressWarnings("serial")
		DeltaCapableGemFireSessionAttributes sessionAttributes = spy(new DeltaCapableGemFireSessionAttributes());

		doAnswer(invocation -> {

			assertThat(Arrays.asList("testOne", "testTwo", "testThree").get(count.getAndIncrement()))
				.isEqualTo(invocation.getArgument(0));

			assertThat(invocation.<DataOutput>getArgument(1)).isSameAs(mockDataOutput);

			return null;

		}).when(sessionAttributes).writeObject(any(), isA(DataOutput.class));

		sessionAttributes.setAttribute("attributeOne", "testOne");
		sessionAttributes.setAttribute("attributeTwo", "testTwo");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF("attributeOne");
		verify(mockDataOutput, times(1)).writeUTF("attributeTwo");
		reset(mockDataOutput);

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.toDelta(mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(0));
		verify(mockDataOutput, never()).writeUTF(any(String.class));
		reset(mockDataOutput);

		sessionAttributes.setAttribute("attributeTwo", "testThree");

		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.toDelta(mockDataOutput);

		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeInt(eq(1));
		verify(mockDataOutput, times(1)).writeUTF(eq("attributeTwo"));
	}

	@Test
	public void sessionAttributesFromDelta() throws Exception {

		DataInput mockDataInput = mock(DataInput.class);

		when(mockDataInput.readInt()).thenReturn(2);
		when(mockDataInput.readUTF()).thenReturn("attributeOne").thenReturn("attributeTwo");

		DeltaCapableGemFireSessionAttributes sessionAttributes = spy(new DeltaCapableGemFireSessionAttributes());

		AtomicInteger count = new AtomicInteger(0);

		doAnswer(invocation -> {

			assertThat(invocation.<DataInput>getArgument(0)).isSameAs(mockDataInput);

			return Arrays.asList("testOne", "testTwo", "testThree").get(count.getAndIncrement());

		}).when(sessionAttributes).readObject(any(DataInput.class));

		sessionAttributes.setAttribute("attributeOne", "one");
		sessionAttributes.setAttribute("attributeTwo", "two");

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();

		reset(mockDataInput);

		when(mockDataInput.readInt()).thenReturn(1);
		when(mockDataInput.readUTF()).thenReturn("attributeTwo");

		sessionAttributes.setAttribute("attributeOne", "one");
		sessionAttributes.setAttribute("attributeTwo", "two");

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("two");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.fromDelta(mockDataInput);

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("attributeOne", "attributeTwo");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("one");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testThree");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(1)).readUTF();
	}

	@Test
	public void sessionAttributesHasDeltaReturnsFalse() {
		assertThat(new GemFireSessionAttributes().hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes).hasSize(1);
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes).hasSize(1);
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeTwo", "testTwo");

		assertThat(sessionAttributes).hasSize(2);
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeTwo")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isTrue();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetAddsAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		// Set attribute to the same value again to make sure it does not clear the dirty bit
		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetDoesNotModifyAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testOne");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetModifiesAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", "testTwo");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testTwo");
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenSetRemovesAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.setAttribute("attributeOne", null);

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isNull();
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenRemovingExistingAttributeReturnsTrue() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.removeAttribute("attributeOne");

		// Remove attribute again to make sure it does not clear the dirty bit
		sessionAttributes.removeAttribute("attributeOne");

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isNull();
		assertThat(sessionAttributes.hasDelta()).isTrue();

		sessionAttributes.commit();

		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenRemovingNonExistingAttributeReturnsFalse() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.from(Collections.singletonMap("attributeOne", "testOne"));
		sessionAttributes.commit();

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.removeAttribute("nonExistingAttribute");

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("attributeOne");
		assertThat(sessionAttributes.<String>getAttribute("attributeOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void sessionAttributesHasDeltaWhenRemovingNullReturnsFalse() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();

		sessionAttributes.removeAttribute(null);

		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();
	}

	@Test
	public void deltaSessionAttributesHasDeltaAnytimeSetAttributeIsCalled() {

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.getMap().put("1", "TEST");

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo("TEST");
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", "TEST");

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo("TEST");
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");

		sessionAttributes.commit();

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo("TEST");
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", "TEST");

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo("TEST");
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");
	}

	@Test
	public void deltaSessionAttributesHasDeltaWhenExistingDeltaObjectHasDeltaIsTrue() {

		Delta mockDelta = mock(Delta.class);

		when(mockDelta.hasDelta()).thenReturn(true);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.getMap().put("1", mockDelta);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", mockDelta);

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");

		verify(mockDelta, times(1)).hasDelta();
	}

	@Test
	public void deltaSessionAttributesHasDeltaWhenExistingObjectIsReplacedByDeltaObject() {

		Delta mockDelta = mock(Delta.class, withSettings().lenient());

		when(mockDelta.hasDelta()).thenReturn(false);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.getMap().put("1", new Tombstone());

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<Tombstone>getAttribute("1")).isInstanceOf(Tombstone.class);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", mockDelta);

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");

		verify(mockDelta, never()).hasDelta();
	}

	@Test
	public void deltaSessionAttributesHasDeltaEvenWhenNonExistingDeltaObjectHasDeltaIsFalse() {

		Delta mockDelta = mock(Delta.class, withSettings().lenient());

		when(mockDelta.hasDelta()).thenReturn(false);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<Object>getAttribute("1")).isNull();
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", mockDelta);

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");

		verify(mockDelta, never()).hasDelta();
	}

	@Test
	public void deltaSessionAttributesHasDeltaWhenExistingDeltaObjectIsRemoved() {

		Delta mockDelta = mock(Delta.class);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.getMap().put("1", mockDelta);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.removeAttribute("1");

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isNull();
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");

		verify(mockDelta, never()).hasDelta();
	}

	@Test
	public void deltaSessionAttributesHasNoDeltaWhenExistingDeltaObjectHasDeltaIsFalse() {

		Delta mockDelta = mock(Delta.class);

		when(mockDelta.hasDelta()).thenReturn(false);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.getMap().put("1", mockDelta);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", mockDelta);

		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		verify(mockDelta, times(1)).hasDelta();
	}

	@Test
	public void deltaSessionAttributesAlwaysHasDeltaWhenIsDirtyPredicateAlwaysReturnsTrue() {

		Delta mockDelta = mock(Delta.class, withSettings().lenient());

		when(mockDelta.hasDelta()).thenReturn(false);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.setIsDirtyPredicate(IsDirtyPredicate.ALWAYS_DIRTY);
		sessionAttributes.getMap().put("1", mockDelta);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(IsDirtyPredicate.ALWAYS_DIRTY);
		assertThat(sessionAttributes.getAttributeNames()).containsExactly("1");
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", mockDelta);
		sessionAttributes.setAttribute("1", mockDelta);
		sessionAttributes.setAttribute("1", mockDelta);

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("1");
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("1");

		verify(mockDelta, never()).hasDelta();
	}

	@Test
	public void deltaSessionAttributesNeverHasDeltaWhenIsDirtyPredicateAlwaysReturnsFalse() {

		Delta mockDelta = mock(Delta.class, withSettings().lenient());

		when(mockDelta.hasDelta()).thenReturn(true);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.setIsDirtyPredicate(IsDirtyPredicate.NEVER_DIRTY);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(IsDirtyPredicate.NEVER_DIRTY);
		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", "TEST");
		sessionAttributes.setAttribute("2", mockDelta);
		sessionAttributes.setAttribute("3", new Object());

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("1", "2", "3");
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		verify(mockDelta, never()).hasDelta();
	}

	@Test
	public void deltaSessionAttributesOnlyHasDeltaWhenIsDirtyPredicateReturnsTrue() {

		Delta mockDelta = mock(Delta.class, withSettings().lenient());

		when(mockDelta.hasDelta()).thenReturn(true);

		IsDirtyPredicate mockDirtyPredicate = mock(IsDirtyPredicate.class);

		when(mockDirtyPredicate.isDirty(any(), any())).thenReturn(false).thenReturn(false).thenReturn(true);

		DeltaCapableGemFireSessionAttributes sessionAttributes = new DeltaCapableGemFireSessionAttributes();

		sessionAttributes.setIsDirtyPredicate(mockDirtyPredicate);

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(mockDirtyPredicate);
		assertThat(sessionAttributes.getAttributeNames()).isEmpty();
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("1", mockDelta);

		assertThat(sessionAttributes.getAttributeNames()).containsExactly("1");
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("2", "TEST");

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("1", "2");
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.<String>getAttribute("2")).isEqualTo("TEST");
		assertThat(sessionAttributes.hasDelta()).isFalse();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).isEmpty();

		sessionAttributes.setAttribute("2", "TEST");

		assertThat(sessionAttributes.getAttributeNames()).containsOnly("1", "2");
		assertThat(sessionAttributes.<Delta>getAttribute("1")).isEqualTo(mockDelta);
		assertThat(sessionAttributes.<String>getAttribute("2")).isEqualTo("TEST");
		assertThat(sessionAttributes.hasDelta()).isTrue();
		assertThat(sessionAttributes.getSessionAttributeDeltas()).containsExactly("2");

		verify(mockDelta, never()).hasDelta();
		verify(mockDirtyPredicate, times(1)).isDirty(eq(null), eq(mockDelta));
		verify(mockDirtyPredicate, times(1)).isDirty(eq(null), eq("TEST"));
		verify(mockDirtyPredicate, times(1)).isDirty(eq("TEST"), eq("TEST"));
	}

	@Test
	public void sessionAttributesEntrySetIteratesAttributeNamesAndValues() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		sessionAttributes.setAttribute("keyOne", "valueOne");
		sessionAttributes.setAttribute("keyTwo", "valueTwo");

		Set<Map.Entry<String, Object>> sessionAttributeEntries = sessionAttributes.entrySet();

		assertThat(sessionAttributeEntries).isNotNull();
		assertThat(sessionAttributeEntries.size()).isEqualTo(2);

		Set<String> expectedNames = new HashSet<>(asSet("keyOne", "keyTwo"));
		Set<?> expectedValues = new HashSet<>(asSet("valueOne", "valueTwo"));

		for (Map.Entry<String, Object> entry : sessionAttributeEntries) {
			expectedNames.remove(entry.getKey());
			expectedValues.remove(entry.getValue());
		}

		assertThat(expectedNames.isEmpty()).isTrue();
		assertThat(expectedValues.isEmpty()).isTrue();

		sessionAttributes.setAttribute("keyThree", "valueThree");

		assertThat(sessionAttributeEntries.size()).isEqualTo(3);

		expectedNames = new HashSet<>(asSet("keyOne", "keyTwo"));
		expectedValues = new HashSet<>(asSet("valueOne", "valueTwo"));

		for (Map.Entry<String, Object> entry : sessionAttributeEntries) {
			expectedNames.remove(entry.getKey());
			expectedValues.remove(entry.getValue());
		}

		assertThat(expectedNames.isEmpty()).isTrue();
		assertThat(expectedValues.isEmpty()).isTrue();

		sessionAttributes.removeAttribute("keyOne");
		sessionAttributes.removeAttribute("keyTwo");

		assertThat(sessionAttributeEntries.size()).isEqualTo(1);

		Map.Entry<String, ?> entry = sessionAttributeEntries.iterator().next();

		assertThat(entry.getKey()).isEqualTo("keyThree");
		assertThat(entry.getValue()).isEqualTo("valueThree");
	}

	@Test
	public void configuresIsDirtyPredicateReturnsGemFireSessionAttributes() {

		GemFireSessionAttributes sessionAttributes = new GemFireSessionAttributes();

		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(DeltaAwareDirtyPredicate.INSTANCE);
		assertThat(sessionAttributes.<GemFireSessionAttributes>configureWith(EqualsDirtyPredicate.INSTANCE))
			.isSameAs(sessionAttributes);
		assertThat(sessionAttributes.getIsDirtyPredicate()).isEqualTo(EqualsDirtyPredicate.INSTANCE);
	}

	@Test
	public void sessionWithAttributesAreThreadSafe() throws Throwable {
		TestFramework.runOnce(new ThreadSafeSessionTestCase());
	}

	@SuppressWarnings("unused")
	protected static final class ThreadSafeSessionTestCase extends MultithreadedTestCase {

		private GemFireSession<?> session;

		private final Instant beforeOrAtCreationTime = Instant.now();

		private volatile Instant expectedCreationTime;

		@Override
		public void initialize() {

			this.session = new GemFireSession<>("1");

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isAfterOrEqualTo(this.beforeOrAtCreationTime);
			assertThat(this.session.getCreationTime()).isBeforeOrEqualTo(Instant.now());
			assertThat(this.session.getLastAccessedTime()).isEqualTo(this.session.getCreationTime());
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
			assertThat(this.session.getPrincipalName()).isNull();
			assertThat(this.session.getAttributeNames()).isEmpty();

			this.expectedCreationTime = this.session.getCreationTime();

			this.session.setLastAccessedTime(this.expectedCreationTime.plusSeconds(1));
			this.session.setMaxInactiveInterval(Duration.ofSeconds(60L));
			this.session.setPrincipalName("jxblum");
		}

		public void thread1() {

			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 1");

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(this.expectedCreationTime.plusSeconds(1));
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(60L));
			assertThat(this.session.getPrincipalName()).isEqualTo("jxblum");
			assertThat(this.session.getAttributeNames()).hasSize(1);
			assertThat(this.session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
				.isEqualTo("jxblum");

			this.session.setAttribute("junk", "test");
			this.session.setAttribute("tennis", "ping");
			this.session.setLastAccessedTime(this.expectedCreationTime.plusSeconds(2));
			this.session.setMaxInactiveInterval(Duration.ofSeconds(120L));
			this.session.setPrincipalName("rwinch");

			waitForTick(2);

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(this.expectedCreationTime.plusSeconds(3));
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(180L));
			assertThat(this.session.getPrincipalName()).isEqualTo("rwinch");
			assertThat(this.session.getAttributeNames()).hasSize(3);
			assertThat(this.session.getAttributeNames()).containsAll(asSet("tennis", "greeting"));
			assertThat(this.session.getAttributeNames().contains("junk")).isFalse();
			assertThat(this.session.<String>getAttribute("junk")).isNull();
			assertThat(this.session.<String>getAttribute("tennis")).isEqualTo("pong");
			assertThat(this.session.<String>getAttribute("greeting")).isEqualTo("hello");
		}

		public void thread2() {

			assertTick(0);

			Thread.currentThread().setName("HTTP Request Processing Thread 2");

			waitForTick(1);
			assertTick(1);

			assertThat(this.session).isNotNull();
			assertThat(this.session.getId()).isEqualTo("1");
			assertThat(this.session.getCreationTime()).isEqualTo(this.expectedCreationTime);
			assertThat(this.session.getLastAccessedTime()).isEqualTo(this.expectedCreationTime.plusSeconds(2));
			assertThat(this.session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(120L));
			assertThat(this.session.getPrincipalName()).isEqualTo("rwinch");
			assertThat(this.session.getAttributeNames()).hasSize(3);
			assertThat(this.session.getAttributeNames()).containsAll(asSet("junk", "tennis"));
			assertThat(this.session.<String>getAttribute("junk")).isEqualTo("test");
			assertThat(this.session.<String>getAttribute("tennis")).isEqualTo("ping");

			this.session.setAttribute("tennis", "pong");
			this.session.setAttribute("greeting", "hello");
			this.session.removeAttribute("junk");
			this.session.setLastAccessedTime(this.expectedCreationTime.plusSeconds(3));
			this.session.setMaxInactiveInterval(Duration.ofSeconds(180L));
			this.session.setPrincipalName("rwinch");
		}

		@Override
		public void finish() {
			this.session = null;
		}
	}

	class TestGemFireOperationsSessionRepository extends GemFireOperationsSessionRepositorySupport {

		TestGemFireOperationsSessionRepository(GemfireOperations gemfireOperations) {
			super(gemfireOperations);
		}

		@Override
		protected Pool resolvePool(String name) {
			return mockPool;
		}
	}

	static class Tombstone { }

}
