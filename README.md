# lecousin.net - Java system framework

The system library allows few operating system specific operations, such as detecting
physical drives.

The system-api library defines the interface to be implemented for each operating system.

The system.unix and system.windows contain implementations for Unix/Mac and Windows OS.

The system-impl contains a POM with profiles to automatically add a dependency to either
system.unix or system.windows depending on your operating system.

## Build status

Current version: 0.2.0

Master: ![build status](https://travis-ci.org/lecousin/java-framework-system.svg?branch=master "Build Status")

Branch 0.2: ![build status](https://travis-ci.org/lecousin/java-framework-system.svg?branch=0.2 "Build Status")

Branch 0.3: ![build status](https://travis-ci.org/lecousin/java-framework-system.svg?branch=0.3 "Build Status") 