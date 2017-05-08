/*
 * Copyright 2012-2017 the original author or authors.
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

package org.springframework.boot.context.properties.bind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import org.springframework.boot.context.properties.bind.BinderTests.ExampleEnum;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MockConfigurationPropertySource;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.test.context.support.TestPropertySourceUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

/**
 * Tests for {@link MapBinder}.
 *
 * @author Phillip Webb
 * @author Madhura Bhave
 */
public class MapBinderTests {

	private static final Bindable<Map<String, String>> STRING_STRING_MAP = Bindable
			.mapOf(String.class, String.class);

	private static final Bindable<Map<String, Integer>> STRING_INTEGER_MAP = Bindable
			.mapOf(String.class, Integer.class);

	private static final Bindable<Map<Integer, Integer>> INTEGER_INTEGER_MAP = Bindable
			.mapOf(Integer.class, Integer.class);

	private static final Bindable<Map<String, Object>> STRING_OBJECT_MAP = Bindable
			.mapOf(String.class, Object.class);

	private static final Bindable<Map<String, String[]>> STRING_ARRAY_MAP = Bindable
			.mapOf(String.class, String[].class);

	private List<ConfigurationPropertySource> sources = new ArrayList<>();

	private Binder binder;

	@Before
	public void setup() {
		this.binder = new Binder(this.sources);
	}

	@Test
	public void bindToMapShouldReturnPopulatedMap() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.bar", "1");
		source.put("foo.[baz]", "2");
		source.put("foo[BiNg]", "3");
		this.sources.add(source);
		Map<String, String> result = this.binder.bind("foo", STRING_STRING_MAP).get();
		assertThat(result).hasSize(3);
		assertThat(result).containsEntry("bar", "1");
		assertThat(result).containsEntry("baz", "2");
		assertThat(result).containsEntry("BiNg", "3");
	}

	@Test
	@SuppressWarnings("unchecked")
	public void bindToMapWithEmptyPrefix() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.bar", "1");
		this.sources.add(source);
		Map<String, Object> result = this.binder.bind("", STRING_OBJECT_MAP).get();
		assertThat((Map<String, Object>) result.get("foo")).containsEntry("bar", "1");
	}

	@Test
	public void bindToMapShouldConvertMapValue() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.bar", "1");
		source.put("foo.[baz]", "2");
		source.put("foo[BiNg]", "3");
		source.put("faf.bar", "x");
		this.sources.add(source);
		Map<String, Integer> result = this.binder.bind("foo", STRING_INTEGER_MAP).get();
		assertThat(result).hasSize(3);
		assertThat(result).containsEntry("bar", 1);
		assertThat(result).containsEntry("baz", 2);
		assertThat(result).containsEntry("BiNg", 3);
	}

	@Test
	public void bindToMapShouldBindToMapValue() throws Exception {
		ResolvableType type = ResolvableType.forClassWithGenerics(Map.class,
				ResolvableType.forClass(String.class), STRING_INTEGER_MAP.getType());
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.bar.baz", "1");
		source.put("foo.bar.bin", "2");
		source.put("foo.far.baz", "3");
		source.put("foo.far.bin", "4");
		source.put("faf.far.bin", "x");
		this.sources.add(source);
		Map<String, Map<String, Integer>> result = this.binder
				.bind("foo", Bindable.<Map<String, Map<String, Integer>>>of(type)).get();
		assertThat(result).hasSize(2);
		assertThat(result.get("bar")).containsEntry("baz", 1).containsEntry("bin", 2);
		assertThat(result.get("far")).containsEntry("baz", 3).containsEntry("bin", 4);
	}

	@Test
	public void bindToMapShouldBindNestedMapValue() throws Exception {
		ResolvableType nestedType = ResolvableType.forClassWithGenerics(Map.class,
				ResolvableType.forClass(String.class), STRING_INTEGER_MAP.getType());
		ResolvableType type = ResolvableType.forClassWithGenerics(Map.class,
				ResolvableType.forClass(String.class), nestedType);
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.nested.bar.baz", "1");
		source.put("foo.nested.bar.bin", "2");
		source.put("foo.nested.far.baz", "3");
		source.put("foo.nested.far.bin", "4");
		source.put("faf.nested.far.bin", "x");
		this.sources.add(source);
		Bindable<Map<String, Map<String, Map<String, Integer>>>> target = Bindable
				.of(type);
		Map<String, Map<String, Map<String, Integer>>> result = this.binder
				.bind("foo", target).get();
		Map<String, Map<String, Integer>> nested = result.get("nested");
		assertThat(nested).hasSize(2);
		assertThat(nested.get("bar")).containsEntry("baz", 1).containsEntry("bin", 2);
		assertThat(nested.get("far")).containsEntry("baz", 3).containsEntry("bin", 4);
	}

	@Test
	@SuppressWarnings("unchecked")
	public void bindToMapWhenMapValueIsObjectShouldBindNestedMapValue() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.nested.bar.baz", "1");
		source.put("foo.nested.bar.bin", "2");
		source.put("foo.nested.far.baz", "3");
		source.put("foo.nested.far.bin", "4");
		source.put("faf.nested.far.bin", "x");
		this.sources.add(source);
		Map<String, Object> result = this.binder
				.bind("foo", Bindable.mapOf(String.class, Object.class)).get();
		Map<String, Object> nested = (Map<String, Object>) result.get("nested");
		assertThat(nested).hasSize(2);
		Map<String, Object> bar = (Map<String, Object>) nested.get("bar");
		assertThat(bar).containsEntry("baz", "1").containsEntry("bin", "2");
		Map<String, Object> far = (Map<String, Object>) nested.get("far");
		assertThat(far).containsEntry("baz", "3").containsEntry("bin", "4");
	}

	@Test
	public void bindToMapWhenMapValueIsObjectAndNoRootShouldBindNestedMapValue()
			throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("commit.id", "abcdefg");
		source.put("branch", "master");
		source.put("foo", "bar");
		this.sources.add(source);
		Map<String, Object> result = this.binder
				.bind("", Bindable.mapOf(String.class, Object.class)).get();
		assertThat(result.get("commit"))
				.isEqualTo(Collections.singletonMap("id", "abcdefg"));
		assertThat(result.get("branch")).isEqualTo("master");
		assertThat(result.get("foo")).isEqualTo("bar");
	}

	@Test
	public void bindToMapWhenEmptyRootNameShouldBindMap() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("bar.baz", "1");
		source.put("bar.bin", "2");
		this.sources.add(source);
		Map<String, Integer> result = this.binder.bind("", STRING_INTEGER_MAP).get();
		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("bar.baz", 1).containsEntry("bar.bin", 2);
	}

	@Test
	public void bindToMapWhenMultipleCandidateShouldBindFirst() throws Exception {
		MockConfigurationPropertySource source1 = new MockConfigurationPropertySource();
		source1.put("foo.bar", "1");
		source1.put("foo.baz", "2");
		this.sources.add(source1);
		MockConfigurationPropertySource source2 = new MockConfigurationPropertySource();
		source2.put("foo.baz", "3");
		source2.put("foo.bin", "4");
		this.sources.add(source2);
		Map<String, Integer> result = this.binder.bind("foo", STRING_INTEGER_MAP).get();
		assertThat(result).hasSize(3);
		assertThat(result).containsEntry("bar", 1);
		assertThat(result).containsEntry("baz", 2);
		assertThat(result).containsEntry("bin", 4);
	}

	@Test
	public void bindToMapWhenMultipleInSameSourceCandidateShouldBindFirst()
			throws Exception {
		Map<String, Object> map = new HashMap<>();
		map.put("foo.bar", "1");
		map.put("foo.b-az", "2");
		map.put("foo.ba-z", "3");
		map.put("foo.bin", "4");
		MapConfigurationPropertySource propertySource = new MapConfigurationPropertySource(
				map);
		this.sources.add(propertySource);
		Map<String, Integer> result = this.binder.bind("foo", STRING_INTEGER_MAP).get();
		assertThat(result).hasSize(4);
		assertThat(result).containsEntry("bar", 1);
		assertThat(result).containsEntry("b-az", 2);
		assertThat(result).containsEntry("ba-z", 3);
		assertThat(result).containsEntry("bin", 4);
	}

	@Test
	public void bindToMapWhenHasExistingMapShouldReplaceOnlyNewContents()
			throws Exception {
		this.sources.add(new MockConfigurationPropertySource("foo.bar", "1"));
		Map<String, Integer> existing = new HashMap<>();
		existing.put("bar", 1000);
		existing.put("baz", 1001);
		Bindable<Map<String, Integer>> target = STRING_INTEGER_MAP
				.withExistingValue(existing);
		Map<String, Integer> result = this.binder.bind("foo", target).get();
		assertThat(result).isExactlyInstanceOf(HashMap.class);
		assertThat(result).isSameAs(existing);
		assertThat(result).hasSize(2);
		assertThat(result).containsEntry("bar", 1);
		assertThat(result).containsEntry("baz", 1001);
	}

	@Test
	public void bindToMapShouldRespectMapType() throws Exception {
		this.sources.add(new MockConfigurationPropertySource("foo.bar", "1"));
		ResolvableType type = ResolvableType.forClassWithGenerics(HashMap.class,
				String.class, Integer.class);
		Object defaultMap = this.binder.bind("foo", STRING_INTEGER_MAP).get();
		Object customMap = this.binder.bind("foo", Bindable.of(type)).get();
		assertThat(customMap).isExactlyInstanceOf(HashMap.class)
				.isNotInstanceOf(defaultMap.getClass());
	}

	@Test
	public void bindToMapWhenNoValueShouldReturnUnbound() throws Exception {
		this.sources.add(new MockConfigurationPropertySource("faf.bar", "1"));
		BindResult<Map<String, Integer>> result = this.binder.bind("foo",
				STRING_INTEGER_MAP);
		assertThat(result.isBound()).isFalse();
	}

	@Test
	public void bindToMapShouldConvertKey() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo[0]", "1");
		source.put("foo[1]", "2");
		source.put("foo[9]", "3");
		this.sources.add(source);
		Map<Integer, Integer> result = this.binder.bind("foo", INTEGER_INTEGER_MAP).get();
		assertThat(result).hasSize(3);
		assertThat(result).containsEntry(0, 1);
		assertThat(result).containsEntry(1, 2);
		assertThat(result).containsEntry(9, 3);
	}

	@Test
	public void bindToMapShouldBeGreedyForStrings() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.aaa.bbb.ccc", "b");
		source.put("foo.bbb.ccc.ddd", "a");
		source.put("foo.ccc.ddd.eee", "r");
		this.sources.add(source);
		Map<String, String> result = this.binder.bind("foo", STRING_STRING_MAP).get();
		assertThat(result).hasSize(3);
		assertThat(result).containsEntry("aaa.bbb.ccc", "b");
		assertThat(result).containsEntry("bbb.ccc.ddd", "a");
		assertThat(result).containsEntry("ccc.ddd.eee", "r");
	}

	@Test
	public void bindToMapShouldBeGreedyForScalars() throws Exception {
		MockConfigurationPropertySource source = new MockConfigurationPropertySource();
		source.put("foo.aaa.bbb.ccc", "foo-bar");
		source.put("foo.bbb.ccc.ddd", "BAR_BAZ");
		source.put("foo.ccc.ddd.eee", "bazboo");
		this.sources.add(source);
		Map<String, ExampleEnum> result = this.binder
				.bind("foo", Bindable.mapOf(String.class, ExampleEnum.class)).get();
		assertThat(result).hasSize(3);
		assertThat(result).containsEntry("aaa.bbb.ccc", ExampleEnum.FOO_BAR);
		assertThat(result).containsEntry("bbb.ccc.ddd", ExampleEnum.BAR_BAZ);
		assertThat(result).containsEntry("ccc.ddd.eee", ExampleEnum.BAZ_BOO);
	}

	@Test
	public void bindToMapWithPlaceholdersShouldBeGreedyForScalars() throws Exception {
		StandardEnvironment environment = new StandardEnvironment();
		TestPropertySourceUtils.addInlinedPropertiesToEnvironment(environment, "foo=boo");
		MockConfigurationPropertySource source = new MockConfigurationPropertySource(
				"foo.aaa.bbb.ccc", "baz-${foo}");
		this.sources.add(source);
		this.binder = new Binder(this.sources,
				new PropertySourcesPlaceholdersResolver(environment));
		Map<String, ExampleEnum> result = this.binder
				.bind("foo", Bindable.mapOf(String.class, ExampleEnum.class)).get();
		assertThat(result).containsEntry("aaa.bbb.ccc", ExampleEnum.BAZ_BOO);
	}

	@Test
	public void bindToMapWithNoPropertiesShouldReturnUnbound() throws Exception {
		this.binder = new Binder(this.sources);
		BindResult<Map<String, ExampleEnum>> result = this.binder.bind("foo",
				Bindable.mapOf(String.class, ExampleEnum.class));
		assertThat(result.isBound()).isFalse();
	}

	@Test
	public void bindToMapShouldTriggerOnSuccess() throws Exception {
		this.sources.add(new MockConfigurationPropertySource("foo.bar", "1", "line1"));
		BindHandler handler = mock(BindHandler.class,
				withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
		Bindable<Map<String, Integer>> target = STRING_INTEGER_MAP;
		this.binder.bind("foo", target, handler);
		InOrder inOrder = inOrder(handler);
		inOrder.verify(handler).onSuccess(eq(ConfigurationPropertyName.of("foo.bar")),
				eq(Bindable.of(Integer.class)), any(), eq(1));
		inOrder.verify(handler).onSuccess(eq(ConfigurationPropertyName.of("foo")),
				eq(target), any(), isA(Map.class));
	}

	@Test
	public void bindToMapStringArrayShouldTriggerOnSuccess() throws Exception {
		this.sources
				.add(new MockConfigurationPropertySource("foo.bar", "a,b,c", "line1"));
		BindHandler handler = mock(BindHandler.class,
				withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));
		Bindable<Map<String, String[]>> target = STRING_ARRAY_MAP;
		this.binder.bind("foo", target, handler);
		InOrder inOrder = inOrder(handler);
		ArgumentCaptor<String[]> array = ArgumentCaptor.forClass(String[].class);
		inOrder.verify(handler).onSuccess(eq(ConfigurationPropertyName.of("foo.bar")),
				eq(Bindable.of(String[].class)), any(), array.capture());
		assertThat(array.getValue()).containsExactly("a", "b", "c");
		inOrder.verify(handler).onSuccess(eq(ConfigurationPropertyName.of("foo")),
				eq(target), any(), isA(Map.class));
	}

}
