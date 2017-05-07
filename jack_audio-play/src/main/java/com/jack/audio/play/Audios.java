package com.jack.audio.play;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.jack.audio.play.module.Audio;
import com.jack.audio.play.uitls.StorageUtil;

import java.util.ArrayList;

/**
 * 2017/4/24.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:AudioPlay 控制类
 * <p>
 * <p>
 * <h1>Usage</h1>
 * <h2>The 1 steps</h2>
 * @see #init() ,init the {@link Audios} in {@link Application#onCreate()}
 * <p>
 * <h2>The 2 steps</h2>
 * set play parmas {@link #setParams(AudioInfoConfig)} and play {@link #playNewAudio()}.You can set Listener for the playing state {@link MediaStateChangedListener}
 * <p>
 * <h2>The 3 steps<h2/>
 * Release the {@link Audios} by method {{@link #release()}}
 */

public class Audios {
    private final String TAG = "Audios";
    private Context context;
    private static Audios instance;
    private boolean serviceBound;
    private MediaPlayerService mediaPlayerService;

    private Audios(Context context) {
        this.context = context.getApplicationContext();
    }

    public static Audios getInstance(Context context) {
        if (instance == null) {
            instance = new Audios(context);
        }

        return instance;
    }

    //Binding this Client to the AudioPlayer Service
    private ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MediaPlayerService.LocalBinder binder = (MediaPlayerService.LocalBinder) service;
            mediaPlayerService = binder.getService();
            serviceBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            serviceBound = false;
            //rebind service
            Intent playerIntent = new Intent(context, MediaPlayerService.class);
            context.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }
    };


    /**
     * init on {@link Application#onCreate()}
     */
    public void init() {
        if (serviceBound) {
            Log.w(TAG, "you have init Audios! return");
            return;
        }

        //1.bind service
        if (!serviceBound) {
            Intent playerIntent = new Intent(context, MediaPlayerService.class);
            context.bindService(playerIntent, serviceConnection, Context.BIND_AUTO_CREATE);
        }

        //2.clear cache
        StorageUtil.getInstance(context).clearCachedAudio();
    }

    /**
     * release on
     */
    public void release() {
        if (serviceBound) {
            context.unbindService(serviceConnection);
            //service is active
            mediaPlayerService.stopSelf();
        }
    }

    /**
     * set play param
     *
     * @param config
     * @return
     */
    public Audios setParams(AudioInfoConfig config) {
        //store
        StorageUtil.getInstance(context).storeAudioIndex(config.getAudioIndex());
        StorageUtil.getInstance(context).storeAudio(config.getAudioList());
        StorageUtil.getInstance(context).storeAudioIntervalOnPlay(config.getAudioIntervalOnPlay());

        //create service
        Intent intent = new Intent(context, MediaPlayerService.class);
        intent.setAction(JConstant.ACTION_UPDATE_PLAY_PARAMS);
        context.startService(intent);

        return this;
    }

    /**
     * 更新本地路径替换http路径,it will only update AudioList.
     *
     * @param orginal
     * @param audio   本地路径的Audio
     * @return
     */
    public Audios update(String orginal, Audio audio) {
        final ArrayList<Audio> audios = StorageUtil.getInstance(context).loadAudio();
        if (audios != null && !audios.isEmpty()) {
            int index = -1;
            for (int i = 0; i < audios.size(); i++) {
                final Audio a = audios.get(i);
                if (a.getData().equals(orginal)) {
                    index = i;
                    break;
                }
            }

            if (index != -1) {
                //update
                audios.get(index).setData(audio.getData());
                StorageUtil.getInstance(context).storeAudio(audios);
                handleEvents(JConstant.ACTION_AUDIOLIST_UPDATE);
            }
        }

        return this;
    }

    public Audios setMediaStateChangedListener(MediaStateChangedListener mediaStateChangedListener) {
        if (isServiceBound()) {
            mediaPlayerService.setMediaStateChangedListener(mediaStateChangedListener);
        }

        return this;
    }

    public Audios setMediaBufferListener(MediaBufferingListener mediaBufferingListener) {
        if (isServiceBound()) {
            mediaPlayerService.setMediaBufferingListener(mediaBufferingListener);
        }

        return this;
    }

    public void removeAllListener() {
        if (isServiceBound()) {
            mediaPlayerService.setMediaStateChangedListener(null);
            mediaPlayerService.setMediaBufferingListener(null);
        }
    }

    private boolean isServiceBound() {
        if (!serviceBound) {
            Log.w(TAG, "has not bind to mediaPlayerService！");
        }

        return serviceBound;
    }

    public void playNewAudio() {
        handleEvents(JConstant.ACTION_PLAY_NEW_AUDIO);
    }

    public void pause() {
        handleEvents(JConstant.ACTION_PAUSE);
    }

    public Audios stop() {
        handleEvents(JConstant.ACTION_STOP);
        return this;
    }

    /**
     * @see MediaPlayerService#cancelOnPrepared
     */
    public void destroy() {
        if (isServiceBound()) {
            mediaPlayerService.setCancelOnPrepared(true);
        }

        StorageUtil.getInstance(context).clearCachedAudio();
    }

    /**
     * it will resume playing after pause
     */
    public void play() {
        handleEvents(JConstant.ACTION_PLAY);
    }

    public void seek(int position) {
        StorageUtil.getInstance(context).storeSeekPosition(position);
        handleEvents(JConstant.ACTION_AUDIO_SEEK);
    }

    public void playPrevious() {
        handleEvents(JConstant.ACTION_PREVIOUS);
    }

    public void playNext() {
        handleEvents(JConstant.ACTION_NEXT);
    }

    private void handleEvents(String action) {
        if (TextUtils.isEmpty(action)) {
            throw new IllegalArgumentException("the action you set is empty");
        }

        Intent intent = new Intent(context, MediaPlayerService.class);
        intent.setAction(action);
        context.startService(intent);
    }

}
