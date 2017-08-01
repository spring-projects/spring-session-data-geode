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

package org.springframework.session.data.gemfire.serialization.data.support;

import static org.springframework.data.gemfire.util.ArrayUtils.asArray;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalArgumentException;
import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newIllegalStateException;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.Optional;

import javax.annotation.Resource;

import org.springframework.session.Session;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;
import org.springframework.session.data.gemfire.serialization.SessionSerializer;

/**
 * The DataSerializerSessionSerializerAdapter class...
 *
 * @author John Blum
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class DataSerializerSessionSerializerAdapter<T extends Session> extends WirableDataSerializer<T> {

	static {
		register(DataSerializerSessionSerializerAdapter.class);
	}

	@Resource(name = "${" + GemFireHttpSessionConfiguration.SESSION_SERIALIZER_QUALIFIER_PROPERTY_NAME
		+ ":" + GemFireHttpSessionConfiguration.SESSION_DATA_SERIALIZER_BEAN_NAME + "}")
	private SessionSerializer<T, DataInput, DataOutput> sessionSerializer;

	public DataSerializerSessionSerializerAdapter() {
		autowire();
	}

	public DataSerializerSessionSerializerAdapter(SessionSerializer<T, DataInput, DataOutput> sessionSerializer) {
		this.sessionSerializer = Optional.ofNullable(sessionSerializer)
			.orElseThrow(() -> newIllegalArgumentException("SessionSerializer is required"));
	}

	@Override
	public int getId() {
		return 0x0BAC2BAC;
	}

	protected SessionSerializer<T, DataInput, DataOutput> getSessionSerializer() {
		return Optional.ofNullable(this.sessionSerializer)
			.orElseThrow(() -> newIllegalStateException("SessionSerializer was not properly configured"));
	}

	@Override
	public Class<?>[] getSupportedClasses() {
		return asArray(Session.class);
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
