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
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Context about a build request
 */
final class J2clBuildRequest {

    static J2clBuildRequest with(final J2clClasspathScope scope,
                                 final CompilationLevel level,
                                 final Map<String, String> defines,
                                 final Set<String> externs,
                                 final List<String> entryPoints,
                                 final J2clPath initialScriptFilename,
                                 final J2clPath base,
                                 final Predicate<J2clArtifactCoords> bootstrap,
                                 final Predicate<J2clArtifactCoords> jre,
                                 final J2clPath buildTarget,
                                 final J2clMavenMiddleware middleware,
                                 final ExecutorService executor,
                                 final J2clLogger logger) {
        return new J2clBuildRequest(scope,
                level,
                defines,
                externs,
                entryPoints,
                initialScriptFilename,
                base,
                bootstrap,
                jre,
                buildTarget,
                middleware,
                executor,
                logger);
    }

    private J2clBuildRequest(final J2clClasspathScope scope,
                             final CompilationLevel level,
                             final Map<String, String> defines,
                             final Set<String> externs,
                             final List<String> entryPoints,
                             final J2clPath initialScriptFilename,
                             final J2clPath base,
                             final Predicate<J2clArtifactCoords> javacBootstrap,
                             final Predicate<J2clArtifactCoords> jre,
                             final J2clPath buildTarget,
                             final J2clMavenMiddleware middleware,
                             final ExecutorService executor,
                             final J2clLogger logger) {
        super();

        this.scope = scope;
        this.level = level;
        this.defines = defines;
        this.entryPoints = entryPoints;
        this.externs = externs;

        this.initialScriptFilename = initialScriptFilename;

        this.base = base;
        this.javacBootstrap = javacBootstrap;
        this.jre = jre;
        this.buildTarget = buildTarget;

        this.middleware = middleware;

        this.executor = executor;
        this.completionService = new ExecutorCompletionService<>(executor);

        this.logger = logger;

        final HashBuilder hash = HashBuilder.empty()
                .append(scope.toString())
                .append(level.toString());
        defines.forEach((k, v) -> hash.append(k).append(v));
        externs.forEach(hash::append);
        entryPoints.forEach(hash::append);

        this.hash = hash
                .toString();
    }

    final J2clClasspathScope scope;
    final CompilationLevel level;
    final Map<String, String> defines;
    final List<String> entryPoints;
    final Set<String> externs;

    final J2clPath initialScriptFilename;

    final J2clPath base;

    /**
     * The target or base directory recieving all build files.
     */
    final J2clPath buildTarget;

    boolean isJavacBootstrap(final J2clArtifactCoords coords) {
        return this.javacBootstrap.test(coords);
    }

    private final Predicate<J2clArtifactCoords> javacBootstrap;

    boolean isJre(final J2clArtifactCoords coords) {
        return this.jre.test(coords);
    }

    private final Predicate<J2clArtifactCoords> jre;

    // MAVEN..............................................................................................................

    J2clMavenMiddleware mavenMiddleware() {
        return this.middleware;
    }

    private final J2clMavenMiddleware middleware;

    // hash..............................................................................................................

    /**
     * Returns a sha1 hash in hex digits that uniquely identifies this compile task without hasshing dependencies
     */
    String hash() {
        return this.hash;
    }

    private final String hash;

    // logger...........................................................................................................

    /**
     * Returns a {@link J2clLogger}
     */
    J2clLogger logger() {
        return this.logger;
    }

    private final J2clLogger logger;

    // tasks............................................................................................................

    /**
     * Holds of artifacts and the artifacts it requires to complete before it can start with its own first step.
     * When the list of required (the map value) becomes empty the {@link J2clDependency coords} can have its steps started.
     */
    private final Map<J2clDependency, Set<J2clDependency>> jobs = Maps.concurrent();

    /**
     * Executes the given project.
     */
    void execute(final J2clDependency project) throws Throwable {
        project.prettyPrintDependencies();
        this.verifyBootstrapAndJreIdentified(project);

        this.prepareJobs(project);

        if (0 == this.trySubmitJobs()) {
            throw new J2clException("Unable to find a leaf dependencies(dependency without dependencies), job failed.");
        }
        this.await();
    }

    private void verifyBootstrapAndJreIdentified(final J2clDependency project) {
        boolean javacBootstrap = false;
        boolean jre = false;

        for (J2clDependency potential : project.dependenciesIncludingTransitives()) {
            javacBootstrap |= potential.isJavacBootstrap();
            jre |= potential.isJreBinary();
        }

        if (false == javacBootstrap) {
            throw new J2clException("Javac bootstrap dependency (" + this.javacBootstrap + ") not found in dependencies");
        }
        if (false == jre) {
            throw new J2clException("JRE dependency (" + this.jre + ") with classes not found in dependencies");
        }
    }

    /**
     * Traverses the dependency graph creating job for each. When this completes
     */
    private void prepareJobs(final J2clDependency artifact) {
        final Set<J2clDependency> dependencies = artifact.dependencies();

        // keep transitive dependencies alphabetical sorted for better readability when trySubmitJob pretty prints queue processing.
        this.jobs.put(artifact,
                dependencies.stream()
                        .peek(this::prepareJobs)
                        .collect(Collectors.toCollection(() -> Sets.sorted(J2clDependency.ALPHABETICAL_SORTER))));
    }

    /**
     * Loops over all {@link #jobs} submitting a job for each that has no required artifacts aka the value is an empty {@link Set}.
     */
    private int trySubmitJobs() {
        final J2clLogger j2clLogger = this.logger();

        final J2clLinePrinter logger = J2clLinePrinter.with(j2clLogger.printer(j2clLogger::debug));
        logger.printLine("Submitting jobs");
        logger.indent();
        logger.printLine("Queue");
        logger.indent();

        final List<Callable<J2clDependency>> submit = Lists.array();
        this.executeWithLock(() -> {

            //for readability sort jobs alphabetically as they will be printed and possibly submitted.....................
            final SortedMap<J2clDependency, Set<J2clDependency>> alphaSortedJobs = Maps.sorted(J2clDependency.ALPHABETICAL_SORTER);
            alphaSortedJobs.putAll(this.jobs);

            for (final Entry<J2clDependency, Set<J2clDependency>> artifactAndDependencies : alphaSortedJobs.entrySet()) {
                final J2clDependency artifact = artifactAndDependencies.getKey();
                final Set<J2clDependency> required = artifactAndDependencies.getValue();

                logger.printLine(artifact.coords().toString());
                logger.indent();

                if (required.isEmpty()) {
                    this.jobs.remove(artifact);
                    submit.add(artifact.job());
                    logger.printLine("Queued " + artifact.coords() + " for submission " + submit.size());
                } else {
                    logger.printLine("Waiting for " + required.size() + " dependencies");
                    logger.indent();

                    required.forEach(r -> logger.printLine(r.coords().toString()));

                    logger.outdent();
                }

                logger.outdent();
            }

            final int waiting = alphaSortedJobs.size() - submit.size();

            logger.outdent();
            logger.printLine(submit.size() + " job(s) submitted, " + waiting + " waiting!!");
            logger.flush();

            submit.forEach(this::submitTask);
        });

        return submit.size();
    }

    private void submitTask(final Callable<J2clDependency> task) {
        this.completionService.submit(task);
        this.running.incrementAndGet();
    }

    private final AtomicInteger running = new AtomicInteger();

    /**
     * Finds all jobs that have the given artifact as a dependency and remove that dependency from the waiting list.
     */
    J2clBuildRequest taskCompleted(final J2clDependency completed) {
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
            if (false == this.jobsLock.tryLock(10, TimeUnit.SECONDS)) {
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

    private final CompletionService<J2clDependency> completionService;

    /**
     * Waits (aka Blocks) for all outstanding tasks to complete.
     */
    private void await() throws Throwable {
        while (false == this.executor.isTerminated()) {
            try {
                final Future<?> task = this.completionService.poll(5, TimeUnit.SECONDS);
                if(null != task) {
                    if( 0 == this.running.decrementAndGet()) {
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
    void cancel(final Throwable cause) {
        final J2clLogger logger = this.logger();
        logger.warn("Killing all running tasks");

        // TODO might be able to give Callable#toString and hope that is used by Runnable returned.
        this.executor.shutdownNow()
                .forEach(task -> logger.warn("" + J2clLogger.INDENTATION + task));
        this.cause.compareAndSet(null, cause);
    }

    private final ExecutorService executor;

    private final AtomicReference<Throwable> cause = new AtomicReference<>();

    // toString.........................................................................................................

    @Override
    public String toString() {
        return this.scope + " " +
                this.level + " " +
                this.defines + " " +
                this.entryPoints + " " +
                this.externs + " " +
                this.initialScriptFilename + " " +
                this.base + " " +
                this.javacBootstrap + " ";
    }
}
