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

package sample.client.model;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * The RequestScopedProxyBean class...
 *
 * @author John Blum
 * @since 1.0.0
 */
@SuppressWarnings("unused")
// tag::class[]
@Component // <1>
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS) // <2>
public class RequestScopedProxyBean {

	private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger(0);

	private final int count;

	public RequestScopedProxyBean() {
		this.count = INSTANCE_COUNTER.incrementAndGet(); // <3>
	}

	public int getCount() {
		return count;
	}

	@Override
	public String toString() {
		return String.format("{ @type = '%s', count = %d }", getClass().getName(), getCount());
	}
}
// end::class[]
