# lecousin.net - Java system framework

The system library allows few operating system specific operations, such as detecting
physical drives.

The system-api library defines the interface to be implemented for each operating system.

The system.unix and system.windows contain implementations for Unix/Mac and Windows OS.

The system-impl contains a POM with profiles to automatically add a dependency to either
system.unix or system.windows depending on your operating system.

## Build status

[![Maven Central](https://img.shields.io/maven-central/v/net.lecousin.framework.system/system-api.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22net.lecousin.framework.system%22)

![build status](https://travis-ci.org/lecousin/java-framework-system.svg?branch=master "Build Status")
![build status](https://ci.appveyor.com/api/projects/status/github/lecousin/java-framework-system?branch=master&svg=true "Build Status")

system-api [![Javadoc](https://img.shields.io/badge/javadoc-0.2.4-brightgreen.svg)](https://www.javadoc.io/doc/net.lecousin.framework.system/system-api/0.2.4)

### Development branch

![build status](https://travis-ci.org/lecousin/java-framework-system.svg?branch=dev "Build Status")
![build status](https://ci.appveyor.com/api/projects/status/github/lecousin/java-framework-system?branch=dev&svg=true "Build Status")
