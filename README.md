<img src="https://wso2.cachefly.net/wso2/sites/all/image_resources/wso2-branding-logos/wso2-logo-orange.png" alt="WSO2 logo" width=30% height=30% />

# WSO2 Micro Integrator

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![stackoverflow](https://img.shields.io/badge/Get%20Support%20on%20Stack%20Overflow-Micro%20Integrator-orange)](https://stackoverflow.com/questions/tagged/wso2-micro-integrator)
[![Join the community on Discord](https://img.shields.io/badge/Join%20us%20on%20Discord-wso2-orange)](https://discord.com/invite/wso2)
[![X](https://img.shields.io/twitter/follow/wso2.svg?style=social&label=Follow%20Us)](https://twitter.com/intent/follow?screen_name=wso2)

[WSO2 Micro Integrator](https://wso2.com/micro-integrator/) is a comprehensive integration solution that simplifies your digital transformation journey. The Micro Integrator streamlines connectivity among applications, services, data, and the cloud using a user-friendly, low-code graphical design experience.

To learn more about WSO2 Micro Integrator please visit [WSO2 Micro Integrator](https://wso2.com/integrator/ballerina-integrator/).

## Why Micro Integrator?

The Micro Integrator provides developers with a flawless experience in developing, testing, and deploying integration solutions. The Micro Integrator developer experience is specially tuned for;
-   Integration developers who prefer a config-driven approach to integration (centralized or decentralized).
-   Integration developers who are looking for a solution to integrate brownfield (i.e. legacy systems) solutions with new microservices-based solutions.
-   Existing WSO2 EI and integration middleware users who want to move into cloud-native or microservices architectures.

The Micro Integrator offers an enhanced experience for container-based architectures by simplifying Docker/Kubernetes deployments. 

## Getting Started

See the following topics to get started with the product:
- [System prerequisites](https://mi.docs.wso2.com/en/latest/install-and-setup/install/installation-prerequisites/)
- [Quick start guide](https://mi.docs.wso2.com/en/latest/get-started/quick-start-guide/)

See the [Tutorials and Examples](https://mi.docs.wso2.com/en/latest/learn/learn-overview/) to try out the main integration use cases.

## Contribute to Micro Integrator

If you are planning to contribute to the development efforts of WSO2 Micro Integrator, you can do so by checking out
the latest development version. The `master` branch holds the latest unreleased source code.

### Build from the source

Please follow the steps below to build WSO2 Micro Integrator from the source code.

1. Clone or download the source code from this repository (https://github.com/wso2/micro-integrator).
2. Run the maven command `mvn clean install` from the root directory of the repository.
3. The generated Micro Integrator distribution can be found at `micro-integrator/distribution/target/wso2mi-<version>.zip`.

Please note that the product can be build using only JDK 11 but the integration tests can be run in either JDK 11, 17 or 21.

### Build the Docker image

You can build the Docker image for the Micro Integrator by setting the `docker.skip` system property to `false` when running the
Maven build. This builds and pushes the micro-integrator Docker image to the local Docker registry.

```bash
mvn clean install -Ddocker.skip=false
```

## Reporting Product Issues

### Opening an issue

Help us make our software better! Submit any bug reports or feature requests through [WSO2 Micro Integrator GitHub](https://github.com/wso2/product-micro-integrator) repo.

### Reporting Security Issues

Please **do not** report security issues via GitHub issues. Instead, follow the [WSO2 Security Vulnerability Reporting Guidelines](https://security.docs.wso2.com/en/latest/security-reporting/vulnerability-reporting-guidelines/).

## Join the community!

- Read our [documentation](https://mi.docs.wso2.com/en/latest/).
- Get help on [Stack Overflow](https://stackoverflow.com/questions/tagged/wso2-micro-integrator).
- Join the conversation on [Discord](https://discord.gg/wso2).
- Learn more by reading articles from our [library](https://wso2.com/library/?area=integration).

## Commercial Support

You can take advantage of a WSO2 on-prem product subscription for the full range of software product benefits needed in your enterprise, like expert support, continuous product updates, vulnerability monitoring, and access to the licensed distribution for commercial use.

To learn more, check [WSO2 Subscription](https://wso2.com/subscription/).

## Can you fill out this survey?

WSO2 wants to learn more about our open source software (OSS) community and your communication preferences to serve you better.
In addition, we may reach out to a small number of respondents to ask additional questions and offer a small gift.

The survey is available at: [WSO2 Open Source Software Communication Survey
](https://forms.gle/h5q4M3K7vyXba3bK6)

--------------------------------------------------------------------------------
(c) Copyright 2012 - 2025 WSO2 Inc.
