package de.kune.mvn.extension.version;

import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;

@RunWith(Parameterized.class)
public class GitDevFlowTest {

    private final String expectedVersion;

    private final String testcase;

    @Parameterized.Parameters(name = "{1}")
    public static Collection<Object[]> parameters() {
        return Arrays.asList(
            new Object[][] {
                    { "0.0.0", "init-no-release" },
                    { "0.0.0", "init-no-release" },
                    { "0.0.0", "init-with-release" },
                    { "0.0.1", "init-with-release-and-chore-commit" }, 
                    { "0.0.1", "init-no-release-and-chore-commit" } });
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
        openGit(testcase);
        try {
            Assert.assertEquals(
                s,
                GitDevFlow.determineVersion(
                    logger,
                    new File(GitDevFlowTest.class.getClassLoader().getResource(testcase).getFile())));
        } finally {
            closeGit(testcase);
        }
    }

    private void closeGit(String s) {
        File file = new File(GitDevFlowTest.class.getClassLoader().getResource(s).getFile());
        if (file != null && file.isDirectory() && new File(file, ".git").isDirectory()) {
            new File(file, ".git").renameTo(new File(file, "git"));
        }
    }

    private void openGit(String s) {
        File file = new File(GitDevFlowTest.class.getClassLoader().getResource(s).getFile());
        if (file != null && file.isDirectory() && new File(file, "git").isDirectory()) {
            new File(file, "git").renameTo(new File(file, ".git"));
        }
    }
}
