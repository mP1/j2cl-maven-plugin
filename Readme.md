J2CL Maven plugin
=================

This plugin is a rewrite of code present in

    https://github.com/Vertispan/j2clmavenplugin
    
For a full list of contributors, read source and to more click [here](https://github.com/Vertispan/j2clmavenplugin/commits/master)    

A major difference between the two is this plugin requires most parameters and dependencies to be declared as Maven
artifacts, nothing is assumed, everything must be declared in the pom in some form. As much as possible nothing is
defaulted and must be present in the POM.


# Goals

The plugin has two goals

1. `build`: executes a single compilation, typically to produce a JS application or library.

2. `clean`: cleans up all the cache directory.

3. `test`: TODO

4. 'watch': aka DEVMODE TODO

Some pieces of the vertispan/j2clmavenplugin are currently missing, because I dont have a large GWT project.

- Auto substitution of Google j2cl artifacts with vertispan forks which are j2cl/gwt compatible.
- j2cl tests.
- DevMode: Should not be too hard to watch the source directory and re-build.



# Dependencies declaration

The `vertispan/j2clmavenplugin` assumes some core dependencies are implied with defaults, meaning these dependencies are
not present in any form in the POM. This plugin requires that every single dependency be eventually declared directly
by the parent project or its dependencies eventually declare them in their respective POMs. This tries to keep faithful,
to how regular Maven java projects work and satisfy their dependencies.

A sample POM with the minimal dependencies and this plugin declaration is present as `sample.pom.xml`.

As a guide the following dependencies below may be considered a minimal requirement, and these were the implied or default
dependencies that were defaulted.

The fragment below was taken directly from the integration tests present in this project.

```xml
<dependencies>
    <dependency>
        <groupId>com.vertispan.j2cl</groupId>
        <artifactId>bootstrap</artifactId>
        <version>0.4-SNAPSHOT</version>
        <type>zip</type>
        <classifier>jszip</classifier>
    </dependency>

    <dependency>
        <groupId>com.vertispan.j2cl</groupId>
        <artifactId>javac-bootstrap-classpath</artifactId>
        <version>0.4-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.vertispan.j2cl</groupId>
        <artifactId>jre</artifactId>
        <version>0.4-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>com.vertispan.j2cl</groupId>
        <artifactId>jre</artifactId>
        <version>0.4-SNAPSHOT</version>
        <type>zip</type>
        <classifier>jszip</classifier>
    </dependency>

    <dependency>
        <groupId>com.vertispan.jsinterop</groupId>
        <artifactId>base</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>

    <dependency>
        <groupId>javax.annotation</groupId>
        <artifactId>jsr250-api</artifactId>
        <version>1.0</version>
    </dependency>
</dependencies>
```



# Maven plugin parameters

Many parameters are actually used to tweak and configure the Closure compiler. Refer to the Closure documentation for
more information.

## classpath-scope
The suggested value is typically `runtime`, for more info click [here](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html)

```xml
<classpath-scope>runtime</classpath-scope>
```



## compilation-level
A closure compiler parameter that controls how the compilation, for more info click [here](https://developers.google.com/closure/compiler/docs/compilation_levels#enable-app). 

    The Closure Compiler lets you choose from three levels of compilation, ranging from simple removal of whitespace and comments to aggressive code transformations.

```xml
<compilation-level>ADVANCED</compilation-level>
```



## defines
These key value pairs are arguments given only to the Closure compiler. The fragment below is the recommended.

```xml
<defines>
    <jre.checkedMode>DISABLED</jre.checkedMode>
    <jre.checks.checkLevel>MINIMAL</jre.checks.checkLevel>
    <jsinterop.checks>DISABLED</jsinterop.checks>
</defines>
```



## entry-points
A Closure compiler argument containing one or more entry point(s).

```xml
<entrypoint>helloworld.app</entrypoint>
```



## externs
Key value pairs that define externs for the Closure compiler. For more info click [here](https://developers.google.com/closure/compiler/docs/api-tutorial3#externs).



## initial-script-filename

The path to the initial script filename.



## javac-bootstrap, jre-jarfile

These two parameters are not a dependency declaration, but the maven coordinates that identify TWO special artifacts defined
as regular `<dependency>`. The values must for these parameters must match a dependency artifact otherwise the build will
complain and fail. Only the group-id and artifact-id is necessary separated by a colon ':', the version should not be included
here.

```xml
<javac-bootstrap>com.vertispan.j2cl:javac-bootstrap-classpath</javac-bootstrap>
<jre-jar-file>com.vertispan.j2cl:jre</jre-jar-file>
```



## output
This path is the final location of the final javascript.



## thread-pool-size
This parameter controls size of the thread pool used to execute parallel dependency processing. A value of 0, uses the
CPU core * 2, a value of 1 is useful to limit a single task at a time which makes for uninterrupted console messages at the
cost of longer build times.

```xml
<thread-pool-size>0</thread-pool-size>
```



# Ignore file(s)

A facility that is almost identical to `.gitignore` files is also supported and honoured when source files are processed.
If a `j2cl-maven-plugin-ignore.txt` is present in a source directory all patterns within it are honoured for the current
and sub directories. Comments, empty lines and no limit is placed on the number of lines with patterns in the file.

The actual patterns are PathMatcher [glob patterns](https://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob)
without the leading `glob:` prefix.

- Blank lines are ignored
- Lines beginning with HASH are considered to be comments and are ignored.
- All other lines are used to build a glob pattern, using the java snippet directly below
- No support is provided for escaping or any sort.



## Processing

Files are ignored before the Google preprocessor attempts to remove classes and class members are annotated with `@Gwt-Incompatible`.



## Sample j2cl-maven-plugin.txt

```text
# This is a comment and ignored. The two blank lines are also ignored.


# The two patterns will ignore files in this and the sub directory.
IgnoredFile1.*
sub2/IgnoredFile2.*
``` 


# Building steps or phases.

The build process involves transforming the parent project and dependencies including transitives from java into javascript,
in reverse order. Reverse order here means that if the project is the root of the dependency tree, then for all
operations to complete successfully dependencies that are leaves of this tree must be processed first. Once the leaves
are completed successfully dependencies or the project only requiring them can be attempted. Eventually the only
outstanding artifact or dependency is the project itself.

The plugin will create a separate directory for each artifact, using the maven coordinates and a HASH of all dependencies.
This means any time a dependency changes for any reason, any artifacts that reference it will also change and they will
be processed once more which is the desired so that changes are included in the final output. Processing a single dependency
potentially involves numerous steps, as each is performed a directory which includes a number prefix is created. These sub
directories will include a log file including all output for that step along with further files and directories. These logs
will be useful if anything goes wrong.

Every single step for every single artifact will have its own log file under its own step directory under the directory
for that artifact named according to the scheme mentioned above.

## Step 1 Hashing

The first step whenever a dependency processing begins is to compute the hash which is then combined with the maven
coordinates and used to create a directory if one did not previously exist.



## Step 2 Unpack

The source from the project or the sources jar file for an artifact will be extracted.



## Step 3 Javac Compile

The source extracted in step 2 will then be compiled by javac.



## Step 4 Gwt incompatible stripped source

The goal of this step is to remove classes and class members such as methods or fields that have been marked with the
`@Gwt-incompatible` annotation with the "output" directory of this step containing the final result AFTER these classes and
members have been removed. Most of this work is done by the Google `JavaPreprocessor`.

This step also removes entire classes that have been matched by the ignore files mentioned above.



## Step 5 Javac Compile Gwt incompatible stripped source

This step invokes javac on the output produced by step 4.



## Step 6 Transpile 

This step accepts the output from step 4 and transpile that java source into javascript.



## Step 7 Closure compile

This is the final step and only run for the project, it uses the Closure compiler to produce the final javascript file(s).



# Contributions

Suggestions via the issue tracker, and pull requests are most welcomed.



# Getting the source

You can either download the source using the "ZIP" button at the top
of the github page, or you can make a clone using git:

```
git clone git://github.com/mP1/j2cl-maven-plugin.git
```
