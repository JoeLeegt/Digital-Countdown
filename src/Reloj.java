import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Reloj — Timer / Countdown con estética de display de segmentos.
 *
 * Atajos en la vista del reloj:
 *   ESPACIO  pausar / reanudar
 *   R        reiniciar
 *   ESC      volver a la configuración
 *   F / F11  pantalla completa (también doble clic)
 *   C        alternar color rojo / verde
 */
public class Reloj {

    // ---------- Estado global y estilo ----------
    static final Color RED = new Color(255, 42, 32);
    static final Color GREEN = new Color(46, 255, 96);
    static final Color BG = Color.BLACK;

    static Font DIGITS; // DSEG7 Classic Bold (números)
    static Font TEXT;   // DSEG14 Classic (textos)

    static boolean red = true;
    static boolean showMs = false;
    static final Preferences PREFS = Preferences.userRoot().node("reloj-segmentos");

    static JFrame frame;
    static JPanel root;
    static CardLayout cards;
    static SetupPanel setup;
    static ClockPanel clock;
    static boolean fullscreen = false;
    static Rectangle windowedBounds;

    static Color accent() { return red ? RED : GREEN; }
    static Color alpha(Color c, int a) { return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a))); }
    static Color ghost() { return alpha(accent(), 26); }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Reloj::launch);
    }

    static Font loadFont(String path, Font fallback) {
        String dir = System.getProperty("reloj.fontdir"); // opcional: cargar fuentes desde carpeta
        if (dir != null) {
            try { return Font.createFont(Font.TRUETYPE_FONT, new java.io.File(dir, path.substring(path.lastIndexOf('/') + 1))); }
            catch (Exception ignored) { }
        }
        try (InputStream in = Reloj.class.getResourceAsStream(path)) {
            if (in == null) return fallback;
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (Exception e) {
            return fallback;
        }
    }

    /** Construye toda la interfaz (sin ventana) — reutilizable para pruebas. */
    static JPanel createUI() {
        DIGITS = loadFont("/fonts/DSEG7Classic-Bold.ttf", new Font(Font.MONOSPACED, Font.BOLD, 12));
        TEXT = loadFont("/fonts/DSEG14Classic-Regular.ttf", new Font(Font.MONOSPACED, Font.PLAIN, 12));
        try {
            red = PREFS.getBoolean("red", true);
            showMs = PREFS.getBoolean("ms", false);
        } catch (Exception ignored) { }

        cards = new CardLayout();
        root = new JPanel(cards);
        root.setBackground(BG);
        setup = new SetupPanel();
        clock = new ClockPanel();
        root.add(setup, "setup");
        root.add(clock, "clock");

        bind(root, JComponent.WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_F11, 0), "fs", Reloj::toggleFullscreen);
        return root;
    }

    static void launch() {
        JPanel ui = createUI();
        frame = new JFrame("Reloj");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().setBackground(BG);
        frame.setContentPane(ui);
        frame.setMinimumSize(new Dimension(640, 440));
        frame.setSize(1000, 620);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        setup.focusFirst();
    }

    static void savePrefs() {
        try {
            PREFS.putBoolean("red", red);
            PREFS.putBoolean("ms", showMs);
        } catch (Exception ignored) { }
    }

    static void setRed(boolean r) {
        red = r;
        savePrefs();
        if (root != null) root.repaint();
    }

    static void toggleFullscreen() {
        if (frame == null) return;
        fullscreen = !fullscreen;
        if (fullscreen) windowedBounds = frame.getBounds();
        GraphicsConfiguration gc = frame.getGraphicsConfiguration();
        frame.dispose();
        frame.setUndecorated(fullscreen);
        if (fullscreen) frame.setBounds(gc.getBounds());
        else if (windowedBounds != null) frame.setBounds(windowedBounds);
        frame.setVisible(true);
        if (clock.isShowing()) clock.requestFocusInWindow();
        else setup.focusFirst();
    }

    static void showClock(boolean countdown, long targetMs) {
        cards.show(root, "clock");
        clock.begin(countdown, targetMs, showMs);
        clock.requestFocusInWindow();
    }

    static void showSetup() {
        clock.halt();
        cards.show(root, "setup");
        setup.focusFirst();
    }

    static void bind(JComponent c, int when, KeyStroke ks, String name, Runnable r) {
        c.getInputMap(when).put(ks, name);
        c.getActionMap().put(name, new AbstractAction() {
            public void actionPerformed(ActionEvent e) { if (c.isShowing()) r.run(); }
        });
    }

    static void hints(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    /** Dibuja texto como figura; con glow opcional (halo suave de fósforo). */
    static void drawShape(Graphics2D g, Font f, String s, float x, float y, Color c, boolean glow) {
        Shape sh = f.createGlyphVector(g.getFontRenderContext(), s).getOutline(x, y);
        if (glow) {
            float sz = f.getSize2D();
            g.setStroke(new BasicStroke(sz * 0.07f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(c, 18));
            g.draw(sh);
            g.setStroke(new BasicStroke(sz * 0.03f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(alpha(c, 40));
            g.draw(sh);
        }
        g.setColor(c);
        g.fill(sh);
    }

    static float advance(Font f, FontRenderContext frc, String s) {
        return (float) f.createGlyphVector(frc, s).getLogicalBounds().getWidth();
    }

    static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    /** Ancho exacto (con métricas fraccionales). */
    static int tw(Font f, String s) { return (int) Math.ceil(advance(f, FRC, s)); }

    /** En DSEG el espacio es muy angosto; "!" es un espacio del ancho de un carácter. */
    static String seg(String s) { return s.replace(' ', '!'); }

    static String pad(long v, int n) {
        String s = Long.toString(v);
        while (s.length() < n) s = "0" + s;
        return s;
    }

    static String ghostOf(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) b.append('8');
        return b.toString();
    }

    // =====================================================================
    //  VISTA 1 — Configuración
    // =====================================================================
    static class SetupPanel extends JPanel {
        final ModeSelector mode = new ModeSelector();
        final DigitField hh = new DigitField(99);
        final DigitField mm = new DigitField(59);
        final DigitField ss = new DigitField(59);
        final ToggleSwitch colorSwitch = new ToggleSwitch("ROJO", "VERDE");
        final ToggleSwitch msSwitch = new ToggleSwitch("SIN MS", "CON MS");
        final StartButton start = new StartButton("INICIAR", this::start);
        boolean errorHidden = false;

        SetupPanel() {
            setBackground(BG);
            setLayout(new GridBagLayout());

            colorSwitch.setValue(!red);
            colorSwitch.onChange = v -> setRed(!v);
            msSwitch.setValue(showMs);
            msSwitch.onChange = v -> { showMs = v; savePrefs(); };

            // Bloque de tiempo HH : MM : SS con etiquetas debajo
            JPanel time = new JPanel(new GridBagLayout());
            time.setOpaque(false);
            GridBagConstraints t = new GridBagConstraints();
            t.gridy = 0;
            t.gridx = 0; time.add(hh, t);
            t.gridx = 1; time.add(new Colon(), t);
            t.gridx = 2; time.add(mm, t);
            t.gridx = 3; time.add(new Colon(), t);
            t.gridx = 4; time.add(ss, t);
            t.gridy = 1; t.insets = new Insets(10, 0, 0, 0);
            t.gridx = 0; time.add(new Txt("HORAS", 12f, 170), t);
            t.gridx = 2; time.add(new Txt("MIN", 12f, 170), t);
            t.gridx = 4; time.add(new Txt("SEG", 12f, 170), t);

            JPanel switches = new JPanel(new FlowLayout(FlowLayout.CENTER, 48, 0));
            switches.setOpaque(false);
            switches.add(colorSwitch);
            switches.add(msSwitch);

            GridBagConstraints c = new GridBagConstraints();
            c.gridx = 0;
            c.gridy = 0; c.insets = new Insets(0, 0, 54, 0); add(mode, c);
            c.gridy = 1; c.insets = new Insets(0, 0, 60, 0); add(time, c);
            c.gridy = 2; c.insets = new Insets(0, 0, 56, 0); add(switches, c);
            c.gridy = 3; c.insets = new Insets(0, 0, 0, 0); add(start, c);
            c.gridy = 4; c.insets = new Insets(54, 0, 0, 0);
            add(new Txt("ESPACIO PAUSA  R REINICIAR  ESC VOLVER  F PANTALLA COMPLETA", 9f, 60), c);

            bind(this, WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "start", this::start);
            bind(this, WHEN_IN_FOCUSED_WINDOW, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "fs", Reloj::toggleFullscreen);
        }

        void focusFirst() { SwingUtilities.invokeLater(hh::requestFocusInWindow); }

        long totalMs() { return ((hh.value * 60L + mm.value) * 60L + ss.value) * 1000L; }

        void start() {
            long total = totalMs();
            if (mode.countdown && total <= 0) { flashError(); return; }
            showClock(mode.countdown, total);
        }

        void flashError() {
            final int[] n = {0};
            javax.swing.Timer t = new javax.swing.Timer(110, null);
            t.addActionListener(e -> {
                errorHidden = !errorHidden;
                repaint();
                if (++n[0] >= 6) { errorHidden = false; repaint(); t.stop(); }
            });
            t.start();
        }
    }

    /** Selector TIMER / COUNTDOWN: la palabra activa se ilumina. */
    static class ModeSelector extends JComponent {
        boolean countdown = false;
        static final String A = "TIMER", B = "COUNTDOWN";
        static final float SIZE = 22f;
        static final int GAP = 56;

        ModeSelector() {
            setFocusable(true);
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) {
                    requestFocusInWindow();
                    countdown = e.getX() > splitX();
                    repaint();
                }
            });
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    int k = e.getKeyCode();
                    if (k == KeyEvent.VK_LEFT) { countdown = false; repaint(); }
                    else if (k == KeyEvent.VK_RIGHT) { countdown = true; repaint(); }
                    else if (k == KeyEvent.VK_SPACE) { countdown = !countdown; repaint(); }
                }
            });
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        Font font() { return TEXT.deriveFont(SIZE); }
        int splitX() { FontMetrics fm = getFontMetrics(font()); return startX() + tw(font(), A) + GAP / 2; }
        int startX() { return (getWidth() - contentW()) / 2; }
        int contentW() { FontMetrics fm = getFontMetrics(font()); return tw(font(), A) + GAP + tw(font(), B); }

        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(font());
            return new Dimension(contentW() + 8, fm.getAscent() + 18);
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Font f = font();
            FontMetrics fm = g.getFontMetrics(f);
            int x = startX();
            int y = fm.getAscent();
            Color on = accent(), off = alpha(accent(), 64);
            drawShape(g, f, A, x, y, countdown ? off : on, !countdown);
            int xb = x + tw(font(), A) + GAP;
            drawShape(g, f, B, xb, y, countdown ? on : off, countdown);
            if (isFocusOwner()) {
                int ux = countdown ? xb : x;
                int uw = tw(font(), countdown ? B : A);
                g.setColor(accent());
                g.fillRect(ux, y + 10, uw, 2);
            }
            g.dispose();
        }
    }

    /** Campo de dos dígitos estilo display. Teclear dígitos, flechas o rueda del mouse. */
    static class DigitField extends JComponent {
        int value = 0;
        final int max;
        int typed = 0;
        static final float SIZE = 76f;

        DigitField(int max) {
            this.max = max;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { requestFocusInWindow(); }
            });
            addMouseWheelListener(e -> step(-e.getWheelRotation()));
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { typed = 0; repaint(); }
                public void focusLost(FocusEvent e) { typed = 0; repaint(); }
            });
            addKeyListener(new KeyAdapter() {
                public void keyTyped(KeyEvent e) {
                    char ch = e.getKeyChar();
                    if (ch >= '0' && ch <= '9') {
                        int d = ch - '0';
                        int nv = (typed == 0) ? d : (value % 10) * 10 + d;
                        value = Math.min(nv, max);
                        typed++;
                        repaint();
                        if (typed >= 2) { typed = 0; transferFocus(); }
                    }
                }
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_UP: step(1); break;
                        case KeyEvent.VK_DOWN: step(-1); break;
                        case KeyEvent.VK_RIGHT: transferFocus(); break;
                        case KeyEvent.VK_LEFT: transferFocusBackward(); break;
                        case KeyEvent.VK_BACK_SPACE:
                        case KeyEvent.VK_DELETE: value /= 10; typed = 0; repaint(); break;
                        default: break;
                    }
                }
            });
        }

        void step(int d) {
            int v = value + d;
            if (v > max) v = 0;
            if (v < 0) v = max;
            value = v;
            typed = 0;
            repaint();
        }

        public Dimension getPreferredSize() {
            Font f = DIGITS.deriveFont(SIZE);
            FontMetrics fm = getFontMetrics(f);
            return new Dimension(fm.stringWidth("88") + 16, fm.getAscent() + 16);
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Font f = DIGITS.deriveFont(SIZE);
            FontMetrics fm = g.getFontMetrics(f);
            float x = (getWidth() - fm.stringWidth("88")) / 2f;
            float y = fm.getAscent() + 2;
            drawShape(g, f, "88", x, y, ghost(), false);
            boolean hidden = setup != null && setup.errorHidden;
            if (!hidden) {
                Color c = isFocusOwner() ? accent() : alpha(accent(), 215);
                drawShape(g, f, pad(value, 2), x, y, c, true);
            }
            if (isFocusOwner()) {
                g.setColor(accent());
                g.fillRect(Math.round(x), getHeight() - 4, fm.stringWidth("88"), 3);
            }
            g.dispose();
        }
    }

    static class Colon extends JComponent {
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(DIGITS.deriveFont(DigitField.SIZE));
            return new Dimension(fm.stringWidth(":") + 18, fm.getAscent() + 16);
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Font f = DIGITS.deriveFont(DigitField.SIZE);
            FontMetrics fm = g.getFontMetrics(f);
            drawShape(g, f, ":", (getWidth() - fm.stringWidth(":")) / 2f, fm.getAscent() + 2, alpha(accent(), 215), false);
            g.dispose();
        }
    }

    /** Texto simple que siempre usa el color de acento. */
    static class Txt extends JComponent {
        final String s; final float size; final int a;
        Txt(String s, float size, int a) { this.s = seg(s); this.size = size; this.a = a; }
        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(TEXT.deriveFont(size));
            return new Dimension(tw(TEXT.deriveFont(size), s) + 4, fm.getAscent() + fm.getDescent() + 2);
        }
        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Font f = TEXT.deriveFont(size);
            FontMetrics fm = g.getFontMetrics(f);
            drawShape(g, f, s, (getWidth() - tw(TEXT.deriveFont(size), s)) / 2f, fm.getAscent() + 1, alpha(accent(), a), false);
            g.dispose();
        }
    }

    /** Switch de dos estados: TEXTO_IZQ (o) TEXTO_DER */
    static class ToggleSwitch extends JComponent {
        final String left, right;
        boolean on = false; // false = izquierda, true = derecha
        Consumer<Boolean> onChange;
        static final float SIZE = 13f;
        static final int PW = 50, PH = 24, PAD = 16;

        ToggleSwitch(String left, String right) {
            this.left = seg(left); this.right = seg(right);
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mousePressed(MouseEvent e) { requestFocusInWindow(); toggle(); }
            });
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) {
                    int k = e.getKeyCode();
                    if (k == KeyEvent.VK_SPACE) toggle();
                    else if (k == KeyEvent.VK_LEFT && on) toggle();
                    else if (k == KeyEvent.VK_RIGHT && !on) toggle();
                }
            });
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        void setValue(boolean v) { on = v; repaint(); }
        void toggle() { on = !on; if (onChange != null) onChange.accept(on); repaint(); }

        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(TEXT.deriveFont(SIZE));
            return new Dimension(tw(TEXT.deriveFont(SIZE), left) + PAD + PW + PAD + tw(TEXT.deriveFont(SIZE), right) + 4, PH + 6);
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Font f = TEXT.deriveFont(SIZE);
            FontMetrics fm = g.getFontMetrics(f);
            int cy = getHeight() / 2;
            float ty = cy + fm.getAscent() / 2f - 1;
            int x = 2;
            Color ac = accent();
            drawShape(g, f, left, x, ty, on ? alpha(ac, 64) : ac, false);
            x += tw(TEXT.deriveFont(SIZE), left) + PAD;

            int py = cy - PH / 2;
            g.setStroke(new BasicStroke(1.5f));
            g.setColor(isFocusOwner() ? ac : alpha(ac, 130));
            g.drawRoundRect(x, py, PW, PH, PH, PH);
            int d = PH - 8;
            int kx = on ? x + PW - 4 - d : x + 4;
            g.setColor(alpha(ac, 40));
            g.fillOval(kx - 3, py + 1, d + 6, d + 6);
            g.setColor(ac);
            g.fillOval(kx, py + 4, d, d);
            x += PW + PAD;

            drawShape(g, f, right, x, ty, on ? ac : alpha(ac, 64), false);
            g.dispose();
        }
    }

    /** Botón de solo contorno; se rellena al pasar el mouse o con foco. */
    static class StartButton extends JComponent {
        final String label; final Runnable action;
        boolean hover = false;
        static final float SIZE = 18f;

        StartButton(String label, Runnable action) {
            this.label = seg(label); this.action = action;
            setFocusable(true);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                public void mouseClicked(MouseEvent e) { action.run(); }
            });
            addKeyListener(new KeyAdapter() {
                public void keyPressed(KeyEvent e) { if (e.getKeyCode() == KeyEvent.VK_SPACE) action.run(); }
            });
            addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) { repaint(); }
                public void focusLost(FocusEvent e) { repaint(); }
            });
        }

        public Dimension getPreferredSize() {
            FontMetrics fm = getFontMetrics(TEXT.deriveFont(SIZE));
            return new Dimension(tw(TEXT.deriveFont(SIZE), label) + 96, fm.getAscent() + 36);
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            hints(g);
            Font f = TEXT.deriveFont(SIZE);
            FontMetrics fm = g.getFontMetrics(f);
            int w = getWidth() - 3, h = getHeight() - 3;
            boolean lit = hover || isFocusOwner();
            Color ac = accent();
            if (lit) { g.setColor(ac); g.fillRect(1, 1, w, h); }
            else { g.setColor(alpha(ac, 170)); g.setStroke(new BasicStroke(1.5f)); g.drawRect(1, 1, w, h); }
            float tx = (getWidth() - tw(TEXT.deriveFont(SIZE), label)) / 2f;
            float ty = (getHeight() + fm.getAscent()) / 2f - 1;
            drawShape(g, f, label, tx, ty, lit ? BG : ac, false);
            g.dispose();
        }
    }

    // =====================================================================
    //  VISTA 2 — Reloj a pantalla completa
    // =====================================================================
    static class ClockPanel extends JComponent {
        boolean countdown;
        long target;         // ms (0 en timer = sin límite)
        boolean hours, ms;
        long startNano, accumulated;
        boolean running, finished;
        long finishedAt;
        final javax.swing.Timer tick = new javax.swing.Timer(15, e -> update());
        final javax.swing.Timer cursorHider;
        final Cursor blank;

        ClockPanel() {
            setFocusable(true);
            setOpaque(true);
            Cursor b;
            try {
                b = Toolkit.getDefaultToolkit().createCustomCursor(
                        new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), new Point(0, 0), "blank");
            } catch (Exception ex) { b = Cursor.getDefaultCursor(); }
            blank = b;
            cursorHider = new javax.swing.Timer(1800, e -> setCursor(blank));
            cursorHider.setRepeats(false);
            addMouseMotionListener(new MouseMotionAdapter() {
                public void mouseMoved(MouseEvent e) { setCursor(Cursor.getDefaultCursor()); cursorHider.restart(); }
            });
            addMouseListener(new MouseAdapter() {
                public void mouseClicked(MouseEvent e) { if (e.getClickCount() == 2) toggleFullscreen(); }
            });
            int w = WHEN_IN_FOCUSED_WINDOW;
            bind(this, w, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "pause", this::togglePause);
            bind(this, w, KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "restart", this::restart);
            bind(this, w, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "back", Reloj::showSetup);
            bind(this, w, KeyStroke.getKeyStroke(KeyEvent.VK_F, 0), "fs", Reloj::toggleFullscreen);
            bind(this, w, KeyStroke.getKeyStroke(KeyEvent.VK_C, 0), "color", () -> {
                setRed(!red);
                setup.colorSwitch.setValue(!red);
            });
        }

        void begin(boolean cd, long targetMs, boolean withMs) {
            countdown = cd;
            target = targetMs;
            ms = withMs;
            hours = targetMs >= 3_600_000L || (!cd && targetMs == 0);
            accumulated = 0;
            finished = false;
            running = true;
            startNano = System.nanoTime();
            tick.start();
            cursorHider.restart();
            repaint();
        }

        void halt() { tick.stop(); running = false; setCursor(Cursor.getDefaultCursor()); }

        long elapsed() { return accumulated + (running ? (System.nanoTime() - startNano) / 1_000_000L : 0); }

        void togglePause() {
            if (finished) return;
            if (running) { accumulated = elapsed(); running = false; }
            else { startNano = System.nanoTime(); running = true; }
            repaint();
        }

        void restart() { begin(countdown, target, ms); }

        void update() {
            if (running && target > 0 && elapsed() >= target) {
                accumulated = target;
                running = false;
                finished = true;
                finishedAt = System.currentTimeMillis();
                Toolkit.getDefaultToolkit().beep();
            }
            repaint();
        }

        long displayValue() {
            long e = elapsed();
            if (target > 0 && e > target) e = target;
            if (countdown) {
                long r = target - e;
                if (!ms) r = ((r + 999) / 1000) * 1000; // sin ms, el countdown redondea hacia arriba
                return r;
            }
            return e;
        }

        protected void paintComponent(Graphics g0) {
            Graphics2D g = (Graphics2D) g0.create();
            g.setColor(BG);
            g.fillRect(0, 0, getWidth(), getHeight());
            hints(g);
            FontRenderContext frc = g.getFontRenderContext();

            long v = displayValue();
            long h = v / 3_600_000L, m = (v / 60_000L) % 60, s = (v / 1000L) % 60, x = v % 1000;
            if (!hours) m += h * 60;

            List<String> vals = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            if (hours) { vals.add(pad(h, 2)); labels.add("HORAS"); }
            vals.add(pad(m, 2)); labels.add("MINUTOS");
            vals.add(pad(s, 2)); labels.add("SEGUNDOS");
            if (ms) { vals.add(pad(x, 3)); labels.add("MILISEG"); }
            int n = vals.size();

            final float MS_SCALE = 0.6f;
            Font base = DIGITS.deriveFont(100f);
            Font baseMs = DIGITS.deriveFont(100f * MS_SCALE);
            final float SEP_PAD = 0.07f; // aire a cada lado de ":" en proporción al tamaño
            float colon100 = advance(base, frc, ":") + 2 * 100f * SEP_PAD;
            float dot100 = advance(base, frc, ".") + 2 * 100f * SEP_PAD * 0.5f;
            float total100 = 0;
            for (int i = 0; i < n; i++) {
                boolean isMs = ms && i == n - 1;
                total100 += advance(isMs ? baseMs : base, frc, ghostOf(vals.get(i)));
                if (i < n - 1) total100 += (ms && i == n - 2) ? dot100 : colon100;
            }
            float digitH100 = (float) base.createGlyphVector(frc, "8").getVisualBounds().getHeight();

            float scale = Math.min(getWidth() * 0.9f / total100, getHeight() * 0.5f / digitH100);
            float size = 100f * scale;
            Font big = DIGITS.deriveFont(size);
            Font small = DIGITS.deriveFont(size * MS_SCALE);
            Font lab = TEXT.deriveFont(Math.max(11f, size * 0.11f));
            FontMetrics lfm = g.getFontMetrics(lab);

            float digitH = digitH100 * scale;
            float gap = digitH * 0.2f;
            float blockH = digitH + gap + lfm.getAscent();
            float baseline = (getHeight() - blockH) / 2f + digitH;
            float labelY = baseline + gap + lfm.getAscent();
            float cx = (getWidth() - total100 * scale) / 2f;

            Color ac = accent();
            boolean blinkOff = finished && ((System.currentTimeMillis() - finishedAt) / 450) % 2 == 1;
            boolean paused = !running && !finished;
            Color digitColor = paused ? alpha(ac, 95) : ac;

            for (int i = 0; i < n; i++) {
                boolean isMs = ms && i == n - 1;
                Font f = isMs ? small : big;
                String val = vals.get(i);
                float w = advance(f, frc, ghostOf(val));
                drawShape(g, f, ghostOf(val), cx, baseline, ghost(), false);
                if (!blinkOff) drawShape(g, f, val, cx, baseline, digitColor, !paused);

                String l = labels.get(i);
                drawShape(g, lab, l, cx + (w - tw(lab, l)) / 2f, labelY, alpha(ac, 170), false);
                cx += w;

                if (i < n - 1) {
                    boolean dot = ms && i == n - 2;
                    String sep = dot ? "." : ":";
                    float pad = size * SEP_PAD * (dot ? 0.5f : 1f);
                    cx += pad;
                    if (!blinkOff) drawShape(g, big, sep, cx, baseline, digitColor, false);
                    cx += advance(big, frc, sep) + pad;
                }
            }
            g.dispose();
        }
    }
}
