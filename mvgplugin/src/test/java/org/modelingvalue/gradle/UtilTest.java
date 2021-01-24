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

package org.modelingvalue.gradle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.modelingvalue.gradle.mvgplugin.Util.toBytes;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class UtilTest {
    @Test
    public void toBytesTest() {
        assertEquals(0L, toBytes("0"));
        assertEquals(12L, toBytes("12"));
        assertEquals(123456789L, toBytes("123456789"));
        assertEquals(1024L, toBytes("1k"));
        assertEquals(1024L, toBytes("1K"));
        assertEquals(88L * 1024 * 1024, toBytes("88M"));
        assertEquals(33L * 1024 * 1024 * 1024, toBytes("33g"));
        assertEquals(44L * 1024 * 1024 * 1024 * 1024, toBytes("44t"));

        Assertions.assertThrows(NullPointerException.class,()-> toBytes(null));
        Assertions.assertThrows(GradleException.class,()-> toBytes(""));
        Assertions.assertThrows(GradleException.class,()-> toBytes("k"));
        Assertions.assertThrows(GradleException.class,()-> toBytes("-10"));
        Assertions.assertThrows(GradleException.class,()-> toBytes(" "));
    }
}