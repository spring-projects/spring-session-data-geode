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

package org.springframework.session.data.gemfire.expiration.config;

import java.time.Duration;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.lang.Nullable;
import org.springframework.session.Session;
import org.springframework.util.Assert;

/**
 * The {@link SessionExpirationTimeoutAwareBeanPostProcessor} class is a Spring {@link BeanPostProcessor} handling
 * the post processing of all Spring beans defined in the Spring container implementing
 * the {@link SessionExpirationTimeoutAware} interface.
 *
 * @author John Blum
 * @see org.springframework.beans.factory.config.BeanPostProcessor
 * @since 2.1.0
 */
public class SessionExpirationTimeoutAwareBeanPostProcessor implements BeanPostProcessor {

	private final Duration expirationTimeout;

	/**
	 * Constructs a new {@link SessionExpirationTimeoutAwareBeanPostProcessor} initialized with
	 * the given {@link Session} {@link Duration expiration timeout}.
	 *
	 * @param expirationTimeout {@link Duration} specifying the length of time until {@link Session} expires.
	 * @throws IllegalArgumentException if {@link Duration} is {@literal null}.
	 * @see java.time.Duration
	 */
	public SessionExpirationTimeoutAwareBeanPostProcessor(Duration expirationTimeout) {

		Assert.notNull(expirationTimeout, "Expiration timeout is required");

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

	@Nullable @Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

		if (bean instanceof SessionExpirationTimeoutAware) {
			((SessionExpirationTimeoutAware) bean).setExpirationTimeout(getExpirationTimeout());
		}

		return bean;
	}
}
