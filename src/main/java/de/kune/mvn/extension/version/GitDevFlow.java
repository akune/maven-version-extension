package de.kune.mvn.extension.version;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.apache.maven.model.building.FileModelSource;
import org.apache.maven.model.building.ModelProcessor;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.CollectionUtils;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.lang.String.join;
import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableSet;
import static java.util.regex.Pattern.compile;
import static java.util.stream.Collectors.toList;

public class GitDevFlow implements VersionExtension {

    private static final Set<String> releaseBranchNames = unmodifiableSet(new HashSet<>(asList("master")));

    private static final Pattern releaseBranchPattern = compile(join("|", releaseBranchNames));

    private static final Set<String> hotfixBranchPrefixes = unmodifiableSet(new HashSet<>(asList("hotfix", "support")));

    private static final Pattern hotfixBranchPattern = compile(
        "(?<type>" + join("|", hotfixBranchPrefixes) + ")-(?<base>.*?)");

    private static final Pattern majorIncrementPattern = Pattern.compile("(^|\\n)BREAKING CHANGE:?.*$");

    private static final Set<String> minorIncrementTypes = unmodifiableSet(new HashSet<String>(asList("feat")));

    private static final Set<String> patchIncrementTypes = unmodifiableSet(
        new HashSet<>(asList("fix", "docs", "style", "refactor", "perf", "test", "chore")));

    private static final String UNKNOWN_SNAPSHOT = "unknown-SNAPSHOT";

    public static final String REFS_TAGS = "refs/tags/";

    private final static Pattern releaseTagPattern = compile(REFS_TAGS + SemVer.versionStringPattern.pattern());

    private final static Pattern hotfixReleaseTagPattern = compile(
        REFS_TAGS + "v?(.*?\\.(" + join("|", hotfixBranchPrefixes) + ")\\.)?" + SemVer.semverPattern);

    public static final String REFS_HEADS = "refs/heads/";

    protected static String determineVersion(Logger logger, File gitDirectory) {
        if (gitDirectory == null || !gitDirectory.exists() || !gitDirectory.isDirectory()) {
            logger.info(
                "Working directory ("
                        + gitDirectory
                        + ") does not exist or is not a directory, falling back to "
                        + UNKNOWN_SNAPSHOT);
            return UNKNOWN_SNAPSHOT;
        }
        try {
            Repository repository = null;
            try {
                repository = new FileRepositoryBuilder().findGitDir(gitDirectory).build();
            } catch (IllegalArgumentException e) {
                logger.info(
                    "Working directory ("
                            + gitDirectory
                            + ") is not a GIT repository, falling back to "
                            + UNKNOWN_SNAPSHOT);
                return UNKNOWN_SNAPSHOT;
            }
            logger.info("Working directory (" + gitDirectory + ") is a GIT repository");
            Ref headRefs = repository.getAllRefs().get("HEAD");
            if (headRefs == null) {
                logger.info("No HEAD refs found, falling back to " + UNKNOWN_SNAPSHOT);
                return UNKNOWN_SNAPSHOT;
            }
            logger.info("Head refs: " + headRefs);

            List<Ref> tags = getTags(repository);
            Optional<String> taggedVersion = determineTaggedVersion(tags, headRefs.getObjectId().toString());
            if (taggedVersion.isPresent()) {
                logger.info("No commit since last release tag " + taggedVersion.get());
                return taggedVersion.get();
            }

            String branch = determineBranch(logger, repository);
            if (releaseBranchPattern.matcher(branch.toLowerCase()).matches()) {
                return determineReleaseVersion(logger, repository, branch);
            } else if (hotfixBranchPattern.matcher(branch.toLowerCase()).matches()) {
                return determineHotfixVersion(logger, repository, branch);
            } else {
                logger.info(
                    "Current branch (" + branch + ") is not a release branch, falling back to " + branch + "-SNAPSHOT");
                return branch + "-SNAPSHOT";
            }
        } catch (IOException e) {
            logger.warn(e.getClass().getSimpleName() + " caught, falling back to " + UNKNOWN_SNAPSHOT, e);
        } catch (IllegalArgumentException e) {
            logger.warn(e.getClass().getSimpleName() + " caught, falling back to " + UNKNOWN_SNAPSHOT, e);
        }
        return UNKNOWN_SNAPSHOT;
    }

    private static Optional<String> determineTaggedVersion(List<Ref> tags, String commitId) {
        return tags.stream()
                .filter(
                    t -> t.getObjectId().toString().equals(commitId)
                            || t.getPeeledObjectId() != null && commitId.equals(t.getPeeledObjectId().toString()))
                .map(t -> {
                    Matcher m = releaseTagPattern.matcher(t.getName());
                    return m.matches() ? m.group("version") : null;
                })
                .filter(v -> v != null)
                .map(v -> SemVer.of((String) v))
                .sorted(SemVer.SEM_VER_COMPARATOR.reversed())
                .map(v -> v.getVersion())
                .findFirst();
    }

    private static String determineBranch(Logger logger, Repository repository) throws IOException {
        String branch = repository.getBranch();
        String fullBranch = repository.getFullBranch();
        if (!fullBranch.startsWith(REFS_HEADS)) {
            logger.info("GIT repository is detached");
            List<String> branchCandidates = repository.getAllRefs()
                    .entrySet()
                    .stream()
                    .filter(e -> e.getKey().startsWith(REFS_HEADS))
                    .filter(e -> e.getValue().getObjectId().getName().equals(fullBranch))
                    .map(e -> e.getKey())
                    .map(e -> e.replaceAll("^" + REFS_HEADS, ""))
                    .collect(toList());
            if (!branchCandidates.isEmpty()) {
                logger.debug("Branch candidates: " + join(", ", branchCandidates));
            }
            if (branchCandidates.size() == 1) {
                branch = branchCandidates.get(0);
                logger.info("Falling back to the only matching branch (" + branch + ")");
            } else if (branchCandidates.size() > 0) {
                Collection<String> releaseBranchCandidates = CollectionUtils
                        .intersection(branchCandidates, releaseBranchNames);
                if (!releaseBranchCandidates.isEmpty()) {
                    branch = releaseBranchCandidates.iterator().next();
                    logger.info("Found at least one release branch candidate, continuing with " + branch);
                } else {
                    logger.info("No branch candidates found, continuing with " + fullBranch);
                }
            } else {
                logger.info("No branch candidates found, continuing with " + fullBranch);
            }
        }
        return branch;
    }

    private static String determineHotfixVersion(Logger logger, Repository repository, String branch)
            throws IOException {
        logger.info("Determining version based on hotfix or support branch (" + branch + ")");
        SemVer newVer = determineVersion(logger, repository, true);
        Matcher matcher = hotfixBranchPattern.matcher(branch);
        matcher.matches();
        String versionString = matcher.group("base") + "." + matcher.group("type") + "." + newVer.getVersion();
        logger.info("Determined version: " + versionString);
        return versionString;
    }

    private static String determineReleaseVersion(Logger logger, Repository repository, String branch)
            throws IOException {
        logger.info("Determining version based on release branch (" + branch + ")");
        SemVer newVer = determineVersion(logger, repository, false);
        logger.info("Determined version: " + newVer.getVersion());
        return newVer.getVersion();
    }

    private static SemVer determineVersion(Logger logger, Repository repository, boolean includeHotfix)
            throws IOException {
        List<Ref> tags = getTags(repository);
        return determineVersion(
            logger,
            directCommitsAfterReleaseTag(logger, repository, tags, includeHotfix),
            latestReachableReleaseTag(logger, repository, tags, includeHotfix));
    }

    private static List<Ref> getTags(Repository repository) {
        return repository.getTags().entrySet().stream().map(Map.Entry::getValue).map(repository::peel).collect(
            toList());
    }

    private static SemVer determineVersion(Logger logger, List<String> commitMessagesAfterRelease, SemVer baseRelease) {
        Set<String> commitTypes = extractTypes(commitMessagesAfterRelease);
        SemVer newVer = baseRelease == null ? SemVer.initial() : baseRelease;
        commitMessagesAfterRelease.stream().filter(m -> majorIncrementPattern.matcher(m).find()).count();
        if (commitMessagesAfterRelease.stream()
                .filter(m -> majorIncrementPattern.matcher(m).find())
                .findAny()
                .isPresent()) {
            logger.info("Found major increment pattern(s)");
            newVer = newVer.incrementMajor(1);
        } else if (CollectionUtils.intersection(commitTypes, minorIncrementTypes).size() > 0) {
            logger.info("Found minor increment type(s)");
            newVer = newVer.incrementMinor(1);
        } else if (CollectionUtils.intersection(commitTypes, patchIncrementTypes).size() > 0) {
            logger.info("Found patch increment type(s)");
            newVer = newVer.incrementPatch(1);
        } else {
            logger.info("No increment type(s) found");
        }
        logger.debug("Base version bump: " + baseRelease + " -> " + newVer.getVersion());
        return newVer;
    }

    private static Set<String> extractTypes(List<String> commitMessagesAfterRelease) {
        return commitMessagesAfterRelease.stream()
                .map(m -> m.split(":")[0])
                .map(m -> m.replaceAll("\\(.*\\)", ""))
                .map(m -> m.toLowerCase())
                .collect(Collectors.toSet());
    }

    public static void main(String[] args) throws IOException, GitAPIException {
        Logger logger = new ConsoleLogger();
        logger.setThreshold(Logger.LEVEL_DEBUG);
        System.out.println(determineVersion(logger, new File("/Users/alexander/")));
        System.out.println(determineVersion(logger, new File("/Users/alexander/Development/git-branches-test")));
        System.out.println(
            determineVersion(
                logger,
                new File(
                        "/Users/alexander/Documents/Business/Deposit-Solutions/Workspaces/Deposit-Solutions/ds-comonea-compliance-reporting")));
    }

    private static List<String> directCommitsAfterReleaseTag(
            Logger logger,
            Repository repository,
            List<Ref> tags,
            boolean includeHotFix)
            throws IOException {
        logger.debug("Direct commits (1st parents): ");
        RevWalk revWalk = new RevWalk(repository);
        RevCommit head = revWalk.parseCommit(repository.findRef(Constants.HEAD).getObjectId());
        RevCommit r = head;
        List<String> result = new ArrayList<>();
        while (r != null) {
            logger.warn(determineTaggedVersion(tags, r.getId().toString()).orElse("Nothing determined..."));
            final RevCommit q = r;
            List<Ref> revTags = tags.stream()
                    .filter(t -> t.getObjectId().equals(q.getId()) || q.getId().equals(t.getPeeledObjectId()))
                    .collect(toList());
            logger.debug(
                "  "
                        + r.getId().getName()
                        + " "
                        + r.getShortMessage()
                        + " (parents: "
                        + r.getParentCount()
                        + ") tags="
                        + revTags);
            Pattern pattern = includeHotFix ? hotfixReleaseTagPattern : releaseTagPattern;
            if (revTags.stream().filter(t -> pattern.matcher(t.getName()).matches()).count() > 0) {
                logger.debug("Stopping at tag(s) " + revTags);
                break;
            }
            result.add(r.getFullMessage());
            if (r.getParentCount() > 0) {
                r = revWalk.parseCommit(r.getParent(0));
            } else {
                r = null;
            }
        }
        return result;
    }

    private static SemVer latestReachableReleaseTag(
            Logger logger,
            Repository repository,
            List<Ref> tags,
            boolean includeHotFix)
            throws IOException {
        logger.debug("All commits (all parents): ");
        RevWalk revWalk = new RevWalk(repository);
        RevCommit head = revWalk.parseCommit(repository.findRef(Constants.HEAD).getObjectId());
        List<RevCommit> r = asList(head);
        while (!r.isEmpty()) {
            List<RevCommit> nextParents = new ArrayList<>();
            for (final RevCommit q : r) {
                List<Ref> revTags = tags.stream()
                        .filter(t -> t.getObjectId().equals(q.getId()) || q.getId().equals(t.getPeeledObjectId()))
                        .collect(toList());
                logger.debug(
                    "  "
                            + q.getId().getName()
                            + " "
                            + q.getShortMessage()
                            + " parents: "
                            + q.getParentCount()
                            + " "
                            + revTags);
                Pattern pattern = includeHotFix ? hotfixReleaseTagPattern : releaseTagPattern;
                if (revTags.stream().filter(t -> pattern.matcher(t.getName()).matches()).count() > 0) {
                    logger.debug("Stopping at tag(s) " + revTags);
                    Matcher matcher = pattern.matcher(revTags.get(0).getName());
                    matcher.matches();
                    return SemVer.of(matcher.group("version"));
                }
                nextParents.addAll(asList(q.getParents()).stream().map(x -> {
                    try {
                        return revWalk.parseCommit(x.getId());
                    } catch (IOException e) {
                        e.printStackTrace();
                        return null;
                    }
                }).collect(toList()));
            }
            r = nextParents;
        }
        return null;
    }

    @Override
    public String determineVersion(
            Logger logger,
            Model model,
            Optional<MavenSession> mavenSession,
            Map<String, ?> options) {
        Object source = options.get(ModelProcessor.SOURCE);
        if (source instanceof FileModelSource) {
            File pomFile = ((FileModelSource) source).getFile();
            if (pomFile != null && pomFile.isFile() && pomFile.getName().toLowerCase().endsWith(".xml")) {
                logger.info("Enhancing " + pomFile + " version with git-dev-flow");
                return determineVersion(logger, pomFile.getParentFile());
            }
        }
        return UNKNOWN_SNAPSHOT;
    }

    private static class SemVer {

        public static final Comparator<SemVer> SEM_VER_COMPARATOR = Comparator.comparingInt(SemVer::getMajor)
                .thenComparingInt(SemVer::getMinor)
                .thenComparingInt(SemVer::getPatch);

        public static SemVer of(int major, int minor, int patch) {
            return new SemVer(major, minor, patch);
        }

        public static SemVer initial() {
            return of(0, 0, 0);
        }

        private static final Pattern semverPattern = compile(
            "(?<version>(?<major>\\d+?)\\.(?<minor>\\d+?)\\.(?<patch>\\d+?))");

        private static final Pattern versionStringPattern = compile("v?" + semverPattern.pattern());

        public static SemVer of(String versionString) {
            Matcher matcher = versionStringPattern.matcher(versionString);
            if (matcher.matches()) {
                return of(
                    Integer.parseInt(matcher.group("major")),
                    Integer.parseInt(matcher.group("minor")),
                    Integer.parseInt(matcher.group("patch")));
            }
            throw new IllegalArgumentException(versionString + " does not match " + versionStringPattern);
        }

        private final int major, minor, patch;

        private SemVer(int major, int minor, int patch) {
            this.major = major;
            this.minor = minor;
            this.patch = patch;
        }

        public int getMajor() {
            return major;
        }

        public int getMinor() {
            return minor;
        }

        public int getPatch() {
            return patch;
        }

        public SemVer incrementPatch(int increment) {
            return of(major, minor, patch + increment);
        }

        public SemVer incrementMinor(int increment) {
            return of(major, minor + increment, 0);
        }

        public SemVer incrementMajor(int increment) {
            return of(major + increment, 0, 0);
        }

        public String toString() {
            return getVersion();
        }

        public String getVersion() {
            return major + "." + minor + "." + patch;
        }

    }
}
