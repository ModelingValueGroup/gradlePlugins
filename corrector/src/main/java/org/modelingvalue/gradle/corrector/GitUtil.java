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

import java.nio.file.Path;
import java.util.Set;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.PushResult;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class GitUtil {
    private final static Logger              LOGGER = Logging.getLogger(MvgCorrectorPluginExtension.NAME);
    private final static TextProgressMonitor MON    = new TextProgressMonitor();
    private final        Path                root;

    public GitUtil(Path root) {
        this.root = root;
    }

    public void pushChanges(Set<Path> changes) {
        try {
            LOGGER.info("pushing " + changes.size() + " files from " + root.toAbsolutePath());

            try (Repository repository = new FileRepositoryBuilder()
                    .findGitDir(root.toFile())
                    .readEnvironment()
                    .build();
                 Git git = new Git(repository)) {
                String branch = repository.getBranch();

                System.err.println("@@@@@@@@@@ " + branch);

                AddCommand add = git.add();
                changes.forEach(f -> add.addFilepattern(f.toString()));
                DirCache dc = add.call();

                Iterable<PushResult> result = git.push().setProgressMonitor(MON).setDryRun(true).call();
                result.forEach(pr -> {
                    System.err.println("@@@@====");
                    pr.getRemoteUpdates().forEach(x -> System.err.println("@@@@ru   - " + x));
                    pr.getTrackingRefUpdates().forEach(x -> System.err.println("@@@@tu   - " + x));
                });

                System.err.println();

                //                Status status = git.status().call();
                //
                //                Set<String> add = status.getAdded();
                //                add.forEach(x -> System.err.println("@@add@@" + x));
                //                System.err.println();
                //
                //                Set<String> chg = status.getChanged();
                //                chg.forEach(x -> System.err.println("@@chg@@" + x));
                //                System.err.println();
                //
                //                Set<String> con = status.getConflicting();
                //                con.forEach(x -> System.err.println("@@con@@" + x));
                //                System.err.println();
                //
                //                Set<String> ign = status.getIgnoredNotInIndex();
                //                ign.forEach(x -> System.err.println("@@ign@@" + x));
                //                System.err.println();
                //
                //                Set<String> mis = status.getMissing();
                //                mis.forEach(x -> System.err.println("@@mis@@" + x));
                //                System.err.println();
                //
                //                Set<String> mod = status.getModified();
                //                mod.forEach(x -> System.err.println("@@mod@@" + x));
                //                System.err.println();
                //
                //                Set<String> rem = status.getRemoved();
                //                rem.forEach(x -> System.err.println("@@rem@@" + x));
                //                System.err.println();
                //
                //                Set<String> unc = status.getUncommittedChanges();
                //                unc.forEach(x -> System.err.println("@@unc@@" + x));
                //                System.err.println();
                //
                //                Set<String> unt = status.getUntracked();
                //                unt.forEach(x -> System.err.println("@@unt@@" + x));
                //                System.err.println();
                //
                //                Set<String> unf = status.getUntrackedFolders();
                //                unf.forEach(x -> System.err.println("@@unf@@" + x));
                //                System.err.println();

            }
            //            List<DiffEntry> diffEntries = git.diff().setPathFilter(new TreeFilter() {
            //                @Override
            //                public boolean include(TreeWalk walker) {
            //                    System.err.println("@@@@@@@@@@@@@@@@@@@@@@ " + walker.getNameString());
            //                    return true;
            //                }
            //
            //                @Override
            //                public boolean shouldBeRecursive() {
            //                    return false;
            //                }
            //
            //                @Override
            //                public TreeFilter clone() {
            //                    return this;
            //                }
            //
            //                @Override
            //                public String toString() {
            //                    return "ALL"; //$NON-NLS-1$
            //                }
            //            }).call();
            //            diffEntries.forEach(de -> System.err.println("@@@ " + de));
            //            DirCache cache = git.add().addFilepattern(".").call();
            //            System.err.println("@@@@@ lock   = " + cache.getEntryCount());
            //            boolean  lock  = cache.lock();
            //            System.err.println("@@@@@ lock   = " + lock);
            //            boolean commit = cache.commit();
            //            System.err.println("@@@@@ commit = " + commit);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
