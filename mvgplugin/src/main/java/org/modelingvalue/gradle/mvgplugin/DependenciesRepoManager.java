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
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_DEPENDENCIES_REPO;
import static org.modelingvalue.gradle.mvgplugin.Info.MVG_DEPENDENCIES_REPO_NAME;
import static org.modelingvalue.gradle.mvgplugin.Util.getTestMarker;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.util.FileUtils;
import org.gradle.api.invocation.Gradle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * keeps a repo with dependencies.
 * <p>
 * repo contents:
 * <p>
 * if the following dependencies exist:
 * <pre>
 *     <B>gh-app</B> produces <B>some-application</B>
 *     <B>gh-lib</B> produces <B>test.ab.c.lib</B>
 *     <B>gh-bas</B> produces <B>test.base.lib</B>
 *
 *     <B>gh-app</B> uses     <B>test.ab.c.lib</B>
 *     <B>gh-app</B> uses     <B>test.qw.e.lib</B>
 *     <B>gh-lib</B> uses     <B>test.base.lib</B>
 *     <B>gh-lib</B> uses     <B>test.qw.e.lib</B>
 * </pre>
 * then the following files are created in the <B>dependencies</B> repo on the same branch as the underlaying project:
 * <pre>
 *     <B>test/ab/c/lib/gh-app.trigger</B>
 *     <B>test/base/lib/gh-lib.trigger</B>
 *     <B>test/qw/e/lib/gh-app.trigger</B>
 *     <B>test/qw/e/lib/gh-lib.trigger</B>
 * </pre>
 * All files are property files with a property WORKFLOWS with all the workflows to trigger (/-separated):
 * <pre>
 *     <B>WORKFLOWS=build.yaml/test.yml/xyzzy.yaml</B>
 * </pre>
 */
public class DependenciesRepoManager {
    private static final String      TRIGGER_EXT = ".trigger";
    private final        String      repoName;
    private final        String      branch;
    private final        boolean     active;
    private final        Path        dependenciesRepoDir;
    private final        Set<String> workflowFileNames;
    private final        String      commitMessage;

    public DependenciesRepoManager(Gradle gradle) {
        repoName = InfoGradle.getMvgRepoName();
        branch = InfoGradle.getBranch();
        active = !InfoGradle.isMasterBranch() && InfoGradle.isTestingOrMvgCI();
        dependenciesRepoDir = active ? gradle.getRootProject().getBuildDir().toPath().resolve(MVG_DEPENDENCIES_REPO_NAME).toAbsolutePath() : null;
        workflowFileNames = active ? findMyTriggerWorkflows() : null;
        commitMessage = active ? repoName + ":" + branch + " @" + Info.NOW_STAMP + " [" + Info.HOSTNAME + "]" : null;
        if (active) {
            cloneDependenciesRepo(dependenciesRepoDir, branch);
        }
    }

    public void saveDependencies(Set<String> packages) {
        if (active) {
            if (Info.TESTING) {
                test();
            }
            if (repoName == null) {
                LOGGER.info("+ mvg-bbb: saveDependencies skipped because not a proper MVG repository at {}", InfoGradle.getAbsProjectDir());
            } else {
                saveDependencies(repoName, packages);
            }
        }
    }

    public void trigger(Set<String> publications) {
        if (active) {
            try {
                getTriggers(publications).forEach(tr -> tr.workflows.forEach(workflowFilename -> trigger(tr.repoName, workflowFilename)));
            } catch (IOException e) {
                LOGGER.warn("triggers could not be retrieved", e);
            }
        }
    }

    private void trigger(String repoName, String workflowFilename) {
        LOGGER.info("+ mvg-bbb: TRIGGER dependent project (repo={} branch={} workflow={})", repoName, branch, workflowFilename);
        try {
            String json = GithubApi.triggerWorkflow(repoName, workflowFilename, branch, msg -> LOGGER.info("+ mvg-bbb: TRIGGER gave problem: err={}", msg));
            if (!json.isBlank()) {
                LOGGER.info("+ mvg-bbb: trigger error: {}: {}", json.length(), json);
            }
        } catch (IOException e) {
            LOGGER.info("+ mvg-bbb: {} could not trigger (repo={} wf={} msg='{}:{}')", getTestMarker("!"), repoName, workflowFilename, e.getClass().getSimpleName(), e.getMessage());
            LOGGER.debug("++ mvg-bbb: could not trigger", e);
        }
    }

    /**
     * <H3>Clone the dependencies repo</H3>
     * <p>
     * It is not very efficient to clone the dep-repo every time, but since this is only done in CI and the CI only does a one-shot it does not hurt.
     * If the dir already exists (can only be during testing) it is first completely removed.
     * </p>
     * <p>
     * The repo is returned in the same branch as the repo gradle is called from.
     * </p>
     *
     * @param dependenciesRepoDir the dir to clone the repo in
     * @param branch              the branch to clone the repo in
     */
    private static void cloneDependenciesRepo(Path dependenciesRepoDir, String branch) {
        long t0 = System.currentTimeMillis();
        try {
            if (Files.isDirectory(dependenciesRepoDir)) {
                // will normally not happen in CI, but can happen in testing
                LOGGER.info("+ mvg-bbb: deleting old dependencies repo at {}", dependenciesRepoDir);
                FileUtils.delete(dependenciesRepoDir.toFile(), FileUtils.RECURSIVE);
            }
            LOGGER.info("+ mvg-bbb: cloning dependencies repo {} branch {} in {}", MVG_DEPENDENCIES_REPO_NAME, branch, dependenciesRepoDir);
            try (Git git = Git.cloneRepository()
                    .setURI(MVG_DEPENDENCIES_REPO)
                    .setDirectory(dependenciesRepoDir.toFile())
                    .call()) {
                if (git.branchList().setListMode(ListMode.REMOTE).call().stream().anyMatch(ref -> ref.getName().endsWith("/" + branch))) {
                    git.checkout()
                            .setName(branch)
                            .setCreateBranch(true)
                            .setUpstreamMode(SetupUpstreamMode.TRACK)
                            .setStartPoint("origin/" + branch)
                            .call();
                } else {
                    git.branchCreate()
                            .setName(branch)
                            .call();
                    git.push()
                            .setCredentialsProvider(GitUtil.getCredentialProvider())
                            .setRemote("origin")
                            .setRefSpecs(new RefSpec(branch + ":" + branch))
                            .call();
                    git.branchCreate()
                            .setName(branch)
                            .setUpstreamMode(SetupUpstreamMode.SET_UPSTREAM)
                            .setStartPoint("origin/" + branch)
                            .setForce(true)
                            .call();
                    git.checkout()
                            .setName(branch)
                            .setUpstreamMode(SetupUpstreamMode.TRACK)
                            .setStartPoint("origin/" + branch)
                            .call();
                }
            }
        } catch (GitAPIException | IOException e) {
            LOGGER.error("+ mvg-bbb: problem with dependencies repo", e);
        } finally {
            LOGGER.info("+ mvg-bbb: dependencies repo done ({} ms)", System.currentTimeMillis() - t0);
        }
    }

    void saveDependencies(String repoName, Set<String> usedPackages) {
        try {
            clearExistingDependencies(repoName);
            writeDependencies(repoName, usedPackages);
            pushDependenciesRepo();
        } catch (IOException | GitAPIException e) {
            e.printStackTrace();
        }
    }

    private void clearExistingDependencies(String repoName) throws IOException {
        if (Files.isDirectory(dependenciesRepoDir)) {
            Files.walkFileTree(dependenciesRepoDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (dir.getFileName().toString().equals(".git")) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    Path file = getTriggerFile(dir, repoName);
                    if (Files.isRegularFile(file)) {
                        LOGGER.info("+ mvg-bbb: deleting obsolete trigger file: {}", file);
                        Files.delete(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void writeDependencies(String repoName, Set<String> usedPackages) {
        String prop = "WORKFLOWS=" + String.join("/", workflowFileNames) + "\n";
        usedPackages.forEach(usedPackage -> {
            try {
                Path triggerFile = getTriggerFile(repoName, usedPackage);
                LOGGER.info("+ mvg-bbb: creating          trigger file: {}", triggerFile);
                Files.createDirectories(triggerFile.getParent());
                Files.write(triggerFile, prop.getBytes());
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void pushDependenciesRepo() throws GitAPIException, IOException {
        GitUtil.stageCommitPush(dependenciesRepoDir, commitMessage, null);
    }

    private Stream<Trigger> getTriggers(Set<String> publications) throws IOException {
        return publications.stream().flatMap(this::getTriggers).distinct();
    }

    private Stream<Trigger> getTriggers(String pack) {
        Path path = getTriggerDirFor(pack);
        try {
            return !Files.isDirectory(path)
                    ? Stream.empty()
                    : Files.list(path)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(TRIGGER_EXT))
                    .map(Trigger::new)
                    .filter(tr -> tr.workflows != null);
        } catch (IOException e) {
            throw new Error("could not list directory " + path, e);
        }
    }

    private static class Trigger {
        public final String       repoName;
        public final List<String> workflows;

        private Trigger(Path triggerFile) {
            repoName = triggerFile.getFileName().toString().replaceFirst(Pattern.quote(TRIGGER_EXT) + "$", "");
            workflows = readWorkflows(triggerFile);
        }

        @Nullable
        private List<String> readWorkflows(Path triggerFile) {
            try {
                Properties props = new Properties();
                props.load(Files.newInputStream(triggerFile));
                String workflowsString = (String) props.get("WORKFLOWS");
                return workflowsString == null ? null : Arrays.stream(workflowsString.split("/")).collect(Collectors.toList());
            } catch (IOException e) {
                return null;
            }
        }

        @Override
        public String toString() {
            return "{repo=" + repoName + ", workflows=" + workflows + '}';
        }
    }

    private Path getTriggerDirFor(String pack) {
        return dependenciesRepoDir.resolve(pack.replace('.', '/'));
    }

    private Path getTriggerFile(String repoName, String pack) {
        return getTriggerFile(getTriggerDirFor(pack), repoName);
    }

    @NotNull
    private Path getTriggerFile(Path d, String repoName) {
        return d.resolve(repoName + TRIGGER_EXT);
    }

    /**
     * Gets you the set of workflow file names with extensions but without any directory info
     *
     * @return set of workflowfile names
     */
    private Set<String> findMyTriggerWorkflows() {
        Path workflowsDir = InfoGradle.getWorkflowsDir();
        if (!Files.isDirectory(workflowsDir)) {
            return Set.of();
        }
        String namePattern = "^name: *" + Pattern.quote(Info.GITHUB_WORKFLOW) + "$";
        try {
            Predicate<Path> fileFilter = p -> {
                try {
                    if (!Files.isRegularFile(p)) {
                        return false;
                    }
                    if (!p.getFileName().toString().matches(".*\\.(yaml|yml)")) {
                        return false;
                    }
                    List<String> lines = Files.readAllLines(p);
                    return lines.stream().anyMatch(l -> l.contains("workflow_dispatch"));
                } catch (IOException e) {
                    return false;
                }
            };
            Set<String> set = Files.list(workflowsDir)
                    .filter(fileFilter)
                    .filter(p -> {
                        try {
                            List<String> lines = Files.readAllLines(p);
                            return lines.stream().anyMatch(l -> l.matches(namePattern));
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
            if (!set.isEmpty()) {
                return set;
            }
            return Files.list(workflowsDir)
                    .filter(fileFilter)
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            return Set.of();
        }
    }

    private void test() {
        try {
            LOGGER.info("+ mvg: ===TESTING============================================================ <<< {}", getTestMarker("t"));
            saveDependencies("gh-app", Set.of("test.ab.c.lib", "test.qw.e.lib"));
            saveDependencies("gh-lib", Set.of("test.base.lib", "test.qw.e.lib"));

            for (String pack : List.of("test.ab.c.lib", "test.qw.e.lib", "test.base.lib")) {
                getTriggers(pack).forEach(s -> LOGGER.info("+ mvg: test trigger: {}  ==={}===>  {}", pack, getTestMarker("triggers"), s));
            }
            trigger("sync-proxy", "build.yaml");
            trigger("sync-proxy", "no_wf.yaml");
        } catch (Exception e) {
            throw new Error("TESTING: unable to test DependenciesManager", e);
        } finally {
            LOGGER.info("+ mvg: ===TESTING============================================================ >>> {}", getTestMarker("t"));

        }
    }
}
