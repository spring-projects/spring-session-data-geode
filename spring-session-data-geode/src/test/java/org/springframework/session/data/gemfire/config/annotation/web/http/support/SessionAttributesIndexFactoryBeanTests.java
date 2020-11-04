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
package org.springframework.session.data.gemfire.config.annotation.web.http.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import org.junit.Before;
import org.junit.Test;

import org.apache.geode.cache.query.Index;

import org.springframework.data.gemfire.util.ArrayUtils;
import org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration;

/**
 * Unit Tests for {@link SessionAttributesIndexFactoryBean}.
 *
 * @author John Blum
 * @see org.junit.Test
 * @see org.mockito.Mockito
 * @see org.apache.geode.cache.query.Index
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.GemFireHttpSessionConfiguration
 * @see org.springframework.session.data.gemfire.config.annotation.web.http.support.SessionAttributesIndexFactoryBean
 * @since 1.3.0
 */
public class SessionAttributesIndexFactoryBeanTests {

	private SessionAttributesIndexFactoryBean indexFactoryBean;

	@Before
	public void setup() {
		this.indexFactoryBean = new SessionAttributesIndexFactoryBean();
	}

	@Test
	public void indexIsNotInitializedWhenNoIndexableSessionAttributesAreConfigured() throws Exception {

		Index mockIndex = mock(Index.class);

		SessionAttributesIndexFactoryBean indexFactoryBean = spy(new SessionAttributesIndexFactoryBean());

		doReturn(mockIndex).when(indexFactoryBean).newIndex();

		indexFactoryBean.afterPropertiesSet();

		assertThat(indexFactoryBean.getObject()).isNull();
		assertThat(indexFactoryBean.getObjectType()).isEqualTo(Index.class);
	}

	@Test
	public void initializesIndexWhenIndexableSessionAttributesAreConfigured() throws Exception {

		Index mockIndex = mock(Index.class);

		SessionAttributesIndexFactoryBean indexFactoryBean = spy(new SessionAttributesIndexFactoryBean());

		doReturn(mockIndex).when(indexFactoryBean).newIndex();

		indexFactoryBean.setIndexableSessionAttributes(ArrayUtils.asArray("one", "two"));
		indexFactoryBean.afterPropertiesSet();

		assertThat(indexFactoryBean.getObject()).isEqualTo(mockIndex);
		assertThat(indexFactoryBean.getObjectType()).isEqualTo(mockIndex.getClass());
	}

	@Test
	public void isSingletonIsTrue() {
		assertThat(this.indexFactoryBean.isSingleton()).isTrue();
	}

	@Test
	public void setAndGetIndexableSessionAttributes() {

		assertThat(this.indexFactoryBean.getIndexableSessionAttributes())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);

		assertThat(this.indexFactoryBean.getIndexableSessionAttributesAsGemFireIndexExpression()).isEqualTo("*");

		this.indexFactoryBean.setIndexableSessionAttributes(ArrayUtils.asArray("one", "two", "three"));

		assertThat(this.indexFactoryBean.getIndexableSessionAttributes())
			.isEqualTo(ArrayUtils.asArray("one", "two", "three"));

		assertThat(this.indexFactoryBean.getIndexableSessionAttributesAsGemFireIndexExpression())
			.isEqualTo("'one', 'two', 'three'");

		this.indexFactoryBean.setIndexableSessionAttributes(ArrayUtils.asArray("one"));

		assertThat(this.indexFactoryBean.getIndexableSessionAttributes()).isEqualTo(ArrayUtils.asArray("one"));
		assertThat(this.indexFactoryBean.getIndexableSessionAttributesAsGemFireIndexExpression()).isEqualTo("'one'");

		this.indexFactoryBean.setIndexableSessionAttributes(null);

		assertThat(this.indexFactoryBean.getIndexableSessionAttributes())
			.isEqualTo(GemFireHttpSessionConfiguration.DEFAULT_INDEXABLE_SESSION_ATTRIBUTES);

		assertThat(this.indexFactoryBean.getIndexableSessionAttributesAsGemFireIndexExpression()).isEqualTo("*");
	}

	@Test
	public void setAndGetRegionName() {

		assertThat(this.indexFactoryBean.getRegionName()).isNull();

		this.indexFactoryBean.setRegionName("Example");

		assertThat(this.indexFactoryBean.getRegionName()).isEqualTo("Example");

		this.indexFactoryBean.setRegionName(null);

		assertThat(this.indexFactoryBean.getRegionName()).isNull();
	}
}
