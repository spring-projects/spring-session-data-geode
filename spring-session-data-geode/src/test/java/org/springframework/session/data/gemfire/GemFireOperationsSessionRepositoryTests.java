/*
 * Copyright 2014-2017 the original author or authors.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;
import static org.springframework.session.FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.cache.AttributesMutator;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.query.SelectResults;

import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.gemfire.GemfireAccessor;
import org.springframework.data.gemfire.GemfireOperations;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.support.GemFireUtils;
import org.springframework.session.events.AbstractSessionEvent;
import org.springframework.session.events.SessionDeletedEvent;

/**
 * Unit tests for {@link GemFireOperationsSessionRepository}.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see java.time.Instant
 * @see java.util.UUID
 * @see org.junit.Test
 * @see org.junit.runner.RunWith
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.apache.geode.cache.Region
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.context.ApplicationEventPublisher
 * @see org.springframework.data.gemfire.GemfireAccessor
 * @see org.springframework.data.gemfire.GemfireOperations
 * @see org.springframework.session.FindByIndexNameSessionRepository
 * @see org.springframework.session.Session
 * @see org.springframework.session.events.AbstractSessionEvent
 * @see org.springframework.session.data.gemfire.GemFireOperationsSessionRepository
 * @since 1.1.0
 */
@RunWith(MockitoJUnitRunner.class)
public class GemFireOperationsSessionRepositoryTests {

	protected static final int MAX_INACTIVE_INTERVAL_IN_SECONDS = 600;

	@Mock
	private ApplicationEventPublisher mockApplicationEventPublisher;

	@Mock
	private AttributesMutator<Object, Session> mockAttributesMutator;

	@Mock
	private GemfireOperationsAccessor mockTemplate;

	// Subject Under Test (SUT)
	private GemFireOperationsSessionRepository sessionRepository;

	@Mock
	private Region<Object, Session> mockRegion;

	@Before
	public void setup() throws Exception {

		when(this.mockRegion.getAttributesMutator()).thenReturn(this.mockAttributesMutator);
		when(this.mockRegion.getFullPath()).thenReturn(GemFireUtils.toRegionPath("Example"));
		when(this.mockTemplate.<Object, Session>getRegion()).thenReturn(this.mockRegion);

		this.sessionRepository = new GemFireOperationsSessionRepository(this.mockTemplate);
		this.sessionRepository.setApplicationEventPublisher(this.mockApplicationEventPublisher);
		this.sessionRepository.setMaxInactiveIntervalInSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);
		this.sessionRepository.afterPropertiesSet();

		assertThat(this.sessionRepository.getApplicationEventPublisher()).isSameAs(this.mockApplicationEventPublisher);
		assertThat(this.sessionRepository.getFullyQualifiedRegionName()).isEqualTo(GemFireUtils.toRegionPath("Example"));
		assertThat(this.sessionRepository.getMaxInactiveIntervalInSeconds()).isEqualTo(MAX_INACTIVE_INTERVAL_IN_SECONDS);
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

	@After
	public void tearDown() {

		verify(this.mockAttributesMutator, times(1)).addCacheListener(same(this.sessionRepository));
		verify(this.mockRegion, times(1)).getFullPath();
		verify(this.mockTemplate, times(1)).getRegion();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByIndexNameAndIndexValueFindsMatchingSession() {

		Session mockSession = mock(Session.class, "MockSession");

		given(mockSession.getId()).willReturn("1");

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		given(mockSelectResults.asList()).willReturn(Collections.singletonList(mockSession));

		String indexName = "vip";
		String indexValue = "rwinch";

		String expectedQql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_INDEX_NAME_INDEX_VALUE_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName(), indexName);

		given(this.mockTemplate.find(eq(expectedQql), eq(indexValue))).willReturn(mockSelectResults);

		Map<String, Session> sessions =
			this.sessionRepository.findByIndexNameAndIndexValue(indexName, indexValue);

		assertThat(sessions).isNotNull();
		assertThat(sessions.size()).isEqualTo(1);
		assertThat(sessions.get("1")).isEqualTo(mockSession);

		verify(this.mockTemplate, times(1)).find(eq(expectedQql), eq(indexValue));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSession, times(1)).getId();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameFindsMatchingSessions() throws Exception {

		Session mockSessionOne = mock(Session.class, "MockSessionOne");
		Session mockSessionTwo = mock(Session.class, "MockSessionTwo");
		Session mockSessionThree = mock(Session.class, "MockSessionThree");

		given(mockSessionOne.getId()).willReturn("1");
		given(mockSessionTwo.getId()).willReturn("2");
		given(mockSessionThree.getId()).willReturn("3");

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		given(mockSelectResults.asList()).willReturn(Arrays.asList(mockSessionOne, mockSessionTwo, mockSessionThree));

		String principalName = "jblum";

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName());

		given(this.mockTemplate.find(eq(expectedOql), eq(principalName))).willReturn(mockSelectResults);

		Map<String, Session> sessions =
			this.sessionRepository.findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, principalName);

		assertThat(sessions).isNotNull();
		assertThat(sessions.size()).isEqualTo(3);
		assertThat(sessions.get("1")).isEqualTo(mockSessionOne);
		assertThat(sessions.get("2")).isEqualTo(mockSessionTwo);
		assertThat(sessions.get("3")).isEqualTo(mockSessionThree);

		verify(this.mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
		verify(mockSessionOne, times(1)).getId();
		verify(mockSessionTwo, times(1)).getId();
		verify(mockSessionThree, times(1)).getId();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void findByPrincipalNameReturnsNoMatchingSessions() {

		SelectResults<Object> mockSelectResults = mock(SelectResults.class);

		given(mockSelectResults.asList()).willReturn(Collections.emptyList());

		String principalName = "jblum";

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName());

		given(this.mockTemplate.find(eq(expectedOql), eq(principalName))).willReturn(mockSelectResults);

		Map<String, Session> sessions =
			this.sessionRepository.findByIndexNameAndIndexValue(PRINCIPAL_NAME_INDEX_NAME, principalName);

		assertThat(sessions).isNotNull();
		assertThat(sessions.isEmpty()).isTrue();

		verify(this.mockTemplate, times(1)).find(eq(expectedOql), eq(principalName));
		verify(mockSelectResults, times(1)).asList();
	}

	@Test
	public void prepareQueryReturnsIndexNameValueOql() {

		String attributeName = "testAttributeName";

		String actualOql = this.sessionRepository.prepareQuery(attributeName);

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_INDEX_NAME_INDEX_VALUE_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName(), attributeName);

		assertThat(actualOql).isEqualTo(expectedOql);
	}

	@Test
	public void prepareQueryReturnsPrincipalNameOql() {

		String actualQql =
			this.sessionRepository.prepareQuery(PRINCIPAL_NAME_INDEX_NAME);

		String expectedOql =
			String.format(GemFireOperationsSessionRepository.FIND_SESSIONS_BY_PRINCIPAL_NAME_QUERY,
				this.sessionRepository.getFullyQualifiedRegionName());

		assertThat(actualQql).isEqualTo(expectedOql);
	}

	@Test
	public void createProperlyInitializedSession() {

		Instant beforeOrAtCreationTime = Instant.now();

		Session session = this.sessionRepository.createSession();

		assertThat(session).isInstanceOf(AbstractGemFireOperationsSessionRepository.GemFireSession.class);
		assertThat(session.getId()).isNotNull();
		assertThat(session.getAttributeNames()).isEmpty();
		assertThat(session.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getLastAccessedTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS));
	}

	@Test
	public void getSessionDeletesMatchingExpiredSessionById() {

		String expectedSessionId = "1";

		Session mockSession = mock(Session.class);

		given(mockSession.isExpired()).willReturn(true);
		given(mockSession.getId()).willReturn(expectedSessionId);
		given(this.mockTemplate.get(eq(expectedSessionId))).willReturn(mockSession);
		given(this.mockTemplate.remove(eq(expectedSessionId))).willReturn(mockSession);

		willAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.<Session>getSession()).isSameAs(mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);
			assertThat(sessionEvent.getSource())
				.isSameAs(GemFireOperationsSessionRepositoryTests.this.sessionRepository);

			return null;

		}).given(this.mockApplicationEventPublisher).publishEvent(any(ApplicationEvent.class));

		assertThat(this.sessionRepository.findById(expectedSessionId)).isNull();

		verify(this.mockTemplate, times(1)).get(eq(expectedSessionId));
		verify(this.mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(2)).getId();
		verify(this.mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void getSessionFindsMatchingNonExpiredSessionById() {

		String expectedId = "1";

		Instant expectedCreationTime = Instant.now();
		Instant currentLastAccessedTime = expectedCreationTime.plusMillis(TimeUnit.MINUTES.toMillis(5));

		Session mockSession = mock(Session.class);

		given(mockSession.isExpired()).willReturn(false);
		given(mockSession.getId()).willReturn(expectedId);
		given(mockSession.getCreationTime()).willReturn(expectedCreationTime);
		given(mockSession.getLastAccessedTime()).willReturn(currentLastAccessedTime);
		given(mockSession.getAttributeNames()).willReturn(Collections.singleton("attrOne"));
		given(mockSession.getAttribute(eq("attrOne"))).willReturn("test");
		given(this.mockTemplate.get(eq(expectedId))).willReturn(mockSession);

		Session actualSession = this.sessionRepository.findById(expectedId);

		assertThat(actualSession).isNotNull();
		assertThat(actualSession).isNotSameAs(mockSession);
		assertThat(actualSession.getId()).isEqualTo(expectedId);
		assertThat(actualSession.getCreationTime()).isEqualTo(expectedCreationTime);
		assertThat(actualSession.getLastAccessedTime()).isNotEqualTo(currentLastAccessedTime);
		assertThat(actualSession.getLastAccessedTime().compareTo(expectedCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(actualSession.getAttributeNames()).isEqualTo(Collections.singleton("attrOne"));
		assertThat(String.valueOf(actualSession.<String>getAttribute("attrOne"))).isEqualTo("test");

		verify(this.mockTemplate, times(1)).get(eq(expectedId));
		verify(mockSession, times(1)).isExpired();
		verify(mockSession, times(1)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getAttributeNames();
		verify(mockSession, times(1)).getAttribute(eq("attrOne"));
	}

	@Test
	public void getSessionReturnsNull() {
		given(this.mockTemplate.get(anyString())).willReturn(null);
		assertThat(this.sessionRepository.findById("1")).isNull();
	}

	@Test
	public void saveStoresSession() {

		String expectedSessionId = "1";

		Instant expectedCreationTime = Instant.now();
		Instant expectedLastAccessTime = expectedCreationTime.plusMillis(TimeUnit.MINUTES.toMillis(5L));

		Duration expectedMaxInactiveInterval = Duration.ofSeconds(MAX_INACTIVE_INTERVAL_IN_SECONDS);

		Session mockSession = mock(Session.class);

		given(mockSession.getId()).willReturn(expectedSessionId);
		given(mockSession.getCreationTime()).willReturn(expectedCreationTime);
		given(mockSession.getLastAccessedTime()).willReturn(expectedLastAccessTime);
		given(mockSession.getMaxInactiveInterval()).willReturn(expectedMaxInactiveInterval);
		given(mockSession.getAttributeNames()).willReturn(Collections.emptySet());

		given(this.mockTemplate.put(eq(expectedSessionId), isA(GemFireSession.class)))
			.willAnswer(invocation -> {

				Session session = invocation.getArgument(1);

				assertThat(session).isNotNull();
				assertThat(session.getId()).isEqualTo(expectedSessionId);
				assertThat(session.getCreationTime()).isEqualTo(expectedCreationTime);
				assertThat(session.getLastAccessedTime()).isEqualTo(expectedLastAccessTime);
				assertThat(session.getMaxInactiveInterval()).isEqualTo(expectedMaxInactiveInterval);
				assertThat(session.getAttributeNames().isEmpty()).isTrue();

				return null;
		});

		this.sessionRepository.save(mockSession);

		verify(mockSession, times(2)).getId();
		verify(mockSession, times(1)).getCreationTime();
		verify(mockSession, times(1)).getLastAccessedTime();
		verify(mockSession, times(1)).getMaxInactiveInterval();
		verify(mockSession, times(1)).getAttributeNames();
		verify(this.mockTemplate, times(1)).put(eq(expectedSessionId),
			isA(GemFireSession.class));
	}

	@Test
	public void saveStoresAndCommitsGemFireSession() {

		GemFireSession<?> session = spy(GemFireSession.create());

		assertThat(session).isNotNull();
		assertThat(session.isDirty()).isTrue();

		this.sessionRepository.save(session);

		InOrder orderVerifier = inOrder(session);

		orderVerifier.verify(session, times(2)).isDirty();
		orderVerifier.verify(session, times(1)).getId();
		orderVerifier.verify(session, times(1)).commit();

		verify(this.mockTemplate, times(1)).put(eq(session.getId()), eq(session));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void saveDoesNotStoreNonDirtyGemFireSessions() {

		GemFireSession session = spy(GemFireSession.from(mockSession()));

		assertThat(session).isNotNull();
		assertThat(session.hasDelta()).isFalse();
		assertThat(session.isDirty()).isFalse();

		this.sessionRepository.save(session);

		verify(session, times(2)).isDirty();
		verify(session, never()).getId();
		verify(session, never()).commit();
		verify(this.mockTemplate, never()).put(any(), any(GemFireSession.class));
	}

	@Test
	public void saveIsNullSafe() {

		this.sessionRepository.save(null);

		verify(this.mockTemplate, never()).put(any(), any());
	}

	@Test
	public void deleteRemovesExistingSessionAndHandlesDelete() {

		String expectedSessionId = "1";

		Session mockSession = mock(Session.class);

		given(mockSession.getId()).willReturn(expectedSessionId);
		given(this.mockTemplate.remove(eq(expectedSessionId))).willReturn(mockSession);

		willAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			assertThat(sessionEvent.getSource()).isSameAs(GemFireOperationsSessionRepositoryTests.this.sessionRepository);
			assertThat(sessionEvent.<Session>getSession()).isSameAs(mockSession);
			assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);

			return null;

		}).given(this.mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		this.sessionRepository.deleteById(expectedSessionId);

		verify(mockSession, times(1)).getId();
		verify(this.mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(this.mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	@Test
	public void deleteRemovesNonExistingSessionAndHandlesDelete() {

		AtomicBoolean called = new AtomicBoolean(false);

		Session mockSession = mock(Session.class);

		String expectedSessionId = "1";

		when(mockSession.getId()).thenReturn(expectedSessionId);
		when(this.mockTemplate.remove(anyString())).thenReturn(mockSession);

		doAnswer(invocation -> {

			ApplicationEvent applicationEvent = invocation.getArgument(0);

			assertThat(applicationEvent).isInstanceOf(SessionDeletedEvent.class);

			AbstractSessionEvent sessionEvent = (AbstractSessionEvent) applicationEvent;

			Session session = sessionEvent.getSession();

			assertThat(session).isNotNull();
			assertThat(session.getId()).isEqualTo(expectedSessionId);
			assertThat(sessionEvent.getSessionId()).isEqualTo(expectedSessionId);
			assertThat(sessionEvent.getSource())
				.isSameAs(GemFireOperationsSessionRepositoryTests.this.sessionRepository);

			called.set(true);

			return null;

		}).when(this.mockApplicationEventPublisher).publishEvent(isA(SessionDeletedEvent.class));

		this.sessionRepository.deleteById(expectedSessionId);

		assertThat(called.get()).isTrue();

		verify(mockSession, times(2)).getId();
		verify(this.mockTemplate, times(1)).remove(eq(expectedSessionId));
		verify(this.mockApplicationEventPublisher, times(1))
			.publishEvent(isA(SessionDeletedEvent.class));
	}

	protected abstract class GemfireOperationsAccessor extends GemfireAccessor implements GemfireOperations { }

}
