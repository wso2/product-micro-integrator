# WSO2 Micro Integrator

[![Build Status](https://wso2.org/jenkins/buildStatus/icon?job=products/micro-integrator)](https://wso2.org/jenkins/job/products/job/micro-integrator/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Stackoverflow](https://img.shields.io/badge/Ask%20for%20help%20on-Stackoverflow-orange)](https://stackoverflow.com/questions/tagged/wso2-micro-integrator)
[![Discord](https://img.shields.io/badge/Join%20us%20on-Discord-%23e01563.svg)](https://discord.gg/wso2)
[![Twitter](https://img.shields.io/twitter/follow/wso2.svg?style=social&label=Follow)](https://twitter.com/intent/follow?screen_name=wso2)

WSO2 Micro Integrator is the integration runtime of [WSO2 Enterprise Integrator
(EI)](https://wso2.com/enterprise-integrator/), which is an open-source, hybrid integration platform. Please refer to the [official documentation](https://ei.docs.wso2.com/en/latest/micro-integrator/overview/introduction/) for further information. 

The Micro Integrator runtime is lightweight, fast, scalable, and supports centralized ESB-style integration architectures as well as decentralized microservices architectures:

-   Centralized integration architecture
  ![esb integration architecture](doc/images/mi-esb-architecture.png)

-   Decentralized integration architecture
  ![microservices architecture](doc/images/mi-microservices-architecture.png)

Developers can create integrations graphically by using [WSO2 Integration Studio](https://wso2.com/integration/integration-studio/) and deploy them in the Micro Integrator runtime.

## Summary

- [**Why Micro Integrator?**](#why-micro-integrator?)
- [**Getting Started**](#getting-started)
- [**Contributing**](#contributing)
- [**Licence**](#licence)
- [**Copyright**](#copyright)

## Why Micro Integrator?

The Micro Integrator provides developers with a flawless experience in developing, testing, and deploying integration solutions. The Micro Integrator developer experience is especially tuned for;
-   Integration developers who prefer a config-driven approach to integration (centralized or decentralized).
-   Integration developers who are looking for a solution to integrate brownfield (i.e.  legacy systems) solutions with new microservices-based solutions.
-   Existing WSO2 EI and integration middleware users who want to move into cloud-native or microservices architectures.

The Micro Integrator offers an enhanced experience for container-based architectures by simplifying Docker/Kubernetes deployments. 

Refer the [developer guide](https://ei.docs.wso2.com/en/latest/micro-integrator/develop/intro-integration-development/) in the documentation for details.

## Getting Started

See the following topics to get started with the product:
- [Install and setup](https://ei.docs.wso2.com/en/latest/micro-integrator/setup/installation/install_prerequisites/) the Micro integrator
- [Quick start guide](https://ei.docs.wso2.com/en/latest/micro-integrator/overview/quick-start-guide/)
- [Developing your first integration](https://ei.docs.wso2.com/en/latest/micro-integrator/develop/integration-development-kickstart/)

See the [Tutorials](https://ei.docs.wso2.com/en/latest/micro-integrator/use-cases/learn-overview/#tutorials) and [Examples](https://ei.docs.wso2.com/en/latest/micro-integrator/use-cases/learn-overview/#examples) to try out the main integration use cases. You can also find more resources in the [WSO2 Library](https://wso2.com/library/integration/).

## Contributing

If you are planning on contributing to the development efforts of WSO2 Micro Integrator, you can do that by checking out
the latest development version. The `master` branch holds the latest unreleased source code.

### Building from the source

Please follow the steps below to build WSO2 Micro Integrator from the source code.

1. Clone or download the source code from this repository (https://github.com/wso2/micro-integrator).
2. Run the maven command `mvn clean install` from the root directory of the repository.
3. The generated Micro Integrator distribution can be found at `micro-integrator/distribution/target/wso2mi-<version>.zip`.

Please note that the product can be build using only JDK 11 but the integration tests can be run in either JDK 11, 17 or 21.

#### Building the Docker image

You can build the Docker image for the Micro Integrator by setting the `docker.skip` system property to `false` when running the
maven build. This builds and pushes the micro-integrator Docker image to the local Docker registry.

```bash
mvn clean install -Ddocker.skip=false
```

## License

WSO2 Micro Integrator is licensed under the [Apache License](http://www.apache.org/licenses/LICENSE-2.0).

## Survey On Open Source Community Communication

WSO2 wants to learn more about our open source software (OSS) community and your communication preferences to serve you better.

In addition, we may reach out to a small number of respondents to ask additional questions and offer a small gift.

Link to survey: https://forms.gle/h5q4M3K7vyXba3bK6

## Copyright

(c) 2020, [WSO2 Inc.](http://www.wso2.org) All Rights Reserved.
