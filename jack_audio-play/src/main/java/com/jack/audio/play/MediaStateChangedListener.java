package com.jack.audio.play;


import com.jack.audio.play.module.Result;
import com.jack.audio.play.module.Status;

/**
 * 2017/4/30.
 * <p>
 * github:[https://github.com/jacky1234]
 * qq:[847564732]
 *
 * @author yangjianfei
 * @description:
 */

public interface MediaStateChangedListener {
    void onStateChanged(Status status, Result result);
}
