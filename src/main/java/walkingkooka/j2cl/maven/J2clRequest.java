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
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;
import walkingkooka.collect.set.Sets;

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
import java.util.stream.Stream;

/**
 * Context about a build/test request. It contains common properties shared by different phases of the build/test process,
 * including classpaths, closure compiler parameters and maven scoping which is used to select dependencies/artifacts.
 */
abstract class J2clRequest {

    J2clRequest(final J2clPath base,
                        final J2clPath target,
                        final J2clClasspathScope scope,
                        final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies,
                        final List<J2clArtifactCoords> classpathRequired,
                        final List<Predicate<J2clArtifactCoords>> excluded,
                        final List<J2clArtifactCoords> javascriptSourceRequired,
                        final List<J2clArtifactCoords> processingSkipped,
                        final Map<J2clArtifactCoords, J2clArtifactCoords> replaced,
                        final CompilationLevel level,
                        final Map<String, String> defines,
                        final Set<String> externs,
                        final Set<ClosureFormattingOption> formatting,
                        final LanguageMode languageOut,
                        final J2clMavenMiddleware middleware,
                        final ExecutorService executor,
                        final J2clLogger logger) {
        super();

        this.base = base;
        this.target = target;
        this.scope = scope;

        this.addedDependencies = addedDependencies;
        this.classpathRequired = classpathRequired;
        this.excluded = excluded;

        this.unusedExcluded = Sets.ordered();
        this.unusedExcluded.addAll(excluded);

        this.javascriptSourceRequired = javascriptSourceRequired;
        this.processingSkipped = processingSkipped;
        this.replaced = replaced;

        this.level = level;
        this.defines = defines;
        this.externs = externs;
        this.formatting = formatting;
        this.languageOut = languageOut;

        this.middleware = middleware;
        this.executor = executor;
        this.completionService = new ExecutorCompletionService<>(executor);
        this.logger = logger;
    }

    /**
     * Classpath scope used to filter artifacts.
     */
    final J2clClasspathScope scope() {
        return this.scope;
    }

    /**
     * Classpath scope used to filter artifacts.
     */
    private J2clClasspathScope scope;

    /**
     * The base or cache directory.
     */
    final J2clPath base() {
        return this.base;
    }

    /**
     * The base or cache directory.
     */
    private final J2clPath base;

    final J2clPath target() {
        return this.target;
    }

    /**
     * The target or base directory receiving all build files.
     */
    final J2clPath target;

    // dependencies.....................................................................................................

    /**
     * Get all dependencies added via the added-dependencies maven plugin parameter
     * or an empty list.
     */
    final List<J2clArtifactCoords> addedDependencies(final J2clArtifactCoords coords) {
        return this.addedDependencies.getOrDefault(coords, Lists.empty());
    }

    /**
     * Added to each discovered dependency as they are discovered.
     */
    private final Map<J2clArtifactCoords, List<J2clArtifactCoords>> addedDependencies;

    final boolean isExcluded(final J2clArtifactCoords coords) {
        return this.excluded.stream()
                .anyMatch(p -> {
                    final boolean excluded = p.test(coords);
                    if (excluded) {
                        this.unusedExcluded.remove(p);
                    }
                    return excluded;
                }) ||
                this.replaced.containsKey(coords);
    }

    private final List<Predicate<J2clArtifactCoords>> excluded;

    /**
     * Tracks all excluded-dependencies {@link Predicate} that have not matched at least one dependency.
     * This will be used to later report and fail unused/unnecessary dependencies.
     */
    private final Set<Predicate<J2clArtifactCoords>> unusedExcluded;

    final List<J2clDependency> classpathRequired() {
        return this.classpathRequired.stream()
                .map(J2clDependency::getOrFail)
                .collect(Collectors.toList());
    }

    final boolean isClasspathRequired(final J2clArtifactCoords coords) {
        return this.classpathRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> classpathRequired;

    final boolean isJavascriptSourceRequired(final J2clArtifactCoords coords) {
        return this.javascriptSourceRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> javascriptSourceRequired;

    /**
     * Returns the coords for all required artifacts, basically combining the {@link #classpathRequired} and {@link #javascriptSourceRequired}.
     */
    final Set<J2clArtifactCoords> required() {
        return Stream.concat(this.classpathRequired.stream(), this.javascriptSourceRequired.stream())
                .collect(Collectors.toCollection(Sets::sorted));
    }

    final boolean isProcessingSkipped(final J2clArtifactCoords coords) {
        return this.processingSkipped.contains(coords);
    }

    private final List<J2clArtifactCoords> processingSkipped;

    // replacements........................................................................................................

    /**
     * Accepts the coords and returns the replacement if one is available.
     */
    final Optional<J2clArtifactCoords> replacement(final J2clArtifactCoords coords) {
        return Optional.ofNullable(this.replaced.get(coords));
    }

    private final Map<J2clArtifactCoords, J2clArtifactCoords> replaced;

    // java.............................................................................................................

    abstract J2clSourcesKind sourcesKind();

    // closure.........................................................................................................

    final CompilationLevel level() {
        return this.level;
    }

    private CompilationLevel level;

    final Map<String, String> defines() {
        return this.defines;
    }

    private final Map<String, String> defines;

    abstract List<String> entryPoints();

    final Set<String> externs() {
        return this.externs;
    }

    private final Set<String> externs;

    final Set<ClosureFormattingOption> formatting() {
        return this.formatting;
    }

    private final Set<ClosureFormattingOption> formatting;

    abstract J2clPath initialScriptFilename();

    final LanguageMode languageOut() {
        return this.languageOut;
    }

    private final LanguageMode languageOut;

    // testTimeout......................................................................................................

    abstract int testTimeout();

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
    abstract String hash();

    /**
     * Creates a {@link HashBuilder} and hashes most of the properties of a request.
     */
    final HashBuilder computeHash() {
        final HashBuilder hash = HashBuilder.empty()
                .append(this.scope.toString())
                .append(this.level.toString());
        hash.append(this.sourcesKind().name());

        this.addedDependencies.forEach((k, v) -> {
            hash.append(k.toString());
            v.forEach(a -> hash.append(a.toString()));
        });
        hash.append(this.classpathRequired.toString());
        hash.append(this.excluded.toString());
        hash.append(this.javascriptSourceRequired.toString());
        hash.append(this.processingSkipped.toString());
        this.replaced.forEach((k, v) -> {
            hash.append(k.toString());
            hash.append(v.toString());
        });

        this.defines.forEach((k, v) -> hash.append(k).append(v));
        this.externs.forEach(hash::append);
        this.formatting.forEach(hash::append);
        hash.append(this.languageOut);

        return hash;
    }

    // logger...........................................................................................................

    /**
     * Returns a {@link J2clLogger}
     */
    final J2clLogger logger() {
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
    final void execute(final J2clDependency project) throws Throwable {
        this.prepareJobs(project);

        if (0 == this.trySubmitJobs()) {
            throw new J2clException("Unable to find a leaf dependencies(dependency without dependencies), job failed.");
        }
        this.await();
    }

    final void verifyArtifactCoords() {
        this.verify(this.classpathRequired, "classpath-required");
        this.verify(this.javascriptSourceRequired, "javascript-required");
        this.verifyExcluded();
        this.verify(this.processingSkipped, "processing-skipped");
    }

    private void verify(final Collection<J2clArtifactCoords> dependencies,
                        final String label) {
        final Collection<J2clArtifactCoords> unknown = dependencies.stream()
                .filter(d -> false == J2clDependency.get(d).isPresent())
                .collect(Collectors.toList());
        if (false == unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown " + label + " dependencies: " + join(unknown));
        }
    }

    /**
     * Will fail if one or more unused excluded dependencies predicates remained.
     */
    private void verifyExcluded() {
        final Collection<Predicate<J2clArtifactCoords>> unused = this.unusedExcluded;
        if (false == unused.isEmpty()) {
            throw new IllegalArgumentException("Excluded dependencies filter matches no dependencies: " + join(unused));
        }
    }

    private static String join(final Collection<?> items) {
        return items.stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    /**
     * Traverses the dependency graph creating job for each, for dependencies that are included.
     */
    private void prepareJobs(final J2clDependency artifact) {
        if (artifact.isProcessingRequired() && false == this.jobs.containsKey(artifact)) {
            final Set<J2clDependency> dependencies = artifact.dependencies(); // dependencies()

            // keep transitive dependencies alphabetical sorted for better readability when trySubmitJob pretty prints queue processing.
            final Set<J2clDependency> required = Sets.sorted();
            this.jobs.put(artifact, required);

            dependencies.stream()
                    .filter(J2clDependency::isProcessingRequired)
                    .forEach(d -> {
                        required.add(d);
                        this.prepareJobs(d);
                    });
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
    final J2clRequest taskCompleted(final J2clDependency completed) {
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

    private final CompletionService<J2clDependency> completionService;

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

        final J2clLogger logger = this.logger();
        logger.warn("Killing all running tasks");

        // TODO might be able to give Callable#toString and hope that is used by Runnable returned.
        this.executor.shutdownNow()
                .forEach(task -> logger.warn("" + J2clLogger.INDENTATION + task));
    }

    private final ExecutorService executor;

    private final AtomicReference<Throwable> cause = new AtomicReference<>();

    // toString.........................................................................................................

    @Override
    public String toString() {
        return this.base + " " +
                this.classpathRequired + " " +
                this.excluded + " " +
                this.javascriptSourceRequired + " " +
                this.processingSkipped + " " +
                this.replaced + " " +
                this.scope + " " +
                this.defines + " " +
                this.entryPoints() + " " +
                this.externs + " " +
                this.formatting + " " +
                this.initialScriptFilename() + " " +
                this.languageOut + " " +
                this.level;
    }
}
