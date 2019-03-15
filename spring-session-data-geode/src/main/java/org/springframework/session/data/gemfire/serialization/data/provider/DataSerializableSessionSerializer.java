/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization.data.provider;

import static org.springframework.data.gemfire.util.ArrayUtils.asArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

import org.apache.geode.DataSerializer;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer;
import org.springframework.session.data.gemfire.support.AbstractSession;
import org.springframework.util.StringUtils;

/**
 * The {@link DataSerializableSessionSerializer} class is an implementation of the {@link SessionSerializer} interface
 * used to serialize a Spring {@link Session} using the GemFire/Geode's Data Serialization framework.
 *
 * @author John Blum
 * @see java.io.DataInput
 * @see java.io.DataOutput
 * @see org.apache.geode.DataSerializer
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer
 * @see org.springframework.session.data.gemfire.support.AbstractSession
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class DataSerializableSessionSerializer extends AbstractDataSerializableSessionSerializer<GemFireSession> {

	/**
	 * Register custom Spring Session {@link DataSerializer DataSerializers} with Apache Geode/Pivotal GemFire
	 * to handle de/serialization of Spring Session, {@link Session} and {@link Session} attribute types.
	 *
	 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionAttributesSerializer#register()
	 * @see org.apache.geode.DataSerializer#register(Class)
	 */
	public static void register() {
		register(DataSerializableSessionSerializer.class);
		DataSerializableSessionAttributesSerializer.register();
	}

	/**
	 * Returns the identifier for this {@link DataSerializer}.
	 *
	 * @return the identifier for this {@link DataSerializer}.
	 */
	@Override
	public int getId() {
		return 0x4096ACE5;
	}

	/**
	 * Returns the {@link Class types} supported and handled by this {@link DataSerializer} during de/serialization.
	 *
	 * @return the {@link Class types} supported and handled by this {@link DataSerializer} during de/serialization.
	 * @see DeltaCapableGemFireSession
	 * @see GemFireSession
	 * @see java.lang.Class
	 */
	@Override
	public Class<?>[] getSupportedClasses() {
		return asArray(GemFireSession.class, DeltaCapableGemFireSession.class);
	}

	@Override
	public void serialize(GemFireSession session, DataOutput out) {

		synchronized (session) {

			safeWrite(out, output -> output.writeUTF(session.getId()));
			safeWrite(out, output -> output.writeLong(session.getCreationTime().toEpochMilli()));
			safeWrite(out, output -> output.writeLong(session.getLastAccessedTime().toEpochMilli()));
			safeWrite(out, output -> output.writeLong(session.getMaxInactiveInterval().getSeconds()));

			String principalName = session.getPrincipalName();

			int principalNameLength = StringUtils.hasText(principalName) ? principalName.length() : 0;

			safeWrite(out, output -> output.writeInt(principalNameLength));

			if (principalNameLength > 0) {
				safeWrite(out, output -> output.writeUTF(principalName));
			}

			safeWrite(out, output -> serializeObject(session.getAttributes(), output));
		}
	}

	@Override
	public GemFireSession deserialize(DataInput in) {

		GemFireSession session = GemFireSession.from(new AbstractSession() {

			@Override
			public String getId() {
				return safeRead(in, DataInput::readUTF);
			}

			@Override
			public Instant getCreationTime() {
				return safeRead(in, in -> Instant.ofEpochMilli(in.readLong()));
			}

			@Override
			public Instant getLastAccessedTime() {
				return safeRead(in, in -> Instant.ofEpochMilli(in.readLong()));
			}

			@Override
			public Duration getMaxInactiveInterval() {
				return safeRead(in, in -> Duration.ofSeconds(in.readLong()));
			}

			@Override
			public Set<String> getAttributeNames() {
				return Collections.emptySet();
			}
		});

		int principalNameLength = safeRead(in, DataInput::readInt);

		if (principalNameLength > 0) {
			session.setPrincipalName(safeRead(in, DataInput::readUTF));
		}

		session.getAttributes().from(this.<GemFireSessionAttributes>safeRead(in, this::deserializeObject));

		return session;
	}
}
