package tool.cjq.com.medicinedecoctionprompter;

import android.content.Intent;
import android.media.MediaPlayer;
import android.support.annotation.RawRes;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class PromptActivity
        extends AppCompatActivity
        implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener {

    public static final String KEY_DECOCTION_PROMPT = "prompt";
    public static final String KEY_DECOCTION_DESCRIPTION = "desc";
    public static final String KEY_MUSIC_RAW_ID = "music";
    private static final int SLIDE_SWITCH_THRESHOLD = 20;
    private static final int CLICK_THRESHOLD = 5;
    private float x;
    private float y;
    private MediaPlayer mediaPlayer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //屏幕常亮
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.activity_prompt);

        Intent intent = getIntent();
        TextView tvPrompt = (TextView) findViewById(R.id.tv_decoction_prompt);
        tvPrompt.setText(intent.getStringExtra(KEY_DECOCTION_PROMPT));
        TextView tvDescription = (TextView) findViewById(R.id.tv_decoction_description);
        tvDescription.setText(intent.getStringExtra(KEY_DECOCTION_DESCRIPTION));

        startPromptMusic(intent.getIntExtra(KEY_MUSIC_RAW_ID, -1));
    }

    @Override
    protected void onDestroy() {
        stopPromptMusic();
        super.onDestroy();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                x = event.getX();
                y = event.getY();
            } break;
            case MotionEvent.ACTION_UP: {
                x = event.getX() - x;
                y = Math.abs(event.getY() - y);
                if (y < CLICK_THRESHOLD && (x > -SLIDE_SWITCH_THRESHOLD || x < SLIDE_SWITCH_THRESHOLD)) {
                    return true;
                }
                if (x > 0) {
                    if (x > y) {
                        finish();
                    }
                } else {
                    if (-x > y) {
                        finish();
                    }
                }
            } break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        finish();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        finish();
        return false;
    }

    private void startPromptMusic(@RawRes int musicRawId) {
        if (musicRawId == -1)
            return;
        stopPromptMusic();
        try {
            mediaPlayer = MediaPlayer.create(this, musicRawId);
            if (mediaPlayer == null)
                return;
            mediaPlayer.setOnCompletionListener(this);
            mediaPlayer.setOnErrorListener(this);
            mediaPlayer.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopPromptMusic() {
        try {
            if (mediaPlayer != null) {
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                }
                mediaPlayer.release();
                mediaPlayer = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
