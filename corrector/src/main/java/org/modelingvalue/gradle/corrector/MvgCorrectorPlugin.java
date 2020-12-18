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

import static org.modelingvalue.gradle.corrector.Info.LOGGER;
import static org.modelingvalue.gradle.corrector.Info.NAME;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.internal.extensibility.DefaultConvention;

@SuppressWarnings("unused")
public class MvgCorrectorPlugin implements Plugin<Project> {
    public void apply(Project project) {
        MvgCorrectorPluginExtension extension = makeExtension(project);
        project.getTasks().register(NAME, task -> taskSetup(project, extension, task));
    }

    private MvgCorrectorPluginExtension makeExtension(Project project) {
        return ((DefaultConvention) project.getExtensions()).create(NAME, MvgCorrectorPluginExtension.class, project);
    }

    private void taskSetup(Project project, MvgCorrectorPluginExtension extension, Task task) {
        task.setGroup("preparation");
        task.setDescription("correct all sources (headers, eols,...)");
        task.doLast(s -> {
            try {
                taskLogic(project, extension);
            } catch (IOException e) {
                throw new Error("could not correct files", e);
            }
        });
        project.getTasks().stream()
                .filter(t -> !t.getName().matches("(?i)clean"))
                .filter(t -> !("" + t.getGroup()).matches("(?i)(build setup|help)"))
                .filter(t -> t != task)
                .forEach(t -> t.dependsOn(task));
    }

    private void taskLogic(Project project, MvgCorrectorPluginExtension extension) throws IOException {
        Set<Path> changes = new HashSet<>();

        changes.addAll(new HdrCorrector(extension).generate().getChangedFiles());
        changes.addAll(new EolCorrector(extension).generate().getChangedFiles());
        changes.addAll(new VerCorrector(extension).generate().getChangedFiles());

        LOGGER.info("changed {} files (CI={}, TOKEN={})", changes.size(), Info.CI, Info.TOKEN != null);

        if (!changes.isEmpty() && Info.CI && Info.TOKEN != null) {
            GitUtil.push(extension.getRoot(), changes);
            // since we pushed changes it is not needed to finish ths build
            // so we stop here in expectation that a new build will have been started
            LOGGER.quiet("some source changes have been pushed to the repo; they will trigger a new build; we will now force this build to fail now.");
            throw new GradleException("abandoned build run");
        }
    }
}
