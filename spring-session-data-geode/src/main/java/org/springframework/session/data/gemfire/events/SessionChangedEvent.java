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
package org.springframework.session.data.gemfire.events;

import org.springframework.context.ApplicationEvent;
import org.springframework.session.Session;

/**
 * {@link SessionChangedEvent} is a Spring {@link ApplicationEvent} fire when the {@link Session} state changes.
 *
 * @author John Blum
 * @see org.springframework.context.ApplicationEvent
 * @see org.springframework.session.Session
 * @since 2.2.0
 */
public class SessionChangedEvent extends ApplicationEvent {

	private final Session session;

	/**
	 * Constructs a new instance of {@link SessionChangedEvent} initialized with the given {@link Object source}
	 * and {@link Session}.
	 *
	 * @param source {@link Object} referencing the source of the event.
	 * @param session {@link Session} that changed.
	 * @see org.springframework.session.Session
	 */
	public SessionChangedEvent(Object source, Session session) {

		super(source);

		this.session = session;
	}

	/**
	 * Gets the {@link Session} that was changed.
	 *
	 * @param <S> {@link Class type} of {@link Session}.
	 * @return a reference to the {@link Session} that is the subject of the change event.
	 * @see org.springframework.session.Session
	 */
	@SuppressWarnings("unchecked")
	public <S extends Session> S getSession() {
		return (S) this.session;
	}
}
