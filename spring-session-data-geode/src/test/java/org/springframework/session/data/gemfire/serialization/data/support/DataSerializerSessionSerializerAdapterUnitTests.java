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

package org.springframework.session.data.gemfire.serialization.data.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.DataInput;
import java.io.DataOutput;

import org.junit.Test;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;

/**
 * Unit tests for {@link DataSerializerSessionSerializerAdapter}.
 *
 * @author John Blum
 * @see java.io.DataInput
 * @see java.io.DataOutput
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.support.DataSerializerSessionSerializerAdapter
 * @since 2.0.0
 */
public class DataSerializerSessionSerializerAdapterUnitTests {

	@Test
	@SuppressWarnings("unchecked")
	public void setAndGetSessionSerializerReturnsExpected() {

		SessionSerializer<Session, DataInput, DataOutput> mockSessionSerializer = mock(SessionSerializer.class);

		DataSerializerSessionSerializerAdapter<Session> dataSerializer =
			new DataSerializerSessionSerializerAdapter<>();

		dataSerializer.setSessionSerializer(mockSessionSerializer);

		assertThat(dataSerializer.getSessionSerializer()).isSameAs(mockSessionSerializer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void setSessionSerializerToNullThrowsIllegalArgumentException() {

		try {
			new DataSerializerSessionSerializerAdapter<>().setSessionSerializer(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("SessionSerializer is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@SuppressWarnings("unchecked")
	@Test(expected = IllegalStateException.class)
	public void getUninitializedSessionSerializerThrowsIllegalStateException() {

		try {
			new DataSerializerSessionSerializerAdapter<>().getSessionSerializer();
		}
		catch (IllegalStateException expected) {

			assertThat(expected).hasMessage("SessionSerializer was not properly configured");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	@SuppressWarnings("unchecked")
	public void serializeDelegatesToSessionSerializerSerialize() {

		DataOutput mockDataOutput = mock(DataOutput.class);

		Session mockSession = mock(Session.class);

		SessionSerializer<Session, DataInput, DataOutput> mockSessionSerializer = mock(SessionSerializer.class);

		DataSerializerSessionSerializerAdapter<Session> dataSerializer = new DataSerializerSessionSerializerAdapter<>();

		dataSerializer.setSessionSerializer(mockSessionSerializer);

		assertThat(dataSerializer.getSessionSerializer()).isSameAs(mockSessionSerializer);

		dataSerializer.serialize(mockSession, mockDataOutput);

		verify(mockSessionSerializer, times(1)).serialize(eq(mockSession), eq(mockDataOutput));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void deserializeDelegatesToSessionSerializerDeserialize() {

		DataInput mockDataInput = mock(DataInput.class);

		Session mockSession = mock(Session.class);

		SessionSerializer<Session, DataInput, DataOutput> mockSessionSerializer = mock(SessionSerializer.class);

		when(mockSessionSerializer.deserialize(any(DataInput.class))).thenReturn(mockSession);

		DataSerializerSessionSerializerAdapter<Session> dataSerializer = new DataSerializerSessionSerializerAdapter<>();

		dataSerializer.setSessionSerializer(mockSessionSerializer);

		assertThat(dataSerializer.getSessionSerializer()).isSameAs(mockSessionSerializer);
		assertThat(dataSerializer.deserialize(mockDataInput)).isEqualTo(mockSession);

		verify(mockSessionSerializer, times(1)).deserialize(eq(mockDataInput));
	}
}
