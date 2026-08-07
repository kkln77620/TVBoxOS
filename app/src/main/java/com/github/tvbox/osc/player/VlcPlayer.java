package com.github.tvbox.osc.player;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.view.Surface;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;

import java.util.ArrayList;
import java.util.Map;

import xyz.doikki.videoplayer.player.AbstractPlayer;

/**
 * VLC 播放器内核: 基于 libVLC, 支持几乎所有视频容器/编码格式
 */
public class VlcPlayer extends AbstractPlayer {

    private LibVLC mLibVLC;
    private MediaPlayer mMediaPlayer;
    private Media mMedia;
    private Context mContext;
    private boolean isPreparing;
    private boolean isPlaying;
    private boolean isLooping;
    private float mSpeed = 1.0f;
    private int mVideoWidth, mVideoHeight;
    private Surface mSurface;

    public VlcPlayer(Context context) {
        this.mContext = context.getApplicationContext();
    }

    @Override
    public void initPlayer() {
        ArrayList<String> options = new ArrayList<>();
        // 注意: 只能使用 libVLC 支持的选项, ffmpeg风格选项(--avcodec-*)会导致 LibVLC 初始化失败
        options.add("--file-caching=3000");
        options.add("--network-caching=6000");
        options.add("--live-caching=3000");
        try {
            mLibVLC = new LibVLC(mContext, options);
            mMediaPlayer = new MediaPlayer(mLibVLC);
            setupListeners();
        } catch (Throwable th) {
            th.printStackTrace();
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError(-1, "VLC 初始化失败: " + th.getMessage());
            }
        }
    }

    private void setupListeners() {
        mMediaPlayer.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                switch (event.type) {
                    case MediaPlayer.Event.Opening:
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_BUFFERING_START, 0);
                        }
                        break;
                    case MediaPlayer.Event.Playing:
                        isPlaying = true;
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onInfo(MEDIA_INFO_RENDERING_START, 0);
                        }
                        break;
                    case MediaPlayer.Event.Paused:
                        isPlaying = false;
                        break;
                    case MediaPlayer.Event.Stopped:
                        isPlaying = false;
                        break;
                    case MediaPlayer.Event.EndReached:
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onCompletion();
                        }
                        break;
                    case MediaPlayer.Event.EncounteredError:
                        if (mPlayerEventListener != null) {
                            mPlayerEventListener.onError(-1, "VLC 播放错误");
                        }
                        break;
                    case MediaPlayer.Event.Buffering:
                        if (event.getBuffering() >= 100f && isPreparing) {
                            isPreparing = false;
                            if (mPlayerEventListener != null) {
                                mPlayerEventListener.onPrepared();
                            }
                        }
                        break;
                }
            }
        });
    }

    @Override
    public void setDataSource(String path, Map<String, String> headers) {
        if (path.startsWith("file://")) {
            path = path.substring(7);
        }
        Uri uri = Uri.parse(path);
        if (uri.getScheme() == null || uri.getScheme().isEmpty()) {
            mMedia = new Media(mLibVLC, path);
        } else {
            mMedia = new Media(mLibVLC, uri);
        }
        // 透传 headers 使用 VLC 的 :http-XXX 选项格式
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, String> e : headers.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    String key = e.getKey().toLowerCase();
                    if (key.equals("user-agent")) {
                        mMedia.addOption(":http-user-agent=" + e.getValue());
                    } else if (key.equals("referer")) {
                        mMedia.addOption(":http-referrer=" + e.getValue());
                    } else if (key.equals("cookie")) {
                        mMedia.addOption(":http-cookie=" + e.getValue());
                    }
                }
            }
        }
        mMediaPlayer.setMedia(mMedia);
        // 注意: 不立即 release() Media, 等待 reset/release 时统一释放, 避免部分设备播放异常
    }

    @Override
    public void setDataSource(AssetFileDescriptor fd) {
        try {
            mMedia = new Media(mLibVLC, fd.getFileDescriptor());
            mMediaPlayer.setMedia(mMedia);
            mMedia.release();
        } catch (Throwable th) {
            if (mPlayerEventListener != null) {
                mPlayerEventListener.onError(-1, "VLC 不支持该资源类型");
            }
        }
    }

    @Override
    public void start() {
        if (mMediaPlayer != null) {
            mMediaPlayer.play();
            isPlaying = true;
        }
    }

    @Override
    public void pause() {
        if (mMediaPlayer != null) {
            mMediaPlayer.pause();
            isPlaying = false;
        }
    }

    @Override
    public void stop() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            isPlaying = false;
        }
    }

    @Override
    public void prepareAsync() {
        isPreparing = true;
        // 附着 Surface
        if (mSurface != null) {
            mMediaPlayer.getVLCVout().setVideoSurface(mSurface, null);
            mMediaPlayer.getVLCVout().attachViews();
        }
        mMediaPlayer.play();
    }

    @Override
    public void reset() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            isPlaying = false;
            isPreparing = false;
        }
    }

    @Override
    public boolean isPlaying() {
        return isPlaying;
    }

    @Override
    public void seekTo(long time) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setTime(time);
        }
    }

    @Override
    public void release() {
        if (mMediaPlayer != null) {
            mMediaPlayer.stop();
            mMediaPlayer.getVLCVout().detachViews();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        if (mMedia != null) {
            try {
                mMedia.release();
            } catch (Throwable ignored) {
            }
            mMedia = null;
        }
        if (mLibVLC != null) {
            mLibVLC.release();
            mLibVLC = null;
        }
    }

    @Override
    public long getCurrentPosition() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getTime();
        }
        return 0;
    }

    @Override
    public long getDuration() {
        if (mMediaPlayer != null) {
            return mMediaPlayer.getLength();
        }
        return 0;
    }

    @Override
    public int getBufferedPercentage() {
        return isPreparing ? 50 : 100;
    }

    @Override
    public void setSurface(Surface surface) {
        this.mSurface = surface;
        if (mMediaPlayer != null && surface != null) {
            mMediaPlayer.getVLCVout().setVideoSurface(surface, null);
            mMediaPlayer.getVLCVout().attachViews();
        }
    }

    @Override
    public void setDisplay(SurfaceHolder holder) {
        if (holder != null) {
            setSurface(holder.getSurface());
        }
    }

    @Override
    public void setVolume(float v1, float v2) {
        if (mMediaPlayer != null) {
            mMediaPlayer.setVolume((int) (Math.max(v1, v2) * 100));
        }
    }

    @Override
    public void setLooping(boolean isLooping) {
        this.isLooping = isLooping;
    }

    @Override
    public void setOptions() {
    }

    @Override
    public void setSpeed(float speed) {
        this.mSpeed = speed;
        if (mMediaPlayer != null) {
            mMediaPlayer.setRate(speed);
        }
    }

    @Override
    public float getSpeed() {
        return mSpeed;
    }

    @Override
    public long getTcpSpeed() {
        return 0;
    }
}