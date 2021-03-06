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

package org.gradle.util;

import groovy.lang.GroovySystem;
import org.apache.ivy.Ivy;
import org.apache.tools.ant.Main;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.os.OperatingSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Hans Dockter
 * @author Russel Winder
 */
public class GradleVersion implements Comparable<GradleVersion> {
    public final static String URL = "http://www.gradle.org";
    private final static Pattern VERSION_PATTERN = Pattern.compile("(\\d+(\\.\\d+)+)(-(\\p{Alpha}+)-(\\d+[a-z]?))?(-(\\d{14}[-+]\\d{4}))?");

    private final String version;
    private final String buildTime;
    private final Long snapshot;
    private final String versionPart;
    private final Stage stage;
    private static final GradleVersion CURRENT;

    public static final String RESOURCE_NAME = "/org/gradle/releases.xml";

    static {
        URL resource = GradleVersion.class.getResource(RESOURCE_NAME);
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream inputStream = resource.openStream();
            try {
                document = builder.parse(inputStream);
            } finally {
                inputStream.close();
            }
            NodeList currentElements = document.getDocumentElement().getElementsByTagName("current");
            if (currentElements.getLength() != 1) {
                throw new GradleException(String.format("Expected to find 1 <current> element, found %s.", currentElements.getLength()));
            }
            Element currentRelease = (Element) currentElements.item(0);
            CURRENT = new GradleVersion(currentRelease.getAttribute("version"), new SimpleDateFormat("yyyyMMddHHmmssZ").parse(currentRelease.getAttribute("build-time")));
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load version details from resource '%s'.", resource), e);
        }
    }

    public static GradleVersion current() {
        return CURRENT;
    }

    public static GradleVersion version(String version) {
        return new GradleVersion(version, null);
    }

    private GradleVersion(String version, Date buildTime) {
        this.version = version;
        this.buildTime = buildTime == null ? null : formatBuildTime(buildTime);
        Matcher matcher = VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) {
            throw new InvalidUserDataException(String.format("Unexpected Gradle version '%s'.", version));
        }
        versionPart = matcher.group(1);

        if (matcher.group(3) != null) {
            int stageNumber = 0;
            if (matcher.group(4).equals("milestone")) {
                stageNumber = 1;
            } else if (matcher.group(4).equals("preview")) {
                stageNumber = 2;
            } else if (matcher.group(4).equals("rc")) {
                stageNumber = 3;
            }
            String stageString = matcher.group(5);
            stage = new Stage(stageNumber, stageString);
        } else {
            stage = null;
        }

        if (matcher.group(7) != null) {
            try {
                snapshot = new SimpleDateFormat("yyyyMMddHHmmssZ").parse(matcher.group(7)).getTime();
            } catch (ParseException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            snapshot = null;
        }
    }

    private String formatBuildTime(Date buildTime) {
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(buildTime);
    }

    @Override
    public String toString() {
        return String.format("Gradle %s", version);
    }

    public String getVersion() {
        return version;
    }

    public String getBuildTime() {
        return buildTime;
    }

    public boolean isSnapshot() {
        return snapshot != null;
    }

    public int compareTo(GradleVersion gradleVersion) {
        String[] majorVersionParts = versionPart.split("\\.");
        String[] otherMajorVersionParts = gradleVersion.versionPart.split("\\.");
        for (int i = 0; i < majorVersionParts.length && i < otherMajorVersionParts.length; i++) {
            int part = Integer.parseInt(majorVersionParts[i]);
            int otherPart = Integer.parseInt(otherMajorVersionParts[i]);
            if (part > otherPart) {
                return 1;
            }
            if (otherPart > part) {
                return -1;
            }
        }
        if (majorVersionParts.length > otherMajorVersionParts.length) {
            return 1;
        }
        if (majorVersionParts.length < otherMajorVersionParts.length) {
            return -1;
        }

        if (stage != null && gradleVersion.stage != null) {
            int diff = stage.compareTo(gradleVersion.stage);
            if (diff != 0) {
                return diff;
            }
        }
        if (stage == null && gradleVersion.stage != null) {
            return 1;
        }
        if (stage != null && gradleVersion.stage == null) {
            return -1;
        }

        if (snapshot != null && gradleVersion.snapshot != null) {
            return snapshot.compareTo(gradleVersion.snapshot);
        }
        if (snapshot == null && gradleVersion.snapshot != null) {
            return 1;
        }
        if (snapshot != null && gradleVersion.snapshot == null) {
            return -1;
        }

        return 0;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || o.getClass() != getClass()) {
            return false;
        }
        GradleVersion other = (GradleVersion) o;
        return version.equals(other.version);
    }

    @Override
    public int hashCode() {
        return version.hashCode();
    }

    public String prettyPrint() {
        final StringBuilder sb = new StringBuilder();
        sb.append("\n------------------------------------------------------------\nGradle ");
        sb.append(getVersion());
        sb.append("\n------------------------------------------------------------\n\nGradle build time: ");
        sb.append(getBuildTime());
        sb.append("\nGroovy: ");
        sb.append(GroovySystem.getVersion());
        sb.append("\nAnt: ");
        sb.append(Main.getAntVersion());
        sb.append("\nIvy: ");
        sb.append(Ivy.getIvyVersion());
        sb.append("\nJVM: ");
        sb.append(Jvm.current());
        sb.append("\nOS: ");
        sb.append(OperatingSystem.current());
        sb.append("\n");
        return sb.toString();
    }

    static final class Stage implements Comparable<Stage> {
        final int stage;
        final int number;
        final Character patchNo;

        Stage(int stage, String number) {
            this.stage = stage;
            Matcher m = Pattern.compile("(\\d+)([a-z])?").matcher(number);
            try {
                m.matches();
                this.number = Integer.parseInt(m.group(1));
            } catch (Exception e) {
                throw new RuntimeException("Invalid stage small number: " + number, e);
            }

            if (m.groupCount() == 2 && m.group(2) != null) {
                this.patchNo = m.group(2).charAt(0);
            } else {
                this.patchNo = '_';
            }
        }

        public int compareTo(Stage other) {
            if (stage > other.stage) {
                return 1;
            }
            if (stage < other.stage) {
                return -1;
            }
            if (number > other.number) {
                return 1;
            }
            if (number < other.number) {
                return -1;
            }
            if (patchNo > other.patchNo) {
                return 1;
            }
            if (patchNo < other.patchNo) {
                return -1;
            }
            return 0;
        }
    }
}
