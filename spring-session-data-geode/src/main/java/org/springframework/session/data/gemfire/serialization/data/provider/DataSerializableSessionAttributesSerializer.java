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
import static org.springframework.data.gemfire.util.CollectionUtils.nullSafeSet;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Set;

import org.apache.geode.DataSerializer;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer;

/**
 * The {@link DataSerializableSessionAttributesSerializer} class is an implementation of the {@link SessionSerializer}
 * interface used to serialize a Spring {@link Session} attributes using the GemFire/Geode's Data Serialization
 * framework.
 *
 * @author John Blum
 * @see org.apache.geode.DataSerializer
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class DataSerializableSessionAttributesSerializer
		extends AbstractDataSerializableSessionSerializer<GemFireSessionAttributes> {

	/**
	 * Register custom Spring Session {@link DataSerializer DataSerializers} with Apache Geode/Pivotal GemFire
	 * to handle de/serialization of Spring Session, {@link Session} attribute types.
	 *
	 * @see org.apache.geode.DataSerializer#register(Class)
	 */
	public static void register() {
		register(DataSerializableSessionAttributesSerializer.class);
	}

	/**
	 * Returns the identifier for this {@link DataSerializer}.
	 *
	 * @return the identifier for this {@link DataSerializer}.
	 */
	@Override
	public int getId() {
		return 0x8192ACE5;
	}

	/**
	 * Returns the {@link Class types} supported and handled by this {@link DataSerializer} during de/serialization.
	 *
	 * @return the {@link Class types} supported and handled by this {@link DataSerializer} during de/serialization.
	 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes
	 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
	 * @see java.lang.Class
	 */
	@Override
	public Class<?>[] getSupportedClasses() {
		return asArray(GemFireSessionAttributes.class, DeltaCapableGemFireSessionAttributes.class);
	}

	@Override
	public void serialize(GemFireSessionAttributes sessionAttributes, DataOutput out) {

		synchronized (sessionAttributes) {

			Set<String> attributeNames = nullSafeSet(sessionAttributes.getAttributeNames());

			safeWrite(out, output -> output.writeInt(attributeNames.size()));

			attributeNames.forEach(attributeName -> {
				safeWrite(out, output -> output.writeUTF(attributeName));
				safeWrite(out, output -> serializeObject(sessionAttributes.getAttribute(attributeName), output));
			});

			sessionAttributes.clearDelta();
		}
	}

	@Override
	public GemFireSessionAttributes deserialize(DataInput in) {

		GemFireSessionAttributes sessionAttributes = GemFireSessionAttributes.create();

		for (int count = safeRead(in, DataInput::readInt); count > 0; count--) {
			sessionAttributes.setAttribute(safeRead(in, DataInput::readUTF), safeRead(in, this::deserializeObject));
		}

		sessionAttributes.clearDelta();

		return sessionAttributes;
	}
}
