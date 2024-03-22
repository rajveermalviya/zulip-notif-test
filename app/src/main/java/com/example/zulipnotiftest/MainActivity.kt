package com.example.zulipnotiftest

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.os.StrictMode
import android.os.StrictMode.ThreadPolicy
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.InboxStyle
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.example.zulipnotiftest.ui.theme.ZulipNotifTestTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.net.URL
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val policy = ThreadPolicy.Builder().permitAll().build()
        StrictMode.setThreadPolicy(policy)

        setContent {
            ZulipNotifTestTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    App()
                }
            }
        }
    }
}

@Composable
fun App(modifier: Modifier = Modifier) {
    val activity = LocalContext.current as Activity
    val coroutineScope = rememberCoroutineScope()

    Column (
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                coroutineScope.launch {
                    showNotification(activity)
                }
            }
        }) {
            Text("Show notification")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun AppPreview() {
    ZulipNotifTestTheme {
        App()
    }
}



@RequiresApi(Build.VERSION_CODES.O)
suspend fun showNotification(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            0)
        if (ActivityCompat.checkSelfPermission(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
    }

    val notificationManager = NotificationManagerCompat.from(activity)

    val channelId = "my_channel_id"
    val channel = NotificationChannel(
        channelId,
        "My Channel",
        NotificationManager.IMPORTANCE_DEFAULT
    )
    notificationManager.createNotificationChannel(channel)

    val personIcon1 = fetchBitmap(URL("https://secure.gravatar.com/avatar/1020089e7fc6f2b6654adbe2c56bcb18?d=identicon&version=1&s=50")) ?: return
    val personIcon2 = fetchBitmap(URL("https://secure.gravatar.com/avatar/3d30c3073a25ead74bac53d08f192fcb?d=identicon&version=1&s=50")) ?: return
    val user = Person.Builder().setName("You").build()
    val person1 =
        Person.Builder().setName("John Doe").setIcon(IconCompat.createWithBitmap(personIcon1))
            .build()
    val person2 =
        Person.Builder().setName("Jane Doe").setIcon(IconCompat.createWithBitmap(personIcon2))
            .build()

    for (i in 1..4) {
        val messagingStyle = NotificationCompat.MessagingStyle(user)
        messagingStyle.setGroupConversation(true)
        messagingStyle.addMessage(
            NotificationCompat.MessagingStyle.Message(
                "Hello!",
                Instant.now().toEpochMilli(),
                person1,
            )
        )
        messagingStyle.addMessage(
            NotificationCompat.MessagingStyle.Message(
                "How to...",
                Instant.now().toEpochMilli() + 2000,
                person1,
            )
        )
        messagingStyle.addMessage(
            NotificationCompat.MessagingStyle.Message(
                "Hello 2!",
                Instant.now().toEpochMilli(),
                person2,
            )
        )
        messagingStyle.addMessage(
            NotificationCompat.MessagingStyle.Message(
                "How to 2...",
                Instant.now().toEpochMilli() + 2000,
                person2,
            )
        )

        val (groupTitle, groupId) = if (i % 2 == 0) {
            Pair("https://rust-lang.zulipchat.com", 0xfff)
        } else {
            Pair("https://chat.zulip.org", 0xaaa)
        }

        val shortcutId = "my_shortcut_id$i"
        ShortcutManagerCompat.pushDynamicShortcut(activity, ShortcutInfoCompat.Builder(activity, shortcutId)
            .setIsConversation()
            .setShortLabel("#stream$i > Topic$i")
            .setLongLived(true)
            .setIntent(Intent(Intent.ACTION_DEFAULT))
            .build())

        notificationManager.notify(i,
            NotificationCompat.Builder(activity, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setShortcutId(shortcutId)
                .setStyle(messagingStyle)
                .setGroup(groupId.toString())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build())

        notificationManager.notify(groupId,
            NotificationCompat.Builder(activity, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setShortcutId(shortcutId)
                .setGroup(groupId.toString())
                .setGroupSummary(true)
                .setContentTitle(groupTitle)
                .setStyle(InboxStyle().setSummaryText(groupTitle))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build())

        ShortcutManagerCompat.removeDynamicShortcuts(activity, arrayListOf(shortcutId))
    }
}

suspend fun fetchBitmap(url: URL): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val connection = url.openConnection()
        connection.useCaches = true
        (connection.content as? InputStream)?.use {
            BitmapFactory.decodeStream(it)
        }
    } catch (e: IOException) {
        Log.e("ZULIP_TEST", e.toString())
        null
    }
}
