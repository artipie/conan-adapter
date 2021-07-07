Below is information for adapter testing.
conanfile.txt - test Conanfile for checking `conan install` command.
conan_server-data.tar.gz - test data for server-side (artipie-conan or conan_server)

Uploading specific list of packages:

cat conanfile.txt|grep ".*/.*"|cut -d/ -f1|https_proxy= http_proxy= xargs -n1 conan upload -r conan-local --confirm --all
ls ~/.conan/data|http_proxy= https_proxy= xargs -n1 conan upload -r conan-local --confirm --all

At the moment, following commands were successfully tested with conan v1.37.2 and conan-artipie adapter (with empty cache).

conan get zlib/1.2.11 -r conan-local
CONAN_LOGGING_LEVEL=debug conan download -r conan-local zlib/1.2.11@

mkdir build && cd build
rm -rf ./* && rm -rf ~/.conan/data && conan install -r conan-local ..

