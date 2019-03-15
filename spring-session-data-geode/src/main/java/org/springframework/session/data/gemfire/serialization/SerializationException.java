/*
 * Copyright 2018 the original author or authors.
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

package org.springframework.session.data.gemfire.serialization;

/**
 * The SerializationException class is a {@link RuntimeException} indicating an error occurred while attempting to
 * serialize a {@link org.springframework.session.Session}.
 *
 * @author John Blum
 * @see java.lang.RuntimeException
 * @since 2.0.0
 */
@SuppressWarnings("unused")
public class SerializationException extends RuntimeException {

	/**
	 * Constructs a default instance of {@link SerializationException} with no {@link String message}
	 * or {@link Throwable cause}.
	 *
	 * @see java.lang.RuntimeException#RuntimeException()
	 */
	public SerializationException() { }

	/**
	 * Constructs a new instance of {@link SerializationException} initialized with the given {@link String message}
	 * describing the serialization error.
	 *
	 * @param message {@link String} describing the serialization error.
	 * @see java.lang.RuntimeException#RuntimeException(String)
	 * @see java.lang.String
	 */
	public SerializationException(String message) {
		super(message);
	}

	/**
	 * Constructs a new instance of {@link SerializationException} initialized with the given {@link Throwable cause}
	 * of the serialization error.
	 *
	 * @param cause {@link Throwable underlying cause} of the serialization error.
	 * @see java.lang.RuntimeException#RuntimeException(Throwable)
	 * @see java.lang.Throwable
	 */
	public SerializationException(Throwable cause) {
		super(cause);
	}

	/**
	 * Constructs a new instance of {@link SerializationException} initialized with the given {@link String message}
	 * describing the serialization error and {@link Throwable cause} of the serialization error.
	 *
	 * @param message {@link String} describing the serialization error.
	 * @param cause {@link Throwable underlying cause} of the serialization error.
	 * @see java.lang.RuntimeException#RuntimeException(String, Throwable)
	 * @see java.lang.Throwable
	 * @see java.lang.String
	 */
	public SerializationException(String message, Throwable cause) {
		super(message, cause);
	}
}
