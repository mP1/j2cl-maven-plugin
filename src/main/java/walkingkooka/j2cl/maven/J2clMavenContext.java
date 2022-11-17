/*
 * Copyright 2019 Miroslav Pokorny (github.com/mP1)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package walkingkooka.j2cl.maven;

import com.google.javascript.jscomp.CompilationLevel;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import walkingkooka.Context;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.j2cl.maven.closure.ClosureFormattingOption;
import walkingkooka.j2cl.maven.hash.HashBuilder;
import walkingkooka.j2cl.maven.log.BrowserLogLevel;
import walkingkooka.j2cl.maven.log.MavenLogger;
import walkingkooka.j2cl.maven.log.TreeLogger;
import walkingkooka.j2cl.maven.test.J2clStepWorkerWebDriverUnitTestRunnerBrowser;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Context about a build/test request. It contains common properties shared by different phases of the build/test process,
 * including classpaths, closure compiler parameters and maven scoping which is used to select dependencies/artifacts.
 */
public abstract class J2clMavenContext implements Context {

    J2clMavenContext(final J2clPath base,
                     final J2clPath target,
                     final J2clClasspathScope scope,
                     final List<J2clArtifactCoords> classpathRequired,
                     final List<J2clArtifactCoords> ignoredDependencies,
                     final List<J2clArtifactCoords> javascriptSourceRequired,
                     final CompilationLevel level,
                     final Map<String, String> defines,
                     final Set<String> externs,
                     final Set<ClosureFormattingOption> formatting,
                     final Set<String> javaCompilerArguments,
                     final LanguageMode languageOut,
                     final Optional<String> sourceMaps,
                     final J2clMavenMiddleware middleware,
                     final ExecutorService executor,
                     final MavenLogger logger) {
        super();

        this.base = base;
        this.target = target;
        this.scope = scope;

        this.classpathRequired = classpathRequired;
        this.ignoredDependencies = ignoredDependencies;
        this.javascriptSourceRequired = javascriptSourceRequired;

        this.level = level;
        this.defines = defines;
        this.externs = externs;
        this.formatting = formatting;
        this.javaCompilerArguments = javaCompilerArguments;
        this.languageOut = languageOut;
        this.sourceMaps = sourceMaps;

        this.middleware = middleware;
        this.executor = executor;
        this.completionService = new ExecutorCompletionService<>(executor);
        this.logger = logger;
    }

    /**
     * Classpath scope used to filter artifacts.
     */
    public final J2clClasspathScope scope() {
        return this.scope;
    }

    /**
     * Classpath scope used to filter artifacts.
     */
    private final J2clClasspathScope scope;

    /**
     * The base or cache directory.
     */
    public final J2clPath base() {
        return this.base;
    }

    /**
     * The base or cache directory.
     */
    private final J2clPath base;

    public final J2clPath target() {
        return this.target;
    }

    /**
     * The target or base directory receiving all build files.
     */
    public final J2clPath target;

    // dependencies.....................................................................................................

    public final boolean isClasspathRequired(final J2clArtifactCoords coords) {
        return this.classpathRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> classpathRequired;

    public final boolean isIgnored(final J2clArtifactCoords coords) {
        return this.ignoredDependencies.contains(coords);
    }

    private final List<J2clArtifactCoords> ignoredDependencies;

    public final boolean isJavascriptSourceRequired(final J2clArtifactCoords coords) {
        return this.javascriptSourceRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> javascriptSourceRequired;

    // java.............................................................................................................

    public abstract J2clSourcesKind sourcesKind();

    // closure.........................................................................................................

    public final CompilationLevel level() {
        return this.level;
    }

    private final CompilationLevel level;

    public final Map<String, String> defines() {
        return this.defines;
    }

    private final Map<String, String> defines;

    public abstract List<String> entryPoints();

    public final Set<String> externs() {
        return this.externs;
    }

    private final Set<String> externs;

    public final Set<ClosureFormattingOption> formatting() {
        return this.formatting;
    }

    private final Set<ClosureFormattingOption> formatting;

    public abstract J2clPath initialScriptFilename();

    private final Set<String> javaCompilerArguments;

    public final Set<String> javaCompilerArguments() {
        return this.javaCompilerArguments;
    }

    public final LanguageMode languageOut() {
        return this.languageOut;
    }

    private final LanguageMode languageOut;

    public final Optional<String> sourceMaps() {
        return this.sourceMaps;
    }

    private final Optional<String> sourceMaps;

    // directoryName....................................................................................................

    abstract String directoryName(final J2clStep step);

    abstract J2clStep firstStep();

    abstract Optional<J2clStep> nextStep(final J2clStep current);

    // browserLogLevel..................................................................................................

    public abstract BrowserLogLevel browserLogLevel();

    // browsers.........................................................................................................

    public abstract List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers();

    // testTimeout......................................................................................................

    public abstract int testTimeout();

    // MAVEN..............................................................................................................

    final J2clMavenMiddleware mavenMiddleware() {
        return this.middleware;
    }

    private final J2clMavenMiddleware middleware;

    // hash..............................................................................................................

    /**
     * Returns a sha1 hash in hex digits that uniquely identifies this request using components.
     * Requests should cache the hash for performance reasons.
     */
    public abstract HashBuilder computeHash(final Set<String> hashItemsNames);

    /**
     * Creates a {@link HashBuilder} and hashes most of the properties of a request.
     */
    final HashBuilder computeHash0(final Set<String> hashItemNames) {
        final HashBuilder hash = HashBuilder.empty();

        final J2clClasspathScope scope = this.scope();
        hashItemNames.add("scope: " + scope);
        hash.append(scope);

        final CompilationLevel level = this.level;
        hashItemNames.add("level: " + level);
        hash.append(scope);

        final J2clSourcesKind sourcesKind = this.sourcesKind();
        hashItemNames.add("sources-kind: " + sourcesKind);
        hash.append(sourcesKind);

        this.defines.forEach((k, v) -> {
            hashItemNames.add("define: " + k + "=" + v);
            hash.append(k).append(v);
        });

        this.externs.forEach(e -> {
            hashItemNames.add("extern: " + e);
            hash.append(e);
        });

        this.formatting.forEach(f -> {
            hashItemNames.add("formatting: " + f);
            hash.append(f);
        });

        final LanguageMode languageOut = this.languageOut();
        hashItemNames.add("language-out: " + languageOut);
        hash.append(languageOut);

        final Optional<String> sourceMaps = this.sourceMaps();
        if(sourceMaps.isPresent()) {
            final String path = sourceMaps.get();
            hashItemNames.add("source-maps: " + path);
            hash.append(path);
        }

        this.classpathRequired.forEach(c -> {
            hashItemNames.add("classpath-required: " + c);
            hash.append(c.toString());
        });

        this.ignoredDependencies.forEach(i -> {
            hashItemNames.add("ignored-dependency: " + i);
            hash.append(i.toString());
        });

        this.javascriptSourceRequired.forEach(j -> {
            hashItemNames.add("javascript-source-required: " + j);
            hash.append(j.toString());
        });

        return hash;
    }

    // verify...........................................................................................................

    /**
     * Verifies all 3 groups of coords using the project to print all dependencies if an test has failed.
     */
    final void verifyClasspathRequiredJavascriptSourceRequiredIgnoredDependencies(final Set<J2clArtifactCoords> all,
                                                                                  final J2clDependency project) {
        verify(this.classpathRequired, "classpath-required", all, project);
        verify(this.javascriptSourceRequired, "javascript-required", all, project);
        verify(this.ignoredDependencies, "ignoredDependencies", all, project);
    }

    private static void verify(final Collection<J2clArtifactCoords> filtered,
                               final String label,
                               final Set<J2clArtifactCoords> all,
                               final J2clDependency project) {
        final Collection<J2clArtifactCoords> unknown = filtered.stream()
                .filter(c -> false == all.contains(c))
                .collect(Collectors.toList());

        if (false == unknown.isEmpty()) {
            project.log(false);

            throw new IllegalArgumentException("Unknown " + label + " dependencies: " + join(unknown));
        }
    }

    private static String join(final Collection<?> items) {
        return items.stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    // tasks............................................................................................................

    /**
     * Holds of artifacts and the artifacts it requires to complete before it can start with its own first step.
     * When the list of required (the map value) becomes empty the {@link J2clDependency coords} can have its steps started.
     */
    private final Map<J2clDependency, Set<J2clDependency>> jobs = Maps.concurrent();

    /**
     * Executes the given project.
     */
    final void execute(final J2clDependency project) throws Throwable {
        this.prepareJobs(project);

        if (0 == this.trySubmitJobs()) {
            throw new J2clException("Unable to find a leaf dependencies(dependency without dependencies), job failed.");
        }
        this.await();
    }

    private void prepareJobs(final J2clDependency artifact) {
        if (false == skipJobs(artifact) && false == this.jobs.containsKey(artifact)) {

            // keep transitive dependencies alphabetical sorted for better readability when trySubmitJob pretty prints queue processing.
            final Set<J2clDependency> required = Sets.sorted();

            this.jobs.put(artifact, required);

            for (final J2clDependency dependency : artifact.dependencies()) {
                if (skipJobs(dependency)) {
                    continue;
                }

                required.add(dependency);
                this.prepareJobs(dependency);
            }
        }
    }

    /**
     * Tests if the dependency should not have a job submitted.
     */
    private boolean skipJobs(final J2clDependency dependency) {
        final boolean skip;

        do {
            if (dependency.isAnnotationProcessor() || dependency.isAnnotationClassFiles()) {
                skip = true;
                break;
            }

            if (dependency.isJreBootstrapClassFiles() || dependency.isJreClassFiles()) {
                skip = true;
                break;
            }

            if (dependency.isJreJavascriptBootstrapFiles() || dependency.isJreJavascriptFiles()) {
                skip = true;
                break;
            }

            if (dependency.isIgnored()) {
                skip = true;
                break;
            }

            skip = false;
        } while (false);

        return skip;
    }

    /**
     * Loops over all {@link #jobs} submitting a job for each that has no required artifacts aka the value is an empty {@link Set}.
     */
    private int trySubmitJobs() {
        final TreeLogger logger = this.mavenLogger()
                .output();

        final List<J2clDependency> submits = Lists.array();

        this.executeWithLock(() -> {
            final String message;

            logger.line("Submitting jobs");
            logger.indent();
            {
                logger.line("Queue");
                logger.indent();

                //for readability sort jobs alphabetically as they will be printed and possibly submitted.....................
                final SortedMap<J2clDependency, Set<J2clDependency>> alphaSortedJobs = Maps.sorted();
                alphaSortedJobs.putAll(this.jobs);

                for (final Entry<J2clDependency, Set<J2clDependency>> artifactAndDependencies : alphaSortedJobs.entrySet()) {
                    final J2clDependency artifact = artifactAndDependencies.getKey();
                    final Set<J2clDependency> required = artifactAndDependencies.getValue();

                    logger.line(artifact.toString());
                    logger.indent();
                    {
                        if (required.isEmpty()) {
                            this.jobs.remove(artifact);
                            submits.add(artifact);
                            logger.line("Queued " + artifact + " for submission " + submits.size());
                        } else {
                            logger.line("Waiting for " + required.size() + " dependencies");
                            logger.indent();

                            required.forEach(r -> logger.line(r.toString()));

                            logger.outdent();
                        }
                    }
                    logger.outdent();
                }

                final int submitCount = submits.size();
                final int waiting = alphaSortedJobs.size() - submits.size();
                final int running = this.running.get();

                if (0 == submitCount && waiting > 0 && 0 == running) {
                    throw new J2clException(submitCount + " jobs submitted with " + waiting + " several waiting and " + running + " running.");
                }

                message = submitCount + " job(s) submitted, " + running + " running " + waiting + " waiting.";
            }
            logger.outdent();

            logger.line(message);
            logger.flush();

            submits.forEach(this::submitTask);
        });

        return submits.size();
    }

    private void submitTask(final J2clDependency task) {
        this.completionService.submit(() -> this.callable(task));
        this.running.incrementAndGet();
    }

    final Void callable(final J2clDependency task) throws Exception {
        final MavenLogger logger = this.mavenLogger();
        final String coords = task.coords().toString();
        final Instant start = Instant.now();

        logger.info(coords);
        {
            J2clStep step = this.firstStep();
            do {
                Thread.currentThread()
                        .setName(coords + "-" + step);

                step = step.execute(task)
                        .orElse(null);
            } while (null != step);
        }
        logger.info(coords + " completed, " + Duration.between(start, Instant.now()).toSeconds() + " second(s) taken");

        this.taskCompleted(task);

        return null;
    }

    private final AtomicInteger running = new AtomicInteger();

    /**
     * Finds all jobs that have the given artifact as a dependency and remove that dependency from the waiting list.
     */
    final J2clMavenContext taskCompleted(final J2clDependency completed) {
        this.executeWithLock(() -> {
            this.jobs.remove(completed);

            for (final Set<J2clDependency> dependencies : this.jobs.values()) {
                dependencies.remove(completed);
            }

            this.trySubmitJobs();
        });
        return this;
    }

    private void executeWithLock(final Runnable execute) {
        try {
            if (false == this.jobsLock.tryLock(3, TimeUnit.SECONDS)) {
                throw new J2clException("Failed to get job lock");
            }
            execute.run();
        } catch (final InterruptedException fail) {
            throw new J2clException("Failed to get job lock: " + fail.getMessage(), fail);
        } finally {
            this.jobsLock.unlock();
        }
    }

    /**
     * A lock used to by {@link #trySubmitJobs()} to avoid concurrent modification of {@link #jobs}.
     */
    private final Lock jobsLock = new ReentrantLock();

    private final CompletionService<Void> completionService;

    /**
     * Waits (aka Blocks) for all outstanding tasks to complete.
     */
    private void await() throws Throwable {
        while (false == this.executor.isTerminated()) {
            try {
                final Future<?> task = this.completionService.poll(1, TimeUnit.SECONDS);
                if (null != task) {
                    task.get();
                    if (0 == this.running.decrementAndGet()) {
                        this.executor.shutdown();
                    }
                }
            } catch (final Exception cause) {
                cause.printStackTrace();
                this.cancel(cause);
                throw cause;
            }
        }

        final Throwable cause = this.cause.get();
        if (null != cause) {
            throw cause;
        }
    }

    /**
     * Used to cancel any outstanding tasks typically done because one step has failed and any future work is pointless
     * and should be immediately aborted.
     */
    final void cancel(final Throwable cause) {
        this.cause.compareAndSet(null, cause);

        final MavenLogger logger = this.mavenLogger();
        logger.warn("Killing all running tasks");

        // TODO might be able to give Callable#toString and hope that is used by Runnable returned.
        this.executor.shutdownNow()
                .forEach(task -> logger.warn("" + MavenLogger.INDENTATION + task));
    }

    private final ExecutorService executor;

    private final AtomicReference<Throwable> cause = new AtomicReference<>();

    // logger...........................................................................................................

    /**
     * Returns a {@link MavenLogger}
     */
    final MavenLogger mavenLogger() {
        return this.logger;
    }

    private final MavenLogger logger;

    // toString.........................................................................................................

    @Override
    public String toString() {
        return this.base + " " +
                this.classpathRequired + " " +
                this.ignoredDependencies + " " +
                this.javascriptSourceRequired + " " +
                this.scope + " " +
                this.defines + " " +
                this.entryPoints() + " " +
                this.externs + " " +
                this.formatting + " " +
                this.initialScriptFilename() + " " +
                this.languageOut + " " +
                this.sourceMaps + " " +
                this.level;
    }
}
