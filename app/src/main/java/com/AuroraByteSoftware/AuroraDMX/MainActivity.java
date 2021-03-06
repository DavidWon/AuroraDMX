package com.AuroraByteSoftware.AuroraDMX;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.AuroraByteSoftware.AuroraDMX.fixture.Fixture;
import com.AuroraByteSoftware.AuroraDMX.fixture.RGBFixture;
import com.AuroraByteSoftware.AuroraDMX.fixture.StandardFixture;
import com.AuroraByteSoftware.AuroraDMX.ui.CueActivity;
import com.AuroraByteSoftware.AuroraDMX.ui.PatchActivity;
import com.AuroraByteSoftware.Billing.util.IabException;
import com.AuroraByteSoftware.Billing.util.IabHelper;
import com.AuroraByteSoftware.Billing.util.IabResult;
import com.AuroraByteSoftware.Billing.util.Inventory;
import com.joanzapata.iconify.IconDrawable;
import com.joanzapata.iconify.Iconify;
import com.joanzapata.iconify.fonts.FontAwesomeIcons;
import com.joanzapata.iconify.fonts.FontAwesomeModule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import fr.azelart.artnetstack.domain.artpollreply.ArtPollReply;

public class MainActivity extends Activity implements OnSharedPreferenceChangeListener {

    private static SharedPreferences sharedPref;
    static Double cueCount = 1.0;// cueCount++ = new cue num
    private static boolean updatingFixtures = false;
    private int orgColor = 0;
    private static IabHelper mHelper;

    public static final ArrayList<ArtPollReply> foundServers = new ArrayList<>();
    public static ProgressDialog progressDialog = null;

    //Static Variables
    static final int ALLOWED_PATCHED_DIMMERS = 50;
    public static final int MAX_DIMMERS = 512;
    private static final int MAX_CHANNEL = 512;
    private final static String BASE_64_ENCODED_PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAj9upoasavmU51/j6g7vWchEf/g2SGuntcXPlzVu8vp3avDGMQp8E20iI+IO5vqB4wVKf9QRiAv0DFLw+XAGCpx7t6GDt4Sd/qMOkj49Eas1R1Uvghp4yy9Cc/8pL7QOvSW99pq9Pg2iqqbPXlAlLmByQy2p9qhDhl788dMZsUd2VxL5NHY2zQl7a1emWH/MUpvVHNSJkTSdQrLJ4cruTvEDldtD0jSNadK1NSruwa/BH6ieLVswek1cyE7hm0Od5pWw0XCpkR6L7ZkEkeTovSihA3h+rSy6kxZCqrDzMR++EOCxwS/kB3Ly6M5E6EwjZVbK18UQM8/Ecr7/buYxalQIDAQAB";
    static final String ITEM_SKU = "unlock_channels";
    private static final String TAG = "AuroraDMX";

    /**
     * patchList[0] is ignored. All numbers are in human format (start at 1) not computer.
     */
    public static List<ChPatch> patchList = new ArrayList<>();
    private final List<String> listOfSkus = new ArrayList<>();
    public static List<Fixture> alColumns = null;
    public static ArrayList<CueObj> alCues = null;
    public static int upwardCue = -1;
    public static int downwardCue = -1;

    private ProjectManagement pm = null;


    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pm = new ProjectManagement(this);
        Log.v(TAG, "onCreate");
        startupIAB();
        Iconify.with(new FontAwesomeModule());
        startup();
        Intent intent = getIntent();

        //Open the file if the user clicked on a file in the file system
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            try {
                pm.openFile(intent.getDataString());
            } catch (IOException e) {
                Toast.makeText(MainActivity.this, R.string.cannotOpen, Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        } else {
            pm.open(null);
        }

        AuroraNetwork.setUpNetwork(this);
    }

    private void startupIAB() {
        mHelper = new IabHelper(this, BASE_64_ENCODED_PUBLIC_KEY);
        mHelper.enableDebugLogging(true, "IabHelper");
        try {
            mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
                public void onIabSetupFinished(IabResult result) {
                    Log.d(TAG, "Setup finished.");
                    if (!result.isSuccess()) {
                        // Oh noes, there was a problem.
                        Log.v(TAG, "Problem setting up in-app billing: " + result);
                        return;
                    }
                    // Have we been disposed of in the meantime? If so, quit.
                    if (mHelper == null)
                        return;
                    // IAB is fully set up. Now, let's get an inventory of stuff
                    // we own.
                    Log.d(TAG, "Setup successful. Querying inventory.");
                    listOfSkus.add(ITEM_SKU);
                    mHelper.queryInventoryAsync(true, listOfSkus, mQueryFinishedListener);
                }
            });
        } catch (NullPointerException e) {
            e.printStackTrace();
            mHelper = null;
            Toast.makeText(MainActivity.this, R.string.errorProcessPurchases, Toast.LENGTH_SHORT).show();
        }
    }

    private void startup() {
        updatingFixtures = true;
        // load the layout
        setContentView(R.layout.activity_main);
        setupButtons();


        sharedPref = PreferenceManager.getDefaultSharedPreferences(this);
        sharedPref.registerOnSharedPreferenceChangeListener(this);
        alCues = new ArrayList<>();
        alColumns = new ArrayList<>();
        int number_channels = Integer.parseInt(sharedPref.getString(SettingsActivity.channels, "5"));
        setNumberOfFixtures(number_channels, null, null, null);
        updatingFixtures = false;
    }

    private void setupButtons() {
        // Add Cue
        Button button = (Button) findViewById(R.id.AddCueButton);
        CueClickListener addCueListener = new CueClickListener();
        button.setOnClickListener(addCueListener);
        button.setOnLongClickListener(addCueListener);
        // Next Cue
        button = (Button) findViewById(R.id.go_button);
        NextCueListener goListener = new NextCueListener();
        button.setOnClickListener(goListener);
        button.setOnLongClickListener(goListener);
    }

    public static SharedPreferences getSharedPref() {
        return sharedPref;
    }


    void setNumberOfFixtures(int numberFixtures, String[] channelNames, boolean[] isRGB,
                             String[] valuePresets) {
        updatingFixtures = true;
        // check for app purchase
        boolean paid = true;
        if (!BuildConfig.DEBUG) {
            // Skip the paid check when developing
            try {
                if (null != mHelper) {
                    Inventory inv = mHelper.queryInventory(false, listOfSkus);
                    paid = inv.hasPurchase(ITEM_SKU);
                } else {
                    paid = false;
                }
            } catch (IabException e) {
                e.printStackTrace();
            } catch (IllegalStateException | NullPointerException e) {
                // Do nothing we must not be connected yet
            }
        }

        // Input cleansing
        if (numberFixtures > MAX_CHANNEL) {
            Toast.makeText(MainActivity.this, R.string.dmxRangeError, Toast.LENGTH_SHORT).show();
            numberFixtures = MAX_CHANNEL;
        } else if (numberFixtures < 1) {
            Toast.makeText(MainActivity.this, R.string.dmxRangeError, Toast.LENGTH_SHORT).show();
            numberFixtures = 1;
        } else if (numberFixtures > 5 && !paid) {
            Toast.makeText(MainActivity.this, R.string.dmxRangePurchaseLimit, Toast.LENGTH_SHORT).show();
            numberFixtures = 5;
        }

        int change = numberFixtures - alColumns.size();
        int numOfChannelsUsed = 0;//use calculateChannelCount() ?
        LinearLayout mainLayout = (LinearLayout) findViewById(R.id.ChanelLayout);

        orgColor = Color.parseColor(getSharedPref().getString("channel_color", "#ffcc00"));
        if (change > 0) {// Adding channels
            for (int x = (numberFixtures - change); x < numberFixtures && x < 512; x++) {
                if (isRGB != null && isRGB[x])
                    alColumns.add(new RGBFixture(
                            this,
                            channelNames == null ? null : channelNames[x],
                            valuePresets == null ? null : valuePresets[x]));
                else
                    alColumns.add(new StandardFixture(
                            this,
                            channelNames == null ? null : channelNames[x],
                            valuePresets == null ? null : valuePresets[x]
                    ));
                mainLayout.addView(alColumns.get(x).getViewGroup());
            }
            for (Fixture fixture : alColumns) {
                numOfChannelsUsed += fixture.getChLevels().size();
            }
            for (CueObj cue : alCues) {// Pad ch's in cues
                cue.padChannels(numOfChannelsUsed);
            }
            for (int i = patchList.size(); i < numOfChannelsUsed; i++) {
                patchList.add(new ChPatch(i));
            }
        } else if (change < 0) {// Removing channels
            for (int x = (numberFixtures - change); x > numberFixtures && x <= 512; x--) {
                mainLayout.removeView(alColumns.get(x - 1).getViewGroup());
                alColumns.remove(x - 1);
            }
            for (Fixture fixture : alColumns) {
                numOfChannelsUsed += fixture.getChLevels().size();
            }
            for (CueObj cue : alCues) {
                cue.padChannels(numOfChannelsUsed);
            }
            patchList = new ArrayList<>(patchList.subList(0, numOfChannelsUsed));
        }

        //Reset all the levels to display the percentage or step value
        for (Fixture alColumn : alColumns) {
            alColumn.setChLevels(alColumn.getChLevels());
        }

        oneToOnePatch();

        recalculateFixtureNumbers();

        getSharedPref().edit().putString(SettingsActivity.channels, String.format("%1$s", numberFixtures)).apply();
        updatingFixtures = false;
    }

    private void oneToOnePatch() {
        int orgSize = patchList.size();
        int endSize = calculateChannelCount();
        for (int i = orgSize; i < endSize; i++) {
            patchList.add(new ChPatch(i));
        }
    }

    // Initiating Menu XML file (menu.xml)
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main, menu);
        addFAIcon(menu, R.id.menu_patch, FontAwesomeIcons.fa_th);
        addFAIcon(menu, R.id.menu_cues, FontAwesomeIcons.fa_caret_square_o_right);
        addFAIcon(menu, R.id.menu_settings, FontAwesomeIcons.fa_sliders);
        addFAIcon(menu, R.id.menu_share, FontAwesomeIcons.fa_share_alt);
        addFAIcon(menu, R.id.menu_save, FontAwesomeIcons.fa_save);
        addFAIcon(menu, R.id.menu_load, FontAwesomeIcons.fa_folder_open);
        return  super.onCreateOptionsMenu(menu);
    }

    private void addFAIcon(Menu menu, int menuId, FontAwesomeIcons faIcon) {
        menu.findItem(menuId).setIcon(
                new IconDrawable(this, faIcon)
                        .colorRes(R.color.white)
                        .alpha(204)
                        .actionBarSize());
    }

    /**
     * Event Handling for Individual menu item selected Identify single menu
     * item by it's id
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        boolean fadingInProgress = false;
        for (CueObj cue : alCues) {
            if (cue.isFadeInProgress())
                fadingInProgress = true;
        }
        if (!fadingInProgress || item.getItemId() == R.id.menu_cues) {
            Intent intent;
            switch (item.getItemId()) {
                case R.id.menu_patch:
                    intent = new Intent(this, PatchActivity.class);
                    startActivity(intent);
                    return true;
                case R.id.menu_cues:
                    intent = new Intent(this, CueActivity.class);
                    startActivity(intent);
                    return true;
                case R.id.menu_settings:
                    intent = new Intent(this, SettingsActivity.class);
                    startActivity(intent);
                    return true;
                case R.id.menu_save:
                    pm.onSaveClick();
                    return true;
                case R.id.menu_load:
                    pm.onLoadClick();
                    return true;
                case R.id.menu_share:
                    pm.onShare();
                    return true;
                default:
                    return super.onOptionsItemSelected(item);
            }
        } else {
            Toast.makeText(MainActivity.this, R.string.waitingOnFade, Toast.LENGTH_SHORT).show();
            return super.onOptionsItemSelected(item);
        }

    }

    protected static List<Integer> getCurrentChannelArray() {
        List<Integer> out = new ArrayList<>();
        for (Fixture fixture : alColumns) {
            out.addAll(fixture.getChLevels());
        }
        return out;
    }

    public static int[] getCurrentDimmerLevels() {
        if (updatingFixtures) {
            return null;
        }
        int out[] = new int[MAX_DIMMERS];

        //flatten the channel values from the UI
        List<Integer> chValues = new ArrayList<>();
        for (Fixture fixture : alColumns) {
            chValues.addAll(fixture.getChLevels());
        }

        for (int ch = 1; ch <= chValues.size() && patchList.size() > ch; ch++) {
            for (Integer dim : patchList.get(ch).getDimmers()) {
                if (dim <= 0 || dim > MAX_DIMMERS)
                    continue;
                int oldLvl = out[dim - 1];
                int newVal = chValues.get(ch - 1);
                if (oldLvl < newVal)
                    out[dim - 1] = newVal;
            }
        }
        return out;
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(TAG, "Pref Change");
        if (key.equals(SettingsActivity.channels)) {
            setNumberOfFixtures(Integer.parseInt(sharedPreferences.getString(SettingsActivity.channels, "5")), null, null, null);
        }
    }

    protected void onResume() {
        super.onResume();
        Log.v(TAG, "onResume");
        getSharedPref().registerOnSharedPreferenceChangeListener(this);

        if (getSharedPref().getBoolean(SettingsActivity.restoredefaults, false)) {
            restoreDefaults();
            getSharedPref().edit().putBoolean(SettingsActivity.restoredefaults, false).apply();
        }
        // Change the column color
        int color = Color.parseColor(getSharedPref().getString("channel_color", "#ffcc00"));
        if (color != orgColor) {// update the channel color
            for (Fixture col : alColumns) {
                col.setScrollColor(color);
            }
        }
        // Change the num of ch.
        int number_channels = 5;
        try {
            number_channels = Integer.parseInt(getSharedPref().getString(SettingsActivity.channels, "5"));
        } catch (Throwable t) {
            Log.w("ExternalStorage", "Error reading channel number", t);
        }
        setNumberOfFixtures(number_channels, null, null, null);

        //setup cues, the button inside the cue obj may be from the Cue Sheet
        pm.refreshCueView();

        AuroraNetwork.setUpNetwork(this);
    }

    @Override
    protected void onPause() {
        AuroraNetwork.stopNetwork();
        Log.v(TAG, "onPause");
        pm.save(null);
        if (getSharedPref() != null)
            getSharedPref().unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }
    }

    private void restoreDefaults() {
        Log.v(TAG, "Restoring Defaults");
        // Remove all the preferences
        getSharedPref().edit().clear().apply();
        // Stop ArtNet
        AuroraNetwork.stopNetwork();
        alCues.clear();
        cueCount = 1.0;
        for (Fixture columns : alColumns) {
            columns.getViewGroup().removeAllViews();
        }
        alColumns.clear();
        ((LinearLayout) findViewById(R.id.ChanelLayout)).removeAllViews();
        // Refresh views
        LinearLayout cueLine = ((LinearLayout) findViewById(R.id.CueLine));
        cueLine.removeAllViews();
        for (CueObj cue : alCues) {
            // create a new "Add Cue" button
            cue.setButton(CueClickListener.makeButton(cue.getCueName(), this));
            cueLine.addView(cue.getButton());
            cue.setHighlight(0, 0, 0);
        }
        PatchActivity.patchOneToOne();

        // create a new "Add Cue" button
        ((LinearLayout) findViewById(R.id.CueLine)).addView(CueClickListener.makeButton(getString(R.string.AddCue), this));
        AuroraNetwork.setUpNetwork(this);
    }

    /**
     * @return the mHelper
     */
    static IabHelper getmHelper() {
        return mHelper;
    }

    // Listener that's called when we finish querying the items and
    // subscriptions we own
    private static final IabHelper.QueryInventoryFinishedListener mQueryFinishedListener = new IabHelper.QueryInventoryFinishedListener() {
        public void onQueryInventoryFinished(IabResult result, Inventory inventory) {
            if (result.isFailure()) {
                // handle error
                System.err.print(result.getMessage());
            }
        }
    };

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "onActivityResult(" + requestCode + "," + resultCode + "," + data);

        // Pass on the activity result to the helper for handling
        if (mHelper != null && !mHelper.handleActivityResult(requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        } else {
            Log.i(TAG, "onActivityResult handled by IABUtil.");
        }
    }

    public void recalculateFixtureNumbers() {
        int currentFixtureNum = 1;
        for (Fixture fixture : alColumns) {
            fixture.setFixtureNumber(currentFixtureNum);
            currentFixtureNum += fixture.getChLevels().size();
        }
    }

    private int calculateChannelCount() {
        int currentFixtureNum = 1;
        for (Fixture fixture : alColumns) {
            currentFixtureNum += fixture.getChLevels().size();
        }
        return currentFixtureNum;
    }

    public static List<Fixture> getAlColumns() {
        return alColumns;
    }

    public static void setUpdatingFixtures(boolean updatingFixtures) {
        MainActivity.updatingFixtures = updatingFixtures;
    }
}
