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

package org.springframework.session.data.gemfire.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Test;

/**
 * Unit Tests for {@link IsDirtyPredicate}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
 * @since 2.1.2
 */
public class IsDirtyPredicateUnitTests {

	@Test
	public void alwaysDirtyPredicateIsAlwaysTrue() {

		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.isDirty("one", "one")).isTrue();
		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.isDirty("one", "two")).isTrue();
	}

	@Test
	public void alwaysDirtyPredicateIsNullSafe() {

		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.isDirty("one", null)).isTrue();
		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.isDirty(null, "one")).isTrue();
		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.isDirty(null, null)).isTrue();
	}

	@Test
	public void neverDirtyPredicateIsAlwaysTrue() {

		assertThat(IsDirtyPredicate.NEVER_DIRTY.isDirty("one", "one")).isFalse();
		assertThat(IsDirtyPredicate.NEVER_DIRTY.isDirty("one", "two")).isFalse();
	}

	@Test
	public void neverDirtyPredicateIsNullSafe() {

		assertThat(IsDirtyPredicate.NEVER_DIRTY.isDirty(null, null)).isFalse();
		assertThat(IsDirtyPredicate.NEVER_DIRTY.isDirty("one", null)).isFalse();
		assertThat(IsDirtyPredicate.NEVER_DIRTY.isDirty(null, "one")).isFalse();
	}

	@Test
	public void andThenComposesThisWithNull() {

		IsDirtyPredicate dirtyPredicate = mock(IsDirtyPredicate.class);

		when(dirtyPredicate.andThen(any())).thenCallRealMethod();

		IsDirtyPredicate composedDirtyPredicate = dirtyPredicate.andThen(null);

		assertThat(composedDirtyPredicate).isSameAs(dirtyPredicate);
	}

	@Test
	public void andThenComposesTwoIsDirtyPredicates() {

		IsDirtyPredicate dirtyPredicateOne = mock(IsDirtyPredicate.class);
		IsDirtyPredicate dirtyPredicateTwo = mock(IsDirtyPredicate.class);

		when(dirtyPredicateOne.andThen(any())).thenCallRealMethod();

		IsDirtyPredicate composedDirtyPredicate = dirtyPredicateOne.andThen(dirtyPredicateTwo);

		assertThat(composedDirtyPredicate).isNotNull();
		assertThat(composedDirtyPredicate).isNotSameAs(dirtyPredicateOne);
		assertThat(composedDirtyPredicate).isNotSameAs(dirtyPredicateTwo);
	}

	@Test
	public void andThenIsDirtyReturnsTrue() {

		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.andThen(IsDirtyPredicate.ALWAYS_DIRTY)
			.isDirty("one", "one")).isTrue();
	}

	@Test
	public void andThenIsDirtyReturnsFalse() {

		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.andThen(IsDirtyPredicate.NEVER_DIRTY)
			.isDirty("one", "two")).isFalse();
		assertThat(IsDirtyPredicate.NEVER_DIRTY.andThen(IsDirtyPredicate.ALWAYS_DIRTY)
			.isDirty("one", "two")).isFalse();
		assertThat(IsDirtyPredicate.NEVER_DIRTY.andThen(IsDirtyPredicate.NEVER_DIRTY)
			.isDirty("one", "two")).isFalse();
	}

	@Test
	public void orThenComposesThisWithNull() {

		IsDirtyPredicate dirtyPredicate = mock(IsDirtyPredicate.class);

		when(dirtyPredicate.orThen(any())).thenCallRealMethod();

		IsDirtyPredicate composedDirtyPredicate = dirtyPredicate.orThen(null);

		assertThat(composedDirtyPredicate).isSameAs(dirtyPredicate);
	}

	@Test
	public void orThenComposesTwoIsDirtyPredicates() {

		IsDirtyPredicate dirtyPredicateOne = mock(IsDirtyPredicate.class);
		IsDirtyPredicate dirtyPredicateTwo = mock(IsDirtyPredicate.class);

		when(dirtyPredicateOne.orThen(any())).thenCallRealMethod();

		IsDirtyPredicate composedDirtyPredicate = dirtyPredicateOne.orThen(dirtyPredicateTwo);

		assertThat(composedDirtyPredicate).isNotNull();
		assertThat(composedDirtyPredicate).isNotSameAs(dirtyPredicateOne);
		assertThat(composedDirtyPredicate).isNotSameAs(dirtyPredicateTwo);
	}

	@Test
	public void orThenIsDirtyReturnsTrue() {

		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.orThen(IsDirtyPredicate.ALWAYS_DIRTY)
			.isDirty("one", "one")).isTrue();
		assertThat(IsDirtyPredicate.ALWAYS_DIRTY.orThen(IsDirtyPredicate.NEVER_DIRTY)
			.isDirty("one", "one")).isTrue();
		assertThat(IsDirtyPredicate.NEVER_DIRTY.orThen(IsDirtyPredicate.ALWAYS_DIRTY)
			.isDirty("one", "one")).isTrue();
	}

	@Test
	public void orThenIsDirtyReturnsFalse() {

		assertThat(IsDirtyPredicate.NEVER_DIRTY.andThen(IsDirtyPredicate.NEVER_DIRTY)
			.isDirty("one", "two")).isFalse();
	}
}
