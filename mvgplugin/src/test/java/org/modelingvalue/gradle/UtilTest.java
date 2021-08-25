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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.modelingvalue.gradle.mvgplugin.Util.toBytes;

import org.gradle.api.GradleException;
import org.junit.jupiter.api.Test;
import org.modelingvalue.gradle.mvgplugin.BranchParameterNames;
import org.modelingvalue.gradle.mvgplugin.Util;

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

        assertThrows(NullPointerException.class, () -> toBytes(null));
        assertThrows(GradleException.class, () -> toBytes(""));
        assertThrows(GradleException.class, () -> toBytes("k"));
        assertThrows(GradleException.class, () -> toBytes("-10"));
        assertThrows(GradleException.class, () -> toBytes(" "));
    }

    @Test
    public void branchParametersTest() {
        BranchParameterNames.init("no_parameters");
        assertEquals("default", BranchParameterNames.get("xyzzy", "default"));
        assertEquals("default", BranchParameterNames.get("arg", "default"));
        assertEquals("default", BranchParameterNames.get("zurch", "default"));

        BranchParameterNames.init("no_parameters@");
        assertEquals("default", BranchParameterNames.get("xyzzy", "default"));
        assertEquals("default", BranchParameterNames.get("arg", "default"));
        assertEquals("default", BranchParameterNames.get("zurch", "default"));

        BranchParameterNames.init("zurg@xyzzy=plugh");
        assertEquals("plugh", BranchParameterNames.get("xyzzy", "default"));
        assertEquals("default", BranchParameterNames.get("arg", "default"));
        assertEquals("default", BranchParameterNames.get("zurch", "default"));

        BranchParameterNames.init("zurg@xyzzy=plugh;arg=42");
        assertEquals("plugh", BranchParameterNames.get("xyzzy", "default"));
        assertEquals("42", BranchParameterNames.get("arg", "default"));
        assertEquals("default", BranchParameterNames.get("zurch", "default"));

        BranchParameterNames.init("zurg@xyzzy=plugh;;arg=42;;;");
        assertEquals("plugh", BranchParameterNames.get("xyzzy", "default"));
        assertEquals("42", BranchParameterNames.get("arg", "default"));
        assertEquals("default", BranchParameterNames.get("zurch", "default"));

        BranchParameterNames.init("zurg@xyzzy=plugh;;arg=42;;zurch;");
        assertEquals("plugh", BranchParameterNames.get("xyzzy", "default"));
        assertEquals("42", BranchParameterNames.get("arg", "default"));
        assertEquals("", BranchParameterNames.get("zurch", "default"));
    }

    @Test
    public void secretTest() {
        assertNull(Util.hide(null));
        assertEquals("", Util.hide(""));
        assertEquals("1", Util.hide("1"));
        assertEquals("12", Util.hide("12"));
        assertEquals("123", Util.hide("123"));
        assertEquals("1234", Util.hide("1234"));
        assertEquals("12345", Util.hide("12345"));
        assertEquals("123456", Util.hide("123456"));
        assertEquals("1234567", Util.hide("1234567"));
        assertEquals("123456**", Util.hide("12345678"));
        assertEquals("123456***", Util.hide("123456789"));
        assertEquals("123456****", Util.hide("1234567890"));
        assertEquals("!@#$%^***********************", Util.hide("!@#$%^&*!@#$%^&*({}+|\":<>?/.,"));
    }
}
