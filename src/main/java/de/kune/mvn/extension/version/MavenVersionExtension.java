package de.kune.mvn.extension.version;

import com.google.inject.Key;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.building.ModelSource2;
import org.apache.maven.model.io.DefaultModelReader;
import org.apache.maven.model.io.DefaultModelWriter;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.locator.ModelLocator;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import javax.inject.Inject;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Optional.of;
import static java.util.Optional.ofNullable;
import static java.util.regex.Pattern.quote;

@Component(role = ModelProcessor.class)
public class MavenVersionExtension extends DefaultModelProcessor {

    @Requirement
    private final Logger logger;

    @Requirement
    private final SessionScope sessionScope;

    @Inject
    public MavenVersionExtension(Logger logger, SessionScope sessionScope) {
        this.logger = logger;
        this.sessionScope = sessionScope;
    }

    @Override
    public DefaultModelProcessor setModelLocator(ModelLocator locator) {
        return super.setModelLocator(locator);
    }

    @Override
    public DefaultModelProcessor setModelReader(ModelReader reader) {
        return super.setModelReader(reader);
    }

    @Override
    public File locatePom(File projectDirectory) {
        return super.locatePom(projectDirectory);
    }

    @Override
    public Model read(File input, Map<String, ?> options) throws IOException {
        Optional<URI> pom = getPom(options);
        if (isLocalProject(pom)) {
            File pomFile = new File(pom.get().getPath());
            File versionedPomFile = getVersionPomFile(pomFile);
            new DefaultModelWriter().write(versionedPomFile, null, enhance(new DefaultModelReader().read(pomFile, options), options));
            return enhance(super.read(input, options), options);
        } else {
            return super.read(input, options);
        }
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        Optional<URI> pom = getPom(options);
        if (isLocalProject(pom)) {
            File pomFile = new File(pom.get().getPath());
            File versionedPomFile = getVersionPomFile(pomFile);
            new DefaultModelWriter().write(versionedPomFile, null, enhance(new DefaultModelReader().read(pomFile, options), options));
            return enhance(super.read(input, options), options);
        } else {
            return super.read(input, options);
        }
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        Optional<URI> pom = getPom(options);
        if (isLocalProject(pom)) {
            File pomFile = new File(pom.get().getPath());
            File versionedPomFile = getVersionPomFile(pomFile);
            new DefaultModelWriter().write(versionedPomFile, null, enhance(new DefaultModelReader().read(pomFile, options), options));
            return enhance(super.read(input, options), options);
        } else {
            return super.read(input, options);
        }
    }

    public static File getVersionPomFile(File pomFile) {
        return new File(pomFile.getParentFile(), "versioned-pom.xml");
    }

    private static boolean isLocalProject(Optional<URI> pom) {
//        return pom.filter(p->p.getScheme().equals("file")).map(URI::getPath).map(String::toLowerCase).map(p->p.endsWith(".xml")).orElse(false);
        return pom.filter(p->p.getScheme().equals("file")).map(URI::getPath).map(String::toLowerCase).map(p->p.endsWith("/pom.xml")).orElse(false);
    }

    private static Optional<URI> getPom(Map<String, ?> options) {
        return Optional.ofNullable(options).map(o->(ModelSource2) o.get(ModelProcessor.SOURCE)).map(o->o.getLocationURI());
    }

    private Model enhance(Model model, Map<String, ?> options) {
        Optional<MavenSession> mavenSession = getMavenSession();
        String previous = model.toString();
        VersionMapper versionMapper = new VersionMapper(logger, model, mavenSession, options);
        model.setVersion(of(model).map(Model::getVersion).map(versionMapper::mapVersion).orElse(null));
        ofNullable(model.getParent()).ifPresent(p->enhance(p, versionMapper, options));
        ofNullable(model.getDependencyManagement()).map(DependencyManagement::getDependencies).ifPresent(c -> c.stream().forEach(d -> d.setVersion(of(d).map(Dependency::getVersion).map(versionMapper::mapVersion).orElse(null))));
        ofNullable(model.getDependencies()).ifPresent(c -> c.stream().forEach(d -> d.setVersion(of(d).map(Dependency::getVersion).map(versionMapper::mapVersion).orElse(null))));
        String current = model.toString();
        if (!current.equals(previous)) {
            logger.info("Enhanced version: " + model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion());
        }
        return model;
    }

    private Optional<MavenSession> getMavenSession() {
        Optional<MavenSession> mavenSession = Optional.empty();
        try {
            mavenSession = ofNullable(sessionScope.scope(Key.get(MavenSession.class), null).get());
        } catch (Exception e) {
            logger.warn("no session", e);
        }
        return mavenSession;
    }

    private void enhance(Parent parent, VersionMapper versionMapper, Map<String, ?> options) {
        parent.setVersion(of(parent).map(Parent::getVersion).map(versionMapper::mapVersion).orElse(null));
    }

    private static String VERSION_EXTENSION_KEY = "version-extension";

    private static final String EXTENSION_GROUP = "extension";

    private static final String VERSION_EXTENSION_REGEX = "[\\$\\#]\\{"
            + quote(VERSION_EXTENSION_KEY)
            + "\\[(?<"
            + EXTENSION_GROUP
            + ">.*?)\\]\\}";

    private static final Pattern VERSION_EXTENSION_PATTERN = Pattern.compile(VERSION_EXTENSION_REGEX);

    private static final VersionExtension DEFAULT_VERSION_EXTENSION = new GitDevFlow();
    private static final String DEFAULT_VERSION_KEY = "[0|maven\\-version\\-extension]\\-SNAPSHOT";
    private static final Pattern DEFAULT_VERSION_KEY_PATTERN = Pattern.compile(DEFAULT_VERSION_KEY);

    private static final Map<String, Class<? extends VersionExtension>> VERSION_EXTENSIONS;

    static {
        VERSION_EXTENSIONS = new HashMap<>();
        VERSION_EXTENSIONS.put("git-dev-flow", GitDevFlow.class);
    }

    private static class VersionMapper {

        private final Logger logger;
        private final Model model;
        private final Optional<MavenSession> mavenSession;
        private final Map<String, ?> options;

        public VersionMapper(Logger logger, Model model, Optional<MavenSession> mavenSession, Map<String, ?> options) {
            this.logger = logger;
            this.model = model;
            this.mavenSession = mavenSession;
            this.options = options;
        }

        private String mapVersion(String s) {
            if (s == null) {
                return null;
            }
            Matcher matcher = VERSION_EXTENSION_PATTERN.matcher(s);
            if (matcher.find()) {
                String extensionName = matcher.group(EXTENSION_GROUP);
                VersionExtension extension = Optional.ofNullable(VERSION_EXTENSIONS.get(extensionName))
                        .map(VersionMapper::versionExtension)
                        .orElseGet(() -> versionExtension(extensionName));
                return matcher.replaceAll(extension.determineVersion(logger, model, mavenSession, options));
            } else {
                return DEFAULT_VERSION_KEY_PATTERN.matcher(s).replaceAll(DEFAULT_VERSION_EXTENSION.determineVersion(logger, model, mavenSession, options));
            }
        }

        private static VersionExtension versionExtension(String className) {
            try {
                Class<?> versionExtensionClass = ClassLoader.getSystemClassLoader().loadClass(className);
                if (!VersionExtension.class.isAssignableFrom(versionExtensionClass)) {
                    throw new IllegalStateException(className + " does not implement " + VersionExtension.class);
                }
                return (VersionExtension) versionExtensionClass.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("Could not instatiate " + className, e);
            }
        }

        private static VersionExtension versionExtension(Class<? extends VersionExtension> versionExtensionClass) {
            try {
                return versionExtensionClass.getDeclaredConstructor(new Class[0]).newInstance(new Object[0]);
            } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
                throw new IllegalStateException("Could not instatiate " + versionExtensionClass, e);
            }
        }

    }
}
