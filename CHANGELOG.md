# Version history

## 4.2
August 21, 2021

### New features ✨
- Added the ability to use [Internal App Sharing](https://support.google.com/googleplay/android-developer/answer/9844679) ([JENKINS-62405](https://issues.jenkins-ci.org/browse/JENKINS-62405), [#44](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/44))
  - Using `internal-app-sharing` as the release track name will upload the given app files to Internal App Sharing, and output the download URL to the build log
- Added the ability to retain certain app files from a previous release when creating a new release ([JENKINS-66099](https://issues.jenkins-ci.org/browse/JENKINS-66099), [#42](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/42); thanks to [Entreco](https://github.com/Entreco))

### Bug fixes 🐛
- Fixed crash when attempting to parse manifest information from certain AAB files ([JENKINS-65475](https://issues.jenkins-ci.org/browse/JENKINS-65475), [#41](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/41))
- Fixed handling of changes that "cannot be sent for review automatically" ([JENKINS-65815](https://issues.jenkins-ci.org/browse/JENKINS-65815), [#43](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/43))
  - The `changesNotSentForReview` flag is now automatically enabled if Google Play rejects an upload with this error

### Miscellaneous 🦆
- Increased the minimum Jenkins version to [2.222.4](https://www.jenkins.io/changelog-stable/#v2.222.4)

## 4.1
January 18, 2021

### New features ✨
- Added the ability to upload [native debug symbols files](https://developer.android.com/studio/build/shrink-code#native-crash-support) ([#38](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/38); thanks to [Brian Wang](https://github.com/wangpy))
- Added the ability to set the release name ([#39](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/39); thanks to [Niklas Therning](https://github.com/ntherning))

### Miscellaneous 🦆
- Updated the list of [possible languages](https://support.google.com/googleplay/android-developer/answer/9844778) for release notes, shown in the auto-complete list
- Made logging at build time a bit more consistent
- Updated inline documentation

## 4.0
September 27, 2020

### Breaking changes ⚠️
- Release track name is now mandatory in job configuration ([JENKINS-62398](https://issues.jenkins-ci.org/browse/JENKINS-62398), [#33](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/33))
  - For jobs configured without a track name, or without a `trackName` for Pipeline, you must now set its value to `production` if you wish to retain the previous behaviour
- Rollout percentage is now mandatory in job configuration ([JENKINS-62516](https://issues.jenkins-ci.org/browse/JENKINS-62516), [#36](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/36))
  - For jobs configured without a rollout percentage, or without a `rolloutPercentage` for Pipeline, you must now set its value to `100` if you wish to retain the previous behaviour

For a minimally configured Pipeline job, these changes would look something like this:
```diff
   // Upload and release immediately to 100% of users in Production
-  androidApkUpload googlePlayCredentialsId: 'gp'
+  androidApkUpload googlePlayCredentialsId: 'gp',
+                   trackName: 'production',
+                   rolloutPercentage: '100'
```

### Bug fixes 🐛
- Fixed inability to use Google Play credentials defined in a Folder ([JENKINS-56508](https://issues.jenkins-ci.org/browse/JENKINS-56508), [#27](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/27))
- Fixed variables in rollout percentage configuration being ignored at build time ([JENKINS-62320](https://issues.jenkins-ci.org/browse/JENKINS-62320), [#28](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/28))
- Fixed handling of certain AAB files to be more robust, e.g. those with a manifest obfuscated by tools like DexGuard ([JENKINS-63063](https://issues.jenkins-ci.org/browse/JENKINS-63063); thanks to [Silvan Fischer](https://github.com/silvanfischer))
- Fixed incorrect error message details shown when desired ProGuard mapping files could not be found

### New features ✨
- Added the ability to use custom release tracks ([JENKINS-62404](https://issues.jenkins-ci.org/browse/JENKINS-62404), [#34](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/34))
- Added the ability to prepare draft releases, by setting the rollout percentage to 0% ([JENKINS-40506](https://issues.jenkins-ci.org/browse/JENKINS-40506), [#30](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/30))
- Ensured that release notes are now retained when assigning files to a new release track ([JENKINS-54479](https://issues.jenkins-ci.org/browse/JENKINS-54479), [#32](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/32))
- Added the ability to optionally set the priority value for Google Play in-app updates ([#37](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/37); thanks to [Timothy](https://github.com/TimoPtr))
- Added automated acknowledgement of the "bundle installation warning" when uploading large AAB files ([JENKINS-62389](https://issues.jenkins-ci.org/browse/JENKINS-62389), [#31](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/31); thanks to [Victor Antonovich](https://github.com/3cky))

### Miscellaneous 🦆
- Increased the minimum Jenkins version to 2.164.3
- Added documentation for configuring Google Play credentials via [JCasC](https://plugins.jenkins.io/configuration-as-code/) ([JENKINS-61809](https://issues.jenkins-ci.org/browse/JENKINS-61809), [#29](https://github.com/jenkinsci/google-play-android-publisher-plugin/pull/29))

## 3.0
December 2, 2019

- Android App Bundles (AAB files) are now supported by the existing build steps (thanks to [andrewjapar](https://github.com/andrewjapar) and [Joe Hansche](https://github.com/jhansche))
- Expansion files uploaded during a build can be applied to other APKs uploaded in the same build (thanks to [Mikhail Korotetsky](https://github.com/mkorotetsky))
- Increased minimum Jenkins version to 2.138.4
- Migrated documentation and changelog to GitHub, as the Jenkins wiki is deprecated

## 2.0
July 17, 2019

- Upgraded to [v3](https://android-developers.googleblog.com/2019/03/changes-to-google-play-developer-api.html) of the Google Play Developer Publishing API (thanks to [Joe Hansche](https://github.com/jhansche))
- Fixed various potential NullPointerExceptions if no APKs had been uploaded already (thanks to [Kazuhide Takahashi](https://github.com/kazuhidet))
- Increased minimum Jenkins version to 2.60.3

## 1.8
June 3, 2018

- Enabled ability to upload to the "internal" track (thanks to [Serge Beauchamp](https://github.com/sergebeauchampGoogle))
- Allowed arbitrary percentage values to be used for staged rollouts
- Fixed potential NullPointerException if something went wrong ([JENKINS-49789](https://issues.jenkins-ci.org/browse/JENKINS-49789))

## 1.7
February 26, 2018

- [Fix security issue](https://jenkins.io/security/advisory/2018-02-26/)

## 1.6
January 18, 2018

- Added ability to upload ProGuard mapping files ([JENKINS-38731](https://issues.jenkins-ci.org/browse/JENKINS-38731))
- Made it possible to specify the Google Play credential dynamically ([JENKINS-38613](https://issues.jenkins-ci.org/browse/JENKINS-38613))
- Updated Google Play API dependency to hopefully make uploads a bit more stable
- Improved various error messages and configuration validation
- Increased minimum Jenkins version to 2.32
- Thanks to [Christopher Frieler](https://github.com/christopherfrieler) and [Dragan Marjanovic](https://github.com/dmarjanovic)

## 1.5
September 8, 2016

- Added support for the [Pipeline Plugin](https://wiki.jenkins.io/display/JENKINS/Pipeline+Plugin)

## 1.4.1
August 27, 2015

- Ensured that the required Token Macro Plugin is installed (see [JENKINS-29887](https://issues.jenkins-ci.org/browse/JENKINS-29887))

## 1.4
July 30, 2015

- Fixed the Google Play credential being forgotten when editing a job's configuration (see [JENKINS-26542](https://issues.jenkins-ci.org/browse/JENKINS-26542))
- Improved error messages when Google Play credentials are missing or misconfigured (see [JENKINS-25933](https://issues.jenkins-ci.org/browse/JENKINS-25933))
- Improved error messages shown when reading info from an APK fails
- Fixed or improved various aspects of job configuration validation
- Added additional logging about the credential ID and app ID being used
- Integrated the [Token Macro Plugin](https://wiki.jenkins.io/display/JENKINS/Token+Macro+Plugin) to give more options for specifying "Recent Changes" text (or any other field)
  - For example, with version 1.11 of token-macro, it will be possible to use something like `${FILE, path="changes-en.txt"}`, to read a changelog from the workspace and use its contents as release notes when uploading an APK to Google Play

## 1.3.1
April 1, 2015

- Improved the logging of which and how many APK files are to be uploaded/re-assigned

## 1.3
March 22, 2015

- Added new build step to enable moving existing APKs to a different release track

## 1.2
November 1, 2014

- Added ability to automatically recover from API failures when committing changes, if possible (see [JENKINS-25398](https://issues.jenkins-ci.org/browse/JENKINS-25398))

## 1.1
October 27, 2014

- Added ability to upload APK expansion files
- Upgraded APK parser library to fix some potential crashes

## 1.0
October 4, 2014

- Initial release
