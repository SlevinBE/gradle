/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.util

import org.gradle.internal.jvm.JavaHomeException
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.Jvm.AppleJvm
import org.junit.Rule
import spock.lang.Specification

class AppleJvmTest extends Specification {
    @Rule TemporaryFolder tmpDir = new TemporaryFolder()
    @Rule SetSystemProperties sysProp = new SetSystemProperties()
    OperatingSystem os = Mock(OperatingSystem)

    AppleJvm getJvm() {
        new AppleJvm(os)
    }

    def "looks for runtime Jar in Java home directory"() {
        TestFile javaHomeDir = tmpDir.createDir('Home')
        TestFile runtimeJar = javaHomeDir.parentFile.file('Classes/classes.jar').createFile()
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        jvm.javaHome == javaHomeDir
        jvm.runtimeJar == runtimeJar
    }

    def "looks for tools Jar in Java home directory"() {
        TestFile javaHomeDir = tmpDir.createDir('Home')
        TestFile toolsJar = javaHomeDir.parentFile.file('Classes/classes.jar').createFile()
        System.properties['java.home'] = javaHomeDir.absolutePath

        expect:
        jvm.javaHome == javaHomeDir
        jvm.toolsJar == toolsJar
    }

    def "filters environment variables"() {
        Map<String, String> env = ['APP_NAME_1234': 'App', 'JAVA_MAIN_CLASS_1234': 'MainClass', 'OTHER': 'value']

        expect:
        jvm.getInheritableEnvironmentVariables(env) == ['OTHER': 'value']
    }

    def "finds executable if java home supplied"() {
        when:
        def home = tmpDir.createDir("home")
        home.create {
            bin { file 'java' }
        }
        os.getExecutableName(_ as String) >> home.file('bin/java').absolutePath

        AppleJvm jvm = new AppleJvm(os, home)

        then:
        home.file('bin/java').absolutePath == jvm.getExecutable('java').absolutePath
    }

    def "provides decent feedback when executable not found"() {
        given:
        def home = tmpDir.createDir("home")
        home.create {
            bin { file 'java' }
        }
        os.getExecutableName({it.contains('java')}) >> home.file('bin/java').absolutePath
        os.getExecutableName({it.contains('foobar')}) >> home.file('bin/foobar').absolutePath

        AppleJvm jvm = new AppleJvm(os, home)

        when:
        jvm.getExecutable('foobar')
        
        then:
        def ex = thrown(JavaHomeException)
        ex.message.contains('foobar')
    }
}
