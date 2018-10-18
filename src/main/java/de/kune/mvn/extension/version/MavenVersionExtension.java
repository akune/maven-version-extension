package de.kune.mvn.extension.version;

import com.google.inject.Key;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.model.building.DefaultModelProcessor;
import org.apache.maven.model.building.ModelProcessor;
import org.apache.maven.model.io.ModelReader;
import org.apache.maven.model.locator.ModelLocator;
import org.apache.maven.session.scope.internal.SessionScope;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
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
        return enhance(super.read(input, options), options);
    }

    @Override
    public Model read(Reader input, Map<String, ?> options) throws IOException {
        return enhance(super.read(input, options), options);
    }

    @Override
    public Model read(InputStream input, Map<String, ?> options) throws IOException {
        return enhance(super.read(input, options), options);
    }

    private Model enhance(Model model, Map<String, ?> options) {
//        if (model.getProjectDirectory() != null)
//        logger.warn(model.getProjectDirectory().toString());
        Optional<MavenSession> mavenSession = Optional.empty(); //ofNullable(sessionScope.scope(Key.get(MavenSession.class), null).get());
        String previous = model.toString();
        VersionMapper versionMapper = new VersionMapper(logger, model, mavenSession, options);
        model.setVersion(of(model).map(Model::getVersion).map(versionMapper::mapVersion).orElse(null));
        ofNullable(model.getParent()).ifPresent(p->enhance(p, versionMapper, options));
        Optional.ofNullable(model.getDependencyManagement()).map(DependencyManagement::getDependencies).ifPresent(c -> c.stream().forEach(d -> d.setVersion(of(d).map(Dependency::getVersion).map(versionMapper::mapVersion).orElse(null))));
        Optional.ofNullable(model.getDependencies()).ifPresent(c -> c.stream().forEach(d -> d.setVersion(of(d).map(Dependency::getVersion).map(versionMapper::mapVersion).orElse(null))));
        String current = model.toString();
        if (!current.equals(previous)) {
            logger.info("Enhanced version: " + model.getGroupId() + ":" + model.getArtifactId() + ":" + model.getVersion());
        }
        return model;
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
                return s;
            }
        }

        private static VersionExtension versionExtension(String className) {
            try {
                Class<?> versionExtensionClass = ClassLoader.getSystemClassLoader().loadClass(className);
                if (!VersionExtension.class.isAssignableFrom(versionExtensionClass)) {
                    throw new IllegalStateException(className + " does not implement " + VersionExtension.class);
                }
                return (VersionExtension) versionExtensionClass.newInstance();
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("Could not load class " + className, e);
            } catch (InstantiationException e) {
                throw new IllegalStateException("Could not instatiate " + className, e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not instatiate " + className, e);
            }
        }

        private static VersionExtension versionExtension(Class<? extends VersionExtension> versionExtensionClass) {
            try {
                return versionExtensionClass.newInstance();
            } catch (InstantiationException e) {
                throw new IllegalStateException("Could not instatiate " + versionExtensionClass, e);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Could not instatiate " + versionExtensionClass, e);
            }
        }

        //    public static void main(String[] args) {
        //        String s = "Hallo ${version-extension[git-dev-flow]} und so";
        //        String s = "Hallo ${version-extension[de.kune.mvn.extension.version.GitDevFlow]} und so";
        //        System.out.println(mapVersion(s));
        //    }
    }
}
