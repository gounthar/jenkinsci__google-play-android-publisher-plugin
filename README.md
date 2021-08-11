# Google Play Android Publisher plugin for Jenkins

[![Jenkins plugin](https://img.shields.io/jenkins/plugin/v/google-play-android-publisher.svg)](https://plugins.jenkins.io/google-play-android-publisher)
[![Jenkins plugin installs](https://img.shields.io/jenkins/plugin/i/google-play-android-publisher?color=blue)](https://plugins.jenkins.io/google-play-android-publisher)
[![Build status](https://ci.jenkins.io/job/Plugins/job/google-play-android-publisher-plugin/job/master/badge/icon)](https://ci.jenkins.io/job/Plugins/job/google-play-android-publisher-plugin/job/master/)

Enables Jenkins to manage and upload Android app files (AAB or APK) to Google Play.

## Features
- Uploading Android App Bundle (AAB) or APK files to Google Play
  - This includes apps which use Multiple APK support
  - ProGuard `mapping.txt` and native debug symbols can also be associated with each app file, for deobfuscating crash dumps
  - The update priority can be set, if using [in-app updates][gp-docs-inappupdates]
  - Include bundles from previous releases
  - APK expansion (.obb) files can also be uploaded
    - With the option to re-use expansion files from existing APKs, e.g. for patch releases
- Assigning apps to internal, alpha, beta, production, or custom release tracks
  - This includes a build step for moving existing versions to a different track, or updating the rollout percentage   
    e.g. You can upload an alpha in one job, then later have another job promote it to beta
- Staged rollout of apps to any release track
- Uploading files without yet rolling out, creating a draft release
- Assigning release notes to uploaded files, for various languages
- Changing the Jenkins build result to failed if the configuration is bad, or uploading or moving app files fails for some reason
- Every configuration field supports variable and [token][plugin-token-macro] expansion, allowing release notes to be dynamically generated, for example
- Integration with the [Google OAuth Credentials Plugin][plugin-google-oauth], so that Google Play credentials can be entered once globally, stored securely, and shared between jobs
  - Multiple Google Play accounts are also supported via this mechanism

## Requirements
### Jenkins
Jenkins [version 2.164.3][lts-changelog] or newer is required.

### Google Play publisher account
For the initial setup only, you must have access to the Google account which owns the [Google Play publisher account][gp-docs-distribute].

This is required to enable API access from Jenkins to your Google Play account.

Note that having admin access to the Google Play Console is not enough; you need the account owner.  
You can see who the account owner is under [Settings → Developer account → Account details][gp-console-account-details] in the Google Play Console.

### Please note
- The app being uploaded must already exist in Google Play; you cannot use the API to upload brand new apps

#### Bundle size warnings
If you try to upload an AAB file to Google Play (including manually via the Google Play Console), and its size is perhaps 100MB+, it [may][gp-apidocs-upload-params] give you a warning:
> The installation of the app bundle may be too large and trigger user warning on some devices […] this needs to be explicitly acknowledged

Unfortunately, this "user warning" that may be shown, presumably when a user installs your app from Google Play, does not appear to be documented. Therefore this plugin automatically "acknowledges" that warning on Google Play on your behalf when uploading any AAB files, regardless of their size, so you should not see any errors.

If you _do_ see see any unexpected behaviour related to uploading bundles, or warnings appearing for end users, please [let us know](#feedback).

## Setup
### One-time: Set up Google Play credentials
Once you have this plugin installed, the following initial setup process is demonstrated in this video: [https://www.youtube.com/watch?v=txdPSJF94RM][demo-video-creds] (note that Google has changed the Google API Console (at least twice) since this video was recorded; steps 3–13 in the "Create Google service account" section below have the updated info).

#### Create Google service account
To enable automated access to your Google Play account, you must create a service account:

1.  Sign in to the [Google Play Console][gp-console] as the developer account owner
    - You can determine the account owner on the [Account details][gp-console-account-details] page
2.  Select [Settings → Developer account → API access][gp-console-api-access]
3.  Under Service Accounts, click "Create Service Account"
4.  Follow the link to the Google API Console
5.  Click the "Create service account" button
6.  Give the service account any name you like, e.g. "Jenkins"
7.  Choose Service Accounts > Service Account User for the "Role" field
8.  Enable "Furnish a new private key"
9.  Choose "JSON" as the key type (P12 works as well, but JSON is a little simpler)
10. Click the "Save" button
11. Note that a .json file is downloaded, named something like "api-xxxxxxxxx-xxxxx-xxxx.json"
12. Close the dialog that appears
13. Copy the email address of the new user (something like "jenkins@api-xxxxxxxxx-xxxxx-xxxx.iam.gserviceaccount.com")
14. You can now close the page

#### Assign permissions to the service account
1. Return to the Google Play Console page
2. Click "Done" on the dialog
3. Note that the service account has been associated with the Google Play publisher account  
   If it hasn't, follow these additional steps before continuing:
   1. Click "Users & permissions" in the menu
   2. Click "Invite new user"
   3. Paste in the email address you copied above
   4. Continue from step 5
4.  Click the "Grant access" button for the account (e.g. "jenkins@api-xxxxxxxxx-xxxxx-xxxx.iam.gserviceaccount.com")
5.  Ensure that at least the following permissions are enabled:
    - **View app information** — this is required for the plugin to function
    - **Manage production releases** — optional, if you want to upload APKs to production
    - **Manage testing track releases** — optional, if you want to upload APKs to alpha, beta, internal, or custom test tracks
6.  Click "Add user" (or "Send invitation", as appropriate)
7.  You can now log out of the Google Play publisher account

#### Add the service account credentials to Jenkins
##### Manually
1. Navigate to your Jenkins instance
2. Select "Credentials" from the Jenkins sidebar, at the top-level, or from within the folder where the credential should live
3. Choose a credentials domain and click "Add Credentials"
4. From the "Kind" drop-down, choose "Google Service Account from private key"
5. Enter a meaningful name for the credential, as you'll need to select it during build configuration, or enter it into your Pipeline configuration
6. Choose the "JSON key" type
7. Upload the .json file that was downloaded by the Google API Console
8. Click "OK" to create the credential

##### Using Configuration as Code
If you're using the [Configuration as Code plugin][plugin-jcasc] to set up your credentials automatically, you can do something like this:
```yaml
credentials:
  system:
    domainCredentials:
      - credentials:
          - googleRobotPrivateKey:
              projectId: 'Google Play'
              serviceAccountConfig:
                json:
                  # Optional
                  filename: 'my-gp-account.json'
                  # The contents of your .json file from Google Play, encoded as base 64, e.g.:
                  #   $ cat api-xxxxxxxxx-xxxxx-xxxx.json | base64 -
                  # You can also provide an environment variable with the same content, to avoid having it in this file
                  secretJsonKey: 'eyJjbGllbnRfZW1haWwiOiJqZW5raW5z […]'
```

Whether done manually or automatically, Jenkins now has the required credentials and permissions in order to publish to Google Play.

Once you've set up a job (see the next section) and confirmed that uploading works, either delete the downloaded JSON file or ensure that it's stored somewhere secure.

### Per-job configuration
#### Freestyle job configuration
##### Uploading app bundles or APKs
The following job setup process is demonstrated in this video:
[https://www.youtube.com/watch?v=iu-bLY9-jkc][demo-video-job]

1. Create a new free-style software project
2. Ensure, via whatever build steps you need, that the file(s) you want to upload will be available in the build's workspace
3. Add the "Upload Android AAB/APK to Google Play" post-build action
4. Select the credential name from the drop-down list
   - The credential must belong to the Google Play account which owns the app to be uploaded
5. Enter paths and/or patterns pointing to the AAB or APKs to be uploaded
   - This can be a glob pattern, e.g. `'build/**/*-release.apk'`, or a filename, both relative to the root of the workspace
   - Multiple patterns or filenames can be entered, if separated by commas
   - If nothing is entered, the default is `'**/build/outputs/**/*.aab, **/build/outputs/**/*.apk'`
6. Enter the release track to which the files should be assigned
   - This can be a built-in track like `'production'`, a testing track like `'beta'`, or a custom track name
   - Note that custom track names are case-sensitive (though the plugin will attempt to determine the correct track)
7. Specify a [rollout percentage][gp-docs-rollout] between 0 and 100%
   - If 100% is entered, the app will be immediately rolled out to all users on the chosen release track
   - If 0% is entered, the given file(s) will be uploaded as a draft release, leaving any existing rollout unaffected
8. Optionally specify an [in-app update priority][gp-docs-inappupdatepriority]
   - If nothing is entered, the default value (0, lowest priority) will be set by Google Play
9. Optionally choose "Add language" to associate release notes with the uploaded file(s)
   - You add entries for as many or as few of your supported language as you wish, but each language must already have been added to your app, under the "Store Listing" section in the Google Play Console.
10. Optionally specify [bundles to include][gp-docs-bundlestoinclude]

###### APK expansion files
You can optionally add up to two [expansion files][gp-docs-expansions] (main + patch) for each APK being uploaded.

A list of expansion files can be specified in the same way as APKs, though note that they must be named in the format `[main|patch].<apk-version>.<package-name>.obb`.

You can also enable the "Re-use expansion files from existing APKs where necessary" option, which will automatically the most-recent expansion files to newly uploaded APKs.

Similarly, if you want to apply the same expansion file(s) to multiple APKs being uploaded, you can do so.  Name the expansion file(s) according to the _lowest_ version code being uploaded: the expansion file will then be uploaded, and applied to the remaining APKs with higher version codes.

See the inline help for more details.

###### Bundles to Include
You can optionally add [bundles to include][gp-docs-bundlestoinclude] for each upload.

A comma seperated list of versionCodes needs to be specified to include/retain. After publishing, these retained bundles will stay published as well.

##### Moving existing app versions to another release track
If you have already uploaded an app to the alpha track (for example), you can later use Jenkins to re-assign that version to the beta or production release track.

Under the "Build" section of the job configuration, add the "Move Android apps to a different release track" build step and configure the new release track.
By setting the rollout percentage to 0%, you have the option of creating a draft release — i.e. the app files are assigned to a new release track, but not yet made available to users.

You can tell Jenkins **which** version codes should be moved by either entering the values directly, or by providing AAB or APK files, from which the plugin will read the application ID and version codes for you.

#### Pipeline job configuration
As of version 1.5, this plugin supports the [Pipeline Plugin][plugin-pipeline] syntax.

You can generate the required Pipeline syntax via the [Snippet Generator][snippets-blog], but some examples follow.

Note that you should avoid using multiple instances of these steps in a `parallel` block, as the Google Play API only allows one concurrent "edit session" to be open at a time.

##### Uploading app bundles or APKs
The `androidApkUpload` build step lets you upload Android App Bundle (AAB) or APK files.

| Parameter                          | Type    | Example                | Default                                                  | Description                                                                                                            |
|------------------------------------|---------|------------------------|----------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------|
| googlePlayCredentialsId            | string  | `'Google Play creds'`  | (none)                                                   | Name of the Google Service Account credential created in Jenkins                                                       |
| filesPattern                       | string  | `'release/my-app.aab'` | `'**/build/outputs/**/*.aab, **/build/outputs/**/*.apk'` | Comma-separated glob patterns or filenames pointing to the app files to upload, relative to the root of the workspace  |
| trackName                          | string  | `'internal'`           | (none)                                                   | Google Play track to which the app files should be published                                                           |
| releaseName                        | string  | `'1.2.3'`              | (none)                                                   | Name used to identify this release in the Google Play Console. If not set, Google Play will use the app version name   |
| rolloutPercentage                  | string  | `'1.5'`                | (none)                                                   | The rollout percentage to set on the track; use 0% to create a draft release                                           |
| ~rolloutPercent~<br>(deprecated)   | number  | `1.5`                  | (none)                                                   | (deprecated, but still supported; prefer `rolloutPercentage` instead — it takes priority if both are defined)          |
| deobfuscationFiles<br>Pattern      | string  | `'**/mapping.txt'`     | (none)                                                   | Comma-separated glob patterns or filenames pointing to ProGuard mapping files to associate with the uploaded app files |
| nativeDebugSymbolFiles<br>Pattern  | string  | `'**/symbols.zip'`     | (none)                                                   | Comma-separated glob patterns or filenames pointing to native debug symbol files to associate with the uploaded app files |
| expansionFilesPattern              | string  | `'**/*.obb'`           | (none)                                                   | Comma-separated glob patterns or filenames pointing to expansion files to associate with the uploaded APK files        |
| usePreviousExpansion<br>FilesIfMissing | boolean | `false`            | `true`                                                   | Whether to re-use the existing expansion files that have already been uploaded to Google Play for this app, if any expansion files are missing |
| recentChangeList                   | list    | (see below)            | (empty)                                                  | List of recent change texts to associate with the upload app files                                                     |
| inAppUpdatePriority                | string  | `'1'`                  | `'0'`                                                    | Priority of this release, used by the Google Play Core in-app update feature                                           |
| bundlesToInclude                   | string  | `'101, 102'`           | (none)                                                   | Version codes of existing app files which should be included in the new release being created                          |

The `googlePlayCredentialsId`, `trackName`, and `rolloutPercentage` parameters are mandatory, e.g. a minimal configuration would be:
```groovy
androidApkUpload googleCredentialsId: 'My Google Play account',
                 trackName: 'production',
                 rolloutPercentage: '100'
```

This will find any app files in the workspace matching the pattern `**/build/outputs/**/*.aab, **/build/outputs/**/*.apk`, upload them to the Production track, and make them available to 100% of users.

A more complete example:
```groovy
androidApkUpload googleCredentialsId: 'My Google Play account',
                 filesPattern: '**/build/outputs/**/*.aab',
                 trackName: 'dogfood',
                 rolloutPercentage: '25',
                 releaseName: 'Test build ({versionCode})',
                 deobfuscationFilesPattern: '**/build/outputs/**/mapping.txt',
                 nativeDebugSymbolFilesPattern: '**/build/outputs/**/native-debug-symbols.zip',
                 inAppUpdatePriority: '2',
                 recentChangeList: [
                   [language: 'en-GB', text: "Please test the changes from Jenkins build ${env.BUILD_NUMBER}."],
                   [language: 'de-DE', text: "Bitte die Änderungen vom Jenkins Build ${env.BUILD_NUMBER} testen."]
                 ]
```

To upload APKs and their expansion files, reusing those from the previous upload where possible:
```
androidApkUpload googleCredentialsId: 'My Google Play account',
                 filesPattern: '**/*.apk',
                 expansionFilesPattern: '**/patch.obb',
                 usePreviousExpansionFilesIfMissing: true
```

To include/retain bundles from a previous:
```
androidApkUpload googleCredentialsId: 'My Google Play account',
                 filesPattern: '**/*.aab',
                 bundlesToInclude: '1,2'
```

##### Updating release tracks with existing app versions
The `androidApkMove` build step lets you move existing Android app versions (whether AAB or APK) to another release track, and/or update the rollout percentage.

| Parameter               | Type    | Example                | Default                                                  | Description                                                                                                                     |
|-------------------------|---------|------------------------|----------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------|
| googlePlayCredentialsId | string  | `'Google Play creds'`  | (none)                                                   | Name of the Google Service Account credential created in Jenkins                                                                |
| trackName               | string  | `'internal'`           | (none)                                                   | Google Play release track to update with the given app versions                                                                 |
| releaseName             | string  | `'1.2.3'`              | (none)                                                   | Name used to identify this release in the Google Play Console. If not set, Google Play will use the app version name            |
| rolloutPercentage       | string  | `'1.5'`                | (none)                                                   | The rollout percentage to set on the given release track; use 0% to create a draft release                                      |
| ~rolloutPercent~<br>(deprecated) | number  | `1.5`         | (none)                                                   | (deprecated, but still supported; prefer `rolloutPercentage` instead — it takes priority if both are defined)                   |
| fromVersionCode         | boolean | `true`                 | `false`                                                  | If true, the `applicationId` and `versionCodes` parameters will be used. Otherwise the `filesPattern` parameter will be used    |
| applicationId           | string  | `'com.example.app'`    | (none)                                                   | The application ID of the app to update                                                                                         |
| versionCodes            | string  | `'1281, 1282, 1283'`   | (none)                                                   | Comma-separated list of version codes to set on the given release track                                                         |
| filesPattern            | string  | `'release/my-app.aab'` | `'**/build/outputs/**/*.aab, **/build/outputs/**/*.apk'` | Comma-separated glob patterns or filenames pointing to the files from which the application ID and version codes should be read |
| inAppUpdatePriority     | string  | `'1'`                  | `'0'`                                                    | Priority of this release, used by the Google Play Core in-app update feature                                                    |

The `googlePlayCredentialsId`, `trackName`, and `rolloutPercentage` parameters are mandatory, plus either an application ID and version code(s), or AAB or APK file(s) to read this information from.

For example, this would move the given versions to the production track, and make them available to 100% of users:
```groovy
androidApkMove googleCredentialsId: 'My Google Play account',
               trackName: 'production',
               rolloutPercentage: '100',
               applicationId: 'com.example.app',
               versionCodes: '1281, 1282, 1283'
```

Or moving versions from alpha (for example), to 50% of beta users, figuring out which application ID and version codes to use, based on the APK files in the workspace:
```groovy
androidApkMove googleCredentialsId: 'My Google Play account',
               trackName: 'beta',
               rolloutPercentage: '50',
               fromVersionCode: false,
               filesPattern: '**/*.apk'
```

Or, say the current production release is rolled out to 10% of users, and we want to expand the rollout to 25% of users:
```groovy
androidApkMove googleCredentialsId: 'My Google Play account',
               trackName: 'production',
               rolloutPercentage: '25',
               applicationId: 'com.example.app',
               versionCodes: '1281, 1282, 1283'
```

#### Setting the release name
You can optionally set the release name, used to identify a particular release in the Google Play Console. This isn't visible to end users.

If this option is not used, Google Play will set the release name to the versionName of the app file being uploaded, for
example: "1.2.34".

When uploading app files and setting a release name value, any instances of `{versionCode}` or `{versionName}` in the value
will be replaced at build time by the respective value from the app file being uploaded. If multiple app files are being
uploaded, the values from the file with lowest versionCode will be used.

For example, entering `releaseName: "Release v{versionName}_${env.GIT_COMMIT}"` in a Pipeline could yield a release name
on Google Play something like "Release v1.2.34_b2c3d3e4".

#### Backwards-compatibility
##### Version 3.0
Version 3.0 of the plugin deprecated some parameters used by the build steps, but they will remain supported for the foreseeable future:
- For Pipeline, `apkFilesPattern` is deprecated — `filesPattern` should be used instead

In addition, version 3.0 introduced the default values shown in the tables above, so those parameters can optionally now be omitted.

##### Version 4.0
**NOTE: This version makes it mandatory to configure a release track name, and the rollout percentage.**

In order to avoid unintentionally publishing to Production — if you forget to provide a track name, or use a string parameter for the track name but accidentally leave it empty, for example — we made the release track name a mandatory field.

If you have jobs configured without a track name, or without a `trackName` for Pipeline, you now need to set the track name to `'production'` to restore the previous behaviour.

For similar reasons, the rollout percentage, or `rolloutPercentage` for Pipeline, must be explicitly specified — it no longer defaults to 100%.

Sorry for any inconvenience caused by this breaking change.

## Troubleshooting
Error messages from the plugin (many of which come directly from the Google Play API) should generally be self-explanatory.  
If you're consistently having trouble getting a certain config to work, try uploading the same files manually to Google Play. There you'll likely see the reason for failure, e.g. a version code conflict or similar.

Otherwise, please check the [existing bug reports][issues-existing], and [file a new bug report][issues-report] with details, including the build console log output, if necessary.

Some known error messages and their solutions are shown below:

### GoogleJsonResponseException: 401 Unauthorized
This means that the Google service account does not have permission to make the changes that you requested.

Make sure that you followed the setup instructions above, and confirm that the service account you are using in this Jenkins job has the appropriate permissions in the Google Play Console for the app that you are trying to update.

### GoogleJsonResponseException: 500 Internal Server Error
Unfortunately, the Google Play API sometimes is not particularly reliable, and will throw generic server errors for no apparent reason.

In these cases you can try running your build again, or wait a few hours before retrying, if the problem persists.

Please also consider [contacting Google Play Developer Support][gp-support-form] to help make them aware that people use the Google Play API, and that it should preferably work in a reliable manner.

This plugin already recognises some temporary Google Play API server problems and works around them; more workarounds may be added in future, e.g. automatically retrying when a generic server error is encountered.

### Unable to retrieve an access token with the provided credentials
If you see this error message, look further down the error log to see what is causing it. Below are a couple of common causes:

#### Invalid JWT: Token must be a short-lived token and in a reasonable timeframe
Ensure that the system time is accurate on both the Jenkins controller and build agent, and then try again.

#### java.net.SocketTimeoutException: connect timed out
This likely means your build machine is behind an HTTP proxy.

In this case, you should set up Jenkins as documented on the [JenkinsBehindProxy][jenkins-behind-proxy] page.

This plugin only makes secure (HTTPS) requests, so you need to make sure that the `-Dhttps.proxyHost=<hostname>` and `-Dhttps.proxyPort=<port>` Java properties are set when starting Jenkins. Add the appropriate http versions of those flags too, if unsecured HTTP requests also need to go through the proxy.

### Internal error encountered
In some cases, the Google Play API can respond with a very generic error message.

If you see this, double-check all of your app-related settings on Google Play before trying again. For example, make sure you have no outstanding issues in your Google Play [inbox][gp-console-inbox], Policy Status, App Content, pricing and distribution settings, etc..

Please also [contact Google Play Developer Support][gp-support-form] to inform them that the Google Play Developers API is returning an unhelpful error message, and let them know what the cause was, if you manage to resolve the error by updating your Google Play account.

### Release track was not specified; this is now a mandatory parameter
Version 4.0 of the plugin made it [mandatory](#version-40) to specify the desired release track name.

If you're seeing this error, it means you were relying on the previous behaviour where not specifying a release track name would default to releasing to the Production track.

To fix this, update any job configurations to explicitly set the track name to `'production'`.

### Rollout percentage was not specified; this is now a mandatory parameter
Version 4.0 of the plugin made it [mandatory](#version-40) to specify the desired rollout percentage.

If you're seeing this error, it means you were relying on the previous behaviour where not specifying a rollout percentage would default to releasing to 100% of users in the given release track.

To fix this, update any job configurations to explicitly set the rollout percentage to `'100'`.

## Frequently asked questions
### What if I want to upload APKs with multiple, different application IDs (i.e. build flavours)?
Using the build flavours feature of the Android Gradle build system, it's possible to have a single Android build which produces multiple APKs, each with a different application ID. e.g. You could have application IDs "com.example.app" and "com.example.app.pro" for free and paid versions.

As these may be built in a single Jenkins job, people have wondered why this plugin will refuse to upload APKs with differing application IDs in a single freestyle job.

However, as far as Google Play is concerned, these are completely separate apps. This is correct and, as such, they should be uploaded in separate Jenkins builds: one per application ID.

If the plugin did allow this and you were to attempt to upload, say three, completely different APKs in one Jenkins build, this would require opening and committing three separate "edit sessions" with the Google Play API. If any one of these were to fail — maybe because of an invalid APK, versionCode conflict, or due to an API failure (which, unfortunately, is not uncommon with the Google Play API) — you would end up with your Google Play account in an inconsistent state. Your Jenkins build would be marked as failed, but one or more applications will have actually been uploaded and published to Google Play, and you would have to fix the situation manually. Also, you would not be able to simply re-run the build, as it would fail due to already-existing APKs.

The best practice in this case would be to have one job that builds the different flavours (i.e. the APKs with different application IDs) and then, if the build is successful, it would archive the APKs and start multiple "downstream" Jenkins builds which individually publish each of the applications.  
This can be achieved, for example, with the Parameterized Trigger Plugin and the Copy Artifacts Plugin, i.e. the "upload" job could be generic, and would receive the APK information via parameter.

Alternatively, if you have version 1.5 of this plugin, and use the [Pipeline Plugin][plugin-pipeline], you should be able to use the `androidApkUpload` step multiple times within a single build.

## Android apps using this plugin
There are several thousand people and companies using this plugin to upload their apps to Google Play, and it's always great to hear from people who are using the plugin.

Feel free to let us know via the feedback section below, or open a Pull Request and add yourself and your apps here! :)

## Contributing
You can potentially get a sense of what's being worked on via the [tickets on the Jenkins Jira][issues-existing].

Please contact us (see below) before working on new features, as we may be working on something already, or at least be able to give advice or pointers.

## Feedback
If you have issues with the plugin that aren't solved via the Troubleshooting section, you can [file a bug report][issues-report] with details, including the build console log output.

You can also send us an email with your comments, suggestions, or feedback:
- [@orrc](https://github.com/orrc)
- [@jhansche](https://github.com/jhansche)

## Changelog
See [CHANGELOG.md][changelog].

[changelog]:https://github.com/jenkinsci/google-play-android-publisher-plugin/blob/master/CHANGELOG.md
[demo-video-creds]:https://www.youtube.com/watch?v=txdPSJF94RM&list=PLhF0STyfNdUk1R3taEmgFR30yzp41yuRK&index=1
[demo-video-job]:https://www.youtube.com/watch?v=iu-bLY9-jkc&list=PLhF0STyfNdUk1R3taEmgFR30yzp41yuRK&index=2
[gp-apidocs-upload-params]:https://developers.google.com/android-publisher/api-ref/rest/v3/edits.bundles/upload#query-parameters
[gp-console]:https://play.google.com/console
[gp-console-account-details]:https://play.google.com/console/developers/contact-details
[gp-console-api-access]:https://play.google.com/console/developers/api-access
[gp-console-inbox]:https://play.google.com/console/developers/inbox
[gp-docs-distribute]:https://developer.android.com/distribute/best-practices/launch
[gp-docs-expansions]:https://developer.android.com/google/play/expansion-files.html
[gp-docs-inappupdatepriority]:https://developer.android.com/guide/playcore/in-app-updates#check-priority
[gp-docs-inappupdates]:https://developer.android.com/guide/playcore/in-app-updates
[gp-docs-rollout]:https://support.google.com/googleplay/android-developer/answer/6346149
[gp-docs-bundlestoinclude]:https://support.google.com/googleplay/android-developer/answer/9859348?hl=en#zippy=%2Cincluded-app-bundles-and-apks
[gp-support-form]:https://support.google.com/googleplay/android-developer/contact/publishing?extra.IssueType=submitting&hl=en&ec=publish&cfsi=publish_cf&cfnti=escalationflow.email&cft=3&rd=1
[issues-existing]:https://issues.jenkins-ci.org/issues/?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20google-play-android-publisher-plugin%20AND%20status%20NOT%20IN(Closed%2C%20Resolved)%20ORDER%20BY%20updated%20DESC
[issues-report]:https://jenkins.io/redirect/report-an-issue
[jenkins-behind-proxy]:https://wiki.jenkins.io/display/JENKINS/JenkinsBehindProxy#JenkinsBehindProxy-HowJenkinshandlesProxyServers
[lts-changelog]:https://jenkins.io/changelog-stable#v2.164.3
[plugin-google-oauth]:https://plugins.jenkins.io/google-oauth-plugin
[plugin-jcasc]:https://plugins.jenkins.io/configuration-as-code
[plugin-token-macro]:https://plugins.jenkins.io/token-macro
[plugin-pipeline]:https://plugins.jenkins.io/workflow-aggregator
[snippets-blog]:https://jenkins.io/blog/2016/05/31/pipeline-snippetizer/
