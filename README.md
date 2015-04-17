ngsutilsj
=========

This is a set of companion programs for NGSUtils written in Java. Any programs that are here
have been either newly written or rewritten from the Python NGSUtils in order to be more efficient.
For the most part, these are currently tools that work with compressed FASTQ or BAM files, but other tools
will be ported over to Java versions as needed.

As an added convenience, this project can be used via an embedded JAR file. If you run "ant jar", you will find two new files in the dist/
folder: `dist/ngsutilsj.jar` and `dist/ngsutilsj`. As you'd expect, you can run the tools directly from the JAR:

    java -jar ngsutilsj.jar

or you can use the embedded JAR version, which is a small shell script shim in front of the JAR file:

    ./ngsutilsj

Using the embedded JAR version is easier for typing at the command-line, but uses the exact same JAR file. If you use the embedded JAR version, but need to set any extra command-line parameters for java, you can use the `JAVA_OPT` environmental variable, or set `JAVA_OPT` in `$HOME/.ngsutilsjrc`. If needed, you can also set `JAVA_HOME` on the command-line or in `$HOME/.ngsutilsjrc`.
