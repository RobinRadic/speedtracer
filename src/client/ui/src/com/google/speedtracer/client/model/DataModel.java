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
import com.google.gwt.core.client.GWT.UncaughtExceptionHandler;
import com.google.gwt.coreext.client.JSOArray;
import com.google.gwt.coreext.client.JSON;
import com.google.gwt.coreext.client.JsIntegerMap;
import com.google.speedtracer.client.ClientConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Interface for getting different kinds of Models. Each returned Model exposes
 * subscriptions for event callbacks.
 * 
 * Also defines Universal base time, which can be calibrated by subclasses only.
 */
public class DataModel implements HintletInterface.HintListener,
    EventRecordLookup, DataInstance.DataListener {
  /**
   * Wrapper objects to proxy callbacks to correct handler.
   */
  public interface EventRecordHandler {
    void onEventRecord(EventRecord data);
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
      final DataModel model = new DataModel(dataInstance);
      model.setTabDescription(tabDescription);
      model.initialize();
      return model;
    }

    private Provider() {
    }
  }

  protected JSOArray<String> traceDataCopy = JSOArray.create();

  private final DataInstance dataInstance;

  private final List<EventRecordHandler> eventModels = new ArrayList<EventRecordHandler>();

  private JsIntegerMap<EventRecord> eventRecordMap = JsIntegerMap.create();

  private final HintletEngineHost hintletEngineHost;

  private final NetworkResourceModel networkResourceModel;

  private final JavaScriptProfileModel profileModel;

  private TabDescription tabDescription;

  private final TabChangeModel tabNavigationModel;

  private final UiEventModel uiEventModel;

  protected DataModel(DataInstance dataInstance) {
    this.dataInstance = dataInstance;
    this.networkResourceModel = new NetworkResourceModel();
    this.uiEventModel = new UiEventModel();
    this.tabNavigationModel = new TabChangeModel();
    this.profileModel = new JavaScriptProfileModel(this);
    this.hintletEngineHost = new HintletEngineHost();
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
   * Gets the sub-model for JavaScript profiling data.
   * 
   * @return the sub-model for JavaScript profiling data.
   */
  public JavaScriptProfileModel getJavaScriptProfileModel() {
    return profileModel;
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

  public JSOArray<String> getTraceCopy() {
    return traceDataCopy;
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

  public void resumeMonitoring(int tabId) {
    getDataInstance().resumeMonitoring();
  }

  public void stopMonitoring() {
    getDataInstance().stopMonitoring();
  }

  /**
   * Hook up the various models. In the general case, we want all of them, but
   * subclasses can pick and choose which models make sense for them.
   */
  protected void initialize() {
    // Listen to the hintlet engine.
    hintletEngineHost.addHintListener(this);

    // Add models to our dispatch list,.
    if (ClientConfig.isDebugMode()) {
      // NOTE: the order of adding models matters, some modify the record object
      eventModels.add(0, new BreakyWorkerHost(this, hintletEngineHost));
    }
    eventModels.add(uiEventModel);
    eventModels.add(networkResourceModel);
    eventModels.add(tabNavigationModel);
    eventModels.add(profileModel);
    eventModels.add(hintletEngineHost);
  }

  protected void setTabDescription(TabDescription tabDescription) {
    this.tabDescription = tabDescription;
  }

  private void fireOnEventRecordImpl(EventRecord data) {
    for (int i = 0, n = eventModels.size(); i < n; i++) {
      eventModels.get(i).onEventRecord(data);
    }
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
