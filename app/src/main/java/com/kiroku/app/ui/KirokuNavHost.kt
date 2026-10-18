package com.kiroku.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.kiroku.app.AppContainer
import com.kiroku.app.AppPrefs
import com.kiroku.app.ui.add.AddEvent
import com.kiroku.app.ui.add.AddLinkSheet
import com.kiroku.app.ui.add.AddLinkViewModel
import com.kiroku.app.ui.components.Space
import com.kiroku.app.ui.detail.DetailScreen
import com.kiroku.app.ui.detail.DetailViewModel
import com.kiroku.app.ui.folder.FolderScreen
import com.kiroku.app.ui.folder.FolderViewModel
import com.kiroku.app.ui.folders.ManageFoldersScreen
import com.kiroku.app.ui.folders.ManageFoldersViewModel
import com.kiroku.app.ui.home.HomeScreen
import com.kiroku.app.ui.home.HomeViewModel
import com.kiroku.app.ui.note.NoteEditorScreen
import com.kiroku.app.ui.note.NoteEditorViewModel
import com.kiroku.app.ui.notes.NotesScreen
import com.kiroku.app.ui.notes.NotesViewModel
import com.kiroku.app.ui.onboarding.OnboardingScreen
import com.kiroku.app.ui.onboarding.OnboardingViewModel
import kotlinx.coroutines.launch

private const val LINKS_HOME = "home"
private const val NOTES_HOME = "notes?archived={archived}"
private const val FOLDER = "folder/{key}"
private const val LINK = "link/{id}"
private const val NOTE = "note/{id}"
private const val MANAGE_FOLDERS = "folders"
private const val ONBOARDING = "onboarding"

/** Go to a space's home, replacing everything on the back stack. */
private fun NavHostController.openSpace(space: Space) {
    navigate(if (space == Space.NOTES) "notes" else LINKS_HOME) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

@Composable
fun KirokuNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val prefs = container.prefs
    val start = remember { if (prefs.lastSpace == AppPrefs.SPACE_NOTES) NOTES_HOME else LINKS_HOME }

    // First launch: let the user set up their folders (skipped if folders already exist,
    // e.g. after updating from a version with built-in folders).
    LaunchedEffect(Unit) {
        if (!prefs.onboarded) {
            if (container.categories.count() > 0) {
                prefs.onboarded = true
            } else {
                nav.navigate(ONBOARDING) {
                    popUpTo(nav.graph.id) { inclusive = true }
                }
            }
        }
    }

    fun switchSpace(space: Space) {
        prefs.lastSpace = if (space == Space.NOTES) AppPrefs.SPACE_NOTES else AppPrefs.SPACE_LINKS
        nav.openSpace(space)
    }

    // One "save a link" sheet for the whole app, so a share can open it on any screen.
    val addVm: AddLinkViewModel = viewModel(factory = AddLinkViewModel.factory(container))
    LaunchedEffect(addVm) { addVm.listenForShares() }
    LaunchedEffect(addVm) {
        addVm.events.collect { event ->
            when (event) {
                is AddEvent.Saved -> launch {
                    val result = snackbar.showSnackbar(
                        message = "Saved to ${event.folderLabel}. Fetching details…",
                        actionLabel = "View",
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        val current = nav.currentBackStackEntry
                        val alreadyThere = current?.destination?.route == FOLDER &&
                            current.arguments?.getString("key") == event.folderKey
                        if (!alreadyThere) nav.navigate("folder/${event.folderKey}")
                    }
                }
                is AddEvent.OpenLink -> nav.navigate("link/${event.id}")
                is AddEvent.OpenNote -> nav.navigate("note/${event.id}")
                is AddEvent.Message -> launch { snackbar.showSnackbar(event.text) }
            }
        }
    }

    fun showArchived(what: String, undo: suspend () -> Unit) {
        scope.launch {
            val result = snackbar.showSnackbar(
                message = "$what archived",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo()
        }
    }

    NavHost(navController = nav, startDestination = start) {
        composable(ONBOARDING) {
            OnboardingScreen(
                vm = viewModel(factory = OnboardingViewModel.factory(container)),
                onDone = { nav.openSpace(Space.LINKS) },
            )
        }
        composable(LINKS_HOME) {
            HomeScreen(
                vm = viewModel(factory = HomeViewModel.factory(container)),
                snackbar = snackbar,
                onSwitchSpace = ::switchSpace,
                onOpenFolder = { key -> nav.navigate("folder/$key") },
                onOpenLink = { id -> nav.navigate("link/$id") },
                onManageFolders = { nav.navigate(MANAGE_FOLDERS) },
                onAddLink = { addVm.open(null) },
            )
        }
        composable(
            route = NOTES_HOME,
            arguments = listOf(
                navArgument("archived") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) {
            NotesScreen(
                vm = viewModel(factory = NotesViewModel.factory(container)),
                snackbar = snackbar,
                onSwitchSpace = ::switchSpace,
                onOpenNote = { id -> nav.navigate("note/$id") },
                onNewNote = { nav.navigate("note/${NoteEditorViewModel.NEW}") },
                onOpenArchive = { nav.navigate("notes?archived=true") },
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = FOLDER,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) {
            FolderScreen(
                vm = viewModel(factory = FolderViewModel.factory(container)),
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onOpenLink = { id -> nav.navigate("link/$id") },
                onAddLink = { category -> addVm.open(category) },
            )
        }
        composable(MANAGE_FOLDERS) {
            ManageFoldersScreen(
                vm = viewModel(factory = ManageFoldersViewModel.factory(container)),
                onBack = { nav.popBackStack() },
            )
        }
        composable(
            route = LINK,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            DetailScreen(
                vm = viewModel(factory = DetailViewModel.factory(container)),
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onArchived = { id -> showArchived("Link") { container.links.unarchive(id) } },
            )
        }
        composable(
            route = NOTE,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            NoteEditorScreen(
                vm = viewModel(factory = NoteEditorViewModel.factory(container)),
                snackbar = snackbar,
                onBack = { nav.popBackStack() },
                onArchived = { id -> showArchived("Note") { container.notes.unarchive(id) } },
            )
        }
    }

    if (addVm.sheet.visible) {
        AddLinkSheet(addVm)
    }
}
