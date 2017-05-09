package com.jack.audio.play.module;

/**
 * 2017/4/28.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:播放状态bean类
 */

public class Result {
    private String activePath;
    private String token;

    /**
     * represent the index of play list
     */
    private int position = 0;
    private int duration;
    private int current;    //milliseconds


    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getActivePath() {
        return activePath;
    }

    public void setActivePath(String activePath) {
        this.activePath = activePath;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getCurrent() {
        return current;
    }

    public void setCurrent(int current) {
        this.current = current;
    }


    public void reset() {
        this.activePath = null;
        this.position = 0;
        this.duration = 0;
        this.current = 0;
    }

    @Override
    public String toString() {
        return "Result{" +
                "activePath='" + activePath + '\'' +
                ", token='" + token + '\'' +
                ", position=" + position +
                ", duration=" + duration +
                ", current=" + current +
                '}';
    }
}
