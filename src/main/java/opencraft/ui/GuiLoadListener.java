package opencraft.ui;

import com.labymedia.ultralight.UltralightView;
import com.labymedia.ultralight.databind.Databind;
import com.labymedia.ultralight.databind.DatabindConfiguration;
import com.labymedia.ultralight.javascript.JavascriptContext;
import com.labymedia.ultralight.javascript.JavascriptContextLock;
import com.labymedia.ultralight.javascript.JavascriptGlobalContext;
import com.labymedia.ultralight.javascript.JavascriptObject;
import com.labymedia.ultralight.javascript.JavascriptValue;
import com.labymedia.ultralight.plugin.loading.UltralightLoadListener;

/** Injects {@link MenuBridge} into each Ultralight page as {@code window.opencraft}. */
final class GuiLoadListener implements UltralightLoadListener {
  private final UltralightView view;
  private final MenuBridge bridge;
  private final Databind databind;

  GuiLoadListener(UltralightView view, MenuBridge bridge) {
    this.view = view;
    this.bridge = bridge;
    this.databind =
        new Databind(
            DatabindConfiguration.builder()
                .contextProviderFactory(new ViewContextProvider.Factory(view))
                .build());
  }

  /** {@inheritDoc} */
  @Override
  public void onBeginLoading(long frameId, boolean isMainFrame, String url) {}

  /** {@inheritDoc} */
  @Override
  public void onFinishLoading(long frameId, boolean isMainFrame, String url) {}

  /** {@inheritDoc} */
  @Override
  public void onFailLoading(
      long frameId,
      boolean isMainFrame,
      String url,
      String description,
      String errorDomain,
      int errorCode) {
    System.err.println(
        "Ultralight failed to load "
            + url
            + ": "
            + description
            + " ("
            + errorDomain
            + "/"
            + errorCode
            + ")");
  }

  /** {@inheritDoc} */
  @Override
  public void onUpdateHistory() {}

  /** {@inheritDoc} */
  @Override
  public void onWindowObjectReady(long frameId, boolean isMainFrame, String url) {
    injectBridge(isMainFrame);
  }

  /** {@inheritDoc} */
  @Override
  public void onDOMReady(long frameId, boolean isMainFrame, String url) {
    injectBridge(isMainFrame);
  }

  private void injectBridge(boolean isMainFrame) {
    if (!isMainFrame) {
      return;
    }
    try (JavascriptContextLock lock = view.lockJavascriptContext()) {
      JavascriptContext context = lock.getContext();
      JavascriptGlobalContext globalContext = context.getGlobalContext();
      JavascriptObject globalObject = globalContext.getGlobalObject();
      JavascriptValue jsBridge = databind.getConversionUtils().toJavascript(context, bridge);
      globalObject.setProperty("opencraft", jsBridge, 0);
    }
  }
}
