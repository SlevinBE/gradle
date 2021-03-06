/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.Ivy;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactDependencyResolver;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.dependencies.AbstractDependency;
import org.gradle.api.specs.Specs;
import org.gradle.util.JUnit4GroovyMockery;
import org.hamcrest.Description;
import org.jmock.Expectations;
import org.jmock.api.Action;
import org.jmock.api.Invocation;
import org.jmock.integration.junit4.JMock;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;

import static java.util.Collections.emptyList;
import static org.gradle.util.WrapUtil.toLinkedSet;
import static org.gradle.util.WrapUtil.toSet;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(JMock.class)
public class SelfResolvingDependencyResolverTest {
    private final JUnit4Mockery context = new JUnit4GroovyMockery();
    private final ArtifactDependencyResolver delegate = context.mock(ArtifactDependencyResolver.class);
    private final ResolvedConfiguration resolvedConfiguration = context.mock(ResolvedConfiguration.class);
    private final ConfigurationInternal configuration = context.mock(ConfigurationInternal.class);
    private final Ivy ivy = Ivy.newInstance();
    private final DependencySet dependencies = context.mock(DependencySet.class);

    private final SelfResolvingDependencyResolver resolver = new SelfResolvingDependencyResolver(delegate);

    @Before
    public void setup() {
        context.checking(new Expectations() {{
            allowing(configuration).getAllDependencies();
            will(returnValue(dependencies));
        }});
    }

    @Test
    public void wrapsResolvedConfigurationProvidedByDelegate() {
        context.checking(new Expectations() {{
            one(delegate).resolve(configuration);
            will(returnValue(resolvedConfiguration));
            allowing(dependencies).iterator();
            will(returnIterator(emptyList()));
            allowing(configuration).isTransitive();
            will(returnValue(true));
        }});

        ResolvedConfiguration configuration = resolver.resolve(this.configuration);
        assertThat(configuration, not(sameInstance(resolvedConfiguration)));

        final File file = new File("file");

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getFiles(Specs.SATISFIES_ALL);
            will(returnValue(toSet(file)));
        }});

        assertThat(configuration.getFiles(Specs.SATISFIES_ALL), equalTo(toLinkedSet(file)));
    }
    
    @Test
    public void addsFilesFromSelfResolvingDependenciesBeforeFilesFromResolvedConfiguration() {
        final AbstractDependency dependency = context.mock(AbstractDependency.class);

        context.checking(new Expectations() {{
            one(delegate).resolve(configuration);
            will(returnValue(resolvedConfiguration));
            allowing(dependencies).iterator();
            will(returnIterator(dependency));
            allowing(configuration).isTransitive();
            will(returnValue(true));
        }});

        ResolvedConfiguration actualResolvedConfiguration = resolver.resolve(this.configuration);
        assertThat(actualResolvedConfiguration, not(sameInstance(resolvedConfiguration)));

        final File configFile = new File("from config");
        final File depFile = new File("from dep");
        final FileCollection depFiles = context.mock(FileCollection.class);

        final boolean transitive = true;
        context.checking(new Expectations() {{
            allowing(configuration);
            will(returnValue(transitive));
            one(resolvedConfiguration).getFiles(Specs.SATISFIES_ALL);
            will(returnValue(toSet(configFile)));
            one(dependency).resolve(with(notNullValue(DependencyResolveContext.class)));
            will(new Action() {
                public void describeTo(Description description) {
                    description.appendText("add files to context");
                }

                public Object invoke(Invocation invocation) throws Throwable {
                    ((DependencyResolveContext) invocation.getParameter(0)).add(depFiles);
                    return null;
                }
            });
            allowing(depFiles).getFiles();
            will(returnValue(toSet(depFile)));
        }});

        assertThat(actualResolvedConfiguration.getFiles(Specs.SATISFIES_ALL), equalTo(toLinkedSet(depFile, configFile)));
    }

    @Test
    public void testGetModuleDependencies() throws IOException, ParseException {
        context.checking(new Expectations() {{
            one(delegate).resolve(configuration);
            will(returnValue(resolvedConfiguration));
            allowing(configuration).isTransitive();
            will(returnValue(true));
        }});

        final ResolvedDependency resolvedDependency = context.mock(ResolvedDependency.class);

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getFirstLevelModuleDependencies();
            will(returnValue(toSet(resolvedDependency)));
        }});

        assertThat(resolver.resolve(this.configuration).getFirstLevelModuleDependencies(),
                equalTo(toSet(resolvedDependency)));
    }

    @Test
    public void testGetResolvedArtifacts() {
        context.checking(new Expectations() {{
            one(delegate).resolve(configuration);
            will(returnValue(resolvedConfiguration));
            allowing(configuration).isTransitive();
            will(returnValue(true));
        }});

        final ResolvedArtifact resolvedArtifact = context.mock(ResolvedArtifact.class);

        context.checking(new Expectations() {{
            one(resolvedConfiguration).getResolvedArtifacts();
            will(returnValue(toSet(resolvedArtifact)));
        }});

        assertThat(resolver.resolve(this.configuration).getResolvedArtifacts(),
                equalTo(toSet(resolvedArtifact)));
    }

}
