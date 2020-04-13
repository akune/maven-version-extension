package de.kune.mvn.extension.version;

import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.Contextualizable;

import java.io.File;
import java.io.IOException;
import java.util.Collections;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class MavenVersionLifecycleParticipant extends AbstractMavenLifecycleParticipant implements Contextualizable {
    @Override
    public void contextualize(Context context) throws ContextException {

    }

    @Override
    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        super.afterProjectsRead(session);
        for (MavenProject p: session.getProjects()) {
            File versionedPomFile = MavenVersionExtension.getVersionPomFile(p.getModel().getPomFile());
            if (versionedPomFile.exists()) {
                p.setPomFile(versionedPomFile);
            }
        }
    }

    @Override
    public void afterSessionStart(MavenSession session) throws MavenExecutionException {
        super.afterSessionStart(session);
    }

    @Override
    public void afterSessionEnd(MavenSession session) throws MavenExecutionException {
        super.afterSessionEnd(session);
        for (MavenProject p: session.getProjects()) {
            File versionedPomFile = MavenVersionExtension.getVersionPomFile(p.getModel().getPomFile());
            if (versionedPomFile.exists()) {
                if (!versionedPomFile.delete()) {
                    versionedPomFile.deleteOnExit();
                }
            }
        }
    }
}
