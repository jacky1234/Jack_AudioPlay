package com.jack.audio.play;

import com.jack.audio.play.module.Audio;
import java.util.ArrayList;
import java.util.List;

/**
 * 2017/4/17.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:设置播放参数 1.sigle Audio play demo
 * <p>
 * <p>
 * 2.Audio list play demo
 */

public final class AudioInfoConfig {
    private List<Audio> audioList;
    private int audioIndex;
    private int audioIntervalOnPlay;

    public AudioInfoConfig(Builder builder) {
        audioList = builder.audioList;
        audioIndex = builder.audioIndex;
        audioIntervalOnPlay = builder.audioIntervalOnPlay;
    }


    public List<Audio> getAudioList() {
        return audioList;
    }

    public int getAudioIndex() {
        return audioIndex;
    }

    public int getAudioIntervalOnPlay() {
        return audioIntervalOnPlay;
    }

    public static class Builder {
        private List<Audio> audioList;
        private Integer audioIndex;
        private Integer audioIntervalOnPlay;
        //播放的时候更新间隔 200 millsec
        private final int DELFAULT_INTERVAL_ON_PLAY = 200;

        public Builder setAudioList(List<Audio> audioList) {
            this.audioList = audioList;
            return this;
        }

        public Builder setAudioIndex(int audioIndex) {
            this.audioIndex = audioIndex;
            return this;
        }

        public Builder setAudioIntervalOnPlay(int audioIntervalOnPlay) {
            this.audioIntervalOnPlay = audioIntervalOnPlay;
            return this;
        }

        //单音频播放
        public Builder setAudio(Audio audio) {
            if (audio == null) {
                throw new IllegalArgumentException("audio is empty");
            }

            this.audioList = new ArrayList<>(1);
            this.audioList.add(audio);

            return this;
        }

        public AudioInfoConfig build() {
            if (audioList == null) {
                throw new IllegalArgumentException("audioList is null");
            }

            initEmptyFieldsWithDefaultValues();
            return new AudioInfoConfig(this);
        }

        private void initEmptyFieldsWithDefaultValues() {
            if (null == audioIndex) {
                audioIndex = 0;
            }

            if (null == audioIntervalOnPlay) {
                audioIntervalOnPlay = DELFAULT_INTERVAL_ON_PLAY;
            }
        }
    }
}
