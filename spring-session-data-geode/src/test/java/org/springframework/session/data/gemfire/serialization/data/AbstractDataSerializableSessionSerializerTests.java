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

package org.springframework.session.data.gemfire.serialization.data;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.data.gemfire.util.ArrayUtils.asArray;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.serialization.SerializationException;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer.DataInputReader;
import org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer.DataOutputWriter;

/**
 * Unit tests for {@link AbstractDataSerializableSessionSerializer}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.Spy
 * @see org.springframework.session.data.gemfire.serialization.data.AbstractDataSerializableSessionSerializer
 * @since 2.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractDataSerializableSessionSerializerTests {

	@Spy
	private AbstractDataSerializableSessionSerializer<Session> sessionSerializer;

	@Mock
	private DataInput mockDataInput;

	@Mock
	private DataOutput mockDataOuput;

	@Mock
	private Session mockSession;

	@Test
	public void constructAbstractDataSerializableSessionSerializer() {

		assertThat(this.sessionSerializer).isNotNull();
		assertThat(this.sessionSerializer.getId()).isEqualTo(0xA11ACE5);
		assertThat(this.sessionSerializer.getSupportedClasses()).isEmpty();
		assertThat(this.sessionSerializer.allowJavaSerialization()).isTrue();
	}

	@Test
	public void toDataCallsSerializeForSerializableObjectAndReturnsTrue() throws IOException {

		when(this.sessionSerializer.canSerialize(any(Session.class))).thenReturn(true);

		assertThat(this.sessionSerializer.toData(this.mockSession, this.mockDataOuput)).isTrue();

		verify(this.sessionSerializer, times(1)).canSerialize(eq(this.mockSession));
		verify(this.sessionSerializer, times(1))
			.serialize(eq(this.mockSession), eq(this.mockDataOuput));
	}

	@Test
	public void toDataWithNonSerializableObjectReturnsFalse() throws IOException {

		when(this.sessionSerializer.canSerialize(any())).thenReturn(false);

		assertThat(this.sessionSerializer.toData("test", this.mockDataOuput)).isFalse();

		verify(this.sessionSerializer, times(1)).canSerialize(eq("test"));
		verify(this.sessionSerializer, never()).serialize(any(), any(DataOutput.class));
	}

	@Test
	public void toDataWithNullReturnsFalse() throws IOException {

		assertThat(this.sessionSerializer.toData(null, this.mockDataOuput)).isFalse();

		verify(this.sessionSerializer, never()).canSerialize(any());
		verify(this.sessionSerializer, never()).serialize(any(), any(DataOutput.class));
	}

	@Test
	public void serializeObjectDefaultsAllowJavaSerializationToTrue() throws IOException {

		this.sessionSerializer.serializeObject("test", this.mockDataOuput);

		verify(this.sessionSerializer, times(1))
			.serializeObject(eq("test"), eq(this.mockDataOuput), eq(true));
	}

	@Test
	public void serializeObjectWithAllowJavaSerializationSetToFalse() throws IOException {
		this.sessionSerializer.serializeObject("test", this.mockDataOuput, false);
	}

	@Test
	public void fromDataCallsDeserialize() throws IOException, ClassNotFoundException {

		when(this.sessionSerializer.deserialize(any(DataInput.class))).thenReturn(this.mockSession);

		assertThat(this.sessionSerializer.fromData(this.mockDataInput)).isEqualTo(this.mockSession);

		verify(this.sessionSerializer, times(1)).deserialize(eq(this.mockDataInput));
	}

	@Test
	public void canSerializeSerializableType() {

		when(this.sessionSerializer.getSupportedClasses()).thenReturn(asArray(Session.class));

		assertThat(this.sessionSerializer.canSerialize(this.mockSession)).isTrue();

		verify(this.sessionSerializer, times(1)).getSupportedClasses();
	}

	@Test
	public void canSerializeSerializableSubType() {

		when(this.sessionSerializer.getSupportedClasses()).thenReturn(asArray(Number.class, Integer.class, Long.class));

		assertThat(this.sessionSerializer.canSerialize((short) 64)).isTrue();

		verify(this.sessionSerializer, times(1)).getSupportedClasses();
	}

	@Test
	public void cannotSerializeNonSerializableType() {

		when(this.sessionSerializer.getSupportedClasses()).thenReturn(asArray(Session.class));

		assertThat(this.sessionSerializer.canSerialize("sessionId")).isFalse();

		verify(this.sessionSerializer, times(1)).getSupportedClasses();
	}

	@Test
	public void cannotSerializeNull() {

		when(this.sessionSerializer.getSupportedClasses()).thenReturn(asArray(Object.class));

		assertThat(this.sessionSerializer.canSerialize(null)).isFalse();

		verify(this.sessionSerializer, times(1)).getSupportedClasses();
	}

	@Test
	public void cannotSerializeWhenSupportClassesAreEmpty() {

		when(this.sessionSerializer.getSupportedClasses()).thenReturn(asArray());

		assertThat(this.sessionSerializer.canSerialize("test")).isFalse();

		verify(this.sessionSerializer, times(1)).getSupportedClasses();
	}

	@Test
	public void cannotSerializeWhenSupportClassesAreNull() {

		when(this.sessionSerializer.getSupportedClasses()).thenReturn(null);

		assertThat(this.sessionSerializer.canSerialize("test")).isFalse();

		verify(this.sessionSerializer, times(1)).getSupportedClasses();
	}

	@Test
	@SuppressWarnings("unchecked")
	public void safeReadReturnsValue() throws IOException, ClassNotFoundException {

		DataInputReader<String> mockDataInputReader = mock(DataInputReader.class);

		when(mockDataInputReader.doRead(any(DataInput.class))).thenReturn("test");

		assertThat(this.sessionSerializer.safeRead(this.mockDataInput, mockDataInputReader)).isEqualTo("test");

		verify(mockDataInputReader, times(1)).doRead(eq(this.mockDataInput));
	}

	@Test(expected = SerializationException.class)
	@SuppressWarnings("unchecked")
	public void safeReadHandlesClassCastException() throws IOException, ClassNotFoundException {

		DataInputReader<String> mockDataInputReader = mock(DataInputReader.class);

		when(mockDataInputReader.doRead(any(DataInput.class))).thenThrow(new ClassNotFoundException("test"));

		try {
			this.sessionSerializer.safeRead(this.mockDataInput, mockDataInputReader);
		}
		catch (Exception expected) {

			assertThat(expected).isInstanceOf(SerializationException.class);
			assertThat(expected).hasCauseInstanceOf(ClassNotFoundException.class);
			assertThat(expected.getCause()).hasMessage("test");
			assertThat(expected.getCause()).hasNoCause();

			throw expected;
		}
		finally {
			verify(mockDataInputReader, times(1)).doRead(eq(this.mockDataInput));
		}
	}

	@Test(expected = SerializationException.class)
	@SuppressWarnings("unchecked")
	public void safeReadHandlesIOException() throws IOException, ClassNotFoundException {

		DataInputReader<String> mockDataInputReader = mock(DataInputReader.class);

		when(mockDataInputReader.doRead(any(DataInput.class))).thenAnswer(invocation ->
			invocation.<DataInput>getArgument(0).readUTF());

		when(this.mockDataInput.readUTF()).thenThrow(new IOException("test"));

		try {
			this.sessionSerializer.safeRead(this.mockDataInput, mockDataInputReader);
		}
		catch (Exception expected) {

			assertThat(expected).isInstanceOf(SerializationException.class);
			assertThat(expected).hasCauseInstanceOf(IOException.class);
			assertThat(expected.getCause()).hasMessage("test");
			assertThat(expected.getCause()).hasNoCause();

			throw expected;
		}
		finally {
			verify(mockDataInputReader, times(1)).doRead(eq(this.mockDataInput));
			verify(this.mockDataInput, times(1)).readUTF();
		}
	}

	@Test
	public void safeWriteWritesValue() throws IOException {

		DataOutputWriter mockDataOutputWriter = mock(DataOutputWriter.class);

		this.sessionSerializer.safeWrite(this.mockDataOuput, mockDataOutputWriter);

		verify(mockDataOutputWriter, times(1)).doWrite(eq(this.mockDataOuput));
	}

	@Test(expected = SerializationException.class)
	public void safeWriteHandlesIOException() throws IOException {

		DataOutputWriter mockDataOutputWriter = mock(DataOutputWriter.class);

		doThrow(new IOException("test")).when(mockDataOutputWriter).doWrite(any(DataOutput.class));

		try {
			this.sessionSerializer.safeWrite(this.mockDataOuput, mockDataOutputWriter);
		}
		catch (Exception expected ) {

			assertThat(expected).isInstanceOf(SerializationException.class);
			assertThat(expected).hasCauseInstanceOf(IOException.class);
			assertThat(expected.getCause()).hasMessage("test");
			assertThat(expected.getCause()).hasNoCause();

			throw expected;
		}
		finally {
			verify(mockDataOutputWriter, times(1)).doWrite(eq(this.mockDataOuput));
		}
	}
}
