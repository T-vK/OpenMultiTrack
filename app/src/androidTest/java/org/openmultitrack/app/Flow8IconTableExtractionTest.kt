package org.openmultitrack.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import dalvik.system.PathClassLoader
import org.junit.Assume.assumeTrue
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Extracts the full FLOW Mix `(input_type, preset) → MS icon` table from the
 * installed Flow Mix app (`com.musicgroup.xairbt`). Run manually when updating
 * [org.openmultitrack.mixer.behringer.Flow8IconPresets]:
 *
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=org.openmultitrack.app.Flow8IconTableExtractionTest
 */
@RunWith(AndroidJUnit4::class)
class Flow8IconTableExtractionTest {
  @Ignore("Manual tool — requires initialized Flow Mix XAIRClient; use to refresh Flow8IconPresets tables")
  @Test
  fun dumpPresetIconTableFromInstalledFlowMix() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val pm = context.packageManager
    assumeTrue(
      "Flow Mix (com.musicgroup.xairbt) is not installed",
      runCatching { pm.getPackageInfo(FLOW_MIX_PACKAGE, 0) }.isSuccess,
    )

    val appInfo = pm.getApplicationInfo(FLOW_MIX_PACKAGE, 0)
    val loader = PathClassLoader(appInfo.sourceDir, appInfo.nativeLibraryDir, context.classLoader)
    val clientClass = loader.loadClass("com.musicgroup.xairbt.NativeModels.XAIRClient")
    val client = clientClass.getDeclaredConstructor().newInstance()
    val getCount = clientClass.getMethod("getNumberOfInputChannelPresets", Int::class.javaPrimitiveType)
    val getIcon = clientClass.getMethod(
      "getInputChannelPresetIconIdAtIndex",
      Int::class.javaPrimitiveType,
      Int::class.javaPrimitiveType,
    )

    val lines = buildList {
      add("# type,preset,ms_icon")
      for (type in 0..6) {
        val count = runCatching { getCount.invoke(client, type) as Int }.getOrNull() ?: continue
        if (count <= 0) continue
        add("# type $type count $count")
        for (preset in 0 until count) {
          val icon = getIcon.invoke(client, type, preset) as Int
          add("$type,$preset,$icon")
        }
      }
    }
  Log.i(TAG, "\n" + lines.joinToString("\n"))
    assertThat(lines.count { it.contains(',') }).isGreaterThan(50)
  }

  private companion object {
    const val TAG = "Flow8IconTable"
    const val FLOW_MIX_PACKAGE = "com.musicgroup.xairbt"
  }
}
