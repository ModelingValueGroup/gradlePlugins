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

import static org.modelingvalue.gradle.mvgplugin.Info.MVG_DEPENDENCIES_REPO;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_DEPENDENCIES_REPO_NAME;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.util.FileUtils;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.logging.Logger;

/**
 * keeps a repo with dependencies.
 * <p>
 * repo contents:
 * <p>
 * if the following dependencies exist:
 * <pre>
 * a.b.c.lib =&gt; aa.bb.cc.app
 * q.w.e.lib =&gt;
 * </pre>
 * then the following files are created:
 * <pre>
 * a/b/c/lib/aa.bb.cc.app = file with "TRIGGER_REPO=ModelingValueGroup/demo-app"
 * q/w/e/lib/aa.bb.cc.app = file with "TRIGGER_REPO=ModelingValueGroup/demo-app"
 * aa/bb/cc/app/a.b.c.lib = empty file
 * aa/bb/cc/app/q.w.e.lib = empty file
 * </pre>
 * <p>
 * all are properties files with:
 * <pre>
 * REPO=ModelingValueGroup/demo-app      # the repo where this package is build
 * </pre>
 */
public class DependencyRepoManager {
    public static final  Logger  LOGGER  = Info.LOGGER;
    private static final boolean TESTING = Util.envOrProp("TESTING", "false").equals("true");

    private final Gradle gradle;
    private final File   dependenciesRepoDir;
    private final Git    git;

    public DependencyRepoManager(Gradle gradle) {
        this.gradle = gradle;
        dependenciesRepoDir = new File(gradle.getRootProject().getBuildDir(), MVG_DEPENDENCIES_REPO_NAME).getAbsoluteFile();
        git = Info.CI || TESTING ? makeDepRepo(gradle, dependenciesRepoDir) : null;
    }

    private static Git makeDepRepo(Gradle gradle, File dependenciesRepoDir) {
        long t0 = System.currentTimeMillis();
        try {
            if (dependenciesRepoDir.isDirectory()) {
                // will normally not happen in CI, but can happen in testing
                FileUtils.delete(dependenciesRepoDir, FileUtils.RECURSIVE);
            }
            String branch = Info.getBranch(gradle);
            LOGGER.info("+ bbb: cloning dependencies repo {} branch {} in {}", MVG_DEPENDENCIES_REPO_NAME, branch, dependenciesRepoDir);
            Git git = Git.cloneRepository()
                    .setURI(MVG_DEPENDENCIES_REPO)
                    .setDirectory(dependenciesRepoDir)
                    .call();
            git
                    .checkout()
                    .setName(branch)
                    .setCreateBranch(true)
                    .call();
            return git;
        } catch (GitAPIException | IOException e) {
            LOGGER.error("+ bbb: problem with dependencies repo", e);
            return null;
        } finally {
            LOGGER.info("+ bbb: dependencies repo done ({} ms)", System.currentTimeMillis() - t0);
        }
    }

    void replaceDependencies(String pack, List<String> usedPackages) {
        clearExistingDependencies();
        writeDependencies(pack, usedPackages);
        pushDependenciesRepo();
    }

    private void clearExistingDependencies() {
        //TODO
    }

    private void writeDependencies(String pack, List<String> usedPackages) {
        //TODO
    }

    private void pushDependenciesRepo() {
        //TODO
    }

    private List<String> getDependencies() {
        //TODO
        return null;
    }

    private File makeDepFile(String user, String usedPackage) {
        return new File(dependenciesRepoDir, user.replace('.', '/') + '/' + usedPackage);
    }

    public void trace_branches() {
        if (git != null) {
            try {
                List<Ref> branches = git.branchList().setListMode(ListMode.ALL).call();
                branches.forEach(r -> LOGGER.info("TOMTOMTOM ############ {}  -  {}", r.getName(), r.getStorage()));
            } catch (GitAPIException e) {
                LOGGER.info("TOMTOMTOM unable to get branch list", e);
            }
        }
    }
}
