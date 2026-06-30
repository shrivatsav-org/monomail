export const meta = {
  name: 'remove-contact-photo',
  description: 'Remove all dead contact photo code, permissions, favicon fallback',
  phases: [{ title: 'Contact Photo', detail: 'Remove contact photo code from all files' }],
}

phase('Contact Photo')
await agent(
  'Remove all contact photo fetching code across the codebase. This is the main branch state where the code still exists.\n\n' +
  'Files to modify:\n\n' +
  '1. MainActivity.kt in app/src/main/java/com/shrivatsav/monomail/:\n' +
  '   - Remove the requestPermissionsLauncher field (registerForActivityResult block)\n' +
  '   - Remove the requestPermissionsOnLaunch() method entirely\n' +
  '   - Remove the call to requestPermissionsOnLaunch() from onCreate()\n' +
  '   - Remove imports: Manifest, PackageManager, Build, ContextCompat, ActivityResultContracts\n\n' +
  '2. AndroidManifest.xml in app/src/main/:\n' +
  '   - Remove: <uses-permission android:name="android.permission.READ_CONTACTS" />\n' +
  '   - Keep POST_NOTIFICATIONS (still needed for notification delivery)\n\n' +
  '3. InboxViewModel.kt in app/src/main/java/com/shrivatsav/monomail/ui/screens/inbox/:\n' +
  '   - Remove: import android.net.Uri\n' +
  '   - Remove: import com.shrivatsav.monomail.data.repository.ContactPhotoProvider\n' +
  '   - Remove: private val contactPhotoProvider: ContactPhotoProvider from constructor params\n' +
  '   - Remove: private val _contactPhotoUris, val contactPhotoUris fields\n' +
  '   - Remove the entire collector coroutine: the one doing state.collect { s -> ... contactPhotoProvider.getPhotoUri ... }\n' +
  '   - Remove unused imports: Dispatchers, withContext (only if no other usage)\n\n' +
  '4. EmailDetailViewModel.kt in detail package:\n' +
  '   - Remove: import android.net.Uri\n' +
  '   - Remove: import com.shrivatsav.monomail.data.repository.ContactPhotoProvider\n' +
  '   - Remove: private val contactPhotoProvider from constructor params\n' +
  '   - Remove: private val _contactPhotoUris, val contactPhotoUris fields\n' +
  '   - Remove the contact photo collector in init (the second init coroutine)\n' +
  '   - Remove unused imports: Dispatchers, withContext (only if no other usage)\n\n' +
  '5. InboxScreen.kt in inbox package:\n' +
  '   - Remove line: val contactPhotoUris by viewModel.contactPhotoUris.collectAsState()\n' +
  '   - Change both SwipeableEmailItem contactPhotoUri from contactPhotoUris[...] to null\n\n' +
  '6. EmailItem.kt in inbox package:\n' +
  '   - Remove: import android.net.Uri\n' +
  '   - Remove: import coil.compose.AsyncImagePainter\n' +
  '   - Remove: import coil.compose.rememberAsyncImagePainter\n' +
  '   - Remove: import coil.request.ImageRequest\n' +
  '   - Remove: import androidx.compose.ui.platform.LocalContext\n' +
  '   - In SenderAvatar: remove context val, imageUrl val, painter val, imageSuccess val entirely\n' +
  '   - Replace the if (imageSuccess) { Image } else { Text } block in SenderAvatar with just Text(senderInitial) as the non-bulk-mode fallback\n' +
  '   - Remove the contactPhotoUri: Uri? = null parameter from EmailItem function signature\n\n' +
  '7. SwipeableEmailItem.kt in inbox package:\n' +
  '   - Remove: import android.net.Uri\n' +
  '   - Remove contactPhotoUri: Uri? = null from SwipeableEmailItem function signature\n' +
  '   - Remove contactPhotoUri from both EmailItem call sites inside SwipeableEmailItem\n\n' +
  '8. AppModule.kt in di package:\n' +
  '   - Remove: import com.shrivatsav.monomail.data.repository.ContactPhotoProvider\n' +
  '   - Remove the provideContactPhotoProvider() method if it exists\n\n' +
  '9. EmailDetailScreen.kt in detail package:\n' +
  '   - Remove: import android.net.Uri\n' +
  '   - Remove: import coil.compose.AsyncImage\n' +
  '   - Remove: import androidx.compose.ui.layout.ContentScale\n' +
  '   - Remove: val contactPhotoUris by viewModel.contactPhotoUris.collectAsState()\n' +
  '   - Remove all contactPhotoUris references from the screen\n\n' +
  'Read each file first before modifying.',
  { label: 'Contact Photo Removal', phase: 'Contact Photo', isolation: 'worktree' }
)
