package tool.cjq.com.medicinedecoctionprompter;

import android.content.Context;
import android.content.Intent;
import android.support.annotation.RawRes;
import android.support.annotation.StringRes;

/**
 * Created by CJQ on 2018/3/21.
 */

public class PromptEvent extends ContinuousTask.Event {

    private final @StringRes int messageRes;
    private final @RawRes int musicRes;
    private final @StringRes int descriptionNameRes;
    private boolean needRelease;

    public PromptEvent(String name, long duration,
                       @StringRes int messageRes,
                       @RawRes int musicRes,
                       @StringRes int descriptionNameRes) {
        super(name, duration);
        this.messageRes = messageRes;
        this.musicRes = musicRes;
        this.descriptionNameRes = descriptionNameRes;
    }

    @Override
    protected void onPrepare(Context context, Intent intent) {
    }

    @Override
    protected boolean onExecute(Context context, Intent intent) {
        context.startActivity(new Intent(context, PromptActivity.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(PromptActivity.KEY_DECOCTION_PROMPT, context.getString(messageRes))
                .putExtra(PromptActivity.KEY_DECOCTION_DESCRIPTION,
                        String.format(context.getString(R.string.on_time_description),
                                context.getString(descriptionNameRes)))
                .putExtra(PromptActivity.KEY_MUSIC_RAW_ID, musicRes));
        return needRelease;
    }

    public PromptEvent setNeedRelease(boolean needRelease) {
        this.needRelease = needRelease;
        return this;
    }
}
