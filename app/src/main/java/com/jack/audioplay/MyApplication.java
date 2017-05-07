package com.jack.audioplay;

import android.app.Application;

import com.jack.audio.play.Audios;

/**
 * 2017/5/7.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:
 */

public class MyApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        Audios.getInstance(this).init();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Audios.getInstance(this).release();
    }
}
