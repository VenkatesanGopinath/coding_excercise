package com.fulfilment.application.monolith.stores;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LegacyStoreManagerGateway {

  private static final Logger LOG = Logger.getLogger(LegacyStoreManagerGateway.class);

  /**
   * Observes StoreEvent after the transaction has successfully committed.
   * This guarantees the legacy system is only notified of confirmed DB changes.
   */
  public void onStoreEvent(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) StoreEvent event) {
    LOG.infof("Received StoreEvent [action=%s, store=%s] after commit", event.action, event.store.name);
    if (event.action == StoreEvent.Action.CREATED) {
      createStoreOnLegacySystem(event.store);
    } else {
      updateStoreOnLegacySystem(event.store);
    }
  }

  public void createStoreOnLegacySystem(Store store) {
    LOG.infof("Notifying legacy system: store created [name=%s]", store.name);
    writeToFile(store);
  }

  public void updateStoreOnLegacySystem(Store store) {
    LOG.infof("Notifying legacy system: store updated [name=%s]", store.name);
    writeToFile(store);
  }

  private void writeToFile(Store store) {
    try {
      // Step 1: Create a temporary file
      Path tempFile = Files.createTempFile(store.name, ".txt");
      LOG.debugf("Temporary file created at: %s", tempFile);

      // Step 2: Write data to the temporary file
      String content =
          "Store created. [ name ="
              + store.name
              + " ] [ items on stock ="
              + store.quantityProductsInStock
              + "]";
      Files.write(tempFile, content.getBytes());
      LOG.debugf("Data written to temporary file: %s", content);

      // Step 3: Optionally, read the data back to verify
      String readContent = new String(Files.readAllBytes(tempFile));
      LOG.debugf("Data read from temporary file: %s", readContent);

      // Step 4: Delete the temporary file when done
      Files.delete(tempFile);
      LOG.debug("Temporary file deleted");

    } catch (Exception e) {
      LOG.errorf(e, "Failed to write store [name=%s] to legacy system", store.name);
    }
  }
}
