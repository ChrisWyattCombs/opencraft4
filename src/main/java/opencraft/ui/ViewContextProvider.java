package opencraft.ui;

import com.labymedia.ultralight.UltralightView;
import com.labymedia.ultralight.databind.context.ContextProvider;
import com.labymedia.ultralight.databind.context.ContextProviderFactory;
import com.labymedia.ultralight.javascript.JavascriptContextLock;
import com.labymedia.ultralight.javascript.JavascriptValue;
import java.util.function.Consumer;

/** Provides Javascript context locks tied to a specific Ultralight view. */
final class ViewContextProvider implements ContextProvider {
  private final UltralightView view;

  ViewContextProvider(UltralightView view) {
    this.view = view;
  }

  /** {@inheritDoc} */
  @Override
  public void syncWithJavascript(Consumer<JavascriptContextLock> callback) {
    try (JavascriptContextLock lock = view.lockJavascriptContext()) {
      callback.accept(lock);
    }
  }

  /** Factory that always binds providers to the owning {@link UltralightView}. */
  static final class Factory implements ContextProviderFactory {
    private final UltralightView view;

    Factory(UltralightView view) {
      this.view = view;
    }

    /** {@inheritDoc} */
    @Override
    public ContextProvider bindProvider(JavascriptValue value) {
      return new ViewContextProvider(view);
    }
  }
}
