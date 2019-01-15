/*
 * Copyright 2019 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization.pdx.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxWriter;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;

/**
 * Unit tests for {@link PdxSerializerSessionSerializerAdapter}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.apache.geode.pdx.PdxReader
 * @see org.apache.geode.pdx.PdxWriter
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.serialization.pdx.support.PdxSerializerSessionSerializerAdapter
 * @since 2.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class PdxSerializerSessionSerializerAdapterTests {

	@Mock
	private PdxReader mockPdxReader;

	@Mock
	private PdxWriter mockPdxWriter;

	@Mock
	private Session mockSession;

	@Mock
	private SessionSerializer<Session, PdxReader, PdxWriter> mockSessionSerializer;

	@Test
	public void constructPdxSerializerSessionSerializerAdapterWithSessionSerializer() {

		PdxSerializerSessionSerializerAdapter<Session> sessionSerializerAdapter =
			new PdxSerializerSessionSerializerAdapter<>(this.mockSessionSerializer);

		assertThat(sessionSerializerAdapter).isNotNull();
		assertThat(sessionSerializerAdapter.getSessionSerializer()).isSameAs(this.mockSessionSerializer);
	}

	@Test(expected = IllegalArgumentException.class)
	public void constructPdxSerializerSessionSerializerAdapterWithNull() {

		try {
			new PdxSerializerSessionSerializerAdapter<>(null);
		}
		catch (IllegalArgumentException expected) {

			assertThat(expected).hasMessage("SessionSerializer is required");
			assertThat(expected).hasNoCause();

			throw expected;
		}
	}

	@Test
	public void serializeSessionCallsSessionSerializerSerialize() {

		PdxSerializerSessionSerializerAdapter<Session> sessionSerializerAdapter =
			new PdxSerializerSessionSerializerAdapter<>(this.mockSessionSerializer);

		sessionSerializerAdapter.serialize(this.mockSession, this.mockPdxWriter);

		verify(this.mockSessionSerializer, times(1))
			.serialize(eq(this.mockSession), eq(this.mockPdxWriter));
	}

	@Test
	public void deserializeSessionCallsSessionSerializerDeserialize() {

		when(this.mockSessionSerializer.deserialize(any(PdxReader.class))).thenReturn(this.mockSession);

		PdxSerializerSessionSerializerAdapter<Session> sessionSerializerAdapter =
			new PdxSerializerSessionSerializerAdapter<>(this.mockSessionSerializer);

		assertThat(sessionSerializerAdapter.deserialize(this.mockPdxReader)).isEqualTo(this.mockSession);

		verify(this.mockSessionSerializer, times(1)).deserialize(eq(this.mockPdxReader));
	}

	@Test
	public void toDataSerializesSessionWithSessionSerializer() {

		PdxSerializerSessionSerializerAdapter<Session> sessionSerializerAdapter =
			new PdxSerializerSessionSerializerAdapter<>(this.mockSessionSerializer);

		sessionSerializerAdapter.toData(this.mockSession, this.mockPdxWriter);

		verify(this.mockSessionSerializer, times(1))
			.serialize(eq(this.mockSession), eq(this.mockPdxWriter));
	}

	@Test
	public void fromDataDeserializesSessionWithSessionSerializer() {

		when(this.mockSessionSerializer.deserialize(any(PdxReader.class))).thenReturn(this.mockSession);

		PdxSerializerSessionSerializerAdapter<Session> sessionSerializerAdapter =
			new PdxSerializerSessionSerializerAdapter<>(this.mockSessionSerializer);

		assertThat(sessionSerializerAdapter.fromData(Session.class, this.mockPdxReader)).isEqualTo(this.mockSession);

		verify(this.mockSessionSerializer, times(1)).deserialize(eq(this.mockPdxReader));
	}
}
