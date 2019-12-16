/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.property.panel.impl.table

import com.android.tools.property.ptable2.PTable
import com.android.tools.property.ptable2.PTableCellRenderer
import com.android.tools.property.ptable2.PTableColumn
import com.android.tools.property.ptable2.PTableItem
import javax.swing.JComponent

/**
 * Table cell renderer for the name of a [PTableItem].
 */
class DefaultNameTableCellRenderer : PTableCellRenderer {
  private val component = DefaultNameComponent {}

  override fun getEditorComponent(table: PTable, item: PTableItem, column: PTableColumn, depth: Int,
                                  isSelected: Boolean, hasFocus: Boolean, isExpanded: Boolean): JComponent {
    return component.setUpItem(table, item, depth, isSelected, hasFocus, isExpanded)
  }
}
