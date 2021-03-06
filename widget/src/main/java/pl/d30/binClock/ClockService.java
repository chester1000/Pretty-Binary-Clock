package pl.d30.binClock;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class ClockService extends Service {

    private static final String TAG = "BinaryService";

    private Provider  mReceiver;
    private AlarmCore alarm;

    @Override
    public void onCreate() {
        super.onCreate();

        alarm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
            ? new AlarmKitkat()
            : new AlarmLegacy();

        registerScreenActions();
    }

    private void registerScreenActions() {
        mReceiver = new Provider();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);

        if (alarm != null)
            alarm.stop();

        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // (intent == null) on service restart
        if (intent != null) {
            String action = intent.getAction();

            if (action == null)
                Log.w(TAG, "action be null");
            else
                processTheAction(action, intent.getExtras());
        }

        // HALT operations if no widgets
        if (Widget.getValidWidgets(this).size() == 0)
            stopSelf();

        return super.onStartCommand(intent, flags, startId);
    }

    private void processTheAction(String action, Bundle b) {
        switch (action) {
            case Intent.ACTION_SCREEN_OFF:
                alarm.stop();
                break;

            case Intent.ACTION_SCREEN_ON:
            case Provider.BINARY_ALARM_START:
                if (b != null && b.getBoolean(Provider.KEY_CLEAN, false))
                    Widget.clearInvalidWidgets(this);

            case Provider.BINARY_WIDGET_CREATE:
                alarm.start();
                break;

            case Provider.BINARY_WIDGET_CHANGE:
                new Widget(this, b.getInt(Provider.KEY_WID))
                    .setDimensions(
                        b.getInt(Widget.MIN_HEIGHT_RAW),
                        b.getInt(Widget.MAX_HEIGHT_RAW),
                        b.getInt(Widget.MIN_WIDTH_RAW),
                        b.getInt(Widget.MAX_WIDTH_RAW)
                    );

                alarm.start();

                break;

            case Provider.BINARY_WIDGET_REMOVE:
                int[] wids = b.getIntArray(Provider.KEY_WIDS);
                if (wids != null)
                    for (int wid : wids)
                        new Widget(this, wid).remove();
                break;

            case Provider.BINARY_ALARM_STOP:
                stopSelf();
                break;

            default:
                Log.w(TAG, "~> Unknown intent: " + action);
                break;
        }
    }

    public static void sendToService(Context c, String action, Bundle b) {
        Intent i = new Intent(c, ClockService.class).setAction(action);
        if (b != null) i.putExtras(b);
        c.startService(i);
    }

    public static void sendToService(Context c, String action) {
        sendToService(c, action, null);
    }

    public static boolean isRunning(Context c) {
        ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
            if (ClockService.class.getName().equals(service.service.getClassName()))
                return true;

        return false;
    }

    private class AlarmLegacy extends AlarmCore {

        @Override
        void start() {
            super.start();
            getAlarmManager().setRepeating(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + getDelay(),
                getInterval(),
                getPendingIntent()
            );
        }

        @Override
        void stop() {
            getAlarmManager().cancel(getPendingIntent());
        }

        protected PendingIntent getPendingIntent() {
            return PendingIntent.getBroadcast(
                ClockService.this,
                0,
                getIntent(),
                0
            );
        }
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    private class AlarmKitkat extends AlarmCore {

        private Timer t = new Timer();

        @Override
        void start() {
            super.start();

            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                sendBroadcast(getIntent());
                }
            }, getDelay(), getInterval());
        }

        @Override
        void stop() {
            t.cancel();
            t.purge();
            t = new Timer();
        }
    }

    private abstract class AlarmCore {

        public static final int SECOND = 1000;
        public static final int MINUTE = 60 * SECOND;

        protected boolean secondsEnabled;

        protected AlarmManager getAlarmManager() {
            return (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        }

        void setSeconds(boolean enabled) {
            secondsEnabled = enabled;
        }

        void start() {
            setSeconds(Widget.areSecondsRequired(ClockService.this));
            sendBroadcast(getIntent());
            stop();
        }

        abstract void stop();

        protected Intent getIntent() {
            Intent i = new Intent(ClockService.this, Provider.class);
            i.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
            i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, Widget.getIds(ClockService.this));
            return i;
        }

        protected long getDelay() {
            if (secondsEnabled)
                return 0;

            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.MINUTE, cal.get(Calendar.MINUTE) + 1);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);

            return cal.getTimeInMillis() - Calendar.getInstance().getTimeInMillis();
        }

        protected long getInterval() {
            return secondsEnabled
                ? SECOND
                : MINUTE;
        }
    }
}
