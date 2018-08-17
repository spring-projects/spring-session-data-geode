/*
 * Copyright 2014-2016 the original author or authors.
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

package sample;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;

@SuppressWarnings("unused")
public class ClientServerReadyBeanPostProcessor implements BeanPostProcessor {

	private static final long DEFAULT_TIMEOUT = TimeUnit.SECONDS.toMillis(20);

	private final AtomicBoolean verifyGemFireServerIsRunning = new AtomicBoolean(true);

	@Value("${spring.data.gemfire.cache.server.port:40404}")
	private int port;

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {

		if (isGemFireServerRunningVerificationEnabled(bean, beanName)) {
			waitOn(getGemFireServerSocketCondition(this.port));
		}

		return bean;
	}

	private boolean isGemFireServerRunningVerificationEnabled(Object bean, String beanName) {

		return isSessionGemFireRegion(bean, beanName)
			&& this.verifyGemFireServerIsRunning.compareAndSet(true, false);
	}

	private boolean isSessionGemFireRegion(Object bean, String beanName) {
		return GemFireHttpSessionConfiguration.DEFAULT_SESSION_REGION_NAME.equals(beanName);
	}

	private Condition getGemFireServerSocketCondition(int port) {

		AtomicBoolean gemfireServerRunning = new AtomicBoolean(false);

		return () -> {

			Socket socket = null;

			try {
				if (!gemfireServerRunning.get()) {
					socket = new Socket("localhost", port);
					gemfireServerRunning.set(true);
				}
			}
			catch (IOException ignore) { }
			finally {
				safeClose(socket);
			}

			return gemfireServerRunning.get();
		};
	}

	private boolean safeClose(Socket socket) {

		try {
			if (socket != null) {
				socket.close();
			}

			return true;
		}
		catch (IOException ignore) {
			return false;
		}
	}

	private boolean waitOn(Condition condition) {

		long timeout = System.currentTimeMillis() + DEFAULT_TIMEOUT;

		while (doWait(condition, timeout)) {
			synchronized (this) {
				try {
					TimeUnit.MICROSECONDS.timedWait(this, 500L);
				}
				catch (InterruptedException cause) {
					Thread.currentThread().interrupt();
				}
			}
		}

		return condition.evaluate();
	}

	private boolean doWait(Condition condition, long timeout) {
		return !Thread.currentThread().isInterrupted() && System.currentTimeMillis() < timeout && !condition.evaluate();
	}

	@FunctionalInterface
	interface Condition {
		boolean evaluate();
	}
}
