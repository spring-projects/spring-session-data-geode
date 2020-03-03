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

package org.springframework.session.data.gemfire.serialization.pdx.provider;

import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxWriter;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.data.gemfire.serialization.pdx.AbstractPdxSerializableSessionSerializer;
import org.springframework.session.data.gemfire.support.AbstractSession;

/**
 * The {@link PdxSerializableSessionSerializer} class is an implementation of the {@link SessionSerializer} interface
 * used to serialize a Spring {@link Session} using the GemFire/Geode's PDX Serialization framework.
 *
 * @author John Blum
 * @see org.apache.geode.pdx.PdxReader
 * @see org.apache.geode.pdx.PdxWriter
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.pdx.AbstractPdxSerializableSessionSerializer
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class PdxSerializableSessionSerializer extends AbstractPdxSerializableSessionSerializer<GemFireSession> {

	@Override
	@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
	public void serialize(GemFireSession session, PdxWriter writer) {

		synchronized (session) {
			writer.writeString("id", session.getId());
			writer.writeLong("creationTime", session.getCreationTime().toEpochMilli());
			writer.writeLong("lastAccessedTime", session.getLastAccessedTime().toEpochMilli());
			writer.writeLong("maxInactiveIntervalInSeconds", session.getMaxInactiveInterval().getSeconds());
			writer.writeString("principalName", session.getPrincipalName());
			writer.writeObject("attributes", newMap(session.getAttributes()));
		}
	}

	protected <K, V> Map<K, V> newMap(Map<K, V> map) {
		return new HashMap<>(map);
	}

	@Override
	@SuppressWarnings("unchecked")
	public GemFireSession deserialize(PdxReader reader) {

		GemFireSession session = GemFireSession.from(new AbstractSession() {

			@Override
			public String getId() {
				return reader.readString("id");
			}

			@Override
			public Instant getCreationTime() {
				return Instant.ofEpochMilli(reader.readLong("creationTime"));
			}

			@Override
			public Instant getLastAccessedTime() {
				return Instant.ofEpochMilli(reader.readLong("lastAccessedTime"));
			}

			@Override
			public Duration getMaxInactiveInterval() {
				return Duration.ofSeconds(reader.readLong("maxInactiveIntervalInSeconds"));
			}

			@Override
			public Set<String> getAttributeNames() {
				return Collections.emptySet();
			}
		});

		session.setPrincipalName(reader.readString("principalName"));
		session.getAttributes().from((Map<String, Object>) reader.readObject("attributes"));

		return session;
	}

	@Override
	public boolean canSerialize(Class<?> type) {
		return Optional.ofNullable(type).map(GemFireSession.class::isAssignableFrom).orElse(false);
	}
}
