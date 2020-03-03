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

package org.springframework.session.data.gemfire.expiration;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.apache.geode.cache.ExpirationAction;
import org.apache.geode.cache.ExpirationAttributes;
import org.apache.geode.cache.Region;
import org.apache.geode.cache.RegionAttributes;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.data.gemfire.config.annotation.PeerCacheApplication;
import org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.expiration.config.SessionExpirationTimeoutAware;
import org.springframework.session.data.gemfire.expiration.support.SessionExpirationPolicyCustomExpiryAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.ReflectionUtils;

/**
 * Integration tests for {@link EnableGemFireHttpSession} and {@link GemFireHttpSessionConfiguration}
 * involving {@link Session} expiration configuration.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.apache.geode.cache.ExpirationAction
 * @see org.apache.geode.cache.ExpirationAttributes
 * @see org.apache.geode.cache.Region
 * @see org.apache.geode.cache.RegionAttributes
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.data.gemfire.config.annotation.PeerCacheApplication
 * @see org.springframework.data.gemfire.tests.mock.annotation.EnableGemFireMockObjects
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireIntegrationTests
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.expiration.config.SessionExpirationTimeoutAware
 * @see org.springframework.session.data.gemfire.expiration.support.SessionExpirationPolicyCustomExpiryAdapter
 * @see org.springframework.test.context.ContextConfiguration
 * @see org.springframework.test.context.junit4.SpringRunner
 * @since 2.1.0
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class CustomSessionExpirationConfigurationIntegrationTests extends AbstractGemFireIntegrationTests {

	@Resource(name = GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME)
	private Region<String, Object> sessions;

	@Autowired
	private SessionExpirationPolicy sessionExpirationPolicy;

	@SuppressWarnings("unchecked")
	private <T> T invokeMethod(Object target, String methodName) throws NoSuchMethodException {

		Method method = target.getClass().getDeclaredMethod(methodName);

		ReflectionUtils.makeAccessible(method);

		return (T) ReflectionUtils.invokeMethod(method, target);
	}

	@Test
	public void sessionExpirationPolicyConfigurationIsCorrect() {

		assertThat(this.sessionExpirationPolicy).isInstanceOf(TestSessionExpirationPolicy.class);
		assertThat(this.sessionExpirationPolicy.determineExpirationTimeout(null).orElse(null))
			.isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	public void regionCustomEntryIdleTimeoutExpirationConfigurationIsCorrect() throws Exception {

		assertThat(this.sessions).isNotNull();

		RegionAttributes<String, Object> sessionRegionAttributes = this.sessions.getAttributes();

		assertThat(sessionRegionAttributes).isNotNull();
		assertThat(sessionRegionAttributes.getCustomEntryIdleTimeout())
			.isInstanceOf(SessionExpirationPolicyCustomExpiryAdapter.class);

		SessionExpirationPolicyCustomExpiryAdapter customEntryIdleTimeout =
			(SessionExpirationPolicyCustomExpiryAdapter) sessionRegionAttributes.getCustomEntryIdleTimeout();

		SessionExpirationPolicy actualSessionExpirationPolicy =
			invokeMethod(customEntryIdleTimeout, "getSessionExpirationPolicy");

		assertThat(actualSessionExpirationPolicy).isEqualTo(this.sessionExpirationPolicy);

	}

	@Test
	public void regionEntryIdleTimeoutExpirationConfigurationIsCorrect() {

		assertThat(this.sessions).isNotNull();

		RegionAttributes<String, Object> sessionRegionAttributes = this.sessions.getAttributes();

		assertThat(sessionRegionAttributes).isNotNull();

		ExpirationAttributes entryIdleTimeout = sessionRegionAttributes.getEntryIdleTimeout();

		assertThat(entryIdleTimeout).isNotNull();
		assertThat(entryIdleTimeout.getTimeout()).isEqualTo(600);
		assertThat(entryIdleTimeout.getAction()).isEqualTo(ExpirationAction.INVALIDATE);
	}

	@PeerCacheApplication
	@EnableGemFireMockObjects
	@EnableGemFireHttpSession(
		maxInactiveIntervalInSeconds = 600,
		sessionExpirationPolicyBeanName = "testSessionExpirationPolicy"
	)
	static class TestConfiguration {

		@Bean
		SessionExpirationPolicy testSessionExpirationPolicy() {
			return new TestSessionExpirationPolicy();
		}
	}

	static class TestSessionExpirationPolicy implements SessionExpirationPolicy, SessionExpirationTimeoutAware {

		private Duration expirationTimeout;

		@Override
		public void setExpirationTimeout(Duration expirationTimeout) {
			this.expirationTimeout = expirationTimeout;
		}

		@Override
		public Optional<Duration> determineExpirationTimeout(Session session) {
			return Optional.ofNullable(this.expirationTimeout);
		}
	}
}
