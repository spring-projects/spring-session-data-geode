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

package org.springframework.session.data.gemfire.expiration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.time.Duration;

import org.junit.Test;

import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.expiration.repository.FixedDurationExpirationSessionRepository;
import org.springframework.util.ReflectionUtils;

/**
 * Unit tests for {@link FixedDurationExpirationSessionRepositoryBeanPostProcessor}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.expiration.config.FixedDurationExpirationSessionRepositoryBeanPostProcessor
 * @since 2.1.0
 */
public class FixedDurationExpirationSessionRepositoryBeanPostProcessorUnitTests {

	@SuppressWarnings("unchecked")
	private <T> T invokeMethod(Object target, String methodName) throws NoSuchMethodException {

		Method method = target.getClass().getDeclaredMethod(methodName);

		ReflectionUtils.makeAccessible(method);

		return (T) ReflectionUtils.invokeMethod(method, target);
	}

	@Test
	public void constructsFixedDurationExpirationSessionRepositoryBeanPostProcessor() {

		Duration expirationTimeout = Duration.ofMinutes(30L);

		FixedDurationExpirationSessionRepositoryBeanPostProcessor beanPostProcessor =
			new FixedDurationExpirationSessionRepositoryBeanPostProcessor(expirationTimeout);

		assertThat(beanPostProcessor).isNotNull();
		assertThat(beanPostProcessor.getExpirationTimeout()).isEqualTo(expirationTimeout);
	}

	@Test
	public void doesNotProcessNonSessionRepositoryBeans() {

		FixedDurationExpirationSessionRepositoryBeanPostProcessor beanPostProcessor =
			new FixedDurationExpirationSessionRepositoryBeanPostProcessor(null);

		Object bean = beanPostProcessor.postProcessAfterInitialization("test", "testBean");

		assertThat(bean).isNotInstanceOf(SessionRepository.class);
		assertThat(bean).isEqualTo("test");
	}

	@Test
	@SuppressWarnings("all")
	public void processesSessionRepositoryBean() throws Exception {

		Duration expirationTimeout = Duration.ofMinutes(30);

		SessionRepository<?> mockSessionRepository = mock(SessionRepository.class);

		FixedDurationExpirationSessionRepositoryBeanPostProcessor beanPostProcessor =
			new FixedDurationExpirationSessionRepositoryBeanPostProcessor(expirationTimeout);

		Object sessionRepository =
			beanPostProcessor.postProcessAfterInitialization(mockSessionRepository, "sessionRepository");

		assertThat(sessionRepository).isNotSameAs(mockSessionRepository);
		assertThat(sessionRepository).isInstanceOf(FixedDurationExpirationSessionRepository.class);
		assertThat(((FixedDurationExpirationSessionRepository<?>) sessionRepository).getExpirationTimeout()
			.orElse(null)).isEqualTo(expirationTimeout);
		assertThat(this.<SessionRepository<?>>invokeMethod(sessionRepository, "getDelegate"))
			.isEqualTo(mockSessionRepository);
	}
}
