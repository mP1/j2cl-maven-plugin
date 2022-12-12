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
import walkingkooka.j2cl.maven.log.MavenLogger;
import walkingkooka.j2cl.maven.log.TreeLogger;

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
import java.util.concurrent.Executors;
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

    J2clMavenContext(final J2clPath cache,
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
                     final int threadPoolSize,
                     final MavenLogger logger) {
        super();

        this.cache = cache;
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
        this.threadPoolSize = threadPoolSize;
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
     * The cache directory, which contains the output of building all dependencies.
     */
    public final J2clPath cache() {
        return this.cache;
    }

    /**
     * The base or cache directory.
     */
    private final J2clPath cache;

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

    // steps............................................................................................................

    public final String directoryName(final J2clStep step) {
        return step.directoryName(
                this.steps()
                        .indexOf(step)
        );
    }

    final J2clStep firstStep() {
        return this.steps().get(0);
    }

    final Optional<J2clStep> nextStep(final J2clStep current) {
        final List<J2clStep> steps = this.steps();

        final int index = steps.indexOf(current);
        return Optional.ofNullable(
                index + 1 < steps.size() ?
                        steps.get(index + 1) :
                        null
        );
    }

    abstract List<J2clStep> steps();

    // tasks............................................................................................................

    /**
     * Holds of artifacts and the artifacts it requires to complete before it can start with its own first step.
     * When the {link Set} of required (the map value) becomes empty the {@link J2clDependency coords} can have its steps started.
     */
    private final Map<J2clDependency, Set<J2clDependency>> jobs = Maps.concurrent();

    /**
     * Executes the given project.
     */
    final void execute(final J2clDependency project,
                       final TreeLogger logger) throws Throwable {
        this.jobs.clear();

        this.prepareJobs(project);

        final ExecutorService executorService = this.executor();
        this.executorService = executorService;
        this.completionService = new ExecutorCompletionService<>(executorService);

        if (0 == this.trySubmitJobs(logger)) {
            throw new J2clException("Unable to find a leaf dependencies(dependency without dependencies), job failed.");
        }
        this.await();
    }

    private ExecutorService executor() {
        final int threadPoolSize = this.threadPoolSize;

        return Executors.newFixedThreadPool(0 != threadPoolSize ?
                threadPoolSize :
                Runtime.getRuntime().availableProcessors() * 2);
    }

    private void prepareJobs(final J2clDependency artifact) {
        if (false == this.jobs.containsKey(artifact)) {

            // keep transitive dependencies alphabetical sorted for better readability when trySubmitJob pretty prints queue processing.
            final Set<J2clDependency> required = Sets.sorted();

            this.jobs.put(artifact, required);

            if (!this.shouldSkipSubmittingDependencyJobs()) {
                for (final J2clDependency dependency : artifact.dependencies()) {
                    if (dependency.shouldSkipJobSubmit()) {
                        continue;
                    }

                    required.add(dependency);
                    this.prepareJobs(dependency);
                }
            }
        }
    }

    /**
     * Only watch during the file event watch phase will return true.
     */
    abstract boolean shouldSkipSubmittingDependencyJobs();

    /**
     * Loops over all {@link #jobs} submitting a job for each that has no required artifacts aka the value is an empty {@link Set}.
     */
    private int trySubmitJobs(final TreeLogger logger) {
        final List<J2clDependency> submits = Lists.array();

        this.executeWithLock(() -> {
            final String message;

            logger.line("Submitting jobs");
            logger.indent();
            {
                logger.line("Queue");
                logger.indent();
                {

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
                                {
                                    required.forEach(r -> logger.line(r.toString()));
                                }
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
            }
            logger.outdent();

            logger.line(message);
            logger.flush();

            submits.forEach(
                    (d) -> this.submitTask(
                            d,
                            logger
                    )
            );
        });

        return submits.size();
    }

    private void submitTask(final J2clDependency task,
                            final TreeLogger logger) {
        this.completionService.submit(
                () -> this.callable(
                        task,
                        logger
                )
        );
        this.running.incrementAndGet();
    }

    final Void callable(final J2clDependency task,
                        final TreeLogger logger) throws Exception {
        final Thread thread = Thread.currentThread();
        final String threadName = thread.getName();

        try {
            final String coords = task.coords().toString();
            final Instant start = Instant.now();

            logger.line(coords);
            logger.indent();
            {
                J2clStep step = this.firstStep();
                do {
                    thread.setName(coords + "-" + step);

                    step = step.execute(
                            task,
                            logger,
                            this
                    ).orElse(null);

                    thread.setName(threadName);
                } while (null != step);
            }
            logger.outdent();
            logger.line(
                    coords +
                            " completed, " +
                            TreeLogger.prettyTimeTaken(
                                    Duration.between(
                                            start,
                                            Instant.now()
                                    )
                            )
            );

            this.taskCompleted(
                    task,
                    logger
            );
        } finally {
            thread.setName(threadName);
        }

        return null;
    }

    private final AtomicInteger running = new AtomicInteger();

    /**
     * Finds all jobs that have the given artifact as a dependency and remove that dependency from the waiting list.
     */
    final J2clMavenContext taskCompleted(final J2clDependency completed,
                                         final TreeLogger logger) {
        this.executeWithLock(() -> {
            this.jobs.remove(completed);

            for (final Set<J2clDependency> dependencies : this.jobs.values()) {
                dependencies.remove(completed);
            }

            this.trySubmitJobs(logger);
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
     * A lock used to by {@link #trySubmitJobs(TreeLogger)} to avoid concurrent modification of {@link #jobs}.
     */
    private final Lock jobsLock = new ReentrantLock();

    /**
     * Waits (aka Blocks) for all outstanding tasks to complete.
     */
    private void await() throws Throwable {
        while (false == this.executorService.isTerminated()) {
            try {
                final Future<?> task = this.completionService.poll(50, TimeUnit.MILLISECONDS);
                if (null != task) {
                    task.get();
                    if (0 == this.running.decrementAndGet()) {
                        this.executorService.shutdown();
                    }
                }
            } catch (final Exception cause) {
                cause.printStackTrace();
                this.cancel(cause);
                throw cause;
            }
        }

        this.executorService = null;
        this.completionService = null;

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
        this.executorService.shutdownNow()
                .forEach(task -> logger.warn("" + MavenLogger.INDENTATION + task));
    }

    /**
     * This value is used when creating a {@link ExecutorService}
     */
    private final int threadPoolSize;

    /**
     * The build and test goals only create a single {@link ExecutorService}, while the watch goal will create
     * a new {@link ExecutorService} each time it runs.
     */
    private ExecutorService executorService;

    /**
     * An instance is created when a new {@link ExecutorService} is created.
     */
    private CompletionService<Void> completionService;

    private final AtomicReference<Throwable> cause = new AtomicReference<>();

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
        if (sourceMaps.isPresent()) {
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
     * Verifies all 3 groups of coords using the project to print all dependencies if a test has failed.
     */
    final void verifyClasspathRequiredJavascriptSourceRequiredIgnoredDependencies(final Set<J2clArtifactCoords> all,
                                                                                  final J2clDependency project,
                                                                                  final TreeLogger logger) {
        verify(this.classpathRequired, "classpath-required", all, project, logger);
        verify(this.javascriptSourceRequired, "javascript-required", all, project, logger);
        verify(this.ignoredDependencies, "ignoredDependencies", all, project, logger);
    }

    private static void verify(final Collection<J2clArtifactCoords> filtered,
                               final String label,
                               final Set<J2clArtifactCoords> all,
                               final J2clDependency project,
                               final TreeLogger logger) {
        final Collection<J2clArtifactCoords> unknown = filtered.stream()
                .filter(c -> false == all.contains(c))
                .collect(Collectors.toList());

        if (false == unknown.isEmpty()) {
            project.log(
                    false,
                    logger
            );

            throw new IllegalArgumentException("Unknown " + label + " dependencies: " + join(unknown));
        }
    }

    private static String join(final Collection<?> items) {
        return items.stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    // MAVEN..............................................................................................................

    final J2clMavenMiddleware mavenMiddleware() {
        return this.middleware;
    }

    private final J2clMavenMiddleware middleware;

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
        return this.cache + " " +
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
