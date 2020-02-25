# refactor_android_namespacedRClass
Script for refactoring android project for new gradle property: `android.namespacedRClass=true`
This script will refactor resource in .java and .kt files from look like:
```java
R.id.some_id
```
to fully qualified package name:
```java
my.package.in.project.module.R.id.some_id
```

# How to prepare:
1. change `projectDir` in script
2. adjust flags 
 a) `enableSimpleRefactoring` - refactor resources that exist in single variation
 b) `refactorAmbigious` - interactive mode for resources that have duplicate names in many modules
 c) `checkSuspiciousResources` - display warnings for suspicious resources that can't be refactored
 d) `warnAboutSkipped` - display warnings about skipped resources
 
 # How to run:
 In terminal run kts by this:
 ```
 kotlinc -script ./refactor_resources.kts 
```
