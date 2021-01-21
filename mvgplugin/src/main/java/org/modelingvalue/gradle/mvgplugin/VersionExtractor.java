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

import java.util.HashSet;
import java.util.Set;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

class VersionExtractor extends DefaultHandler {
    private       String        latest;
    private final Set<String>   versions = new HashSet<>();
    private       Exception     exception;
    //
    private       boolean       inLatest;
    private       boolean       inVersion;
    private final StringBuilder b        = new StringBuilder();

    public VersionExtractor(String url) {
        try {
            SAXParserFactory.newInstance().newSAXParser().parse(url, this);
        } catch (Exception e) {
            exception = e;
        }
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        inLatest = qName.equals("latest");
        inVersion = qName.equals("version");
    }

    public void endElement(String uri, String localName, String qName) {
        if (inLatest || inVersion) {
            String s = b.toString();
            b.setLength(0);
            if (inLatest) {
                inLatest = false;
                latest = s;
            }
            if (inVersion) {
                inVersion = false;
                versions.add(s);
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

    public String getLatest() {
        return latest;
    }

    public Set<String> getVersions() {
        return versions;
    }

    public Exception getException() {
        return exception;
    }
}
