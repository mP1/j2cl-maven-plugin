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

import org.junit.Test;
import walkingkooka.collect.map.Maps;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class J2clStepWorkerShadeJavaSourceTest {

    @Test
    public void testImport() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;");
    }

    @Test
    public void testImportShadedIgnored() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;",
                Maps.of("ignored", "package4.package5"));
    }

    @Test
    public void testImportShadedIgnored2() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;",
                Maps.of("package1.package2.type4", "NEVER"));
    }

    @Test
    public void testImportShadedIgnored3() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;",
                Maps.of("package2", "NEVER"));
    }

    @Test
    public void testImportShaded() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;",
                Maps.of("package1.package2", "package4.package5"),
                "import package4.package5.type3;");
    }

    @Test
    public void testImportShaded2() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;",
                Maps.of("package1", "package4"),
                "import package4.package2.type3;");
    }

    @Test
    public void testImportShaded3() throws Exception {
        this.shadeAndCheck("import package1.package2.type3;",
                Maps.of("package1", "package4", "package1", "NEVER"),
                "import package4.package2.type3;");
    }

    @Test
    public void testImportWildcard() throws Exception {
        this.shadeAndCheck("import package.package2.*;");
    }

    @Test
    public void testImportWildcardShadingIgnored() throws Exception {
        this.shadeAndCheck("import package.package2.*;", Maps.of("ignored", "NEVER"));
    }

    @Test
    public void testImportWildcardShaded() throws Exception {
        this.shadeAndCheck("import package1.package2.*;",
                Maps.of("package1.package2", "package4.package5"),
                "import package4.package5.*;");
    }

    @Test
    public void testImportWildcardShaded2() throws Exception {
        this.shadeAndCheck("import package1.package2.*;",
                Maps.of("package1", "package4"),
                "import package4.package2.*;");
    }

    @Test
    public void testPackageClassUnchangedEmptyShadings() throws Exception {
        this.shadeAndCheck("package package1; import package2.type3; class Type4{};");
    }

    @Test
    public void testPackageClassUnchangedEmptyShadings2() throws Exception {
        this.shadeAndCheck("package package1; import package2.*; class Type4{};");
    }

    @Test
    public void testPackageClassWithMethodUnchangedEmptyShadings() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};");
    }

    @Test
    public void testPackageClassPackageShaded() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};",
                Maps.of("package1", "package99"),
                "package package99;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};");
    }

    @Test
    public void testPackageClassPackageShaded2() throws Exception {
        this.shadeAndCheck("package package1.package2;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};",
                Maps.of("package1.package2", "package98.package99"),
                "package package98.package99;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};");
    }

    @Test
    public void testPackageClassPackageShaded3() throws Exception {
        this.shadeAndCheck("package package1.package2;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};",
                Maps.of("package1", "package99"),
                "package package99.package2;\nimport package2.type3;\nclass Type4{\npublic static void main(final String[] args){}\n};");
    }

    @Test
    public void testPackageClassPackageShaded4() throws Exception {
        this.shadeAndCheck("package package1.package2;\nimport package3.type2;\nclass Type4{\npublic static void main(final String[] args){}\n};",
                Maps.of("package1", "package99"),
                "package package99.package2;\nimport package3.type2;\nclass Type4{\npublic static void main(final String[] args){}\n};");
    }

    @Test
    public void testPackageClassIgnored() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static Type6 x(){return null}\n};",
                Maps.of("package5", "package99"),
                "package package1;\nimport package2.type3;\nclass Type4{\npublic static Type6 x(){return null}\n};");
    }

    @Test
    public void testPackageClassIgnored2() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static Type6 x(){return null}\n};",
                Maps.of("Type4", "Type99"),
                "package package1;\nimport package2.type3;\nclass Type99{\npublic static Type6 x(){return null}\n};");
    }

    @Test
    public void testPackageClassShaded() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static package5.Type6 x(){return null}\n};",
                Maps.of("package5", "package99"),
                "package package1;\nimport package2.type3;\nclass Type4{\npublic static package99.Type6 x(){return null}\n};");
    }

    @Test
    public void testPackageManyShaded() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static package5.Type6 x(){return null}\n};",
                Maps.of("package1", "package91", "package5", "package95"),
                "package package91;\nimport package2.type3;\nclass Type4{\npublic static package95.Type6 x(){return null}\n};");
    }

    @Test
    public void testPackageManyShaded2() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static package1.Type5 x(){return null}\n};",
                Maps.of("package1", "package99", "package5", "package95"),
                "package package99;\nimport package2.type3;\nclass Type4{\npublic static package99.Type5 x(){return null}\n};");
    }

    @Test
    public void testPackageManyShaded3() throws Exception {
        this.shadeAndCheck("package package1;\nimport package2.type3;\nclass Type4{\npublic static package1.Type5 x(){return null}\n};",
                Maps.of("package99", "NEVER", "package1", "package91", "package2", "package92"),
                "package package91;\nimport package92.type3;\nclass Type4{\npublic static package91.Type5 x(){return null}\n};");
    }

    private void shadeAndCheck(final String original) throws Exception {
        this.shadeAndCheck(original, Maps.empty());
    }

    private void shadeAndCheck(final String original,
                               final Map<String, String> shadings) throws Exception {
        shadeAndCheck(original, shadings, original);
    }

    private void shadeAndCheck(final String original,
                               final Map<String, String> shadings,
                               final String expected) throws Exception {
        assertEquals(expected, J2clStepWorkerShadeJavaSource.shade(original, shadings), () -> " shadings: " + shadings);
    }
}
