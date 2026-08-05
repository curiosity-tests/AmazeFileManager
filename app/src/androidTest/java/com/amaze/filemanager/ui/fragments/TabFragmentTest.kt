package com.amaze.filemanager.ui.fragments

import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import androidx.viewpager2.widget.ViewPager2
import com.amaze.filemanager.R
import com.amaze.filemanager.test.StoragePermissionHelper
import com.amaze.filemanager.ui.activities.MainActivity
import org.awaitility.Awaitility.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * Tests for [TabFragment] functionality, mainly for
 * https://github.com/TeamAmaze/AmazeFileManager/issues/1555.
 *
 * Note: deprecated methods and classes are used here for best reproducing the issues.
 */
@Suppress("DEPRECATION")
@RunWith(AndroidJUnit4::class)
class TabFragmentTest {
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

    @Before
    fun grantManageStoragePermission() {
        StoragePermissionHelper.grantManageStoragePermission()
    }

    /**
     * This test saves state while a MainFragment is detached.
     */
    @Test
    fun testFragmentStateSavingDuringDetachment() {
        withScenario { scenario ->
            awaitTabFragment(scenario)

            scenario.onActivity { activity ->
                val tabFragment =
                    activity.supportFragmentManager
                        .findFragmentById(R.id.content_frame) as TabFragment

                activity.supportFragmentManager.beginTransaction().apply {
                    tabFragment.fragments.firstOrNull { it.isAdded }?.let { detach(it) }
                    commitNow()
                }
            }

            recreateActivity(scenario)
        }
    }

    /**
     * Check if the fragment state is saved correctly during a configuration change
     * by rotate the screen while swiping between the tabs.
     */
    @Test
    fun testFragmentStateSavingDuringConfigChange() {
        withScenario { scenario ->
            setCurrentItem(scenario, 1)
            recreateActivity(scenario)
            awaitCurrentItem(scenario, 1)
        }
    }

    /**
     * Check if the fragment state is saved correctly during rapid tab swiping.
     */
    @Test
    fun testRapidTabSwitchingAndStateSaving() {
        withScenario { scenario ->
            repeat(10) {
                setCurrentItem(scenario, 1)
                setCurrentItem(scenario, 0)
            }

            recreateActivity(scenario)
            awaitCurrentItem(scenario, 0)
        }
    }

    /**
     * Check if the fragment state is saved correctly when the fragment is detached.
     */
    @Test
    fun testFragmentDetachmentAndStateSaving() {
        withScenario { scenario ->
            setCurrentItem(scenario, 1)
            awaitTabFragment(scenario)

            scenario.onActivity { activity ->
                val tabFragment =
                    activity.supportFragmentManager
                        .findFragmentById(R.id.content_frame) as TabFragment

                activity.supportFragmentManager.beginTransaction().apply {
                    tabFragment.fragments.firstOrNull { it.isAdded }?.let { detach(it) }
                    commitNow()
                }
            }

            recreateActivity(scenario)
        }
    }

    private fun withScenario(testBody: (ActivityScenario<MainActivity>) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitPager(scenario)
            testBody(scenario)
        }
    }

    private fun awaitPager(scenario: ActivityScenario<MainActivity>): ViewPager2 {
        var pager: ViewPager2? = null

        await().atMost(10, TimeUnit.SECONDS).until {
            scenario.onActivity { activity ->
                pager = activity.findViewById(R.id.pager)
            }

            pager != null
        }

        return requireNotNull(pager)
    }

    private fun awaitTabFragment(scenario: ActivityScenario<MainActivity>): TabFragment {
        var tabFragment: TabFragment? = null

        await().atMost(10, TimeUnit.SECONDS).until {
            runCatching {
                scenario.onActivity { activity ->
                    tabFragment =
                        activity.supportFragmentManager
                            .findFragmentById(R.id.content_frame) as? TabFragment
                }
            }

            tabFragment?.view != null && tabFragment?.fragments?.isNotEmpty() == true
        }

        return requireNotNull(tabFragment)
    }

    private fun setCurrentItem(
        scenario: ActivityScenario<MainActivity>,
        index: Int,
    ) {
        awaitPager(scenario)

        scenario.onActivity { activity ->
            activity.findViewById<ViewPager2>(R.id.pager).setCurrentItem(index, false)
        }

        awaitCurrentItem(scenario, index)
    }

    private fun awaitCurrentItem(
        scenario: ActivityScenario<MainActivity>,
        index: Int,
    ) {
        await().atMost(5, TimeUnit.SECONDS).until {
            var currentItem = -1

            runCatching {
                scenario.onActivity { activity ->
                    currentItem = activity.findViewById<ViewPager2>(R.id.pager).currentItem
                }
            }

            currentItem == index
        }
    }

    private fun recreateActivity(scenario: ActivityScenario<MainActivity>) {
        scenario.recreate()
        awaitPager(scenario)
        awaitTabFragment(scenario)
    }
}
