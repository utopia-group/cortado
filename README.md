# cortado

Tool from our 2022 OOPSLA paper [Synthesizing Fine-Grained Synchronization Protocols for Implicit Monitors](https://kferles.github.io/assets/pdf/OOPSLA-22.pdf)

## Tool

### Building

#### Requirements

To build and use Cortado, you need to install the following software:

- [Z3 version 4.8.15 - 64 bit](https://github.com/Z3Prover/z3) (needs to be built with Java bindings)
- [Maven version 3 or higher](https://maven.apache.org/install.html)
- [Java 1.8](https://www.java.com/en/download/manual.jsp)

Please refer to the above links for OS-specific installation instructions.

#### Building Cortado

With Java 1.8 as your *default* Java version, simply run the following:

```bash
mvn initialize --file cortado-core
mvn install -DskipTests=true --file cortado-core
```

### Running Cortado

To run Cortado, Java must be able to dynamically load Z3's library (libz3 & libz3java). Depending your OS, you must 
do some configuration so that can be done successfully. We provide some sample configuration steps that are known to
work with commonly used distributions.

#### Ubuntu/Debian

Simply set enviroment variable `LD_LIBRARY_PATH` to Z3's installation directory:

```bash
export LD_LIBRARY_PATH=path/to/Z3/dir/bin
```

Make sure that this directory contains files `libz3.so` and `libz3java.so`.

#### MacOS

For MacOS, `libz3.dylib` must be properly linked inside of `libz3java.dylib` (read [this](https://github.com/Z3Prover/z3/issues/294#issuecomment-352472522) for more information). To do so, run the following command:

```bash
install_name_tool -change libz3.dylib  path/to/Z3/dir/bin/libz3.dylib path/to/Z3/dir/bin/libz3java.dylib
```

The command `install_name_tool` can be installed through your preferred package manager.

#### Command Line Interface

Since Cortado is based on [Soot](http://soot-oss.github.io/soot/), our tool expects the implicit monitor as a compiled class file. 
To see how you can generate such a class, consult [this sample implicit monitor](https://github.com/utopia-group/cortado/blob/main/cortado-benchmarks/cortado-benchmark-implementations/src/main/java/edu/utexas/cs/utopia/cortado/githubbenchmarks/org/springframework/util/ImplicitConcurrencyThrottleSupport.java).
To convert an implicit monitor to an explicit one, you can simply run the following command:

```bash
java -jar cortado-core/cortado/target/cortado-0.1.0.jar class-file.txt [tool options] -- [soot options]
```

*Important Note:* If you are running Cortado on MacOS, you need to launch the VM as follows.

```bash
java -Djava.library.path=path/to/Z3/dir/bin/ -jar cortado-core/cortado/target/cortado-0.1.0.jar class-file.txt [tool options] -- [soot options]
```

Here, `class-file.txt` is a text file containing the class names of all implicit monitors to be converted. For an example of such a file, please see [here](https://github.com/utopia-group/cortado/blob/main/cortado-benchmarks/cortado-benchmark-implementations/all-example-monitors.txt).
For a complete list of available options, you can simply run `java -jar cortado-core/cortado/target/cortado-0.1.0.jar --help`. 
All arguments have a default option, so the tool can be run without any additional arguments. 
Arguments following the `--` delimiter can be used to configure Soot, a complete list of Soot options can be found [here](https://soot-oss.github.io/soot/docs/4.3.0-SNAPSHOT/options/soot_options.html). A typical configuration of Cortado can be found [here](https://github.com/utopia-group/cortado/blob/main/cortado-benchmarks/cortado-benchmark-implementations/pom.xml#L386-L478).

## Benchmarks
Benchmarks used for our 2022 OOPSLA submission.

To compile them, please follow the following instructions.

### Compiling benchmarks and harnesses

After installing cortado, you can compile the benchmarks and run
the cortado algorithm on them using the following command:

```bash
mvn clean install -f cortado-benchmarks -Dsolver.exec=/path/to/z3/executable
```

To simply compile the benchmarks without running the cortado algorithm,
run:

```bash
mvn clean install -f cortado-benchmarks -Dexec.skip=true
```

Note that you still must install cortado first due to a dependency on
the [`@Atomic` annotations](https://github.com/utopia-group/cortado/blob/main/cortado-core/cortado-mockclasses/src/main/java/edu/utexas/cs/utopia/cortado/mockclasses/Atomic.java).

### Running benchmarks

To run all benhcmarks invoke script [run-all-benchmarks.sh](https://github.com/utopia-group/cortado/blob/main/cortado-benchmarks/cortado-benchmark-harnesses/run-all-benchmakrs.sh) from its enclosing directory.

### Running ablation studies

You can run the ablation sudies we performed in our paper by running the following commands within the `cortado-benchmarks/scripts` directory.

```bash
./build-all-ablations.sh
./run-all-ablations.sh
```

