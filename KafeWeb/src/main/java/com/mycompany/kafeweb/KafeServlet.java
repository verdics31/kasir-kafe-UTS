package com.mycompany.kafeweb;   // sesuaikan dengan package project teman Anda

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import jakarta.servlet.annotation.WebServlet;      // Tomcat 9 ke bawah: ganti jadi javax.servlet...
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

// ===================== ABSTRAKSI + ENKAPSULASI =====================
abstract class ItemMenu {
    private String id;
    private String nama;
    private int harga;
    private int stok;

    public ItemMenu(String id, String nama, int harga, int stok) {
        this.id = id;
        this.nama = nama;
        setHarga(harga);
        setStok(stok);
    }

    public String getId() { return id; }
    public String getNama() { return nama; }
    public int getHarga() { return harga; }
    public int getStok() { return stok; }

    public void setHarga(int harga) {
        if (harga <= 0) throw new IllegalArgumentException("Harga harus lebih dari 0");
        this.harga = harga;
    }

    public void setStok(int stok) {
        if (stok < 0) throw new IllegalArgumentException("Stok tidak boleh negatif");
        this.stok = stok;
    }

    public void keluarkanStok(int jumlah) { setStok(stok - jumlah); }

    // Wajib di-override subclass (POLIMORFISME)
    public abstract int hitungHarga(int qty);
    public abstract String jenis();
    public abstract String catatan();
}

// ===================== PEWARISAN =====================
class Kopi extends ItemMenu {
    private boolean large;

    public Kopi(String id, String nama, int harga, int stok, boolean large) {
        super(id, nama, harga, stok);
        this.large = large;
    }

    public boolean isLarge() { return large; }

    @Override
    public int hitungHarga(int qty) {
        int hargaSatuan = getHarga() + (large ? 4000 : 0); // ukuran large +4.000
        return hargaSatuan * qty;
    }

    @Override
    public String jenis() { return "Kopi"; }

    @Override
    public String catatan() { return large ? "Ukuran large (+Rp4.000)" : "Ukuran regular"; }
}

class Roti extends ItemMenu {
    public Roti(String id, String nama, int harga, int stok) {
        super(id, nama, harga, stok);
    }

    @Override
    public int hitungHarga(int qty) {
        int dibayar = qty - (qty / 3); // promo beli 3 bayar 2
        return getHarga() * dibayar;
    }

    @Override
    public String jenis() { return "Roti"; }

    @Override
    public String catatan() { return "Promo beli 3 bayar 2"; }
}

class Merchandise extends ItemMenu {
    public Merchandise(String id, String nama, int harga, int stok) {
        super(id, nama, harga, stok);
    }

    @Override
    public int hitungHarga(int qty) {
        return (int) Math.round(getHarga() * qty * 1.11); // PPN 11%
    }

    @Override
    public String jenis() { return "Merchandise"; }

    @Override
    public String catatan() { return "Termasuk PPN 11%"; }
}

// ===================== ABSTRACT CLASS PEMBAYARAN + POLIMORFISME =====================
abstract class Pembayaran {
    private String nama;

    public Pembayaran(String nama) { this.nama = nama; }

    public String getNama() { return nama; }

    // positif = biaya tambahan, negatif = potongan
    public abstract int hitungPenyesuaian(int total);

    // method biasa (konkret) yang diwarisi semua subclass
    public String label() { return getNama(); }
}

class Tunai extends Pembayaran {
    public Tunai() { super("Tunai"); }

    @Override
    public int hitungPenyesuaian(int total) { return 0; }
}

class EWallet extends Pembayaran {
    public EWallet() { super("E-Wallet"); }

    @Override
    public int hitungPenyesuaian(int total) {
        return (int) Math.round(total * 0.015); // biaya layanan 1,5%
    }
}

class Voucher extends Pembayaran {
    public Voucher() { super("Voucher Diskon"); }

    @Override
    public int hitungPenyesuaian(int total) {
        int potongan = (int) Math.round(total * 0.10); // diskon 10%
        return -Math.min(potongan, 20000);             // maksimal Rp20.000
    }
}

// ===================== PESANAN =====================
class BarisPesanan {
    private ItemMenu item;
    private int qty;

    public BarisPesanan(ItemMenu item, int qty) {
        this.item = item;
        this.qty = qty;
    }

    public ItemMenu getItem() { return item; }
    public int getQty() { return qty; }
    public void tambahQty(int n) { qty += n; }

    // Polimorfisme: tiap jenis menu punya cara hitung sendiri
    public int subtotal() { return item.hitungHarga(qty); }
}

class Pesanan {
    private List<BarisPesanan> baris = new ArrayList<>();

    public List<BarisPesanan> getBaris() { return baris; }
    public boolean kosong() { return baris.isEmpty(); }
    public void kosongkan() { baris.clear(); }

    public boolean tambah(ItemMenu item, int qty) {
        BarisPesanan ada = null;
        for (BarisPesanan b : baris) {
            if (b.getItem() == item) ada = b;
        }
        int sudah = (ada == null) ? 0 : ada.getQty();
        if (qty <= 0 || sudah + qty > item.getStok()) return false;
        if (ada != null) ada.tambahQty(qty);
        else baris.add(new BarisPesanan(item, qty));
        return true;
    }

    public void hapus(int index) {
        if (index >= 0 && index < baris.size()) baris.remove(index);
    }

    public int total() {
        int t = 0;
        for (BarisPesanan b : baris) t += b.subtotal();
        return t;
    }

    public void potongStok() {
        for (BarisPesanan b : baris) b.getItem().keluarkanStok(b.getQty());
    }

    public String buatStruk(Pembayaran cara, int dibayar) {
        int total = total();
        int penyesuaian = cara.hitungPenyesuaian(total);
        int akhir = total + penyesuaian;

        StringBuilder sb = new StringBuilder();
        sb.append("======== STRUK KAFE ========\n");
        for (BarisPesanan b : baris) {
            sb.append(b.getItem().getNama()).append(" x").append(b.getQty())
              .append("\n   ").append(KafeServlet.rp(b.subtotal())).append("\n");
        }
        sb.append("----------------------------\n");
        sb.append("Subtotal   : ").append(KafeServlet.rp(total)).append("\n");
        sb.append(penyesuaian < 0 ? "Potongan   : " : "Biaya      : ")
          .append(KafeServlet.rp(Math.abs(penyesuaian)))
          .append(" (").append(cara.label()).append(")\n");
        sb.append("Total      : ").append(KafeServlet.rp(akhir)).append("\n");
        sb.append("Dibayar    : ").append(KafeServlet.rp(dibayar)).append("\n");
        sb.append("Kembalian  : ").append(KafeServlet.rp(dibayar - akhir)).append("\n");
        sb.append("============================\n");
        sb.append("   Terima kasih, datang lagi!");
        return sb.toString();
    }
}

// ===================== SERVLET =====================
@WebServlet("/kafe")
public class KafeServlet extends HttpServlet {

    // Polimorfisme: satu list bertipe ItemMenu berisi banyak subclass
    private static final List<ItemMenu> MENU = new ArrayList<>();
    // Polimorfisme: satu array bertipe Pembayaran berisi banyak subclass
    private static final Pembayaran[] CARA_BAYAR = { new Tunai(), new EWallet(), new Voucher() };

    static {
        MENU.add(new Kopi("K01", "Latte", 22000, 30, false));
        MENU.add(new Kopi("K02", "Cappuccino Large", 24000, 30, true));
        MENU.add(new Kopi("K03", "Americano", 18000, 40, false));
        MENU.add(new Roti("R01", "Croissant", 15000, 25));
        MENU.add(new Roti("R02", "Roti Bakar Coklat", 12000, 25));
        MENU.add(new Merchandise("M01", "Tumbler", 85000, 10));
        MENU.add(new Merchandise("M02", "Tote Bag", 45000, 15));
    }

    static String rp(int n) {
        return "Rp" + String.format(Locale.forLanguageTag("id-ID"), "%,d", n);
    }

    private static int angka(String s) {
        if (s == null) return -1;
        s = s.replaceAll("[^0-9]", "");
        if (s.isEmpty() || s.length() > 9) return -1;
        return Integer.parseInt(s);
    }

    private Pesanan ambilPesanan(HttpSession session) {
        Pesanan p = (Pesanan) session.getAttribute("pesanan");
        if (p == null) {
            p = new Pesanan();
            session.setAttribute("pesanan", p);
        }
        return p;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        req.setCharacterEncoding("UTF-8");
        HttpSession session = req.getSession();
        Pesanan pesanan = ambilPesanan(session);
        String aksi = req.getParameter("aksi");
        String pesan = null;

        synchronized (MENU) {
            if ("tambah".equals(aksi)) {
                int idx = angka(req.getParameter("idx"));
                int qty = angka(req.getParameter("qty"));
                if (idx < 0 || idx >= MENU.size() || qty <= 0) {
                    pesan = "Jumlah tidak valid!";
                } else if (!pesanan.tambah(MENU.get(idx), qty)) {
                    pesan = "Stok tidak cukup!";
                }

            } else if ("hapus".equals(aksi)) {
                pesanan.hapus(angka(req.getParameter("idx")));

            } else if ("kosongkan".equals(aksi)) {
                pesanan.kosongkan();

            } else if ("bayar".equals(aksi)) {
                int c = angka(req.getParameter("cara"));
                if (pesanan.kosong()) {
                    pesan = "Pesanan masih kosong!";
                } else if (c < 0 || c >= CARA_BAYAR.length) {
                    pesan = "Cara bayar tidak valid!";
                } else {
                    Pembayaran cara = CARA_BAYAR[c];
                    int total = pesanan.total();
                    int akhir = total + cara.hitungPenyesuaian(total);
                    int dibayar = akhir;

                    if (cara instanceof Tunai) {
                        int uang = angka(req.getParameter("uang"));
                        if (uang < 0) pesan = "Isi uang dengan angka yang valid!";
                        else if (uang < akhir) pesan = "Uang kurang " + rp(akhir - uang);
                        else dibayar = uang;
                    }

                    if (pesan == null) {
                        session.setAttribute("struk", pesanan.buatStruk(cara, dibayar));
                        pesanan.potongStok();
                        pesanan.kosongkan();
                    }
                }
            }
        }

        if (pesan != null) session.setAttribute("pesan", pesan);
        resp.sendRedirect(req.getContextPath() + "/kafe");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/html;charset=UTF-8");
        HttpSession session = req.getSession();
        Pesanan pesanan = ambilPesanan(session);

        String pesan = (String) session.getAttribute("pesan");
        String struk = (String) session.getAttribute("struk");
        session.removeAttribute("pesan");
        session.removeAttribute("struk");

        PrintWriter out = resp.getWriter();
        out.println("<!DOCTYPE html><html lang='id'><head><meta charset='UTF-8'>");
        out.println("<meta name='viewport' content='width=device-width, initial-scale=1'>");
        out.println("<title>Kasir Kafe</title>");
        out.println("<style>"
                + "body{font-family:Georgia,serif;background:#f5efe6;margin:0;padding:20px;color:#3b2f2f}"
                + "h1{text-align:center;margin-top:0;color:#5c3d2e}"
                + ".wrap{display:grid;grid-template-columns:3fr 2fr;gap:20px;max-width:1150px;margin:auto}"
                + ".card{background:#fffaf3;border-radius:12px;padding:16px;box-shadow:0 2px 8px rgba(0,0,0,.12)}"
                + "table{width:100%;border-collapse:collapse}"
                + "th,td{padding:8px;border-bottom:1px solid #e6d9c8;text-align:left;font-size:14px}"
                + "th{background:#efe3d2}"
                + "input[type=number]{width:60px;padding:5px}"
                + "input[name=uang],select{padding:8px;font-size:15px}"
                + "button{background:#8b5a3c;color:#fff;border:0;padding:7px 12px;border-radius:6px;cursor:pointer}"
                + "button.merah{background:#b91c1c}"
                + "button.hijau{background:#2f7d4f;width:100%;padding:12px;font-size:16px;margin-top:10px}"
                + ".total{font-size:22px;font-weight:bold;margin:14px 0}"
                + ".alert{max-width:1150px;margin:0 auto 16px;background:#fde2e2;color:#8a1c1c;padding:12px;border-radius:8px}"
                + ".struk{max-width:420px;margin:0 auto 16px;background:#fff;padding:16px;border-radius:10px;box-shadow:0 2px 8px rgba(0,0,0,.2)}"
                + ".struk pre{margin:0;font-size:14px}"
                + ".kecil{font-size:13px;color:#7a6a5a}"
                + "form{margin:0}"
                + "@media(max-width:900px){.wrap{grid-template-columns:1fr}}"
                + "</style></head><body>");
        out.println("<h1>&#9749; Kasir Kafe</h1>");

        if (pesan != null) out.println("<div class='alert'>" + pesan + "</div>");
        if (struk != null) out.println("<div class='struk'><pre>" + struk + "</pre></div>");

        out.println("<div class='wrap'>");

        // ----- Daftar menu -----
        out.println("<div class='card'><h2>Daftar Menu</h2><table>");
        out.println("<tr><th>ID</th><th>Nama</th><th>Jenis</th><th>Harga</th><th>Stok</th>"
                + "<th>Catatan</th><th>Jumlah</th></tr>");
        synchronized (MENU) {
            for (int i = 0; i < MENU.size(); i++) {
                ItemMenu m = MENU.get(i);
                out.println("<tr>"
                        + "<td>" + m.getId() + "</td><td>" + m.getNama() + "</td>"
                        + "<td>" + m.jenis() + "</td><td>" + rp(m.getHarga()) + "</td>"
                        + "<td>" + m.getStok() + "</td>"
                        + "<td class='kecil'>" + m.catatan() + "</td>"
                        + "<td><form method='post' action='kafe'>"
                        + "<input type='hidden' name='aksi' value='tambah'>"
                        + "<input type='hidden' name='idx' value='" + i + "'>"
                        + "<input type='number' name='qty' value='1' min='1'> "
                        + "<button type='submit'>Tambah</button></form></td></tr>");
            }
        }
        out.println("</table></div>");

        // ----- Pesanan & pembayaran -----
        out.println("<div class='card'><h2>Pesanan</h2><table>");
        out.println("<tr><th>Menu</th><th>Jml</th><th>Subtotal</th><th></th></tr>");
        if (pesanan.kosong()) {
            out.println("<tr><td colspan='4' style='text-align:center;color:#a39383'>Belum ada pesanan</td></tr>");
        }
        List<BarisPesanan> daftar = pesanan.getBaris();
        for (int i = 0; i < daftar.size(); i++) {
            BarisPesanan b = daftar.get(i);
            out.println("<tr><td>" + b.getItem().getNama() + "</td><td>" + b.getQty() + "</td>"
                    + "<td>" + rp(b.subtotal()) + "</td>"
                    + "<td><form method='post' action='kafe'>"
                    + "<input type='hidden' name='aksi' value='hapus'>"
                    + "<input type='hidden' name='idx' value='" + i + "'>"
                    + "<button class='merah' type='submit'>&#10005;</button></form></td></tr>");
        }
        out.println("</table>");

        int total = pesanan.total();
        out.println("<div class='total'>SUBTOTAL: " + rp(total) + "</div>");

        if (!pesanan.kosong()) {
            out.println("<table class='kecil'><tr><th>Cara bayar</th><th>Penyesuaian</th><th>Total akhir</th></tr>");
            for (Pembayaran c : CARA_BAYAR) {
                int p = c.hitungPenyesuaian(total);
                String teks = (p < 0 ? "-" : "+") + rp(Math.abs(p));
                out.println("<tr><td>" + c.getNama() + "</td><td>" + teks
                        + "</td><td>" + rp(total + p) + "</td></tr>");
            }
            out.println("</table><br>");
        }

        out.println("<form method='post' action='kafe'>"
                + "<input type='hidden' name='aksi' value='bayar'>"
                + "Cara bayar: <select name='cara'>");
        for (int i = 0; i < CARA_BAYAR.length; i++) {
            out.println("<option value='" + i + "'>" + CARA_BAYAR[i].getNama() + "</option>");
        }
        out.println("</select><br><br>"
                + "Uang diterima (khusus Tunai): Rp <input type='text' name='uang' placeholder='0' inputmode='numeric'>"
                + "<button class='hijau' type='submit'>BAYAR</button></form><br>");
        out.println("<form method='post' action='kafe'>"
                + "<input type='hidden' name='aksi' value='kosongkan'>"
                + "<button class='merah' type='submit'>Kosongkan Pesanan</button></form>");
        out.println("</div>");

        out.println("</div></body></html>");
    }
}
