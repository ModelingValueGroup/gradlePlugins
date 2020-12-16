package org.modelingvalue.gradle.corrector;

import java.nio.file.Path;
import java.util.Set;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;

public class GitUtil {
    private static final Logger LOGGER = Logging.getLogger(MvgCorrectorPluginExtension.NAME);

    static void pushChanges(Path d) {
        try {
            LOGGER.info("pushing from " + d.toAbsolutePath());
            Repository repository = new FileRepositoryBuilder()
                    .findGitDir(d.toFile())
                    .readEnvironment()
                    .build();
            Git git = new Git(repository);

            System.err.println("@@@@@@@@@@ " + repository.getBranch());
            System.err.println();

            Status status = git.status().call();

            Set<String> add = status.getAdded();
            add.forEach(x -> System.err.println("@@add@@" + x));
            System.err.println();

            Set<String> chg = status.getChanged();
            chg.forEach(x -> System.err.println("@@chg@@" + x));
            System.err.println();

            Set<String> con = status.getConflicting();
            con.forEach(x -> System.err.println("@@con@@" + x));
            System.err.println();

            Set<String> ign = status.getIgnoredNotInIndex();
            ign.forEach(x -> System.err.println("@@ign@@" + x));
            System.err.println();

            Set<String> mis = status.getMissing();
            mis.forEach(x -> System.err.println("@@mis@@" + x));
            System.err.println();

            Set<String> mod = status.getModified();
            mod.forEach(x -> System.err.println("@@mod@@" + x));
            System.err.println();

            Set<String> rem = status.getRemoved();
            rem.forEach(x -> System.err.println("@@rem@@" + x));
            System.err.println();

            Set<String> unc = status.getUncommittedChanges();
            unc.forEach(x -> System.err.println("@@unc@@" + x));
            System.err.println();

            Set<String> unt = status.getUntracked();
            unt.forEach(x -> System.err.println("@@unt@@" + x));
            System.err.println();

            Set<String> unf = status.getUntrackedFolders();
            unf.forEach(x -> System.err.println("@@unf@@" + x));
            System.err.println();


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
