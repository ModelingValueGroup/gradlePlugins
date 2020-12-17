//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2020 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

package org.modelingvalue.gradle.corrector;

import static org.modelingvalue.gradle.corrector.MvgCorrectorPluginExtension.NAME;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.internal.extensibility.DefaultConvention;

@SuppressWarnings("unused")
public class MvgCorrectorPlugin implements Plugin<Project> {
    public void apply(Project project) {
        MvgCorrectorPluginExtension extension = ((DefaultConvention) project.getExtensions()).create(NAME, MvgCorrectorPluginExtension.class, project);

        project.getTasks().register(NAME, task -> task.doLast(s -> {
            try {
                task(project, extension);
            } catch (IOException e) {
                throw new Error("could not correct files", e);
            }
        }));
    }

    private void task(Project project, MvgCorrectorPluginExtension extension) throws IOException {
        Path      root    = project.getRootDir().toPath();
        Set<Path> changes = new HashSet<>();

        changes.addAll(new HdrCorrector(extension).generate().getChangedFiles(root));
        changes.addAll(new EolCorrector(extension).generate().getChangedFiles(root));

        if (!changes.isEmpty() && Boolean.parseBoolean(System.getenv("CI"))) {
            new GitUtil(root).pushChanges(changes);
        }
    }
}
