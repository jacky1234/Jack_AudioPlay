package com.jack.audioplay;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.jack.audio.play.AudioInfoConfig;
import com.jack.audio.play.Audios;
import com.jack.audio.play.MediaBufferingListener;
import com.jack.audio.play.MediaStateChangedListener;
import com.jack.audio.play.module.Audio;
import com.jack.audio.play.module.Result;
import com.jack.audio.play.module.Status;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.content.ContentValues.TAG;

/**
 * see full example link
 * <a href="https://github.com/jacky1234/Jack_BLOG_All">AudioPlayActivity</a>
 */
public class MainActivity extends Activity implements MediaStateChangedListener, MediaBufferingListener, Runnable {

    @Bind(R.id.tv_title)
    TextView tvTitle;

    @Bind(R.id.tv_pre)
    TextView tvPre;
    @Bind(R.id.tv_play_or_pause)
    TextView tvPlayOrPause;
    @Bind(R.id.tv_next)
    TextView tvNext;
    @Bind(R.id.seekbar)
    SeekBar seekBar;
    @Bind(R.id.start_time)
    TextView startTime;
    @Bind(R.id.end_time)
    TextView endTime;
    @Bind(R.id.progressBar)
    ProgressBar progressBar;

    private static List<String> playLists = new ArrayList<>();
    private Context context;

    Status status;
    private boolean isSeekBarOnTouchTracking = false;

    static {
        playLists.add("http://filebag-1252817547.cosgz.myqcloud.com/201704/2975e488-8e72-4089-9928-7144f86eb8ec.mp3");
        playLists.add("http://filebag-1252817547.cosgz.myqcloud.com/201703/5b2f2b3e-08e5-4655-981d-748780feb2cd.mp3");
        playLists.add("http://filebag-1252817547.cosgz.myqcloud.com/201705/34780a43-0230-481d-9c9b-ddc6432b572f.mp3");
        playLists.add("http://filebag-1252817547.cosgz.myqcloud.com/201705/d2cefd80-d9f3-4965-aeb0-e90be410a17e.mp3");
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        context = this;
        seekBar.setOnSeekBarChangeListener(changeListener);

        //post this in order Service bind success.
        new Handler().postDelayed(this, 200);
    }

    private List<String> taskIds = new ArrayList<>();

    @Override
    public void run() {
        //1.build param
        List<Audio> lists = new ArrayList<>();
        for (String s : playLists) {
            lists.add(new Audio(s));
        }

        //2
        final AudioInfoConfig config = new AudioInfoConfig.Builder()
                .setAudioList(lists)
                .build();
        Audios.getInstance(this).setParams(config)
                .setMediaStateChangedListener(this)
                .setMediaBufferListener(this).playNewAudio();
    }

    @Override
    public void onStateChanged(Status status, Result result) {
        Log.d(TAG, "Status:" + status.toString() + "    result :" + result.toString());
        this.status = status;
        final String activePath = result.getActivePath();
        if (status == Status.PREPARING) {
            startTime.setText("00:00");
            endTime.setVisibility(View.GONE);
            progressBar.setVisibility(View.VISIBLE);


            if (activePath.lastIndexOf("/") != -1) {
                String text = activePath.substring(activePath.lastIndexOf("/") + 1);
                tvTitle.setText("position:" + result.getPosition() + "\n" + text);
            }
        }

        //todo something on Prepared,es download the uri resource
        if (status == Status.PREPARED) {
            if (activePath.startsWith("http")) {

            }
        }

        if (status == Status.PLAY) {
            //update ui
            progressBar.setVisibility(View.INVISIBLE);
            Log.d(TAG, "onplay,current:" + result.getCurrent() + ";duration:" + result.getDuration());
            endTime.setVisibility(View.VISIBLE);
            endTime.setText(formatAudioPlayTime(result.getDuration()));
            if (!isSeekBarOnTouchTracking) {
                Log.i(TAG, "progress:" + result.toString());
                seekBar.setProgress(result.getCurrent());
                startTime.setText(formatAudioPlayTime(result.getCurrent()));
            }
            seekBar.setMax(result.getDuration());
        }

        if (status == Status.COMPLETE) {
            seekBar.setProgress(0);
            startTime.setText("00:00");
        }
    }

    private String formatAudioPlayTime(long milliseconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("mm:ss", Locale.CHINA);
        try {
            return sdf.format(new Date(milliseconds));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }


    @Override
    public void onBufferingUpdate(int percent) {
        Log.i(TAG, "percent:" + percent);
    }

    SeekBar.OnSeekBarChangeListener changeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            Log.i(TAG, "onProgressChanged->progress:" + progress + ",fromUser:" + Boolean.toString(fromUser));

            if (fromUser) {
                startTime.setText(formatAudioPlayTime(progress));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            isSeekBarOnTouchTracking = true;
            Log.i(TAG, "onStartTrackingTouch");
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            /**
             * @see Audios.getInstance(context).seek(seekBar.getProgress());
             * It is a consuming event....
             */
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    isSeekBarOnTouchTracking = false;
                }
            }, 200);
            Log.i(TAG, "onStopTrackingTouch");

            //only when it is in play state,it will seek position
            if (status == Status.PLAY || status == Status.PAUSE) {
                Audios.getInstance(context).seek(seekBar.getProgress());
            }
        }
    };

    @OnClick({R.id.tv_pre, R.id.tv_play_or_pause, R.id.tv_next})
    public void onViewClicked(View view) {
        switch (view.getId()) {
            case R.id.tv_pre:
                Audios.getInstance(this).playPrevious();
                break;
            case R.id.tv_play_or_pause:
                if (status == Status.PLAY) {
                    Audios.getInstance(this).pause();
                } else if (status == Status.PAUSE) {
                    Audios.getInstance(this).play();
                } else if (status == Status.COMPLETE) {
                    Audios.getInstance(this).playNewAudio();
                }
                break;
            case R.id.tv_next:
                Audios.getInstance(this).playNext();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //stop
        Audios.getInstance(this).stop().destroy();
        Audios.getInstance(this).removeAllListener();
    }
}
