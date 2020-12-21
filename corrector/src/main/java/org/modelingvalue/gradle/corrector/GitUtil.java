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

import static org.modelingvalue.gradle.corrector.Info.ALLREP_TOKEN;
import static org.modelingvalue.gradle.corrector.Info.LOGGER;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
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
    public final static  String              NO_CI_MESSAGE    = "[no-ci]";
    private final static TextProgressMonitor PROGRESS_MONITOR = new TextProgressMonitor();
    private final static CredentialsProvider CREDENTIALS_PROV = new UsernamePasswordCredentialsProvider(ALLREP_TOKEN, "");
    private final static PersonIdent         AUTOMATION_IDENT = new PersonIdent("automation", "automation@modelingvalue.org");
    private final static boolean             DRY_RUN          = "DRY".equals(ALLREP_TOKEN);

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static List<String> getAllTags(Path root) {
        return calcWithGit(root, GitUtil::doListTags);
    }

    public static String getBranch(Path root) {
        return calcWithGit(root, git -> git.getRepository().getBranch());
    }

    public static void push(Path root, Set<Path> changes, String message) {
        calcWithGit(root, git -> {
            doAdd(git, changes);
            doCommit(git, message);
            doPush(git);
            return null;
        });
    }

    public static void tag(Path root, String tag) {
        calcWithGit(root, git -> {
            doTag(git, tag);
            doPush(git);
            return null;
        });
    }

    public static void untag(Path root, String... tags) {
        calcWithGit(root, git -> {
            doDeleteTag(git, tags);
            doPush(git);
            return null;
        });
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    @FunctionalInterface
    public interface Function<T, R> {
        R apply(T t) throws GitAPIException, IOException;
    }

    public static <T> T calcWithGit(Path root, Function<Git, T> f) {
        try {
            try (Repository repository = new FileRepositoryBuilder()
                    .findGitDir(root.toFile())
                    .readEnvironment()
                    .build();
                 Git git = new Git(repository)) {
                return f.apply(git);
            }
        } catch (TransportException e) {
            if (!DRY_RUN || !e.getMessage().contains("not authorized")) {
                throw new GradleException("git push failed: " + e.getMessage(), e);
            }
            return null;
        } catch (Throwable e) {
            throw new GradleException("git tag failed: " + e.getMessage(), e);
        }
    }

    private static void doAdd(Git git, Set<Path> changes) throws GitAPIException, IOException {
        Path   root   = git.getRepository().getDirectory().toPath().toAbsolutePath();
        String branch = git.getRepository().getBranch();
        LOGGER.info("add {} files under {} to branch '{}'", changes.size(), root, branch);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.trace("added files:");
            changes.forEach(x -> LOGGER.trace(" - {}", x));
        }
        AddCommand add = git.add();
        changes.forEach(f -> add.addFilepattern(f.toString()));
        add.call();
    }

    private static void doCommit(Git git, String message) throws GitAPIException {
        LOGGER.info("commit with message '{}'", message);
        RevCommit rc = git
                .commit()
                .setAuthor(AUTOMATION_IDENT)
                .setCommitter(AUTOMATION_IDENT)
                .setMessage(message)
                .call();
        LOGGER.info("commit result: {}", rc);
    }

    private static List<String> doListTags(Git git) throws GitAPIException {
        return git.tagList()
                .call()
                .stream()
                .map(ref -> ref.getName().replaceAll("^refs/tags/", ""))
                .collect(Collectors.toList());
    }

    private static void doTag(Git git, String tag) throws GitAPIException {
        LOGGER.info("tagging with '{}'", tag);
        Ref ref = git.tag()
                .setName(tag)
                .setForceUpdate(true)
                .call();
        LOGGER.info("added tag '{}', id={}", tag, ref.getObjectId());
    }

    private static void doDeleteTag(Git git, String... tags) throws GitAPIException {
        LOGGER.info("delete tags: {}", Arrays.asList(tags));
        List<String> l = git.tagDelete()
                .setTags(tags)
                .call();
        LOGGER.info("deleted tags {}", l);
    }

    private static void doPush(Git git) throws GitAPIException {
        LOGGER.info("{}push", DRY_RUN ? "[dry] " : "");
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
    }
}
