from conans import ConanFile, tools
import os


class NlohmannJsonConan(ConanFile):
    name = "nlohmann_json"
    homepage = "https://github.com/nlohmann/json"
    description = "JSON for Modern C++ parser and generator."
    topics = ("conan", "jsonformoderncpp",
              "nlohmann_json", "json", "header-only")
    url = "https://github.com/conan-io/conan-center-index"
    no_copy_source = True
    license = "MIT"

    @property
    def _source_subfolder(self):
        return "source_subfolder"

    def package_id(self):
        self.info.header_only()

    def source(self):
        tools.get(**self.conan_data["sources"][self.version])
        extracted_dir = "json-" + self.version
        os.rename(extracted_dir, self._source_subfolder)

    def package(self):
        self.copy(pattern="LICENSE*", dst="licenses", src=self._source_subfolder)
        self.copy("*", dst="include", src=os.path.join(self._source_subfolder, "include"))

    def package_info(self):
        self.cpp_info.names["cmake_find_package"] = "nlohmann_json"
        self.cpp_info.names["cmake_find_package_multi"] = "nlohmann_json"
        self.cpp_info.names["pkg_config"] = "nlohmann_json"
