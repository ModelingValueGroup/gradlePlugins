//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2025 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
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

package org.modelingvalue.gradle.mvgplugin;

import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

class MavenMetaVersionExtractor extends DefaultHandler {
    private static final String        LATEST_ELEMENT_NAME  = "latest";
    private static final String        VERSION_ELEMENT_NAME = "version";
    //
    private              Version       latest;
    private final        Set<Version>  versions             = new HashSet<>();
    private              Exception     exception;
    //
    private              boolean       inLatest;
    private              boolean       inVersion;
    private final        StringBuilder b                    = new StringBuilder();

    public MavenMetaVersionExtractor(String url) {
        try {
            SAXParserFactory.newInstance().newSAXParser().parse(url, this);
        } catch (Exception e) {
            exception = e;
        }
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        inLatest = qName.equals(LATEST_ELEMENT_NAME);
        inVersion = qName.equals(VERSION_ELEMENT_NAME);
    }

    public void endElement(String uri, String localName, String qName) {
        if (inLatest || inVersion) {
            String s = b.toString();
            b.setLength(0);
            if (inLatest) {
                inLatest = false;
                latest = new Version(s);
            }
            if (inVersion) {
                inVersion = false;
                versions.add(new Version(s));
            }
        }
    }

    public void characters(char[] ch, int start, int length) {
        if (inLatest || inVersion) {
            b.append(ch, start, length);
        }
    }

    public boolean error() {
        return exception != null;
    }

    public Version getLatest() {
        return latest;
    }

    public Set<Version> getVersions() {
        return versions;
    }

    public Exception getException() {
        return exception;
    }
}
