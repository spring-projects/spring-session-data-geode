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

package org.springframework.session.data.gemfire.serialization.data.provider;

import static org.springframework.data.gemfire.util.ArrayUtils.asArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Set;

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
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class DataSerializableSessionSerializer extends AbstractDataSerializableSessionSerializer<GemFireSession> {

	public static void register() {
		register(DataSerializableSessionSerializer.class);
		DataSerializableSessionAttributesSerializer.register();
	}

	@Override
	public int getId() {
		return 0x4096ACE5;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return asArray(GemFireSession.class, DeltaCapableGemFireSession.class);
	}

	@Override
	//@SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
	public void serialize(GemFireSession session, DataOutput out) {

		synchronized (session) {

			safeWrite(out, output -> output.writeUTF(session.getId()));
			safeWrite(out, output -> output.writeLong(session.getCreationTime().toEpochMilli()));
			safeWrite(out, output -> output.writeLong(session.getLastAccessedTime().toEpochMilli()));
			safeWrite(out, output -> output.writeLong(session.getMaxInactiveInterval().getSeconds()));

			String principalName = session.getPrincipalName();

			int length = (StringUtils.hasText(principalName) ? principalName.length() : 0);

			safeWrite(out, output -> output.writeInt(length));

			if (length > 0) {
				safeWrite(out, output -> output.writeUTF(principalName));
			}

			safeWrite(out, output -> serializeObject(session.getAttributes(), out));

			session.clearDelta();
			session.getAttributes().clearDelta();
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
		session.getAttributes().clearDelta();
		session.clearDelta();

		return session;
	}
}
