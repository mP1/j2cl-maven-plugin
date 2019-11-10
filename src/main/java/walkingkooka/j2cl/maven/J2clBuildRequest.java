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

import com.google.common.collect.Streams;
import com.google.javascript.jscomp.CompilationLevel;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;
import walkingkooka.text.CharSequences;

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

    static J2clBuildRequest with(final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                                 final J2clClasspathScope scope,
                                 final CompilationLevel level,
                                 final Map<String, String> defines,
                                 final Set<String> externs,
                                 final List<String> entryPoints,
                                 final J2clPath initialScriptFilename,
                                 final J2clPath base,
                                 final List<J2clArtifactCoords> classpathRequired,
                                 final List<J2clArtifactCoords> javascriptSourceRequired,
                                 final List<J2clArtifactCoords> processingSkipped,
                                 final Predicate<J2clArtifactCoords> excluded,
                                 final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                                 final J2clSourcesKind sourcesKind,
                                 final J2clPath buildTarget,
                                 final J2clMavenMiddleware middleware,
                                 final ExecutorService executor,
                                 final J2clLogger logger) {
        return new J2clBuildRequest(addedDependencies,
                scope,
                level,
                defines,
                externs,
                entryPoints,
                initialScriptFilename,
                base,
                excluded,
                replaced,
                classpathRequired,
                javascriptSourceRequired,
                processingSkipped,
                sourcesKind,
                buildTarget,
                middleware,
                executor,
                logger);
    }

    private J2clBuildRequest(final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                             final J2clClasspathScope scope,
                             final CompilationLevel level,
                             final Map<String, String> defines,
                             final Set<String> externs,
                             final List<String> entryPoints,
                             final J2clPath initialScriptFilename,
                             final J2clPath base,
                             final Predicate<J2clArtifactCoords> excluded,
                             final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                             final List<J2clArtifactCoords> classpathRequired,
                             final List<J2clArtifactCoords> javascriptSourceRequired,
                             final List<J2clArtifactCoords> processingSkipped,
                             final J2clSourcesKind sourcesKind,
                             final J2clPath buildTarget,
                             final J2clMavenMiddleware middleware,
                             final ExecutorService executor,
                             final J2clLogger logger) {
        super();

        this.addedDependencies = addedDependencies;

        this.scope = scope;
        this.level = level;
        this.defines = defines;
        this.entryPoints = entryPoints;
        this.externs = externs;

        this.initialScriptFilename = initialScriptFilename;

        this.base = base;

        this.excluded = excluded;
        this.classpathRequired = classpathRequired;
        this.javascriptSourceRequired = javascriptSourceRequired;
        this.processingSkipped = processingSkipped;
        this.replaced = replaced;

        this.sourcesKind = sourcesKind;

        this.buildTarget = buildTarget;

        this.middleware = middleware;

        this.executor = executor;
        this.completionService = new ExecutorCompletionService<>(executor);

        this.logger = logger;

        final HashBuilder hash = HashBuilder.empty()
                .append(scope.toString())
                .append(level.toString());
        addedDependencies.forEach((k, v) -> {
            hash.append(k.toString());

            v.forEach(a -> hash.append(a.toString()));
        });
        defines.forEach((k, v) -> hash.append(k).append(v));
        entryPoints.forEach(hash::append);
        externs.forEach(hash::append);

        hash.append(excluded.toString());

        replaced.forEach((k, v)-> {
            hash.append(k.toString());
            hash.append(v.toString());
        });
        hash.append(sourcesKind.name());
        
        hash.append(classpathRequired.toString());
        hash.append(javascriptSourceRequired.toString());
        hash.append(processingSkipped.toString());

        this.hash = hash
                .toString();
    }

    /**
     * Get all dependencies added via the added-dependencies maven plugin parameter
     * or an empty list.
     */
    List<J2clArtifactCoords> addedDependencies(final J2clArtifactCoords coords) {
        return this.addedDependencies.getOrDefault(coords, Lists.empty());
    }

    /**
     * Added to each discovered dependency as they are discovered.
     */
    private final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies;

    final J2clClasspathScope scope;
    final CompilationLevel level;
    final Map<String, String> defines;
    final List<String> entryPoints;
    final Set<String> externs;

    final J2clPath initialScriptFilename;

    final J2clPath base;

    final J2clSourcesKind sourcesKind;

    /**
     * The target or base directory recieving all build files.
     */
    final J2clPath buildTarget;

    boolean isExcluded(final J2clArtifactCoords coords) {
        return this.excluded.test(coords) || this.replaced.containsKey(coords);
    }

    private final Predicate<J2clArtifactCoords> excluded;

    List<J2clDependency> classpathRequired() {
        return this.classpathRequired.stream()
                .map(J2clDependency::getOrFail)
                .collect(Collectors.toList());
    }

    boolean isClasspathRequired(final J2clArtifactCoords coords) {
        return this.classpathRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> classpathRequired;

    boolean isJavascriptSourceRequired(final J2clArtifactCoords coords) {
        return this.javascriptSourceRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> javascriptSourceRequired;

    /**
     * Returns the coords for all required artifacts, basically combining the {@link #classpathRequired} and {@link #javascriptSourceRequired}.
     */
    Set<J2clArtifactCoords> required() {
        return Streams.concat(this.classpathRequired.stream(), this.javascriptSourceRequired.stream())
                .collect(Collectors.toCollection(Sets::sorted));
    }

    boolean isProcessingSkipped(final J2clArtifactCoords coords) {
        return this.processingSkipped.contains(coords);
    }

    private final List<J2clArtifactCoords> processingSkipped;

    // replacements........................................................................................................

    /**
     * Accepts the coords and returns the replacement if one is available.
     */
    Optional<J2clArtifactCoords> replacement(final J2clArtifactCoords coords) {
        return Optional.ofNullable(this.replaced.get(coords));
    }

    private final Map<J2clArtifactCoords, J2clArtifactCoords> replaced;

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
        this.verifyClasspathRequiredAndjavascriptSourceRequired();
        this.prepareJobs(project);

        if (0 == this.trySubmitJobs()) {
            throw new J2clException("Unable to find a leaf dependencies(dependency without dependencies), job failed.");
        }
        this.await();
    }

    private void verifyClasspathRequiredAndjavascriptSourceRequired() {
        this.verify(this.classpathRequired, "classpath-required");
        this.verify(this.javascriptSourceRequired, "javascript-required");
        this.verify(this.processingSkipped, "transpile-excluded");
    }

    private void verify(final Collection<J2clArtifactCoords> dependencies,
                        final String label) {
        final Collection<J2clArtifactCoords> unknown = dependencies.stream()
                .filter(d -> false == J2clDependency.get(d).isPresent())
                .collect(Collectors.toList());
        if(false == unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown " + label + " dependencies: " + unknown.stream().map(J2clArtifactCoords::toString).collect(Collectors.joining()));
        }
    }

    /**
     * Traverses the dependency graph creating job for each, for dependencies that are included.
     */
    private void prepareJobs(final J2clDependency artifact) {
        //System.err.println("PREPARE JOBS " + artifact + " jobs " + (false == this.jobs.containsKey(artifact)));
        if (/*artifact.isIncluded() && */artifact.isProcessingRequired() && false == this.jobs.containsKey(artifact)) {
            final Set<J2clDependency> dependencies = artifact.dependencies(); // dependencies()
            //System.err.println("PREPARE JOBS " + artifact + " ABPUT! !!! " + dependencies);

            // keep transitive dependencies alphabetical sorted for better readability when trySubmitJob pretty prints queue processing.
            final Set<J2clDependency> required = Sets.sorted();
            this.jobs.put(artifact, required);

            dependencies.stream()
                    //.peek(d -> System.err.println("maybe " + artifact + "\t\t" + d))
                    //.filter(J2clDependency::isIncluded)
                    .filter(J2clDependency::isProcessingRequired)
                    .forEach(d -> {
                        required.add(d);
                        this.prepareJobs(d);
                    });
            //System.err.println("PREPARE JOBS " + artifact + " REQUIRED " + required);
        }
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
            final SortedMap<J2clDependency, Set<J2clDependency>> alphaSortedJobs = Maps.sorted();
            alphaSortedJobs.putAll(this.jobs);

            for (final Entry<J2clDependency, Set<J2clDependency>> artifactAndDependencies : alphaSortedJobs.entrySet()) {
                final J2clDependency artifact = artifactAndDependencies.getKey();
                final Set<J2clDependency> required = artifactAndDependencies.getValue();

                logger.printLine(artifact.toString());
                logger.indent();

                if (required.isEmpty()) {
                    this.jobs.remove(artifact);
                    submit.add(artifact.job());
                    logger.printLine("Queued " + artifact + " for submission " + submit.size());
                } else {
                    logger.printLine("Waiting for " + required.size() + " dependencies");
                    logger.indent();

                    required.forEach(r -> logger.printLine(r.toString()));

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
                this.excluded + " " + 
                this.replaced + " " +
                this.classpathRequired + " " +
                this.javascriptSourceRequired + " " +
                this.processingSkipped + " ";
    }
}
