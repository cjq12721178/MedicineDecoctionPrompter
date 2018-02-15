package tool.cjq.com.medicinedecoctionprompter;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.PowerManager;
import android.text.TextUtils;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * Created by KAT on 2017/1/5.
 */
public class ContinuousTask {

    private transient final Context context;
    private final String name;
    private int startEventNumber = -1;
    private List<Event> events;
    private transient TimerTask countDownTask;

    private ContinuousTask(Context context, String name) {
        this.context = context;
        this.name = name;
    }

    public static ContinuousTask build(Context context, String name) {
        if (context == null || TextUtils.isEmpty(name))
            return null;
        ContinuousTask task = new ContinuousTask(context, name);
        task.importEvents();
        if (task.events == null) {
            task.events = new ArrayList<>();
        }
        return task;
    }

    public boolean addEvent(Event event) {
        if (event == null)
            return false;
        return events.add(event);
    }

    private void importEvents() {
        FileInputStream fis = null;
        ObjectInputStream ois = null;
        try {
            fis = context.openFileInput(getFileName(name));
            ois = new ObjectInputStream(fis);
            startEventNumber = (int)ois.readObject();
            events = (ArrayList<Event>)ois.readObject();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ois != null) {
                try {
                    ois.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean exportEvents() {
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = context.openFileOutput(getFileName(name), Context.MODE_PRIVATE);
            oos = new ObjectOutputStream(fos);
            oos.writeObject(startEventNumber);
            oos.writeObject(events);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    private static String getFileName(String taskName) {
        return taskName + ".ctt";
    }

    private void release() {
        release(context, name);
    }

    //注意，若某个任务正在执行，则调用此方法后，后续任务将不会继续执行
    public static void release(Context context, String taskName) {
        if (context == null || taskName == null)
            return;
        try {
            context.deleteFile(getFileName(taskName));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int getEventSize() {
        return events.size();
    }

    public Event getEvent(int number) {
        return number >= 0 && number < events.size() ? events.get(number) : null;
    }

    public void start() {
        start(0);
    }

    //注意，为了简便起见，所有任务将同时开启定时
    //number:[0,Integer.MAX_VALUE]
    public synchronized boolean start(int eventNumber) {
        if (eventNumber < 0 ||
                eventNumber >= events.size())
            return false;
        AlarmManager alarmManager = getAlarmManager(context);
        long currentTime = System.currentTimeMillis();
        //取消当前任务
        cancelRunningEvent(alarmManager, currentTime);
        //清除启动任务之前所有任务的启动时间
        clearEventStartTime(startEventNumber);
        //重新设置将启动任务之后(含本身)所有任务的启动时间
        startEventNumber = eventNumber;
        long launchTime = currentTime;
        for (int i = eventNumber, size = events.size();i < size;++i) {
            events.get(i).setStartTime(launchTime);
            launchTime += events.get(i).getDuration();
        }
        //保存所有任务信息
        if (!exportEvents()) {
            ContinuousTask origin = build(context, name);
            if (origin.startEventNumber == -1) {
                clearState();
            } else {
                startEventNumber = origin.startEventNumber;
                events = origin.events;
            }
            return false;
        }

        //启动序号为eventNumber的定时任务
        startEvent(alarmManager, eventNumber);
        return true;
    }

    private static AlarmManager getAlarmManager(Context context) {
        return (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    }

    public void stop() {
        //取消当前任务
        cancelRunningEvent(getAlarmManager(context), System.currentTimeMillis());
    }

    public void reset() {
        stop();
        clearState();
        exportEvents();
    }

    private void clearState() {
        clearEventStartTime(events.size());
        startEventNumber = -1;
    }

    private void clearEventStartTime(int exclusiveEndNumber) {
        for (int i = 0, end = Math.min(exclusiveEndNumber, events.size());i < end;++i) {
            events.get(i).setStartTime(0);
        }
    }

    private void startEvent(AlarmManager alarmManager, int eventNumber) {
        events.get(eventNumber).start(context, alarmManager, name, eventNumber);
    }

    private void cancelRunningEvent(AlarmManager alarmManager, long currentTime) {
        if (startEventNumber == -1)
            return;
        Event event;
        for (int m, l = startEventNumber, h = events.size() - 1;l <= h;) {
            m = (l + h) >> 1;
            event = events.get(m);
            switch (event.getState(currentTime)) {
                case EXECUTING: {
                    event.cancel(context, alarmManager, m);
                    return;
                }
                case NOT_START:h = m - 1;break;
                case FINISHED:l = m + 1;break;
            }
        }
    }

    //handler为null则定时触发方法运行于定时器线程，否则运行于handler绑定的线程
    public void startCountDown(final Timer timer,
                               final OnCountDownListener listener,
                               final Handler handler) {
        if (timer == null || listener == null || events.size() == 0)
            return;
        final long countDownMilliseconds = events.get(events.size() - 1)
                .getScheduledFinishTime() - System.currentTimeMillis();
        if (countDownMilliseconds < 0)
            return;
        stopCountDown();
        final long TIME_INTERVAL = TimeUnit.SECONDS.toMillis(1);
        timer.scheduleAtFixedRate(countDownTask = new TimerTask() {

            long countDownMillis = countDownMilliseconds;
            Runnable runImpl = new Runnable() {
                @Override
                public void run() {
                    Event event;
                    long leftCountDowns = countDownMillis;
                    long seconds;
                    long minutes;
                    for (int i = events.size() - 1;i >= 0;--i) {
                        event = events.get(i);
                        if (leftCountDowns >= 0) {
                            minutes = TimeUnit.MILLISECONDS.toMinutes(leftCountDowns);
                            seconds = TimeUnit.MILLISECONDS.toSeconds(leftCountDowns) -
                                    TimeUnit.MINUTES.toSeconds(minutes);
                            listener.onCountDown(ContinuousTask.this, i, minutes, seconds);
                        } else {
                            break;
                        }
                        leftCountDowns -= event.getDuration();
                    }
                    countDownMillis -= TIME_INTERVAL;
                    if (countDownMillis <= 0) {
                        cancel();
                    }
                }
            };

            @Override
            public void run() {
                if (handler == null) {
                    runImpl.run();
                } else {
                    handler.post(runImpl);
                }
            }
        }, 0, TIME_INTERVAL);
    }

    public void stopCountDown() {
        if (countDownTask != null) {
            countDownTask.cancel();
            countDownTask = null;
        }
    }

    public interface OnCountDownListener {
        void onCountDown(ContinuousTask task, int eventNumber, long minutes, long seconds);
    }

    public String getName() {
        return name;
    }

    public long getStartTime() {
        if (startEventNumber == -1)
            return 0;
        return events.get(startEventNumber).getStartTime();
    }

    public State getState() {
        if (startEventNumber == -1) {
            return events.size() == 0 ? State.NOT_SCHEDULED : State.NOT_START;
        }
        return events.get(events.size() - 1).getState() == State.FINISHED ?
                State.FINISHED : State.EXECUTING;
    }

    public enum State {
        //startTime == 0
        NOT_SCHEDULED,
        //currentTime < startTime
        NOT_START,
        //startTime <= currentTime < finishTime
        EXECUTING,
        //finishTime <= currentTime
        FINISHED;
    }

    public static class EventReceiver extends BroadcastReceiver {

        private static final String TASK_NAME = "name";
        private static final String EVENT_NUMBER = "number";

        @Override
        public void onReceive(Context context, Intent intent) {
            //保持CPU5秒唤醒时间，以执行后续任务
            try {
                PowerManager powerManager = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK , "ctt");
                wakeLock.acquire(5000);
            } catch (Exception e) {
            }
            ContinuousTask task = ContinuousTask.build(context, intent.getStringExtra(TASK_NAME));
            int currentEventNumber = intent.getIntExtra(EVENT_NUMBER, -1);
            if (task == null || currentEventNumber == -1)
                return;
            //为下一个任务设置定时
            if (currentEventNumber + 1 < task.getEventSize()) {
                task.startEvent(getAlarmManager(context),
                        currentEventNumber + 1);
            }
            //执行当前任务
            Event currentEvent = task.getEvent(currentEventNumber);
            if (currentEvent != null) {
                if (currentEvent.onExecute(context, intent) && currentEventNumber + 1 == task.getEventSize()) {
                    task.release();
                }
            }
        }
    }

    public static abstract class Event implements Serializable {

        private final String name;
        private final long duration;
        private long startTime;

        public Event(String name, long duration) {
            this.name = name;
            this.duration = duration;
        }

        private void start(Context context, AlarmManager alarmManager, String taskName, int number) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP,
                    startTime + duration,
                    buildOperation(context, taskName, number));
        }

        private PendingIntent buildOperation(Context context, String taskName, int number) {
            Intent intent = new Intent("android.intent.action.EXECUTE_EVENT");
            intent.putExtra(EventReceiver.TASK_NAME, taskName)
                    .putExtra(EventReceiver.EVENT_NUMBER, number);
            onPrepare(context, intent);
            return PendingIntent.getBroadcast(context, taskName.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }

        private void cancel(Context context, AlarmManager alarmManager, int number) {
            alarmManager.cancel(buildOperation(context, name, number));
            startTime = 0;
        }

        private void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public State getState() {
            return getState(System.currentTimeMillis());
        }

        private State getState(long currentTime) {
            if (startTime == 0)
                return State.NOT_SCHEDULED;
            if (currentTime < startTime)
                return State.NOT_START;
            if (currentTime < startTime + duration)
                return State.EXECUTING;
            return State.FINISHED;
        }

        public String getName() {
            return name;
        }

        public long getDuration() {
            return duration;
        }

        public long getStartTime() {
            return startTime;
        }

        public long getScheduledFinishTime() {
            return startTime + duration;
        }

        protected abstract void onPrepare(Context context, Intent intent);

        //返回true，将删除连续任务在文档中的信息，仅当其为最后一个任务时有用
        protected abstract boolean onExecute(Context context, Intent intent);
    }
}
