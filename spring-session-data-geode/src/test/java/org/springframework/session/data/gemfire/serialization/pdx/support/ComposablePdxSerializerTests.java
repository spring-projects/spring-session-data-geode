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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxSerializer;
import org.apache.geode.pdx.PdxWriter;

/**
 * Unit tests for {@link ComposablePdxSerializer}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.apache.geode.pdx.PdxReader
 * @see org.apache.geode.pdx.PdxSerializer
 * @see org.apache.geode.pdx.PdxWriter
 * @see org.springframework.session.data.gemfire.serialization.pdx.support.ComposablePdxSerializer
 * @since 2.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class ComposablePdxSerializerTests {

	@Mock
	private PdxReader mockPdxReader;

	@Mock
	private PdxWriter mockPdxWriter;

	@Mock
	private PdxSerializer mockPdxSerializerOne;

	@Mock
	private PdxSerializer mockPdxSerializerTwo;

	@Test
	public void composeArrayWithTwoPdxSerializers() {

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isInstanceOf(ComposablePdxSerializer.class);
		assertThat((ComposablePdxSerializer) pdxSerializer)
			.contains(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);
	}

	@Test
	public void composeArrayWithOnePdxSerializer() {
		assertThat(ComposablePdxSerializer.compose(this.mockPdxSerializerOne)).isSameAs(this.mockPdxSerializerOne);
	}

	@Test
	public void composeArrayWithNoPdxSerializers() {
		assertThat(ComposablePdxSerializer.compose()).isNull();
	}

	@Test
	public void composeIterableWithTwoPdxSerializers() {

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(Arrays.asList(this.mockPdxSerializerOne, this.mockPdxSerializerTwo));

		assertThat(pdxSerializer).isInstanceOf(ComposablePdxSerializer.class);
		assertThat((ComposablePdxSerializer) pdxSerializer)
			.contains(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);
	}

	@Test
	public void composeIterableWithOnePdxSerializer() {
		assertThat(ComposablePdxSerializer.compose(Collections.singleton(this.mockPdxSerializerTwo)))
			.isSameAs(this.mockPdxSerializerTwo);
	}

	@Test
	public void composeIterableWithNoPdxSerializers() {
		assertThat(ComposablePdxSerializer.compose(Collections.emptyList())).isNull();
	}

	@Test
	public void toDataSerializesObjectWithFirstPdxSerializer() {

		when(this.mockPdxSerializerOne.toData(any(), any(PdxWriter.class))).thenReturn(true);

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isNotNull();
		assertThat(pdxSerializer.toData("test", this.mockPdxWriter)).isTrue();

		verify(this.mockPdxSerializerOne, times(1))
			.toData(eq("test"), eq(this.mockPdxWriter));

		verify(this.mockPdxSerializerTwo, never()).toData(any(), any(PdxWriter.class));
	}

	@Test
	public void toDataSerializesObjectWithSecondPdxSerializer() {

		when(this.mockPdxSerializerOne.toData(any(), any(PdxWriter.class))).thenReturn(false);
		when(this.mockPdxSerializerTwo.toData(any(), any(PdxWriter.class))).thenReturn(true);

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isNotNull();
		assertThat(pdxSerializer.toData("test", this.mockPdxWriter)).isTrue();

		verify(this.mockPdxSerializerOne, times(1))
			.toData(eq("test"), eq(this.mockPdxWriter));

		verify(this.mockPdxSerializerTwo, times(1))
			.toData(eq("test"), eq(this.mockPdxWriter));
	}

	@Test
	public void toDataCannotSerializeObject() {

		when(this.mockPdxSerializerOne.toData(any(), any(PdxWriter.class))).thenReturn(false);
		when(this.mockPdxSerializerTwo.toData(any(), any(PdxWriter.class))).thenReturn(false);

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isNotNull();
		assertThat(pdxSerializer.toData("test", this.mockPdxWriter)).isFalse();

		verify(this.mockPdxSerializerOne, times(1))
			.toData(eq("test"), eq(this.mockPdxWriter));

		verify(this.mockPdxSerializerTwo, times(1))
			.toData(eq("test"), eq(this.mockPdxWriter));
	}

	@Test
	public void fromDataDeserializesObjectWithFirstPdxSerializer() {

		when(this.mockPdxSerializerOne.fromData(any(Class.class), any(PdxReader.class))).thenReturn("test");

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isNotNull();
		assertThat(pdxSerializer.fromData(Object.class, this.mockPdxReader)).isEqualTo("test");

		verify(this.mockPdxSerializerOne, times(1))
			.fromData(eq(Object.class), eq(this.mockPdxReader));

		verify(this.mockPdxSerializerTwo, never()).fromData(any(Class.class), any(PdxReader.class));
	}

	@Test
	public void fromDataDeserializesObjectWithSecondPdxSerializer() {

		when(this.mockPdxSerializerOne.fromData(any(Class.class), any(PdxReader.class))).thenReturn(null);
		when(this.mockPdxSerializerTwo.fromData(any(Class.class), any(PdxReader.class))).thenReturn("two");

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isNotNull();
		assertThat(pdxSerializer.fromData(Object.class, this.mockPdxReader)).isEqualTo("two");

		verify(this.mockPdxSerializerOne, times(1))
			.fromData(eq(Object.class), eq(this.mockPdxReader));

		verify(this.mockPdxSerializerTwo, times(1))
			.fromData(eq(Object.class), eq(this.mockPdxReader));
	}

	@Test
	public void fromDataCannotDeserializeObject() {

		when(this.mockPdxSerializerOne.fromData(any(Class.class), any(PdxReader.class))).thenReturn(null);
		when(this.mockPdxSerializerTwo.fromData(any(Class.class), any(PdxReader.class))).thenReturn(null);

		PdxSerializer pdxSerializer =
			ComposablePdxSerializer.compose(this.mockPdxSerializerOne, this.mockPdxSerializerTwo);

		assertThat(pdxSerializer).isNotNull();
		assertThat(pdxSerializer.fromData(Object.class, this.mockPdxReader)).isNull();

		verify(this.mockPdxSerializerOne, times(1))
			.fromData(eq(Object.class), eq(this.mockPdxReader));

		verify(this.mockPdxSerializerTwo, times(1))
			.fromData(eq(Object.class), eq(this.mockPdxReader));
	}
}
