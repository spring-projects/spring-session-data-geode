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

package org.springframework.session.data.gemfire.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.CustomExpiry;
import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.client.ClientRegionShortcut;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.expiration.support.FixedTimeoutSessionExpirationPolicy;
import org.springframework.session.data.gemfire.expiration.support.SessionExpirationPolicyCustomExpiryAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ReflectionUtils;

/**
 * Integration tests asserting that idle timeout expiration takes precedence over fixed duration expiration
 * when an idle {@link Session} will expire before the fixed duration timeout.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.apache.geode.cache.Region
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.data.gemfire.config.annotation.ClientCacheApplication
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.expiration.support.FixedTimeoutSessionExpirationPolicy
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.1.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class IdleOverFixedTimeoutSessionExpirationIntegrationTests extends AbstractGemFireIntegrationTests {

	@Resource(name = GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME)
	private Region<String, Session> sessions;

	@Autowired
	private SessionExpirationPolicy sessionExpirationPolicy;

	@SuppressWarnings("unchecked")
	private <T> T invokeMethod(Object target, String methodName) throws NoSuchMethodException {

		Method method = ReflectionUtils.findMethod(target.getClass(), methodName);

		if (method == null) {
			throw new NoSuchMethodException(String.format("No method with name [%1$s] found on object of type [%2$s]",
				methodName, target.getClass()));
		}

		ReflectionUtils.makeAccessible(method);

		return (T) ReflectionUtils.invokeMethod(method, target);
	}

	@Test
	public void sessionExpirationPolicyConfigurationIsCorrect() throws Exception {

		assertThat(this.sessionExpirationPolicy).isInstanceOf(FixedTimeoutSessionExpirationPolicy.class);

		assertThat(this.sessionExpirationPolicy.getExpirationAction())
			.isEqualTo(SessionExpirationPolicy.ExpirationAction.INVALIDATE);

		assertThat(this.<Duration>invokeMethod(this.sessionExpirationPolicy, "getFixedTimeout"))
			.isEqualTo(Duration.ofSeconds(8));

		assertThat(this.<Optional<Duration>>invokeMethod(this.sessionExpirationPolicy, "getIdleTimeout")
			.orElse(null)).isEqualTo(Duration.ofSeconds(2));
	}

	@Test
	public void sessionsRegionCustomEntryIdleTimeoutExpirationConfigurationIsCorrect() throws Exception {

		assertThat(this.sessions).isNotNull();
		assertThat(this.sessions.getAttributes()).isNotNull();

		CustomExpiry<String, Session> customEntryIdleTimeout = this.sessions.getAttributes().getCustomEntryIdleTimeout();

		assertThat(customEntryIdleTimeout).isInstanceOf(SessionExpirationPolicyCustomExpiryAdapter.class);

		SessionExpirationPolicy actualSessionExpirationPolicy =
			invokeMethod(customEntryIdleTimeout, "getSessionExpirationPolicy");

		assertThat(actualSessionExpirationPolicy).isEqualTo(this.sessionExpirationPolicy);
	}

	@Test
	public void sessionsRegionEntryIdleTimeoutExpirationConfigurationIsCorrect() {

		assertThat(this.sessions).isNotNull();
		assertThat(this.sessions.getAttributes()).isNotNull();

		ExpirationAttributes entryIdleTimeout = this.sessions.getAttributes().getEntryIdleTimeout();

		assertThat(entryIdleTimeout).isNotNull();
		assertThat(entryIdleTimeout.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
		assertThat(entryIdleTimeout.getTimeout()).isEqualTo((int) Duration.ofSeconds(2).getSeconds());
	}

	@Test
	public void sessionExpiresAfterIdleDuration() {

		Session session = save(touch(createSession()));

		assertThat(session).isNotNull();
		assertThat(session.getId()).isNotEmpty();
		assertThat(session.isExpired()).isFalse();

		Instant creationTime = session.getCreationTime();
		Instant lastAccessTime = session.getLastAccessedTime();

		waitOn(() -> false, Duration.ofSeconds(1).toMillis());

		Session loadedSession = get(session.getId());

		assertThat(loadedSession).isEqualTo(session);
		assertThat(loadedSession.isExpired()).isFalse();
		assertThat(loadedSession.getLastAccessedTime().isAfter(lastAccessTime)).isTrue();

		waitOn(() -> false, Duration.ofSeconds(2).toMillis() + 1);

		Session expiredSession = get(loadedSession.getId());

		assertThat(expiredSession).isNull();
		assertThat(System.currentTimeMillis() - creationTime.toEpochMilli())
			.isLessThan(Duration.ofSeconds(8).toMillis());
	}

	@ClientCacheApplication(logLevel = "error")
	@EnableGemFireHttpSession(
		clientRegionShortcut = ClientRegionShortcut.LOCAL,
		poolName = "DEFAULT",
		maxInactiveIntervalInSeconds = 2,
		sessionExpirationPolicyBeanName = "fixedTimeoutSessionExpirationPolicy"
	)
	static class TestConfiguration {

		@Bean
		SessionExpirationPolicy fixedTimeoutSessionExpirationPolicy() {
			return new FixedTimeoutSessionExpirationPolicy(Duration.ofSeconds(8));
		}
	}
}
