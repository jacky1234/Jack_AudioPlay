package com.jack.audio.play;

import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.session.MediaSessionManager;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.jack.audio.play.module.Audio;
import com.jack.audio.play.module.Result;
import com.jack.audio.play.module.Status;
import com.jack.audio.play.uitls.StorageUtil;

import java.io.IOException;
import java.util.ArrayList;

import static com.jack.audio.play.JConstant.ACTION_AUDIOLIST_UPDATE;
import static com.jack.audio.play.JConstant.ACTION_AUDIO_SEEK;
import static com.jack.audio.play.JConstant.ACTION_NEXT;
import static com.jack.audio.play.JConstant.ACTION_PAUSE;
import static com.jack.audio.play.JConstant.ACTION_PLAY;
import static com.jack.audio.play.JConstant.ACTION_PLAY_NEW_AUDIO;
import static com.jack.audio.play.JConstant.ACTION_PREVIOUS;
import static com.jack.audio.play.JConstant.ACTION_STOP;

/**
 * 2017/4/26.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:Service for playing.
 */

public class MediaPlayerService extends Service implements MediaPlayer.OnCompletionListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, MediaPlayer.OnSeekCompleteListener,
        MediaPlayer.OnInfoListener, MediaPlayer.OnBufferingUpdateListener,
        AudioManager.OnAudioFocusChangeListener {
    private final String TAG = "MediaPlayerService";

    //play state action
    private MediaPlayer mediaPlayer;

    //MediaSession
    private MediaSessionManager mediaSessionManager;
    private MediaSessionCompat mediaSession;
    private MediaControllerCompat.TransportControls transportControls;

    //AudioPlayer notification ID
    private static final int NOTIFICATION_ID = 101;

    //Used to pause/resume MediaPlayer
    private int resumePosition;

    //AudioFocus
    private AudioManager audioManager;

    // Binder given to clients
    private final IBinder iBinder = new LocalBinder();

    //List of available Audio files
    private ArrayList<Audio> audioList;
    private int audioIndex = -1;
    private Audio activeAudio; //an object on the currently playing audio


    //Handle incoming phone calls
    private boolean ongoingCall = false;
    private PhoneStateListener phoneStateListener;
    private TelephonyManager telephonyManager;

    private int TIMER = 200;

    private MediaStateChangedListener mediaStateChangedListener;
    private MediaBufferingListener mediaBufferingListener;
    private Result result;

    /**
     * <a href="http://stackoverflow.com/questions/16544891/stop-or-release-mediaplayer-while-it-is-still-preparing/16545152#16545152"></a>
     * Stop or release MediaPlayer while it is still preparing
     * <p>
     * it is on preparing state,while netWork disconnect.and later the net work fine,it will callback
     *
     * @see android.media.MediaPlayer.OnPreparedListener#onPrepared(MediaPlayer)
     */
    private boolean cancelOnPrepared = false;

    public void setCancelOnPrepared(boolean cancelOnPrepared) {
        this.cancelOnPrepared = cancelOnPrepared;
    }

    /**
     * msg
     */
    private final static int MSG_TO_UPDATE_PROGRESS = 1 << 1;
    private final static int MSG_TO_PLAY = 1 << 2;
    private final static int MSG_TO_PAUSE = 1 << 3;
    private final static int MSG_TO_STOP = 1 << 4;
    private MediaHandler handler;

    final class MediaHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TO_UPDATE_PROGRESS:
                    //MSG_TO_PAUSE or MSG_TO_STOP should not update status,caution the other player gainer focus the mediaplayer is null
                    if (hasMessages(MSG_TO_UPDATE_PROGRESS) && mediaPlayer != null) {
                        if (mediaStateChangedListener != null) {
                            result.setCurrent(mediaPlayer.getCurrentPosition());
                            mediaStateChangedListener.onStateChanged(Status.PLAY, result);
                        }

                        sendEmptyMessageDelayed(MSG_TO_UPDATE_PROGRESS, TIMER);
                    }

                    break;
                case MSG_TO_PLAY:
                    obtainMessage(MSG_TO_UPDATE_PROGRESS).sendToTarget();
                    break;
                case MSG_TO_PAUSE:
                    removeMessages(MSG_TO_UPDATE_PROGRESS);
                    break;
                case MSG_TO_STOP:
                    removeMessages(MSG_TO_UPDATE_PROGRESS);
                    break;
            }
        }
    }

    public void setMediaStateChangedListener(MediaStateChangedListener mediaStateChangedListener) {
        this.mediaStateChangedListener = mediaStateChangedListener;
    }

    public void setMediaBufferingListener(MediaBufferingListener mediaBufferingListener) {
        this.mediaBufferingListener = mediaBufferingListener;
    }

    /**
     * Service lifecycle methods
     */
    @Override
    public IBinder onBind(Intent intent) {
        return iBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Perform one-time setup procedures

        // Manage incoming phone calls during playback.
        // Pause MediaPlayer on incoming call,
        // Resume on hangup.
        callStateListener();
        //ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs -- BroadcastReceiver
        registerBecomingNoisyReceiver();

        handler = new MediaHandler();
        result = new Result();
    }

    //The system calls this method when an activity, requests the service be started
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        /**
         * {@link JConstant.ACTION_UPDATE_PLAY_PARAMS} Load data from sharedPreference
         */
        if (JConstant.ACTION_UPDATE_PLAY_PARAMS.equals(action)) {
            audioList = StorageUtil.getInstance(this).loadAudio();
            audioIndex = StorageUtil.getInstance(this).loadAudioIndex();
            TIMER = StorageUtil.getInstance(this).getAudioIntervalOnPlay();

            if (!(audioIndex != -1 && audioList != null && audioIndex < audioList.size())) {
                Log.e(TAG, "wrong param->audioIndex:" + audioIndex + ",audioList:" + audioList);
                stopSelf();
            }
        }

        //Request audio focus
        if (requestAudioFocus() == false) {
            //Could not gain focus
            Log.w(TAG, "cant not gain focus the service will be stopped");
            stopSelf();
        }

        if (mediaSessionManager == null) {
            try {
                initMediaSession();
            } catch (RemoteException e) {
                e.printStackTrace();
                stopSelf();
            }
        }

        handleIncomingActions(intent);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mediaSession.release();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            stopMedia();
            mediaPlayer.release();
        }
        removeAudioFocus();
        //Disable the PhoneStateListener
        if (phoneStateListener != null) {
            telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
        }

        removeNotification();

        //unregister BroadcastReceivers
        unregisterReceiver(becomingNoisyReceiver);

        //clear cached playlist
        StorageUtil.getInstance(this).clearCachedAudio();

        handler = null;
    }

    /**
     * Service Binder
     */
    public class LocalBinder extends Binder {
        public MediaPlayerService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MediaPlayerService.this;
        }
    }


    /**
     * MediaPlayer callback methods
     */
    @Override
    public void onBufferingUpdate(MediaPlayer mp, int percent) {
        //Invoked indicating buffering status of
        //a media resource being streamed over the network.
        Log.i(TAG, "onBufferingUpdate,percent:" + percent);

        if (mediaBufferingListener != null) {
            mediaBufferingListener.onBufferingUpdate(percent);
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        Log.i(TAG, "onCompletion");

        //Invoked when playback of a media source has completed.
        stopMedia();

        if (mediaStateChangedListener != null) {
            result.setCurrent(mediaPlayer.getDuration());
            mediaStateChangedListener.onStateChanged(Status.COMPLETE, result);
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        //Invoked when there has been an error during an asynchronous operation
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                Log.i("MediaPlayer Error", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                Log.i("MediaPlayer Error", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                Log.i("MediaPlayer Error", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        Log.i(TAG, "onError,what:" + what + ",extra:" + extra);
        return true;
    }

    @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        //Invoked to communicate some info
        Log.i(TAG, "onInfo,what:" + what + ",extra:" + extra);
        return false;
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        Log.i(TAG, "onPrepared");

        //note to do something on Prepared
        if (mediaStateChangedListener != null) {
            result.setDuration(mediaPlayer.getDuration());
            mediaStateChangedListener.onStateChanged(Status.PREPARED, result);
        }

        if (cancelOnPrepared) {
            cancelOnPrepared = false;
            return;
        }

        //Invoked when the media source is ready for playback.
        playMedia();
        handler.obtainMessage(MSG_TO_PLAY).sendToTarget();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        //Invoked indicating the completion of a seek operation.
        Log.i(TAG, "onSeekComplete");
    }

    @Override
    public void onAudioFocusChange(int focusState) {
        //Invoked when the audio focus of the system is updated.
        switch (focusState) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // resume playback
                if (mediaPlayer == null) initMediaPlayer();
                else if (!mediaPlayer.isPlaying()) mediaPlayer.start();
                mediaPlayer.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                // Lost focus for an unbounded amount of time: stop playback and release media player
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();

                    if (mediaStateChangedListener != null) {
                        result.setCurrent(mediaPlayer.getCurrentPosition());
                        mediaStateChangedListener.onStateChanged(Status.STOP, result);
                    }

                    mediaPlayer.release();
                    mediaPlayer = null;
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // Lost focus for a short time, but we have to stop
                // playback. We don't release the media player because playback
                // is likely to resume
                if (mediaPlayer.isPlaying()) mediaPlayer.pause();
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // Lost focus for a short time, but it's ok to keep playing
                // at an attenuated level
                if (mediaPlayer.isPlaying()) mediaPlayer.setVolume(0.1f, 0.1f);
                break;
        }
    }


    /**
     * AudioFocus
     */
    private boolean requestAudioFocus() {
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            //Focus gained
            return true;
        }
        //Could not gain focus
        return false;
    }

    private boolean removeAudioFocus() {
        return AudioManager.AUDIOFOCUS_REQUEST_GRANTED ==
                audioManager.abandonAudioFocus(this);
    }


    /**
     * MediaPlayer actions
     */
    private void initMediaPlayer() {
        cancelOnPrepared = false;

        if (mediaPlayer == null)
            mediaPlayer = new MediaPlayer();//new MediaPlayer instance

        activeAudio = audioList.get(audioIndex);

        //Set up MediaPlayer event listeners
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnErrorListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnSeekCompleteListener(this);
        mediaPlayer.setOnInfoListener(this);
        //Reset so that the MediaPlayer is not pointing to another data source
        mediaPlayer.reset();

        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            //reset data
            result.reset();
            final String data = activeAudio.getData();
            final String token = activeAudio.getToken();

            result.setActivePath(data);
            result.setToken(token);
            // Set the data source to the mediaFile location
            mediaPlayer.setDataSource(data);
        } catch (IOException e) {
            e.printStackTrace();
            stopSelf();
        }
        mediaPlayer.prepareAsync();

        if (mediaStateChangedListener != null) {
            result.setPosition(audioIndex);
            mediaStateChangedListener.onStateChanged(Status.PREPARING, result);
        }
    }

    private void playMedia() {
        handler.obtainMessage(MSG_TO_PLAY).sendToTarget();

        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();

            if (mediaStateChangedListener != null) {
                result.setDuration(mediaPlayer.getDuration());
                mediaStateChangedListener.onStateChanged(Status.PLAY, result);
            }
        }
    }

    private void stopMedia() {
        handler.obtainMessage(MSG_TO_STOP).sendToTarget();

        if (mediaPlayer == null) return;
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.stop();

            if (mediaStateChangedListener != null) {
                result.setCurrent(mediaPlayer.getCurrentPosition());
                mediaStateChangedListener.onStateChanged(Status.STOP, result);
            }
        }
    }

    private void pauseMedia() {
        handler.obtainMessage(MSG_TO_PAUSE).sendToTarget();

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            resumePosition = mediaPlayer.getCurrentPosition();

            if (mediaStateChangedListener != null) {
                result.setCurrent(resumePosition);
                mediaStateChangedListener.onStateChanged(Status.PAUSE, result);
            }
        }
    }

    private void resumeMedia() {
        handler.obtainMessage(MSG_TO_PLAY).sendToTarget();

        mediaPlayer.seekTo(resumePosition);
        if (!mediaPlayer.isPlaying()) {
            mediaPlayer.start();
        }

        if (mediaStateChangedListener != null) {
            result.setCurrent(resumePosition);
            mediaStateChangedListener.onStateChanged(Status.PLAY, result);
        }
    }

    private void skipToNext() {
        if (audioIndex == audioList.size() - 1) {
            //if last in playlist
            audioIndex = 0;
        } else {
            //get next in playlist
            audioIndex++;
        }

        //Update stored index
        StorageUtil.getInstance(this).storeAudioIndex(audioIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }


    private void skipToPrevious() {
        if (audioIndex == 0) {
            //if first in playlist
            //set index to the last of audioList
            audioIndex = audioList.size() - 1;
        } else {
            //get previous in playlist
            audioIndex--;
        }

        //Update stored index
        StorageUtil.getInstance(this).storeAudioIndex(audioIndex);

        stopMedia();
        //reset mediaPlayer
        mediaPlayer.reset();
        initMediaPlayer();
    }

    /**
     * ACTION_AUDIO_BECOMING_NOISY -- change in audio outputs
     */
    private BroadcastReceiver becomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //pause audio on ACTION_AUDIO_BECOMING_NOISY
            pauseMedia();
//            buildNotification(PlaybackStatus.PAUSED);
        }
    };


    private void registerBecomingNoisyReceiver() {
        //register after getting audio focus
        IntentFilter intentFilter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(becomingNoisyReceiver, intentFilter);
    }

    /**
     * Handle PhoneState changes
     */
    private void callStateListener() {
        // Get the telephony manager
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        //Starting listening for PhoneState changes
        phoneStateListener = new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                switch (state) {
                    //if at least one call exists or the phone is ringing
                    //pause the MediaPlayer
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                    case TelephonyManager.CALL_STATE_RINGING:
                        if (mediaPlayer != null) {
                            pauseMedia();
                            ongoingCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        // Phone idle. Start playing.
                        if (mediaPlayer != null) {
                            if (ongoingCall) {
                                ongoingCall = false;
                                resumeMedia();
                            }
                        }
                        break;
                }
            }
        };
        // Register the listener with the telephony manager
        // Listen for changes to the device call state.
        telephonyManager.listen(phoneStateListener,
                PhoneStateListener.LISTEN_CALL_STATE);
    }

    /**
     * MediaSession and Notification actions
     */
    private void initMediaSession() throws RemoteException {
        if (mediaSessionManager != null) return; //mediaSessionManager exists

        mediaSessionManager = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
        // Create a new MediaSession
        mediaSession = new MediaSessionCompat(getApplicationContext(), "AudioPlayer");
        //Get MediaSessions transport controls
        transportControls = mediaSession.getController().getTransportControls();
        //set MediaSession -> ready to receive media commands
        mediaSession.setActive(true);
        //indicate that the MediaSession handles transport control commands
        // through its MediaSessionCompat.Callback.
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);

        //Set mediaSession's MetaData
//        updateMetaData();

        // Attach Callback to receive MediaSession updates
        mediaSession.setCallback(new MediaSessionCompat.Callback() {

            // Implement callbacks
            @Override
            public void onPlay() {
                super.onPlay();
                Log.d(TAG, "mediaSession->onPlay,path:" + activeAudio.getData());
                resumeMedia();
//                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onPause() {
                super.onPause();
                Log.d(TAG, "mediaSession->mediaSession->onPause,path:" + activeAudio.getData());
                pauseMedia();
//                buildNotification(PlaybackStatus.PAUSED);
            }

            @Override
            public void onSkipToNext() {
                super.onSkipToNext();
                skipToNext();
//                updateMetaData();
//                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onSkipToPrevious() {
                super.onSkipToPrevious();

                skipToPrevious();
//                updateMetaData();
//                buildNotification(PlaybackStatus.PLAYING);
            }

            @Override
            public void onStop() {
                super.onStop();
                Log.d(TAG, "mediaSession->onStop,path:" + activeAudio.getData());
                stopMedia();
//                removeNotification();
                //Stop the service
//                stopSelf();
            }

            @Override
            public void onSeekTo(long position) {
                super.onSeekTo(position);
                Log.d(TAG, "mediaSession->onSeekTo,path:" + activeAudio.getData() + ",seekPosition:" + position);
                resumePosition = (int) position;

                resumeMedia();
            }
        });
    }


    private void removeNotification() {
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.cancel(NOTIFICATION_ID);
    }

    private void handleIncomingActions(Intent intent) {

        if (intent == null || intent.getAction() == null) return;

        String actionString = intent.getAction();
        if (actionString.equalsIgnoreCase(ACTION_PLAY)) {
            transportControls.play();
        } else if (actionString.equalsIgnoreCase(ACTION_PAUSE)) {
            transportControls.pause();
        } else if (actionString.equalsIgnoreCase(ACTION_NEXT)) {
            transportControls.skipToNext();
        } else if (actionString.equalsIgnoreCase(ACTION_PREVIOUS)) {
            transportControls.skipToPrevious();
        } else if (actionString.equalsIgnoreCase(ACTION_STOP)) {
            transportControls.stop();
        } else if (actionString.equals(ACTION_AUDIO_SEEK)) {
            transportControls.seekTo(StorageUtil.getInstance(this).getSeekPosition());
        } else if (actionString.equals(ACTION_PLAY_NEW_AUDIO)) {
            stopMedia();
            initMediaPlayer();
        } else if (actionString.equals(ACTION_AUDIOLIST_UPDATE)) {
            audioList = StorageUtil.getInstance(this).loadAudio();
        }
    }
}
