# upload-client

**AWS Java Project – Upload Client Module** 

##  Description

`upload-client` is a Java module designed to provide upload functionality (e.g. to AWS storage) for your applications.  
It is structured as a Maven project and includes configuration for static code analysis and code-quality checks (pre-commit hooks, logging, checkstyle configuration, etc.).

## Contents

- `src/` – Main Java source code.  
- `pom.xml` – Maven build file, dependencies and project configuration.  
- `.gitignore` – Git ignore list.  
- `.pre-commit-config.yaml` – Pre-commit hooks configuration for automated static code analysis.  
- `config/checkstyle/` – Checkstyle configuration files to enforce code style/quality.  
- `LICENSE` – Project license (MIT).  

##  Features & Objectives

- Provides a structure for upload client functionality in Java.  
- Enforces coding style and quality via automated static analysis (Checkstyle, pre-commit hooks).  
- Logging support.  

## Prerequisites

- Java (compatible with project’s `pom.xml` settings).  
- Maven for building and managing dependencies.  
- (Optional but recommended) Pre-commit hooks for automated static code analysis.  

##  Installation & Build

```bash
git clone https://github.com/Hasna878/upload-client.git
cd upload-client
mvn clean install
