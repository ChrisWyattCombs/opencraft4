package opencraft.ui;

import static org.lwjgl.glfw.GLFW.GLFW_KEY_0;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_9;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_A;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_APOSTROPHE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSLASH;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_CAPS_LOCK;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_COMMA;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_END;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_EQUAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F1;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_F12;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_GRAVE_ACCENT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_HOME;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_INSERT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_0;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_9;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ADD;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_DECIMAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_DIVIDE;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_EQUAL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_MULTIPLY;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_KP_SUBTRACT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SUPER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_MINUS;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_PERIOD;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_BRACKET;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SUPER;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SEMICOLON;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_SLASH;
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
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;
import static org.lwjgl.glfw.GLFW.glfwGetCursorPos;
import static org.lwjgl.glfw.GLFW.glfwGetMouseButton;
import static org.lwjgl.glfw.GLFW.glfwGetWindowContentScale;
import static org.lwjgl.glfw.GLFW.glfwSetCharCallback;
import static org.lwjgl.glfw.GLFW.glfwSetCursorPosCallback;
import static org.lwjgl.glfw.GLFW.glfwSetKeyCallback;
import static org.lwjgl.glfw.GLFW.glfwSetMouseButtonCallback;
import static org.lwjgl.glfw.GLFW.glfwSetScrollCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowContentScaleCallback;
import static org.lwjgl.glfw.GLFW.glfwSetWindowFocusCallback;

import com.labymedia.ultralight.UltralightView;
import com.labymedia.ultralight.input.UltralightInputModifier;
import com.labymedia.ultralight.input.UltralightKey;
import com.labymedia.ultralight.input.UltralightKeyEvent;
import com.labymedia.ultralight.input.UltralightKeyEventType;
import com.labymedia.ultralight.input.UltralightMouseEvent;
import com.labymedia.ultralight.input.UltralightMouseEventButton;
import com.labymedia.ultralight.input.UltralightMouseEventType;
import com.labymedia.ultralight.input.UltralightScrollEvent;
import com.labymedia.ultralight.input.UltralightScrollEventType;
import opencraft.graphics.Display;

/**
 * Forwards GLFW mouse, scroll, and keyboard events into an Ultralight view.
 *
 * <p>Matches the Labymedia Ultralight example input adapter so clicks and text fields receive
 * correct events (moved events omit a button unless held; keys use RAW_DOWN/UP + CHAR).
 */
final class GuiInput {
  private final Display display;
  private UltralightView view;
  private float xScale = 1f;
  private float yScale = 1f;

  GuiInput(Display display) {
    this.display = display;
  }

  void bind(UltralightView view) {
    this.view = view;
    long window = display.getWindowHandle();

    float[] sx = new float[1];
    float[] sy = new float[1];
    glfwGetWindowContentScale(window, sx, sy);
    if (sx[0] > 0f) {
      xScale = sx[0];
    }
    if (sy[0] > 0f) {
      yScale = sy[0];
    }

    glfwSetWindowContentScaleCallback(
        window,
        (win, x, y) -> {
          if (x > 0f) {
            xScale = x;
          }
          if (y > 0f) {
            yScale = y;
          }
        });
    glfwSetWindowFocusCallback(
        window,
        (win, focused) -> {
          if (this.view == null) {
            return;
          }
          if (focused) {
            this.view.focus();
          } else {
            this.view.unfocus();
          }
        });
    glfwSetCursorPosCallback(
        window,
        (win, x, y) -> {
          if (this.view == null) {
            return;
          }
          UltralightMouseEvent event =
              new UltralightMouseEvent()
                  .x((int) (x * xScale))
                  .y((int) (y * yScale))
                  .type(UltralightMouseEventType.MOVED);
          if (glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_LEFT) == GLFW_PRESS) {
            event.button(UltralightMouseEventButton.LEFT);
          } else if (glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_MIDDLE) == GLFW_PRESS) {
            event.button(UltralightMouseEventButton.MIDDLE);
          } else if (glfwGetMouseButton(win, GLFW_MOUSE_BUTTON_RIGHT) == GLFW_PRESS) {
            event.button(UltralightMouseEventButton.RIGHT);
          }
          this.view.fireMouseEvent(event);
        });
    glfwSetMouseButtonCallback(
        window,
        (win, button, action, mods) -> {
          if (this.view == null) {
            return;
          }
          UltralightMouseEventButton mapped = mapButton(button);
          if (mapped == null) {
            return;
          }
          this.view.fireMouseEvent(
              new UltralightMouseEvent()
                  .x(scaledCursorX(win))
                  .y(scaledCursorY(win))
                  .type(
                      action == GLFW_PRESS
                          ? UltralightMouseEventType.DOWN
                          : UltralightMouseEventType.UP)
                  .button(mapped));
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
          UltralightKey translated = translateKey(key);
          UltralightKeyEventType type =
              action == GLFW_PRESS || action == GLFW_REPEAT
                  ? UltralightKeyEventType.RAW_DOWN
                  : UltralightKeyEventType.UP;
          this.view.fireKeyEvent(
              new UltralightKeyEvent()
                  .type(type)
                  .virtualKeyCode(translated)
                  .nativeKeyCode(scancode)
                  .keyIdentifier(UltralightKeyEvent.getKeyIdentifierFromVirtualKeyCode(translated))
                  .modifiers(mapMods(mods)));
          if ((action == GLFW_PRESS || action == GLFW_REPEAT)
              && (key == GLFW_KEY_ENTER || key == GLFW_KEY_KP_ENTER || key == GLFW_KEY_TAB)) {
            String text = key == GLFW_KEY_TAB ? "\t" : "\r";
            this.view.fireKeyEvent(
                new UltralightKeyEvent()
                    .type(UltralightKeyEventType.CHAR)
                    .text(text)
                    .unmodifiedText(text));
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

    this.view.focus();
  }

  private int scaledCursorX(long window) {
    double[] x = new double[1];
    double[] y = new double[1];
    glfwGetCursorPos(window, x, y);
    return (int) (x[0] * xScale);
  }

  private int scaledCursorY(long window) {
    double[] x = new double[1];
    double[] y = new double[1];
    glfwGetCursorPos(window, x, y);
    return (int) (y[0] * yScale);
  }

  private static UltralightMouseEventButton mapButton(int button) {
    return switch (button) {
      case GLFW_MOUSE_BUTTON_LEFT -> UltralightMouseEventButton.LEFT;
      case GLFW_MOUSE_BUTTON_MIDDLE -> UltralightMouseEventButton.MIDDLE;
      case GLFW_MOUSE_BUTTON_RIGHT -> UltralightMouseEventButton.RIGHT;
      default -> null;
    };
  }

  private static int mapMods(int mods) {
    int result = 0;
    if ((mods & GLFW_MOD_ALT) != 0) {
      result |= UltralightInputModifier.ALT_KEY;
    }
    if ((mods & GLFW_MOD_CONTROL) != 0) {
      result |= UltralightInputModifier.CTRL_KEY;
    }
    if ((mods & GLFW_MOD_SUPER) != 0) {
      result |= UltralightInputModifier.META_KEY;
    }
    if ((mods & GLFW_MOD_SHIFT) != 0) {
      result |= UltralightInputModifier.SHIFT_KEY;
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
    if (glfwKey >= GLFW_KEY_F1 && glfwKey <= GLFW_KEY_F12) {
      return UltralightKey.values()[UltralightKey.F1.ordinal() + (glfwKey - GLFW_KEY_F1)];
    }
    if (glfwKey >= GLFW_KEY_KP_0 && glfwKey <= GLFW_KEY_KP_9) {
      return UltralightKey.values()[UltralightKey.NUMPAD0.ordinal() + (glfwKey - GLFW_KEY_KP_0)];
    }
    return switch (glfwKey) {
      case GLFW_KEY_SPACE -> UltralightKey.SPACE;
      case GLFW_KEY_APOSTROPHE -> UltralightKey.OEM_7;
      case GLFW_KEY_COMMA -> UltralightKey.OEM_COMMA;
      case GLFW_KEY_MINUS -> UltralightKey.OEM_MINUS;
      case GLFW_KEY_PERIOD -> UltralightKey.OEM_PERIOD;
      case GLFW_KEY_SLASH -> UltralightKey.OEM_2;
      case GLFW_KEY_SEMICOLON -> UltralightKey.OEM_1;
      case GLFW_KEY_EQUAL, GLFW_KEY_KP_EQUAL -> UltralightKey.OEM_PLUS;
      case GLFW_KEY_LEFT_BRACKET -> UltralightKey.OEM_4;
      case GLFW_KEY_BACKSLASH -> UltralightKey.OEM_5;
      case GLFW_KEY_RIGHT_BRACKET -> UltralightKey.OEM_6;
      case GLFW_KEY_GRAVE_ACCENT -> UltralightKey.OEM_3;
      case GLFW_KEY_ESCAPE -> UltralightKey.ESCAPE;
      case GLFW_KEY_ENTER, GLFW_KEY_KP_ENTER -> UltralightKey.RETURN;
      case GLFW_KEY_TAB -> UltralightKey.TAB;
      case GLFW_KEY_BACKSPACE -> UltralightKey.BACK;
      case GLFW_KEY_INSERT -> UltralightKey.INSERT;
      case GLFW_KEY_DELETE -> UltralightKey.DELETE;
      case GLFW_KEY_RIGHT -> UltralightKey.RIGHT;
      case GLFW_KEY_LEFT -> UltralightKey.LEFT;
      case GLFW_KEY_DOWN -> UltralightKey.DOWN;
      case GLFW_KEY_UP -> UltralightKey.UP;
      case GLFW_KEY_PAGE_UP -> UltralightKey.PRIOR;
      case GLFW_KEY_PAGE_DOWN -> UltralightKey.NEXT;
      case GLFW_KEY_HOME -> UltralightKey.HOME;
      case GLFW_KEY_END -> UltralightKey.END;
      case GLFW_KEY_CAPS_LOCK -> UltralightKey.CAPITAL;
      case GLFW_KEY_KP_DECIMAL -> UltralightKey.DECIMAL;
      case GLFW_KEY_KP_DIVIDE -> UltralightKey.DIVIDE;
      case GLFW_KEY_KP_MULTIPLY -> UltralightKey.MULTIPLY;
      case GLFW_KEY_KP_SUBTRACT -> UltralightKey.SUBTRACT;
      case GLFW_KEY_KP_ADD -> UltralightKey.ADD;
      case GLFW_KEY_LEFT_SHIFT, GLFW_KEY_RIGHT_SHIFT -> UltralightKey.SHIFT;
      case GLFW_KEY_LEFT_CONTROL, GLFW_KEY_RIGHT_CONTROL -> UltralightKey.CONTROL;
      case GLFW_KEY_LEFT_ALT, GLFW_KEY_RIGHT_ALT -> UltralightKey.MENU;
      case GLFW_KEY_LEFT_SUPER -> UltralightKey.LWIN;
      case GLFW_KEY_RIGHT_SUPER -> UltralightKey.RWIN;
      default -> UltralightKey.UNKNOWN;
    };
  }
}
