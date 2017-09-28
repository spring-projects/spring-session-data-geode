/*
 * Copyright 2017 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization.data;

import static java.util.Arrays.stream;
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Optional;

import org.apache.geode.DataSerializer;

import org.springframework.session.data.gemfire.serialization.SerializationException;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;

/**
 * The {@link AbstractDataSerializableSessionSerializer} class...
 *
 * @author John Blum
 * @see java.io.DataInput
 * @see java.io.DataOutput
 * @see org.apache.geode.DataSerializer
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @since 2.0.0
 */
public abstract class AbstractDataSerializableSessionSerializer<T> extends DataSerializer
		implements SessionSerializer<T, DataInput, DataOutput> {

	protected static final boolean DEFAULT_ALLOW_JAVA_SERIALIZATION = true;

	@Override
	public int getId() {
		return 0x0A11ACE5;
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return new Class[0];
	}

	protected boolean allowJavaSerialization() {
		return DEFAULT_ALLOW_JAVA_SERIALIZATION;
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean toData(Object session, DataOutput out) throws IOException {

		return Optional.ofNullable(session)
			.filter(this::canSerialize)
			.map(it -> {
				serialize((T) session, out);
				return true;
			})
			.orElse(false);
	}

	public void serializeObject(Object obj, DataOutput out) throws IOException {
		serializeObject(obj, out, allowJavaSerialization());
	}

	public void serializeObject(Object obj, DataOutput out, boolean allowJavaSerialization) throws IOException {
		writeObject(obj, out, allowJavaSerialization);
	}

	@Override
	public Object fromData(DataInput in) throws IOException, ClassNotFoundException {
		return deserialize(in);
	}

	public <T> T deserializeObject(DataInput in) throws ClassNotFoundException, IOException {
		return DataSerializer.readObject(in);
	}

	@Override
	@SuppressWarnings("unchecked")
	public boolean canSerialize(Class<?> type) {
		return stream(nullSafeArray(getSupportedClasses(), Class.class))
			.filter(it -> type != null)
			.anyMatch(supportedClass -> supportedClass.isAssignableFrom(type));
	}

	protected <T> T safeRead(DataInput in, DataInputReader<T> reader) {
		try {
			return reader.doRead(in);
		}
		catch (ClassNotFoundException | IOException cause) {
			throw new SerializationException(cause);
		}
	}

	protected void safeWrite(DataOutput out, DataOutputWriter writer) {
		try {
			writer.doWrite(out);
		}
		catch (IOException cause) {
			throw new SerializationException(cause);
		}
	}

	protected interface DataInputReader<T> {
		T doRead(DataInput in) throws ClassNotFoundException, IOException;
	}

	protected interface DataOutputWriter {
		void doWrite(DataOutput out) throws IOException;
	}
}
