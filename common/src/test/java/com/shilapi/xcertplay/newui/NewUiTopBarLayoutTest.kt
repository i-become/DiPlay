package com.shilapi.xcertplay.newui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.shilapi.xcertplay.host.R
import org.robolectric.RuntimeEnvironment
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "en-w1280dp-h720dp-land")
class NewUiTopBarLayoutTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun topBarBandKeepsMockupHeight() {
        composeTestRule.setContent {
            NewUiTheme {
                NewUiShell(
                    destination = NewUiDestination.CONNECTION,
                    showHs5Branding = true,
                    topBarStatus = NewUiTopBarStatus.placeholder.copy(timeText = "11:35"),
                    onDestinationSelected = {},
                    onOpenHeadUnitHome = {},
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }
        composeTestRule.onNodeWithTag(NewUiTestTags.TopBarBand)
            .assertHeightIsEqualTo(NewUiLayout.TopBarToDividerHeight)
    }

    @Test
    fun hongqiBadgeIsInsetFromTopBarTop() {
        val context = RuntimeEnvironment.getApplication()
        val badgeDescription = context.getString(R.string.new_ui_brand_hongqi)
        composeTestRule.setContent {
            NewUiTheme {
                NewUiShell(
                    destination = NewUiDestination.CONNECTION,
                    showHs5Branding = true,
                    topBarStatus = NewUiTopBarStatus.placeholder.copy(timeText = "11:35"),
                    onDestinationSelected = {},
                    onOpenHeadUnitHome = {},
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        val band = composeTestRule.onNodeWithTag(NewUiTestTags.TopBarBand).getBoundsInRoot()
        val badge = composeTestRule.onNodeWithContentDescription(badgeDescription).getBoundsInRoot()
        val topInset = badge.top - band.top
        assertTrue(topInset.value >= NewUiLayout.TopBarContentVerticalPadding.value - 1f)
    }

    @Test
    fun hongqiBadgeIsInsetFromTopBarBottom() {
        val context = RuntimeEnvironment.getApplication()
        val badgeDescription = context.getString(R.string.new_ui_brand_hongqi)
        composeTestRule.setContent {
            NewUiTheme {
                NewUiShell(
                    destination = NewUiDestination.CONNECTION,
                    showHs5Branding = true,
                    topBarStatus = NewUiTopBarStatus.placeholder.copy(timeText = "11:35"),
                    onDestinationSelected = {},
                    onOpenHeadUnitHome = {},
                ) {
                    Box(Modifier.fillMaxSize())
                }
            }
        }

        val band = composeTestRule.onNodeWithTag(NewUiTestTags.TopBarBand).getBoundsInRoot()
        val badge = composeTestRule.onNodeWithContentDescription(badgeDescription).getBoundsInRoot()
        val bottomInset = band.bottom - badge.bottom
        assertTrue(bottomInset.value >= NewUiLayout.TopBarContentVerticalPadding.value - 1f)
    }
}
