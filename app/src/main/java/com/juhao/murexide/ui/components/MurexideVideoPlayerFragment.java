package com.juhao.murexide.ui.components;

import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.AspectRatioFrameLayout;
import androidx.media3.ui.PlayerView;

import com.flyjingfish.openimagelib.BaseImageFragment;
import com.flyjingfish.openimagelib.photoview.PhotoView;
import com.flyjingfish.openimagelib.widget.LoadingView;

import java.util.Collections;

/** Public Fragment class so FragmentStateAdapter can recreate the OpenImage video page. */
@OptIn(markerClass = UnstableApi.class)
public final class MurexideVideoPlayerFragment extends BaseImageFragment<LoadingView> {
    private static final String TAG = "MurexideVideoPlayer";
    private static final String YUNHU_MEDIA_REFERER = "http://myapp.jwznb.com";

    private PhotoView smallCoverView;
    private PhotoView coverView;
    private LoadingView loadingIndicator;
    private PlayerView playerView;
    private ExoPlayer player;
    private long playbackPosition;

    public MurexideVideoPlayerFragment() {
        // Required public empty constructor for Fragment recreation.
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        FrameLayout root = new FrameLayout(requireContext());
        root.setBackgroundColor(Color.BLACK);

        playerView = new PlayerView(requireContext());
        playerView.setBackgroundColor(Color.BLACK);
        playerView.setShutterBackgroundColor(Color.BLACK);
        playerView.setUseController(true);
        playerView.setControllerAutoShow(true);
        playerView.setControllerShowTimeoutMs(3_000);
        playerView.setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setKeepScreenOn(true);
        playerView.setContentDescription("视频播放器");

        smallCoverView = new PhotoView(requireContext());
        coverView = new PhotoView(requireContext());
        loadingIndicator = new LoadingView(requireContext());

        FrameLayout.LayoutParams matchParent = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        );
        matchParent.gravity = Gravity.CENTER;
        root.addView(playerView, new FrameLayout.LayoutParams(matchParent));
        root.addView(smallCoverView, new FrameLayout.LayoutParams(matchParent));
        root.addView(coverView, new FrameLayout.LayoutParams(matchParent));

        int loadingSize = dp(44);
        FrameLayout.LayoutParams loadingParams = new FrameLayout.LayoutParams(loadingSize, loadingSize);
        loadingParams.gravity = Gravity.CENTER;
        root.addView(loadingIndicator, loadingParams);

        return root;
    }

    @Override
    protected PhotoView getSmallCoverImageView() {
        return smallCoverView;
    }

    @Override
    protected PhotoView getPhotoView() {
        return coverView;
    }

    @Override
    protected View getItemClickableView() {
        return coverView;
    }

    @Override
    protected LoadingView getLoadingView() {
        return loadingIndicator;
    }

    @Override
    protected void loadImageFinish(boolean isLoadImageSuccess) {
        loadingIndicator.hideLoading();
    }

    @Override
    public void onStart() {
        super.onStart();
        initializePlayer();
    }

    private void initializePlayer() {
        if (player != null || openImageUrl == null) {
            return;
        }
        String url = openImageUrl.getVideoUrl();
        if (url == null || url.trim().isEmpty()) {
            Toast.makeText(requireContext(), "视频地址无效", Toast.LENGTH_SHORT).show();
            return;
        }

        DefaultHttpDataSource.Factory httpDataSourceFactory =
                new DefaultHttpDataSource.Factory()
                        .setAllowCrossProtocolRedirects(true)
                        .setConnectTimeoutMs(15_000)
                        .setReadTimeoutMs(15_000)
                        .setDefaultRequestProperties(
                                Collections.singletonMap("Referer", YUNHU_MEDIA_REFERER)
                        );
        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(requireContext(), httpDataSourceFactory);
        DefaultMediaSourceFactory mediaSourceFactory =
                new DefaultMediaSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(requireContext())
                .setMediaSourceFactory(mediaSourceFactory)
                .setHandleAudioBecomingNoisy(true)
                .build();
        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    Log.d(
                            TAG,
                            "Stream ready at " + player.getCurrentPosition()
                                    + "ms, buffered=" + player.getBufferedPercentage() + "%"
                    );
                }
            }

            @Override
            public void onRenderedFirstFrame() {
                showPlayer();
            }

            @Override
            public void onPlayerError(@NonNull PlaybackException error) {
                Log.e(TAG, "Streaming playback failed", error);
                loadingIndicator.hideLoading();
                smallCoverView.setVisibility(View.GONE);
                coverView.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), "视频加载失败", Toast.LENGTH_SHORT).show();
            }
        });
        playerView.setPlayer(player);
        player.setMediaItem(MediaItem.fromUri(url));
        if (playbackPosition > 0L) {
            player.seekTo(playbackPosition);
        }
        player.setPlayWhenReady(true);
        player.prepare();
        loadingIndicator.hideLoading();
    }

    private void showPlayer() {
        loadingIndicator.hideLoading();
        smallCoverView.setVisibility(View.GONE);
        coverView.setVisibility(View.GONE);
        playerView.setVisibility(View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (player != null) {
            player.play();
        }
    }

    @Override
    public void onPause() {
        if (player != null) {
            playbackPosition = player.getCurrentPosition();
            player.pause();
        }
        super.onPause();
    }

    @Override
    public void onStop() {
        releasePlayer();
        super.onStop();
    }

    @Override
    public View getExitImageView() {
        releasePlayer();
        playerView.setVisibility(View.GONE);
        coverView.setVisibility(View.VISIBLE);
        coverView.setAlpha(1f);
        return super.getExitImageView();
    }

    @Override
    public void onDestroyView() {
        releasePlayer();
        super.onDestroyView();
        playerView = null;
        smallCoverView = null;
        coverView = null;
        loadingIndicator = null;
    }

    private void releasePlayer() {
        if (player == null) {
            return;
        }
        playbackPosition = player.getCurrentPosition();
        playerView.setPlayer(null);
        player.release();
        player = null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
