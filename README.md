# Jack_AudioPlay 
Jack_AudioPlay Provides a simple way to play audio in service.

## Usage 
### 1.Init in Application
```java
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
```

### 2.Set parms And play
```java
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
```
Cautions:you should destroy the audios and remove listeners of audios in the method of `onDestory` in case of Leaking memory.
```java
@Override
protected void onDestroy() {
    super.onDestroy();
    //stop
    Audios.getInstance(this).stop().destroy();
    Audios.getInstance(this).removeAllListener();
}
```

