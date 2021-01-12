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
package org.springframework.session.data.gemfire.web.http;

import static org.springframework.data.gemfire.util.RuntimeExceptionFactory.newUnsupportedOperationException;

import java.util.Collections;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.session.web.http.CookieSerializer;

/**
 * Abstract base class implementing the Spring Session core {@link CookieSerializer} interface to encapsulate
 * functionality common to all implementations as well as to simplify the implementation of the Spring Session core
 * {@link CookieSerializer} interface.
 *
 * @author John Blum
 * @see javax.servlet.http.HttpServletRequest
 * @see org.springframework.session.web.http.CookieSerializer
 * @since 2.5.0
 */
public class AbstractCookieSerializer implements CookieSerializer {

	protected static final String NOT_IMPLEMENTED = "NOT IMPLEMENTED";

	/**
	 * @inheritDoc
	 */
	@Override
	public List<String> readCookieValues(HttpServletRequest request) {
		return Collections.emptyList();
	}

	/**
	 * @inheritDoc
	 */
	@Override
	public void writeCookieValue(CookieValue cookieValue) {
		throw newUnsupportedOperationException(NOT_IMPLEMENTED);
	}
}
