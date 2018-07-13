# maven-version-extension
A Maven extension to maintain versions independently from the POM.

## Usage

### Activate extension
.mvn/extensions.xml: 
```
<extensions xmlns="http://maven.apache.org/EXTENSIONS/1.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://maven.apache.org/EXTENSIONS/1.0.0 http://maven.apache.org/xsd/core-extensions-1.0.0.xsd">
  <extension>
    <groupId>com.github.akune</groupId>
    <artifactId>maven-version-extension</artifactId>
    <version>0.1.1</version>
  </extension>
</extensions>
```

### Add jitpack.io as a repository

.mvn/settings.xml: 
```
<settings>
    <profiles>
        <profile>
            <id>jitpack</id>
            <repositories>
                <repository>
                    <id>jitpack.io</id>
                    <url>https://jitpack.io</url>
                </repository>
            </repositories>
            <pluginRepositories>
                <pluginRepository>
                    <id>jitpack.io</id>
                    <url>https://jitpack.io</url>
                </pluginRepository>
            </pluginRepositories>
        </profile>
    </profiles>

    <activeProfiles>
        <activeProfile>jitpack</activeProfile>
    </activeProfiles>
</settings>
```
.mvn/maven.config: 
```
-s .mvn/settings.xml
```

### POM configuration
#### GIT branch and commit-based versioning
The git-dev-flow version extension determines the version from the current GIT branch and commit messages. 
* building a release branch (currently that's master ) will result in a release version
   * the version will be determined by inspecting the commits since the latest release tag (see https://github.com/commitizen/cz-cli for a great way to chose useful commit messages)
* building a feature branch (named feature-something) will result in a SNAPSHOT version (i.e. feature-something-SNAPSHOT)
* building a support or hotfix branch (named hotfix-0.3.0 or support-whatever) will result in a hotfix release version
   * the version will be determined similarly to building a release branch but the base name (the part of the branch name after support- or hotfix-) will be maintained in the version like this 0.3.0.hotfix.0.3.1 or whatever.hotfix.0.8.9

pom.xml: 
```xml
<project ...>
    <groupId>somegroup</groupId>
    <artifactId>someartifact</artifactId>
    <version>${version-extension[git-dev-flow]}</version>
</project>
```
