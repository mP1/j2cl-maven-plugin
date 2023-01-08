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
import java.util.concurrent.Callable;
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

    final void computeEntryPointAndInitialScriptFilenameHash(final List<String> entryPoints,
                                                             final J2clPath initialScriptFilename,
                                                             final Set<String> hashItemNames,
                                                             final HashBuilder hash) {
        entryPoints.forEach(
                e -> {
                    hashItemNames.add("entry-points: " + e);
                    hash.append(e);
                }
        );

        final String initialScriptFilenameString = initialScriptFilename.toString();
        hashItemNames.add("initial-script-filename: " + initialScriptFilenameString);
        hash.append(initialScriptFilenameString);
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

    public abstract J2clPath initialScriptFilename(final J2clArtifact artifact);

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

    // tasks............................................................................................................

    public final List<J2clPath> sources(final J2clArtifact artifact) {
        return artifact.isDependency() ?
                this.unpackOutputDirectory(artifact) :
                artifact.sourceRoots();
    }

    private List<J2clPath> unpackOutputDirectory(final J2clArtifact artifact) {
        return Lists.of(
                artifact.taskDirectory(J2clTaskKind.UNPACK)
                        .output()
        );
    }

    public abstract J2clPath compiledBinaries(final J2clArtifact artifact);

    final J2clPath compiledBinariesTaskDirectory(final J2clArtifact artifact) {
        return artifact.taskDirectory(J2clTaskKind.JAVAC_ANNOTATION_PROCESSORS_ENABLED)
                .output();
    }

    public final String directoryName(final J2clArtifact artifact,
                                      final J2clTaskKind kind) {
        return kind.directoryName(
                this.tasks(artifact)
                        .indexOf(kind)
        );
    }

    final J2clTaskKind firstTaskKind(final J2clArtifact artifact) {
        return this.tasks(artifact)
                .get(0);
    }

    final Optional<J2clTaskKind> nextTask(final J2clArtifact artifact,
                                          final J2clTaskKind current) {
        final List<J2clTaskKind> tasks = this.tasks(artifact);

        final int index = tasks.indexOf(current);
        return Optional.ofNullable(
                index + 1 < tasks.size() ?
                        tasks.get(index + 1) :
                        null
        );
    }

    abstract List<J2clTaskKind> tasks(final J2clArtifact artifact);

    /**
     * When true directories like !SUCCESS should be checked and honoured.
     * This will be false when running the file event watching phase of the watch task. It is necessary to skip
     * this check because it is possible for a build to be interrupted and then need to be restarted as in the case
     * of multiple file events such as the deletion of a class file and recreation by the IDE.
     */
    abstract public boolean shouldCheckCache();

    // tasks............................................................................................................

    /**
     * Holds of artifacts and the artifacts it requires to complete before it can start with its own first task.
     * When the {link Set} of required (the map value) becomes empty the {@link J2clArtifact coords} can have its tasks started.
     */
    private final Map<J2clArtifact, Set<J2clArtifact>> tasks = Maps.concurrent();

    /**
     * Executes the given project.
     */
    final void prepareAndStart(final J2clArtifact project,
                               final TreeLogger logger) {
        this.tasks.clear();

        this.prepareTasks(project);

        if (0 == this.trySubmitTasks(logger)) {
            throw new J2clException("Unable to find a leaf dependencies(dependency without dependencies), task failed.");
        }
    }

    private ExecutorService executor() {
        final int threadPoolSize = this.threadPoolSize;

        return Executors.newFixedThreadPool(0 != threadPoolSize ?
                threadPoolSize :
                Runtime.getRuntime().availableProcessors() * 2);
    }

    private void prepareTasks(final J2clArtifact artifact) {
        if (false == this.tasks.containsKey(artifact)) {

            // keep transitive dependencies alphabetical sorted for better readability when trySubmitTasks pretty prints queue processing.
            final Set<J2clArtifact> required = Sets.sorted();

            this.tasks.put(artifact, required);

            if (!this.shouldSkipSubmittingDependencyTasks()) {
                for (final J2clArtifact dependency : artifact.dependencies()) {
                    if (dependency.shouldSkipTaskSubmit()) {
                        continue;
                    }

                    required.add(dependency);
                    this.prepareTasks(dependency);
                }
            }
        }
    }

    /**
     * Only watch during the file event watch phase will return true.
     */
    abstract boolean shouldSkipSubmittingDependencyTasks();

    /**
     * Loops over all {@link #tasks} submitting a task for each that has no required artifacts aka the value is an empty {@link Set}.
     */
    private int trySubmitTasks(final TreeLogger logger) {
        final List<J2clArtifact> submits = Lists.array();

        this.executeWithLock(() -> {
            final String message;

            logger.line("Submitting tasks");
            logger.indent();
            {
                logger.line("Queue");
                logger.indent();
                {

                    //for readability sort tasks alphabetically as they will be printed and possibly submitted.....................
                    final SortedMap<J2clArtifact, Set<J2clArtifact>> tasksAlphaSorted = Maps.sorted();
                    tasksAlphaSorted.putAll(this.tasks);

                    for (final Entry<J2clArtifact, Set<J2clArtifact>> artifactAndDependencies : tasksAlphaSorted.entrySet()) {
                        final J2clArtifact artifact = artifactAndDependencies.getKey();
                        final Set<J2clArtifact> required = artifactAndDependencies.getValue();

                        logger.line(artifact.toString());
                        logger.indent();
                        {
                            if (required.isEmpty()) {
                                this.tasks.remove(artifact);
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
                    final int waiting = tasksAlphaSorted.size() - submits.size();
                    final int running = this.running.get();

                    if (0 == submitCount && waiting > 0 && 0 == running) {
                        throw new J2clException(submitCount + " tasks submitted with " + waiting + " several waiting and " + running + " running.");
                    }

                    message = submitCount + " task(s) submitted, " + running + " running " + waiting + " waiting.";
                }
                logger.outdent();
            }
            logger.outdent();

            logger.line(message);
            logger.flush();

            submits.forEach(
                    (d) -> this.submitTask(
                            () -> this.callable(
                                    d,
                                    logger
                            )
                    )
            );
        });

        return submits.size();
    }

    /**
     * Lazily create {@link ExecutorService} and {@link CompletionService}, this helps support the watch task which
     * creates a new instance of each for each file event.
     */
    public void submitTask(final Callable<Void> task) {
        // lazily create ExecutorService, the watch service will clear any ExecutorService & CompletionService after each build run.
        if (null == this.executorService.get()) {
            final ExecutorService executorService = this.executor();
            this.executorService.set(executorService);
            this.completionService.set(
                    new ExecutorCompletionService<>(executorService)
            );
            this.running.set(0);
        }

        this.completionService.get()
                .submit(task);

        this.running.incrementAndGet(); // increment here because watch task will submit a Callable and it shouldnt be counted by await()
    }

    private Void callable(final J2clArtifact artifact,
                          final TreeLogger logger) throws Exception {
        final Thread thread = Thread.currentThread();
        final String threadName = thread.getName();

        try {
            final String coords = artifact.coords().toString();
            final Instant start = Instant.now();

            logger.line(coords);
            logger.indent();
            {
                J2clTaskKind kind = this.firstTaskKind(artifact);

                do {
                    // skip this and any more tasks, watch task probably issued a shutdown because of a new file watch event.
                    if (!this.isRunning()) {
                        break;
                    }
                    thread.setName(coords + "-" + kind);

                    kind = kind.execute(
                            artifact,
                            logger,
                            this
                    ).orElse(null);

                    thread.setName(threadName);
                } while (null != kind);
            }
            logger.outdent();

            logger.line(coords + " completed.");
            logger.timeTaken(
                    Duration.between(
                            start,
                            Instant.now()
                    )
            );

            this.taskCompleted(
                    artifact,
                    logger
            );
        } finally {
            thread.setName(threadName);
        }

        return null;
    }

    private final AtomicInteger running = new AtomicInteger();

    /**
     * Finds all tasks that have the given artifact as a dependency and remove that dependency from the waiting list.
     */
    final J2clMavenContext taskCompleted(final J2clArtifact completed,
                                         final TreeLogger logger) {
        this.executeWithLock(() -> {
            this.tasks.remove(completed);

            for (final Set<J2clArtifact> dependencies : this.tasks.values()) {
                dependencies.remove(completed);
            }

            this.trySubmitTasks(logger);
        });
        return this;
    }

    private void executeWithLock(final Runnable execute) {
        try {
            if (false == this.tasksLock.tryLock(3, TimeUnit.SECONDS)) {
                throw new J2clException("Failed to get tasks lock");
            }
            execute.run();
        } catch (final InterruptedException fail) {
            throw new J2clException("Failed to get tasks lock: " + fail.getMessage(), fail);
        } finally {
            this.tasksLock.unlock();
        }
    }

    /**
     * A lock used to by {@link #trySubmitTasks(TreeLogger)} to avoid concurrent modification of {@link #tasks}.
     */
    private final Lock tasksLock = new ReentrantLock();

    /**
     * Waits (aka Blocks) for all outstanding tasks to complete.
     */
    public void waitUntilCompletion() throws Throwable {
        final ExecutorService executorService = this.executorService.get();
        final CompletionService<?> completionService = this.completionService.get();

        if (null != executorService && null != completionService) {
            while (false == executorService.isTerminated()) {
                try {
                    final Future<?> task = completionService.poll(
                            AWAIT_POLL_TIMEOUT,
                            TimeUnit.MILLISECONDS
                    );
                    if (null != task) {
                        task.get();
                        if (0 == this.running.decrementAndGet()) {
                            this.executorService.set(null);
                            this.completionService.set(null);

                            executorService.shutdown();
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
    }

    private final static int AWAIT_POLL_TIMEOUT = 50;

    /**
     * Used to cancel any outstanding tasks typically done because one task has failed and any future work is pointless
     * and should be immediately aborted.
     */
    final void cancel(final Throwable cause) {
        final ExecutorService executorService = this.executorService.get();

        if (this.isRunning()) {
            this.cause.compareAndSet(null, cause);

            final MavenLogger logger = this.mavenLogger();
            logger.warn("Killing all running tasks");

            executorService.shutdown();

            while (!executorService.isTerminated()) {
                try {
                    executorService.awaitTermination(
                            AWAIT_POLL_TIMEOUT,
                            TimeUnit.MILLISECONDS
                    );
                } catch (final InterruptedException interrupted) {
                    interrupted.printStackTrace();
                }
            }

            logger.warn("Cancelled tasks completed");
        }

        this.executorService.set(null);
        this.completionService.set(null);
        this.running.set(0);
    }

    /**
     * Returns true if the {@link ExecutorService} is still alive and executing new or pending tasks.
     */
    private boolean isRunning() {
        final ExecutorService executorService = this.executorService.get();
        return null != executorService && !executorService.isShutdown();
    }

    /**
     * This value is used when creating a {@link ExecutorService}
     */
    private final int threadPoolSize;

    /**
     * The build and test goals only create a single {@link ExecutorService}, while the watch goal will create
     * a new {@link ExecutorService} each time it runs.
     */
    private final AtomicReference<ExecutorService> executorService = new AtomicReference<>();

    /**
     * An instance is created when a new {@link ExecutorService} is created.
     */
    private final AtomicReference<CompletionService<Void>> completionService = new AtomicReference<>();

    private final AtomicReference<Throwable> cause = new AtomicReference<>();

    // hash..............................................................................................................

    /**
     * Returns a sha1 hash in hex digits that uniquely identifies this request using components.
     * Requests should cache the hash for performance reasons.
     */
    public abstract void computeHash(final J2clArtifact artifact,
                                     final HashBuilder hash,
                                     final Set<String> hashItemNames);

    /**
     * Creates a {@link HashBuilder} and hashes most of the properties of a request.
     */
    final void computeHash0(final J2clArtifact artifact,
                            final HashBuilder hash,
                            final Set<String> hashItemNames) {
        if (artifact.isDependency()) {
            final J2clClasspathScope scope = this.scope();
            hashItemNames.add("scope: " + scope);
            hash.append(scope);

            final J2clSourcesKind sourcesKind = this.sourcesKind();
            hashItemNames.add("sources-kind: " + sourcesKind);
            hash.append(sourcesKind);
        }

        final CompilationLevel level = this.level;
        hashItemNames.add("level: " + level);
        hash.append(scope);

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
    }

    // verify...........................................................................................................

    /**
     * Verifies all 3 groups of coords using the project to print all dependencies if a test has failed.
     */
    final void verifyClasspathRequiredJavascriptSourceRequiredIgnoredDependencies(final Set<J2clArtifactCoords> all,
                                                                                  final J2clArtifact project,
                                                                                  final TreeLogger logger) {
        verify(this.classpathRequired, "classpath-required", all, project, logger);
        verify(this.javascriptSourceRequired, "javascript-required", all, project, logger);
        verify(this.ignoredDependencies, "ignoredDependencies", all, project, logger);
    }

    private static void verify(final Collection<J2clArtifactCoords> filtered,
                               final String label,
                               final Set<J2clArtifactCoords> all,
                               final J2clArtifact project,
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
                this.languageOut + " " +
                this.sourceMaps + " " +
                this.level;
    }
}
