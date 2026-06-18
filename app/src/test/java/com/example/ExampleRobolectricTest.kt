package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Nexus GPT", appName)
  }

  @Test
  fun `verify shared preferences session store`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sharedPrefs = context.getSharedPreferences("test_nexus_gpt_prefs", Context.MODE_PRIVATE)
    
    // Verify initial default session values
    assertEquals(false, sharedPrefs.getBoolean("is_logged_in", false))
    
    // Commit simulated sign-on actions
    sharedPrefs.edit()
        .putBoolean("is_logged_in", true)
        .putString("user_name", "Jane Doe")
        .putString("user_email", "jane@email.com")
        .putString("user_initials", "JD")
        .commit()
      
    // Assert keys are reading back correctly
    assertEquals(true, sharedPrefs.getBoolean("is_logged_in", false))
    assertEquals("Jane Doe", sharedPrefs.getString("user_name", ""))
    assertEquals("jane@email.com", sharedPrefs.getString("user_email", ""))
    assertEquals("JD", sharedPrefs.getString("user_initials", ""))
  }
}
