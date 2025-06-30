Android 14 introduces Selected Photos Access, which allows users to grant apps
access to specific images and videos in their library, rather than granting
access to all media of a given type.

This change is only enabled if your app targets Android 14 (API level 34) or
higher. If you don't use the photo picker yet, we recommend implementing it in\\
your app to provide a consistent experience for selecting images and videos
that also enhances user privacy without having to request any storage
permissions.

If you maintain your own gallery picker using storage permissions and need to
maintain full control over your implementation, adapt your implementation
to use the new `READ_MEDIA_VISUAL_USER_SELECTED` permission. If your app
doesn't use the new permission, the system runs your app in a compatibility\\
mode.

| Target SDK | `READ_MEDIA_VISUAL_USER_SELECTED` declared | Selected Photos Access enabled | UX Behavior |
| --- | --- | --- | --- |
| SDK 33 | No | No | N/A |
| Yes | Yes | Controlled by the app |
| SDK 34 | No | Yes | Controlled by the system (compat behaviour) |
| Yes | Yes | Controlled by the app |

## Create or adapt your own gallery picker

Creating your own gallery picker requires extensive development and maintenance,
and your app needs to request storage permissions to get explicit user consent.
Users can deny these requests or, if your app is running on a device with
Android 14 and your app targets Android 14 (API level 34) or higher, limit
access to selected media. The following image shows an example of requesting
permissions and selecting media using the new options.

photos and videos that they want to make available to your app, in addition
to the usual options to grant full access or deny all access.

This section demonstrates the recommended approach for creating your own gallery
picker using `MediaStore`. If you already maintain a gallery picker for your app
and need to maintain full control, you can use these examples to adapt your
implementation. If you don't update your implementation to handle Selected
Photos Access, the system runs your app in a compatibility mode.

### Request permissions

First, request the correct storage permissions in the Android manifest,
depending on the OS version:

Then, request the correct runtime permissions, also depending on the OS version:

// Register ActivityResult handler

// See the permission example in the Android platform samples:
}

// Permission request logic

requestPermissions.launch(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED))

requestPermissions.launch(arrayOf(READ_MEDIA_IMAGES, READ_MEDIA_VIDEO))
} else {
requestPermissions.launch(arrayOf(READ_EXTERNAL_STORAGE))
}

#### Some apps don't need permissions

As of Android 10 (API level 29), apps no longer need storage permissions to add
files to shared storage. This means that apps can add images to the gallery,
record videos and save them to shared storage, or download PDF invoices without
having to request storage permissions. If your app only adds files to shared
storage and doesn't query images or videos, you should stop requesting storage
permissions and set a `maxSdkVersion` of API 28 in your `AndroidManifest.xml`:

### Handle media reselection

With the Selected Photos Access feature in Android 14, your app should adopt the
new `READ_MEDIA_VISUAL_USER_SELECTED` permission to control media
re-selection, and update your app's interface to let users grant your app access
to a different set of images and videos. The following image shows an example of
requesting permissions and re-selecting media:

photos and videos that they want to make available to your app.

When opening the selection dialog, photos, videos, or both are shown depending
on the permissions requested. For example, if you're requesting the
`READ_MEDIA_VIDEO` permission without the `READ_MEDIA_IMAGES` permission, only
videos would appear in the UI for users to select files.

// Allow the user to select only videos
requestPermissions.launch(arrayOf(READ_MEDIA_VIDEO, READ_MEDIA_VISUAL_USER_SELECTED))

You can check if your app has full, partial, or denied access to the device's
photo library and update your interface accordingly. Request these permissions
when the app needs storage access, instead of at startup. Keep in mind that the
permission grant can be changed between the `onStart` and `onResume` app
lifecycle callbacks, as the user can change the access in the settings without
closing your app.

if (

(
ContextCompat.checkSelfPermission(context, READ_MEDIA_IMAGES) == PERMISSION_GRANTED ||
ContextCompat.checkSelfPermission(context, READ_MEDIA_VIDEO) == PERMISSION_GRANTED
)
) {
// Full access on Android 13 (API level 33) or higher
} else if (

ContextCompat.checkSelfPermission(context, READ_MEDIA_VISUAL_USER_SELECTED) == PERMISSION_GRANTED
) {
// Partial access on Android 14 (API level 34) or higher
} else if (ContextCompat.checkSelfPermission(context, READ_EXTERNAL_STORAGE) == PERMISSION_GRANTED) {
// Full access up to Android 12 (API level 32)
} else {
// Access denied
}

### Query the device library

After you've verified you have access to the right storage permissions, you can
interact with `MediaStore` to query the device library (the same approach works
whether the granted access is partial or full):

data class Media(
val uri: Uri,
val name: String,
val size: Long,
val mimeType: String,
)

// Run the querying logic in a coroutine outside of the main thread to keep the app responsive.
// Keep in mind that this code snippet is querying only images of the shared storage.

val projection = arrayOf(
Images.Media._ID,
Images.Media.DISPLAY_NAME,
Images.Media.SIZE,
Images.Media.MIME_TYPE,
)

// Query all the device storage volumes instead of the primary only
Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
} else {
Images.Media.EXTERNAL_CONTENT_URI
}

contentResolver.query(
collectionUri,
projection,
null,
null,
"${Images.Media.DATE_ADDED} DESC"

val displayNameColumn = cursor.getColumnIndexOrThrow(Images.Media.DISPLAY_NAME)
val sizeColumn = cursor.getColumnIndexOrThrow(Images.Media.SIZE)
val mimeTypeColumn = cursor.getColumnIndexOrThrow(Images.Media.MIME_TYPE)

while (cursor.moveToNext()) {
val uri = ContentUris.withAppendedId(collectionUri, cursor.getLong(idColumn))
val name = cursor.getString(displayNameColumn)
val size = cursor.getLong(sizeColumn)
val mimeType = cursor.getString(mimeTypeColumn)

val image = Media(uri, name, size, mimeType)
images.add(image)
}
}

return@withContext images
}

This code snippet is simplified to illustrate how to interact with `MediaStore`.
In a production-ready app, use pagination with something like the Paging\\
library to help ensure good performance.

### Query the last selection

Apps on Android 15+ and on Android 14 with Google Play system updates support can
query the last selection of images and videos made by the user on partial access
by enabling the `QUERY_ARG_LATEST_SELECTION_ONLY`:

val queryArgs = bundleOf(
QUERY_ARG_SQL_SORT_ORDER to "${Images.Media.DATE_ADDED} DESC"
QUERY_ARG_LATEST_SELECTION_ONLY to true
)

contentResolver.query(collectionUri, projection, queryArgs, null)
}

### Photo and video access is preserved when the device upgrades

In cases where your app is on a device that upgrades from an earlier Android
version to Android 14, the system keeps full access to the user's photos and
videos, and it grants some permissions to your app automatically. The exact
behavior depends on the set of permissions that are granted to your app before
the device upgrades to Android 14.

#### Permissions from Android 13

Consider the following situation:

1. Your app is installed on a device that runs Android 13.
2. The user has granted the `READ_MEDIA_IMAGES` permission and the
`READ_MEDIA_VIDEO` permission to your app.
3. The device then upgrades to Android 14 while your app is still installed.
4. Your app starts targeting Android 14 (API level 34) or higher.

In this case, your app still has full access to the user's photos and videos.
The system also keeps the `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` permissions
granted to your app automatically.

#### Permissions from Android 12 and lower

1. Your app is installed on a device that runs Android 13.
2. The user has granted the `READ_EXTERNAL_STORAGE` permission or the
`WRITE_EXTERNAL_STORAGE` permission to your app.
3. The device then upgrades to Android 14 while your app is still installed.
4. Your app starts targeting Android 14 (API level 34) or higher.

In this case, your app still has full access to the user's photos and videos.
The system also grants the `READ_MEDIA_IMAGES` permission and the
`READ_MEDIA_VIDEO` permission to your app automatically.

### Best practices

This section contains several best practices for using the
`READ_MEDIA_VISUAL_USER_SELECTED` permission. For more information, check out
our permission best practices.

#### Don't store the permission state permanently

Don't store the permission state in a permanent way, including
`SharedPreferences` or `DataStore`. The stored state might not be in sync with
the actual state. The permission state can change after permission reset,
app hibernation, a user-initiated change in your app's settings, or when
your app goes into the background. Instead, check for storage permissions using
`ContextCompat.checkSelfPermission()`.

#### Don't assume full access to photos and videos

Based on the changes introduced in Android 14, your app might have only partial
access to the device's photo library. If the app is caching `MediaStore` data
when queried using `ContentResolver`, the cache might not be up to date.

- Always query `MediaStore` using `ContentResolver`, instead of relying on a
stored cache.
- Keep the results in memory while your app is in the foreground.
- Refresh the results when your app goes through the `onResume` app lifecycle
as the user might switch from full access to partial access through the
permission settings.

#### Treat URI access as temporary

If the user chooses **Select photos and videos** in the system permissions
dialog, your app's access to the selected photos and videos expires eventually.
Your app should always handle the case of not having access to any `Uri`, no
matter their authority.

#### Filter selectionable media type by permission

The selection dialog is sensitive to the requested permission type:

- Requesting only `READ_MEDIA_IMAGES` shows only images to be selectable.
- Requesting only `READ_MEDIA_VIDEO` shows only video to be selectable.
- Requesting both `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` shows the whole
photo library to be selectable.

Based on your app's use cases, you should make sure to request the right
permissions to avoid a poor user experience. If a feature is only expecting
videos to be selected, make sure to request only `READ_MEDIA_VIDEO`.

#### Request permissions in a single operation

To prevent users from seeing multiple system runtime dialog boxes, request the
`READ_MEDIA_VISUAL_USER_SELECTED`, `ACCESS_MEDIA_LOCATION` and the "read media"
permissions ( `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, or both) in a single
operation.

#### Allow users to manage their selection

When the user chooses the partial access mode, your app shouldn't assume that
the device's photo library is empty, and should allow the user to grant more
files.

The user might decide to switch from full access to partial access through the
permission settings without granting access to some visual media files.

## Compatibility mode

If you maintain your own gallery picker using storage permissions but haven't
adapted your app to use the new `READ_MEDIA_VISUAL_USER_SELECTED`
permission, the system runs your app in a compatibility mode whenever the user
needs to select or reselect media.

### Behavior during initial media selection

During initial selection, if a user chooses "Select photos and videos" (see
figure 1), the `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` permissions are
granted during the app session, providing a temporary permission grant and
temporary access to the user-selected photos and videos. When your app moves to
the background, or when the user actively kills your app, the system eventually
denies these permissions. This behavior is just like other one-time permissions.

### Behavior during media reselection

If your app needs access to additional photos and videos at a later time, you
must manually request the `READ_MEDIA_IMAGES` permission or the
`READ_MEDIA_VIDEO` permission again. The system follows the same flow as with
the initial permission request, prompting users to select photos and videos (see
figure 2).

If your app is following permissions best practices, this change shouldn't
break your app. This is especially true if your app doesn't assume that URI
access is retained, stores system permission state, or refreshes the set of
displayed images after the permission changes. However, this behavior might not
be ideal depending on your app's use case. To help provide the best experience
for your users, we recommend implementing photo picker or adapting your\\
app's gallery picker to handle this behavior directly using the
`READ_MEDIA_VISUAL_USER_SELECTED` permission.

Content and code samples on this page are subject to the licenses described in the Content License. Java and OpenJDK are trademarks or registered trademarks of Oracle and/or its affiliates.

Last updated 2024-06-20 UTC.

iframe

---

