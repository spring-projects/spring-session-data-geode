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

package org.springframework.session.data.gemfire.serialization.data.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.data.gemfire.util.CollectionUtils.asSet;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

/**
 * Unit tests for {@link DataSerializableSessionAttributesSerializer}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.Spy
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionAttributesSerializer
 * @since 2.0.0
 */
public class DataSerializableSessionAttributesSerializerTests {

	private DataSerializableSessionAttributesSerializer sessionAttributesSerializer =
		spy(new DataSerializableSessionAttributesSerializer());

	@Test
	public void getIdReturnsSameValue() {

		int id = this.sessionAttributesSerializer.getId();

		assertThat(id).isNotEqualTo(0);
		assertThat(id).isEqualTo(this.sessionAttributesSerializer.getId());
	}

	@Test
	public void supportedClassesContainsGemFireSessionAttributesAndSubTypes() {
		assertThat(this.sessionAttributesSerializer.getSupportedClasses()).contains(GemFireSessionAttributes.class);
		assertThat(this.sessionAttributesSerializer.getSupportedClasses()).contains(DeltaCapableGemFireSessionAttributes.class);
	}

	@Test
	public void sessionAttributesToData() throws Exception {

		DataOutput mockDataOutput = mock(DataOutput.class);

		GemFireSessionAttributes sessionAttributes = GemFireSessionAttributes.create();

		sessionAttributes.setAttribute("attrOne", "testOne");
		sessionAttributes.setAttribute("attrTwo", "testTwo");

		doAnswer(invocation -> {

			DataOutput dataOutput = invocation.getArgument(1);

			dataOutput.writeUTF(invocation.getArgument(0));

			return null;

		}).when(sessionAttributesSerializer).serializeObject(any(), any(DataOutput.class));

		sessionAttributesSerializer.serialize(sessionAttributes, mockDataOutput);

		verify(mockDataOutput, times(1)).writeInt(eq(2));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrOne"));
		verify(mockDataOutput, times(1)).writeUTF(eq("testOne"));
		verify(mockDataOutput, times(1)).writeUTF(eq("attrTwo"));
		verify(mockDataOutput, times(1)).writeUTF(eq("testTwo"));
	}

	@Test
	public void sessionAttributesFromData() throws Exception {

		AtomicInteger count =  new AtomicInteger(0);

		DataInput mockDataInput = mock(DataInput.class);

		given(mockDataInput.readInt()).willReturn(2);
		given(mockDataInput.readUTF()).willReturn("attrOne").willReturn("attrTwo");

		doAnswer(invocation -> Arrays.asList("testOne", "testTwo").get(count.getAndIncrement()))
			.when(sessionAttributesSerializer).deserializeObject(any(DataInput.class));

		GemFireSessionAttributes sessionAttributes = sessionAttributesSerializer.deserialize(mockDataInput);

		assertThat(sessionAttributes).isNotNull();
		assertThat(sessionAttributes.getAttributeNames()).hasSize(2);
		assertThat(sessionAttributes.getAttributeNames()).containsAll(asSet("attrOne", "attrTwo"));
		assertThat(sessionAttributes.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(sessionAttributes.<String>getAttribute("attrTwo")).isEqualTo("testTwo");

		verify(mockDataInput, times(1)).readInt();
		verify(mockDataInput, times(2)).readUTF();
	}
}
