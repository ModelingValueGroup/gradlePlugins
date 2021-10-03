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

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
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
    private final static CredentialsProvider CREDENTIALS_PROV = new UsernamePasswordCredentialsProvider(Info.ALLREP_TOKEN, "");
    private final static PersonIdent         AUTOMATION_IDENT = new PersonIdent("automation", "automation@modelingvalue.org");
    private final static boolean             DRY_RUN          = "DRY".equals(Info.ALLREP_TOKEN);

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static PersonIdent getAutomationIdent() {
        return AUTOMATION_IDENT;
    }

    public static CredentialsProvider getCredentialProvider() {
        return CREDENTIALS_PROV;
    }

    public static List<String> getAllTags(Path root) {
        return calcWithGit(root, GitUtil::doListTags);
    }

    public static String getBranch(Path root) {
        return calcWithGit(root, git -> git.getRepository().getBranch());
    }

    public static void addCommitPush(Path root, String message) {
        calcWithGit(root, git -> {
            addCommitPush(git, message);
            return null;
        });
    }

    public static void tag(Path root, String tag) {
        calcWithGit(root, git -> {
            tag(git, tag);
            return null;
        });
    }

    public static void untag(Path root, String... tags) {
        calcWithGit(root, git -> {
            untag(git, tags);
            return null;
        });
    }

    public static void addCommitPush(Git git, String message) throws GitAPIException, IOException {
        if (doAdd(git)) {
            doCommit(git, message);
            doPush(git);
        }
    }

    public static Status statusVerbose(Git git, String traceMessage) throws GitAPIException {
        Status status = git.status().call();

        LOGGER.debug("    ##### git status @{}", traceMessage);
        traceStatusClass(status.getAdded(), "added");
        traceStatusClass(status.getChanged(), "changed");
        traceStatusClass(status.getModified(), "modified");
        traceStatusClass(status.getRemoved(), "removed");
        traceStatusClass(status.getMissing(), "missing");
        traceStatusClass(status.getUncommittedChanges(), "uncommited");
        traceStatusClass(status.getUntracked(), "untracked");
        traceStatusClass(status.getUntrackedFolders(), "untrackedFolders");

        return status;
    }

    public static void tag(Git git, String tag) throws GitAPIException {
        doTag(git, tag);
        doPushTags(git);
    }

    public static void untag(Git git, String[] tags) throws GitAPIException {
        doDeleteTag(git, tags);
        doPushTags(git);
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

    private static boolean doAdd(Git git) throws GitAPIException, IOException {
        Path   root   = git.getRepository().getDirectory().toPath().toAbsolutePath();
        String branch = git.getRepository().getBranch();
        Status status = statusVerbose(git, "before add");

        int modifications = status.getModified().size();
        int creations     = status.getUntracked().size();
        int deletions     = status.getMissing().size();

        boolean nothing = modifications + creations + deletions == 0;
        if (nothing) {
            LOGGER.info("+ git: staging changes (nothing to stage; branch={} dir={})", branch, root);
        } else {
            LOGGER.info("+ git: staging changes (mod={} adds={} del={}; branch={} dir={})", modifications, creations, deletions, branch, root);

            if (0 < modifications + creations) {
                AddCommand addCommand = git.add();
                for (String s : status.getModified()) {
                    addCommand.addFilepattern(s);
                }
                for (String s : status.getUntracked()) {
                    addCommand.addFilepattern(s);
                }
                addCommand.call();
            }
            if (0 < deletions) {
                RmCommand remCommand = git.rm();
                for (String s : status.getMissing()) {
                    remCommand.addFilepattern(s);
                }
                remCommand.call();
            }
        }
        return !nothing;
    }

    private static void doCommit(Git git, String message) throws GitAPIException {
        LOGGER.info("+ git: commit (message='{}')", message);
        RevCommit rc = git
                .commit()
                .setAuthor(AUTOMATION_IDENT)
                .setCommitter(AUTOMATION_IDENT)
                .setMessage(message)
                .call();
        LOGGER.info("+ git: commit (result={})", rc);
    }

    private static List<String> doListTags(Git git) throws GitAPIException {
        return git.tagList()
                .call()
                .stream()
                .map(ref -> ref.getName().replaceAll("^refs/tags/", ""))
                .collect(Collectors.toList());
    }

    private static void doTag(Git git, String tag) throws GitAPIException {
        LOGGER.info("+ git: tagging with '{}'", tag);
        Ref ref = git.tag()
                .setName(tag)
                .setForceUpdate(true)
                .call();
        LOGGER.info("+ git: added tag '{}', result id={}", tag, ref.getObjectId());
    }

    private static void doDeleteTag(Git git, String... tags) throws GitAPIException {
        LOGGER.info("+ git: deleting tags: {}", Arrays.asList(tags));
        List<String> l = git.tagDelete()
                .setTags(tags)
                .call();
        LOGGER.info("+ git: deleting tags result={}", l);
    }

    private static void doPush(Git git) throws GitAPIException {
        doPush(git, false);
    }

    private static void doPushTags(Git git) throws GitAPIException {
        doPush(git, true);
    }

    private static void doPush(Git git, boolean withTags) throws GitAPIException {
        LOGGER.info("+ git: {}push", DRY_RUN ? "[dry] " : "");
        Iterable<PushResult> result =
                (withTags ? git.push().setPushTags() : git.push())
                        .setDryRun(DRY_RUN)
                        .setCredentialsProvider(CREDENTIALS_PROV)
                        .setProgressMonitor(PROGRESS_MONITOR)
                        .call();
        if (LOGGER.isInfoEnabled()) {
            result.forEach(pr -> {
                LOGGER.info("+ git: push result  : {}", pr.getMessages());
                pr.getRemoteUpdates().forEach(x -> LOGGER.info("+ git: remote update     - {}", x));
                pr.getTrackingRefUpdates().forEach(x -> LOGGER.info("+ git: tracking update   - {}", x));
            });
        }
    }

    private static void traceStatusClass(Set<String> set, String name) {
        String name_ = String.format("%16s", name);
        if (set.isEmpty()) {
            LOGGER.debug("    ## {}: NONE", name_);
        } else {
            set.stream().sorted().forEach(e -> LOGGER.debug("    ## {}: {}", name_, e));
        }
    }
}
