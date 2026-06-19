package dev.pranav.reef

import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.UserHandle
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.test.core.app.ApplicationProvider
import dev.pranav.reef.ui.whitelist.AllowedAppsState
import dev.pranav.reef.ui.whitelist.WhitelistViewModel
import dev.pranav.reef.ui.whitelist.WhitelistedApp
import dev.pranav.reef.util.Whitelist
import io.mockk.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.Q])
class WhitelistViewModelTest {

    private lateinit var launcherApps: LauncherApps
    private lateinit var packageManager: PackageManager
    private lateinit var userHandle: UserHandle
    private val testPackageName = "dev.pranav.reef"

    @Before
    fun setUp() {
        launcherApps = mockk()
        packageManager = mockk()
        userHandle = mockk()
        mockkObject(Whitelist)

        every { Whitelist.isWhitelisted(any()) } returns false
        every { Whitelist.whitelist(any()) } just Runs
        every { Whitelist.unwhitelist(any()) } just Runs
        every { launcherApps.profiles } returns listOf(userHandle)
    }

    @Test
    fun `WhitelistedApp data class equality`() {
        val icon: ImageBitmap = mockk()
        val app1 = WhitelistedApp("com.example.app", "Test App", icon, false, userHandle)
        val app2 = WhitelistedApp("com.example.app", "Test App", icon, false, userHandle)

        assertEquals(app1, app2)
        assertEquals(app1.hashCode(), app2.hashCode())
    }

    @Test
    fun `WhitelistedApp data class inequality on different packageName`() {
        val icon: ImageBitmap = mockk()
        val app1 = WhitelistedApp("com.example.one", "App", icon, false, userHandle)
        val app2 = WhitelistedApp("com.example.two", "App", icon, false, userHandle)

        assertNotEquals(app1, app2)
    }

    @Test
    fun `WhitelistedApp data class copy with modified field`() {
        val icon: ImageBitmap = mockk()
        val app1 = WhitelistedApp("com.example.app", "Test", icon, false, userHandle)
        val app2 = app1.copy(isWhitelisted = true)

        assertTrue(app2.isWhitelisted)
        assertEquals(app1.packageName, app2.packageName)
        assertNotEquals(app1.isWhitelisted, app2.isWhitelisted)
    }

    @Test
    fun `AllowedAppsState Loading is an instance of AllowedAppsState`() {
        val state: AllowedAppsState = AllowedAppsState.Loading
        assertTrue(state is AllowedAppsState)
    }

    @Test
    fun `AllowedAppsState Success holds apps list`() {
        val apps = listOf(
            WhitelistedApp("com.a", "App A", mockk(), false, userHandle)
        )
        val state: AllowedAppsState = AllowedAppsState.Success(apps)
        assertTrue(state is AllowedAppsState.Success)
        assertEquals(1, (state as AllowedAppsState.Success).apps.size)
    }

    @Test
    fun `search filter by label`() {
        val vm = createViewModelWithApps(
            listOf("com.a", "com.b"),
            listOf("MyApp", "OtherApp")
        )

        vm.onSearchQueryChange("MyApp")
        val state = vm.uiState.value
        assertTrue(state is AllowedAppsState.Success)
        assertEquals(1, (state as AllowedAppsState.Success).apps.size)
        assertEquals("MyApp", state.apps.first().label)
    }

    @Test
    fun `search filter by packageName`() {
        val vm = createViewModelWithApps(
            listOf("com.example.alpha", "com.example.beta"),
            listOf("Alpha", "Beta")
        )

        vm.onSearchQueryChange("alpha")
        val state = vm.uiState.value
        assertTrue(state is AllowedAppsState.Success)
        assertEquals(1, (state as AllowedAppsState.Success).apps.size)
        assertEquals("com.example.alpha", state.apps.first().packageName)
    }

    @Test
    fun `empty search query shows all apps`() {
        val vm = createViewModelWithApps(
            listOf("com.one", "com.two", "com.three"),
            listOf("One", "Two", "Three")
        )

        vm.onSearchQueryChange("")
        val state = vm.uiState.value
        assertTrue(state is AllowedAppsState.Success)
        assertEquals(3, (state as AllowedAppsState.Success).apps.size)
    }

    @Test
    fun `search is case insensitive`() {
        val vm = createViewModelWithApps(
            listOf("com.myapp"),
            listOf("MyApplication")
        )

        vm.onSearchQueryChange("myapplication")
        val state = vm.uiState.value
        assertTrue(state is AllowedAppsState.Success)
        assertEquals(1, (state as AllowedAppsState.Success).apps.size)
    }

    @Test
    fun `toggleWhitelist whitelists an unwhitelisted app`() {
        val vm = createViewModelWithApps(
            listOf("com.test.pkg"),
            listOf("Test App")
        )

        vm.onSearchQueryChange("")
        var state = vm.uiState.value as AllowedAppsState.Success
        val app = state.apps.first()
        assertFalse(app.isWhitelisted)

        vm.toggleWhitelist(app)

        verify { Whitelist.whitelist("com.test.pkg") }

        state = vm.uiState.value as AllowedAppsState.Success
        val updatedApp = state.apps.first()
        assertTrue(updatedApp.isWhitelisted)
    }

    @Test
    fun `toggleWhitelist unwhitelists a whitelisted app`() {
        every { Whitelist.isWhitelisted("com.test.pkg") } returns true

        val vm = createViewModelWithApps(
            listOf("com.test.pkg"),
            listOf("Test App")
        )

        vm.onSearchQueryChange("")
        var state = vm.uiState.value as AllowedAppsState.Success
        val app = state.apps.first().copy(isWhitelisted = true)

        vm.toggleWhitelist(app)

        verify { Whitelist.unwhitelist("com.test.pkg") }

        state = vm.uiState.value as AllowedAppsState.Success
        val updatedApp = state.apps.first()
        assertFalse(updatedApp.isWhitelisted)
    }

    @Test
    fun `search returns no results for non-matching query`() {
        val vm = createViewModelWithApps(
            listOf("com.alpha"),
            listOf("Alpha")
        )

        vm.onSearchQueryChange("Zeta")
        val state = vm.uiState.value
        assertTrue(state is AllowedAppsState.Success)
        assertTrue((state as AllowedAppsState.Success).apps.isEmpty())
    }

    @Test
    fun `sorting by label`() {
        val vm = createViewModelWithApps(
            listOf("com.z", "com.a", "com.m"),
            listOf("Zeta", "Alpha", "Mu")
        )

        vm.onSearchQueryChange("")
        val state = vm.uiState.value as AllowedAppsState.Success
        val labels = state.apps.map { it.label }
        assertEquals(listOf("Alpha", "Mu", "Zeta"), labels)
    }

    private fun createViewModelWithApps(
        packages: List<String>,
        labels: List<String>
    ): WhitelistViewModel {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        val mockApps = packages.mapIndexed { index, pkg ->
            val info = mockk<android.content.pm.LauncherActivityInfo>()
            val appInfo = mockk<android.content.pm.ApplicationInfo>()
            every { info.applicationInfo } returns appInfo
            every { appInfo.packageName } returns pkg
            every { appInfo.loadLabel(packageManager) } returns labels[index]
            val drawable = BitmapDrawable(context.resources, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888))
            every { appInfo.loadIcon(packageManager) } returns drawable
            every { packageManager.getUserBadgedIcon(any(), any()) } returns drawable
            info
        }

        every { launcherApps.getActivityList(null, userHandle) } returns mockApps

        return WhitelistViewModel(launcherApps, packageManager, testPackageName)
    }
}
