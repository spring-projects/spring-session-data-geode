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

package org.springframework.session.data.gemfire.expiration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for {@link SessionExpirationTimeoutAwareBeanPostProcessor}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.junit.MockitoJUnitRunner
 * @see org.springframework.session.data.gemfire.expiration.config.SessionExpirationTimeoutAware
 * @see org.springframework.session.data.gemfire.expiration.config.SessionExpirationTimeoutAwareBeanPostProcessor
 * @since 1.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionExpirationTimeoutAwareBeanPostProcessorUnitTests {

	@Test
	public void constructsSessionExpirationTimeoutAwareBeanPostProcessor() {

		Duration expirationTimeout = Duration.ofMinutes(30L);

		SessionExpirationTimeoutAwareBeanPostProcessor beanPostProcessor =
			new SessionExpirationTimeoutAwareBeanPostProcessor(expirationTimeout);

		assertThat(beanPostProcessor).isNotNull();
		assertThat(beanPostProcessor.getExpirationTimeout()).isEqualTo(expirationTimeout);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructsSessionExpirationTimeoutAwareBeanPostProcessorWithNullExpirationTimeout() {

		try {
			new SessionExpirationTimeoutAwareBeanPostProcessor(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("Expiration timeout is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void configuresSessionExpirationTimeoutAwareBean() {

		Duration expirationTimeout = Duration.ofMinutes(30L);

		SessionExpirationTimeoutAware bean = mock(SessionExpirationTimeoutAware.class);

		SessionExpirationTimeoutAwareBeanPostProcessor beanPostProcessor =
			new SessionExpirationTimeoutAwareBeanPostProcessor(expirationTimeout);

		assertThat(beanPostProcessor.postProcessBeforeInitialization(bean, "testBean")).isSameAs(bean);

		verify(bean, times(1)).setExpirationTimeout(eq(expirationTimeout));
	}

	@Test
	public void doesNotConfigureNonSessionExpirationTimeoutAwareBean() {

		Duration expirationTimeout = Duration.ofMinutes(30L);

		SessionExpirationTimeoutAwareBeanPostProcessor beanPostProcessor =
			new SessionExpirationTimeoutAwareBeanPostProcessor(expirationTimeout);

		TestBean bean = new TestBean();

		assertThat(bean.getExpirationTimeout()).isNull();
		assertThat(beanPostProcessor.postProcessBeforeInitialization(bean, "testBean")).isSameAs(bean);
		assertThat(bean.getExpirationTimeout()).isNull();
	}

	@SuppressWarnings("unused")
	class TestBean {

		private Duration expirationTimeout;

		Duration getExpirationTimeout() {
			return this.expirationTimeout;
		}

		void setExpirationTimeout(Duration expirationTimeout) {
			this.expirationTimeout = expirationTimeout;
		}
	}
}
