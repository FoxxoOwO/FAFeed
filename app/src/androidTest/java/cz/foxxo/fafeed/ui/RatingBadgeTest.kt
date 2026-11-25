package cz.foxxo.fafeed.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

/** Smoke test that the Compose test setup works without a running app. */
class RatingBadgeTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun adultRatingShowsAdultLabel() {
        composeTestRule.setContent {
            MaterialTheme { RatingBadge("adult") }
        }
        composeTestRule.onNodeWithText("ADULT").assertIsDisplayed()
    }

    @Test
    fun unknownRatingFallsBackToGeneral() {
        composeTestRule.setContent {
            MaterialTheme { RatingBadge("something") }
        }
        composeTestRule.onNodeWithText("GENERAL").assertIsDisplayed()
    }
}
