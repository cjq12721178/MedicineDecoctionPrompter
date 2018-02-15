package tool.cjq.com.medicinedecoctionprompter;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Handler;
import android.support.annotation.IdRes;
import android.support.annotation.IntegerRes;
import android.support.annotation.RawRes;
import android.support.annotation.StringRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private Handler handler;
    private Timer timer;
    private DecoctionGroup firstGroup;
    private DecoctionGroup secondGroup;
    private Descriptor descriptor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        handler = new Handler();
        timer = new Timer();
        setDecoctionViews();
    }

    @Override
    protected void onDestroy() {
        timer.cancel();
        super.onDestroy();
    }

    private void setDecoctionViews() {
        descriptor = new Descriptor(this);
        firstGroup = getDecoctionGroup(R.id.il_decoction_first,
                importDecoctionTask(R.string.task_first_decoction,
                        R.integer.first_medicine_duration_waiting_for_first_stir,
                        R.integer.first_medicine_duration_waiting_for_second_stir,
                        R.integer.first_medicine_duration_waiting_for_finish));
        secondGroup = getDecoctionGroup(R.id.il_decoction_second,
                importDecoctionTask(R.string.task_second_decoction,
                        R.integer.second_medicine_duration_waiting_for_first_stir,
                        R.integer.second_medicine_duration_waiting_for_second_stir,
                        R.integer.second_medicine_duration_waiting_for_finish));
        setDecoctionView(firstGroup);
        setDecoctionView(secondGroup);
    }

    private ContinuousTask importDecoctionTask(@StringRes int nameId,
                                               @IntegerRes int duration1,
                                               @IntegerRes int duration2,
                                               @IntegerRes int duration3) {
        ContinuousTask decoctionTask = ContinuousTask.build(this, getString(nameId));
        if (decoctionTask.getEventSize() == 0) {
            Resources resources = getResources();
            decoctionTask.addEvent(new WaitForFirstStirEvent(getString(R.string.event_first_stir),
                    TimeUnit.MINUTES.toMillis(resources.getInteger(duration1))));
            decoctionTask.addEvent(new WaitForSecondStirEvent(getString(R.string.event_second_stir),
                    TimeUnit.MINUTES.toMillis(resources.getInteger(duration2))));
            decoctionTask.addEvent(nameId == R.string.task_first_decoction ?
                    new WaitForFirstDecoctionFinishEvent(getString(R.string.event_decoction_finish),
                            TimeUnit.MINUTES.toMillis(resources.getInteger(duration3))) :
                    new WaitForSecondDecoctionFinishEvent(getString(R.string.event_decoction_finish),
                            TimeUnit.MINUTES.toMillis(resources.getInteger(duration3))));
        }
        return decoctionTask;
    }

    private DecoctionGroup getDecoctionGroup(@IdRes int groupId, ContinuousTask decoctionTask) {
        DecoctionGroup group = new DecoctionGroup();
        View parent = findViewById(groupId);
        group.tvName = (TextView) parent.findViewById(R.id.tv_decoction_name);
        group.tvStartTime = (TextView)parent.findViewById(R.id.tv_start_time);
        group.tvFirstStir = (TextView)parent.findViewById(R.id.tv_first_stir);
        group.tvSecondStir = (TextView)parent.findViewById(R.id.tv_second_stir);
        group.tvState = (TextView)parent.findViewById(R.id.tv_decoction_state);
        group.decoctionTask = decoctionTask;
        return group;
    }

    private void setDecoctionView(DecoctionGroup group) {
        group.tvName.setText(group.decoctionTask.getName());
        group.tvStartTime.setOnClickListener(group);
        group.setStartTime();
        setEventViewState(group.tvFirstStir, group.decoctionTask, 0);
        setEventViewState(group.tvSecondStir, group.decoctionTask, 1);
        setEventViewState(group.tvState, group.decoctionTask, 2);
        group.decoctionTask.startCountDown(timer, group, handler);
    }

    private void setEventViewState(TextView tv, ContinuousTask task, int eventNumber) {
        String description = eventNumber == 2 ?
                descriptor.getTaskStateDescription(task) :
                descriptor.getEventStateDescription(task.getEvent(eventNumber));
        if (description != null) {
            tv.setText(description);
        }
    }

    private class DecoctionGroup implements
            ContinuousTask.OnCountDownListener,
            View.OnClickListener{

        TextView tvName;
        TextView tvStartTime;
        TextView tvFirstStir;
        TextView tvSecondStir;
        TextView tvState;
        ContinuousTask decoctionTask;

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.tv_start_time: {
                    decoctionTask.start(0);
                } break;
                case R.id.tv_first_stir: {
                    decoctionTask.start(1);
                } break;
                case R.id.tv_second_stir: {
                    decoctionTask.start(2);
                } break;
            }
            decoctionTask.startCountDown(timer, this, handler);
            setStartTime();
        }

        public void setStartTime() {
            tvStartTime.setText(descriptor.getTaskStartTimeDescription(decoctionTask.getStartTime()));
        }

        @Override
        public void onCountDown(ContinuousTask task, int eventNumber, long minutes, long seconds) {
            if (minutes == 0 && seconds == 0) {
                switch (eventNumber) {
                    case 0:tvFirstStir.setText(descriptor.getEventFinishDescription(task.getEvent(eventNumber).getName()));break;
                    case 1:tvSecondStir.setText(descriptor.getEventFinishDescription(task.getEvent(eventNumber).getName()));break;
                    case 2:tvState.setText(descriptor.getEventFinishDescription(task.getName()));break;
                }
            } else {
                String description = descriptor.getCountDownDescription(task.getEvent(eventNumber).getName(), minutes, seconds);
                switch (eventNumber) {
                    case 0:tvFirstStir.setText(description);break;
                    case 1:tvSecondStir.setText(description);break;
                    case 2:tvState.setText(description);break;
                }
            }
        }
    }

    private static class Descriptor {

        public final String TASK_NOT_START;
        private final String TASK_START_TIME;
        private final String EVENT_NOT_START;
        private final String EVENT_FINISH;
        private final String COUNT_DOWN_DESCRIPTION;

        public Descriptor(Context context) {
            TASK_NOT_START = context.getString(R.string.decoction_not_start);
            TASK_START_TIME = context.getString(R.string.decoction_start_time);
            EVENT_NOT_START = context.getString(R.string.event_not_start);
            EVENT_FINISH = context.getString(R.string.event_finish);
            COUNT_DOWN_DESCRIPTION = context.getString(R.string.count_down_description);
        }

        public String getTaskStartTimeDescription(long startTime) {
            if (startTime == 0)
                return TASK_NOT_START;
            return new SimpleDateFormat(TASK_START_TIME).format(new Date(startTime));
        }

        public String getEventStateDescription(ContinuousTask.Event event) {
            if (event == null)
                return null;
            return getStateDescription(event.getState(), event.getName());
        }

        public String getTaskStateDescription(ContinuousTask task) {
            if (task == null)
                return null;
            return getStateDescription(task.getState(), task.getName());
        }

        private String getStateDescription(ContinuousTask.State state, String name) {
            if (state == null)
                return null;
            switch (state) {
                case NOT_SCHEDULED:
                case NOT_START:return String.format(EVENT_NOT_START, name);
                case FINISHED:return String.format(EVENT_FINISH, name);
            }
            return null;
        }

        public String getEventFinishDescription(String taskName) {
            return String.format(EVENT_FINISH, taskName);
        }

        public String getCountDownDescription(String eventName, long minutes, long seconds) {
            return String.format(COUNT_DOWN_DESCRIPTION, eventName, minutes, seconds);
        }
    }

    private static abstract class BaseEvent extends ContinuousTask.Event {

        public BaseEvent(String name, long duration) {
            super(name, duration);
        }

        @Override
        protected void onPrepare(Context context, Intent intent) {
        }

        @StringRes
        protected abstract int getMessage();

        @RawRes
        protected abstract int getMusic();

        @StringRes
        protected abstract int getDescriptionName();

        @Override
        protected boolean onExecute(Context context, Intent intent) {
            context.startActivity(new Intent(context, PromptActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(PromptActivity.KEY_DECOCTION_PROMPT, context.getString(getMessage()))
                    .putExtra(PromptActivity.KEY_DECOCTION_DESCRIPTION,
                            String.format(context.getString(R.string.on_time_description),
                                    context.getString(getDescriptionName())))
                    .putExtra(PromptActivity.KEY_MUSIC_RAW_ID, getMusic()));
            return false;
        }
    }

    public static class WaitForFirstStirEvent extends BaseEvent {

        public WaitForFirstStirEvent(String name, long duration) {
            super(name, duration);
        }

        @Override
        protected int getMessage() {
            return R.string.on_time_stir;
        }

        @Override
        protected int getMusic() {
            return R.raw.music_fisrt_stir;
        }

        @Override
        protected int getDescriptionName() {
            return R.string.event_first_stir;
        }
    }

    public static class WaitForSecondStirEvent extends BaseEvent {

        public WaitForSecondStirEvent(String name, long duration) {
            super(name, duration);
        }

        @Override
        protected int getMessage() {
            return R.string.on_time_stir;
        }

        @Override
        protected int getMusic() {
            return R.raw.music_second_stir;
        }

        @Override
        protected int getDescriptionName() {
            return R.string.event_second_stir;
        }
    }

    public static class WaitForFirstDecoctionFinishEvent extends BaseEvent {

        public WaitForFirstDecoctionFinishEvent(String name, long duration) {
            super(name, duration);
        }

        @Override
        protected int getMessage() {
            return R.string.on_time_decoction_finish;
        }

        @Override
        protected int getMusic() {
            return R.raw.music_decoction;
        }

        @Override
        protected int getDescriptionName() {
            return R.string.task_first_decoction;
        }
    }

    public static class WaitForSecondDecoctionFinishEvent extends BaseEvent {

        public WaitForSecondDecoctionFinishEvent(String name, long duration) {
            super(name, duration);
        }

        @Override
        protected int getMessage() {
            return R.string.on_time_decoction_finish;
        }

        @Override
        protected int getMusic() {
            return R.raw.music_decoction;
        }

        @Override
        protected int getDescriptionName() {
            return R.string.task_second_decoction;
        }

        @Override
        protected boolean onExecute(Context context, Intent intent) {
            super.onExecute(context, intent);
            ContinuousTask.release(context, context.getString(R.string.task_first_decoction));
            return true;
        }
    }
}
