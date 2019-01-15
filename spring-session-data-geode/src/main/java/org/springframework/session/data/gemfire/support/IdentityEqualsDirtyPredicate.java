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

package org.springframework.session.data.gemfire.support;

import org.springframework.lang.Nullable;

/**
 * {@link IdentityEqualsDirtyPredicate} is an {@link IsDirtyPredicate} strategy interface implementation
 * that determines whether the {@link Object new value} is dirty by comparing the {@link Object new value}
 * with the {@link Object old value} using the identity operator ({@literal ==}).
 *
 * @author John Blum
 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
 * @since 2.1.2
 */
@SuppressWarnings("unused")
public class IdentityEqualsDirtyPredicate implements IsDirtyPredicate {

	public static final IdentityEqualsDirtyPredicate INSTANCE = new IdentityEqualsDirtyPredicate();

	/**
	 * Determines whether the {@link Object new value} is dirty by comparing the {@link Object new value}
	 * with the {@link Object old value} using the identity operator ({@literal ==}).
	 *
	 * This method is {@literal null-safe}.
	 *
	 * @param oldValue {@link Object} referring to the previous value.
	 * @param newValue {@link Object} referring to the new value.
	 * @return a boolean value indicating whether the {@link Object new value} is dirty by comparing
	 * the {@link Object new value} with the {@link Object old value} using the identity operator ({@literal ==}).
	 */
	@Override
	public boolean isDirty(@Nullable Object oldValue, @Nullable Object newValue) {
		return newValue != oldValue;
	}
}
