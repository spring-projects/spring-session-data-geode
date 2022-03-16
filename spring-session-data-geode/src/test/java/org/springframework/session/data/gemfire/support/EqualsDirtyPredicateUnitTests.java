/*
 * Copyright 2015-present the original author or authors.
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

import org.junit.Test;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Unit Tests for {@link EqualsDirtyPredicate}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.data.gemfire.support.EqualsDirtyPredicate
 * @since 2.1.2
 */
public class EqualsDirtyPredicateUnitTests {

	@Test
	public void isDirtyIsIsNullSafe() {

		assertThat(EqualsDirtyPredicate.INSTANCE.isDirty(null, null)).isFalse();
		assertThat(EqualsDirtyPredicate.INSTANCE.isDirty("one", null)).isTrue();
		assertThat(EqualsDirtyPredicate.INSTANCE.isDirty(null, "one")).isTrue();
	}

	@Test
	public void isDirtyWithSameObjectReturnsFalse() {

		Entry keyValue = Entry.newEntry(1, "one");

		assertThat(EqualsDirtyPredicate.INSTANCE.isDirty(keyValue, keyValue));
	}

	@Test
	public void isDirtyWithEqualObjectsReturnsFalse() {

		Entry entryOne = Entry.newEntry(1, "one");
		Entry entryTwo = Entry.newEntry(1, "one");

		assertThat(EqualsDirtyPredicate.INSTANCE.isDirty(entryOne, entryTwo));
	}

	@Test
	public void isDirtyWithDifferentObjectsReturnsFalse() {

		Entry entryOne = Entry.newEntry(1, "one");
		Entry entryTwo = Entry.newEntry(2, "two");

		assertThat(EqualsDirtyPredicate.INSTANCE.isDirty(entryOne, entryTwo));
	}

	@Data
	@EqualsAndHashCode
	@RequiredArgsConstructor(staticName = "newEntry")
	static class Entry {

		@NonNull
		private final Object key;

		@NonNull
		private final Object value;

		@Override
		public String toString() {
			return String.format("%1$s = %2$s", getKey(), getValue());
		}
	}
}
