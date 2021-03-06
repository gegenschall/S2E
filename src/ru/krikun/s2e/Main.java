package ru.krikun.s2e;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import ru.krikun.s2e.utils.Helper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

public class Main extends PreferenceActivity {

    private HashMap<String, Integer> jobSizes;
    private HashMap<String, Boolean> jobStatuses;
    private HashMap<String, Boolean> jobValues;

    private int[] spacesData;
    private int[] spacesExt;
    private int[] spacesCache;

    private Resources res;
    private SharedPreferences prefs;

    private boolean showExtendedInformation;

    private void loadStatuses() {
        jobStatuses = new HashMap<String, Boolean>();

        for (String target : res.getStringArray(R.array.targets)) {
            jobStatuses.put(target, Helper.checkStatus(target));
        }
    }

    private void loadValues() {
        jobValues = new HashMap<String, Boolean>();

        for (String target : res.getStringArray(R.array.targets)) {
            jobValues.put(target, prefs.getBoolean(target, false));
        }
    }

    private void loadTargetSizes() {
        jobSizes = new HashMap<String, Integer>();

        for (String target : res.getStringArray(R.array.targets)) {
            jobSizes.put(target, Helper.getSize(Helper.getPath(target)));
        }
    }

    private void loadPartitionsSizes() {
        // 0 - Size; 1 - Used; 2 - Free
        spacesData = Helper.getPartitionsInfo("data");
        spacesExt = Helper.getPartitionsInfo("sd-ext");
        spacesCache = Helper.getPartitionsInfo("cache");

    }

    private void loadExtendedInformationPref() {
        showExtendedInformation = prefs.getBoolean("show_extended_information", true);
    }

    private void setTargetSummary(Preference pref, String target, String partition) {
        pref.setSummary(
                res.getString(R.string.location) + ": /" + partition + "/" + target + "\n" +
                        res.getString(R.string.size) + ": " +
                        Helper.convertSize(jobSizes.get(target), res.getString(R.string.kb), res.getString(R.string.mb))
        );
    }

    private void setMovingSummary(Preference pref, String targetPartition, String sourcesPartition) {
        pref.setSummary(
                String.format(res.getString(R.string.move), sourcesPartition, targetPartition) + "\n" +
                        res.getString(R.string.reboot_required)
        );
    }

    private void setTargetState() {

        loadValues();

        int sumToExt = 0;
        int sumToData = 0;

        for (String target : res.getStringArray(R.array.targets)) {
            Preference pref = findPreference(target);
            if (!target.equals("download")) {
                if (jobValues.get(target) && !jobStatuses.get(target)) {
                    setMovingSummary(pref, "/sd-ext", "/data");
                    sumToExt += jobSizes.get(target);
                } else if (!jobValues.get(target) && jobStatuses.get(target)) {
                    setMovingSummary(pref, "/data", "/sd-ext");
                    sumToData += jobSizes.get(target);
                }
            } else {
                if (jobValues.get(target) && !jobStatuses.get(target)) setMovingSummary(pref, "/sd-ext", "/cache");
                else if (!jobValues.get(target) && jobStatuses.get(target)) setMovingSummary(pref, "/cache", "/sd-ext");
            }
        }

        int freeSpace;
        for (String target : res.getStringArray(R.array.targets)) {
            Preference pref = findPreference(target);
            if (!target.equals("download")) {
                if (!jobValues.get(target) && !jobStatuses.get(target)) {

                    setTargetSummary(pref, target, "data");
                    freeSpace = spacesExt[2] - sumToExt - 1024;
                    if (!target.equals("data")) {
                        if (!Helper.compareSizes(jobSizes.get(target), freeSpace)) pref.setEnabled(false);
                        else pref.setEnabled(true);
                    } else {
                        if (!Helper.compareSizes(jobSizes.get(target), freeSpace) || !prefs.getBoolean("not_recommended", false))
                            pref.setEnabled(false);
                        else pref.setEnabled(true);
                    }

                } else if (jobValues.get(target) && jobStatuses.get(target)) {

                    setTargetSummary(pref, target, "sd-ext");
                    freeSpace = spacesData[2] - sumToData - 1024;
                    if (!target.equals("data")) {
                        if (!Helper.compareSizes(jobSizes.get(target), freeSpace)) pref.setEnabled(false);
                        else pref.setEnabled(true);
                    } else {
                        if (!Helper.compareSizes(jobSizes.get(target), freeSpace) || !prefs.getBoolean("not_recommended", false))
                            pref.setEnabled(false);
                        else pref.setEnabled(true);
                    }
                } else {
                    if (target.equals("data")) {
                        if (!prefs.getBoolean("not_recommended", false)) pref.setEnabled(false);
                        else pref.setEnabled(true);
                    }
                }
            } else {
                if (!jobValues.get(target) && !jobStatuses.get(target)) setTargetSummary(pref, target, "cache");
                else if (jobValues.get(target) && jobStatuses.get(target)) setTargetSummary(pref, target, "sd-ext");
            }
        }
    }

    private String getPartitionString(String partition) {
        int[] array = new int[3];
        if (partition.equals("data")) array = spacesData;
        else if (partition.equals("sd-ext")) array = spacesExt;
        else if (partition.equals("cache")) array = spacesCache;

        return "\n\t" + res.getString(R.string.size) + ": " +
                Helper.convertSize(array[0], res.getString(R.string.kb), res.getString(R.string.mb)) +
                "\n\t" + res.getString(R.string.used) + ": " +
                Helper.convertSize(array[1], res.getString(R.string.kb), res.getString(R.string.mb)) +
                "\n\t" + res.getString(R.string.free) + ": " +
                Helper.convertSize(array[2], res.getString(R.string.kb), res.getString(R.string.mb));
    }

    private void showPartitionsSpaces() {
        String message =
                "DATA:" + getPartitionString("data") +
                        "\n\nSD-EXT:" + getPartitionString("sd-ext") +
                        "\n\nCACHE:" + getPartitionString("cache");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message).setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showInformation() {

        if (showExtendedInformation) {
            Intent intentFreeSpace = new Intent(Intent.ACTION_MAIN);
            intentFreeSpace.setClassName("ru.krikun.freespace", "ru.krikun.freespace.activity.List");

            Intent intentFreeSpacePlus = new Intent(Intent.ACTION_MAIN);
            intentFreeSpacePlus.setClassName("ru.krikun.freespace.plus", "ru.krikun.freespace.plus.activity.List");

            if (checkInformationProvider(intentFreeSpacePlus))
                startActivity(intentFreeSpacePlus);

            else if (checkInformationProvider(intentFreeSpace))
                startActivity(intentFreeSpace);

            else {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(res.getString(R.string.fs_title))
                        .setMessage(res.getString(R.string.fs_msg))
                        .setCancelable(true)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(res.getString(R.string.fs_install), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                Intent fs = new Intent(Intent.ACTION_VIEW);
                                fs.setData(Uri.parse("https://market.android.com/details?id=ru.krikun.freespace"));
                                startActivity(fs);
                            }
                        })
                        .setNegativeButton(res.getString(R.string.fs_dont), new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                showExtendedInformation = false;
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putBoolean("show_extended_information", showExtendedInformation);
                                editor.commit();
                                showPartitionsSpaces();
                            }
                        });
                AlertDialog alert = builder.create();
                alert.show();
            }

        } else showPartitionsSpaces();

    }

    private boolean checkInformationProvider(Intent intent) {
        PackageManager packageManager = getPackageManager();
        List list = packageManager.queryIntentActivities(intent, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
        return list.size() > 0;
    }


    private void copyScript() {
        try {
            InputStream in = getAssets().open("script01.sh");
            FileOutputStream out = openFileOutput("script01.sh", 0);
            if (in != null) {
                int size = in.available();
                byte[] buffer = new byte[size];
                in.read(buffer);
                out.write(buffer, 0, size);
                in.close();
                out.close();
                Helper.copyScriptFinal();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean installScript() {
        copyScript();
        return Helper.checkScript();
    }

    private void showAlertScript() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(res.getString(R.string.alert_script_title))
                .setCancelable(false)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setNeutralButton(res.getString(R.string.exit), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        Main.this.finish();
                    }
                });
        AlertDialog alert = builder.create();
        alert.show();
    }

    private void setTitle() {
        TextView title_line = (TextView) findViewById(R.id.title_line);
        String label =
                "DATA: " + Helper.convertSize(spacesData[2], res.getString(R.string.kb), res.getString(R.string.mb)) + "  " +
                        "EXT: " + Helper.convertSize(spacesExt[2], res.getString(R.string.kb), res.getString(R.string.mb));
        title_line.setText(label);
    }

    private void runRefresh() {
        loadTargetSizes();
        loadPartitionsSizes();
        setTargetState();
        setTitle();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        res = getResources();

        loadStatuses();
        loadValues();

		if (!Helper.checkScript()) {
			if (!installScript()) showAlertScript();
		}

        setContentView(R.layout.main_screen);

        addPreferencesFromResource(R.xml.main);
        setOnPreferenceClick();

        runRefresh();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadExtendedInformationPref();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        new MenuInflater(getApplication())
                .inflate(R.menu.menu, menu);
        return (super.onPrepareOptionsMenu(menu));
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.refresh:
                Toast.makeText(Main.this, res.getString(R.string.refreshing), 5).show();
                runRefresh();
                break;
            case R.id.reboot:
                Helper.runReboot();
                break;
            case R.id.info:
                showInformation();
                break;
            case R.id.settings:
                Intent prefSettings = new Intent(getBaseContext(), Settings.class);
                startActivity(prefSettings);
                break;
            case R.id.myapps:
                Intent buy = new Intent(Intent.ACTION_VIEW);
                buy.setData(Uri.parse("market://search?q=pub:OlegKrikun"));
                startActivity(buy);
                break;
            case R.id.about:
                Intent prefAbout = new Intent(getBaseContext(), About.class);
                startActivity(prefAbout);
                break;
        }
        return (super.onOptionsItemSelected(item));
    }

    private void setOnPreferenceClick() {

        for (String target : res.getStringArray(R.array.targets)) {
            Preference pref = findPreference(target);
            pref.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

                public boolean onPreferenceClick(Preference preference) {
                    setTargetState();
                    return true;
                }

            });
        }
    }
}


