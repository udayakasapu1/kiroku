package com.kiroku.app.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.AppPrefs
import com.kiroku.app.data.CategoryRepository
import com.kiroku.app.data.FolderSuggestion
import com.kiroku.app.data.FolderSuggestions
import kotlinx.coroutines.launch

/** First launch: pick suggested folders and/or add your own. */
class OnboardingViewModel(
    private val categories: CategoryRepository,
    private val prefs: AppPrefs,
) : ViewModel() {

    /** Suggestions plus any folders typed in, in display order. */
    val options = mutableStateListOf<FolderSuggestion>().apply { addAll(FolderSuggestions.starter) }
    val selected = mutableStateListOf<FolderSuggestion>()

    var saving by mutableStateOf(false)
        private set

    fun toggle(option: FolderSuggestion) {
        if (option in selected) selected.remove(option) else selected.add(option)
    }

    fun addCustom(name: String, icon: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        val existing = options.firstOrNull { it.name.equals(clean, ignoreCase = true) }
        if (existing != null) {
            if (existing !in selected) selected.add(existing)
            return
        }
        val option = FolderSuggestion(clean, icon)
        options.add(0, option)
        selected.add(option)
    }

    /** Creates the chosen folders (in the order shown) and marks setup as done. */
    fun finish(onDone: () -> Unit) {
        if (saving) return
        saving = true
        val chosen = options.filter { it in selected }
        viewModelScope.launch {
            categories.createAll(chosen)
            prefs.onboarded = true
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer) = viewModelFactory {
            initializer { OnboardingViewModel(container.categories, container.prefs) }
        }
    }
}
