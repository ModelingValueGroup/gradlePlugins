//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
// (C) Copyright 2018-2022 Modeling Value Group B.V. (http://modelingvalue.org)                                        ~
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

import java.io.File;
import java.util.Set;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.DependencyResolveContext;
import org.gradle.api.internal.artifacts.ResolvableDependency;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.tasks.TaskDependency;
import org.jetbrains.annotations.Nullable;

public class MpsDependency implements SelfResolvingDependency, SelfResolvingDependencyInternal, ResolvableDependency, FileCollectionDependency {
    private final MvgMps         mvgMps;
    private final String         dep;
    private       FileCollection fileCollection;

    public MpsDependency(MvgMps mvgMps, String dep) {
        this.mvgMps = mvgMps;
        this.dep = dep;
    }

    @Override
    public FileCollection getFiles() {
        if (fileCollection == null || !fileCollection.getFiles().stream().allMatch(File::isFile)) {
            return fileCollection = mvgMps.getFiles(dep);
        } else {
            return fileCollection;
        }
    }

    @Nullable
    @Override
    public ComponentIdentifier getTargetComponentId() {
        return null;
    }

    @Override
    public Set<File> resolve() {
        throw new IllegalArgumentException("MpsDependency.resolve() should not be called");
    }

    @Override
    public Set<File> resolve(boolean transitive) {
        throw new IllegalArgumentException("MpsDependency.resolve() should not be called");
    }

    @Override
    public TaskDependency getBuildDependencies() {
        throw new IllegalArgumentException("MpsDependency.getBuildDependencies() should not be called");
    }

    @Nullable
    @Override
    public String getGroup() {
        throw new IllegalArgumentException("MpsDependency.getGroup() should not be called");
    }

    @Override
    public String getName() {
        throw new IllegalArgumentException("MpsDependency.getName() should not be called");
    }

    @Nullable
    @Override
    public String getVersion() {
        throw new IllegalArgumentException("MpsDependency.getVersion() should not be called");
    }

    @Override
    public boolean contentEquals(Dependency dependency) {
        throw new IllegalArgumentException("MpsDependency.contentEquals() should not be called");
    }

    @Override
    public Dependency copy() {
        throw new IllegalArgumentException("MpsDependency.copy() should not be called");
    }

    @Nullable
    @Override
    public String getReason() {
        throw new IllegalArgumentException("MpsDependency.getReason() should not be called");
    }

    @Override
    public void because(@Nullable String reason) {
        throw new IllegalArgumentException("MpsDependency.because() should not be called");
    }

    @Override
    public void resolve(DependencyResolveContext context) {
        throw new IllegalArgumentException("MpsDependency.resolve() should not be called");
    }
}
