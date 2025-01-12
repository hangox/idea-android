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
package com.android.tools.idea.sqlite

import com.android.tools.idea.appinspection.inspector.api.AppInspectorClient
import com.android.tools.idea.appinspection.inspector.api.AppInspectorJar
import com.android.tools.idea.appinspection.inspector.ide.AppInspectionIdeServices
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTab
import com.android.tools.idea.appinspection.inspector.ide.AppInspectorTabProvider
import com.android.tools.idea.sqlite.controllers.DatabaseInspectorController.SavedUiState
import com.android.tools.idea.sqlite.databaseConnection.live.LiveDatabaseConnection
import com.android.tools.idea.sqlite.databaseConnection.live.handleError
import com.android.tools.idea.sqlite.model.SqliteDatabaseId
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.EdtExecutorService
import org.jetbrains.ide.PooledThreadExecutor
import javax.swing.JComponent

class DatabaseInspectorTabProvider : AppInspectorTabProvider {
  override val inspectorId = "androidx.sqlite.inspection"
  override val displayName = "Database Inspector"
  override val inspectorAgentJar = AppInspectorJar(
    name = "sqlite-inspection.jar",
    developmentDirectory = "../../prebuilts/tools/common/app-inspection/androidx/sqlite/",
    releaseDirectory = "plugins/android/resources/app-inspection/"
  )

  private var savedState: SavedUiState? = null

  override fun isApplicable(): Boolean {
    return DatabaseInspectorFlagController.isFeatureEnabled
  }

  override fun createTab(
    project: Project,
    messenger: AppInspectorClient.CommandMessenger,
    ideServices: AppInspectionIdeServices
  ): AppInspectorTab {
    return object : AppInspectorTab {
      private val taskExecutor = PooledThreadExecutor.INSTANCE
      private val errorsSideChannel = createErrorSideChannel(project)
      private val databaseInspectorProjectService = DatabaseInspectorProjectService.getInstance(project)
      private val openDatabase: (SqliteDatabaseId, LiveDatabaseConnection) -> Unit = { databaseId, databaseConnection ->
        databaseInspectorProjectService.openSqliteDatabase(databaseId, databaseConnection)
      }

      private val handleError: (String) -> Unit = { databaseInspectorProjectService.handleError(it, null) }
      private val onDatabasePossiblyChanged: () -> Unit = { databaseInspectorProjectService.databasePossiblyChanged() }
      private val onDatabaseClosed: (SqliteDatabaseId) -> Unit = {
        databaseId -> databaseInspectorProjectService.handleDatabaseClosed(databaseId)
      }

      override val client = DatabaseInspectorClient(
        messenger,
        project,
        handleError,
        openDatabase,
        onDatabasePossiblyChanged,
        onDatabaseClosed,
        taskExecutor,
        errorsSideChannel
      )

      override val component: JComponent = databaseInspectorProjectService.sqliteInspectorComponent

      val databaseInspectorClientCommands = object : DatabaseInspectorClientCommandsChannel {
        override fun keepConnectionsOpen(keepOpen: Boolean) = client.keepConnectionsOpen(keepOpen)
      }

      init {
        databaseInspectorProjectService.startAppInspectionSession(savedState, databaseInspectorClientCommands, ideServices)
        client.startTrackingDatabaseConnections()
        client.addServiceEventListener(object : AppInspectorClient.ServiceEventListener {
          override fun onDispose() {
            savedState = databaseInspectorProjectService.stopAppInspectionSession()
          }
        }, EdtExecutorService.getInstance())
      }
    }
  }
}

fun createErrorSideChannel(project: Project) : ErrorsSideChannel = { command, errorResponse ->
  handleError(project, command, errorResponse.content, logger<DatabaseInspectorMessenger>())
}

/**
 * Interface used to send commands to on-device inspector
 */
interface DatabaseInspectorClientCommandsChannel {
  fun keepConnectionsOpen(keepOpen: Boolean): ListenableFuture<Boolean?>
}