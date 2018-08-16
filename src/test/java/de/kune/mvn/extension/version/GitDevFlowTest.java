package de.kune.mvn.extension.version;

import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;

import static org.apache.commons.io.FileUtils.copyDirectory;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@RunWith(Parameterized.class)
public class GitDevFlowTest {

    private final String expectedVersion;

    private final String testcase;

    private File gitTestDir;

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
            new Object[][] {
                    { "0.1.2", "detached-release-tag" },
                    { "0.0.1", "detached-master" },
                    { "1.0.0", "master-with-breaking-change" },
                    { "0.0.1", "detached-feature-branch-and-master" },
                    { "feature-something-SNAPSHOT", "detached-feature-branch" },
                    { "519831d117b4eaed279295a9151f956d74a4e68b-SNAPSHOT", "detached-no-branch" },
                    { "unknown-SNAPSHOT", "init-no-branch" },
                    { "0.0.0", "init-no-release" },
                    { "0.0.0", "init-with-release" },
                    { "0.0.1", "init-with-release-and-chore-commit" },
                    { "0.0.1", "init-no-release-and-chore-commit" },
                    { "feat-a-SNAPSHOT", "branch" },
                    { "feat-a-SNAPSHOT", "branch-with-merged-release" },
                    { "0.1.1", "merged-branch-with-merged-release" } });
    }

    public GitDevFlowTest(String expectedVersion, String testcase) {
        this.expectedVersion = expectedVersion;
        this.testcase = testcase;
    }

    @Test
    public void test() {
        assertVersion(expectedVersion, testcase);
    }

    private void assertVersion(String s, String testcase) {
        ConsoleLogger logger = new ConsoleLogger();
        logger.setThreshold(Logger.LEVEL_DEBUG);
        Assert.assertEquals(s, GitDevFlow.determineVersion(logger, gitTestDir));
    }

    @Before
    public void setUp() throws IOException {
        Path tmp = Files.createTempDirectory(UUID.randomUUID().toString());
        File gitTmp = new File(tmp.toFile(), ".git");
        copyDirectory(gitSource(testcase), gitTmp);
        gitTestDir = tmp.toFile();
    }

    @After
    public void tearDown() throws IOException {
        deleteDirectory(gitTestDir);
    }

    private static File gitParent(String testcase) {
        File gitParent = new File(GitDevFlowTest.class.getClassLoader().getResource(testcase).getFile());
        Assert.assertTrue(gitParent.exists());
        Assert.assertTrue(gitParent.isDirectory());
        return gitParent;
    }

    private static File gitSource(String testcase) {
        return new File(gitParent(testcase), "git");
    }

}
