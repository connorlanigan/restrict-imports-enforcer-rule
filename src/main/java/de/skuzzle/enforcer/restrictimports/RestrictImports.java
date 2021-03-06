package de.skuzzle.enforcer.restrictimports;

import static com.google.common.base.Preconditions.checkArgument;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.apache.maven.enforcer.rule.api.EnforcerRule;
import org.apache.maven.enforcer.rule.api.EnforcerRuleException;
import org.apache.maven.enforcer.rule.api.EnforcerRuleHelper;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluationException;

import de.skuzzle.enforcer.restrictimports.impl.DefaultAnalyzerFactory;
import de.skuzzle.enforcer.restrictimports.impl.RuntimeIOException;

/**
 */
public class RestrictImports implements EnforcerRule {

    private PackagePattern basePackage = PackagePattern.parse("**");
    private List<PackagePattern> bannedImports = new ArrayList<>();
    private List<PackagePattern> allowedImports = new ArrayList<>();
    private List<PackagePattern> excludedClasses = new ArrayList<>();

    private static final SourceTreeAnalyzer ANALYZER = DefaultAnalyzerFactory
            .getInstance()
            .createAnalyzer();

    @Override
    @SuppressWarnings("unchecked")
    public void execute(EnforcerRuleHelper helper) throws EnforcerRuleException {
        final Log log = helper.getLog();
        try {
            final MavenProject project = (MavenProject) helper.evaluate("${project}");

            log.debug("Checking for banned imports");
            final BannedImportGroup group = new BannedImportGroup(this.basePackage,
                    this.bannedImports, this.allowedImports, this.excludedClasses);
            final Map<String, List<Match>> matches = ANALYZER.analyze(
                    listSourceRoots(project, log),
                    group);

            if (!matches.isEmpty()) {
                final List<String> roots = project.getCompileSourceRoots();
                throw new EnforcerRuleException(formatErrorString(roots, matches));
            } else {
                log.debug("No banned imports found");
            }

        } catch (final RuntimeIOException e) {
            throw new EnforcerRuleException("Encountered IO exception", e);
        } catch (final ExpressionEvaluationException e) {
            throw new EnforcerRuleException("Unable to lookup an expression " +
                e.getLocalizedMessage(), e);
        }
    }

    private static String relativize(Collection<String> roots, String path) {
        for (final String root : roots) {
            if (path.startsWith(root)) {
                return path.substring(root.length());
            }
        }
        return path;
    }

    private String formatErrorString(Collection<String> roots,
            Map<String, List<Match>> groups) {
        final StringBuilder b = new StringBuilder("\nBanned imports detected:\n");
        groups.forEach((fileName, matches) -> {
            b.append("\tin file: ").append(relativize(roots, fileName)).append("\n");
            matches.forEach(match -> {
                b.append("\t\t").append(match.getMatchedString())
                        .append(" (Line: ").append(match.getImportLine()).append(")\n");
            });
        });
        return b.toString();
    }

    @SuppressWarnings("unchecked")
    private Stream<Path> listSourceRoots(MavenProject project, Log log) {
        final List<String> roots = project.getCompileSourceRoots();
        return roots.stream()
                .peek(root -> log.debug("Including source dir: " + root))
                .map(Paths::get);
    }

    @Override
    public String getCacheId() {
        return "";
    }

    @Override
    public boolean isCacheable() {
        return false;
    }

    @Override
    public boolean isResultValid(EnforcerRule rule) {
        return false;
    }

    public final void setBasePackage(String basePackage) {
        this.basePackage = PackagePattern.parse(basePackage);
    }

    public final void setBannedImports(List<String> bannedPackages) {
        checkArgument(bannedPackages != null && !bannedPackages.isEmpty(),
                "bannedPackages must not be empty");
        this.bannedImports = PackagePattern.parseAll(bannedPackages);
    }

    public final void setAllowedImports(List<String> allowedImports) {
        this.allowedImports = PackagePattern.parseAll(allowedImports);
    }

    public final void setExcludedClasses(List<String> excludedClasses) {
        this.excludedClasses = PackagePattern.parseAll(excludedClasses);
    }
}