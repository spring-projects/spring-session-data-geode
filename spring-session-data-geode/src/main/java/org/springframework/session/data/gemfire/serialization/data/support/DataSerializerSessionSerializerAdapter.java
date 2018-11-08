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

package org.springframework.session.data.gemfire.serialization.data.support;

import static org.springframework.data.gemfire.util.ArrayUtils.asArray;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalArgumentException;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalStateException;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Optional;

import org.apache.geode.DataSerializer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSession;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.DeltaCapableGemFireSessionAttributes;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSession;
import org.springframework.session.data.gemfire.AbstractGemFireOperationsSessionRepository.GemFireSessionAttributes;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;
import org.springframework.stereotype.Component;

/**
 * The {@link DataSerializerSessionSerializerAdapter} class is a two-way Adapter adapting a {@link SessionSerializer}
 * instance as an instance of {@link DataSerializer} in a GemFire/Geode context, or adapting a {@link DataSerializer}
 * as a {@link SessionSerializer} in a Spring Session context.
 *
 * @author John Blum
 * @see org.apache.geode.DataSerializer
 * @see org.springframework.session.Session
 * @see org.springframework.session.data.gemfire.serialization.SessionSerializer
 * @see org.springframework.session.data.gemfire.serialization.data.support.WirableDataSerializer
 * @see org.springframework.stereotype.Component
 * @since 2.0.0
 */
@Component
@SuppressWarnings("unused")
public class DataSerializerSessionSerializerAdapter<T extends Session> extends WirableDataSerializer<T> {

	static {
		register(DataSerializerSessionSerializerAdapter.class);
	}

	private SessionSerializer<T, DataInput, DataOutput> sessionSerializer;

	public DataSerializerSessionSerializerAdapter() {
		autowire();
	}

	@Override
	public int getId() {
		return 0x0BAC2BAC;
	}

	@Autowired
	@Qualifier(GemFireHttpSessionConfiguration.SESSION_SERIALIZER_BEAN_ALIAS)
	public final void setSessionSerializer(SessionSerializer<T, DataInput, DataOutput> sessionSerializer) {
		this.sessionSerializer = Optional.ofNullable(sessionSerializer)
			.orElseThrow(() -> newIllegalArgumentException("SessionSerializer is required"));
	}

	public SessionSerializer<T, DataInput, DataOutput> getSessionSerializer() {
		return Optional.ofNullable(this.sessionSerializer)
			.orElseThrow(() -> newIllegalStateException("SessionSerializer was not properly configured"));
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return asArray(GemFireSession.class, GemFireSessionAttributes.class, DeltaCapableGemFireSession.class,
			DeltaCapableGemFireSessionAttributes.class);
	}

	@Override
	public void serialize(T session, DataOutput out) {
		getSessionSerializer().serialize(session, out);
	}

	@Override
	public T deserialize(DataInput in) {
		return getSessionSerializer().deserialize(in);
	}
}
