package com.fulfilment.application.monolith.stores;

public class StoreEvent {

  public enum Action {
    CREATED,
    UPDATED
  }

  public final Store store;
  public final Action action;

  public StoreEvent(Store store, Action action) {
    this.store = store;
    this.action = action;
  }
}
