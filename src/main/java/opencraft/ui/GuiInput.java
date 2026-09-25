package opencraft.ui;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_0;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_9;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_TAB;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_Z;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwSetCharCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;

import com.labymedia.ultralight.UltralightView;
import com.labymedia.ultralight.input.UltralightKey;
import com.labymedia.ultralight.input.UltralightKeyEvent;
import com.labymedia.ultralight.input.UltralightKeyEventType;
import com.labymedia.ultralight.input.UltralightMouseEvent;
import com.labymedia.ultralight.input.UltralightMouseEventButton;
import com.labymedia.ultralight.input.UltralightMouseEventType;
import com.labymedia.ultralight.input.UltralightScrollEvent;
import com.labymedia.ultralight.input.UltralightScrollEventType;
import opencraft.graphics.Display;

/** Forwards GLFW mouse, scroll, and keyboard events into an Ultralight view. */
final class GuiInput {
  private final Display display;
  private UltralightView view;

  GuiInput(Display display) {
    this.display = display;
  }

  void bind(UltralightView view) {
    this.view = view;
    long window = display.getWindowHandle();

    glfwSetCursorPosCallback(
        window,
        (win, x, y) ->
            fireMouse(
                UltralightMouseEventType.MOVED, (int) x, (int) y, UltralightMouseEventButton.LEFT));
    glfwSetMouseButtonCallback(
        window,
        (win, button, action, mods) -> {
          UltralightMouseEventType type =
              action == GLFW_PRESS ? UltralightMouseEventType.DOWN : UltralightMouseEventType.UP;
          fireMouse(type, cursorX(win), cursorY(win), mapButton(button));
        });
    glfwSetScrollCallback(
        window,
        (win, xOffset, yOffset) -> {
          if (this.view == null) {
            return;
          }
          this.view.fireScrollEvent(
              new UltralightScrollEvent()
                  .type(UltralightScrollEventType.BY_PIXEL)
                  .deltaX((int) (xOffset * 32))
                  .deltaY((int) (yOffset * 32)));
        });
    glfwSetKeyCallback(
        window,
        (win, key, scancode, action, mods) -> {
          if (this.view == null) {
            return;
          }
          UltralightKeyEventType type;
          if (action == GLFW_PRESS || action == GLFW_REPEAT) {
            type = UltralightKeyEventType.RAW_DOWN;
          } else {
            type = UltralightKeyEventType.UP;
          }
          UltralightKey translated = translateKey(key);
          this.view.fireKeyEvent(
              new UltralightKeyEvent()
                  .type(type)
                  .virtualKeyCode(translated)
                  .nativeKeyCode(scancode)
                  .keyIdentifier(UltralightKeyEvent.getKeyIdentifierFromVirtualKeyCode(translated))
                  .modifiers(mapMods(mods)));
          if (action != GLFW_RELEASE) {
            this.view.fireKeyEvent(
                new UltralightKeyEvent()
                    .type(UltralightKeyEventType.DOWN)
                    .virtualKeyCode(translated)
                    .nativeKeyCode(scancode)
                    .keyIdentifier(
                        UltralightKeyEvent.getKeyIdentifierFromVirtualKeyCode(translated))
                    .modifiers(mapMods(mods)));
          }
        });
    glfwSetCharCallback(
        window,
        (win, codepoint) -> {
          if (this.view == null) {
            return;
          }
          String text = new String(Character.toChars(codepoint));
          this.view.fireKeyEvent(
              new UltralightKeyEvent()
                  .type(UltralightKeyEventType.CHAR)
                  .text(text)
                  .unmodifiedText(text));
        });
  }

  private void fireMouse(
      UltralightMouseEventType type, int x, int y, UltralightMouseEventButton button) {
    if (view == null) {
      return;
    }
    view.fireMouseEvent(new UltralightMouseEvent().type(type).x(x).y(y).button(button));
  }

  private static int cursorX(long window) {
    double[] x = new double[1];
    double[] y = new double[1];
    glfwGetCursorPos(window, x, y);
    return (int) x[0];
  }

  private static int cursorY(long window) {
    double[] x = new double[1];
    double[] y = new double[1];
    glfwGetCursorPos(window, x, y);
    return (int) y[0];
  }

  private static UltralightMouseEventButton mapButton(int button) {
    return switch (button) {
      case GLFW_MOUSE_BUTTON_LEFT -> UltralightMouseEventButton.LEFT;
      case GLFW_MOUSE_BUTTON_MIDDLE -> UltralightMouseEventButton.MIDDLE;
      case GLFW_MOUSE_BUTTON_RIGHT -> UltralightMouseEventButton.RIGHT;
      default -> UltralightMouseEventButton.LEFT;
    };
  }

  private static int mapMods(int mods) {
    int result = 0;
    if ((mods & GLFW_MOD_ALT) != 0) {
      result |= 1;
    }
    if ((mods & GLFW_MOD_CONTROL) != 0) {
      result |= 2;
    }
    if ((mods & GLFW_MOD_SUPER) != 0) {
      result |= 4;
    }
    if ((mods & GLFW_MOD_SHIFT) != 0) {
      result |= 8;
    }
    return result;
  }

  private static UltralightKey translateKey(int glfwKey) {
    if (glfwKey >= GLFW_KEY_A && glfwKey <= GLFW_KEY_Z) {
      return UltralightKey.values()[UltralightKey.A.ordinal() + (glfwKey - GLFW_KEY_A)];
    }
    if (glfwKey >= GLFW_KEY_0 && glfwKey <= GLFW_KEY_9) {
      return UltralightKey.values()[UltralightKey.NUM_0.ordinal() + (glfwKey - GLFW_KEY_0)];
    }
    return switch (glfwKey) {
      case GLFW_KEY_BACKSPACE -> UltralightKey.BACK;
      case GLFW_KEY_TAB -> UltralightKey.TAB;
      case GLFW_KEY_ENTER -> UltralightKey.RETURN;
      case GLFW_KEY_ESCAPE -> UltralightKey.ESCAPE;
      case GLFW_KEY_SPACE -> UltralightKey.SPACE;
      case GLFW_KEY_DELETE -> UltralightKey.DELETE;
      case GLFW_KEY_LEFT -> UltralightKey.LEFT;
      case GLFW_KEY_RIGHT -> UltralightKey.RIGHT;
      case GLFW_KEY_UP -> UltralightKey.UP;
      case GLFW_KEY_DOWN -> UltralightKey.DOWN;
      case GLFW_KEY_HOME -> UltralightKey.HOME;
      case GLFW_KEY_END -> UltralightKey.END;
      default -> UltralightKey.UNKNOWN;
    };
  }
}
