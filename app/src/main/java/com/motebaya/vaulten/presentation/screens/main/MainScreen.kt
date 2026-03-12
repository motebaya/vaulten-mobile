package com.motebaya.vaulten.presentation.screens.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.motebaya.vaulten.presentation.components.FloatingBottomNavBar
import com.motebaya.vaulten.presentation.screens.dashboard.DashboardScreen
import com.motebaya.vaulten.presentation.screens.platform.PlatformScreen
import com.motebaya.vaulten.presentation.screens.settings.SettingsScreen
import com.motebaya.vaulten.presentation.screens.support.SupportScreen
import kotlinx.coroutines.launch

/**
 * Main screen that hosts all 4 primary screens in a HorizontalPager.
 *
 * Features:
 * - Swipe navigation between Credentials, Platforms, Settings, Support
 * - Floating pill bottom nav bar (hides on scroll down, shows on scroll up)
 * - FAB for Credentials & Platforms pages (managed by each screen, visibility passed down)
 * - Lock icon in top-right of each page (via PageScaffold)
 * - Last page (Support, index 3) swipe right-to-left wraps to Credentials via clone page
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(
    onNavigateToAdd: () -> Unit,
    onExportVault: () -> Unit,
    onImportVault: () -> Unit,
    onChangePin: () -> Unit,
    onForgotPin: () -> Unit,
    onLock: () -> Unit
) {
    val scope = rememberCoroutineScope()
    // 5 pages: 0=Credentials, 1=Platforms, 2=Settings, 3=Support, 4=clone of Credentials (for loop)
    val pageCount = 5
    val pagerState = rememberPagerState(initialPage = 0) { pageCount }

    // Track scroll direction for nav bar / FAB visibility
    var isNavVisible by remember { mutableStateOf(true) }
    var scrollDelta by remember { mutableFloatStateOf(0f) }

    // Nested scroll connection to detect scroll direction from child LazyColumns/ScrollStates
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                // Accumulate scroll delta; threshold avoids jitter
                scrollDelta += delta
                if (scrollDelta > 30f) {
                    isNavVisible = true
                    scrollDelta = 0f
                } else if (scrollDelta < -30f) {
                    isNavVisible = false
                    scrollDelta = 0f
                }
                return Offset.Zero // Don't consume any scroll
            }
        }
    }

    // Handle circular pager: when settling on page 4 (clone), jump to page 0
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .collect { isScrolling ->
                if (!isScrolling && pagerState.currentPage == 4) {
                    pagerState.scrollToPage(0)
                }
            }
    }

    // Map pager page to nav index (page 4 maps to 0 for the clone)
    val currentNavIndex by remember {
        derivedStateOf {
            val page = pagerState.currentPage
            if (page >= 4) 0 else page
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        // HorizontalPager with 5 pages (4 real + 1 clone for loop)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondBoundsPageCount = 1
        ) { page ->
            // Map page 4 -> same content as page 0 (Credentials)
            when (if (page == 4) 0 else page) {
                0 -> DashboardScreen(
                    onNavigateToAdd = onNavigateToAdd,
                    onForgotPin = onForgotPin,
                    onLock = onLock,
                    isFabVisible = isNavVisible
                )
                1 -> PlatformScreen(
                    onLock = onLock,
                    onForgotPin = onForgotPin,
                    isFabVisible = isNavVisible
                )
                2 -> SettingsScreen(
                    onExportVault = onExportVault,
                    onImportVault = onImportVault,
                    onChangePin = onChangePin,
                    onLock = onLock
                )
                3 -> SupportScreen(
                    onLock = onLock
                )
            }
        }

        // Floating Bottom Nav Bar
        FloatingBottomNavBar(
            selectedIndex = currentNavIndex,
            onItemSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
            isVisible = isNavVisible,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        )
    }
}
