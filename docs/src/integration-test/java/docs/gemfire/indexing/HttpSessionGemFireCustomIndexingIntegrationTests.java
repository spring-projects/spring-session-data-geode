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

package docs.gemfire.indexing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.gemfire.config.annotation.PeerCacheApplication;
import org.springframework.session.Session;
import org.springframework.session.data.gemfire.GemFireOperationsSessionRepository;
import org.springframework.session.data.gemfire.config.annotation.web.http.EnableGemFireHttpSession;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author Rob Winch
 * @author John Blum
 */
@RunWith(SpringRunner.class)
@ContextConfiguration
@SuppressWarnings("unused")
public class HttpSessionGemFireCustomIndexingIntegrationTests {

	@Autowired
	private GemFireOperationsSessionRepository sessionRepository;

	@Test
	public void findByIndexName() {

		Session session = this.sessionRepository.createSession();

		String indexValue = "HttpSessionGemFireCustomIndexingIntegrationTests-findByIndexName";

		// tag::findbyindexname-set[]
		String indexName = "name1";

		session.setAttribute(indexName, indexValue);
		// end::findbyindexname-set[]

		this.sessionRepository.save(session);

		// tag::findbyindexname-get[]
		Map<String, Session> idToSessions =
			this.sessionRepository.findByIndexNameAndIndexValue(indexName, indexValue);
		// end::findbyindexname-get[]

		assertThat(idToSessions.keySet()).containsOnly(session.getId());

		this.sessionRepository.deleteById(session.getId());
	}

	@PeerCacheApplication(name = "HttpSessionGemFireCustomIndexingIntegrationTests", logLevel = "error")
	@EnableGemFireHttpSession(indexableSessionAttributes = { "name1", "name2", "name3" },
		regionName = "HttpSessionGemFireIndexingCustomTestRegion")
	static class TestConfiguration {
	}
}
