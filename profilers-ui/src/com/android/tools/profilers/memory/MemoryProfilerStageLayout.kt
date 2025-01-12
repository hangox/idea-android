/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.profilers.memory

import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_HORIZONTAL_BORDERS
import com.android.tools.adtui.common.AdtUiUtils.DEFAULT_VERTICAL_BORDERS
import com.android.tools.adtui.model.AspectObserver
import com.android.tools.profilers.CloseButton
import com.android.tools.profilers.ProfilerLayout.createToolbarLayout
import com.android.tools.profilers.stacktrace.LoadingPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.ui.JBEmptyBorder
import java.awt.BorderLayout
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel


/**
 * This class abstracts away the laying out of (@link MemoryProfilerStageView},
 * responding differently to requests showing/hiding the heap-dump.
 * The legacy one uses JBSplitters to share the heap-dump view with the main timeline.
 * The new one uses a CardLayout that swaps out the timeline for the heap-dump view.
 * This is a temporary solution for maintaining both the new and legacy UIs.
 * The right solution in the long run should be separating the heap-dump out into its own stage.
 */
abstract class MemoryProfilerStageLayout(val capturePanel: CapturePanel,
                                         private val makeLoadingPanel: () -> LoadingPanel) {
  abstract val component: JComponent
  val toolbar = JPanel(createToolbarLayout())

  protected var loadingPanel: LoadingPanel? = null

  var isLoadingUiVisible: Boolean
    get() = loadingPanel != null
    set(isShown) {
      val currentLoadingPanel = loadingPanel
      when {
        currentLoadingPanel == null && isShown -> {
          val newLoadingPanel = makeLoadingPanel()
          loadingPanel = newLoadingPanel
          newLoadingPanel.startLoading()
          showLoadingView(newLoadingPanel)
        }
        currentLoadingPanel != null && !isShown -> {
          currentLoadingPanel.stopLoading()
          hideLoadingView(currentLoadingPanel)
          loadingPanel = null
        }
      }
    }
  abstract var isShowingCaptureUi: Boolean

  protected abstract fun showLoadingView(loadingPanel: LoadingPanel)
  protected abstract fun hideLoadingView(loadingPanel: LoadingPanel)

  // TODO: Below are just for legacy tests. Remove them once fully migrated to new Ui
  abstract val chartCaptureSplitter: Splitter
  abstract val mainSplitter: Splitter
}

internal class LegacyMemoryProfilerStageLayout(timelineView: JComponent?,
                                               capturePanel: CapturePanel,
                                               makeLoadingPanel: () -> LoadingPanel)
      : MemoryProfilerStageLayout(capturePanel, makeLoadingPanel) {
  private val instanceDetailsSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    isOpaque = true
    firstComponent = capturePanel.classSetView.component
    secondComponent = capturePanel.instanceDetailsView.component
  }
  override val chartCaptureSplitter = JBSplitter(true).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = timelineView
  }
  override val mainSplitter = JBSplitter(false).apply {
    border = DEFAULT_VERTICAL_BORDERS
    firstComponent = chartCaptureSplitter
    secondComponent = instanceDetailsSplitter
    proportion = .6f
  }

  override var isShowingCaptureUi = false
    set(isShown) {
      field = isShown
      chartCaptureSplitter.secondComponent = if (isShown) capturePanel.component else null
    }

  override fun showLoadingView(loadingPanel: LoadingPanel) {
    chartCaptureSplitter.secondComponent = loadingPanel.component
  }

  override fun hideLoadingView(loadingPanel: LoadingPanel) {
    chartCaptureSplitter.secondComponent = null
  }

  override val component: JComponent
    get() = mainSplitter
}