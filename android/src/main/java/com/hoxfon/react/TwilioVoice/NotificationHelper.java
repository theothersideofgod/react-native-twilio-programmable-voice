package com.hoxfon.react.TwilioVoice;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;

import com.facebook.react.bridge.ReactApplicationContext;
import com.twilio.voice.CallInvite;

import java.util.List;

import static android.content.Context.ACTIVITY_SERVICE;
import static com.facebook.react.common.ApplicationHolder.getApplication;

import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.LOG_TAG;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_ANSWER_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_REJECT_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_HANGUP_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_INCOMING_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_MISSED_CALL;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_CALL_INVITE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.NOTIFICATION_TYPE;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.CALL_SID_KEY;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.INCOMING_NOTIFICATION_PREFIX;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.MISSED_CALLS_GROUP;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.MISSED_CALLS_NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.HANGUP_NOTIFICATION_ID;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.PREFERENCE_KEY;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.ACTION_CLEAR_MISSED_CALLS_COUNT;
import static com.hoxfon.react.TwilioVoice.TwilioVoiceModule.CLEAR_MISSED_CALLS_NOTIFICATION_ID;

public class NotificationHelper {

    private NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();

    public NotificationHelper() {}

    public int getApplicationImportance(ReactApplicationContext context) {
        ActivityManager activityManager = (ActivityManager) context.getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        if (processInfos == null) {
            return 0;
        }
        for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.processName.equals(getApplication().getPackageName())) {
                return processInfo.importance;
            }
        }
        return 0;
    }

    public Class getMainActivityClass(ReactApplicationContext context) {
        String packageName = context.getPackageName();
        Intent launchIntent = context.getPackageManager().getLaunchIntentForPackage(packageName);
        String className = launchIntent.getComponent().getClassName();
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public Intent getLaunchIntent(ReactApplicationContext context,
                                  Bundle bundle,
                                  CallInvite callInvite,
                                  Boolean shouldStartNewTask,
                                  int appImportance
    ) {
        int notificationId = Integer.parseInt(bundle.getString("id"));
        Intent launchIntent = new Intent(context, getMainActivityClass(context));

        int launchFlag = Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP;
        if (shouldStartNewTask ||
                appImportance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND ||
                appImportance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE) {
            launchFlag = Intent.FLAG_ACTIVITY_NEW_TASK;
        }
        launchIntent.setAction(ACTION_INCOMING_CALL)
                .putExtra(NOTIFICATION_ID, notificationId)
                .addFlags(
                        launchFlag +
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED +
                        WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD +
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON +
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                );

        if (callInvite != null) {
            launchIntent.putExtra(INCOMING_CALL_INVITE, callInvite);
        }
        return launchIntent;
    }

    public void createIncomingCallNotification(ReactApplicationContext context,
                                               CallInvite callInvite,
                                               Bundle bundle,
                                               Intent launchIntent) {

        int notificationId = Integer.parseInt(bundle.getString("id"));

        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID, notificationId);
        extras.putString(CALL_SID_KEY, callInvite.getCallSid());
        extras.putString(NOTIFICATION_TYPE, ACTION_INCOMING_CALL);

        /*
         * Create the notification shown in the notification drawer
         */
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_CALL)
                        .setSmallIcon(R.drawable.ic_call_white_24dp)
                        .setContentTitle("Incoming call")
                        .setContentText(callInvite.getFrom() + " is calling")
                        .setAutoCancel(true)
                        .setExtras(extras)
                        .setContentIntent(pendingIntent);

        // build notification large icon
        Resources res = context.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (largeIconResId != 0) {
                notification.setLargeIcon(largeIconBitmap);
            }
        }

        // Reject action
        Intent rejectIntent = new Intent(ACTION_REJECT_CALL)
                .putExtra(NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingRejectIntent = PendingIntent.getBroadcast(context, 1, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(0, "DISMISS", pendingRejectIntent);

        // Answer action
        Intent answerIntent = new Intent(ACTION_ANSWER_CALL);
        answerIntent
                .putExtra(NOTIFICATION_ID, notificationId)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingAnswerIntent = PendingIntent.getBroadcast(context, 0, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        notification.addAction(R.drawable.ic_call_white_24dp, "ANSWER", pendingAnswerIntent);

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(notificationId, notification.build());
        TwilioVoiceModule.callNotificationMap.put(INCOMING_NOTIFICATION_PREFIX+callInvite.getCallSid(), notificationId);
    }

    public void createMissedCallNotification(ReactApplicationContext context, CallInvite callInvite) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();

        /*
         * Create a PendingIntent to specify the action when the notification is
         * selected in the notification drawer
         */
        Intent intent = new Intent(context, getMainActivityClass(context));
        intent.setAction(ACTION_MISSED_CALL)
                .putExtra(NOTIFICATION_ID, MISSED_CALLS_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Intent clearMissedCallsCountIntent = new Intent(ACTION_CLEAR_MISSED_CALLS_COUNT)
                .putExtra(NOTIFICATION_ID, CLEAR_MISSED_CALLS_NOTIFICATION_ID);
        PendingIntent clearMissedCallsCountPendingIntent = PendingIntent.getBroadcast(context, 0, clearMissedCallsCountIntent, 0);
        /*
         * Pass the notification id and call sid to use as an identifier to open the notification
         */
        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID, MISSED_CALLS_NOTIFICATION_ID);
        extras.putString(CALL_SID_KEY, callInvite.getCallSid());
        extras.putString(NOTIFICATION_TYPE, ACTION_MISSED_CALL);

        /*
         * Create the notification shown in the notification drawer
         */
        NotificationCompat.Builder notification =
                new NotificationCompat.Builder(context)
                        .setGroup(MISSED_CALLS_GROUP)
                        .setGroupSummary(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                        .setSmallIcon(R.drawable.ic_call_missed_white_24dp)
                        .setContentTitle("Missed call")
                        .setContentText(callInvite.getFrom() + " called")
                        .setAutoCancel(true)
                        .setShowWhen(true)
                        .setExtras(extras)
                        .setDeleteIntent(clearMissedCallsCountPendingIntent)
                        .setContentIntent(pendingIntent);

        int missedCalls = sharedPref.getInt(MISSED_CALLS_GROUP, 0);
        missedCalls++;
        if (missedCalls == 1) {
            inboxStyle = new NotificationCompat.InboxStyle();
            inboxStyle.setBigContentTitle("Missed call");
        } else {
            inboxStyle.setBigContentTitle(String.valueOf(missedCalls) + " missed calls");
        }
        inboxStyle.addLine("from: " +callInvite.getFrom());
        sharedPrefEditor.putInt(MISSED_CALLS_GROUP, missedCalls);
        sharedPrefEditor.commit();

        notification.setStyle(inboxStyle);

        // build notification large icon
        Resources res = context.getResources();
        int largeIconResId = res.getIdentifier("ic_launcher", "mipmap", context.getPackageName());
        Bitmap largeIconBitmap = BitmapFactory.decodeResource(res, largeIconResId);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && largeIconResId != 0) {
            notification.setLargeIcon(largeIconBitmap);
        }

        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(MISSED_CALLS_NOTIFICATION_ID, notification.build());
    }

    public void createHangupLocalNotification(ReactApplicationContext context, String callSid, String caller) {
        PendingIntent pendingHangupIntent = PendingIntent.getBroadcast(
                context,
                0,
                new Intent(ACTION_HANGUP_CALL).putExtra(NOTIFICATION_ID, HANGUP_NOTIFICATION_ID),
                PendingIntent.FLAG_UPDATE_CURRENT
        );
        Intent launchIntent = new Intent(context, getMainActivityClass(context));
        launchIntent.setAction(ACTION_INCOMING_CALL)
                .putExtra(NOTIFICATION_ID, HANGUP_NOTIFICATION_ID)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent activityPendingIntent = PendingIntent.getActivity(context, 0, launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        /*
         * Pass the notification id and call sid to use as an identifier to cancel the
         * notification later
         */
        Bundle extras = new Bundle();
        extras.putInt(NOTIFICATION_ID, HANGUP_NOTIFICATION_ID);
        extras.putString(CALL_SID_KEY, callSid);
        extras.putString(NOTIFICATION_TYPE, ACTION_HANGUP_CALL);

        NotificationCompat.Builder notification = new NotificationCompat.Builder(context)
                .setContentTitle("Call in progress")
                .setContentText(caller)
                .setSmallIcon(R.drawable.ic_call_white_24dp)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setUsesChronometer(true)
                .setExtras(extras)
                .setContentIntent(activityPendingIntent);

        notification.addAction(0, "HANG UP", pendingHangupIntent);
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.notify(HANGUP_NOTIFICATION_ID, notification.build());
    }

    public void removeIncomingCallNotification(ReactApplicationContext context,
                                               CallInvite callInvite,
                                               int notificationId) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            if (callInvite != null && callInvite.isCancelled()) {
                /*
                 * If the incoming call message was cancelled then remove the notification by matching
                 * it with the call sid from the list of notifications in the notification drawer.
                 */
                StatusBarNotification[] activeNotifications = notificationManager.getActiveNotifications();
                for (StatusBarNotification statusBarNotification : activeNotifications) {
                    Notification notification = statusBarNotification.getNotification();
                    String notificationType = notification.extras.getString(NOTIFICATION_TYPE);
                    if (callInvite.getCallSid().equals(notification.extras.getString(CALL_SID_KEY)) &&
                            notificationType != null && notificationType.equals(ACTION_INCOMING_CALL)) {
                        notificationManager.cancel(notification.extras.getInt(NOTIFICATION_ID));
                    }
                }
            } else if (notificationId != 0) {
                notificationManager.cancel(notificationId);
            }
        } else {
            if (notificationId != 0) {
                Log.d(LOG_TAG, "cancel direct notification id "+ notificationId);
                notificationManager.cancel(notificationId);
            } else if (callInvite != null) {
                String notificationKey = INCOMING_NOTIFICATION_PREFIX+callInvite.getCallSid();
                if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                    notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
                    Log.d(LOG_TAG, "cancel map notification id " + notificationId);
                    notificationManager.cancel(notificationId);
                    TwilioVoiceModule.callNotificationMap.remove(notificationKey);
                }
            }
        }
    }

    public void removeHangupNotification(ReactApplicationContext context) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(HANGUP_NOTIFICATION_ID);
    }
}
