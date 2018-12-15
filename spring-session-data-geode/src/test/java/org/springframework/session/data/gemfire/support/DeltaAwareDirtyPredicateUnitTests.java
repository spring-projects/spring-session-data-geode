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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

import org.apache.geode.Delta;

/**
 * Unit Tests for {@link DeltaAwareDirtyPredicate}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.data.gemfire.support.DeltaAwareDirtyPredicate
 * @since 2.1.2
 */
public class DeltaAwareDirtyPredicateUnitTests {

	@Test
	public void isDirtyIsNullSafe() {

		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty(null, null)).isTrue();
		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty("one", null)).isTrue();
		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty(null, "one")).isTrue();
	}

	@Test
	public void isDirtyWithNonDeltaObjectReturnsTrue() {

		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty("one", "one")).isTrue();
		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty("one", "two")).isTrue();
	}

	@Test
	public void isDirtyWithDeltaObjectReturnsTrue() {

		Delta mockDelta = mock(Delta.class);

		when(mockDelta.hasDelta()).thenReturn(true);

		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty(null, mockDelta)).isTrue();

		verify(mockDelta, times(1)).hasDelta();
	}

	@Test
	public void isDirtyWithDeltaObjectReturnsFalse() {

		Delta mockDelta = mock(Delta.class);

		when(mockDelta.hasDelta()).thenReturn(false);

		assertThat(DeltaAwareDirtyPredicate.INSTANCE.isDirty("one", mockDelta)).isFalse();

		verify(mockDelta, times(1)).hasDelta();
	}
}
