package com.stemaker.ampachealbumplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.stemaker.ampachealbumplayer.musicdb.MusicDb
import com.stemaker.ampachealbumplayer.player.StatusDesc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

const val NOTIFICATION_STATUS_ID = 1

object PlayerNotificationHandler {
    var inited = false

    fun initialize() {
        if(!inited) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val ctx = AmpacheAlbumPlayerApp.appContext
                val name = ctx.getString(R.string.notification_chname)
                val descriptionText = ctx.getString(R.string.notification_chdescription)
                val importance = NotificationManager.IMPORTANCE_DEFAULT
                val channel = NotificationChannel(name, name, importance).apply {
                    description = descriptionText
                }
                // Register the channel with the system
                val notificationManager: NotificationManager =
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }
        }
    }

    fun getIdleNotification(): Notification {
        val ctx = AmpacheAlbumPlayerApp.appContext
        val intent = Intent(ctx, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0)

        val notification = NotificationCompat.Builder(ctx, ctx.getString(R.string.notification_chname))
            .setSmallIcon(R.drawable.ic_play_circle_outline_black_24dp)
            .setContentTitle("Not playing")
            .setContentText("")
            .setLargeIcon(BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_stop_black_24dp))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()
        return notification
    }

    fun statusUpdate(status: StatusDesc) {
        val ctx = AmpacheAlbumPlayerApp.appContext
        val intent = Intent(ctx, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent: PendingIntent = PendingIntent.getActivity(ctx, 0, intent, 0)

        val builder = NotificationCompat.Builder(ctx, ctx.getString(R.string.notification_chname))
            .setSmallIcon(R.drawable.ic_play_circle_outline_black_24dp)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
        GlobalScope.launch(Dispatchers.Main) {
            if(status.newState == StatusDesc.States.PLAYING) {
                builder.setContentTitle("Album: ${MusicDb.getAlbumByUid(status.albumId).name}")
                builder.setContentText("Titel: ${MusicDb.getSongByUid(status.songId).title}")
                //builder.setContentText("Titel: titttle")
                builder.setLargeIcon(BitmapFactory.decodeResource( ctx.resources, R.drawable.ic_play_arrow_black_24dp ))
            } else if(status.newState == StatusDesc.States.PAUSED) {
                builder.setContentTitle("Album: ${MusicDb.getAlbumByUid(status.albumId).name}")
                //builder.setContentText("Titel: ${MusicDb.getSongByUid(status.songId).title}")
                builder.setContentText("Titel: titttle")
                builder.setLargeIcon(BitmapFactory.decodeResource( ctx.resources, R.drawable.ic_pause_black_24dp))
            } else {
                builder.setContentTitle("Not playing")
                builder.setContentText("")
                builder.setLargeIcon( BitmapFactory.decodeResource(ctx.resources, R.drawable.ic_stop_black_24dp))
            }
            with(NotificationManagerCompat.from(ctx)) {
                notify(NOTIFICATION_STATUS_ID, builder.build())
            }
        }
    }
}