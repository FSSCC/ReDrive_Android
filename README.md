# ReDrive SDK for Android

## Dependencies

Android version 5.0 or higher.

```java
compile "org.altbeacon:android-beacon-library:2.9.1"
compile "com.google.guava:guava:18.0"
```

## Installation

### Using AAR

1. Go to Android Studio | New Project | Minimum SDK

2. Select "API 21: Android 5.0" or higher and create your new project.

3. After you create a new project, go to File > New > New Module

4. Select "Import .JAR or >AAR Package"

5. Enter the path to .AAR file downloaded from this repo.

6. Under File > Project Structure, add pdk module as a dependency for your Project

7. Add the following as a dependency in build.gradle file

```java
compile "org.altbeacon:android-beacon-library:2.9.1"
compile "com.google.guava:guava:18.0"
```

### Using Source code

1. Go to Android Studio | New Project | Minimum SDK

2. Select "API 21: Android 5.0" or higher and create your new project.

3. After you create a new project, go to File > New > New Module

4. Select "Import Existing Project as Module"

5. Enter the path to source code downloaded from this repo.

6. If the pdk module is not added as a dependency automatically, add it manually under File > Project Structure.

## Sample project

Sample project available online at https://github.com/FSSCC/ReDrive_Android/tree/master/app