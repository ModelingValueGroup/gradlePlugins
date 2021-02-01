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

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.nio.file.Path;
import java.util.Stack;

import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

class AntFileMpsVersionsExtractor extends DefaultHandler {
    private static final String        PROJECT_ELEMENT_NAME = "project";
    //
    private final        Path          file;
    private              boolean       isAntFile;
    private              Exception     exception;
    private              boolean       inProject;
    private final        Stack<String> curentElementPath    = new Stack<>();
    private              Version       since;
    private              Version       until;

    public AntFileMpsVersionsExtractor(Path f) {
        file = f;
        try {
            SAXParserFactory.newInstance().newSAXParser().parse(f.toFile(), this);
            isAntFile = true;
        } catch (NoAntFile e) {
            isAntFile = false;
        } catch (Exception e) {
            isAntFile = false;
            exception = e;
        }
    }

    private static class NoAntFile extends RuntimeException {
    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) {
        if (!inProject) {
            inProject = qName.equals(PROJECT_ELEMENT_NAME);
            if (!inProject) {
                throw new NoAntFile();
            }
            curentElementPath.clear();
        }
        curentElementPath.push(qName);

        String fullPath = String.join(".", curentElementPath);
        if (fullPath.endsWith(".idea-plugin.idea-version")) {

            Version newSince = new Version(attributes.getValue("since-build"));
            if (newSince.valid()) {
                if (since != null && !newSince.equals(since)) {
                    LOGGER.error("MPS ant file contains conflicting 'since' values: {} != {}", newSince, since);
                }
                since = newSince;
            }

            Version newUntil = new Version(attributes.getValue("until-build"));
            if (newUntil.valid()) {
                if (until != null && !newUntil.equals(until)) {
                    LOGGER.error("MPS ant file contains conflicting 'until' values: {} != {}", newUntil, until);
                }
                until = newUntil;
            }
        }
    }

    public void endElement(String uri, String localName, String qName) {
        curentElementPath.pop();
    }

    public boolean error() {
        return exception != null;
    }

    public Exception getException() {
        return exception;
    }

    public boolean isAntFile() {
        return isAntFile;
    }

    public boolean hasRange() {
        return since != null || until != null;
    }

    public Path getFile() {
        return file;
    }

    public Version getSince() {
        return since;
    }

    public Version getUntil() {
        return until;
    }

    @Override
    public String toString() {
        return isAntFile ? file + ": [" + since + "..." + until + "]" : file + ": NO-ANT-FILE";
    }
}
