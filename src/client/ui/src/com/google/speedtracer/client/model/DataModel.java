/*
 * Copyright 2008 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.speedtracer.client.model;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.speedtracer.client.util.JSOArray;
import com.google.speedtracer.client.util.JSON;
import com.google.speedtracer.client.util.JsIntegerMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for getting different kinds of Models. Each returned Model exposes
 * subscriptions for event callbacks.
 * 
 * Also defines Universal base time, which can be calibrated by subclasses only.
 */
public abstract class DataModel implements HintletEngineHost.HintListener {

  /**
   * API for interacting with a JSO that drives our DataModel. This JSO can be
   * one of the following:
   * 
   * 1. NPObject created by our plugin for interacting and receiving data from
   * other browsers.
   * 
   * 2. JSO wired up to a the data_loader content script for loading saved data
   * dumps.
   * 
   * 3. JSO wired up to the devtools extensions API for monitoring Chrome.
   * 
   * 4. An empty JSO when running in in Mock mode via the MockModel.
   */
  public static class DataInstance extends JavaScriptObject {
    protected DataInstance() {
    }

    public final native void load(DataModel model) /*-{
      var callback = {
        // This gets called by everyone else (file loader and devtools data instances).
        onEventRecord: function(record) {
          model.@com.google.speedtracer.client.model.DataModel::onEventRecord(Lcom/google/speedtracer/client/model/EventRecord;)(record);
        },

        // This gets called from the plugin.
        onEventRecordString: function(sequence, recordString) {
          var data = JSON.parse(recordString);
          // Populate the sequence field in the record so that we don't have
          // to keep passing it out of band along with the record.
          data.sequence = sequence;
          model.@com.google.speedtracer.client.model.DataModel::onEventRecord(Lcom/google/speedtracer/client/model/EventRecord;)(data);
        }
      };
      this.Load(callback);
    }-*/;

    public final native void resumeMonitoring() /*-{
      this.Resume();
    }-*/;

    public final native void setProfilingOptions(boolean enableStackTraces,
        boolean enableCpuProfiling) /*-{
      this.SetOptions(enableStackTraces, enableCpuProfiling);
    }-*/;

    public final native void stopMonitoring() /*-{
      this.Stop();
    }-*/;

    public final native void unload()/*-{
      this.Unload();
    }-*/;
  }

  /**
   * Wrapper objects to proxy callbacks to correct handler.
   */
  public interface EventCallbackProxy {
    void onEventRecord(EventRecord data);
  }

  /**
   * Wrapper interface for a class that provides callbacks for an event.
   */
  public interface EventCallbackProxyProvider {
    EventCallbackProxy getEventCallback(EventRecord data);
  }

  /**
   * Provides a deferred-binding factory for creating a {@link DataModel}.
   */
  public static class Provider {

    /**
     * Creates a {@link DataModel} based on an opaque handle.
     * 
     * @param tabDescription info about the tab represented by this model
     * @param dataInstance an opaque handle to model functionality.
     * @return a model
     */
    public static DataModel createModel(TabDescription tabDescription,
        DataInstance dataInstance) {
      final DataModel model = GWT.create(DataModel.class);
      model.setTabDescription(tabDescription);
      model.dataInstance = dataInstance;
      model.bind(tabDescription, dataInstance);
      return model;
    }

    private Provider() {
    }
  }

  protected JSOArray<String> traceDataCopy = JSOArray.create();

  private DataInstance dataInstance;

  private final List<EventCallbackProxyProvider> eventModels = new ArrayList<EventCallbackProxyProvider>();

  private JsIntegerMap<EventRecord> eventRecordMap = JsIntegerMap.create();

  private final HintletEngineHost hintletEngineHost;

  private final NetworkResourceModel networkResourceModel;

  private TabDescription tabDescription;

  private final TabChangeModel tabNavigationModel;

  private final UiEventModel uiEventModel;

  protected DataModel() {
    this.hintletEngineHost = new HintletEngineHost();
    this.networkResourceModel = new NetworkResourceModel();
    this.uiEventModel = new UiEventModel();
    this.tabNavigationModel = new TabChangeModel();
    this.hintletEngineHost.addHintListener(this);
    eventModels.add(uiEventModel);
    eventModels.add(networkResourceModel);
    eventModels.add(tabNavigationModel);
  }

  /**
   * Clears the store of saved EventRecords. Relies on v8 garbage collecting
   * appropriately.
   */
  public void clear() {
    // Replace the existing map.
    eventRecordMap = JsIntegerMap.create();

    // Replace the backing String store.
    traceDataCopy = JSOArray.create();
  }

  /**
   * Retrieves an EventRecord by sequence number.
   * 
   * @param sequence A sequence number to look for.
   * @return the record with the specified sequence number.
   */
  public EventRecord findEventRecord(int sequence) {
    return eventRecordMap.get(sequence);
  }

  public void fireOnEventRecord(EventRecord data) {
    UncaughtExceptionHandler ueh = GWT.getUncaughtExceptionHandler();
    if (ueh != null) {
      try {
        fireOnEventRecordImpl(data);
      } catch (Exception ex) {
        ueh.onUncaughtException(ex);
      }
    } else {
      fireOnEventRecordImpl(data);
    }
  }

  /**
   * Returns the opaque handle associated with this DataModel.
   * 
   * @return the opaque handle associated with this DataModel.
   */
  public DataInstance getDataInstance() {
    return dataInstance;
  }

  public JsIntegerMap<EventRecord> getEventRecordMap() {
    return eventRecordMap;
  }

  /**
   * Gets the sub-model for Hintlets.
   * 
   * @return a HintletEngineHost which exposes API for receiving hints.
   */
  public HintletEngineHost getHintletEngineHost() {
    return hintletEngineHost;
  }

  /**
   * Gets the sub-model for network resource related events.
   * 
   * @return a model
   */
  public NetworkResourceModel getNetworkResourceModel() {
    return networkResourceModel;
  }

  /**
   * Gets info about the tab being monitored. Clients SHOULD NOT mutate the
   * object.
   * 
   * @return a shared instance of tab info
   */
  public TabDescription getTabDescription() {
    return tabDescription;
  }

  public TabChangeModel getTabNavigationModel() {
    return tabNavigationModel;
  }

  /**
   * Gets the sub-model for DOM events.
   * 
   * @return a model
   */
  public UiEventModel getUiEventModel() {
    return uiEventModel;
  }

  /**
   * Data sources that drive the DataModel drive it through this single function
   * which passes in an {@link EventRecord}.
   * 
   * @param record the timeline {@link EventRecord}
   */
  public void onEventRecord(EventRecord record) {
    // Keep a copy of the String for saving later.
    traceDataCopy.push(JSON.stringify(record));
    eventRecordMap.put(record.getSequence(), record);
    fireOnEventRecord(record);
  }

  public void onHint(HintRecord hintlet) {
    saveHintRecord(hintlet);
  }

  public abstract void resumeMonitoring(int tabId);

  public abstract void saveRecords(JSOArray<String> visitedUrls);

  public abstract void stopMonitoring();

  /**
   * Do not call this directly. Provides an abstract way to tie a tab and an
   * opaque handle to an instance of a data model.
   * 
   * @param tabDescription info about the tab
   * @param dataInstance an opaque handle containing model specific
   *          functionality
   */
  protected abstract void bind(TabDescription tabDescription,
      DataInstance dataInstance);

  protected void setTabDescription(TabDescription tabDescription) {
    this.tabDescription = tabDescription;
  }

  private void fireOnEventRecordImpl(EventRecord data) {
    // Forward to the hintlet engine.
    hintletEngineHost.addRecord(data);

    EventCallbackProxy callback = null;
    for (int i = 0, n = eventModels.size(); i < n; i++) {
      callback = eventModels.get(i).getEventCallback(data);
      if (callback != null) {
        break;
      }
    }
    assert (callback != null);
    callback.onEventRecord(data);
  }

  @SuppressWarnings("unused")
  private JSOArray<String> getTraceCopy() {
    return traceDataCopy;
  }

  /**
   * When a new hint record arrives, update the association between the UI
   * record and the hint record.
   */
  private void saveHintRecord(HintRecord hintletRecord) {
    int sequence = hintletRecord.getRefRecord();
    if (sequence < 0) {
      return;
    }
    EventRecord rec = eventRecordMap.get(sequence);
    if (rec != null) {
      rec.addHint(hintletRecord);
    }
  }
}