//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2026 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.modelingvalue.gradle.mvgplugin.VersionCorrector.vacantVersion;

import java.util.List;

import org.junit.jupiter.api.Test;

public class VersionCorrectorTest {
    @Test
    public void nextPatchAfterHighestTag() {
        assertEquals("0.0.4", vacantVersion("0.0.1", List.of("v0.0.1", "v0.0.2", "v0.0.3")));
        // gaps below the highest tag do not matter
        assertEquals("0.9.22", vacantVersion("0.9.0", List.of("v0.9.2", "v0.9.10", "v0.9.21")));
        // the property being below the lowest tag does not matter either
        assertEquals("0.9.22", vacantVersion("0.0.1", List.of("v0.9.21")));
    }

    @Test
    public void versionLikeTagPrefixes() {
        assertEquals("1.2.4", vacantVersion("0.0.1", List.of("v1.2.3")));
        assertEquals("1.2.4", vacantVersion("0.0.1", List.of("V1.2.3")));
        assertEquals("1.2.4", vacantVersion("0.0.1", List.of("release1.2.3")));
        assertEquals("1.2.4", vacantVersion("0.0.1", List.of("1.2.3")));
        // non version-like tags are ignored
        assertEquals("0.0.1", vacantVersion("0.0.1", List.of("v1.2", "v1.2.3.4", "v1.2.3-rc1", "foo")));
    }

    @Test
    public void propertyAboveAllTagsStartsNewReleaseLine() {
        assertEquals("0.10.0", vacantVersion("0.10.0", List.of("v0.9.21")));
        assertEquals("1.0.0", vacantVersion("1.0.0", List.of("v0.9.21", "release0.9.1")));
        // but a property at or below the highest tag yields its patch successor
        assertEquals("0.9.22", vacantVersion("0.9.21", List.of("v0.9.21")));
    }

    @Test
    public void noTagsAtAll() {
        assertEquals("0.0.1", vacantVersion("0.0.1", List.of()));
        assertEquals("2.3.4", vacantVersion("2.3.4", List.of()));
    }
}
