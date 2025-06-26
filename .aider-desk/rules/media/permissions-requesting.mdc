Every Android app runs in a limited-access sandbox. If your app needs to use
resources or information outside of its own sandbox, you can declare a runtime\\
permission and set up a permission request
that provides this access. These steps are part of the workflow for using\\
permissions.

If you declare any dangerous\\
permissions, and if
your app is installed on a device that runs Android 6.0 (API level 23) or
higher, you must request the dangerous permissions at runtime by following the
steps in this guide.

If you don't declare any dangerous permissions, or if your app is installed on a
device that runs Android 5.1 (API level 22) or lower, the permissions are
automatically granted, and you don't need to complete any of the remaining steps
on this page.

## Basic principles

The basic principles for requesting permissions at runtime are as follows:

- Ask for a permission in context, when the user starts to interact with the
feature that requires it.
- Don't block the user. Always provide the option to cancel an educational UI
flow, such as a flow that explains the rationale for requesting permissions.
- If the user denies or revokes a permission that a feature needs, gracefully
degrade your app so that the user can continue using your app, possibly by
disabling the feature that requires the permission.
- Don't assume any system behavior. For example, don't assume that permissions
appear in the same _permission group_. A permission group merely helps the
system minimize the number of system dialogs that are presented to the user when
an app requests closely related permissions.

## Workflow for requesting permissions

Before you declare and request runtime permissions in your app, evaluate\\
whether your app needs to do so. You can
fulfill many use cases in your app, such as taking photos, pausing media
playback, and displaying relevant ads, without needing to declare any
permissions.

If you conclude that your app needs to declare and request runtime permissions,
complete these steps:

1. In your app's manifest file, declare the\\
permissions that your app might need to
request.
2. Design your app's UX so that specific actions in your app are associated with
specific runtime permissions. Let users know which actions might require them
to grant permission for your app to access private user data.
3. Wait for the user to invoke the task or action in your app
that requires access to specific private user data. At that time, your app can
request the runtime permission that's required for accessing that data.
4. Check whether the user has already granted the runtime\\
permission that your app requires. If so, your app can access
the private user data. If not, continue to the next step.

You must check whether you have a permission every time you perform an
operation that requires that permission.

5. Check whether your app should show a rationale to the user,
explaining why your app needs the user to grant a particular runtime permission.
If the system determines that your app shouldn't show a rationale, continue to
the next step directly, without showing a UI element.

If the system determines that your app should show a rationale, however,
present the rationale to the user in a UI element. In this rationale,
clearly explain what data your app is trying to access and what benefits
the app can provide to the user if they grant the runtime permission. After
the user acknowledges the rationale, continue to the next step.

6. Request the runtime permission that your app requires
to access the private user data. The system displays a runtime
permission prompt, such as the one shown on the permissions overview\\
page.

7. Check the user's response—whether they chose to grant or deny the
runtime permission.

8. If the user granted the permission to your app, you can access the private
user data. If the user denied the permission instead, gracefully degrade your\\
app experience so that it provides functionality to the user
without the information that's protected by that permission.

Figure 1 illustrates the workflow and set of decisions associated with this
process:

requesting runtime permissions on Android.

## Determine whether your app was already granted the permission

To check whether the user already granted your app a particular permission, pass
that permission into the
`ContextCompat.checkSelfPermission()`
method. This method returns either
`PERMISSION_GRANTED`
or `PERMISSION_DENIED`, depending on whether your app has the permission.

## Explain why your app needs the permission

The permissions dialog shown by the system when you call
`requestPermissions()` says what permission your app wants, but doesn't say
why. In some cases, the user might find that puzzling. It's a good idea to
explain to the user why your app wants the permissions before you call
`requestPermissions()`.

Research shows that users are much more comfortable with permissions requests if
they know why the app needs them, such as whether the permission is needed to
support a core feature of the app or for advertising. As a result, if you're
only using a fraction of the API calls that fall under a permission group, it
helps to explicitly list which of those permissions you're using and why. For
example, if you're only using coarse location, let the user know this in your
app description or in help articles about your app.

Under certain conditions, it's also helpful to let users know about
sensitive data access in real time. For example, if you’re accessing the
camera or microphone, it’s a good idea to let the user know by using a
notification icon somewhere in your app, or in the notification tray (if the
application is running in the background), so it doesn't seem like you're
collecting data surreptitiously.

Ultimately, if you need to request a permission to make something in your app
work, but the reason isn't clear to the user, find a way to let the user
know why you need the most sensitive permissions.

If the `ContextCompat.checkSelfPermission()` method returns `PERMISSION_DENIED`,
call `shouldShowRequestPermissionRationale()`.
If this method returns `true`, show an educational UI to the user. In this UI,
describe why the feature that the user wants to enable needs a particular
permission.

Additionally, if your app requests a permission related to location, microphone,
or camera, consider explaining why your app needs\\
access to this information.

## Request permissions

After the user views an educational UI, or the return value of
`shouldShowRequestPermissionRationale()` indicates that you don't need to show
an educational UI, request the permission. Users see a system
permission dialog, where they can choose whether to grant a particular
permission to your app.

To do this, use the `RequestPermission`
contract, included in an AndroidX library, where you allow the system to manage\\
the permission request code for you. Because
using the `RequestPermission` contract simplifies your logic, it is the recommended
solution when possible. However, if needed you can also manage a request code\\
yourself as part of the permission request and
include this request code in your permission callback logic.

### Allow the system to manage the permission request code

To allow the system to manage the request code that's associated with a
permissions request, add dependencies on the following libraries in your
module's `build.gradle` file:

- `androidx.activity`,
version 1.2.0 or later
- `androidx.fragment`,
version 1.3.0 or later

You can then use one of the following classes:

- To request a single permission, use
`RequestPermission`.
- To request multiple permissions at the same time, use
`RequestMultiplePermissions`.

The following steps show how to use the `RequestPermission` contract. The
process is nearly the same for the `RequestMultiplePermissions` contract.

1. In your activity or fragment's initialization logic, pass in an implementation
of `ActivityResultCallback`
into a call to
`registerForActivityResult()`.
The `ActivityResultCallback` defines how your app handles the user's response to
the permission request.

Keep a reference to the return value of `registerForActivityResult()`, which
is of type
`ActivityResultLauncher`.

2. To display the system permissions dialog when necessary, call the
`launch()`
method on the instance of `ActivityResultLauncher` that you saved in the
previous step.

After `launch()` is called, the system permissions dialog appears. When the
user makes a choice, the system asynchronously invokes your implementation
of `ActivityResultCallback`, which you defined in the previous step.

**Note:** Your app _cannot_ customize the dialog that appears
when you call `launch()`. To provide more information or
context to the user, change your app's UI so that it's easier for users to
understand why a feature in your app needs a particular permission. For
example, you might change the text in the button that enables the
feature.

Also, the text in the system permission dialog references the
permission\\
group associated with the permission that you requested. This
permission grouping is designed for system ease-of-use, and your app
shouldn't rely on permissions being within or outside of a specific
permission group.

The following code snippet shows how to handle the permissions response:

KotlinJavaMore

// Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher. You can use either a val, as shown in this snippet,
// or a lateinit var in your onAttach() or onCreate() method.
val requestPermissionLauncher =
registerForActivityResult(RequestPermission()

// Permission is granted. Continue the action or workflow in your
// app.
} else {
// Explain to the user that the feature is unavailable because the
// feature requires a permission that the user has denied. At the
// same time, respect the user's decision. Don't link to system
// settings in an effort to convince the user to change their
// decision.
}
}
// Register the permissions callback, which handles the user's response to the
// system permissions dialog. Save the return value, an instance of
// ActivityResultLauncher, as an instance variable.

if (isGranted) {
// Permission is granted. Continue the action or workflow in your
// app.
} else {
// Explain to the user that the feature is unavailable because the
// feature requires a permission that the user has denied. At the
// same time, respect the user's decision. Don't link to system
// settings in an effort to convince the user to change their
// decision.
}
});

And this code snippet demonstrates the recommended process to check for
a permission and to request a permission from the user when necessary:

when {
ContextCompat.checkSelfPermission(
CONTEXT,
Manifest.permission.REQUESTED_PERMISSION

// You can use the API that requires the permission.
}
ActivityCompat.shouldShowRequestPermissionRationale(

// In an educational UI, explain to the user why your app requires this
// permission for a specific feature to behave as expected, and what
// features are disabled if it's declined. In this UI, include a
// "cancel" or "no thanks" button that lets the user continue
// using your app without granting the permission.
showInContextUI(...)
}

// You can directly ask for the permission.
// The registered ActivityResultCallback gets the result of this request.
requestPermissionLauncher.launch(
Manifest.permission.REQUESTED_PERMISSION)
}
}
if (ContextCompat.checkSelfPermission(
CONTEXT, Manifest.permission.REQUESTED_PERMISSION) ==
PackageManager.PERMISSION_GRANTED) {
// You can use the API that requires the permission.
performAction(...);
} else if (ActivityCompat.shouldShowRequestPermissionRationale(
this, Manifest.permission.REQUESTED_PERMISSION)) {
// In an educational UI, explain to the user why your app requires this
// permission for a specific feature to behave as expected, and what
// features are disabled if it's declined. In this UI, include a
// "cancel" or "no thanks" button that lets the user continue
// using your app without granting the permission.
showInContextUI(...);
} else {
// You can directly ask for the permission.
// The registered ActivityResultCallback gets the result of this request.
requestPermissionLauncher.launch(
Manifest.permission.REQUESTED_PERMISSION);
}

### Manage the permission request code yourself

As an alternative to allowing the system to manage the permission request\\
code, you can manage the permission request
code yourself. To do so, include the request code in a call to
`requestPermissions()`.

The following code snippet demonstrates how to request a permission using a
request code:

// You can use the API that requires the permission.
performAction(...)
}
ActivityCompat.shouldShowRequestPermissionRationale(

// You can directly ask for the permission.
requestPermissions(CONTEXT,
arrayOf(Manifest.permission.REQUESTED_PERMISSION),
REQUEST_CODE)
}
}
if (ContextCompat.checkSelfPermission(
CONTEXT, Manifest.permission.REQUESTED_PERMISSION) ==
PackageManager.PERMISSION_GRANTED) {
// You can use the API that requires the permission.
performAction(...);
} else if (ActivityCompat.shouldShowRequestPermissionRationale(
this, Manifest.permission.REQUESTED_PERMISSION)) {
// In an educational UI, explain to the user why your app requires this
// permission for a specific feature to behave as expected, and what
// features are disabled if it's declined. In this UI, include a
// "cancel" or "no thanks" button that lets the user continue
// using your app without granting the permission.
showInContextUI(...);
} else {
// You can directly ask for the permission.
requestPermissions(CONTEXT,
new String[] { Manifest.permission.REQUESTED_PERMISSION },
REQUEST_CODE);
}

After the user responds to the system permissions dialog, the system then
invokes your app's implementation of `onRequestPermissionsResult()`. The system passes in the user
response to the permission dialog, as well as the request code that you defined,
as shown in the following code snippet:

override fun onRequestPermissionsResult(requestCode: Int,

when (requestCode) {

// If request is cancelled, the result arrays are empty.
if ((grantResults.isNotEmpty() &&
grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
// Permission is granted. Continue the action or workflow
// in your app.
} else {
// Explain to the user that the feature is unavailable because
// the feature requires a permission that the user has denied.
// At the same time, respect the user's decision. Don't link to
// system settings in an effort to convince the user to change
// their decision.
}
return
}

// Add other 'when' lines to check for other
// permissions this app might request.

// Ignore all other requests.
}
}
}
@Override
public void onRequestPermissionsResult(int requestCode, String[] permissions,
int[] grantResults) {
switch (requestCode) {
case PERMISSION_REQUEST_CODE:
// If request is cancelled, the result arrays are empty.

grantResults[0] == PackageManager.PERMISSION_GRANTED) {
// Permission is granted. Continue the action or workflow
// in your app.
} else {
// Explain to the user that the feature is unavailable because
// the feature requires a permission that the user has denied.
// At the same time, respect the user's decision. Don't link to
// system settings in an effort to convince the user to change
// their decision.
}
return;
}
// Other 'case' lines to check for other
// permissions this app might request.
}
}

### Request location permissions

When you request location permissions, follow the same best practices as
for any other runtime permission.
One important difference when it comes to location permissions is that the
system includes multiple permissions related to location. Which permissions you
request, and how you request them, depend on the location requirements for your
app's use case.

#### Foreground location

If your app contains a feature that shares or receives location information only
once, or for a defined amount of time, then that feature requires foreground
location access. Some examples include the following:

- Within a navigation app, a feature lets users get turn-by-turn
directions.
- Within a messaging app, a feature lets users share their current location
with another user.

The system considers your app to be using foreground location if a feature of
your app accesses the device's current location in one of the following
situations:

- An activity that belongs to your app is visible.
- Your app is running a foreground service. When a foreground service is
running, the system raises user awareness by showing a persistent notification.
Your app retains access when it's placed in the background, such as when the
user presses the Home button on their device or turns their device's display
off.

On Android 10 (API level 29) and higher, you must declare a foreground\\
service type
of `location`, as shown in the following code snippet. On earlier versions
of Android, it's recommended that you declare this foreground service type.

android:name="MyNavigationService"

You declare a need for foreground location when your app requests either the
`ACCESS_COARSE_LOCATION`
permission or the
`ACCESS_FINE_LOCATION`
permission, as shown in the following snippet:

#### Background location

An app requires background location access if a feature within the app
constantly shares location with other users or uses the Geofencing\\
API. Several examples include the following:

- Within a family location sharing app, a feature lets users continuously
share location with family members.
- Within an IoT app, a feature lets users configure their home devices such
that they turn off when the user leaves their home and turn back on when the
user returns home.

The system considers your app to be using background location if it accesses the
device's current location in any situation other than the ones described in the
foreground location section. The background location accuracy is
the same as the foreground location accuracy, which depends on
the location permissions that your app declares.

On Android 10 (API level 29) and higher, you must declare the
`ACCESS_BACKGROUND_LOCATION`
permission in your app's manifest to request background location
access at runtime. On earlier versions of
Android, when your app receives foreground location access, it automatically
receives background location access as well.

## Handle permission denial

If the user denies a permission request, your app should help users understand
the implications of denying the permission. In particular, your app should make
users aware of the features that don't work because of the missing permission.
When you do so, keep the following best practices in mind:

- **Guide the user's attention.** Highlight a specific part of your app's UI
where there's limited functionality because your app doesn't have the necessary
permission. Examples of what you could do include the following:

- Show a message where the feature's results or data would have appeared.
- Display a different button that contains an error icon and color.
- **Be specific.** Don't display a generic message. Instead, make clear which
features are unavailable because your app doesn't have the necessary permission.

- **Don't block the user interface.** In other words, don't display a
full-screen warning message that prevents users from continuing to use your app
at all.

At the same time, your app should respect the user's decision to deny a
permission. Starting in Android 11 (API level 30), if the user taps **Deny** for
a specific permission more than once during your app's lifetime of installation
on a device, the user doesn't see the system permissions dialog if your app
requests that permission again. The user's action implies "don't ask again." On
previous versions, users saw the system permissions dialog each time your
app requested a permission, unless they had previously selected a "don't ask
again" checkbox or option.

If a user denies a permission request more than once, this is considered a permanant
denial. It's very important to only prompt users for permissions when they need
access to a specific feature, otherwise you might inadvertently lose the ability
to re-request permissions.

In certain situations, the permission might be denied automatically, without the
user taking any action. (A permission might be _granted_
automatically as well.) It's important to not assume anything about automatic
behavior. Each time your app needs to access functionality that requires a
permission, check that your app is still granted that permission.

To provide the best user experience when asking for app
permissions, also see App permissions best practices.

### Inspect denial status when testing and debugging

To identify whether an app has been permanently denied permissions (for debugging
and testing purposes), use the following command:

adb shell dumpsys package PACKAGE_NAME

Where PACKAGE\_NAME is the name of the package to inspect.

The output of the command contains sections that look like this:

...
runtime permissions:
android.permission.POST_NOTIFICATIONS: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
android.permission.ACCESS_FINE_LOCATION: granted=false, flags=[ USER_SET|USER_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
android.permission.BLUETOOTH_CONNECT: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED]
...

Permissions that have been denied once by the user are flagged by `USER_SET`.
Permissions that have been denied permanently by selecting **Deny** twice are
flagged by `USER_FIXED`.

To ensure that testers see the request dialog during testing, reset these flags
when you're done debugging your app. To do this, use the command:

adb shell pm clear-permission-flags PACKAGE_NAME PERMISSION_NAME user-set user-fixed

PERMISSION\_NAME is the name of the permission you want to
reset.

To view a complete list of Android app permissions, visit the permissions API\\
reference page.

## One-time permissions

a one-time permission.

Starting in Android 11 (API level 30), whenever your app requests a permission
related to location, microphone, or camera, the user-facing permissions dialog
contains an option called **Only this time**, as shown in figure 2. If the user selects this option in
the dialog, your app is granted a temporary _one-time permission_.

Your app can then access the related data for a period of time that depends on
your app's behavior and the user's actions:

- While your app's activity is visible, your app can access the data.
- If the user sends your app to the background, your app can continue to access
the data for a short period of time.
- If you launch a foreground service while the activity is visible, and the user
then moves your app to the background, your app can continue to access the data
until the foreground service stops.

### App process terminates when permission revoked

If the user revokes the one-time permission, such as in system settings, your
app can't access the data, regardless of whether you launched a foreground
service. As with any permission, if the user revokes your app's one-time
permission, your app's process terminates.

When the user next opens your app and a feature in your app requests access to
location, microphone, or camera, the user is prompted for the permission again.

## Reset unused permissions

Android provides several ways to reset unused runtime permissions to their
default, denied state:

- An API where you can proactively remove your app's access
to an unused runtime permission.
- A system mechanism that automatically
resets the permissions of unused apps.

### Remove app access

On Android 13 (API level 33) and higher, you can remove your app's access to
runtime permissions that your app no longer requires. When you update your app,
perform this step so that users are more likely to understand why your app
continues to request specific permissions. This knowledge helps build user trust
in your app.

To remove access to a runtime permission, pass the name of that permission
into
`revokeSelfPermissionOnKill()`.
To remove access to a group of runtime permissions at the same time, pass a
collection of permission names into
`revokeSelfPermissionsOnKill()`.
The permission removal process happens asynchronously and kills all processes
associated with your app's UID.

For the system to remove your app's access to the permissions, all
processes tied to your app must be killed. When you call the API, the system
determines when it's safe to kill these processes. Usually, the system waits
until your app spends an extended period of time running in the background
instead of the foreground.

To inform the user that your app no longer requires access to specific runtime
permissions, show a dialog the next time the user launches your app. This dialog
can include the list of permissions.

### Auto-reset permissions of unused apps

If your app targets Android 11 (API level 30) or higher and isn't used for a few
months, the system protects user data by automatically resetting the sensitive
runtime permissions that the user had granted your app. Learn more in the guide
about app hibernation.

## Request to become the default handler if necessary

Some apps depend on access to sensitive user information related to call logs
and SMS messages. If you want to request the permissions specific to call logs
and SMS messages and publish your app to the Play Store, you must prompt the
user to set your app as the _default handler_ for a core system function before
requesting these runtime permissions.

For more information on default handlers, including guidance on showing a
default handler prompt to users, see the guide about permissions used only in\\
default handlers.

## Grant all runtime permissions for testing purposes

To grant all runtime permissions automatically when you install an app on an
emulator or test device, use the `-g` option for the `adb shell install`
command, as demonstrated in the following code snippet:

adb shell install -g PATH_TO_APK_FILE

## Additional resources

For additional information about permissions, read these articles:

- Permissions overview
- App permissions best practices

To learn more about requesting permissions, review the
permissions samples

You can also complete this codelab that demonstrates privacy best\\
practices.

Previous\\
\\
arrow\_back\\
\\
Declare app permissions

Next\\
\\
Request special permissions\\
\\
arrow\_forward

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2025-06-24 UTC.

---

