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

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitManager {
    private final static Map<Path, Git> PATH2GIT = new HashMap<>();

    public synchronized static Git git(Path p) {
        return PATH2GIT.computeIfAbsent(makeKey(p), GitManager::find);
    }

    private synchronized static Git find(Path path) {
        try {
            Repository repo = new FileRepositoryBuilder()
                    .findGitDir(path.toFile())
                    .readEnvironment()
                    .build();
            Path key = makeKey(repo.getDirectory().toPath());
            Git  git = PATH2GIT.get(key);
            if (git == null) {
                git = new Git(repo);
            } else {
                repo.close();
            }
            return git;
        } catch (IOException e) {
            throw new Error("could not find git repo at " + path, e);
        }
    }

    private static Path makeKey(Path p) {
        return p.normalize().toAbsolutePath();
    }
}
