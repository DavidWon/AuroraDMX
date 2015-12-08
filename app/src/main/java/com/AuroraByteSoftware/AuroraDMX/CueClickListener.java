package com.AuroraByteSoftware.AuroraDMX;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.AuroraByteSoftware.AuroraDMX.ui.EditCueMenu;

import java.util.ArrayList;
import java.util.List;

/**
 * Listen to click events on the cue list
 * Created by furtchet on 12/6/15.
 */
public class CueClickListener implements View.OnClickListener, View.OnLongClickListener {
    private static final String TAG = "AuroraDMX";
    Context context;
    Button button;

    /**
     * Cue Click handler
     */
    @Override
    public void onClick(View arg0) {
        if (arg0 instanceof Button) {
            button = (Button) arg0;
        }
        if (button != null && button.getContext() == null) {
            Log.e(TAG, "Cue button onclick had a null context");
            return;
        }
        context = button.getContext();
        int curCue = -1;// Current cue number on alCues scale
        boolean otherCueFading = false;
        for (int x = 0; x < MainActivity.alCues.size(); x++) {
            if (button == MainActivity.alCues.get(x).getButton()) {
                curCue = x;
                break;
            }
        }

        if (curCue == -1) {// Adding a new cue
            createCue(button, MainActivity.getCurrentChannelArray(), -1, "", -1, -1);
        } else {
            // check if anyone else is fading

            // ======= Loading a cue ========
            List<Integer> newChLevels = MainActivity.alCues.get(curCue).getLevels();
            // Find previously active cue
            int prevCueNum = -1;
            for (int x = 0; x < MainActivity.alCues.size(); x++) {
                if (MainActivity.alCues.get(x).getHighlight() > 1 && x != curCue)
                    prevCueNum = x;
                if (MainActivity.alCues.get(x).isFadeInProgress())
                    otherCueFading = true;
            }
            if (!otherCueFading) {
                Log.d(TAG, String.format("newChLevels %1$s", newChLevels));
                Log.d(TAG, "oldChLevels " + MainActivity.getCurrentChannelArray());
                // Set the channels to the cue
                int chIndex = 0;
                for (int x = 0; x < MainActivity.alColumns.size() && x < newChLevels.size(); x++) {
                    // If a channel changed value
                    int fixtureUses = MainActivity.alColumns.get(x).getChLevels().size();
                    MainActivity.alColumns.get(x).setupIncrementLevelFade(new ArrayList<>(newChLevels.subList(chIndex, chIndex + fixtureUses)));
                    chIndex += fixtureUses;
                }
                MainActivity.alCues.get(curCue).startCueFade(curCue, prevCueNum);
            } else {
                Toast.makeText(context, R.string.waitingOnFade, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Cue Long Click handler
     */
    @Override
    public boolean onLongClick(View buttonView) {
        boolean buttonIsAddCue = true;
        for (CueObj cue : MainActivity.alCues) {
            if (cue.getButton() == buttonView) {
                buttonIsAddCue = false;
            }
        }
        if (!buttonIsAddCue)
            EditCueMenu.createEditCueMenu(MainActivity.alCues, (Button) buttonView);
        return true;
    }


    /**
     * Adds a new cue with the current ch Levels
     *
     * @param button   of "Add Cue"
     * @param chLevels level of channels
     * @param cueNum   cue number
     * @param cueName  the cue name
     */
    protected void createCue(Button button, List<Integer> chLevels, double cueNum, String cueName, int fadeUpTime, int fadeDownTime) {
        // Rename the old button to Cue #

        try {
            fadeUpTime = (fadeUpTime == -1) ? Integer.parseInt(MainActivity.getSharedPref().getString("fade_up_time", "5")) : fadeUpTime;
            fadeDownTime = (fadeDownTime == -1) ? Integer.parseInt(MainActivity.getSharedPref().getString("fade_down_time", "5")) : fadeDownTime;
        } catch (Throwable t) {
            t.printStackTrace();
            Toast.makeText(context, R.string.errNumConv, Toast.LENGTH_SHORT).show();
        }
        if (cueNum == -1) {
            String name = String.format(context.getString(R.string.cue), MainActivity.cueCount);
            button.setText(name);
            MainActivity.alCues.add(new CueObj(MainActivity.cueCount, name, fadeUpTime, fadeDownTime, chLevels, button));
        } else {
            button.setText(String.format(context.getString(R.string.cue), cueNum));
            // Add cue name
            if (cueName.equals("")) {
                MainActivity.alCues.add(new CueObj(cueNum, String.format(context.getString(R.string.cue), cueNum), fadeUpTime, fadeDownTime, chLevels, button));
            } else {
                MainActivity.alCues.add(new CueObj(cueNum, cueName, fadeUpTime, fadeDownTime, chLevels, button));
            }
        }
        MainActivity.cueCount++;

        // create a new "Add Cue" button
        ((LinearLayout) button.getParent()).addView(makeButton(context.getString(R.string.AddCue), context));
    }


    public static Button makeButton(String name, Context context) {
        Button button = new Button(context);
        button.setText(name);
        button.setOnClickListener(new CueClickListener());
        button.setLongClickable(true);
        button.setOnLongClickListener(new CueClickListener());
        return button;
    }
}