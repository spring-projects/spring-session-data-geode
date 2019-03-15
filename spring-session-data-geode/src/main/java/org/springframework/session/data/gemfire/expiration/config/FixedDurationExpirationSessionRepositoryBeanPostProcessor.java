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

import java.time.Duration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.session.data.gemfire.expiration.repository.FixedDurationExpirationSessionRepository;

/**
 * The {@link FixedDurationExpirationSessionRepositoryBeanPostProcessor} class wraps an existing, data store specific,
 * instance of {@link SessionRepository} in an instance of {@link FixedDurationExpirationSessionRepository} initialized
 * with a provided {@link Duration} for the expiration timeout to implement lazy, fixed {@link Duration} expiration
 * on all {@link Session Sessions}.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @see org.springframework.session.Session
 * @see org.springframework.session.SessionRepository
 * @see org.springframework.session.data.gemfire.expiration.repository.FixedDurationExpirationSessionRepository
 * @see <a href="https://github.com/spring-projects/spring-session/issues/922">Absolute Session Timeouts</a>
 * @since 2.1.0
 */
@SuppressWarnings("unused")
public class FixedDurationExpirationSessionRepositoryBeanPostProcessor implements BeanPostProcessor {

	private final Duration expirationTimeout;

	/**
	 * Constructs a new instance of {@link FixedDurationExpirationSessionRepositoryBeanPostProcessor} initialized with
	 * the given {@link Duration} to implement lazy, fixed {@link Duration} expiration policy
	 * on all {@link Session Sessions}.
	 *
	 * @param expirationTimeout {@link Duration} indicating the length of time until the {@link Session} expires.
	 * @see java.time.Duration
	 */
	public FixedDurationExpirationSessionRepositoryBeanPostProcessor(@Nullable Duration expirationTimeout) {
		this.expirationTimeout = expirationTimeout;
	}

	/**
	 * Returns the configured {@link Session} {@link Duration expiration timeout}.
	 *
	 * @return the configured {@link Session} {@link Duration expiration timeout}.
	 * @see java.time.Duration
	 */
	protected Duration getExpirationTimeout() {
		return this.expirationTimeout;
	}

	@Nullable @Override @SuppressWarnings("unchecked")
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {

		return bean instanceof SessionRepository
			? new FixedDurationExpirationSessionRepository<>((SessionRepository) bean, getExpirationTimeout())
			: bean;
	}
}
