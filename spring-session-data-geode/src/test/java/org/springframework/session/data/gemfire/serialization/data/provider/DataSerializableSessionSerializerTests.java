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

package org.springframework.session.data.gemfire.serialization.data.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.data.gemfire.util.CollectionUtils.asSet;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import static org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import org.springframework.session.FindByIndexNameSessionRepository;

/**
 * Unit tests for {@link DataSerializableSessionSerializer}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.mockito.Spy
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession
 * @see org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes
 * @see org.springframework.session.data.gemfire.serialization.data.provider.DataSerializableSessionSerializer
 * @since 2.0.0
 */
public class DataSerializableSessionSerializerTests {

	private DataSerializableSessionSerializer sessionSerializer = spy(new DataSerializableSessionSerializer());

	@Test
	public void getIdReturnsSameValue() {

		int id = this.sessionSerializer.getId();

		assertThat(id).isNotEqualTo(0);
		assertThat(id).isEqualTo((this.sessionSerializer.getId()));
	}

	@Test
	public void supportedClassContainsGemFireSessionAndSubTypes() {

		assertThat(this.sessionSerializer.getSupportedClasses()).contains(GemFireSession.class);
		assertThat(this.sessionSerializer.getSupportedClasses()).contains(DeltaCapableGemFireSession.class);
	}

	@Test
	public void sessionToData() throws Exception {

		GemFireSession<?> session = GemFireSession.create();

		session.setLastAccessedTime(Instant.ofEpochMilli(123L));
		session.setMaxInactiveInterval(Duration.ofSeconds(60L));
		session.setPrincipalName("jblum");

		DataOutput mockDataOutput = mock(DataOutput.class);

		doAnswer(invocation -> {
			assertThat(invocation.<GemFireSessionAttributes>getArgument(0)).isEqualTo(session.getAttributes());
			assertThat(invocation.<DataOutput>getArgument(1)).isEqualTo(mockDataOutput);
			return null;
		}).when(this.sessionSerializer).serializeObject(any(), any(DataOutput.class));

		assertThat(session.hasDelta()).isTrue();

		this.sessionSerializer.serialize(session, mockDataOutput);

		assertThat(session.hasDelta()).isTrue();

		verify(mockDataOutput, times(1)).writeUTF(eq(session.getId()));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getCreationTime().toEpochMilli()));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getLastAccessedTime().toEpochMilli()));
		verify(mockDataOutput, times(1)).writeLong(eq(session.getMaxInactiveInterval().getSeconds()));
		verify(mockDataOutput, times(1)).writeInt(eq("jblum".length()));
		verify(mockDataOutput, times(1)).writeUTF(eq(session.getPrincipalName()));
		verify(this.sessionSerializer, times(1))
			.serializeObject(eq(session.getAttributes()), eq(mockDataOutput));
	}

	@Test
	public void sessionFromData() throws Exception {

		long expectedCreationTime = 1L;
		long expectedLastAccessedTime = 2L;
		long expectedMaxInactiveIntervalInSeconds = TimeUnit.HOURS.toSeconds(2);

		String expectedPrincipalName = "jblum";
		String expectedSessionId = "2";

		DataInput mockDataInput = mock(DataInput.class);

		given(mockDataInput.readUTF()).willReturn(expectedSessionId).willReturn(expectedPrincipalName);
		given(mockDataInput.readLong()).willReturn(expectedCreationTime).willReturn(expectedLastAccessedTime)
			.willReturn(expectedMaxInactiveIntervalInSeconds);
		given(mockDataInput.readInt()).willReturn(expectedPrincipalName.length());

		doAnswer(invocation -> {

			GemFireSessionAttributes sessionAttributes = GemFireSessionAttributes.create();

			sessionAttributes.setAttribute("attrOne", "testOne");
			sessionAttributes.setAttribute("attrTwo", "testTwo");

			return sessionAttributes;

		}).when(this.sessionSerializer).deserializeObject(any(DataInput.class));

		GemFireSession<?> session = this.sessionSerializer.deserialize(mockDataInput);

		Set<String> expectedAttributeNames =
			asSet("attrOne", "attrTwo", FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME);

		assertThat(session.getId()).isEqualTo(expectedSessionId);
		assertThat(session.getCreationTime()).isEqualTo(Instant.ofEpochMilli(expectedCreationTime));
		assertThat(session.getLastAccessedTime()).isEqualTo(Instant.ofEpochMilli(expectedLastAccessedTime));
		assertThat(session.getMaxInactiveInterval()).isEqualTo(Duration.ofSeconds(expectedMaxInactiveIntervalInSeconds));
		assertThat(session.getPrincipalName()).isEqualTo(expectedPrincipalName);
		assertThat(session.hasDelta()).isTrue();
		assertThat(session.getAttributeNames()).hasSize(3);
		assertThat(session.getAttributeNames()).containsAll(expectedAttributeNames);
		assertThat(session.<String>getAttribute("attrOne")).isEqualTo("testOne");
		assertThat(session.<String>getAttribute("attrTwo")).isEqualTo("testTwo");
		assertThat(session.<String>getAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME))
			.isEqualTo(expectedPrincipalName);

		verify(mockDataInput, times(2)).readUTF();
		verify(mockDataInput, times(3)).readLong();
		verify(mockDataInput, times(1)).readInt();
	}

	@Test
	public void sessionToDataThenFromDataWhenPrincipalNameIsNullGetsHandledProperly()
			throws ClassNotFoundException, IOException {

		Instant beforeOrAtCreationTime = Instant.now();

		GemFireSession<?> expectedSession = GemFireSession.create();

		assertThat(expectedSession.getId()).isNotNull();
		assertThat(expectedSession.getCreationTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(expectedSession.getLastAccessedTime().compareTo(beforeOrAtCreationTime)).isGreaterThanOrEqualTo(0);
		assertThat(expectedSession.getMaxInactiveInterval()).isEqualTo(Duration.ZERO);
		assertThat(expectedSession.getPrincipalName()).isNull();

		ByteArrayOutputStream outBytes = new ByteArrayOutputStream();

		doAnswer(invocation -> null).when(this.sessionSerializer).serializeObject(any(), any(DataOutput.class));

		doAnswer(invocation -> GemFireSessionAttributes.create())
			.when(this.sessionSerializer).deserializeObject(any(DataInput.class));

		this.sessionSerializer.serialize(expectedSession, new DataOutputStream(outBytes));

		GemFireSession<?> deserializedSession =
			this.sessionSerializer.deserialize(new DataInputStream(new ByteArrayInputStream(outBytes.toByteArray())));

		assertThat(deserializedSession).isEqualTo(expectedSession);
		assertThat(deserializedSession.getCreationTime()).isEqualTo(expectedSession.getCreationTime());
		assertThat(deserializedSession.getLastAccessedTime()).isEqualTo(expectedSession.getLastAccessedTime());
		assertThat(deserializedSession.getMaxInactiveInterval()).isEqualTo(expectedSession.getMaxInactiveInterval());
		assertThat(deserializedSession.getPrincipalName()).isNull();

		verify(this.sessionSerializer, times(1))
			.serializeObject(eq(expectedSession.getAttributes()), isA(DataOutput.class));

		verify(this.sessionSerializer, times(1)).deserializeObject(isA(DataInput.class));
	}
}
