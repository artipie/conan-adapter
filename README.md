# Conan Artipie adapter

Conan is a C/C++ repository, this adapter is an SDK for working with Conan data and metadata and a HTTP endpoint for the Conan repository.


## How does Conan work

### Conan recipe example:

```
zmqpp
├── all
│   ├── CMakeLists.txt           # This recipe uses CMake as C++ build system
│   ├── conandata.yml            # Source codes (URLs), patches and versions mapping
│   ├── conanfile.py             # Main python script of Conan recipe
│   ├── patches
│   │   ├── 0001-fix_zmqpp_export_header.patch
│   │   └── 0002-fix_zmqpp_libsodium_deps.patch
│   └── test_package
│       ├── CMakeLists.txt
│       ├── conanfile.py
│       └── test_package.cpp
└── config.yml                   # Versions & directories mapping
```


### Conan recipe lifecycle:

```
Conan package sources (uses library sources) -> 
local package recipe check (conan create ...) -> 
push to Conan package recipes git -> 
remote recipe checks/CI (e.g. GitHub Actions) -> 
Recipe code review -> Merge recipe code -> 
build popular package configurations on CI VMs/containers -> 
Upload package recipe with package binaries to Conan repository (conan upload zmqpp/4.2.0 -r conan-local --all) -> 
Download Conan recipes from Conan repository (with dependencies) ->
Download compatible prebuilt package binaries ->
build missing package binaries from package recipe (i.e. from sources).
```


### Conan package uploaded, directory structure for Conan python server:

```
zmqpp/
└── 4.2.0
    ├── _
    │   └── _
    │       ├── 0
    │       │   ├── export
    │       │   │   ├── conan_export.tgz
    │       │   │   ├── conanfile.py
    │       │   │   ├── conanmanifest.txt
    │       │   │   └── conan_sources.tgz
    │       │   └── package
    │       │       ├── 05d8bc6b87d38fd66f059ec637f04f9ebcc3aa8d
    │       │       │   ├── 0
    │       │       │   │   ├── conaninfo.txt
    │       │       │   │   ├── conanmanifest.txt
    │       │       │   │   └── conan_package.tgz
    │       │       │   ├── revisions.txt
    │       │       │   └── revisions.txt.lock
    │       │       ├── ........................................
    │       ├── revisions.txt
    │       └── revisions.txt.lock
    └── demo
        └── testing
            ├── 0
            │   ├── export
            │   │   ├── conan_export.tgz
            │   │   ├── conanfile.py
            │   │   ├── conanmanifest.txt
            │   │   └── conan_sources.tgz
            │   └── package
            │       └── f1c4d98ac919d48870f68bc5674f6b1be6efa827
            │           ├── 0
            │           │   ├── conaninfo.txt
            │           │   ├── conanmanifest.txt
            │           │   └── conan_package.tgz
            │           ├── revisions.txt
            │           └── revisions.txt.lock
            ├── revisions.txt
            └── revisions.txt.lock
```


* Conan repository stores Conan packages, which contain conan package recipe + package binaries (>=0)
* Every Conan repository package (artifact) recipe is the directory with fixed structure. *.yml is used for metadata, and conanfile.py is used for build & packaging logic. Source code may be part of the package source, but often downloaded from original repository by package recipe. Package binaries stored for popular build configurations, which is stored in text format (conaninfo.txt). https://docs.conan.io/en/latest/creating_packages/getting_started.html
* Basic configuration options for every Conan recipe: OS (Windows/Linux/macOS), architecture, compiler, static/shared mode, debug/release mode. Configureation stored in text format (conaninfo.txt). Every conan package may have dependencies on other conan packages. https://docs.conan.io/en/latest/creating_packages/define_abi_compatibility.html
* Conan uses Python scripts with some base classes provided by Conan project, which wrap steps like patch/configure/make/install, as well as dependencies configuration.
* Makefile/Automake and CMake are supported well by Conan python framework, custom builds could also be implemented manually. https://docs.conan.io/en/latest/reference/build_helpers.html
* Every package recipe contains simple executable test project, which is used for build & packaging checks (successful run and exit without crash, dependency resolution, etc).
* Due to the complexity of C/C++ dependency configuration for multi-platform, CI/build checks is important step, so Conan recipes first committed in conan index git repository.
* After reviewing and merging Conan recipe and package binaries for popular configurations are pushed to Conan repository server. Other configurations built locally on client by downloading and running Conan recipe.
* Conan provides its own server on Python with all basic functionality implemented. SSL/https and authentication is supported but not required there. https://docs.conan.io/en/latest/uploading_packages/running_your_server.html
* Conan supports multiple remotes for multiple sources of the packagess, including company-internal hosting. https://docs.conan.io/en/latest/uploading_packages/remotes.html
* Package binary's hash is SHA-1 and computed based on full build configuration info. Package archive contains include files and library binaries. https://docs.conan.io/en/latest/creating_packages/understand_packaging.html#package-creation-process


## How to use Artipie Conan SDK

This is the dependency you need:

```xml
<dependency>
  <groupId>com.artipie</groupId>
  <artifactId>conan-adapter</artifactId>
  <version>[...]</version>
</dependency>
```

After that, you can use existing [asto](https://github.com/artipie/asto) `com.artipie.asto.Storage` implementations to create required storage object. 
There are filesystem storage, S3 storage, in-memory storage types. After that, you could use available API classes.

# RevisionsIndexApi

This class provides APIs for Conan revision index support for Conan V2 protocol.
It provides methods to add and remove revision information from revisions.txt files, for example:
```java
CompletionStage<Void> addRecipeRevision(final int revision);
CompletionStage<Boolean> removeRecipeRevision(final int revision);
CompletionStage<List<Integer>> getRecipeRevisions();
CompletionStage<List<Integer>> getBinaryRevisions(final int reciperev, final String hash);
```

It also provides methods for index files update after package contents changes:
```java
CompletionStage<List<Integer>> updateRecipeIndex();
CompletionStage<List<Integer>> updateBinaryIndex(final int reciperev, final String hash);
CompletionStage<Void> fullIndexUpdate()
```

Usage example:

```java
package com.artipie.conan;
final Storage storage = new InMemoryStorage();
final RevisionsIndexApi index = new RevisionsIndexApi(storage, new Key.From("zlib/1.2.11/_/_"));
index.fullIndexUpdate().toCompletableFuture().join();
```

See also Artipie Conan adapter [Javadoc](https://www.javadoc.io/doc/com.artipie/conan-adapter/latest/index.html) for more technical details.

## How to configure and start Artipie Conan endpoint

## How to switch Conan to V2 API

Conan client uses V1 API by default, as it's more stable. Anyway, both V1 and V2 APIs aren't public, so they could change. The conan_server supports both APIs by default.
Switching to V2 API on client:
```
nano ~/.conan/conan.conf

[general]
+revisions_enabled = True
```

