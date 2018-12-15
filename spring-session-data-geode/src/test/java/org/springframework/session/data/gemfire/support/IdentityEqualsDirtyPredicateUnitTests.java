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

import org.junit.Test;

/**
 * Unit Tests for {@link IdentityEqualsDirtyPredicate}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.springframework.session.data.gemfire.support.IdentityEqualsDirtyPredicate
 * @since 2.1.2
 */
public class IdentityEqualsDirtyPredicateUnitTests {

	@Test
	public void isDirtyIsNullSafe() {

		assertThat(IdentityEqualsDirtyPredicate.INSTANCE.isDirty(null, null)).isFalse();
		assertThat(IdentityEqualsDirtyPredicate.INSTANCE.isDirty("one", null)).isTrue();
		assertThat(IdentityEqualsDirtyPredicate.INSTANCE.isDirty(null, "one")).isTrue();
	}

	@Test
	public void isDirtyWithDifferentObjectsReturnsTrue() {
		assertThat(IdentityEqualsDirtyPredicate.INSTANCE.isDirty("one", "two")).isTrue();
	}

	@Test
	public void isDirtyWithSameObjectsReturnsFalse() {
		assertThat(IdentityEqualsDirtyPredicate.INSTANCE.isDirty("one", "one")).isFalse();
	}
}
