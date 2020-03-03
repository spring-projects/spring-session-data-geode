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

import org.apache.geode.Delta;

import org.springframework.lang.Nullable;

/**
 * {@link DeltaAwareDirtyPredicate} is an {@link IsDirtyPredicate} strategy interface implementation that evaluates
 * the {@link Object new value} as instance of {@link Delta} and uses the {@link Delta#hasDelta()} method
 * to determine if the {@link Object new value} is dirty.
 *
 * @author John Blum
 * @see org.apache.geode.Delta
 * @see org.springframework.session.data.gemfire.support.IsDirtyPredicate
 * @since 2.1.2
 */
@SuppressWarnings("unused")
public class DeltaAwareDirtyPredicate implements IsDirtyPredicate {

	public static final DeltaAwareDirtyPredicate INSTANCE = new DeltaAwareDirtyPredicate();

	/**
	 * Determines whether the {@link Object new value} is dirty by evaluating the {@link Object new value}
	 * as an instance of {@link Delta} and invoking its {@link Delta#hasDelta()} method.
	 *
	 * The {@link Object new value} is considered dirty immediately and automatically if the {@link Object new value}
	 * is not an instance of {@link Delta}.
	 *
	 * This method is {@literal null-safe}.
	 *
	 * @param oldValue {@link Object} referring to the previous value.
	 * @param newValue {@link Object} referring to the new value.
	 * @return a boolean value indicating whether the {@link Object new value} is dirty by evaluating
	 * the {@link Object new value} as an instance of {@link Delta} and invoking its {@link Delta#hasDelta()} method.
	 * Returns {@literal true} immediately if the {@link Object new value} is not an instance of {@link Delta}.
	 * @see org.apache.geode.Delta#hasDelta()
	 * @see org.apache.geode.Delta
	 */
	@Override
	public boolean isDirty(@Nullable Object oldValue, @Nullable Object newValue) {

		return newValue != oldValue
			|| !(newValue instanceof Delta)
			|| ((Delta) newValue).hasDelta();
	}
}
