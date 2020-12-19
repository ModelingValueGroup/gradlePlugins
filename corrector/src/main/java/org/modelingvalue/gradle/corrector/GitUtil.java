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
import static org.modelingvalue.gradle.corrector.Info.TOKEN;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.gradle.api.GradleException;

public class GitUtil {
    private final static TextProgressMonitor PROGRESS_MONITOR = new TextProgressMonitor();
    private final static CredentialsProvider CREDENTIALS_PROV = new UsernamePasswordCredentialsProvider(TOKEN, "");
    private final static PersonIdent         AUTOMATION_IDENT = new PersonIdent("automation", "automation@modelingvalue.org");
    private final static boolean             DRY_RUN          = "DRY".equals(TOKEN);

    public static void push(Path root, Set<Path> changes) {
        try {
            try (Repository repository = new FileRepositoryBuilder()
                    .findGitDir(root.toFile())
                    .readEnvironment()
                    .build();
                 Git git = new Git(repository)) {

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                LOGGER.info("pushing {} files under {} to branch '{}'{}", changes.size(), root.toAbsolutePath(), repository.getBranch(), DRY_RUN ? " (dry)" : "");
                if (LOGGER.isTraceEnabled()) {
                    LOGGER.trace("changed files:");
                    changes.forEach(x -> LOGGER.trace(" - {}", x));
                }

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                AddCommand add = git.add();
                changes.forEach(f -> add.addFilepattern(f.toString()));
                add.call();

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                RevCommit rc = git
                        .commit()
                        .setAuthor(AUTOMATION_IDENT)
                        .setCommitter(AUTOMATION_IDENT)
                        .setMessage("~changed by corrector")
                        .call();
                LOGGER.info("commit result: {}", rc);

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                Iterable<PushResult> result = git
                        .push()
                        .setDryRun(DRY_RUN)
                        .setCredentialsProvider(CREDENTIALS_PROV)
                        .setProgressMonitor(PROGRESS_MONITOR)
                        .call();
                if (LOGGER.isInfoEnabled()) {
                    result.forEach(pr -> {
                        LOGGER.info("push result  : {}", pr.getMessages());
                        pr.getRemoteUpdates().forEach(x -> LOGGER.info("remote update     - {}", x));
                        pr.getTrackingRefUpdates().forEach(x -> LOGGER.info("tracking update   - {}", x));
                    });
                }
                ///////////////////////////////////////////////////////////////////////////////////////////////////
            }
        } catch (TransportException e) {
            if (!DRY_RUN || !e.getMessage().contains("not authorized")) {
                throw new GradleException("push to git failed: " + e.getMessage(), e);
            }
        } catch (Throwable e) {
            throw new GradleException("push to git failed: " + e.getMessage(), e);
        }
    }

    public static List<String> getAllTags(Path root) {
        try {
            try (Repository repository = new FileRepositoryBuilder()
                    .findGitDir(root.toFile())
                    .readEnvironment()
                    .build();
                 Git git = new Git(repository)) {

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                return git.tagList()
                        .call()
                        .stream()
                        .map(ref -> ref.getName().replaceAll("^refs/tags/", ""))
                        .collect(Collectors.toList());
                ///////////////////////////////////////////////////////////////////////////////////////////////////
            }
        } catch (Throwable e) {
            throw new GradleException("push to git failed: " + e.getMessage(), e);
        }
    }

    public static void tag(Path root,String tag) {
        try {
            try (Repository repository = new FileRepositoryBuilder()
                    .findGitDir(root.toFile())
                    .readEnvironment()
                    .build();
                 Git git = new Git(repository)) {

                ///////////////////////////////////////////////////////////////////////////////////////////////////
                Ref ref = git.tag()
                        .setName(tag)
                        .setForceUpdate(true)
                        .call();
                LOGGER.info("added tag '{}', id={}", tag, ref.getObjectId());

                // Pushing the commit and tag
                Iterable<PushResult> result = git.push()
                        .setPushTags()
                        .setDryRun(DRY_RUN)
                        .setCredentialsProvider(CREDENTIALS_PROV)
                        .setProgressMonitor(PROGRESS_MONITOR)
                        .call();
                if (LOGGER.isInfoEnabled()) {
                    result.forEach(pr -> {
                        LOGGER.info("push result  : {}", pr.getMessages());
                        pr.getRemoteUpdates().forEach(x -> LOGGER.info("remote update     - {}", x));
                        pr.getTrackingRefUpdates().forEach(x -> LOGGER.info("tracking update   - {}", x));
                    });
                }
                ///////////////////////////////////////////////////////////////////////////////////////////////////
            }
        } catch (Throwable e) {
            throw new GradleException("push to git failed: " + e.getMessage(), e);
        }
    }
}
