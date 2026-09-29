package infinitylink.core;

import java.util.Arrays;

/** Le contrat parle en codes GLFW ; Minecraft 26.3 lit le clavier via SDL3
 *  (InputConstants.isKeyDown(int) = SDL_GetKeyboardState()[scancode], KEY_R = 21, KEY_A = 4, KEY_0 = 39).
 *  Table GLFW keycode -> SDL_Scancode (touches physiques, position US). -1 = non gérée. */
public final class KeyMap {
    private KeyMap() {}

    private static final int[] T = new int[349];

    private static void put(int glfw, int sdl) { T[glfw] = sdl; }

    static {
        Arrays.fill(T, -1);
        put(32, 44);                                                      // espace
        put(39, 52); put(44, 54); put(45, 45); put(46, 55); put(47, 56);  // apostrophe virgule moins point slash
        put(48, 39); for (int i = 1; i <= 9; i++) put(48 + i, 29 + i);   // 0..9
        put(59, 51); put(61, 46);                                         // point-virgule egal
        for (int i = 0; i < 26; i++) put(65 + i, 4 + i);                  // A..Z
        put(91, 47); put(92, 49); put(93, 48); put(96, 53);               // crochets antislash accent grave
        put(256, 41); put(257, 40); put(258, 43); put(259, 42);           // echap entree tab retour
        put(260, 73); put(261, 76); put(262, 79); put(263, 80);           // inser suppr droite gauche
        put(264, 81); put(265, 82); put(266, 75); put(267, 78);           // bas haut pgprec pgsuiv
        put(268, 74); put(269, 77);                                       // debut fin
        put(280, 57); put(281, 71); put(282, 83); put(283, 70); put(284, 72); // verrmaj defil verrnum impr pause
        for (int i = 0; i < 12; i++) put(290 + i, 58 + i);                // F1..F12
        for (int i = 0; i < 12; i++) put(302 + i, 104 + i);               // F13..F24
        put(320, 98); for (int i = 1; i <= 9; i++) put(320 + i, 88 + i);  // pave 0..9
        put(330, 99); put(331, 84); put(332, 85); put(333, 86); put(334, 87); put(335, 88); put(336, 103);
        put(340, 225); put(341, 224); put(342, 226); put(343, 227);       // maj ctrl alt super gauches
        put(344, 229); put(345, 228); put(346, 230); put(347, 231);       // droites
        put(348, 101);                                                    // menu (SDL_SCANCODE_APPLICATION)
    }

    public static int glfwToSdl(int glfw) { return glfw >= 0 && glfw < T.length ? T[glfw] : -1; }
}
