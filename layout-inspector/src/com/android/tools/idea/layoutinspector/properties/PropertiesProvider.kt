/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,ndroid
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.layoutinspector.properties

import com.android.SdkConstants.ATTR_NAME
import com.android.SdkConstants.ATTR_HEIGHT
import com.android.SdkConstants.ATTR_WIDTH
import com.android.tools.idea.layoutinspector.model.ViewNode
import com.android.tools.idea.layoutinspector.properties.PropertySection.DIMENSION
import com.android.tools.idea.layoutinspector.properties.PropertySection.VIEW
import com.android.tools.idea.layoutinspector.resource.ResourceLookup
import com.android.tools.layoutinspector.proto.LayoutInspectorProto.Property.Type
import com.android.tools.property.panel.api.PropertiesTable
import com.google.common.collect.Table
import com.google.common.util.concurrent.Futures
import java.util.concurrent.Future

// Constants for fabricated internal properties
const val NAMESPACE_INTERNAL = "internal"
const val ATTR_X = "x"
const val ATTR_Y = "y"

/**
 * A [PropertiesProvider] provides properties to registered listeners..
 */
interface PropertiesProvider {

  /**
   * Listeners for [PropertiesProvider] results.
   */
  val resultListeners: MutableList<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>

  /**
   * Requests properties for the specified [view].
   *
   * This is potentially an asynchronous request. The associated [InspectorPropertiesModel]
   * is notified when the table is ready.
   */
  fun requestProperties(view: ViewNode): Future<*>
}

object EmptyPropertiesProvider : PropertiesProvider {

  override val resultListeners = mutableListOf<(PropertiesProvider, ViewNode, PropertiesTable<InspectorPropertyItem>) -> Unit>()

  override fun requestProperties(view: ViewNode): Future<*> {
    return Futures.immediateFuture(null)
  }
}

/**
 * Add a few fabricated internal attributes.
 */
internal fun addInternalProperties(table: Table<String, String, InspectorPropertyItem>, view: ViewNode, lookup: ResourceLookup) {
  add(table, InspectorPropertyItem(NAMESPACE_INTERNAL, ATTR_NAME, Type.STRING, view.qualifiedName, VIEW, null, view, lookup))
  add(table, InspectorPropertyItem(NAMESPACE_INTERNAL, ATTR_X, Type.DIMENSION, view.x.toString(), DIMENSION, null, view, lookup))
  add(table, InspectorPropertyItem(NAMESPACE_INTERNAL, ATTR_Y, Type.DIMENSION, view.y.toString(), DIMENSION, null, view, lookup))
  add(table, InspectorPropertyItem(NAMESPACE_INTERNAL, ATTR_WIDTH, Type.DIMENSION, view.width.toString(), DIMENSION, null, view, lookup))
  add(table, InspectorPropertyItem(NAMESPACE_INTERNAL, ATTR_HEIGHT, Type.DIMENSION, view.height.toString(), DIMENSION, null, view, lookup))
}

private fun add(table: Table<String, String, InspectorPropertyItem>, item: InspectorPropertyItem) {
  table.put(item.namespace, item.name, item)
}
