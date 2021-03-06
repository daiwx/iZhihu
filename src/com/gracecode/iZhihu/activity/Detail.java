package com.gracecode.iZhihu.activity;

import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Message;
import android.os.PowerManager;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.gracecode.iZhihu.BuildConfig;
import com.gracecode.iZhihu.R;
import com.gracecode.iZhihu.dao.Question;
import com.gracecode.iZhihu.db.QuestionsDatabase;
import com.gracecode.iZhihu.fragment.DetailFragment;
import com.gracecode.iZhihu.fragment.ScrollDetailFragment;
import com.gracecode.iZhihu.util.Helper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class Detail extends BaseActivity implements ViewPager.OnPageChangeListener {
    private static final String TAG = Detail.class.getName();

    public static final String INTENT_EXTRA_CURRENT_QUESTION = "mCurrentQuestion";
    public static final String INTENT_EXTRA_QUESTIONS = "questions";
    public static final String INTENT_EXTRA_CURRENT_POSITION = "mCurrentPosition";
    public static final String INTENT_MODIFIED_LISTS = "modifiedLists";
    private static final int DEFAULT_POSITION = 0;

    private Menu menuItem;
    private PowerManager.WakeLock wakeLock;
    private PowerManager powerManager;
    private AudioManager audioManager;

    private static final int MESSAGE_UPDATE_START_SUCCESS = 0;
    private static final int MESSAGE_UPDATE_START_FAILD = -1;

    private QuestionsDatabase mQuestionsDatabase;

    private DetailFragment mFragCurrentQuestionDetail = null;
    private ScrollDetailFragment mFragListQuestions = null;

    private Question mCurrentQuestion = null;
    private ArrayList<Question> questionsList = new ArrayList<Question>();
    private int mCurrentPosition = DEFAULT_POSITION;

    private boolean isShareByTextOnly = false;
    private boolean isShareAndSave = true;
    private boolean isSetScrollRead = true;

    /**
     * 标记当前条目（未）收藏
     */
    private final Runnable mMarkAsStared = new Runnable() {
        @Override
        public void run() {
            Boolean isStared = mCurrentQuestion.isStared();

            if (mQuestionsDatabase.markQuestionAsStared(mCurrentQuestion.getId(), !isStared) > 0) {
                mCurrentQuestion.setStared(!isStared);
                UIChangedChangedHandler.sendEmptyMessage(MESSAGE_UPDATE_START_SUCCESS);
            } else {
                UIChangedChangedHandler.sendEmptyMessage(MESSAGE_UPDATE_START_FAILD);
            }
        }
    };


    /**
     * 刷新 UI 线程集中的地方
     */
    private final android.os.Handler UIChangedChangedHandler = new android.os.Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_UPDATE_START_SUCCESS:
                    Helper.showShortToast(context,
                            getString(mCurrentQuestion.isStared() ?
                                    R.string.mark_as_starred : R.string.cancel_mark_as_stared));

                    updateMenu();
                    break;
                case MESSAGE_UPDATE_START_FAILD:
                    Helper.showLongToast(context, getString(R.string.database_faild));
                    break;
            }
        }
    };
    private int currentStreamVolume = 0;


    /**
     * 更新 ActionBar 的收藏图标，并返回状态
     */
    private void updateMenu() {
        if (menuItem != null) {
            menuItem.findItem(R.id.menu_favorite).setIcon(mCurrentQuestion.isStared() ?
                    R.drawable.ic_action_star_selected : R.drawable.ic_action_star);

            if (!Helper.isExternalStorageExists() && !isShareByTextOnly) {
                menuItem.findItem(R.id.menu_share).setEnabled(false);
            }
        }
    }


    /**
     * 判断是否需要常亮屏幕
     *
     * @return boolean
     */
    private boolean isNeedScreenWakeLock() {
        return mSharedPreferences.getBoolean(getString(R.string.key_wake_lock), true);
    }


    /**
     * 获取截图文件
     *
     * @return
     */
    private File getScreenShotFile() throws IOException {
        if (mCurrentQuestion == null || mCurrentQuestion.getId() == DetailFragment.ID_NOT_FOUND) {
            throw new IOException();
        }

        File pictureDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(pictureDirectory, mCurrentQuestion.getId() + ".png");
    }


    private void updateCurrentQuestion(int index) {
        mCurrentQuestion = questionsList.get(index);
        if (mCurrentQuestion != null) {
            updateMenu();

            mCurrentPosition = index;
            mQuestionsDatabase.markAsRead(mCurrentQuestion.getId());
            mCurrentQuestion.setUnread(false);

            if (mFragListQuestions != null) {
                mFragCurrentQuestionDetail = mFragListQuestions.getItem(mCurrentPosition);
            }
        }
    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 屏幕常亮控制
        this.powerManager = ((PowerManager) getSystemService(POWER_SERVICE));
        this.wakeLock = powerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, Detail.class.getName());

        this.audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 配置项
        this.isShareByTextOnly = mSharedPreferences.getBoolean(getString(R.string.key_share_text_only), false);
        this.isShareAndSave = mSharedPreferences.getBoolean(getString(R.string.key_share_and_save), true);
        this.isSetScrollRead = mSharedPreferences.getBoolean(getString(R.string.key_scroll_read), true);

        // Database for questions.
        this.mQuestionsDatabase = new QuestionsDatabase(context);

        // 获取当权选定的条目
        if (savedInstanceState != null) {
            this.mCurrentQuestion = savedInstanceState.getParcelable(INTENT_EXTRA_CURRENT_POSITION);
            this.questionsList = savedInstanceState.getParcelableArrayList(INTENT_EXTRA_QUESTIONS);
            this.mCurrentPosition = savedInstanceState.getInt(INTENT_EXTRA_CURRENT_POSITION);
        } else {
            this.mCurrentQuestion = getIntent().getParcelableExtra(INTENT_EXTRA_CURRENT_QUESTION);
            this.questionsList = getIntent().getParcelableArrayListExtra(INTENT_EXTRA_QUESTIONS);
            this.mCurrentPosition = getIntent().getIntExtra(INTENT_EXTRA_CURRENT_POSITION, DEFAULT_POSITION);
        }

        // 是否是滚动阅读
        if (isSetScrollRead && questionsList.size() > 0) {
            this.mFragListQuestions = new ScrollDetailFragment(this, questionsList, mCurrentPosition);
        } else {
            this.mFragCurrentQuestionDetail = new DetailFragment(this, mCurrentQuestion);
        }

        // ActionBar 的样式
        actionBar.setDisplayHomeAsUpEnabled(true);

        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, (isSetScrollRead) ? mFragListQuestions : mFragCurrentQuestionDetail)
                .commit();
    }


    @Override
    public void onSaveInstanceState(Bundle outState) {
        try {
            if (outState != null) {
                outState.putParcelableArrayList(INTENT_EXTRA_QUESTIONS, questionsList);
                outState.putParcelable(INTENT_EXTRA_CURRENT_POSITION, mCurrentQuestion);
                outState.putInt(INTENT_EXTRA_CURRENT_POSITION, mCurrentPosition);
            } else {
                outState = new Bundle();
            }

            super.onSaveInstanceState(outState);
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onResume() {
        super.onResume();

        // Mark and update current item.
        updateCurrentQuestion(mCurrentPosition);

        // 弱化 Navigation Bar
        getWindow().getDecorView()
                .setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

        if (isNeedScreenWakeLock()) {
            wakeLock.acquire();
        }

        // 是否保留分享时用的图片
        try {
            File screenshots = getScreenShotFile();
            if (!isShareByTextOnly && !isShareAndSave && screenshots != null && screenshots.exists()) {
                screenshots.delete();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public void onPause() {
        super.onPause();
        try {
            wakeLock.release();
        } catch (RuntimeException e) {
            if (BuildConfig.DEBUG) Log.e(TAG, e.getMessage());
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.detail, menu);
        menuItem = menu;
        updateMenu();
        return true;
    }


    private boolean returnModifiedListsAndFinish() {
        Intent intent = new Intent();
        intent.putParcelableArrayListExtra(INTENT_MODIFIED_LISTS, questionsList);
        setResult(Intent.FILL_IN_PACKAGE, intent);
        finish();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            // Home(Back button)
            case android.R.id.home:
                return returnModifiedListsAndFinish();

            // Toggle star
            case R.id.menu_favorite:
                new Thread(mMarkAsStared).start();
                return true;
//
//            case R.id.menu_comment:
//                int answerId = mCurrentQuestion.getAnswerId();
//
//                Intent intent = new Intent(Detail.this, Comment.class);
//                intent.putExtra(Comment.ANSWER_ID, answerId);
//                startActivity(intent);
//
//                break;

            // View question via zhihu.com
            case R.id.menu_view_at_zhihu:
                if (Helper.isZhihuInstalled(this)) {
                    String url = "zhihu://answers/" + mCurrentQuestion.getAnswerId();
                    Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(i);
                } else {
                    Helper.showShortToast(this, getString(R.string.zhihu_not_intstalled));
                }
                return true;

            // View question at online
            case R.id.menu_view_at_online:
                String url =
                        String.format(getString(R.string.url_zhihu_questioin_pre),
                                mCurrentQuestion.getQuestionId(), mCurrentQuestion.getAnswerId());

                Helper.openWithBrowser(this, url);
                break;

            // Share question by intent
            case R.id.menu_share:
                String shareString = mCurrentQuestion.getShareString(context);

                if (isShareByTextOnly) {
                    Helper.openShareIntentWithPlainText(this, shareString);
                    return true;
                }

                try {
                    File screenShotFile = getScreenShotFile();
                    if (mFragCurrentQuestionDetail.isTempScreenShotsFileCached()) {
                        Helper.copyFile(mFragCurrentQuestionDetail.getTempScreenShotsFile(), screenShotFile);
                        Helper.openShareIntentWithImage(this, shareString, Uri.fromFile(screenShotFile));
                    } else {
                        throw new IOException();
                    }

                } catch (IOException e) {
                    Helper.openShareIntentWithPlainText(this, shareString);
                    e.printStackTrace();
                } finally {
                    return true;
                }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        currentStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM);
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, 0, AudioManager.FLAG_PLAY_SOUND);
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        audioManager.setStreamVolume(AudioManager.STREAM_SYSTEM, currentStreamVolume, AudioManager.FLAG_PLAY_SOUND);
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return returnModifiedListsAndFinish();
        }

        boolean isTurningPageByVolumeKey =
                mSharedPreferences.getBoolean(getString(R.string.key_page_turning_by_volkey), true);

        if (isTurningPageByVolumeKey
                && (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP)) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_VOLUME_DOWN:
                    mFragCurrentQuestionDetail.nextPage();
                    break;
                case KeyEvent.KEYCODE_VOLUME_UP:
                    mFragCurrentQuestionDetail.prevPage();
                    break;
            }

            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onPageScrolled(int i, float v, int i2) {
        // ...
    }

    @Override
    public void onPageSelected(int i) {
        updateCurrentQuestion(i);
    }


    @Override
    public void onPageScrollStateChanged(int i) {

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mQuestionsDatabase.close();
    }
}
