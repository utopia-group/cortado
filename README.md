# cortado

Tool from our 2022 OOPSLA paper [Synthesizing Fine-Grained Synchronization Protocols for Implicit Monitors](todo)

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

TODO

## Benchmarks
Benchmarks used for our 2022 OOPSLA submission.

To compile them, please follow the following instructions.

### Compiling benchmarks

Build and install the package using the [Maven](https://maven.apache.org)
build system.
```bash
mvn clean install -f cortado-core
mvn clean isntall -f cortado-benchmarks
```

