//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
//  (C) Copyright 2018-2026 Modeling Value Group B.V. (http://modelingvalue.org)                                         ~
//                                                                                                                       ~
//  Licensed under the GNU Lesser General Public License v3.0 (the 'License'). You may not use this file except in       ~
//  compliance with the License. You may obtain a copy of the License at: https://choosealicense.com/licenses/lgpl-3.0   ~
//  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on  ~
//  an 'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the   ~
//  specific language governing permissions and limitations under the License.                                           ~
//                                                                                                                       ~
//  Maintainers:                                                                                                         ~
//      Wim Bast, Tom Brus                                                                                               ~
//                                                                                                                       ~
//  Contributors:                                                                                                        ~
//      Ronald Krijgsheld ✝, Arjan Kok, Carel Bast                                                                       ~
// --------------------------------------------------------------------------------------------------------------------- ~
//  In Memory of Ronald Krijgsheld, 1972 - 2023                                                                          ~
//      Ronald was suddenly and unexpectedly taken from us. He was not only our long-term colleague and team member      ~
//      but also our friend. "He will live on in many of the lines of code you see below."                               ~
//~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

package org.modelingvalue.gradle.mvgplugin;

import static org.modelingvalue.gradle.mvgplugin.Info.LOGGER;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gradle.api.GradleException;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.RmCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

public class GitUtil {
    public final static  String              NO_CI_COMMIT_MARKER = "[no-ci]";
    private final static TextProgressMonitor PROGRESS_MONITOR    = new TextProgressMonitor();
    private final static CredentialsProvider CREDENTIALS_PROV    = new UsernamePasswordCredentialsProvider(Info.ALLREP_TOKEN, "");
    private final static PersonIdent         AUTOMATION_IDENT    = new PersonIdent("automation", "automation@modelingvalue.org");

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    public static String describe(Git git) {
        return git.getRepository().getDirectory().toPath().getParent().getFileName().toString();
    }

    public static PersonIdent getAutomationIdent() {
        return AUTOMATION_IDENT;
    }

    public static CredentialsProvider getCredentialProvider() {
        return CREDENTIALS_PROV;
    }

    public static List<String> listTags(Path root) {
        try {
            return GitManager.git(root)
                    .tagList()
                    .call()
                    .stream()
                    .map(ref -> ref.getName().replaceAll("^refs/tags/", ""))
                    .collect(Collectors.toList());
        } catch (GitAPIException e) {
            throw new GradleException("could not list tags", e);
        }
    }

    public static void tag(Path root, String tag) {
        Git git = GitManager.git(root);
        LOGGER.info("+ mvg-git:{}: adding tag '{}'", describe(git), tag);
        try {
            Ref ref = git.tag()
                    .setName(tag)
                    .setForceUpdate(true)
                    .call();
            push(git, true);
            LOGGER.info("+ mvg-git:{}: added tag '{}' => {}", describe(git), tag, ref.getObjectId());
        } catch (GitAPIException e) {
            throw new GradleException("could not add tag " + tag + " to git " + describe(git), e);
        }
    }

    public static void untag(Path root, String... tags) {
        Git git = GitManager.git(root);
        LOGGER.info("+ mvg-git:{}: deleting tags: {}", describe(git), Arrays.asList(tags));
        try {
            List<String> l = git.tagDelete()
                    .setTags(tags)
                    .call();
            LOGGER.info("+ mvg-git:{}: deleted tags {} => result={}", describe(git), Arrays.asList(tags), l);
            push(git, true);
        } catch (GitAPIException e) {
            throw new GradleException("could not delete tag " + Arrays.asList(tags) + " from git " + describe(git), e);
        }
    }

    public static String getBranch(Path root) {
        Git git = GitManager.git(root);
        try {
            return git.getRepository().getBranch();
        } catch (IOException e) {
            throw new GradleException("could not get branch from git " + describe(git), e);
        }
    }

    public static void stageCommitPush(Path root, String message) {
        stageCommitPush(root, message, null);
    }

    public static void stageCommitPush(Path root, String message, Set<Path> changes) {
        Git git = GitManager.git(root);
        try {
            if (stage(git, changes)) {
                commit(git, message);
                push(git, false);
            }
        } catch (GitAPIException | IOException e) {
            throw new GradleException("could not stage-commit-push on git " + describe(git), e);
        }
    }

    private static void traceStatusClass(Set<String> set, String name) {
        String name_ = String.format("%16s", name);
        if (set.isEmpty()) {
            LOGGER.debug("++ mvg-git:    ## {}: NONE", name_);
        } else {
            set.stream().sorted().forEach(e -> LOGGER.debug("++ mvg-git:    ## {}: {}", name_, e));
        }
    }

    public static boolean stage(Git git, Set<Path> changes) throws GitAPIException, IOException {
        Set<String> changesNames = changes == null ? Set.of() : changes.stream().map(Path::toString).collect(Collectors.toSet());
        String      branch       = git.getRepository().getBranch();
        Status      status       = statusVerbose(git, "before add");

        Set<String> toAdd = Stream.concat(status.getModified().stream(), status.getUntracked().stream()).collect(Collectors.toSet());
        Set<String> toRm  = status.getMissing();

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("++ mvg-git:{}: {} changes passed to stage()", describe(git), changesNames.size());
            changesNames.stream().sorted().forEach(n -> LOGGER.debug("++ mvg-git:{}:   - {}", describe(git), n));
            LOGGER.debug("++ mvg-git:{}: {} to-adds found on disk", describe(git), toAdd.size());
            toAdd.stream().sorted().forEach(n -> LOGGER.debug("++ mvg-git:{}:   - {}", describe(git), n));
            LOGGER.debug("++ mvg-git:{}: {} to-removes found on disk", describe(git), toRm.size());
            toRm.stream().sorted().forEach(n -> LOGGER.debug("++ mvg-git:{}:   - {}", describe(git), n));
        }

        Set<String> adds = toAdd.stream()
                .filter(s -> changesNames.isEmpty() || changesNames.contains(s))
                .collect(Collectors.toSet());
        Set<String> rms = toRm.stream()
                .filter(s -> changesNames.isEmpty() || changesNames.contains(s))
                .collect(Collectors.toSet());

        if (LOGGER.isInfoEnabled() && !changesNames.isEmpty()) {
            Set<String> missedChanges = new HashSet<>(changesNames);
            missedChanges.removeAll(adds);
            missedChanges.removeAll(rms);
            missedChanges.forEach(s -> LOGGER.info("+ mvg-git:{}: CHANGE NOT FOUND: {}", describe(git), s));
            Set<String> filtered = new HashSet<>();
            filtered.addAll(toAdd);
            filtered.addAll(toRm);
            filtered.removeAll(adds);
            filtered.removeAll(rms);
            filtered.forEach(s -> LOGGER.info("+ mvg-git:{}: CHANGED BUT NOT STAGED: {}", describe(git), s));
        }

        int numAdds = adds.size();
        int numRms  = rms.size();

        boolean nothing = numAdds + numRms == 0;
        if (nothing) {
            LOGGER.info("+ mvg-git:{}: staging changes (nothing to stage; branch={})", describe(git), branch);
        } else {
            LOGGER.info("+ mvg-git:{}: staging changes (adds={} rms={}; branch={})", describe(git), numAdds, numRms, branch);

            if (0 < numAdds) {
                AddCommand addCommand = git.add();
                for (String s : adds) {
                    LOGGER.debug("++ mvg-git:{}:    add {}", describe(git), s);
                    addCommand.addFilepattern(s);
                }
                addCommand.call();
            }
            if (0 < numRms) {
                RmCommand remCommand = git.rm();
                for (String s : rms) {
                    LOGGER.debug("++ mvg-git:{}:    rm  {}", describe(git), s);
                    remCommand.addFilepattern(s);
                }
                remCommand.call();
            }
            statusVerbose(git, "after stage");
        }
        return !nothing;
    }

    public static void commit(Git git, String message) throws GitAPIException {
        LOGGER.info("+ mvg-git:{}: commit (message='{}')", describe(git), message);
        RevCommit rc = git.commit()
                .setAuthor(AUTOMATION_IDENT)
                .setCommitter(AUTOMATION_IDENT)
                .setMessage(message)
                .call();
        LOGGER.info("+ mvg-git:{}: commit (result={})", describe(git), rc);
    }

    public static void push(Git git, boolean withTags) throws GitAPIException {
        StoredConfig config = git.getRepository().getConfig();

        if (config.getSubsections("remote").isEmpty()) {
            LOGGER.info("+ mvg-git:{}: NOT pushing, repo has no remotes", describe(git));
        } else {
            LOGGER.info("+ mvg-git:{}: pushing {} tags", describe(git), withTags ? "with" : "without");
            Iterable<PushResult> result = (withTags ? git.push().setPushTags() : git.push())
                    .setCredentialsProvider(CREDENTIALS_PROV)
                    .setProgressMonitor(PROGRESS_MONITOR)
                    .call();
            if (LOGGER.isInfoEnabled()) {
                result.forEach(pr -> {
                    LOGGER.info("+ mvg-git:{}: push result  : {}", describe(git), pr.getMessages());
                    pr.getRemoteUpdates().forEach(x -> LOGGER.info("+ mvg-git:{}: remote update     - {}", describe(git), x));
                    pr.getTrackingRefUpdates().forEach(x -> LOGGER.info("+ mvg-git:{}: tracking update   - {}", describe(git), x));
                });
            }
        }
    }

    private static Status statusVerbose(Git git, String traceMessage) throws GitAPIException {
        Status status = git.status().call();

        LOGGER.debug("++ mvg-git:    ##### git status @{}", traceMessage);
        traceStatusClass(status.getAdded/*             */(), "added");
        traceStatusClass(status.getChanged/*           */(), "changed");
        traceStatusClass(status.getModified/*          */(), "modified");
        traceStatusClass(status.getRemoved/*           */(), "removed");
        traceStatusClass(status.getMissing/*           */(), "missing");
        traceStatusClass(status.getUncommittedChanges/**/(), "uncommited");
        traceStatusClass(status.getUntracked/*         */(), "untracked");
        traceStatusClass(status.getUntrackedFolders/*  */(), "untrackedFolders");

        return status;
    }
}
