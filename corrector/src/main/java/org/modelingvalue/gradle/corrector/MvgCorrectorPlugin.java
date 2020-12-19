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

import static org.modelingvalue.gradle.corrector.Info.CORRECTOR_TASK_NAME;
import static org.modelingvalue.gradle.corrector.Info.LOGGER;
import static org.modelingvalue.gradle.corrector.Info.TAG_TASK_NAME;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

@SuppressWarnings("unused")
public class MvgCorrectorPlugin implements Plugin<Project> {
    public void apply(Project project) {
        CorrectorExtension ext1 = CorrectorExtension.make(project, CORRECTOR_TASK_NAME);
        project.getTasks().register(CORRECTOR_TASK_NAME, task -> correctorTaskSetup(project, task, ext1));

        TagExtension ext2 = TagExtension.make(project, TAG_TASK_NAME);
        project.getTasks().register(TAG_TASK_NAME, task -> tagTaskSetup(project, task, ext2));
    }

    private void correctorTaskSetup(Project project, Task task, CorrectorExtension extension) {
        task.setGroup("preparation");
        task.setDescription("correct various sources (version, headers, eols) and push to git");
        task.doLast(s -> {
            try {
                correctorTaskLogic(project, extension);
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

    private void correctorTaskLogic(Project project, CorrectorExtension extension) throws IOException {
        Set<Path> changes = new HashSet<>();

        changes.addAll(new HdrCorrector(extension).generate().getChangedFiles());
        changes.addAll(new EolCorrector(extension).generate().getChangedFiles());
        changes.addAll(new VerCorrector(extension).generate().getChangedFiles());

        LOGGER.info("changed {} files (CI={}, TOKEN={})", changes.size(), Info.CI, Info.TOKEN != null);

        if (!changes.isEmpty() && Info.CI && Info.TOKEN != null) {
            GitUtil.push(extension.getRoot(), changes);
            // since we pushed changes it is not needed to finish ths build
            // so we stop here in expectation that a new build will have been started
            LOGGER.quiet("!!! Some source-changes have been pushed to the repo.                              !!!");
            LOGGER.quiet("!!! They will trigger a new build.                                                 !!!");
            LOGGER.quiet("!!! We will force this build to fail now. This is ok, the next build will pick up. !!!");
            throw new GradleException("abandoned build run (this is a planned failure)");
        }
    }

    private void tagTaskSetup(Project project, Task task, TagExtension extension) {
        task.setGroup("wrap-up");
        task.setDescription("tag the git repo with the current version");
        task.doLast(s -> {
            try {
                tagTaskLogic(project, extension);
            } catch (IOException e) {
                throw new Error("could not correct files", e);
            }
        });
    }

    private void tagTaskLogic(Project project, TagExtension extension) throws IOException {
        GitUtil.tag(project.getRootDir().toPath(), "v" + project.getVersion());
    }
}
