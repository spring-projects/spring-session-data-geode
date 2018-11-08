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

package sample.client;

import java.io.PrintWriter;
import java.io.StringWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.gemfire.config.annotation.ClientCacheApplication;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import sample.client.model.RequestScopedProxyBean;
import sample.client.model.SessionScopedProxyBean;

/**
 * A Spring Boot, Pivotal GemFire cache client, web application that reveals the current state of the HTTP Session.
 *
 * @author John Blum
 * @see javax.servlet.http.HttpSession
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @see org.springframework.context.annotation.Bean
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession
 * @see org.springframework.stereotype.Controller
 * @see org.apache.geode.cache.client.ClientCache
 * @see org.apache.geode.cache.client.Pool
 * @since 1.2.1
 */
@SuppressWarnings("unused")
// tag::class[]
@SpringBootApplication // <1>
@Controller // <2>
public class Application {

	static final String INDEX_TEMPLATE_VIEW_NAME = "index";
	static final String PING_RESPONSE = "PONG";

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@ClientCacheApplication(name = "SpringSessionDataGeodeBootSampleWithScopedProxiesClient", logLevel = "error",
		readTimeout = 15000, retryAttempts = 1, subscriptionEnabled = true)  // <3>
	@EnableGemFireHttpSession(poolName = "DEFAULT") // <4>
	static class ClientCacheConfiguration { }

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

	@Autowired
	private RequestScopedProxyBean requestBean;

	@Autowired
	private SessionScopedProxyBean sessionBean;

	@ExceptionHandler
	@ResponseBody
	public String errorHandler(Throwable error) {
		StringWriter writer = new StringWriter();
		error.printStackTrace(new PrintWriter(writer));
		return writer.toString();
	}

	@RequestMapping(method = RequestMethod.GET, path = "/ping")
	@ResponseBody
	public String ping() {
		return PING_RESPONSE;
	}

	@RequestMapping(method = RequestMethod.GET, path = "/counts")
	public String requestAndSessionInstanceCount(HttpServletRequest request, HttpSession session, Model model) { // <6>

		model.addAttribute("sessionId", session.getId());
		model.addAttribute("requestCount", this.requestBean.getCount());
		model.addAttribute("sessionCount", this.sessionBean.getCount());

		return INDEX_TEMPLATE_VIEW_NAME;
	}
}
// end::class[]
