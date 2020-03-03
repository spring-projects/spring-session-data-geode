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

import java.time.Duration;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;

/**
 * The {@link SessionExpirationTimeoutAware} interface is a configuration callback interface allowing implementors
 * to receive a callback with the configured {@link Session} {@link Duration expiration timeout} as set on the
 * {@link EnableGemFireHttpSession} annotation, {@link EnableGemFireHttpSession#maxInactiveIntervalInSeconds()}
 * attribute.
 *
 * @author John Blum
 * @see java.time.Duration
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @since 2.1.0
 */
@SuppressWarnings("unused")
public interface SessionExpirationTimeoutAware {

	/**
	 * Configures the {@link Session} {@link Duration expiration timeout} on this implementating object.
	 *
	 * @param expirationTimeout {@link Duration} specifying the expiration timeout fo the {@link Session}.
	 * @see java.time.Duration
	 */
	void setExpirationTimeout(Duration expirationTimeout);

}
