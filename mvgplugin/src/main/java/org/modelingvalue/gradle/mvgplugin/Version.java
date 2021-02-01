//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2021 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
//                                                                                                                     ~
// Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in      ~
// compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0  ~
// Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on ~
// an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the  ~
// specific language governing permissions and limitations under the License.                                          ~
//                                                                                                                     ~
// Maintainers:                                                                                                        ~
//     Wim Bast, Tom Brus, Ronald Krijgsheld                                                                           ~
// Contributors:                                                                                                       ~
//     Arjan Kok, Carel Bast                                                                                           ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import org.gradle.api.GradleException;

public class Version implements Comparable<Version> {
    public static final String VERSION_REGEX = "[0-9]+(\\.[0-9]+)*";
    //
    private final       String version;

    public final String get() {
        return this.version;
    }

    public Version(String version) {
        this.version = version;
    }

    public Version mustbe() {
        if (version == null) {
            throw new GradleException("Version can not be null");
        }
        if (!version.matches(VERSION_REGEX)) {
            throw new GradleException("Invalid version format");
        }
        return this;
    }

    public boolean valid() {
        return version != null && version.matches(VERSION_REGEX);
    }

    @Override
    public int compareTo(Version that) {
        mustbe();
        if (that == null) {
            return 1;
        } else {
            String[] thisParts = this.get().split("\\.");
            String[] thatParts = that.get().split("\\.");
            int      length    = Math.max(thisParts.length, thatParts.length);
            for (int i = 0; i < length; i++) {
                int thisPart = i < thisParts.length ? Integer.parseInt(thisParts[i]) : 0;
                int thatPart = i < thatParts.length ? Integer.parseInt(thatParts[i]) : 0;
                if (thisPart < thatPart) {
                    return -1;
                }
                if (thisPart > thatPart) {
                    return 1;
                }
            }
            return 0;
        }
    }

    @Override
    public boolean equals(Object that) {
        if (!valid()) {
            return false;
        }
        if (this == that) {
            return true;
        }
        if (that == null) {
            return false;
        }
        if (this.getClass() != that.getClass()) {
            return false;
        }
        Version thatVersion = (Version) that;
        if (!thatVersion.valid()) {
            return false;
        }
        return this.compareTo(thatVersion) == 0;
    }

    @Override
    public int hashCode() {
        return valid() ? version.hashCode() : 0;
    }

    @Override
    public String toString() {
        return version == null ? "<null>" : valid() ? version : "invalid:" + version;
    }
}
