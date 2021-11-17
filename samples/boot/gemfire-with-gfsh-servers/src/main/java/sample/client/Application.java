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
package sample.client;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import jakarta.servlet.http.HttpSession;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.data.gemfire.util.CollectionUtils;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@SuppressWarnings("unused")
// tag::class[]
@SpringBootApplication // <1>
@ClientCacheApplication(subscriptionEnabled = true) // <2>
@EnableGemFireHttpSession(regionName = "Sessions", poolName = "DEFAULT") // <3>
@Controller // <4>
public class Application {

	private static final String INDEX_TEMPLATE_VIEW_NAME = "index";
	private static final String PING_RESPONSE = "PONG";
	private static final String REQUEST_COUNT_ATTRIBUTE_NAME = "requestCount";

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Configuration
	static class SpringWebMvcConfiguration {  // <5>

		@Bean
		public WebMvcConfigurer webMvcConfig() {

			return new WebMvcConfigurer() {

				@Override
				public void addViewControllers(ViewControllerRegistry registry) {
					registry.addViewController("/").setViewName(INDEX_TEMPLATE_VIEW_NAME);
				}
			};
		}
	}

	@ExceptionHandler // <6>
	public String errorHandler(Throwable error) {
		StringWriter writer = new StringWriter();
		error.printStackTrace(new PrintWriter(writer));
		return writer.toString();
	}

	@GetMapping("/ping")
	@ResponseBody
	public String ping() {
		return PING_RESPONSE;
	}

	@PostMapping("/session")
	public String session(HttpSession session, ModelMap modelMap,
			@RequestParam(name = "attributeName", required = false) String name,
			@RequestParam(name = "attributeValue", required = false) String value) { // <7>

		modelMap.addAttribute("sessionAttributes",
			attributes(setAttribute(updateRequestCount(session), name, value)));

		return INDEX_TEMPLATE_VIEW_NAME;
	}

// end::class[]

	HttpSession updateRequestCount(HttpSession session) {

		synchronized (session) {
			Integer currentRequestCount = (Integer) session.getAttribute(REQUEST_COUNT_ATTRIBUTE_NAME);
			session.setAttribute(REQUEST_COUNT_ATTRIBUTE_NAME, nullSafeIncrement(currentRequestCount));
			return session;
		}
	}

	Integer nullSafeIncrement(Integer value) {
		return nullSafeIntValue(value) + 1;
	}

	int nullSafeIntValue(Number value) {
		return Optional.ofNullable(value).map(Number::intValue).orElse(0);
	}

	HttpSession setAttribute(HttpSession session, String attributeName, String attributeValue) {

		if (isSet(attributeName, attributeValue)) {
			session.setAttribute(attributeName, attributeValue);
		}

		return session;
	}

	boolean isSet(String... values) {

		boolean set = true;

		for (String value : values) {
			set &= StringUtils.hasText(value);
		}

		return set;
	}

	Map<String, String> attributes(HttpSession session) {

		Map<String, String> sessionAttributes = new HashMap<>();

		StreamSupport.stream(toIterable(session.getAttributeNames()).spliterator(), false)
			.forEach(attributeName -> sessionAttributes.put(attributeName,
				String.valueOf(session.getAttribute(attributeName))));

		return sessionAttributes;
	}

	<T> Iterable<T> toIterable(Enumeration<T> enumeration) {

		return () -> Optional.ofNullable(enumeration)
			.map(CollectionUtils::toIterator)
			.orElseGet(Collections::emptyIterator);
	}
}
