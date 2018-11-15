package ca.pkay.rcloneexplorer;

import android.app.ActivityManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.leinardi.android.speeddial.SpeedDialView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Stack;
import java.util.regex.Pattern;

import ca.pkay.rcloneexplorer.Dialogs.InputDialog;
import ca.pkay.rcloneexplorer.Dialogs.SortDialog;
import ca.pkay.rcloneexplorer.RecyclerViewAdapters.FilePickerAdapter;
import es.dmoral.toasty.Toasty;

import static android.os.Build.VERSION.SDK_INT;

public class FilePicker extends AppCompatActivity implements    FilePickerAdapter.OnClickListener,
                                                                InputDialog.OnPositive,
                                                                SortDialog.OnClickListener {

    public static final String FILE_PICKER_PICK_DESTINATION_TYPE = "ca.pkay.rcexplorer.FILE_PICKER_PICK_DEST_TYPE";
    public static final String FILE_PICKER_RESULT = "ca.pkay.rcexplorer.FILE_PICKER_RESULT";
    private static final String SHARED_PREFS_SORT_ORDER = "ca.pkay.rcexplorer.sort_order";
    private final String SAVED_PATH = "ca.pkay.rcexplorer.FilePicker.PATH";
    private final String SAVED_DESTINATION_PICKER_TYPE = "ca.pkay.rcexplorer.FilePicker.DESTINATION_PICKER_TYPE";
    private final String SAVED_SELECTED_ITEMS = "ca.pkay.rcexplorer.FilePicker.SELECTED_ITEMS";
    private FilePickerAdapter filePickerAdapter;
    private ActionBar actionBar;
    private ArrayList<String> availableStorage;
    private File root;
    private File current;
    private ArrayList<File> fileList;
    private boolean isDarkTheme;
    private int sortOrder;
    private SpeedDialView speedDialView;
    private boolean destinationPickerType;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyTheme();
        setContentView(R.layout.activity_file_picker);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(R.string.file_picker_root_title);
        }

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        sortOrder = sharedPreferences.getInt(SHARED_PREFS_SORT_ORDER, SortDialog.ALPHA_ASCENDING);

        if (savedInstanceState != null) {
            destinationPickerType = savedInstanceState.getBoolean(SAVED_DESTINATION_PICKER_TYPE);
            availableStorage = new ArrayList<>(getStorageDirectories());
            String path = savedInstanceState.getString(SAVED_PATH);
            if (path == null) {
                root = current = new File(availableStorage.get(0));
            } else {
                current = new File(path);
                for (String s : availableStorage) {
                    if (path.startsWith(s) || path.equals(s)) {
                        root = new File(s);
                        break;
                    }
                }
                actionBar.setTitle(current.getName());
            }
        } else {
            destinationPickerType = getIntent().getBooleanExtra(FILE_PICKER_PICK_DESTINATION_TYPE, false);
            availableStorage = new ArrayList<>(getStorageDirectories());
            root = current = new File(availableStorage.get(0));
        }

        fileList = new ArrayList<>(Arrays.asList(current.listFiles()));
        sortDirectory();

        RecyclerView recyclerView = findViewById(R.id.file_picker_list);
        recyclerView.setHasFixedSize(true);
        RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);
        filePickerAdapter = new FilePickerAdapter(this, fileList, destinationPickerType, findViewById(R.id.empty_folder_view));
        recyclerView.setAdapter(filePickerAdapter);

        speedDialView = findViewById(R.id.fab_activity_file_picker);
        if (!destinationPickerType) {
            speedDialView.setVisibility(View.INVISIBLE);
        }
        speedDialView.setOnChangeListener(new SpeedDialView.OnChangeListener() {
            @Override
            public boolean onMainActionSelected() {
                fabClicked();
                return false;
            }

            @Override
            public void onToggleChanged(boolean isOpen) {

            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVED_PATH, current.getPath());
        outState.putBoolean(SAVED_DESTINATION_PICKER_TYPE, destinationPickerType);
        if (filePickerAdapter.isDataSelected()) {
            ArrayList<File> selectedItems = filePickerAdapter.getSelectedFiles();
            ArrayList<String> savedSelectedPaths = new ArrayList<>();

            for (File file : selectedItems) {
                savedSelectedPaths.add(file.getAbsolutePath());
            }
            outState.putStringArrayList(SAVED_SELECTED_ITEMS, savedSelectedPaths);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        ArrayList<String> savedSelectedFilePaths = savedInstanceState.getStringArrayList(SAVED_SELECTED_ITEMS);
        if (savedSelectedFilePaths != null && !savedSelectedFilePaths.isEmpty()) {
            ArrayList<File> selectedFiles = new ArrayList<>();
            for (String path : savedSelectedFilePaths) {
                selectedFiles.add(new File(path));
            }
            filePickerAdapter.setSelectedFiles(selectedFiles);
        }
    }

    private void applyTheme() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        int customPrimaryColor = sharedPreferences.getInt(getString(R.string.pref_key_color_primary), -1);
        int customAccentColor = sharedPreferences.getInt(getString(R.string.pref_key_color_accent), -1);
        isDarkTheme = sharedPreferences.getBoolean(getString(R.string.pref_key_dark_theme), false);
        getTheme().applyStyle(CustomColorHelper.getPrimaryColorTheme(this, customPrimaryColor), true);
        getTheme().applyStyle(CustomColorHelper.getAccentColorTheme(this, customAccentColor), true);
        if (isDarkTheme) {
            getTheme().applyStyle(R.style.DarkTheme, true);
        } else {
            getTheme().applyStyle(R.style.LightTheme, true);
        }

        // set recents app color to the primary color
        Bitmap bm = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_round);
        ActivityManager.TaskDescription taskDesc = new ActivityManager.TaskDescription(getString(R.string.app_name), bm, customPrimaryColor);
        setTaskDescription(taskDesc);
    }

    private void fabClicked() {
        if (destinationPickerType) {
            setResultData();
        } else if (filePickerAdapter.isDataSelected()) {
            setResultData(filePickerAdapter.getSelectedFiles());
        } else {
            Toasty.info(this, getString(R.string.nothing_selected), Toast.LENGTH_SHORT, true).show();
            finish();
        }
    }

    private void setResultData(ArrayList<File> result) {
        Intent intent = new Intent();
        intent.putExtra(FILE_PICKER_RESULT, result);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void setResultData() {
        Intent intent = new Intent();
        intent.putExtra(FILE_PICKER_RESULT, current.getPath());
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.file_picker_menu, menu);
        if (destinationPickerType) {
            menu.removeItem(R.id.action_select_all);
        } else {
            menu.removeItem(R.id.action_new_folder);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_select_all:
                filePickerAdapter.toggleSelectAll();
                return true;
            case R.id.action_sort:
                showSortMenu();
                return true;
            case R.id.action_new_folder:
                newFolderDialog();
                return true;
            case R.id.action_storage:
                showStorageMenu();
                return true;
            default:
                return super.onOptionsItemSelected(item);

        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onBackPressed() {
        if (current.equals(root)) {
            super.onBackPressed();
            if (filePickerAdapter.isDataSelected()) {
                setResultData(filePickerAdapter.getSelectedFiles());
            } else {
                finish();
            }
        } else {
            current = current.getParentFile();
            if (current.equals(root)) {
                actionBar.setTitle(R.string.file_picker_root_title);
            } else {
                actionBar.setTitle(current.getName());
            }
            fileList.clear();
            fileList.addAll(Arrays.asList(current.listFiles()));
            sortDirectory();
            filePickerAdapter.setNewData(fileList);

            if (destinationPickerType) {
                speedDialView.show();
            }
        }
    }

    @Override
    public void onDirectoryClicked(File file) {
        actionBar.setTitle(file.getName());
        current = file;
        fileList.clear();
        fileList.addAll(Arrays.asList(file.listFiles()));
        sortDirectory();
        filePickerAdapter.setNewData(fileList);

        if (destinationPickerType) {
            speedDialView.show();
        }
    }

    @Override
    public void onFileClicked(File file) {
        setResultData(new ArrayList<>(Collections.singletonList(file)));
    }

    @Override
    public void onSelectionChanged(boolean isSelected) {
        if (isSelected && speedDialView.getVisibility() == View.INVISIBLE) {
            speedDialView.setVisibility(View.VISIBLE);
            speedDialView.show();
        } else if (!isSelected && speedDialView.getVisibility() == View.VISIBLE) {
            speedDialView.hide();
            speedDialView.setVisibility(View.INVISIBLE);
        }
    }

    private void newFolderDialog() {
        new InputDialog()
                .setTitle(R.string.create_new_folder)
                .setMessage(R.string.type_new_folder_name)
                .setNegativeButton(R.string.cancel)
                .setPositiveButton(R.string.okay_confirmation)
                .setDarkTheme(isDarkTheme)
                .show(getSupportFragmentManager(), "input dialog");
    }

    /*
     * Input Dialog callback
     */
    @Override
    public void onPositive(String tag, String input) {
        if (input.trim().isEmpty()) {
            return;
        }
        
        File newDir = new File(current.getAbsolutePath() + "/" + input);
        if (newDir.mkdir()) {
            fileList.clear();
            fileList.addAll(Arrays.asList(current.listFiles()));
            sortDirectory();
            filePickerAdapter.setNewData(fileList);
        }
    }

    private void showStorageMenu() {
        AlertDialog.Builder builder;
        final int[] userSelected = new int[1];
        if (isDarkTheme) {
            builder = new AlertDialog.Builder(this, R.style.DarkDialogTheme);
        } else {
            builder = new AlertDialog.Builder(this);
        }

        builder.setTitle(R.string.select_storage);

        int selected = availableStorage.indexOf(root.getAbsolutePath());
        CharSequence[] options = availableStorage.toArray(new CharSequence[availableStorage.size()]);
        builder.setSingleChoiceItems(options, selected, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                userSelected[0] = which;
            }
        });
        builder.setNegativeButton(R.string.cancel, null);
        builder.setPositiveButton(R.string.select, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switchStorage(userSelected[0]);
            }
        });

        builder.show();
    }

    private void switchStorage(int which) {
        File newStorage = new File(availableStorage.get(which));
        root = current = newStorage;
        fileList = new ArrayList<>(Arrays.asList(current.listFiles()));
        sortDirectory();
        filePickerAdapter.setNewData(fileList);
    }

    private void showSortMenu() {
        SortDialog sortDialog = new SortDialog();
        sortDialog
                .setTitle(R.string.sort)
                .setNegativeButton(R.string.cancel)
                .setPositiveButton(R.string.ok)
                .setSortOrder(sortOrder)
                .setDarkTheme(isDarkTheme);
        if (getFragmentManager() != null) {
            sortDialog.show(getSupportFragmentManager(), "sort dialog");
        }
    }

    /*
     * Sort Dialog callback
     */
    @Override
    public void onPositiveButtonClick(int sortById, int sortOrderId) {
        sortSelected(sortById, sortOrderId);
    }

    private void sortDirectory() {
        switch (sortOrder) {
            case SortDialog.MOD_TIME_DESCENDING:
                Collections.sort(fileList, new FileComparators.SortFileModTimeDescending());
                sortOrder = SortDialog.MOD_TIME_ASCENDING;
                break;
            case SortDialog.MOD_TIME_ASCENDING:
                Collections.sort(fileList, new FileComparators.SortFileModTimeAscending());
                sortOrder = SortDialog.MOD_TIME_DESCENDING;
                break;
            case SortDialog.SIZE_DESCENDING:
                Collections.sort(fileList, new FileComparators.SortFileSizeDescending());
                sortOrder = SortDialog.SIZE_ASCENDING;
                break;
            case SortDialog.SIZE_ASCENDING:
                Collections.sort(fileList, new FileComparators.SortFileSizeAscending());
                sortOrder = SortDialog.SIZE_DESCENDING;
                break;
            case SortDialog.ALPHA_ASCENDING:
                Collections.sort(fileList, new FileComparators.SortFileAlphaAscending());
                sortOrder = SortDialog.ALPHA_ASCENDING;
                break;
            case SortDialog.ALPHA_DESCENDING:
            default:
                Collections.sort(fileList, new FileComparators.SortFileAlphaDescending());
                sortOrder = SortDialog.ALPHA_DESCENDING;
        }
    }

    private void sortSelected(int sortById, int sortOrderId) {
        switch (sortById) {
            case R.id.radio_sort_name:
                if (sortOrderId == R.id.radio_sort_ascending) {
                    Collections.sort(fileList, new FileComparators.SortFileAlphaAscending());
                    sortOrder = SortDialog.ALPHA_ASCENDING;
                } else {
                    Collections.sort(fileList, new FileComparators.SortFileAlphaDescending());
                    sortOrder = SortDialog.ALPHA_DESCENDING;
                }
                break;
            case R.id.radio_sort_date:
                if (sortOrderId == R.id.radio_sort_ascending) {
                    Collections.sort(fileList, new FileComparators.SortFileModTimeAscending());
                    sortOrder = SortDialog.MOD_TIME_ASCENDING;
                } else {
                    Collections.sort(fileList, new FileComparators.SortFileModTimeDescending());
                    sortOrder = SortDialog.MOD_TIME_DESCENDING;
                }
                break;
            case R.id.radio_sort_size:
                if (sortOrderId == R.id.radio_sort_ascending) {
                    Collections.sort(fileList, new FileComparators.SortFileSizeAscending());
                    sortOrder = SortDialog.SIZE_ASCENDING;
                } else {
                    Collections.sort(fileList, new FileComparators.SortFileSizeDescending());
                    sortOrder = SortDialog.SIZE_DESCENDING;
                }
                break;
        }
        filePickerAdapter.updateData(fileList);

        if (sortOrder > 0) {
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
            sharedPreferences.edit().putInt(SHARED_PREFS_SORT_ORDER, sortOrder).apply();
        }
    }


    /*
     * based on the answer from https://stackoverflow.com/questions/11281010/how-can-i-get-external-sd-card-path-for-android-4-0/18871043#18871043
     */
    private ArrayList<String> getStorageDirectories() {
        // Final set of paths
        final ArrayList<String> storageDirectories = new ArrayList<>();
        // Primary physical SD-CARD (not emulated)
        final String rawExternalStorage = System.getenv("EXTERNAL_STORAGE");
        // All Secondary SD-CARDs (all exclude primary) separated by ":"
        final String rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE");
        // Primary emulated SD-CARD
        final String rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET");
        if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
            // Device has physical external storage; use plain paths.
            if (TextUtils.isEmpty(rawExternalStorage)) {
                // EXTERNAL_STORAGE undefined; falling back to default.
                // Check for actual existence of the directory before adding to list
                if (new File("/storage/sdcard0").exists()) {
                    storageDirectories.add("/storage/sdcard0");
                } else {
                    //We know nothing else, use Environment's fallback
                    storageDirectories.add(Environment.getExternalStorageDirectory().getAbsolutePath());
                }
            } else {
                storageDirectories.add(rawExternalStorage);
            }
        } else {
            // Device has emulated storage; external storage paths should have
            // userId burned into them.
            final String rawUserId;
            final String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            final Pattern dirSeparator = Pattern.compile("/");
            final String[] folders = dirSeparator.split(path);
            final String lastFolder = folders[folders.length - 1];
            boolean isDigit = false;
            try {
                Integer.valueOf(lastFolder);
                isDigit = true;
            } catch (NumberFormatException ignored) {

            }
            rawUserId = isDigit ? lastFolder : "";
            // /storage/emulated/0[1,2,...]
            if (TextUtils.isEmpty(rawUserId)) {
                storageDirectories.add(rawEmulatedStorageTarget);
            } else {
                storageDirectories.add(rawEmulatedStorageTarget + File.separator + rawUserId);
            }
        }

        if (destinationPickerType) {
            File[] extFiles = getExternalMediaDirs();
            String internalStorage = storageDirectories.get(0);
            for (File f : extFiles) {
                if (f == null) {
                    continue;
                }

                if (!f.getAbsolutePath().startsWith(internalStorage)) {
                    storageDirectories.add(f.getAbsolutePath());
                }
            }
        }

        // Add all secondary storages
        if (!destinationPickerType) {
            if (!TextUtils.isEmpty(rawSecondaryStoragesStr)) {
                // All Secondary SD-CARDs splited into array
                final String[] rawSecondaryStorages = rawSecondaryStoragesStr.split(File.pathSeparator);
                Collections.addAll(storageDirectories, rawSecondaryStorages);
            }
        }

        return storageDirectories;
    }
}
