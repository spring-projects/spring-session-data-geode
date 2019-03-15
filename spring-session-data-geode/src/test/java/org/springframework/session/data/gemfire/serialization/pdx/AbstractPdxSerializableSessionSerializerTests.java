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

package org.springframework.session.data.gemfire.serialization.pdx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxWriter;

import org.springframework.session.Session;

/**
 * Unit tests for {@link AbstractPdxSerializableSessionSerializer}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.Spy
 * @see org.apache.geode.pdx.PdxReader
 * @see org.apache.geode.pdx.PdxWriter
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.serialization.pdx.AbstractPdxSerializableSessionSerializer
 * @since 2.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class AbstractPdxSerializableSessionSerializerTests {

	@Spy
	private AbstractPdxSerializableSessionSerializer<Session> sessionSerializer;

	@Mock
	private PdxReader mockPdxReader;

	@Mock
	private PdxWriter mockPdxWriter;

	@Mock
	private Session mockSession;

	@Test
	public void toDataSerializesSessionAndReturnsTrue() {

		when(this.sessionSerializer.canSerialize(any(Object.class))).thenReturn(true);

		assertThat(this.sessionSerializer.toData(this.mockSession, this.mockPdxWriter)).isTrue();

		verify(this.sessionSerializer, times(1))
			.serialize(eq(this.mockSession), eq(this.mockPdxWriter));
	}

	@Test
	public void toDataWithNonSerializableObjectReturnsFalse() {

		when(this.sessionSerializer.canSerialize(any(Object.class))).thenReturn(false);

		assertThat(this.sessionSerializer.toData("test", this.mockPdxWriter)).isFalse();

		verify(this.sessionSerializer, never()).serialize(any(), any(PdxWriter.class));
	}

	@Test
	public void toDataWithNullReturnsFalse() {

		assertThat(this.sessionSerializer.toData(null, this.mockPdxWriter)).isFalse();

		verify(this.sessionSerializer, never()).serialize(any(), any(PdxWriter.class));
	}

	@Test
	public void fromDataWithSessionTypeReturnsSessionObject() {

		when(this.sessionSerializer.deserialize(eq(this.mockPdxReader))).thenReturn(this.mockSession);

		assertThat(this.sessionSerializer.fromData(Session.class, this.mockPdxReader)).isEqualTo(this.mockSession);

		verify(this.sessionSerializer, times(1)).canSerialize(eq(Session.class));
		verify(this.sessionSerializer, times(1)).deserialize(eq(this.mockPdxReader));
	}

	@Test
	public void fromDataWithNonSerializableTypeReturnsNull() {

		when(this.sessionSerializer.canSerialize(any(Class.class))).thenReturn(false);

		assertThat(this.sessionSerializer.fromData(Object.class, this.mockPdxReader)).isNull();

		verify(this.sessionSerializer, times(1)).canSerialize(eq(Object.class));
		verify(this.sessionSerializer, never()).deserialize(any(PdxReader.class));
	}

	@Test
	public void fromDataWithNullReturnsNull() {

		assertThat(this.sessionSerializer.fromData(null, this.mockPdxReader)).isNull();

		verify(this.sessionSerializer, never()).canSerialize(any());
		verify(this.sessionSerializer, never()).deserialize(any(PdxReader.class));
	}

	@Test
	public void canSerializeSessionIsTrue() {
		assertThat(this.sessionSerializer.canSerialize(Session.class)).isTrue();
	}

	@Test
	public void canSerializeGemFireSessionIsTrue() {
		assertThat(this.sessionSerializer.canSerialize(GemFireSession.class)).isTrue();
	}

	@Test
	public void canSerializeObjectIsFalse() {
		assertThat(this.sessionSerializer.canSerialize(Object.class)).isFalse();
	}

	@Test
	public void canSerializeNullIsFalse() {
		assertThat(this.sessionSerializer.canSerialize(null)).isFalse();
	}
}
