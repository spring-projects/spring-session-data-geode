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

package org.springframework.session.data.gemfire.serialization.pdx.support;

import static java.util.stream.StreamSupport.stream;
import static org.springframework.data.gemfire.util.ArrayUtils.nullSafeArray;
import static org.springframework.data.gemfire.util.CollectionUtils.nullSafeIterable;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalArgumentException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.geode.pdx.PdxReader;
import org.apache.geode.pdx.PdxSerializer;
import org.apache.geode.pdx.PdxWriter;

/**
 * The ComposablePdxSerializer class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
public class ComposablePdxSerializer implements Iterable<PdxSerializer>, PdxSerializer {

	private final List<PdxSerializer> pdxSerializers;

	private ComposablePdxSerializer(List<PdxSerializer> pdxSerializers) {

		this.pdxSerializers = Optional.ofNullable(pdxSerializers)
			.map(it -> Collections.unmodifiableList(pdxSerializers))
			.orElseThrow(() -> newIllegalArgumentException("PdxSerializers [%s] are required", pdxSerializers));
	}

	public static PdxSerializer compose(PdxSerializer... pdxSerializers) {
		return compose(Arrays.asList(nullSafeArray(pdxSerializers, PdxSerializer.class)));
	}

	public static PdxSerializer compose(Iterable<PdxSerializer> pdxSerializers) {

		List<PdxSerializer> pdxSerializerList =
			stream(nullSafeIterable(pdxSerializers).spliterator(), false)
				.filter(Objects::nonNull).collect(Collectors.toList());

		return (pdxSerializerList.isEmpty() ? null
			: (pdxSerializerList.size() == 1 ? pdxSerializerList.get(0)
			: new ComposablePdxSerializer(pdxSerializerList)));
	}

	@Override
	public Iterator<PdxSerializer> iterator() {
		return this.pdxSerializers.iterator();
	}

	@Override
	public boolean toData(Object obj, PdxWriter out) {

		for (PdxSerializer pdxSerializer : this) {
			if (pdxSerializer.toData(obj, out)) {
				return true;
			}
		}

		return false;
	}

	@Override
	public Object fromData(Class<?> type, PdxReader in) {

		for (PdxSerializer pdxSerializer : this) {

			Object obj = pdxSerializer.fromData(type, in);

			if (obj != null) {
				return obj;
			}
		}

		return null;
	}
}
