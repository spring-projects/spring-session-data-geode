/*
 * Copyright 2019 the original author or authors.
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

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * {@link IsDirtyPredicate} is a strategy interface used to configure Spring Session on how to evaluate application
 * domain objects to determine whether they are dirty or not.
 *
 * @author John Blum
 * @since 2.1.2
 */
@FunctionalInterface
@SuppressWarnings("unused")
public interface IsDirtyPredicate {

	IsDirtyPredicate ALWAYS_DIRTY = (oldValue, newValue) -> true;
	IsDirtyPredicate NEVER_DIRTY = (oldValue, newValue) -> false;

	/**
	 * Determines whether the {@link Object newValue} is dirty relative to the {@link Object oldValue}.
	 *
	 * @param oldValue {@link Object} referring to the previous value.
	 * @param newValue {@link Object} referring to the new value.
	 * @return a boolean value indicating whether the {@link Object newValue} is dirty
	 * relative to the {@link Object oldValue}.
	 */
	boolean isDirty(Object oldValue, Object newValue);

	/**
	 * Composes 2 {@link IsDirtyPredicate} objects using the logical AND operator.
	 *
	 * The {@code other} {@link IsDirtyPredicate} is applied after {@literal this} {@link IsDirtyPredicate}
	 * in the {@link #isDirty(Object, Object)} comparison evaluation.
	 *
	 * This composition is {@literal null-safe} and returns {@literal this} {@link IsDirtyPredicate}
	 * if {@link IsDirtyPredicate other} is {@literal null}.
	 *
	 * @param other {@link IsDirtyPredicate} composed with {@literal this} {@link IsDirtyPredicate}.
	 * @return an {@link IsDirtyPredicate} composition consisting of {@literal this} {@link IsDirtyPredicate}
	 * composed with the {@code other} {@link IsDirtyPredicate} using the logical AND operator.
	 * Returns {@literal this} {@link IsDirtyPredicate} if {@link IsDirtyPredicate other} is {@literal null}.
	 * @see #orThen(IsDirtyPredicate)
	 */
	default @NonNull IsDirtyPredicate andThen(@Nullable IsDirtyPredicate other) {

		return other != null
			? (oldValue, newValue) -> this.isDirty(oldValue, newValue) && other.isDirty(oldValue, newValue)
			: this;
	}

	/**
	 * Composes 2 {@link IsDirtyPredicate} objects using the logical OR operator.
	 *
	 * The {@code other} {@link IsDirtyPredicate} is applied after {@literal this} {@link IsDirtyPredicate}
	 * in the {@link #isDirty(Object, Object)} comparison evaluation.
	 *
	 * This composition is {@literal null-safe} and returns {@literal this} {@link IsDirtyPredicate}
	 * if {@link IsDirtyPredicate other} is {@literal null}.
	 *
	 * @param other {@link IsDirtyPredicate} composed with {@literal this} {@link IsDirtyPredicate}.
	 * @return an {@link IsDirtyPredicate} composition consisting of {@literal this} {@link IsDirtyPredicate}
	 * composed with the {@code other} {@link IsDirtyPredicate} using the logical OR operator.
	 * Returns {@literal this} {@link IsDirtyPredicate} if {@link IsDirtyPredicate other} is {@literal null}.
	 * @see #andThen(IsDirtyPredicate)
	 */
	default @NonNull IsDirtyPredicate orThen(@Nullable IsDirtyPredicate other) {

		return other != null
			? (oldValue, newValue) -> this.isDirty(oldValue, newValue) || other.isDirty(oldValue, newValue)
			: this;
	}
}
