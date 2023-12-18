# Photinia

This document is to help users reproduce the results we reported in our submission. It contains the following descriptions

## 0. Artifact Expectation

The tools and experimental results mentioned in the paper are all stored in the repository of *JavaPhotonia* users. **We run all the experiments on a Docker environment which was deployed on a  machine  with 128GB RAM and  48 cores of CPU.** Both can be executed using Docker 20.10.8. We hope that users can reproduce our experiments using this version or a later version of Docker. For experiments with no specific performance requirements (such as those not based on the DOOP framework), results can also be obtained using a personal computer.

## 1. Environment Setup

Photinia provides two usage methods based on the Soot framework and the DOOP framework, respectively. The following will explain how to configure them.

### 1.1. Soot

* Pull Photinia from GitHub.

```
$ git clone https://github.com/JavaPhotinia/Photinia.git
```

* Open Photinia with IntelliJ IDEA.
* Modify the ```.../src/main/resources/config.properties``` so that Photinia can analyze the local project under test.
  * **benchmark_base_path:** change to the absolute path where the benchmark is located
  * **benchmark_name:** the name of the project that needs to be tested
  * **bean_xml_paths:**  the  absolute path of Spring bean configuration file in project under test
  * **shiro_xml_paths:** the  absolute path of Shiro configuration file in project under test
  * **web_xml_paths:** the  absolute path of web.xml configuration file in project under test
* Run ParserSpringMain.main().

### 1.2. DOOP
