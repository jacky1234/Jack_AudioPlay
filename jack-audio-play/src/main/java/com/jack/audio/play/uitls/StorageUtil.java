package com.jack.audio.play.uitls;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.jack.audio.play.module.Audio;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * 2017/4/27.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:
 */

public class StorageUtil {
    private final String STORAGE = " com.valdioveliu.valdio.audioplayer.STORAGE";
    private SharedPreferences preferences;
    private Context context;
    private static StorageUtil instance;

    private final String KEY_AUDIOARRAYLIST = "KEY_AUDIOARRAYLIST";
    private final String KEY_AUDIOINDEX = "KEY_AUDIOINDEX";
    private final String KEY_SEEKPOSITION = "KEY_SEEKPOSITION";
    private final String KEY_AUTOINTERVALONPLAY = "KEY_AUTOINTERVALONPLAY";

    private StorageUtil(Context context) {
        this.context = context;
        this.preferences = context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE);
    }

    public static StorageUtil getInstance(Context context) {
        if (instance == null) {
            instance = new StorageUtil(context.getApplicationContext());
        }

        return instance;
    }

    //
    public void storeAudio(List<Audio> arrayList) {
        SharedPreferences.Editor editor = preferences.edit();
        Gson gson = new Gson();
        String json = gson.toJson(arrayList);
        editor.putString(KEY_AUDIOARRAYLIST, json).commit();
    }

    public ArrayList<Audio> loadAudio() {
        Gson gson = new Gson();
        String json = preferences.getString(KEY_AUDIOARRAYLIST, null);
        Type type = new TypeToken<ArrayList<Audio>>() {
        }.getType();
        return gson.fromJson(json, type);
    }

    //
    public void storeAudioIndex(int index) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_AUDIOINDEX, index).commit();
    }

    public int loadAudioIndex() {
        return preferences.getInt(KEY_AUDIOINDEX, -1);//return -1 if no data found
    }


    public void storeSeekPosition(int seekPosition) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_SEEKPOSITION, seekPosition).commit();
    }

    public int getSeekPosition() {
        return preferences.getInt(KEY_SEEKPOSITION, 0);
    }

    public void storeAudioIntervalOnPlay(int autoIntervalOnPlay) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt(KEY_AUTOINTERVALONPLAY, autoIntervalOnPlay).commit();
    }

    public int getAudioIntervalOnPlay() {
        return preferences.getInt(KEY_AUTOINTERVALONPLAY, 0);
    }

    //clear audio cache
    public void clearCachedAudio() {
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        editor.commit();
    }
}
