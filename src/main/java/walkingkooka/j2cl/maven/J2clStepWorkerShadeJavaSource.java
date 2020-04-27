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

import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import walkingkooka.collect.list.Lists;
import walkingkooka.collect.map.Maps;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * If the dependency source has a shade file, create an output directory with selected shaded class files combined
 * with the other class files changed.
 */
final class J2clStepWorkerShadeJavaSource extends J2clStepWorkerShade {

    /**
     * Singleton
     */
    static J2clStepWorker instance() {
        return new J2clStepWorkerShadeJavaSource();
    }

    /**
     * Use singleton
     */
    private J2clStepWorkerShadeJavaSource() {
        super();
    }

    @Override
    J2clStep step() {
        return J2clStep.GWT_INCOMPATIBLE_STRIP;
    }

    @Override
    Predicate<Path> fileFilter() {
        return J2clPath.JAVA_FILES;
    }

    @Override
    byte[] shade(final byte[] content,
                 final Map<String, String> shadings) {
        final Charset charset = Charset.defaultCharset();
        return shade(new String(content, charset), shadings).getBytes(charset);
    }

    static String shade(final String content,
                        final Map<String, String> shadings) {
        ASTParser parser = ASTParser.newParser(AST.JLS3);
        parser.setSource(content.toCharArray());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);

        final CompilationUnit unit = (CompilationUnit) parser.createAST(null);
        final List<Name> qualifieds = collectNames(unit);
        return collectText(reverseSort(qualifieds), content, shadings);
    }

    private static List<Name> collectNames(final ASTNode root) {
        final List<Name> names = Lists.array();

        root.accept(new ASTVisitor() {

            @Override
            public boolean visit(final QualifiedName node) {
                names.add(node);
                return false;
            }

            @Override
            public boolean visit(final SimpleName node) {
                names.add(node);
                return false;
            }

            @Override
            public boolean visit(final SimpleType node) {
                final Name name = node.getName();
                if (name instanceof QualifiedName) {
                    names.add(name);
                }

                return false; // dont want to update simple type names.
            }
        });
        return names;
    }

    private static List<Name> reverseSort(final List<Name> nodes) {
        final Map<Integer, Name> offsetToQualified = Maps.sorted(Comparator.reverseOrder());

        nodes.forEach(q -> offsetToQualified.put(q.getStartPosition(), q));

        return offsetToQualified.values().stream().collect(Collectors.toList());
    }

    private static String collectText(final List<Name> names,
                                      final String file,
                                      final Map<String, String> shadings) {
        final StringBuilder text = new StringBuilder();
        text.append(file);

        for (final Name node : names) {
            final int start = node.getStartPosition();
            final int end = start + node.getLength();

            final String typeName = text.substring(start, end);

            for (final Entry<String, String> oldAndNew : shadings.entrySet()) {
                final String old = oldAndNew.getKey();
                final String neww = oldAndNew.getValue();

                if (typeName.equals(old) || typeName.startsWith(old)) {
                    text.delete(start, start + old.length());
                    text.insert(start, neww);
                    break;
                }
            }
        }

        return text.toString();
    }

    /**
     * This solves the problem of javascript files that ill be present in {@link J2clStep#COMPILE_GWT_INCOMPATIBLE_STRIPPED}
     * that are generated by annotation process such as during tests.
     */
    @Override
    void postCopyAndShade(final J2clDependency artifact,
                          final J2clPath output,
                          final J2clLinePrinter logger) throws Exception {
        copyJavascriptFiles(Lists.of(artifact.step(J2clStep.GWT_INCOMPATIBLE_STRIP).output(), artifact.step(J2clStep.UNPACK).output()),
                output,
                logger);
    }

    private static void copyJavascriptFiles(final List<J2clPath> sourceRoots,
                                            final J2clPath output,
                                            final J2clLinePrinter logger) throws IOException {
        logger.printLine("Copying *.js");
        logger.indent();
        {
            for (final J2clPath sourceRoot : sourceRoots) {
                logger.printLine(sourceRoot.toString());
                logger.indent();
                {
                    final Set<J2clPath> copy = sourceRoot.gatherFiles(J2clPath.JAVASCRIPT_FILES);
                    output.copyFiles(sourceRoot,
                            copy,
                            J2clPathTargetFile.REPLACE,
                            J2clPath.COPY_FILE_CONTENT_VERBATIM,
                            logger);
                }
                logger.outdent();
            }
        }
        logger.outdent();
    }
}
