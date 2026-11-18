import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * IMPÉRIO DO MALI – CONSTRUA SUA CIDADE
 * Jogo educativo em um único arquivo (Java + Swing). Sem bibliotecas externas, sem internet.
 * Rodar: javac MaliGame.java && java MaliGame     (ou botão "Run" no VS Code)
 * Texturas: pasta "imagens/" ao lado deste arquivo (se faltar alguma, o jogo usa o desenho por código).
 *
 * Arquitetura (resumo):
 *  - Estado do jogo: grade 9x9 (grid), recursos, ano, estado da tela (0=intro, 1=jogando, 2=vitória, 3=fim do reinado).
 *  - Um Timer de 16 ms atualiza o jogo (update) e redesenha tudo (paintComponent).
 *  - A cada 1 segundo ocorre um "tick" (produção/consumo); a cada 10 ticks passa 1 ano.
 *  - Todo o visual é desenhado por código (isométrico). Botões são retângulos desenhados + lista de cliques.
 */
public class MaliGame extends JPanel {

    // ===== Constantes =====
    static final int W = 1000, H = 720, N = 9, TW = 80, TH = 40, OX = 500, OY = 150;
    static final int TICKS_ANO = 10, ANO_INICIAL = 1312, ANO_FINAL = 1337;
    static final int CASA = 0, FAZENDA = 1, MERCADO = 2, POCO = 3, MESQUITA = 4, ESCOLA = 5, ARMAZEM = 6;
    static final String[] NOME = {"Casa", "Fazenda", "Mercado", "Poço", "Mesquita", "Escola", "Armazém"};
    static final String[] DESC = {"+8 moradores", "produz comida", "gera ouro", "fornece água", "+ouro · cultura", "+ouro · saber", "+estoque"};
    static final int[] CUSTO = {30, 40, 60, 30, 80, 80, 50};
    static final Color[] COR = {new Color(196, 140, 88), new Color(110, 170, 70), new Color(200, 70, 50),
            new Color(70, 150, 200), new Color(222, 186, 128), new Color(60, 110, 170), new Color(190, 150, 60)};
    static final Color MARROM = new Color(74, 46, 24), CREME = new Color(244, 228, 190), OURO = new Color(240, 190, 40);

    static final String[] FATOS = {
        /*0*/ "O Mali enriqueceu com o comércio de ouro e sal: o ouro vinha das regiões do sul e o sal, do deserto do Saara. As caravanas cruzavam o deserto em rotas comerciais.",
        /*1*/ "Mansa Musa ficou famoso por sua riqueza e pela peregrinação a Meca em 1324. Ele distribuiu tanto ouro no Cairo que o valor do metal caiu por anos.",
        /*2*/ "O rio Níger foi essencial para o Mali: irrigava plantações de painço e arroz e servia de estrada para canoas de comércio e pesca.",
        /*3*/ "Timbuktu tornou-se um importante centro de conhecimento, com a Universidade de Sankoré, estudiosos e grandes coleções de manuscritos.",
        /*4*/ "A Mesquita de Djinguereber, em Timbuktu, foi construída com a ajuda do arquiteto Abu Ishaq al-Sahili, trazido por Mansa Musa depois de sua peregrinação.",
        /*5*/ "Escravidão: no Mali, como em muitos reinos da época, pessoas eram escravizadas e vendidas nas rotas do Saara. Parte da riqueza do período veio dessa injustiça.",
        /*6*/ "Desigualdade: enquanto a corte vivia com enorme luxo, a maior parte da população era formada por camponeses, pescadores e artesãos que pagavam tributos.",
        /*7*/ "Conflitos: o império cresceu conquistando e cobrando tributos de povos vizinhos, e as disputas pelo poder eram comuns entre as famílias governantes.",
        /*8*/ "O Império do Mali foi fundado por Sundiata Keita por volta de 1235. Os griôs, contadores de histórias, guardavam a memória do império de forma oral.",
        /*9*/ "As casas de barro (adobe) mantêm o interior fresco no calor, mas precisam de reparos depois das chuvas. Por isso a comunidade construía junta.",
        /*10*/ "\"Mansa\" significa \"rei\" ou \"imperador\" na língua mandinga. Musa era o décimo rei do Mali.",
        /*11*/ "O sal era muito valioso no Sudão Ocidental: o corpo precisa dele e quase não havia sal no sul. Por isso era trocado por ouro."};
    static final int[] FATO_DA_OBRA = {9, 2, 0, 2, 4, 3, 11};

    // ===== Estado =====
    final int[][] grid = new int[N][N];
    final double[][] nasceu = new double[N][N];
    int[] qtd = new int[7];
    double ouro, comida, agua, anim, acc, tempoJogo, bannerT, toastT, fatoT;
    int pop, ano, ticks, estado = 0, selecionado = -1, hx = -1, hy = -1, fatoIdx = 8;
    boolean pausado, uiAtiva;
    String banner = "", toast = "";
    double mx, my, escala = 1, offX, offY;
    final Random rnd = new Random();
    final List<Walker> pessoas = new ArrayList<>();
    final List<Btn> btns = new ArrayList<>();
    final Map<String, BufferedImage> imgs = new HashMap<>(), cache = new HashMap<>();
    static final String[] ARQUIVOS = {"grama_escura", "grama_seca", "grama_clara", "madeira_marrom", "madeira_cinza",
            "madeira_laranja", "tijolo_a", "tijolo_b", "picareta", "foice", "arco", "forcado", "machado", "barril"};

    static class Walker { double x, y, ph = Math.random() * 6; int tx, ty; Color robe; }
    static class Btn { Rectangle r; Runnable a; Btn(Rectangle r, Runnable a) { this.r = r; this.a = a; } }

    public MaliGame() {
        Dimension sc;
        try { sc = Toolkit.getDefaultToolkit().getScreenSize(); } catch (HeadlessException ex) { sc = new Dimension(1920, 1080); }
        double s = Math.min(1.0, Math.min((sc.width - 40) / (double) W, (sc.height - 90) / (double) H));
        setPreferredSize(new Dimension((int) (W * s), (int) (H * s)));
        carregarImagens();
        reset();
        estado = 0;
        MouseAdapter ma = new MouseAdapter() {
            public void mouseMoved(MouseEvent e) { mover(e); }
            public void mouseDragged(MouseEvent e) { mover(e); }
            public void mousePressed(MouseEvent e) {
                mover(e);
                if (SwingUtilities.isRightMouseButton(e)) { selecionado = -1; return; }
                for (int i = btns.size() - 1; i >= 0; i--)
                    if (btns.get(i).r.contains(mx, my)) { btns.get(i).a.run(); return; }
                if (estado == 1 && !pausado && selecionado >= 0) { int[] p = pick(mx, my); if (p != null) construir(p[0], p[1]); }
            }
        };
        addMouseListener(ma);
        addMouseMotionListener(ma);
        final long[] last = {System.nanoTime()};
        new javax.swing.Timer(16, e -> {
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - last[0]) / 1e9);
            last[0] = now;
            update(dt);
            repaint();
        }).start();
    }

    void carregarImagens() {
        // 1) Texturas embutidas no próprio arquivo: funcionam em qualquer lugar.
        for (String[] d : Embutidas.DADOS) {
            try { imgs.put(d[0], ImageIO.read(new java.io.ByteArrayInputStream(java.util.Base64.getDecoder().decode(d[1])))); }
            catch (Exception ex) { /* usa o desenho por código */ }
        }
        // 2) Se existir uma pasta "imagens" por perto, os arquivos dela substituem os embutidos (para você trocar as texturas).
        List<File> bases = new ArrayList<>();
        bases.add(new File(System.getProperty("user.dir", ".")));
        try {
            File loc = new File(MaliGame.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File d = loc.isFile() ? loc.getParentFile() : loc;
            for (int i = 0; i < 3 && d != null; i++) { bases.add(d); d = d.getParentFile(); }
        } catch (Exception ex) { /* ignora */ }
        File pasta = null;
        for (File b : bases) if (new File(b, "imagens").isDirectory()) { pasta = new File(b, "imagens"); break; }
        int externas = 0;
        if (pasta != null)
            for (String n : ARQUIVOS) {
                try {
                    File f = new File(pasta, n + ".png");
                    if (f.exists()) { BufferedImage im = ImageIO.read(f); if (im != null) { imgs.put(n, im); externas++; } }
                } catch (Exception ex) { /* mantém a embutida */ }
            }
        System.out.println("Texturas prontas: " + imgs.size() + "/" + ARQUIVOS.length + (externas > 0 ? "  (" + externas + " lidas da pasta " + pasta + ")" : "  (embutidas)"));
    }

    /** Imagem redimensionada (com cache). Ícones pequenos usam pixel art nítido. */
    BufferedImage img(String n, int w, int h) {
        BufferedImage src = imgs.get(n);
        if (src == null) return null;
        String key = n + w + "x" + h;
        BufferedImage r = cache.get(key);
        if (r == null) {
            r = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            Graphics2D q = r.createGraphics();
            q.setRenderingHint(RenderingHints.KEY_INTERPOLATION, src.getWidth() <= 64 ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR : RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            q.drawImage(src, 0, 0, w, h, null);
            q.dispose();
            cache.put(key, r);
        }
        return r;
    }

    /** Preenche um losango (chão/telhado) com uma textura. */
    void texLosango(Graphics2D g, String n, int cx, int cy, int hw, int hh, float alfa) {
        BufferedImage t = img(n, 2 * hw, 2 * hh);
        if (t == null) return;
        Shape old = g.getClip();
        g.clip(losango(cx, cy, hw, hh));
        g.setComposite(AlphaComposite.SrcOver.derive(alfa));
        g.drawImage(t, cx - hw, cy - hh, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(old);
    }

    void texElipse(Graphics2D g, String n, int cx, int cy, int rx, int ry, float alfa) {
        BufferedImage t = img(n, 2 * rx, 2 * ry);
        if (t == null) return;
        Shape old = g.getClip();
        g.clip(new Ellipse2D.Double(cx - rx, cy - ry, 2 * rx, 2 * ry));
        g.setComposite(AlphaComposite.SrcOver.derive(alfa));
        g.drawImage(t, cx - rx, cy - ry, null);
        g.setComposite(AlphaComposite.SrcOver);
        g.setClip(old);
    }

    /** Ícone pixel art centralizado em (cx, cy), com sombra. */
    void icone(Graphics2D g, String n, int cx, int cy, int tam, double giro) {
        BufferedImage t = img(n, tam, tam);
        if (t == null) return;
        AffineTransform at = g.getTransform();
        g.rotate(giro, cx, cy);
        g.drawImage(t, cx - tam / 2, cy - tam / 2, null);
        g.setTransform(at);
    }

    void mover(MouseEvent e) {
        mx = (e.getX() - offX) / escala;
        my = (e.getY() - offY) / escala;
        int[] p = pick(mx, my);
        hx = p == null ? -1 : p[0];
        hy = p == null ? -1 : p[1];
        boolean over = false;
        for (Btn b : btns) if (b.r.contains(mx, my)) over = true;
        setCursor(Cursor.getPredefinedCursor(over ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
    }

    // ===== Lógica =====
    void reset() {
        for (int[] r : grid) Arrays.fill(r, -1);
        for (double[] r : nasceu) Arrays.fill(r, -10);
        qtd = new int[7];
        colocar(4, 3, CASA); colocar(5, 4, FAZENDA); colocar(3, 4, POCO);
        ouro = 150; comida = 50; agua = 50; pop = 12; ano = ANO_INICIAL; ticks = 0; acc = 0; tempoJogo = 0;
        pausado = false; selecionado = -1; banner = ""; toast = ""; bannerT = 0; toastT = 0;
        pessoas.clear(); fatoIdx = 8; fatoT = 14; estado = 1;
    }

    void colocar(int x, int y, int t) { grid[x][y] = t; qtd[t]++; }
    int total() { int s = 0; for (int q : qtd) s += q; return s; }
    int capacidade() { return 10 + 8 * qtd[CASA]; }
    int estoque() { return 120 + 100 * qtd[ARMAZEM]; }
    static boolean rio(int x, int y) { return y == 0 || (y == 1 && x >= 6); }
    boolean livre(int x, int y) { return x >= 0 && y >= 0 && x < N && y < N && !rio(x, y) && grid[x][y] < 0; }

    void construir(int x, int y) {
        if (rio(x, y)) { aviso("O rio Níger não pode receber construções."); return; }
        if (grid[x][y] >= 0) { aviso("Já existe uma construção aqui."); return; }
        if (ouro < CUSTO[selecionado]) { aviso("Ouro insuficiente!"); return; }
        ouro -= CUSTO[selecionado];
        colocar(x, y, selecionado);
        nasceu[x][y] = anim;
        setFato(FATO_DA_OBRA[selecionado]);
    }

    void aviso(String s) { toast = s; toastT = 2.5; }
    void anuncio(String s) { banner = ano + ": " + s; bannerT = 5; }
    void setFato(int i) { fatoIdx = i; fatoT = 14; }

    void update(double dt) {
        anim += dt;
        if (estado != 1 || pausado) return;
        tempoJogo += dt;
        bannerT -= dt; toastT -= dt; fatoT -= dt;
        if (fatoT <= 0) setFato(rnd.nextInt(FATOS.length));
        acc += dt;
        while (acc >= 1 && estado == 1) { acc -= 1; tick(); }
        moverPessoas(dt);
    }

    void tick() {
        ticks++;
        ouro += qtd[MERCADO] * 3 + qtd[MESQUITA] * 2 + qtd[ESCOLA] * 2 + pop * 0.15;
        comida += qtd[FAZENDA] * 2.0 - pop * 0.05;
        agua += qtd[POCO] * 2.0 - pop * 0.05;
        ouro = Math.min(estoque(), ouro);
        comida = Math.max(0, Math.min(estoque(), comida));
        agua = Math.max(0, Math.min(estoque(), agua));
        if (ticks % 4 == 0 && pop < capacidade() && comida > 3 && agua > 3) pop++;
        if ((comida <= 0 || agua <= 0) && ticks % 3 == 0 && pop > 1) { pop--; aviso("Falta comida ou água! Moradores estão partindo."); }
        else if (ticks % 5 == 0 && comida < 8) aviso("Poucos alimentos! Construa fazendas.");
        else if (ticks % 5 == 0 && agua < 8) aviso("Pouca água! Construa poços.");
        if (ticks % TICKS_ANO == 0) { ano++; evento(); }
        if (pop >= 50 && total() >= 5 && comida > 0 && agua > 0) estado = 2;
        else if (ano > ANO_FINAL) estado = 3;
    }

    void evento() {
        if (ano == 1324) { anuncio("Mansa Musa parte em peregrinação a Meca com uma enorme caravana de ouro!"); setFato(1); return; }
        if (rnd.nextInt(100) >= 55) return;
        switch (rnd.nextInt(4)) {
            case 0: { int a = 25 + qtd[FAZENDA] * 15; comida = Math.min(estoque(), comida + a); anuncio("Boa colheita! +" + a + " alimentos."); break; }
            case 1: { int a = 40 + qtd[MERCADO] * 20; ouro = Math.min(estoque(), ouro + a); anuncio("Chegada de comerciantes! +" + a + " ouro."); setFato(0); break; }
            case 2: { agua = Math.max(0, agua - 20); anuncio("Seca! A água diminuiu. Poços ajudam muito."); break; }
            default: {
                int a = Math.min(5, capacidade() - pop);
                if (a > 0) { pop += a; anuncio("Crescimento populacional! +" + a + " moradores chegaram."); }
                else anuncio("Famílias querem chegar, mas faltam casas!");
            }
        }
    }

    void moverPessoas(double dt) {
        int quer = Math.min(14, 2 + pop / 4);
        while (pessoas.size() < quer) { Walker w = new Walker(); Color[] cs = {new Color(40, 80, 160), new Color(240, 235, 220), new Color(200, 120, 40), new Color(150, 50, 50), new Color(60, 130, 90)}; w.robe = cs[rnd.nextInt(cs.length)]; reposicionar(w); pessoas.add(w); }
        while (pessoas.size() > quer) pessoas.remove(pessoas.size() - 1);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (Walker w : pessoas) {
            if (!livre((int) Math.round(w.x), (int) Math.round(w.y))) reposicionar(w);
            if (!livre(w.tx, w.ty)) { w.tx = (int) Math.round(w.x); w.ty = (int) Math.round(w.y); }
            double dx = w.tx - w.x, dy = w.ty - w.y, d = Math.hypot(dx, dy), st = dt * 0.8;
            if (d <= st) {
                w.x = w.tx; w.y = w.ty;
                int s = rnd.nextInt(4);
                for (int i = 0; i < 4; i++) { int[] dd = dirs[(s + i) % 4]; if (livre(w.tx + dd[0], w.ty + dd[1])) { w.tx += dd[0]; w.ty += dd[1]; break; } }
            } else { w.x += dx / d * st; w.y += dy / d * st; }
        }
    }

    void reposicionar(Walker w) {
        for (int i = 0; i < 300; i++) {
            int x = rnd.nextInt(N), y = rnd.nextInt(N);
            if (livre(x, y)) { w.x = w.tx = x; w.y = w.ty = y; return; }
        }
    }

    int[] pick(double px, double py) {
        double u = (px - OX) / (TW / 2.0), v = (py - OY - TH / 2.0) / (TH / 2.0);
        int gx = (int) Math.round((u + v) / 2), gy = (int) Math.round((v - u) / 2);
        return (gx >= 0 && gy >= 0 && gx < N && gy < N) ? new int[]{gx, gy} : null;
    }

    // ===== Desenho =====
    @Override protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        g.setColor(new Color(40, 25, 15));
        g.fillRect(0, 0, getWidth(), getHeight());
        escala = Math.min(getWidth() / (double) W, getHeight() / (double) H);
        offX = (getWidth() - W * escala) / 2; offY = (getHeight() - H * escala) / 2;
        g.translate(offX, offY);
        g.scale(escala, escala);
        g.setClip(0, 0, W, H);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        btns.clear();
        uiAtiva = estado == 1;
        desenharMundo(g);
        desenharHud(g);
        desenharObjetivos(g);
        desenharPaleta(g);
        desenharFato(g);
        desenharMensagens(g);
        uiAtiva = true;
        if (estado == 0) overlay(g, "Império do Mali", new String[]{
                "Ano de 1312. Você governa uma pequena aldeia no reinado de Mansa Musa.",
                "COMO JOGAR: escolha uma construção na barra de baixo e clique no mapa.",
                "META: 50 habitantes, 5 construções e comida e água sobrando.",
                "Cuidado com a seca! Construa fazendas e poços."}, "JOGAR", () -> reset());
        else if (estado == 2) overlay(g, "Cidade próspera!", new String[]{
                "Sua aldeia virou uma cidade próspera no ano de " + ano + " d.C.!",
                "População " + pop + "  ·  Ouro " + (int) ouro + "  ·  Alimentos " + (int) comida + "  ·  Água " + (int) agua,
                "Casas " + qtd[CASA] + "  ·  Fazendas " + qtd[FAZENDA] + "  ·  Mercados " + qtd[MERCADO] + "  ·  Poços " + qtd[POCO],
                "Mesquitas " + qtd[MESQUITA] + "  ·  Escolas " + qtd[ESCOLA] + "  ·  Armazéns " + qtd[ARMAZEM] + "  ·  Tempo " + (int) tempoJogo / 60 + ":" + String.format("%02d", (int) tempoJogo % 60),
                "A riqueza do Mali dependeu do trabalho de muita gente, inclusive de pessoas escravizadas. Conhecer essa história faz parte de entender o passado."}, "JOGAR DE NOVO", () -> reset());
        else if (estado == 3) overlay(g, "O reinado terminou", new String[]{
                "Chegamos a 1337 e Mansa Musa deixou o trono. Sua cidade ainda não prosperou.",
                "População " + pop + "/50  ·  Construções " + total() + "/5  ·  Ouro " + (int) ouro,
                "Dica: casas aumentam a população; fazendas e poços mantêm todos vivos.",
                "Tente de novo!"}, "JOGAR DE NOVO", () -> reset());
        g.dispose();
    }

    static Color sombra(Color c, double f) { return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f), c.getAlpha()); }
    static Polygon losango(int cx, int cy, int hw, int hh) { return new Polygon(new int[]{cx, cx + hw, cx, cx - hw}, new int[]{cy - hh, cy, cy + hh, cy}, 4); }

    void desenharMundo(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(246, 226, 176), 0, H, new Color(214, 176, 118)));
        g.fillRect(0, 0, W, H);
        g.setColor(new Color(255, 240, 190, 160)); g.fillOval(780, 70, 70, 70);
        g.setColor(new Color(200, 160, 100, 90)); g.fillOval(-120, 470, 520, 120); g.fillOval(640, 490, 560, 110);
        // espessura do terreno
        int L = OX - N * TW / 2, R = OX + N * TW / 2, MY = OY + N * TH / 2, B = OY + N * TH;
        g.setColor(new Color(140, 98, 56)); g.fillPolygon(new int[]{L, OX, OX, L}, new int[]{MY, B, B + 16, MY + 16}, 4);
        g.setColor(new Color(112, 76, 44)); g.fillPolygon(new int[]{R, OX, OX, R}, new int[]{MY, B, B + 16, MY + 16}, 4);
        int canoa = (int) ((anim * 0.35) % N);
        for (int gy = 0; gy < N; gy++)
            for (int gx = 0; gx < N; gx++) {
                int cx = OX + (gx - gy) * TW / 2, cy = OY + TH / 2 + (gx + gy) * TH / 2;
                tile(g, gx, gy, cx, cy);
                if (gy == 0 && gx == canoa) desenharCanoa(g, cx, cy);
                int t = grid[gx][gy];
                if (t >= 0) {
                    double k = Math.min(1, (anim - nasceu[gx][gy]) / 0.6);
                    desenharObra(g, t, cx, cy, k * (2 - k), gx + gy);
                } else if (livre(gx, gy)) {
                    int h = (gx * 31 + gy * 17 + gx * gy) % 9;
                    if (h == 0) palmeira(g, cx, cy + 4, gx);
                    else if (h == 4) { g.setColor(new Color(110, 140, 70)); g.fillOval(cx - 8, cy - 6, 14, 9); g.setColor(new Color(130, 165, 80)); g.fillOval(cx - 2, cy - 8, 12, 8); }
                }
                if (estado == 1 && selecionado >= 0 && gx == hx && gy == hy) {
                    boolean ok = livre(gx, gy) && ouro >= CUSTO[selecionado];
                    if (grid[gx][gy] < 0 && !rio(gx, gy)) {
                        g.setComposite(AlphaComposite.SrcOver.derive(0.6f));
                        desenharObra(g, selecionado, cx, cy, 1, gx + gy);
                        g.setComposite(AlphaComposite.SrcOver);
                    }
                    g.setColor(ok ? new Color(80, 220, 80, 110) : new Color(230, 60, 60, 130));
                    g.fillPolygon(losango(cx, cy, TW / 2, TH / 2));
                    g.setColor(ok ? new Color(30, 140, 30) : new Color(160, 20, 20));
                    g.setStroke(new BasicStroke(2)); g.drawPolygon(losango(cx, cy, TW / 2, TH / 2));
                }
                for (Walker w : pessoas) if (Math.round(w.x) == gx && Math.round(w.y) == gy) desenharPessoa(g, w);
            }
        g.setStroke(new BasicStroke(1));
        if (pausado && estado == 1) { g.setFont(new Font("Serif", Font.BOLD, 46)); g.setColor(new Color(60, 35, 15, 200)); centro(g, "PAUSADO", W / 2, 330); }
    }

    void tile(Graphics2D g, int gx, int gy, int cx, int cy) {
        Polygon p = losango(cx, cy, TW / 2, TH / 2);
        if (rio(gx, gy)) {
            g.setColor(new Color(64, 144, 196)); g.fillPolygon(p);
            g.setColor(new Color(150, 210, 240, 200)); g.setStroke(new BasicStroke(2));
            for (int i = 0; i < 2; i++) {
                double f = ((anim * 0.5 + i * 0.5 + gx * 0.13) % 1.0);
                int x = (int) (cx - 24 + f * 40), y = cy - 4 + i * 8;
                g.drawLine(x, y, x + 10, y);
            }
            g.setStroke(new BasicStroke(1));
            return;
        }
        int h = (gx * 73 + gy * 151 + gx * gy * 7) % 11;
        g.setColor(h < 3 ? new Color(228, 200, 144) : h < 8 ? new Color(236, 210, 154) : new Color(214, 204, 130));
        g.fillPolygon(p);
        // Margem do rio bem verde; savana seca no interior (textura de grama sobre a areia)
        String[] gr = {"grama_clara", "grama_escura", "grama_seca"};
        float a = gy <= 2 ? 0.85f : gy <= 3 ? 0.55f : 0.28f + (h % 3) * 0.06f;
        texLosango(g, gy <= 2 ? gr[(gx + gy) % 2] : gr[2], cx, cy, TW / 2, TH / 2, a);
        g.setColor(new Color(190, 154, 100, 90)); g.drawPolygon(p);
    }

    void desenharCanoa(Graphics2D g, int cx, int cy) {
        g.setColor(new Color(0, 0, 0, 40)); g.fillOval(cx - 16, cy - 2, 32, 9);
        g.setColor(new Color(120, 78, 40)); g.fillOval(cx - 16, cy - 6, 32, 9);
        g.setColor(new Color(60, 40, 20)); g.fillOval(cx - 11, cy - 5, 22, 5);
        g.setColor(new Color(240, 235, 220)); g.fillRoundRect(cx - 3, cy - 16, 7, 11, 4, 4);
        g.setColor(new Color(110, 70, 45)); g.fillOval(cx - 2, cy - 21, 6, 6);
    }

    void palmeira(Graphics2D g, int cx, int cy, int seed) {
        double sw = Math.sin(anim * 1.6 + seed) * 3;
        g.setStroke(new BasicStroke(3.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(120, 82, 46));
        double tx = cx + 4 + sw * 0.5, ty = cy - 36;
        g.draw(new QuadCurve2D.Double(cx, cy, cx - 4, cy - 18, tx, ty));
        g.setColor(new Color(50, 140, 60)); g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 7; i++) {
            double a = i * Math.PI * 2 / 7 + sw * 0.03;
            g.draw(new QuadCurve2D.Double(tx, ty, tx + Math.cos(a) * 12, ty - 9 + Math.sin(a) * 3, tx + Math.cos(a) * 22, ty + 4 + Math.sin(a) * 7));
        }
        g.setStroke(new BasicStroke(1));
    }

    void desenharPessoa(Graphics2D g, Walker w) {
        int cx = (int) (OX + (w.x - w.y) * TW / 2), cy = (int) (OY + TH / 2 + (w.x + w.y) * TH / 2);
        double bob = Math.abs(Math.sin(anim * 9 + w.ph)) * 2;
        g.setColor(new Color(0, 0, 0, 50)); g.fillOval(cx - 5, cy - 2, 10, 5);
        g.setColor(w.robe); g.fillRoundRect(cx - 4, (int) (cy - 14 - bob), 8, 13, 4, 4);
        g.setColor(new Color(110, 70, 45)); g.fillOval(cx - 3, (int) (cy - 19 - bob), 6, 6);
    }

    // ----- construções -----
    void caixa(Graphics2D g, int cx, int cy, int hw, int hh, int h, Color top) {
        g.setColor(sombra(top, .82)); g.fillPolygon(new int[]{cx - hw, cx, cx, cx - hw}, new int[]{cy, cy + hh, cy + hh - h, cy - h}, 4);
        g.setColor(sombra(top, .66)); g.fillPolygon(new int[]{cx + hw, cx, cx, cx + hw}, new int[]{cy, cy + hh, cy + hh - h, cy - h}, 4);
        g.setColor(top); g.fillPolygon(new int[]{cx, cx + hw, cx, cx - hw}, new int[]{cy - hh - h, cy - h, cy + hh - h, cy - h}, 4);
    }

    /** Caixa com textura no topo (ex.: vigas de madeira, tijolos). */
    void caixaT(Graphics2D g, int cx, int cy, int hw, int hh, int h, Color top, String tex, float alfa) {
        caixa(g, cx, cy, hw, hh, h, top);
        texLosango(g, tex, cx, cy - h, hw, hh, alfa);
    }

    void faceD(Graphics2D g, int cx, int cy, int hw, int hh, double t0, double t1, int z0, int z1, Color c) {
        g.setColor(c);
        g.fillPolygon(new int[]{(int) (cx + t0 * hw), (int) (cx + t1 * hw), (int) (cx + t1 * hw), (int) (cx + t0 * hw)},
                new int[]{(int) (cy + hh - t0 * hh - z0), (int) (cy + hh - t1 * hh - z0), (int) (cy + hh - t1 * hh - z1), (int) (cy + hh - t0 * hh - z1)}, 4);
    }

    void faceE(Graphics2D g, int cx, int cy, int hw, int hh, double t0, double t1, int z0, int z1, Color c) {
        g.setColor(c);
        g.fillPolygon(new int[]{(int) (cx - hw + t0 * hw), (int) (cx - hw + t1 * hw), (int) (cx - hw + t1 * hw), (int) (cx - hw + t0 * hw)},
                new int[]{(int) (cy + t0 * hh - z0), (int) (cy + t1 * hh - z0), (int) (cy + t1 * hh - z1), (int) (cy + t0 * hh - z1)}, 4);
    }

    void faixa(Graphics2D g, int cx, int cy, int hw, int hh, double u0, double u1, double v0, double v1, Color c) {
        int[] xs = new int[4], ys = new int[4];
        double[][] uv = {{u0, v0}, {u1, v0}, {u1, v1}, {u0, v1}};
        for (int i = 0; i < 4; i++) { xs[i] = (int) (cx + (uv[i][0] - uv[i][1]) * hw); ys[i] = (int) (cy - hh + (uv[i][0] + uv[i][1]) * hh); }
        g.setColor(c); g.fillPolygon(xs, ys, 4);
    }

    void cilindro(Graphics2D g, int cx, int cy, int rx, int ry, int h, Color lado, Color topo) {
        g.setColor(sombra(lado, .8)); g.fillOval(cx - rx, cy - ry, 2 * rx, 2 * ry);
        g.setColor(lado); g.fillRect(cx - rx, cy - h, 2 * rx, h);
        g.setColor(topo); g.fillOval(cx - rx, cy - h - ry, 2 * rx, 2 * ry);
    }

    void celeiro(Graphics2D g, int cx, int cy, int rx, int ry, int h, int cone) {
        cilindro(g, cx, cy, rx, ry, h, new Color(206, 160, 100), new Color(220, 176, 116));
        g.setColor(new Color(196, 156, 64));
        g.fillOval(cx - rx - 3, cy - h - ry - 1, 2 * rx + 6, 2 * ry + 2);
        g.fillPolygon(new int[]{cx - rx - 3, cx + rx + 3, cx}, new int[]{cy - h, cy - h, cy - h - cone}, 3);
        g.setColor(new Color(150, 112, 40)); g.drawLine(cx - rx / 2, cy - h, cx, cy - h - cone); g.drawLine(cx + rx / 2, cy - h, cx, cy - h - cone);
    }

    void desenharObra(Graphics2D g, int t, int cx, int cy, double k, int v) {
        Color escuro = new Color(70, 40, 25);
        if (t != FAZENDA) { g.setColor(new Color(0, 0, 0, 40)); g.fillOval(cx - 30, cy - 8, 60, 24); }
        // piso de cada obra: tijolo (praça), madeira (tablado)
        if (t == MERCADO) texLosango(g, "tijolo_b", cx, cy, 38, 19, 1f);
        else if (t == POCO) texLosango(g, "tijolo_a", cx, cy, 32, 16, 1f);
        else if (t == MESQUITA) texLosango(g, "tijolo_a", cx, cy, 38, 19, 1f);
        else if (t == ESCOLA) texLosango(g, "madeira_cinza", cx, cy, 36, 18, 1f);
        else if (t == ARMAZEM) texLosango(g, "madeira_marrom", cx, cy, 36, 18, 1f);
        if (t == MERCADO || t == POCO || t == MESQUITA || t == ESCOLA || t == ARMAZEM) {
            g.setColor(new Color(60, 40, 20, 150)); g.setStroke(new BasicStroke(2)); g.drawPolygon(losango(cx, cy, 36, 18)); g.setStroke(new BasicStroke(1));
        }
        switch (t) {
            case CASA: {
                Color c = new Color(198, 142, 90);
                int h = (int) (24 * k);
                caixaT(g, cx, cy, 22, 11, h, c, "madeira_marrom", .55f);
                if (v % 2 == 0) caixaT(g, cx, cy - h, 12, 6, (int) (14 * k), new Color(206, 152, 100), "madeira_laranja", .5f);
                else { g.setColor(sombra(c, .75)); g.drawPolygon(losango(cx, cy - h, 22, 11)); }
                if (k >= 1) icone(g, "barril", cx + 26, cy + 4, 18, 0);
                faceD(g, cx, cy, 22, 11, .35, .65, 0, (int) (14 * k), escuro);
                faceE(g, cx, cy, 22, 11, .3, .55, (int) (11 * k), (int) (18 * k), escuro);
                break;
            }
            case FAZENDA: {
                g.setColor(new Color(100, 72, 40)); g.fillPolygon(losango(cx, cy, 34, 17));
                texLosango(g, "grama_seca", cx, cy, 34, 17, .35f);
                for (int i = 0; i < 5; i++) {
                    double v0 = 0.1 + i * 0.18;
                    faixa(g, cx, cy, 34, 17, .08, .92, v0, v0 + .1, new Color(92, 165, 58));
                    faixa(g, cx, cy, 34, 17, .08, .92, v0 + .1, v0 + .12, new Color(170, 200, 80));
                }
                g.setColor(new Color(90, 60, 30)); g.setStroke(new BasicStroke(2)); g.drawPolygon(losango(cx, cy, 34, 17)); g.setStroke(new BasicStroke(1));
                if (k >= 1) { icone(g, "forcado", cx - 26, cy - 2, 24, -0.2); icone(g, "foice", cx + 24, cy + 2, 20, 0.3); }
                break;
            }
            case MERCADO: {
                caixaT(g, cx, cy, 28, 14, 4, new Color(214, 184, 130), "madeira_laranja", .8f);
                caixa(g, cx + 9, cy + 1, 6, 3, (int) (6 * k), new Color(230, 150, 50));
                if (k >= 1) { icone(g, "barril", cx - 14, cy + 2, 20, 0); icone(g, "barril", cx - 2, cy + 8, 18, 0); icone(g, "arco", cx + 20, cy - 2, 22, 0.15); }
                int z = (int) (28 * k), top = cy - 4 - z;
                g.setColor(new Color(100, 70, 40)); g.setStroke(new BasicStroke(2));
                g.drawLine(cx - 24, cy - 4, cx - 24, top); g.drawLine(cx, cy + 10, cx, top + 14); g.drawLine(cx + 24, cy - 4, cx + 24, top);
                g.setStroke(new BasicStroke(1));
                for (int i = 0; i < 6; i++) faixa(g, cx, top, 28, 14, i / 6.0, (i + 1) / 6.0, 0, 1, i % 2 == 0 ? new Color(200, 70, 50) : new Color(250, 240, 220));
                g.setColor(new Color(200, 70, 50)); g.fillPolygon(new int[]{cx - 28, cx, cx, cx - 28}, new int[]{top, top + 14, top + 19, top + 5}, 4);
                g.setColor(new Color(240, 230, 210)); g.fillPolygon(new int[]{cx + 28, cx, cx, cx + 28}, new int[]{top, top + 14, top + 19, top + 5}, 4);
                break;
            }
            case POCO: {
                int h = (int) (10 * k);
                cilindro(g, cx, cy, 15, 8, h, new Color(150, 140, 125), new Color(180, 170, 150));
                texElipse(g, "tijolo_a", cx, cy - h, 15, 8, .95f);
                g.setColor(new Color(50, 120, 180)); g.fillOval(cx - 10, cy - h - 5, 20, 10);
                g.setColor(new Color(100, 70, 40)); g.setStroke(new BasicStroke(2.5f));
                int a = (int) (28 * k);
                g.drawLine(cx - 13, cy - h, cx - 13, cy - h - a); g.drawLine(cx + 13, cy - h, cx + 13, cy - h - a); g.drawLine(cx - 15, cy - h - a, cx + 15, cy - h - a);
                g.setStroke(new BasicStroke(1)); g.drawLine(cx, cy - h - a, cx, cy - h - a / 2);
                g.setColor(new Color(130, 90, 50)); g.fillRect(cx - 4, cy - h - a / 2, 8, 6);
                if (k >= 1) icone(g, "picareta", cx + 24, cy + 4, 20, 0.35);
                break;
            }
            case MESQUITA: {
                Color c = new Color(222, 186, 128);
                caixa(g, cx, cy - 12, 9, 5, (int) (58 * k), c);
                int ty = cy - 12 - (int) (58 * k);
                g.setColor(new Color(90, 60, 35)); g.setStroke(new BasicStroke(2));
                for (int z : new int[]{16, 30, 44}) { int yy = cy - 12 - (int) (z * k); g.drawLine(cx - 9, yy, cx - 14, yy - 1); g.drawLine(cx + 9, yy, cx + 14, yy - 1); }
                g.setStroke(new BasicStroke(1));
                g.setColor(new Color(205, 165, 105)); g.fillPolygon(new int[]{cx - 9, cx + 9, cx}, new int[]{ty, ty, ty - 14}, 3);
                g.setColor(Color.WHITE); g.fillOval(cx - 3, ty - 20, 6, 8);
                int h = (int) (28 * k);
                caixaT(g, cx, cy + 2, 30, 15, h, c, "tijolo_b", .45f);
                caixaT(g, cx, cy + 2 - h, 18, 9, (int) (10 * k), new Color(230, 196, 138), "tijolo_a", .4f);
                g.setColor(new Color(238, 210, 150)); g.fillArc(cx - 10, cy + 2 - h - (int) (10 * k) - 10, 20, 20, 0, 180);
                faceD(g, cx, cy + 2, 30, 15, .38, .62, 0, (int) (15 * k), escuro);
                faceE(g, cx, cy + 2, 30, 15, .25, .4, (int) (12 * k), (int) (22 * k), escuro);
                faceE(g, cx, cy + 2, 30, 15, .6, .75, (int) (12 * k), (int) (22 * k), escuro);
                break;
            }
            case ESCOLA: {
                Color c = new Color(232, 208, 158);
                int h = (int) (22 * k);
                caixaT(g, cx, cy, 26, 13, h, c, "madeira_laranja", .6f);
                caixaT(g, cx, cy - h, 14, 7, (int) (6 * k), new Color(240, 220, 170), "madeira_cinza", .6f);
                faceE(g, cx, cy, 26, 13, .22, .42, (int) (9 * k), (int) (17 * k), new Color(50, 70, 110));
                faceE(g, cx, cy, 26, 13, .55, .75, (int) (9 * k), (int) (17 * k), new Color(50, 70, 110));
                faceD(g, cx, cy, 26, 13, .4, .65, 0, (int) (14 * k), new Color(60, 100, 160));
                int by = cy - h - (int) (6 * k);
                g.setColor(new Color(90, 60, 35)); g.setStroke(new BasicStroke(2)); g.drawLine(cx, by, cx, by - 28); g.setStroke(new BasicStroke(1));
                g.setColor(new Color(40, 150, 90)); g.fillPolygon(new int[]{cx, cx + 16, cx}, new int[]{by - 28, by - 23, by - 17}, 3);
                break;
            }
            default: {
                celeiro(g, cx + 10, cy - 5, 13, 6, (int) (16 * k), (int) (16 * k));
                celeiro(g, cx - 10, cy + 5, 14, 7, (int) (18 * k), (int) (17 * k));
                caixaT(g, cx + 17, cy + 9, 8, 4, (int) (8 * k), new Color(160, 110, 60), "madeira_marrom", .8f);
                if (k >= 1) { icone(g, "barril", cx - 28, cy + 8, 20, 0); icone(g, "barril", cx + 30, cy + 2, 18, 0); }
            }
        }
        // ferramenta batendo enquanto a obra sobe
        if (k < 1) {
            String f = t == FAZENDA ? "foice" : (t == POCO || t == MESQUITA || t == ARMAZEM) ? "picareta" : "machado";
            icone(g, f, cx + 16, cy - 36 - (int) (22 * k), 30, Math.sin(anim * 25) * 0.8);
        }
    }

    // ----- interface -----
    void centro(Graphics2D g, String s, int cx, int y) { g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y); }

    void caixaUI(Graphics2D g, int x, int y, int w, int h, Color c) {
        g.setColor(new Color(0, 0, 0, 60)); g.fillRoundRect(x + 2, y + 3, w, h, 14, 14);
        g.setColor(c); g.fillRoundRect(x, y, w, h, 14, 14);
        g.setColor(new Color(40, 25, 12)); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, w, h, 14, 14); g.setStroke(new BasicStroke(1));
    }

    void botao(Graphics2D g, int x, int y, int w, int h, String txt, Color c, Runnable a) {
        Rectangle r = new Rectangle(x, y, w, h);
        boolean hov = uiAtiva && r.contains(mx, my);
        caixaUI(g, x, y, w, h, hov ? c.brighter() : c);
        g.setFont(new Font("SansSerif", Font.BOLD, 14)); g.setColor(Color.WHITE);
        centro(g, txt, x + w / 2, y + h / 2 + 5);
        if (uiAtiva) btns.add(new Btn(r, a));
    }

    void chip(Graphics2D g, int x, int y, String nome, String valor, String max, int tipo, boolean alerta) {
        caixaUI(g, x, y, 150, 40, new Color(58, 36, 18));
        if (alerta && (int) (anim * 3) % 2 == 0) { g.setColor(new Color(230, 60, 40)); g.setStroke(new BasicStroke(2)); g.drawRoundRect(x, y, 150, 40, 14, 14); g.setStroke(new BasicStroke(1)); }
        int ix = x + 22, iy = y + 20;
        Color[] cs = {OURO, new Color(120, 180, 60), new Color(70, 160, 230), new Color(230, 140, 90)};
        g.setColor(cs[tipo]); g.fillOval(ix - 13, iy - 13, 26, 26);
        g.setColor(new Color(255, 255, 255, 220));
        if (tipo == 0) { g.setColor(new Color(170, 120, 10)); g.setStroke(new BasicStroke(2)); g.drawOval(ix - 8, iy - 8, 16, 16); g.setStroke(new BasicStroke(1)); }
        else if (tipo == 1) { g.setStroke(new BasicStroke(2)); g.setColor(new Color(240, 230, 120)); for (int i = -1; i <= 1; i++) g.drawLine(ix + i * 5, iy + 7, ix + i * 5, iy - 5 - (i == 0 ? 2 : 0)); g.setStroke(new BasicStroke(1)); }
        else if (tipo == 2) { g.fillPolygon(new int[]{ix, ix - 6, ix + 6}, new int[]{iy - 9, iy, iy}, 3); g.fillOval(ix - 6, iy - 4, 12, 12); }
        else { g.fillOval(ix - 4, iy - 9, 8, 8); g.fillArc(ix - 8, iy, 16, 14, 0, 180); }
        g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.setColor(new Color(230, 210, 170)); g.drawString(nome, x + 42, y + 15);
        g.setFont(new Font("SansSerif", Font.BOLD, 18)); g.setColor(alerta ? new Color(255, 120, 100) : Color.WHITE); g.drawString(valor, x + 42, y + 34);
        int vw = g.getFontMetrics().stringWidth(valor);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.setColor(new Color(200, 180, 140)); g.drawString(max, x + 44 + vw, y + 34);
    }

    void desenharHud(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(110, 70, 36), 0, 58, MARROM)); g.fillRect(0, 0, W, 58);
        g.setColor(new Color(40, 25, 12)); g.fillRect(0, 58, W, 3);
        chip(g, 16, 9, "Ouro", "" + (int) ouro, "/" + estoque(), 0, false);
        chip(g, 176, 9, "Alimentos", "" + (int) comida, "/" + estoque(), 1, comida < 10);
        chip(g, 336, 9, "Água", "" + (int) agua, "/" + estoque(), 2, agua < 10);
        chip(g, 496, 9, "População", "" + pop, "/" + capacidade(), 3, false);
        caixaUI(g, 662, 9, 132, 40, new Color(58, 36, 18));
        g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.setColor(new Color(230, 210, 170)); g.drawString("ANO", 674, 24);
        g.setFont(new Font("SansSerif", Font.BOLD, 18)); g.setColor(Color.WHITE); g.drawString(ano + " d.C.", 674, 42);
        double prog = Math.min(1, ((ano - ANO_INICIAL) * TICKS_ANO + ticks % TICKS_ANO) / (double) ((ANO_FINAL - ANO_INICIAL + 1) * TICKS_ANO));
        g.setColor(new Color(120, 90, 50)); g.fillRect(724, 20, 60, 5); g.setColor(OURO); g.fillRect(724, 20, (int) (60 * prog), 5);
        botao(g, 806, 9, 88, 40, pausado ? "Continuar" : "Pausar", new Color(60, 120, 80), () -> pausado = !pausado);
        botao(g, 902, 9, 88, 40, "Reiniciar", new Color(160, 70, 50), () -> reset());
    }

    void desenharObjetivos(Graphics2D g) {
        caixaUI(g, 16, 74, 216, 112, new Color(74, 46, 24, 225));
        g.setFont(new Font("Serif", Font.BOLD, 16)); g.setColor(OURO); g.drawString("Objetivos", 30, 98);
        String[] t = {"50 habitantes  (" + pop + ")", "5 construções  (" + total() + ")", "Comida e água positivas"};
        boolean[] ok = {pop >= 50, total() >= 5, comida > 0 && agua > 0};
        g.setFont(new Font("SansSerif", Font.PLAIN, 13));
        for (int i = 0; i < 3; i++) {
            int y = 120 + i * 24;
            g.setColor(ok[i] ? new Color(90, 200, 90) : new Color(120, 90, 60)); g.fillOval(30, y - 12, 16, 16);
            if (ok[i]) { g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2)); g.drawLine(34, y - 4, 37, y - 1); g.drawLine(37, y - 1, 43, y - 8); g.setStroke(new BasicStroke(1)); }
            g.setColor(CREME); g.drawString(t[i], 54, y);
        }
    }

    void desenharPaleta(Graphics2D g) {
        for (int i = 0; i < 7; i++) {
            int x = 21 + i * 138, y = 578, w = 130, h = 66;
            Rectangle r = new Rectangle(x, y, w, h);
            boolean sel = selecionado == i, pode = ouro >= CUSTO[i], hov = uiAtiva && r.contains(mx, my);
            int dy = sel ? -6 : 0;
            caixaUI(g, x, y + dy, w, h, hov ? new Color(250, 238, 205) : CREME);
            if (sel) { g.setColor(OURO); g.setStroke(new BasicStroke(4)); g.drawRoundRect(x, y + dy, w, h, 14, 14); g.setStroke(new BasicStroke(1)); }
            g.setColor(COR[i]); g.fillRoundRect(x + 6, y + dy + 7, 34, 34, 8, 8);
            g.setColor(new Color(60, 40, 20)); g.drawRoundRect(x + 6, y + dy + 7, 34, 34, 8, 8);
            String[] ico = {"machado", "foice", "barril", "picareta", null, null, "forcado"};
            if (ico[i] != null) icone(g, ico[i], x + 23, y + dy + 24, 32, 0);
            else if (i == MESQUITA) { g.setColor(Color.WHITE); g.fillOval(x + 14, y + dy + 14, 18, 18); g.setColor(COR[i]); g.fillOval(x + 19, y + dy + 12, 17, 17); }
            else { g.setColor(Color.WHITE); g.fillRect(x + 11, y + dy + 16, 12, 16); g.fillRect(x + 24, y + dy + 16, 12, 16); g.setColor(new Color(60, 110, 170)); g.drawLine(x + 23, y + dy + 16, x + 23, y + dy + 32); }
            g.setFont(new Font("SansSerif", Font.BOLD, 14)); g.setColor(MARROM); g.drawString(NOME[i], x + 46, y + dy + 22);
            g.setFont(new Font("SansSerif", Font.BOLD, 13)); g.setColor(pode ? new Color(150, 110, 0) : new Color(190, 50, 40)); g.drawString(CUSTO[i] + " ouro", x + 46, y + dy + 38);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11)); g.setColor(new Color(100, 75, 50)); g.drawString(DESC[i], x + 10, y + dy + 56);
            final int idx = i;
            if (uiAtiva) btns.add(new Btn(r, () -> selecionado = selecionado == idx ? -1 : idx));
        }
    }

    java.util.List<String> quebrar(String s, FontMetrics fm, int w) {
        java.util.List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String p : s.split(" ")) {
            if (cur.length() > 0 && fm.stringWidth(cur + " " + p) > w) { out.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append(' ');
            cur.append(p);
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    void desenharFato(Graphics2D g) {
        caixaUI(g, 20, 652, 960, 62, new Color(74, 46, 24));
        g.setFont(new Font("SansSerif", Font.BOLD, 12)); g.setColor(OURO); g.drawString("VOCÊ SABIA?", 34, 670);
        g.setFont(new Font("SansSerif", Font.PLAIN, 13)); g.setColor(CREME);
        int y = 687;
        for (String l : quebrar(FATOS[fatoIdx], g.getFontMetrics(), 925)) { g.drawString(l, 34, y); y += 16; }
    }

    void desenharMensagens(Graphics2D g) {
        if (estado != 1) return;
        g.setFont(new Font("SansSerif", Font.BOLD, 15));
        if (bannerT > 0 && !banner.isEmpty()) {
            int w = g.getFontMetrics().stringWidth(banner) + 40, x = (W - w) / 2;
            caixaUI(g, x, 68, w, 34, new Color(255, 236, 170));
            g.setColor(MARROM); centro(g, banner, W / 2, 91);
        }
        if (toastT > 0 && !toast.isEmpty()) {
            int w = g.getFontMetrics().stringWidth(toast) + 40, x = (W - w) / 2;
            caixaUI(g, x, 530, w, 34, new Color(170, 60, 40));
            g.setColor(Color.WHITE); centro(g, toast, W / 2, 553);
        }
        if (selecionado < 0 && total() <= 3 && ticks < 25) {
            g.setColor(new Color(60, 35, 15, 220)); g.setFont(new Font("SansSerif", Font.BOLD, 16));
            centro(g, "Escolha uma construção abaixo e clique no mapa!", W / 2, 566);
        }
    }

    void overlay(Graphics2D g, String titulo, String[] linhas, String bt, Runnable a) {
        g.setColor(new Color(0, 0, 0, 150)); g.fillRect(0, 0, W, H);
        caixaUI(g, 190, 110, 620, 470, CREME);
        g.setColor(new Color(160, 110, 50)); g.setStroke(new BasicStroke(3)); g.drawRoundRect(198, 118, 604, 454, 10, 10); g.setStroke(new BasicStroke(1));
        g.setFont(new Font("Serif", Font.BOLD, 38)); g.setColor(MARROM); centro(g, titulo, W / 2, 175);
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        int y = 225;
        for (String s : linhas) { for (String l : quebrar(s, g.getFontMetrics(), 540)) { g.setColor(new Color(70, 45, 25)); centro(g, l, W / 2, y); y += 24; } y += 8; }
        botao(g, 380, 500, 240, 52, bt, new Color(60, 120, 80), a);
    }

    // ===== Início =====
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame("Império do Mali – Construa sua cidade");
            f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            f.add(new MaliGame());
            f.pack();
            f.setLocationRelativeTo(null);
            f.setVisible(true);
        });
    }

    /** Texturas embutidas (assim o jogo funciona mesmo sem a pasta imagens/). */
    static final class Embutidas {
        static final String[][] DADOS = {
        {"grama_escura", "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"},
        {"grama_seca", "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"},
        {"grama_clara", "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"},
        {"madeira_marrom", "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"},
        {"madeira_cinza", "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"},
        {"madeira_laranja", "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"},
        {"tijolo_a", "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"},
        {"tijolo_b", "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"},
        {"picareta", "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAACCklEQVR42u2XP2gTYRjGf3dU6BAnG6cqEk/hcGmWlGwlQxQnNzEYcKgBISJSEUFQmk1IJ4dC6VCxaN2K1BIzlG7FILRLyNBLEdspbXAww019HdocV5o/3/W+Omjf6bj3+773eZ73z30HZ3Zm/7sZKotGhhPSzbexUzHCABhQCW6PjRK5cP6Yr9X8DatIG0guGReAmbV1Q6cC4gMDgD02ymX7irfgZ+0HtdVv5B9a7G21KC9vsrJbM7SloBsYP5BfS/MkbtkAjL/+cJKzA5sAMjKckKmFkqSituSScUlFbfED/Rt2BEQqasu9+4+lV+GGScExS0VtsaxBKtvnyBYL3vtGvcrX6U99u2RAhwSx65eAbd4/e0W2WODBnTSQPnBOI71AmGHZZ/JxhmIRHMdlY6fC96Uvnv/5xNO+Z5g6FNjbarGyWwMwPs6/JT/+xPNliwVU6yGQ5ZJxmZ3MyOxkplPVy9RCSZruvjTd/Z4Faepg38ka9ar3fPPR3a4qmCdlf1B48OLd507dZLx5OcHcYhmAi1dv6O+CoVikK3u/CnOLmotQgf0RFbR2weGYBaBSqinv89dD6BS02c+srStN0ka9yqESRqhR3Gafvn2t/blV2S/94gRSwLIGAVSDK60xg+a+vLypdaCZQdg7jhuEvTYAHnvdwQMp4DjuqdxmlAGcBvtAd79/9s/oD9oeyiiy1+E/AAAAAElFTkSuQmCC"},
        {"foice", "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAACAUlEQVR42u2WP2gTURzHPy9Y7NAMbbAgpFiaa0vQIQR64iIl51BwEg+hRLdKJ2cdFAo66OwmbhoqJdLFFhHPwUVJSi1iKVRb0j8SKOQcElwyPId6wfxpeXe8i0u/093j3u/zvd97v/d7cKL/LBFwntQVz/eEVNyUs/OvGu+r+ecA1CpV5l8+9R03kAHv2fnxuTF+7+79ViMizCVoMuMZ8Uz8+rZD3skpxRc6NlIqbsqgJiI6DKztF8Ts1VsAPH7yCID+C+eU5kZ0lVPeyTVMpO0ZAGwre1zF6DXgaaFY4sbEMAODMaUsaDWQd3LCmXsAwPDla0pZiBCyes4O0NUl8KtQDCwUS/z+udl9A7aVldbcQwBW3iwBUC+73TFgW1l56fZhGZY+LtIXi4baDbGtbNPO9uAHW+vsbpToi0WV+sKpoPBEJs1g4nzT+L9wlfQHMpCKmzKRSeMeVNj68KKt5Dx4rVJV6gXCL3zi+hVqlWrj746qc9WW7CcDMjl5keLr9wAY46Md4fWyq9yKlT7KnElKw+hlZGyId8vfcU9HMcZHjzqK9d2IPDCAOZWk8HYDgMJeD2v7BW1VJTqBAQyjF3MqeQj9C3/26YvWi0xbIAW4QLNEJ/jI2BAA25t7ocJbg8rpm3eol13cr6t46x8mvK0XtJ7fYcM7BZdh3JpPdJz+APLlvKzjtXLZAAAAAElFTkSuQmCC"},
        {"arco", "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAACNUlEQVR42uVXPUgcQRT+9jgsghYSLMRCPMEfEOSw2iIRYpNqIUhAj5PtLsRWtl+buLCkSHPhkusiwULTCTYaJEVA0BAQTo9jg4pYmGCREOyehe4yuzdzzuxPmjwYFh47773vm++9nQX+d9MyiEkqObW0kzqmETi95hmmFkrY//AxeL77+i1V4ASAHNMIlu9jl22VCQDVqi5FGconQVxfLgEA9rYaPjIWHe1ub+Do2MOLRQsAMDZaaAsUpwDiJbatMvqHJgGA/ETTM7NsUZQK5fXlElX0Ipdizgrt3d3eaPPnVJEzqDWf7jvkrE+TFVouQfJ/ZiHaGSWTiFZRHN6794mQHNPAT+9PILaLH99Rq7qICE2KkaNjTx29Yxr0pG+crTwqOimrVV12nxQD5JgGvOYZdi4bIpSyWqCx0YI/DzQlEbZa120+RvWJLZcAfSqWl0QfOjfeSE2zgAC9b/6YjaP8OB3AVX5E/UqdZFtl4V7ZSZhYA7a7yo0TLYAqejFEf1bq78jAyud9PP+0Gbp0sNNPkX4launtFw8H63VcHZ6gd2IQA12ngQBjiI9sqyykn9sFf8+buDo8uW03/TGW5p4CWI2jhXvRRwNSRS+i1brGcOkZkzyWAIPkndALB9H7N68w/+s3slK+VBt2P+xJlLx/aBJ3LHS8qnGPYOeygddrW3gwMIKXjwqxj6FWdbmjWyRmquhFdgIqf/NF/w3Rxd6M8hn/tvFikOi7kAZiqZtR1jmU7Aa/WDaPVcP4KQAAAABJRU5ErkJggg=="},
        {"forcado", "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAB6klEQVR42t1VPUgCURz/KbZELRoHEYQkhOLUUODi3CS0BIbgVJs0NBQ1NRQWSESBUC5BVEQQhIY0OOggmOioqYkUEVi6JBE0vBbf4Xlqeu+dRH+44d67e7+P/8cD/kmQ+tNzaHmALy6vKybFgwD0gkEGvrrl65sDLUMwWftCgDhdnpYbg2OTXaWE2YFxixHVcuW3mmibEi4pqFU+JO9mmx2fLznJmtE+x50Acbo8qJYrOD89AABNo/3ZeFTy8fy0UR0HmtSL9h/vb1NSHTtCy1p8zerNNjtSVwGZ/Zf3JXUc8F2ExQp3ujzIxqON6kX7S9Hrlv/rWAkkgyFKAslgSJYSav/OxoqEFDcCFJi2ZDOQYLJi17fHvQY09dxL5sFTpiRJCS3Iduq5zQE6jL5fq0gGQ/DHivDHigAga0eZEh43oV4w4DGSAgAMjOrFzSHDsKwguQ6iJdsU9IIB1XIFhYd8z+CsDhCv2wEAWDu5UXy+jkU9ANzd5pnEKE7BzKwFxdwzIm8ZJieVECCBzQW8F2soFL6YO0irBJxaz6q+VwIi+Nlhmgt4LwRE8EQ4ww28WwJiuyXCGRzF09zAuyFAvG4HRiaGVAHvdBgBAKqcV8F1Q4AAAB0ytM3UAm8+lLQBVg1cRkCF2/Lvxw+0UtOWfBSTUwAAAABJRU5ErkJggg=="},
        {"machado", "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAACF0lEQVR42u2WsUtbURTGfy+mtEOyvNSARUubpLHBRdsqZtAhipRmEXQolEKXunZw9A+QTg6ZrEtBQ5cMBXUR4uCS4lIXUUSzKAZi8xwSsDXQ26HeR5Ia++7LSwKlBx7c99699/vOd86958B/a7NpjSyeiQ4IOf6Q+aq1jEB/95AA0H8UmXjxCIDswbEtEi474INT4wxOjWPc9pI9OAYgEO4h1hkRTVWgv3tIhHp/exyMPTG/n6+tcHj4HYDNsz0NYHrslQBIpZOaowoA3OrSMfIFjHwBf7CP3nfzxBML8rcAxOL6sqW93HbAAUqFIgD7mS0AHkdHiScWiAP5o10Aom9fA4ibVHA3eoxKhSIen9ckIokBvJmcILO07EwSyviXc8aN8zw+r5IDDSmgCuZIEsockOCVkjeVwM7JtpZKJ80QSODrVFBRRlWBKhJWLJVOOhuCmeiAuWk9+XW/D4CPnzeczwGAWGeETyuJusfSH+yzvFeHIrDQdTfPhh/y89TF6pd1ng6PVM25vLjkXjjMzv4Rsy+f//W6dzlxEVWO70ceNPcYBsI9Ve8yFLX5YMV7WwTuBjzXqlDOGab3shY0RQHZfGye7ZkelnNGVXl+PzdrudQrEQiF7tS9G+yAKxGQ3c63bMlsPmpNFVxZgdoEbBTcVjWUPWANmO3u2rICV0n3x7jl5bhe/FtCoEL+9inQbhNXz79jvwDqSLtE4daN2gAAAABJRU5ErkJggg=="},
        {"barril", "iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAACIklEQVR42u2WsYsTQRTGf3ukSBEL7+JVbrO3WVhsTGEknahEOUQQrFSwy58itoIgnL3gPxByQYLdYYo7EWRxM06zNppgY4oVi7U4Z2836yazlz2uyat2Zt+875v3vTczsLa1nbMZp1gTlRnTKArcbTexHDP1Q/pB/C1ECMBw4mnFruiCJ4GlH2RIqLHlKEJupEOiUhT8ZJdBZmw5JnWrRt1yoe9pkdjQ2b4CH/TGDHrjXD/pB4z6HgCtuy5lSBB1282Uxv/0ZThhbu64PpJm29WlWagU2b0CV3bzkpsitXdwRLfd5M3LowSBxbZQgiRAEnx+Pum/d3AM3tltaMmwcVYHTN2qafktJSD9AOkHqXSqTCTlyGpPOQQsx8z0fLYgs4RUN6zUBcOJR4fGSW9DrHGePX96P07/qO/lZkm7C6QfxOAKQPpBhohqQV3ttQkIEWLJWRxYZcNyTKQfpCo9mfapnMX3wio1YKgUzgf8306FCJnKGVM5S9aEsfJlNOiN6ew2YiLz2clbU+ZtqB1Q16/IeyB6eOsxPz8dagW07SpChOw8esDrF8/KkUB8GePeuQ3A9/13qYNGiDAeCxFy8d4TrgH7r96W+iKKrl5uAeDeuA5AbesCAJvbW2zvXAHgx9fPMfDHbyOt+No1sPn7F7ZdZfT+w0K/lvlHq/2KEjCGEy+y7WZMJE8CqGq132luQ2PZMZw4qo0yu+BMn+VrO3f7C9X/9aSmmjLPAAAAAElFTkSuQmCC"}
        };
    }
}
