# Conan Artipie adapter

Conan is a C/C++ repository, this adapter is an SDK for working with Conan data and metadata and a HTTP endpoint for the Conan repository.


## How does Conan work

* Repository data with Conan recipes is the directory with fixed structure. *.yml is used for metadata, and *.py is used for packaging logic. Binary artifacts stored in separate repository. https://docs.conan.io/en/latest/creating_packages/getting_started.html
* Basic configuration options: OS (Windows/Linux/macOS), architecture, compiler, static/shared mode, debug/release mode. https://docs.conan.io/en/latest/creating_packages/define_abi_compatibility.html
* Conan uses Python scripts with some base classes provided by Conan project, which wrap steps like patch/configure/make/install, as well as dependencies configuration.
* Makefile/Automake and CMake are supported well by Conan python framework, custom builds could also be implemented manually. https://docs.conan.io/en/latest/reference/build_helpers.html
* When accepting repository changes it builds for multiple popular configurations before code review
* After reviewing and merging binary artifacts are stored for the most popular configurations. Other configurations built locally on client
* Every artifact recipe contains simple executable test project, which is also used for build & packaging checks
* Conan provides its own server on Python. SSL/https and authentication is supported but not required. https://docs.conan.io/en/latest/uploading_packages/running_your_server.html
* Conan supports multiple remotes for multiple sources, including company-internal hosting. https://docs.conan.io/en/latest/uploading_packages/remotes.html
* Binary artifacts repository for every artifact stores archived source code and multiple pre-built packages with its built configuration in specified text format (conaninfo.txt)
* Package hash is SHA-1 and computed based on full build configuration info. Package archive contains include files and library binaries https://docs.conan.io/en/latest/creating_packages/understand_packaging.html#package-creation-process


## How to use Artipie Conan SDK

TODO

## How to configure and start Artipie Conan endpoint

TODO

