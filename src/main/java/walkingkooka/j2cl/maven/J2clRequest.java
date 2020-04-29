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
import java.util.stream.Collectors;

/**
 * Context about a build/test request. It contains common properties shared by different phases of the build/test process,
 * including classpaths, closure compiler parameters and maven scoping which is used to select dependencies/artifacts.
 */
abstract class J2clRequest {

    J2clRequest(final J2clPath base,
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
                        final J2clMavenMiddleware middleware,
                        final ExecutorService executor,
                        final J2clLogger logger) {
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

    final boolean isClasspathRequired(final J2clArtifactCoords coords) {
        return this.classpathRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> classpathRequired;

    final boolean isIgnored(final J2clArtifactCoords coords) {
        return this.ignoredDependencies.contains(coords);
    }

    private final List<J2clArtifactCoords> ignoredDependencies;

    final boolean isJavascriptSourceRequired(final J2clArtifactCoords coords) {
        return this.javascriptSourceRequired.contains(coords);
    }

    private final List<J2clArtifactCoords> javascriptSourceRequired;

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

    private final Set<String> javaCompilerArguments;

    final Set<String> javaCompilerArguments() {
        return this.javaCompilerArguments;
    }

    final LanguageMode languageOut() {
        return this.languageOut;
    }

    private final LanguageMode languageOut;

    // browsers.........................................................................................................

    abstract List<J2clStepWorkerWebDriverUnitTestRunnerBrowser> browsers();

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
    abstract HashBuilder computeHash(final Set<String> hashItemsNames);

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
            project.print(false);

            throw new IllegalArgumentException("Unknown " + label + " dependencies: " + join(unknown));
        }
    }

    private static String join(final Collection<?> items) {
        return items.stream()
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
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

    private void prepareJobs(final J2clDependency artifact) {
        if (false == artifact.isIgnored() && false == this.jobs.containsKey(artifact)) {
            // keep transitive dependencies alphabetical sorted for better readability when trySubmitJob pretty prints queue processing.
            final Set<J2clDependency> required = Sets.sorted();

            if(false == artifact.isIgnored()) {
                this.jobs.put(artifact, required);
            }

            for (final J2clDependency dependency : artifact.dependencies()) {
                if (false == dependency.isIgnored()) {
                    required.add(dependency);
                    this.prepareJobs(dependency);
                }
            }
        }
    }

    /**
     * Loops over all {@link #jobs} submitting a job for each that has no required artifacts aka the value is an empty {@link Set}.
     */
    private int trySubmitJobs() {
        final J2clLogger j2clLogger = this.logger();
        final J2clLinePrinter logger = J2clLinePrinter.with(j2clLogger.printer(j2clLogger::info), null);

        final List<Callable<J2clDependency>> submit = Lists.array();

        this.executeWithLock(() -> {
            final String message;

            logger.printLine("Submitting jobs");
            logger.indent();
            {
                logger.printLine("Queue");
                logger.indent();

                //for readability sort jobs alphabetically as they will be printed and possibly submitted.....................
                final SortedMap<J2clDependency, Set<J2clDependency>> alphaSortedJobs = Maps.sorted();
                alphaSortedJobs.putAll(this.jobs);

                for (final Entry<J2clDependency, Set<J2clDependency>> artifactAndDependencies : alphaSortedJobs.entrySet()) {
                    final J2clDependency artifact = artifactAndDependencies.getKey();
                    final Set<J2clDependency> required = artifactAndDependencies.getValue();

                    logger.printLine(artifact.toString());
                    logger.indent();
                    {
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
                    }
                    logger.outdent();
                }

                final int submitCount = submit.size();
                final int waiting = alphaSortedJobs.size() - submit.size();
                final int running = this.running.get();

                if (0 == submitCount && waiting > 0 && 0 == running) {
                    throw new J2clException(submitCount + " jobs submitted with " + waiting + " several waiting and " + running + " running.");
                }

                message = submitCount + " job(s) submitted, " + running + " running " + waiting + " waiting.";
            }
            logger.outdent();

            logger.printLine(message);
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
                this.ignoredDependencies + " " +
                this.javascriptSourceRequired + " " +
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
