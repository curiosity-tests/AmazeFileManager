/*
 * Copyright (C) 2014-2025 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>,
 * Emmanuel Messulam<emmanuelbendavid@gmail.com>, Raymond Lai <airwave209gt at gmail.com> and Contributors.
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.ui.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import android.os.Environment
import androidx.lifecycle.Lifecycle
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.amaze.filemanager.R
import com.amaze.filemanager.test.StoragePermissionHelper
import com.amaze.filemanager.ui.activities.PreferencesActivity
import com.amaze.filemanager.ui.fragments.preferencefragments.BackupPrefsFragment
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import org.awaitility.Awaitility.await
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupPrefsFragmentTest {
    var storagePath: String = Environment.getExternalStorageDirectory().absolutePath
    var fileName = "amaze_backup.json"

    @Rule
    @JvmField
    val storagePermissionRule: GrantPermissionRule =
        GrantPermissionRule
            .grant(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)

    @Rule
    @JvmField
    val notificationPermissionRule: GrantPermissionRule =
        if (SDK_INT >= TIRAMISU) {
            GrantPermissionRule.grant(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            GrantPermissionRule.grant()
        }

    /**
     * Storage permission is needed for saving the preferences to a user accessible file
     */
    @Before
    fun grantManageStoragePermission() {
        StoragePermissionHelper.grantManageStoragePermission()
    }

    /** Test exporting and reimporting preferences */
    @Test
    fun testPreferencesExportImport() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val exportFile = File("$storagePath${File.separator}$fileName")
        exportFile.delete() // delete if already exists

        export(context, exportFile)
        import(exportFile)
    }

    /**
     * Waits (with a timeout) for the given file to exist, since some writes to storage happen
     * asynchronously on a background thread.
     */
    private fun waitForFile(
        file: File,
        timeoutSeconds: Long = 5L,
    ) {
        await().atMost(timeoutSeconds, TimeUnit.SECONDS).until {
            file.exists()
        }
    }

    /**
     * Test whether the exported file contains the expected preference values
     */
    private fun export(
        context: Context,
        exportFile: File,
    ) {
        val backupPrefsFragment = BackupPrefsFragment()
        val activityScenario = ActivityScenario.launch(PreferencesActivity::class.java)

        // Espresso requires an activity to be RESUMED to dispatch view actions/clicks.
        activityScenario.moveToState(Lifecycle.State.RESUMED)

        lateinit var preferences: SharedPreferences

        activityScenario.onActivity { preferencesActivity ->
            preferencesActivity.supportFragmentManager.beginTransaction()
                .add(backupPrefsFragment, null)
                .commitNow()

            backupPrefsFragment.exportPrefs()

            val tempFile = File("${context.cacheDir.absolutePath}${File.separator}$fileName")

            assertTrue(tempFile.exists())

            preferences = PreferenceManager.getDefaultSharedPreferences(preferencesActivity)
        }

        // Espresso's onView().perform() must run on the instrumentation/test thread, never from
        // inside onActivity {} or runOnUiThread {} (both of which run on the main/UI thread).
        // Espresso internally synchronizes with the UI thread itself; calling it from the UI
        // thread can deadlock or throw IllegalStateException.
        // exportPrefs() launches MainActivity with an ACTION_SEND intent, which shows a Snackbar
        // with a "Save" action; that is the only view action needed here.
        onView(withText(R.string.save)).perform(ViewActions.click())

        // The actual write to storagePath happens asynchronously (RxJava) after the "Save" click
        // and after MainActivity finishes, so poll for the file instead of asserting immediately.
        waitForFile(exportFile)

        val preferenceMap: Map<String?, *> = preferences.all

        val inputString =
            exportFile
                .inputStream()
                .bufferedReader()
                .use {
                    it.readText()
                }

        val type = object : TypeToken<Map<String?, *>>() {}.type

        val importMap: Map<String?, *> =
            GsonBuilder()
                .create()
                .fromJson(
                    inputString,
                    type,
                )

        for ((key, value) in preferenceMap) {
            val importedValue = importMap[key]
            val mapValue =
                if (importedValue != null && importedValue::class.simpleName.equals("Double")) {
                    (importedValue as Double).toInt() // since Gson parses Integer as Double
                } else {
                    importedValue
                }

            assertEquals("Difference found at key $key", value, mapValue)
        }

        activityScenario.close()
    }

    /**
     * Test whether the imported preferences contains the expected values
     */
    private fun import(exportFile: File) {
        val activityScenario = ActivityScenario.launch(PreferencesActivity::class.java)
        activityScenario.moveToState(Lifecycle.State.STARTED)

        val backupPrefsFragment = BackupPrefsFragment()

        activityScenario.onActivity { preferencesActivity ->
            preferencesActivity.supportFragmentManager.beginTransaction()
                .add(backupPrefsFragment, null)
                .commitNow()

            javaClass.getResourceAsStream("/$fileName")?.copyTo(exportFile.outputStream())

            backupPrefsFragment.onActivityResult(
                BackupPrefsFragment.IMPORT_BACKUP_FILE,
                Activity.RESULT_OK,
                Intent().setData(
                    Uri.fromFile(exportFile),
                ),
            )

            val inputString =
                exportFile
                    .inputStream()
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            val type = object : TypeToken<Map<String?, *>>() {}.type

            val importMap: Map<String?, *> =
                GsonBuilder()
                    .create()
                    .fromJson(inputString, type)

            val preferences = PreferenceManager.getDefaultSharedPreferences(preferencesActivity)

            val preferenceMap: Map<String?, *> = preferences.all

            assertFalse(preferenceMap.containsKey(null))

            for ((k, v) in preferenceMap) {
                // This cast tells the kotlin type checker that fail() never returns
                val key = k ?: (fail() as Nothing)
                val value = v ?: (fail() as Nothing)

                assertTrue("checkPrefEqual($key) failed", checkPrefEqual(preferences, importMap, key, value))
            }
        }

        activityScenario.close()
    }

    private fun checkPrefEqual(
        preferences: SharedPreferences,
        importMap: Map<String?, *>,
        key: String,
        value: Any,
    ): Boolean {
        when (value::class.simpleName) {
            "Boolean" -> return importMap[key] as Boolean ==
                preferences.getBoolean(key, false)
            "Float" ->
                importMap[key] as Float ==
                    preferences.getFloat(key, 0f)
            "Int" -> {
                // since Gson parses Integer as Double
                val toInt = (importMap[key] as Double).toInt()

                return toInt == preferences.getInt(key, 0)
            }
            "Long" -> return importMap[key] as Long ==
                preferences.getLong(key, 0L)
            "String" -> return importMap[key] as String ==
                preferences.getString(key, null)
            "Set<*>" -> return importMap[key] as Set<*> ==
                preferences.getStringSet(key, null)
        }
        return false
    }
}
