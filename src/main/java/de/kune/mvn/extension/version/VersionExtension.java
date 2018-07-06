package de.kune.mvn.extension.version;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Model;
import org.codehaus.plexus.logging.Logger;

import java.util.Map;
import java.util.Optional;

public interface VersionExtension {

    String determineVersion(Logger logger, Model model, Optional<MavenSession> mavenSession, Map<String, ?> options);

}
