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

package org.springframework.session.data.gemfire.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/**
 * Unit tests for the {@link SessionSerializer} interface.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mock
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @since 2.0.0
 */
@RunWith(MockitoJUnitRunner.class)
public class SessionSerializerTests {

	@Mock
	private SessionSerializer sessionSerializer;

	@Test
	@SuppressWarnings("unchecked")
	public void canSerializeWithSerializableObjectReturnsTrue() {

		when(this.sessionSerializer.canSerialize(any(Class.class))).thenReturn(true);
		when(this.sessionSerializer.canSerialize(any(Object.class))).thenCallRealMethod();

		assertThat(this.sessionSerializer.canSerialize("test")).isTrue();

		verify(this.sessionSerializer, times(1)).canSerialize(eq("test"));
		verify(this.sessionSerializer, times(1)).canSerialize(eq(String.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void canSerializeWithNullReturnFalse() {

		assertThat(this.sessionSerializer.canSerialize((Object) null)).isFalse();

		verify(this.sessionSerializer, times(1)).canSerialize(eq((Object) null));
		verify(this.sessionSerializer, never()).canSerialize(any(Class.class));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void canSerializeWithNonSerializableObjectReturnFalse() {

		when(this.sessionSerializer.canSerialize(any(Class.class))).thenReturn(false);
		when(this.sessionSerializer.canSerialize(any(Object.class))).thenCallRealMethod();

		assertThat(this.sessionSerializer.canSerialize("test")).isFalse();

		verify(this.sessionSerializer, times(1)).canSerialize(eq("test"));
		verify(this.sessionSerializer, times(1)).canSerialize(eq(String.class));
	}
}
