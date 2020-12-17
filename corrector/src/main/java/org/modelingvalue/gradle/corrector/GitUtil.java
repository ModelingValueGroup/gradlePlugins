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
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class GitUtil {
    private final static Logger              LOGGER           = Logging.getLogger(MvgCorrectorPluginExtension.NAME);
    private final static TextProgressMonitor PROGRESS_MONITOR = new TextProgressMonitor();
    private final static CredentialsProvider CREDENTIALS_PROV = new UsernamePasswordCredentialsProvider(System.getenv("ALLREP_TOKEN"), "");
    public final static  PersonIdent         AUTOMATION_IDENT = new PersonIdent("automation", "automation@modelingvalue.org");

    private final Path root;

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

                System.err.println("@@@@@@@@@@ branch       : " + branch);
                System.err.println("@@@@@@@@@@ changes      : " + changes.size());
                changes.forEach(x -> System.err.println("@@@ c - " + x));

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                AddCommand add = git.add();
                changes.forEach(f -> add.addFilepattern(f.toString()));
                DirCache dc = add.call();
                for (int i = 0; i < dc.getEntryCount(); i++) {
                    System.err.println("@@@@@@@@@@ added        : " + dc.getEntry(i));
                }

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                RevCommit rc = git
                        .commit()
                        .setAuthor(AUTOMATION_IDENT)
                        .setCommitter(AUTOMATION_IDENT)
                        .setMessage("~changed by corrector")
                        .call();
                System.err.println("@@@@@@@@@@ commit result: " + rc);

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                Iterable<PushResult> result = git
                        .push()
                        .setCredentialsProvider(CREDENTIALS_PROV)
                        .setProgressMonitor(PROGRESS_MONITOR)
                        .call();
                result.forEach(pr -> {
                    System.err.println("@@@@@@@@@@ push result  :");
                    pr.getRemoteUpdates().forEach(x -> System.err.println("@@@@@@@@@@@@@@@@@@@@ remote update     - " + x));
                    pr.getTrackingRefUpdates().forEach(x -> System.err.println("@@@@@@@@@@@@@@@@@@@@ tracking update   - " + x));
                });
                ///////////////////////////////////////////////////////////////////////////////////////////////////
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
